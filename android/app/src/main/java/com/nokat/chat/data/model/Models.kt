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
    /** "text" | "image" | "file". Defaults to text for older servers. */
    val kind: String = MessageKind.TEXT,
    val attachment: Attachment? = null,
    /** Local-only: optimistic message whose delivery failed. */
    @Transient val failed: Boolean = false,
    /** Local-only: the picked file behind an optimistic attachment message (kept for preview/retry). */
    @Transient val localFile: LocalFile? = null,
) {
    val isPending: Boolean get() = status == MessageStatus.SENDING

    val isImage: Boolean get() = kind == MessageKind.IMAGE
    val isFile: Boolean get() = kind == MessageKind.FILE

    /** Blank client ids are treated as absent. */
    val clientIdOrNull: String? get() = clientId?.takeIf { it.isNotBlank() }
}

object MessageKind {
    const val TEXT = "text"
    const val IMAGE = "image"
    const val FILE = "file"
}

/** Mirrors the `Attachment` model in docs/API.md (also the response of POST /uploads). */
@Serializable
data class Attachment(
    val id: String,
    /** Root-relative, e.g. `/api/v1/files/x.jpg`; resolve with [com.nokat.chat.data.remote.FileUrls]. */
    val url: String = "",
    val name: String = "",
    val size: Long = 0,
    val mime: String = "",
    val kind: String = MessageKind.FILE,
    val width: Int = 0,
    val height: Int = 0,
)

/** A file picked on the device, described via ContentResolver (local only, never serialized). */
data class LocalFile(
    /** `content://` Uri as a string. */
    val uri: String,
    val name: String,
    /** Bytes, or -1 when the provider does not report a size. */
    val size: Long,
    val mime: String,
    val isImage: Boolean,
    val width: Int,
    val height: Int,
)

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

/** `attachment_id` is omitted when null (Json.encodeDefaults = false). */
@Serializable
data class SendMessageRequest(
    val text: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("attachment_id") val attachmentId: String? = null,
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
