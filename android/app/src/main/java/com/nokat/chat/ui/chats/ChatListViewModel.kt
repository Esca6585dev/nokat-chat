package com.nokat.chat.ui.chats

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nokat.chat.data.model.Chat
import com.nokat.chat.data.model.MessageKind
import com.nokat.chat.data.model.displayName
import com.nokat.chat.data.model.displayTitle
import com.nokat.chat.data.model.peer
import com.nokat.chat.data.realtime.RealtimeClient
import com.nokat.chat.data.repo.ChatRepository
import com.nokat.chat.data.repo.Decrypted
import com.nokat.chat.data.repo.SecretAccess
import com.nokat.chat.data.repo.SecretChatManager
import com.nokat.chat.data.session.SessionManager
import com.nokat.chat.data.session.SessionState
import com.nokat.chat.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pre-formatted row model so the list does no work during composition. */
@Immutable
data class ChatItemUi(
    val id: String,
    val title: String,
    /** Last message text (the caption for photos/files); null when there is no message. */
    val preview: String?,
    /** Kind of the last message: "text" | "image" | "file". */
    val previewKind: String = MessageKind.TEXT,
    /** File name for file messages. */
    val attachmentName: String? = null,
    val lastFromMe: Boolean,
    /** Sender's first name for incoming group messages. */
    val lastSenderName: String?,
    /** Status of the last message if it is mine (for ticks), else null. */
    val outgoingStatus: String?,
    val time: String,
    val unread: Int,
    val online: Boolean,
    val isGroup: Boolean,
    val avatarSeed: String,
    val avatarUrl: String,
    /** null → nobody typing; "" → peer typing (direct); name → member typing (group). */
    val typingName: String?,
    val isSecret: Boolean = false,
    /** Secret chats only. */
    val secretAccess: SecretAccess? = null,
    /** Secret chat whose last message cannot be decrypted here ("🔒 Şifrlenen hat"). */
    val previewEncrypted: Boolean = false,
)

@Immutable
data class ChatListUiState(
    val items: List<ChatItemUi> = emptyList(),
    val refreshing: Boolean = false,
    val loaded: Boolean = false,
    val connected: Boolean = true,
)

class ChatListViewModel(
    private val chats: ChatRepository,
    session: SessionManager,
    realtime: RealtimeClient,
    private val timeFormatter: TimeFormatter,
    private val secret: SecretChatManager,
) : ViewModel() {

    var query by mutableStateOf("")
        private set

    private val refreshing = MutableStateFlow(false)
    private val loaded = MutableStateFlow(chats.chats.value.isNotEmpty())

    private val items: Flow<List<ChatItemUi>> = combine(
        chats.chats,
        chats.typing,
        snapshotFlow { query },
        session.state,
        secret.version,
    ) { list, typing, q, s, _ ->
        val me = (s as? SessionState.LoggedIn)?.user?.id
        val mapped = list.map { toUi(it, me, typing[it.id].orEmpty()) }
        val needle = q.trim()
        if (needle.isEmpty()) {
            mapped
        } else {
            mapped.filter {
                it.title.contains(needle, ignoreCase = true) ||
                    it.preview?.contains(needle, ignoreCase = true) == true ||
                    it.attachmentName?.contains(needle, ignoreCase = true) == true
            }
        }
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<ChatListUiState> = combine(
        items,
        refreshing,
        loaded,
        realtime.state,
    ) { items, isRefreshing, isLoaded, conn ->
        ChatListUiState(
            items = items,
            refreshing = isRefreshing,
            loaded = isLoaded || items.isNotEmpty(),
            connected = conn == RealtimeClient.State.Connected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatListUiState())

    init {
        refresh(showIndicator = false)
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun refresh(showIndicator: Boolean = true) {
        viewModelScope.launch {
            if (showIndicator) refreshing.value = true
            chats.refreshChats()
            loaded.value = true
            refreshing.value = false
        }
    }

    private fun toUi(chat: Chat, me: String?, typingIds: Set<String>): ChatItemUi {
        val peer = chat.peer(me)
        val last = chat.lastMessage
        val fromMe = last != null && last.senderId == me
        val typingName: String? = when {
            typingIds.isEmpty() -> null
            !chat.isGroup -> ""
            else -> chat.members.firstOrNull { it.id in typingIds }?.displayName?.substringBefore(' ') ?: ""
        }
        val senderName: String? = if (chat.isGroup && last != null && !fromMe) {
            chat.members.firstOrNull { it.id == last.senderId }?.displayName?.substringBefore(' ')
        } else {
            null
        }
        var preview: String? = last?.text?.replace('\n', ' ')
        var previewKind = when {
            last == null -> MessageKind.TEXT
            last.kind == MessageKind.IMAGE || last.attachment?.kind == MessageKind.IMAGE -> MessageKind.IMAGE
            last.kind == MessageKind.FILE || last.attachment != null -> MessageKind.FILE
            else -> MessageKind.TEXT
        }
        var attachmentName: String? = last?.attachment?.name
        var previewEncrypted = false
        val access = if (chat.isSecret) secret.access(chat, me) else null
        if (chat.isSecret && last != null) {
            // Decrypt locally when this device can; otherwise "🔒 Şifrlenen hat". Memory only.
            preview = null
            previewKind = MessageKind.TEXT
            attachmentName = null
            previewEncrypted = true
            if (access == SecretAccess.ACTIVE) {
                val key = secret.cachedKey(chat.id)
                if (key == null) {
                    secret.ensureKeyAsync(chat) // re-emits via secret.version when ready
                } else {
                    val decrypted = secret.decrypt(chat.id, key, last)
                    if (decrypted is Decrypted.Ok) {
                        previewEncrypted = false
                        preview = decrypted.payload.text.replace('\n', ' ')
                        previewKind = decrypted.payload.kind
                        attachmentName = decrypted.payload.file?.name
                    }
                }
            }
        }
        return ChatItemUi(
            id = chat.id,
            title = chat.displayTitle(me),
            preview = preview,
            previewKind = previewKind,
            attachmentName = attachmentName,
            lastFromMe = fromMe,
            lastSenderName = senderName,
            outgoingStatus = if (fromMe) last?.status else null,
            time = timeFormatter.chatListTime(last?.createdAt ?: chat.updatedAt),
            unread = chat.unreadCount,
            online = !chat.isGroup && peer?.online == true,
            isGroup = chat.isGroup,
            avatarSeed = if (chat.isGroup) chat.id else peer?.id ?: chat.id,
            avatarUrl = if (chat.isGroup) chat.avatarUrl else peer?.avatarUrl.orEmpty(),
            typingName = typingName,
            isSecret = chat.isSecret,
            secretAccess = access,
            previewEncrypted = previewEncrypted,
        )
    }
}
