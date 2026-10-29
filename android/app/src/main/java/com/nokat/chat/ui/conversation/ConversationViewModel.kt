package com.nokat.chat.ui.conversation

import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nokat.chat.data.model.Chat
import com.nokat.chat.data.model.Message
import com.nokat.chat.data.model.MessageStatus
import com.nokat.chat.data.model.User
import com.nokat.chat.data.model.displayName
import com.nokat.chat.data.model.displayTitle
import com.nokat.chat.data.model.peer
import com.nokat.chat.data.realtime.WsEvent
import com.nokat.chat.data.realtime.RealtimeClient
import com.nokat.chat.data.remote.toUiText
import com.nokat.chat.data.repo.ChatRepository
import com.nokat.chat.data.session.SessionManager
import com.nokat.chat.util.TimeFormatter
import com.nokat.chat.util.Timestamps
import com.nokat.chat.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Rows of the (reverse-layout) message list, newest first. */
@Immutable
sealed interface ConvItem {
    val key: String

    data class DateHeader(override val key: String, val label: String) : ConvItem

    data class Msg(
        override val key: String,
        val message: Message,
        val outgoing: Boolean,
        val time: String,
        /** Sender name above the first bubble of a run (incoming group messages only). */
        val senderName: String?,
        val senderSeed: String,
        /** First bubble of a run of consecutive messages by the same sender. */
        val firstInRun: Boolean,
        /** Last bubble of a run: gets the "tail" corner. */
        val lastInRun: Boolean,
    ) : ConvItem
}

@Immutable
sealed interface HeaderStatus {
    data object None : HeaderStatus
    data object Online : HeaderStatus
    data class LastSeen(val text: String) : HeaderStatus
    /** [name] is null in direct chats. */
    data class Typing(val name: String?) : HeaderStatus
    data class Members(val count: Int, val online: Int) : HeaderStatus
}

@Immutable
data class ConversationHeader(
    val title: String,
    val avatarSeed: String,
    val avatarUrl: String,
    val isGroup: Boolean,
    val status: HeaderStatus,
)

@Immutable
data class PagingState(
    val loadingInitial: Boolean = true,
    val loadingOlder: Boolean = false,
    val endReached: Boolean = false,
    val error: UiText? = null,
)

class ConversationViewModel(
    private val chatId: String,
    private val chats: ChatRepository,
    private val session: SessionManager,
    private val realtime: RealtimeClient,
    private val timeFormatter: TimeFormatter,
) : ViewModel() {

    private val myId: String? get() = session.currentUser?.id

    /** Oldest → newest, deduplicated by id / client_id. */
    private val messages = MutableStateFlow<List<Message>>(emptyList())

    private val _paging = MutableStateFlow(PagingState())
    val paging: StateFlow<PagingState> = _paging.asStateFlow()

    private val chatFlow = chats.chats
        .map { list -> list.firstOrNull { it.id == chatId } }
        .distinctUntilChanged()

    val items: StateFlow<List<ConvItem>> = combine(messages, chatFlow) { list, chat ->
        buildItems(list, chat)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val header: StateFlow<ConversationHeader?> = combine(chatFlow, chats.typing) { chat, typing ->
        chat?.let { buildHeader(it, typing[chatId].orEmpty()) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Fired when the list should jump to the newest message (e.g. after sending). */
    private val _scrollToBottom = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToBottom: SharedFlow<Unit> = _scrollToBottom.asSharedFlow()

    var draft by mutableStateOf("")
        private set

    private var resumed = false
    private var typingSentAt = 0L
    private var typingStopJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                chats.ensureChat(chatId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Header stays empty; messages may still load.
            }
        }
        viewModelScope.launch { realtime.events.collect { onEvent(it) } }
        loadInitial()
    }

    // ---- Lifecycle ----

    fun onResume() {
        resumed = true
        chats.setActiveChat(chatId)
        chats.markRead(chatId)
    }

    fun onPause() {
        resumed = false
        chats.clearActiveChat(chatId)
        stopTyping()
    }

    override fun onCleared() {
        chats.clearActiveChat(chatId)
        if (typingSentAt != 0L) chats.sendTyping(chatId, false)
    }

    // ---- Loading ----

    private fun loadInitial() {
        viewModelScope.launch {
            _paging.update { it.copy(loadingInitial = true, error = null) }
            try {
                val page = chats.loadMessages(chatId, before = null)
                messages.update { merge(it, page) }
                _paging.update {
                    it.copy(loadingInitial = false, endReached = page.size < ChatRepository.PAGE_SIZE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _paging.update { it.copy(loadingInitial = false, error = e.toUiText()) }
            }
        }
    }

    /** Loads the page before the oldest loaded message (`?before=`). */
    fun loadOlder() {
        val state = _paging.value
        if (state.loadingInitial || state.loadingOlder || state.endReached) return
        val oldest = messages.value.firstOrNull { !it.isPending } ?: return
        _paging.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            try {
                val page = chats.loadMessages(chatId, before = oldest.id)
                messages.update { merge(it, page) }
                _paging.update {
                    it.copy(loadingOlder = false, endReached = page.size < ChatRepository.PAGE_SIZE, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _paging.update { it.copy(loadingOlder = false, error = e.toUiText()) }
            }
        }
    }

    /** After a reconnect: pull the newest page and merge it in (catches messages missed while offline). */
    private fun syncLatest() {
        viewModelScope.launch {
            try {
                val page = chats.loadMessages(chatId, before = null)
                messages.update { merge(it, page) }
                if (resumed) chats.markRead(chatId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignore; the next reconnect will retry.
            }
        }
    }

    // ---- Realtime ----

    private fun onEvent(event: WsEvent) {
        when (event) {
            is WsEvent.MessageNew -> {
                val message = event.message
                if (message.chatId != chatId) return
                messages.update { merge(it, listOf(message)) }
                if (message.senderId != myId && resumed) chats.markRead(chatId)
            }
            is WsEvent.MessageRead -> {
                if (event.chatId != chatId || event.userId == myId) return
                val me = myId ?: return
                messages.update { list ->
                    list.map { m ->
                        if (m.senderId == me && !m.isPending && m.status != MessageStatus.READ) {
                            m.copy(status = MessageStatus.READ)
                        } else {
                            m
                        }
                    }
                }
            }
            is WsEvent.Connected -> if (!_paging.value.loadingInitial) syncLatest()
            else -> Unit
        }
    }

    // ---- Composer ----

    fun onDraftChange(value: String) {
        draft = value
        if (value.isBlank()) {
            stopTyping()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (typingSentAt == 0L || now - typingSentAt > TYPING_REFRESH_MS) {
            chats.sendTyping(chatId, true)
            typingSentAt = now
        }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(TYPING_IDLE_MS)
            stopTyping()
        }
    }

    private fun stopTyping() {
        typingStopJob?.cancel()
        typingStopJob = null
        if (typingSentAt != 0L) {
            typingSentAt = 0L
            chats.sendTyping(chatId, false)
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        val me = myId ?: return
        draft = ""
        stopTyping()
        val clientId = UUID.randomUUID().toString()
        val local = Message(
            id = "local-$clientId",
            chatId = chatId,
            senderId = me,
            text = text,
            clientId = clientId,
            status = MessageStatus.SENDING,
            createdAt = Timestamps.nowIso(),
        )
        messages.update { it + local }
        _scrollToBottom.tryEmit(Unit)
        deliver(local)
    }

    fun retry(message: Message) {
        if (!message.failed) return
        val clientId = message.clientIdOrNull ?: return
        messages.update { list -> list.map { if (it.clientIdOrNull == clientId) it.copy(failed = false) else it } }
        deliver(message.copy(failed = false))
    }

    private fun deliver(local: Message) {
        val clientId = local.clientIdOrNull ?: return
        viewModelScope.launch {
            try {
                val sent = chats.sendMessage(chatId, local.text, clientId)
                // If the server does not echo client_id, keep ours so the optimistic row is replaced.
                val fixed = if (sent.clientIdOrNull == null) sent.copy(clientId = clientId) else sent
                messages.update { merge(it, listOf(fixed)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.update { list ->
                    list.map { if (it.clientIdOrNull == clientId && it.isPending) it.copy(failed = true) else it }
                }
            }
        }
    }

    // ---- Pure helpers ----

    /** Upserts [incoming] by id or client_id; never downgrades a delivery status. Sorted oldest → newest. */
    private fun merge(existing: List<Message>, incoming: List<Message>): List<Message> {
        if (incoming.isEmpty()) return existing
        val result = existing.toMutableList()
        for (m in incoming) {
            if (m.chatId != chatId) continue
            val cid = m.clientIdOrNull
            val index = result.indexOfFirst { it.id == m.id || (cid != null && it.clientIdOrNull == cid) }
            if (index >= 0) {
                val old = result[index]
                result[index] = if (!old.isPending && MessageStatus.rank(old.status) > MessageStatus.rank(m.status)) {
                    m.copy(status = old.status)
                } else {
                    m
                }
            } else {
                result += m
            }
        }
        return result.sortedBy { Timestamps.toMillis(it.createdAt) }
    }

    private fun buildItems(list: List<Message>, chat: Chat?): List<ConvItem> {
        if (list.isEmpty()) return emptyList()
        val me = myId
        val isGroup = chat?.isGroup == true
        val members: Map<String, User> = chat?.members?.associateBy { it.id }.orEmpty()
        val days = LongArray(list.size) { timeFormatter.epochDay(list[it].createdAt) }
        val out = ArrayList<ConvItem>(list.size + 8)
        val seen = HashSet<String>(list.size * 2)

        for (i in list.indices.reversed()) {
            val m = list[i]
            val day = days[i]
            val older = if (i > 0) list[i - 1] else null
            val newer = if (i < list.lastIndex) list[i + 1] else null
            val sameRunAsOlder = older != null && older.senderId == m.senderId && days[i - 1] == day
            val sameRunAsNewer = newer != null && newer.senderId == m.senderId && days[i + 1] == day
            val outgoing = m.senderId == me
            val key = "m:" + (m.clientIdOrNull ?: m.id)
            if (seen.add(key)) {
                out += ConvItem.Msg(
                    key = key,
                    message = m,
                    outgoing = outgoing,
                    time = timeFormatter.messageTime(m.createdAt),
                    senderName = if (isGroup && !outgoing && !sameRunAsOlder) {
                        members[m.senderId]?.displayName
                    } else {
                        null
                    },
                    senderSeed = m.senderId,
                    firstInRun = !sameRunAsOlder,
                    lastInRun = !sameRunAsNewer,
                )
            }
            if (i == 0 || days[i - 1] != day) {
                val headerKey = "d:$day"
                if (seen.add(headerKey)) out += ConvItem.DateHeader(headerKey, timeFormatter.dayLabel(day))
            }
        }
        return out
    }

    private fun buildHeader(chat: Chat, typingIds: Set<String>): ConversationHeader {
        val me = myId
        val peer = chat.peer(me)
        val status: HeaderStatus = when {
            typingIds.isNotEmpty() -> HeaderStatus.Typing(
                if (chat.isGroup) {
                    chat.members.firstOrNull { it.id in typingIds }?.displayName?.substringBefore(' ')
                } else {
                    null
                },
            )
            chat.isGroup -> HeaderStatus.Members(
                count = chat.members.size,
                online = chat.members.count { it.online && it.id != me },
            )
            peer == null -> HeaderStatus.None
            peer.online -> HeaderStatus.Online
            else -> HeaderStatus.LastSeen(timeFormatter.lastSeen(peer.lastSeen))
        }
        return ConversationHeader(
            title = chat.displayTitle(me),
            avatarSeed = if (chat.isGroup) chat.id else peer?.id ?: chat.id,
            avatarUrl = if (chat.isGroup) chat.avatarUrl else peer?.avatarUrl.orEmpty(),
            isGroup = chat.isGroup,
            status = status,
        )
    }

    private companion object {
        /** Re-send `typing: true` at most this often while the user keeps typing. */
        const val TYPING_REFRESH_MS = 3_000L

        /** Send `typing: false` after this much inactivity. */
        const val TYPING_IDLE_MS = 4_000L
    }
}
