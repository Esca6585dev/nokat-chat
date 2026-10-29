package com.nokat.chat.ui.conversation

import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nokat.chat.R
import com.nokat.chat.data.model.Chat
import com.nokat.chat.data.model.Message
import com.nokat.chat.data.model.MessageKind
import com.nokat.chat.data.model.MessageStatus
import com.nokat.chat.data.model.User
import com.nokat.chat.data.model.displayName
import com.nokat.chat.data.model.displayTitle
import com.nokat.chat.data.model.peer
import com.nokat.chat.data.realtime.WsEvent
import com.nokat.chat.data.realtime.RealtimeClient
import com.nokat.chat.data.remote.FileUrls
import com.nokat.chat.data.remote.httpCode
import com.nokat.chat.data.remote.toUiText
import com.nokat.chat.data.repo.ChatRepository
import com.nokat.chat.data.session.SessionManager
import com.nokat.chat.util.AttachmentReader
import com.nokat.chat.util.TimeFormatter
import com.nokat.chat.util.Timestamps
import com.nokat.chat.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        /** Photo / file payload, null for plain text. */
        val attachment: AttachmentUi? = null,
    ) : ConvItem
}

@Immutable
data class AttachmentUi(
    val isImage: Boolean,
    /** Coil model: the local `content://` Uri while we have it (no flicker after upload), else the remote URL. */
    val previewModel: String,
    /** Absolute download URL; null until the upload finished. */
    val remoteUrl: String?,
    val name: String,
    val sizeText: String,
    val width: Int,
    val height: Int,
)

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
    private val fileUrls: FileUrls,
    private val attachmentReader: AttachmentReader,
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

    /** clientId → upload progress 0..1 (absent = not uploading / unknown). Read per bubble. */
    private val uploadProgress = mutableStateMapOf<String, Float>()

    fun progressOf(clientId: String?): Float? = clientId?.let { uploadProgress[it] }

    /** One-off messages for a snackbar (e.g. file too big). */
    private val _notices = Channel<UiText>(Channel.BUFFERED)
    val notices: Flow<UiText> = _notices.receiveAsFlow()

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

    /** Photos/files picked in the composer. The current draft becomes the caption of the first one. */
    fun sendAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val me = myId ?: return
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) { uris.mapNotNull { attachmentReader.describe(it) } }
            if (files.size < uris.size) _notices.send(UiText.Res(R.string.error_file_unreadable))
            val (tooBig, accepted) = files.partition { it.size > AttachmentReader.MAX_UPLOAD_BYTES }
            tooBig.firstOrNull()?.let { _notices.send(UiText.Res(R.string.error_file_too_big, listOf(it.name))) }
            if (accepted.isEmpty()) return@launch

            val caption = draft.trim()
            if (caption.isNotEmpty()) {
                draft = ""
                stopTyping()
            }
            val locals = accepted.mapIndexed { index, file ->
                val clientId = UUID.randomUUID().toString()
                Message(
                    id = "local-$clientId",
                    chatId = chatId,
                    senderId = me,
                    text = if (index == 0) caption else "",
                    clientId = clientId,
                    status = MessageStatus.SENDING,
                    createdAt = Timestamps.nowIso(),
                    kind = if (file.isImage) MessageKind.IMAGE else MessageKind.FILE,
                    localFile = file,
                )
            }
            messages.update { it + locals }
            _scrollToBottom.tryEmit(Unit)
            // Sequential so the messages arrive in the order they were picked.
            for (local in locals) deliverNow(local)
        }
    }

    private fun deliver(local: Message) {
        viewModelScope.launch { deliverNow(local) }
    }

    /** Uploads the attachment first if needed (only once, also across retries), then posts the message. */
    private suspend fun deliverNow(local: Message) {
        val clientId = local.clientIdOrNull ?: return
        try {
            var attachmentId = local.attachment?.id
            val file = local.localFile
            if (attachmentId == null && file != null) {
                uploadProgress[clientId] = 0f
                val uploaded = chats.uploadAttachment(file) { fraction ->
                    // Called on an OkHttp thread; hop to main for the snapshot state write.
                    viewModelScope.launch { if (uploadProgress.containsKey(clientId)) uploadProgress[clientId] = fraction }
                }
                attachmentId = uploaded.id
                messages.update { list ->
                    list.map { if (it.clientIdOrNull == clientId) it.copy(attachment = uploaded) else it }
                }
            }
            uploadProgress.remove(clientId)
            val sent = chats.sendMessage(chatId, local.text, clientId, attachmentId)
            // If the server does not echo client_id, keep ours so the optimistic row is replaced.
            val fixed = if (sent.clientIdOrNull == null) sent.copy(clientId = clientId) else sent
            messages.update { merge(it, listOf(fixed)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.httpCode == 413) _notices.trySend(UiText.Res(R.string.error_file_too_big_generic))
            messages.update { list ->
                list.map { if (it.clientIdOrNull == clientId && it.isPending) it.copy(failed = true) else it }
            }
        } finally {
            uploadProgress.remove(clientId)
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
                // Keep the local file so the bubble keeps showing the already-decoded local preview.
                val incoming = if (m.localFile == null && old.localFile != null) m.copy(localFile = old.localFile) else m
                result[index] = if (!old.isPending && MessageStatus.rank(old.status) > MessageStatus.rank(m.status)) {
                    incoming.copy(status = old.status)
                } else {
                    incoming
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
                    attachment = attachmentUi(m),
                )
            }
            if (i == 0 || days[i - 1] != day) {
                val headerKey = "d:$day"
                if (seen.add(headerKey)) out += ConvItem.DateHeader(headerKey, timeFormatter.dayLabel(day))
            }
        }
        return out
    }

    private fun attachmentUi(m: Message): AttachmentUi? {
        val remote = m.attachment
        val local = m.localFile
        if (remote == null && local == null) return null
        val remoteUrl = remote?.url?.takeIf { it.isNotEmpty() }?.let { fileUrls.resolve(it) }
        val model = local?.uri ?: remoteUrl ?: return null
        val isImage = when {
            m.kind == MessageKind.IMAGE -> true
            m.kind == MessageKind.FILE -> false
            remote != null -> remote.kind == MessageKind.IMAGE
            else -> local?.isImage == true
        }
        return AttachmentUi(
            isImage = isImage,
            previewModel = model,
            remoteUrl = remoteUrl,
            name = remote?.name?.takeIf { it.isNotEmpty() } ?: local?.name.orEmpty(),
            sizeText = timeFormatter.fileSize(remote?.size ?: local?.size ?: -1L),
            width = remote?.width?.takeIf { it > 0 } ?: local?.width ?: 0,
            height = remote?.height?.takeIf { it > 0 } ?: local?.height ?: 0,
        )
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
