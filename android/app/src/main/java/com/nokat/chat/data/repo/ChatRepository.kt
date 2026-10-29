package com.nokat.chat.data.repo

import com.nokat.chat.data.model.Chat
import com.nokat.chat.data.model.DirectChatRequest
import com.nokat.chat.data.model.GroupChatRequest
import com.nokat.chat.data.model.Message
import com.nokat.chat.data.model.MessageStatus
import com.nokat.chat.data.model.SendMessageRequest
import com.nokat.chat.data.realtime.RealtimeClient
import com.nokat.chat.data.realtime.WsEvent
import com.nokat.chat.data.remote.NokatApi
import com.nokat.chat.data.remote.decodeList
import com.nokat.chat.data.session.SessionManager
import com.nokat.chat.util.Timestamps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the chat list, typing indicators and presence. Keeps itself in sync with the
 * realtime stream; screens observe [chats] / [typing].
 */
class ChatRepository(
    private val api: NokatApi,
    private val json: Json,
    private val realtime: RealtimeClient,
    private val session: SessionManager,
    private val scope: CoroutineScope,
) {
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    /** chatId → ids of users currently typing there (never includes the current user). */
    private val _typing = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val typing: StateFlow<Map<String, Set<String>>> = _typing.asStateFlow()

    private val typingExpiry = ConcurrentHashMap<String, Job>()

    /** Chat currently open and visible: incoming messages there don't bump unread. */
    @Volatile
    private var activeChatId: String? = null

    init {
        scope.launch { realtime.events.collect { handle(it) } }
    }

    // ---- Queries / commands ----

    /** Refetches GET /chats. Returns true on success. */
    suspend fun refreshChats(): Boolean = try {
        val list = json.decodeList(api.chats(), Chat.serializer())
        _chats.value = list.sortedByActivity()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    fun chatOrNull(chatId: String): Chat? = _chats.value.firstOrNull { it.id == chatId }

    /** Makes sure the chat is in [chats] (fetches GET /chats/:id if needed). */
    suspend fun ensureChat(chatId: String): Chat =
        chatOrNull(chatId) ?: api.chat(chatId).also { upsertChat(it) }

    suspend fun openDirectChat(userId: String): Chat =
        api.createDirectChat(DirectChatRequest(userId)).also { upsertChat(it) }

    suspend fun createGroup(title: String, memberIds: List<String>): Chat =
        api.createGroupChat(GroupChatRequest(title, memberIds)).also { upsertChat(it) }

    /** GET /chats/:id/messages, oldest → newest. */
    suspend fun loadMessages(chatId: String, before: String?, limit: Int = PAGE_SIZE): List<Message> =
        json.decodeList(api.messages(chatId, before, limit), Message.serializer())

    /**
     * POST /chats/:id/messages. Runs in the application scope so leaving the screen does not
     * cancel an in-flight send.
     */
    suspend fun sendMessage(chatId: String, text: String, clientId: String): Message =
        scope.async { api.sendMessage(chatId, SendMessageRequest(text, clientId)) }
            .await()
            .also { onMessage(it) }

    fun markRead(chatId: String) {
        _chats.update { list -> list.map { if (it.id == chatId && it.unreadCount != 0) it.copy(unreadCount = 0) else it } }
        scope.launch {
            try {
                api.markRead(chatId, JsonObject(emptyMap()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Fall back to the socket.
                realtime.send("read", JsonObject(mapOf("chat_id" to JsonPrimitive(chatId))))
            }
        }
    }

    fun sendTyping(chatId: String, typing: Boolean) {
        realtime.send(
            "typing",
            JsonObject(mapOf("chat_id" to JsonPrimitive(chatId), "typing" to JsonPrimitive(typing))),
        )
    }

    fun setActiveChat(chatId: String) {
        activeChatId = chatId
    }

    fun clearActiveChat(chatId: String) {
        if (activeChatId == chatId) activeChatId = null
    }

    fun clear() {
        _chats.value = emptyList()
        _typing.value = emptyMap()
        typingExpiry.values.forEach { it.cancel() }
        typingExpiry.clear()
        activeChatId = null
    }

    // ---- Realtime ----

    private fun handle(event: WsEvent) {
        val me = session.currentUser?.id
        when (event) {
            is WsEvent.Connected -> scope.launch { refreshChats() }
            is WsEvent.MessageNew -> {
                setTyping(event.message.chatId, event.message.senderId, typing = false)
                onMessage(event.message)
            }
            is WsEvent.MessageRead -> _chats.update { list ->
                list.map { chat ->
                    when {
                        chat.id != event.chatId -> chat
                        event.userId == me -> chat.copy(unreadCount = 0)
                        else -> {
                            val last = chat.lastMessage
                            if (last != null && last.senderId == me && last.status != MessageStatus.READ) {
                                chat.copy(lastMessage = last.copy(status = MessageStatus.READ))
                            } else {
                                chat
                            }
                        }
                    }
                }
            }
            is WsEvent.ChatNew -> upsertChat(event.chat)
            is WsEvent.Typing -> if (event.userId != me) setTyping(event.chatId, event.userId, event.typing)
            is WsEvent.Presence -> _chats.update { list ->
                list.map { chat ->
                    if (chat.members.none { it.id == event.userId }) {
                        chat
                    } else {
                        chat.copy(
                            members = chat.members.map { u ->
                                if (u.id == event.userId) {
                                    u.copy(online = event.online, lastSeen = event.lastSeen ?: u.lastSeen)
                                } else {
                                    u
                                }
                            },
                        )
                    }
                }
            }
            is WsEvent.Unauthorized -> session.token?.let { session.onUnauthorized(it) }
        }
    }

    /** Applies a new/echoed message to the chat list (last message, order, unread). Idempotent. */
    private fun onMessage(message: Message) {
        val me = session.currentUser?.id
        var known = false
        _chats.update { list ->
            val index = list.indexOfFirst { it.id == message.chatId }
            if (index < 0) return@update list
            known = true
            val chat = list[index]
            val last = chat.lastMessage
            val duplicate = last != null && last.id == message.id
            val isNewer = last == null || duplicate ||
                Timestamps.toMillis(message.createdAt) >= Timestamps.toMillis(last?.createdAt)
            val bumpUnread = !duplicate && message.senderId != me && activeChatId != message.chatId
            val updated = chat.copy(
                lastMessage = if (isNewer) mergeStatus(last, message) else last,
                updatedAt = if (isNewer && message.createdAt.isNotBlank()) message.createdAt else chat.updatedAt,
                unreadCount = chat.unreadCount + if (bumpUnread) 1 else 0,
            )
            list.toMutableList().apply { set(index, updated) }.sortedByActivity()
        }
        if (!known) scope.launch { refreshChats() }
    }

    private fun mergeStatus(old: Message?, new: Message): Message =
        if (old != null && old.id == new.id && MessageStatus.rank(old.status) > MessageStatus.rank(new.status)) {
            new.copy(status = old.status)
        } else {
            new
        }

    private fun upsertChat(chat: Chat) {
        _chats.update { list ->
            val index = list.indexOfFirst { it.id == chat.id }
            val next = if (index >= 0) list.toMutableList().apply { set(index, chat) } else list + chat
            next.sortedByActivity()
        }
    }

    private fun setTyping(chatId: String, userId: String, typing: Boolean) {
        val key = "$chatId|$userId"
        typingExpiry.remove(key)?.cancel()
        if (typing) {
            _typing.update { map -> map + (chatId to (map[chatId].orEmpty() + userId)) }
            typingExpiry[key] = scope.launch {
                delay(TYPING_TIMEOUT_MS)
                removeTyping(chatId, userId)
            }
        } else {
            removeTyping(chatId, userId)
        }
    }

    private fun removeTyping(chatId: String, userId: String) {
        _typing.update { map ->
            val current = map[chatId] ?: return@update map
            val next = current - userId
            if (next.isEmpty()) map - chatId else map + (chatId to next)
        }
    }

    private fun List<Chat>.sortedByActivity(): List<Chat> = sortedByDescending { chat ->
        maxOf(Timestamps.toMillis(chat.updatedAt), Timestamps.toMillis(chat.lastMessage?.createdAt))
    }

    companion object {
        const val PAGE_SIZE = 50
        private const val TYPING_TIMEOUT_MS = 6_000L
    }
}
