package com.nokat.chat.data.repo

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.core.content.FileProvider
import com.nokat.chat.data.crypto.SecretCrypto
import com.nokat.chat.data.crypto.SecretKeyStore
import com.nokat.chat.data.model.Chat
import com.nokat.chat.data.model.LocalFile
import com.nokat.chat.data.model.Message
import com.nokat.chat.data.model.SecretFile
import com.nokat.chat.data.model.SecretPayload
import com.nokat.chat.data.model.SecretState
import com.nokat.chat.data.remote.FileUrls
import com.nokat.chat.data.session.SessionManager
import com.nokat.chat.util.AttachmentReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** What this device can do with a secret chat. */
enum class SecretAccess {
    /** I started it; waiting for the peer to accept. */
    PENDING_OUTGOING,

    /** The peer started it; I can accept or decline. */
    PENDING_INCOMING,

    /** Active and this device holds the private key. */
    ACTIVE,

    /** Active but the key lives on another device: read-only "locked" view. */
    NO_KEY,
}

/** Result of decrypting one message. */
sealed interface Decrypted {
    data class Ok(val payload: SecretPayload) : Decrypted
    data object Failed : Decrypted
}

class FileTooBigException : IOException("file too big")

/**
 * End-to-end encrypted secret chats (docs/SECRET_CHATS.md): key lifecycle, chat-key derivation,
 * message/file encryption, and decrypted caches. Plaintext and keys live in memory only (the one
 * exception is a decrypted copy written to `cacheDir/secret/` when the user opens a file in another
 * app; that directory is wiped when the chat is closed and on logout). Never log anything from here.
 */
class SecretChatManager(
    context: Context,
    private val keyStore: SecretKeyStore,
    private val chats: ChatRepository,
    private val session: SessionManager,
    private val okHttp: OkHttpClient,
    private val fileUrls: FileUrls,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver

    /** Chat ids this device holds a private key for. */
    private val ownedIds = MutableStateFlow<Set<String>>(emptySet())

    /** Derived AES chat keys (memory only). */
    private val chatKeys = ConcurrentHashMap<String, ByteArray>()

    /** Chats whose stored key does not match the server's key for our side. */
    private val mismatched = ConcurrentHashMap.newKeySet<String>()
    private val deriving = ConcurrentHashMap.newKeySet<String>()

    /** "$chatId/$messageId" → decrypt result (memory only). */
    private val decrypted = ConcurrentHashMap<String, Decrypted>()

    /** Decrypted file bytes by blob URL (memory only, ~32 MB). */
    private val media = object : LruCache<String, ByteArray>(MEDIA_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    private val _version = MutableStateFlow(0)

    /** Bumped whenever key ownership or cached keys change, so UIs re-evaluate secret chats. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val payloadJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val secretDir: File get() = File(appContext.cacheDir, "secret")

    init {
        scope.launch(Dispatchers.IO) {
            val ids = try {
                keyStore.chatIds()
            } catch (e: Exception) {
                emptySet()
            }
            ownedIds.update { it + ids }
            bump()
        }
        scope.launch { chats.removedChats.collect { forget(it) } }
    }

    private fun bump() = _version.update { it + 1 }

    // ---- State ----

    fun access(chat: Chat, myId: String?): SecretAccess? {
        val secret = chat.secret ?: return null
        if (secret.state != SecretState.ACTIVE) {
            return if (secret.initiatorId == myId) SecretAccess.PENDING_OUTGOING else SecretAccess.PENDING_INCOMING
        }
        return if (chat.id in ownedIds.value && chat.id !in mismatched) SecretAccess.ACTIVE else SecretAccess.NO_KEY
    }

    /** Fingerprint for an active chat, `XXXX XXXX …` (8 groups). */
    fun fingerprint(chat: Chat): String? {
        val secret = chat.secret ?: return null
        if (secret.state != SecretState.ACTIVE || secret.peerKey.isEmpty()) return null
        return try {
            SecretCrypto.fingerprint(secret.initiatorKey, secret.peerKey)
        } catch (e: Exception) {
            null
        }
    }

    fun cachedKey(chatId: String): ByteArray? = chatKeys[chatId]

    /** Derives (once) and caches the chat key; null if this device cannot read the chat. */
    suspend fun chatKey(chat: Chat): ByteArray? =
        chatKeys[chat.id] ?: withContext(Dispatchers.IO) { derive(chat) }

    /** Non-suspending trigger for list screens; [version] changes when the key is ready. */
    fun ensureKeyAsync(chat: Chat) {
        if (chatKeys.containsKey(chat.id) || chat.id !in ownedIds.value) return
        if (chat.secret?.state != SecretState.ACTIVE || !deriving.add(chat.id)) return
        scope.launch(Dispatchers.IO) {
            try {
                derive(chat)
            } finally {
                deriving.remove(chat.id)
            }
        }
    }

    private fun derive(chat: Chat): ByteArray? {
        chatKeys[chat.id]?.let { return it }
        val secret = chat.secret ?: return null
        if (secret.state != SecretState.ACTIVE) return null
        val me = session.currentUser?.id ?: return null
        val iAmInitiator = secret.initiatorId == me
        val myKeyB64 = if (iAmInitiator) secret.initiatorKey else secret.peerKey
        val otherKeyB64 = if (iAmInitiator) secret.peerKey else secret.initiatorKey
        if (myKeyB64.isEmpty() || otherKeyB64.isEmpty()) return null
        return try {
            val storedPublic = keyStore.publicRaw(chat.id) ?: return null
            if (!storedPublic.contentEquals(SecretCrypto.decodeBase64(myKeyB64))) {
                // Another device of ours owns this chat.
                if (mismatched.add(chat.id)) bump()
                return null
            }
            val privateKey = keyStore.privateKey(chat.id) ?: return null
            val key = SecretCrypto.deriveChatKey(privateKey, SecretCrypto.decodeBase64(otherKeyB64), chat.id)
            chatKeys[chat.id] = key
            bump()
            key
        } catch (e: Exception) {
            null
        }
    }

    // ---- Lifecycle ----

    /** Generates a fresh key pair, POST /chats/secret, stores the private key under the new chat id. */
    suspend fun startSecretChat(userId: String): Chat = withContext(Dispatchers.IO) {
        val keyPair = SecretCrypto.generateKeyPair()
        val publicKey = SecretCrypto.encodeBase64(SecretCrypto.publicRaw(keyPair))
        val chat = chats.createSecretChat(userId, publicKey)
        keyStore.save(chat.id, keyPair)
        ownedIds.update { it + chat.id }
        bump()
        chat
    }

    /** Peer side: stores a fresh key, then POST accept (rolls the key back if the accept fails). */
    suspend fun acceptSecretChat(chatId: String): Chat = withContext(Dispatchers.IO) {
        val keyPair = SecretCrypto.generateKeyPair()
        keyStore.save(chatId, keyPair)
        ownedIds.update { it + chatId }
        mismatched.remove(chatId)
        bump()
        try {
            chats.acceptSecretChat(chatId, SecretCrypto.encodeBase64(SecretCrypto.publicRaw(keyPair)))
        } catch (e: Exception) {
            keyStore.delete(chatId)
            ownedIds.update { it - chatId }
            bump()
            throw e
        }
    }

    /** DELETE /chats/:id (also used to decline an invitation). Keys are dropped via [ChatRepository.removedChats]. */
    suspend fun deleteSecretChat(chatId: String) {
        chats.deleteChat(chatId)
    }

    /** Chat deleted (by anyone): drop its key, cached key, and decrypted content. */
    fun forget(chatId: String) {
        chatKeys.remove(chatId)?.fill(0)
        mismatched.remove(chatId)
        ownedIds.update { it - chatId }
        val prefix = "$chatId/"
        decrypted.keys.removeAll { it.startsWith(prefix) }
        bump()
        scope.launch(Dispatchers.IO) {
            try {
                keyStore.delete(chatId)
            } catch (e: Exception) {
                // Best effort.
            }
        }
    }

    /** Logout: wipe every key and all plaintext caches. */
    suspend fun wipeAll() {
        chatKeys.values.forEach { it.fill(0) }
        chatKeys.clear()
        mismatched.clear()
        decrypted.clear()
        media.evictAll()
        ownedIds.value = emptySet()
        bump()
        withContext(Dispatchers.IO) {
            try {
                keyStore.deleteAll()
            } catch (e: Exception) {
                // Best effort.
            }
            clearOpenedFiles()
        }
    }

    // ---- Messages ----

    /** Decrypts (and caches) [message]; null when it carries no ciphertext. */
    fun decrypt(chatId: String, key: ByteArray, message: Message): Decrypted? {
        if (message.ciphertext.isEmpty()) return null
        val cacheKey = "$chatId/${message.id}"
        decrypted[cacheKey]?.let { return it }
        val result: Decrypted = try {
            val json = SecretCrypto.decryptMessage(key, chatId, message.senderId, message.ciphertext)
            Decrypted.Ok(payloadJson.decodeFromString(SecretPayload.serializer(), json))
        } catch (e: Exception) {
            Decrypted.Failed
        }
        decrypted[cacheKey] = result
        return result
    }

    /** Payload JSON: `{"v":1,"kind":…,"text":…}` plus `"file"` only for image/file messages. */
    fun encodePayload(payload: SecretPayload): String = buildJsonObject {
        put("v", JsonPrimitive(payload.v))
        put("kind", JsonPrimitive(payload.kind))
        put("text", JsonPrimitive(payload.text))
        val file = payload.file
        if (file != null) put("file", payloadJson.encodeToJsonElement(SecretFile.serializer(), file))
    }.toString()

    fun encryptPayload(chatId: String, key: ByteArray, senderId: String, payload: SecretPayload): String =
        SecretCrypto.encryptMessage(key, chatId, senderId, encodePayload(payload))

    // ---- Files ----

    /**
     * Reads the picked file (≤ 20 MB, in memory), encrypts it with a fresh file key and uploads the
     * blob as `encrypted.bin`. Returns the `file` object for the payload.
     */
    suspend fun encryptAndUpload(file: LocalFile, onProgress: (Float) -> Unit): SecretFile =
        withContext(Dispatchers.IO) {
            if (file.size > AttachmentReader.MAX_UPLOAD_BYTES) throw FileTooBigException()
            val bytes = contentResolver.openInputStream(Uri.parse(file.uri))?.use { input ->
                readLimited(input, AttachmentReader.MAX_UPLOAD_BYTES)
            } ?: throw IOException("cannot open file")
            val fileKey = SecretCrypto.newFileKey()
            val blob = SecretCrypto.encryptFile(fileKey, bytes)
            val uploaded = chats.uploadBytes(blob, ENCRYPTED_FILE_NAME, onProgress)
            media.put(uploaded.url, bytes)
            SecretFile(
                uploadId = uploaded.id,
                url = uploaded.url,
                key = SecretCrypto.encodeBase64(fileKey),
                name = file.name,
                mime = file.mime,
                size = bytes.size.toLong(),
                width = file.width,
                height = file.height,
            )
        }

    private fun readLimited(input: java.io.InputStream, limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n == -1) break
            total += n
            if (total > limit) throw FileTooBigException()
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** Downloads and decrypts a secret file (memory-cached); null on failure. */
    suspend fun loadFile(file: SecretFile): ByteArray? {
        if (file.url.isEmpty() || file.key.isEmpty()) return null
        media.get(file.url)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(fileUrls.resolve(file.url)).build()
                val blob = okHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body?.bytes() ?: throw IOException("empty body")
                }
                val plain = SecretCrypto.decryptFile(SecretCrypto.decodeBase64(file.key), blob)
                media.put(file.url, plain)
                plain
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Writes a decrypted copy to `cacheDir/secret/` so another app can open it via FileProvider.
     * Returns a `content://` Uri, or null on failure. Wiped by [clearOpenedFiles].
     */
    suspend fun exportForViewing(file: SecretFile): Uri? {
        val bytes = loadFile(file) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val folder = File(secretDir, Integer.toHexString(file.url.hashCode())).apply { mkdirs() }
                val out = File(folder, safeFileName(file.name))
                out.writeBytes(bytes)
                FileProvider.getUriForFile(appContext, appContext.packageName + FILE_PROVIDER_SUFFIX, out)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun clearOpenedFiles() {
        try {
            secretDir.deleteRecursively()
        } catch (e: Exception) {
            // Best effort.
        }
    }

    private fun safeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().take(120)
        return cleaned.ifEmpty { "file" }
    }

    companion object {
        const val ENCRYPTED_FILE_NAME = "encrypted.bin"
        const val FILE_PROVIDER_SUFFIX = ".fileprovider"
        private const val MEDIA_CACHE_BYTES = 32 * 1024 * 1024
    }
}
