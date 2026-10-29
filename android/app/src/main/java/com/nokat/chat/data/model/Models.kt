package com.nokat.chat.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Mirrors the `User` model in docs/API.md. */
@Serializable
data class User(
    val id: String,
    val phone: String = "",
    val name: String = "",
    val username: String = "",
    val bio: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    val online: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Mirrors the `Message` model in docs/API.md. */
@Serializable
data class Message(
    val id: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String = "",
    val text: String = "",
    @SerialName("client_id") val clientId: String? = null,
    val status: String = MessageStatus.SENT,
    @SerialName("created_at") val createdAt: String = "",
    /** Local-only: optimistic message whose delivery failed. */
    @Transient val failed: Boolean = false,
) {
    val isPending: Boolean get() = status == MessageStatus.SENDING

    /** Blank client ids are treated as absent. */
    val clientIdOrNull: String? get() = clientId?.takeIf { it.isNotBlank() }
}

object MessageStatus {
    /** Local-only status for optimistic messages that the server has not acknowledged yet. */
    const val SENDING = "sending"
    const val SENT = "sent"
    const val DELIVERED = "delivered"
    const val READ = "read"

    fun rank(status: String): Int = when (status) {
        SENDING -> 0
        SENT -> 1
        DELIVERED -> 2
        READ -> 3
        else -> 1
    }
}

/** Mirrors the `Chat` model in docs/API.md. */
@Serializable
data class Chat(
    val id: String,
    val type: String = ChatType.DIRECT,
    val title: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    val members: List<User> = emptyList(),
    @SerialName("last_message") val lastMessage: Message? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("updated_at") val updatedAt: String = "",
) {
    val isGroup: Boolean get() = type == ChatType.GROUP
}

object ChatType {
    const val DIRECT = "direct"
    const val GROUP = "group"
}

val User.displayName: String
    get() = when {
        name.isNotBlank() -> name
        username.isNotBlank() -> "@$username"
        else -> phone
    }

/** The other participant of a direct chat (null for groups). */
fun Chat.peer(myId: String?): User? =
    if (isGroup) null else members.firstOrNull { it.id != myId } ?: members.firstOrNull()

fun Chat.displayTitle(myId: String?): String =
    if (isGroup) {
        title.ifBlank { members.joinToString(", ") { it.displayName } }
    } else {
        peer(myId)?.displayName ?: title
    }

// ---- Request / response bodies ----

@Serializable
data class OtpRequest(val phone: String)

@Serializable
data class OtpResponse(
    val ok: Boolean = false,
    @SerialName("expires_in") val expiresIn: Int = 120,
    @SerialName("dev_code") val devCode: String? = null,
)

@Serializable
data class VerifyRequest(val phone: String, val code: String)

@Serializable
data class VerifyResponse(
    val token: String,
    val user: User,
    @SerialName("is_new") val isNew: Boolean = false,
)

/** PATCH /me. Null fields are omitted (Json.encodeDefaults = false). */
@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val username: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class DirectChatRequest(@SerialName("user_id") val userId: String)

@Serializable
data class GroupChatRequest(
    val title: String,
    @SerialName("member_ids") val memberIds: List<String>,
)

@Serializable
data class SendMessageRequest(
    val text: String,
    @SerialName("client_id") val clientId: String,
)

@Serializable
data class OkResponse(val ok: Boolean = false)

// ---- WebSocket payloads ----

@Serializable
data class ReadPayload(
    @SerialName("chat_id") val chatId: String,
    @SerialName("user_id") val userId: String = "",
    @SerialName("read_at") val readAt: String? = null,
)

@Serializable
data class TypingPayload(
    @SerialName("chat_id") val chatId: String,
    @SerialName("user_id") val userId: String = "",
    val typing: Boolean = false,
)

@Serializable
data class PresencePayload(
    @SerialName("user_id") val userId: String,
    val online: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null,
)
