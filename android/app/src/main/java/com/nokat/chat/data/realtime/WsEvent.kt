package com.nokat.chat.data.realtime

import com.nokat.chat.data.model.Chat
import com.nokat.chat.data.model.Message

/** Server → client WebSocket events (docs/API.md), plus local connection events. */
sealed interface WsEvent {
    data class MessageNew(val message: Message) : WsEvent
    data class MessageRead(val chatId: String, val userId: String, val readAt: String?) : WsEvent
    data class ChatNew(val chat: Chat) : WsEvent
    data class Typing(val chatId: String, val userId: String, val typing: Boolean) : WsEvent
    data class Presence(val userId: String, val online: Boolean, val lastSeen: String?) : WsEvent

    /** Emitted every time the socket (re)connects; consumers should refetch state. */
    data object Connected : WsEvent

    /** Handshake was rejected with 401. */
    data object Unauthorized : WsEvent
}
