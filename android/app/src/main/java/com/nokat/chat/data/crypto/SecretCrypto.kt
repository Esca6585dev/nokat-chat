package com.nokat.chat.data.crypto

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secret-chat cryptography, bit-exact with docs/SECRET_CHATS.md (v1).
 *
 * Pure JVM (no android.* imports) and a 1:1 translation of
 * `android/tools/crypto-check/SecretCryptoCheck.java`, which is verified against
 * docs/secret-chat-test-vectors.json. Only JCA algorithms available on Android API 26+ are used:
 * EC key generation (secp256r1), ECDH, HmacSHA256, AES/GCM/NoPadding, SHA-256.
 *
 * Never log the inputs or outputs of these functions.
 */
object SecretCrypto {

    const val HKDF_INFO = "nokat-secret-chat-v1"
    const val IV_LENGTH = 12
    const val TAG_BITS = 128
    const val KEY_LENGTH = 32
    const val RAW_PUBLIC_LENGTH = 65
    private const val COORDINATE_LENGTH = 32
    private const val CURVE = "secp256r1"

    private val random = SecureRandom()

    @Volatile
    private var p256Params: ECParameterSpec? = null

    /** P-256 domain parameters, taken from a generated key (portable across providers). */
    @Synchronized
    private fun p256(): ECParameterSpec =
        p256Params ?: (generateKeyPair().public as ECPublicKey).params.also { p256Params = it }

    /** Fresh P-256 key pair (one per secret chat per side). */
    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(CURVE), random)
        return generator.generateKeyPair()
    }

    fun publicRaw(keyPair: KeyPair): ByteArray = publicRaw(keyPair.public)

    /** Uncompressed point `0x04 ‖ X(32) ‖ Y(32)`, coordinates left-padded to 32 bytes. */
    fun publicRaw(publicKey: PublicKey): ByteArray {
        val w = (publicKey as ECPublicKey).w
        val out = ByteArray(RAW_PUBLIC_LENGTH)
        out[0] = 0x04
        writeFixed(w.affineX, out, 1)
        writeFixed(w.affineY, out, 1 + COORDINATE_LENGTH)
        return out
    }

    /** Writes an unsigned big-endian integer into exactly 32 bytes (drops a sign byte, left-pads short values). */
    internal fun writeFixed(value: BigInteger, out: ByteArray, offset: Int) {
        val bytes = value.toByteArray()
        var start = 0
        var length = bytes.size
        while (length > COORDINATE_LENGTH && bytes[start] == 0.toByte()) {
            start++
            length--
        }
        require(length <= COORDINATE_LENGTH) { "coordinate too large" }
        System.arraycopy(bytes, start, out, offset + COORDINATE_LENGTH - length, length)
    }

    fun publicFromRaw(raw: ByteArray): PublicKey {
        if (raw.size != RAW_PUBLIC_LENGTH || raw[0] != 0x04.toByte()) {
            throw GeneralSecurityException("invalid raw public key")
        }
        val x = BigInteger(1, raw.copyOfRange(1, 1 + COORDINATE_LENGTH))
        val y = BigInteger(1, raw.copyOfRange(1 + COORDINATE_LENGTH, RAW_PUBLIC_LENGTH))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), p256()))
    }

    fun privateFromPkcs8(pkcs8: ByteArray): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))

    /** ECDH: the 32-byte X coordinate of the shared point. */
    fun sharedSecret(privateKey: PrivateKey, peerRaw: ByteArray): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicFromRaw(peerRaw), true)
        return agreement.generateSecret()
    }

    /** RFC 5869 HKDF-SHA256. */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < length) {
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val n = minOf(previous.size, length - written)
            System.arraycopy(previous, 0, out, written, n)
            written += n
            counter++
        }
        return out
    }

    /** `HKDF-SHA256(ECDH(priv, peer), salt = chat_id, info = "nokat-secret-chat-v1", 32)`. */
    fun deriveChatKey(privateKey: PrivateKey, peerRaw: ByteArray, chatId: String): ByteArray {
        val shared = sharedSecret(privateKey, peerRaw)
        try {
            return hkdf(
                shared,
                chatId.toByteArray(StandardCharsets.UTF_8),
                HKDF_INFO.toByteArray(StandardCharsets.UTF_8),
                KEY_LENGTH,
            )
        } finally {
            shared.fill(0)
        }
    }

    private fun aad(chatId: String, senderId: String): ByteArray =
        "$chatId|$senderId".toByteArray(StandardCharsets.UTF_8)

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    /** `IV ‖ AES-256-GCM(plaintext)` with optional AAD. */
    private fun gcmSeal(key: ByteArray, plaintext: ByteArray, aad: ByteArray?, iv: ByteArray?): ByteArray {
        val nonce = iv ?: randomBytes(IV_LENGTH)
        if (nonce.size != IV_LENGTH) throw GeneralSecurityException("IV must be 12 bytes")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        if (aad != null) cipher.updateAAD(aad)
        val sealed = cipher.doFinal(plaintext)
        val out = ByteArray(IV_LENGTH + sealed.size)
        System.arraycopy(nonce, 0, out, 0, IV_LENGTH)
        System.arraycopy(sealed, 0, out, IV_LENGTH, sealed.size)
        return out
    }

    private fun gcmOpen(key: ByteArray, blob: ByteArray, aad: ByteArray?): ByteArray {
        if (blob.size < IV_LENGTH + TAG_BITS / 8) throw GeneralSecurityException("ciphertext too short")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, blob, 0, IV_LENGTH))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(blob, IV_LENGTH, blob.size - IV_LENGTH)
    }

    /** Message wire format: `base64(IV ‖ GCM(ct ‖ tag))`, AAD = `chat_id|sender_id`. [iv] only for tests. */
    fun encryptMessage(
        chatKey: ByteArray,
        chatId: String,
        senderId: String,
        plaintext: String,
        iv: ByteArray? = null,
    ): String {
        val blob = gcmSeal(chatKey, plaintext.toByteArray(StandardCharsets.UTF_8), aad(chatId, senderId), iv)
        return encodeBase64(blob)
    }

    /** Throws [GeneralSecurityException] (or IllegalArgumentException for bad base64) when it cannot be opened. */
    fun decryptMessage(chatKey: ByteArray, chatId: String, senderId: String, ciphertextB64: String): String {
        val blob = decodeBase64(ciphertextB64)
        return String(gcmOpen(chatKey, blob, aad(chatId, senderId)), StandardCharsets.UTF_8)
    }

    fun newFileKey(): ByteArray = randomBytes(KEY_LENGTH)

    /** File blob: `IV ‖ AES-256-GCM(bytes)`, no AAD. */
    fun encryptFile(fileKey: ByteArray, data: ByteArray, iv: ByteArray? = null): ByteArray =
        gcmSeal(fileKey, data, null, iv)

    fun decryptFile(fileKey: ByteArray, blob: ByteArray): ByteArray = gcmOpen(fileKey, blob, null)

    /** `SHA-256(initiator_raw ‖ peer_raw)`, first 16 bytes, uppercase hex in 8 groups of 4. */
    fun fingerprint(initiatorKeyB64: String, peerKeyB64: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(decodeBase64(initiatorKeyB64))
        digest.update(decodeBase64(peerKeyB64))
        val hex = hex(digest.digest().copyOf(16)).uppercase()
        val sb = StringBuilder()
        var i = 0
        while (i < hex.length) {
            if (i > 0) sb.append(' ')
            sb.append(hex, i, i + 4)
            i += 4
        }
        return sb.toString()
    }

    fun hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(digits[v shr 4]).append(digits[v and 0x0F])
        }
        return sb.toString()
    }

    /** Standard base64 with padding (java.util.Base64 is available from API 26). */
    fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decodeBase64(value: String): ByteArray = Base64.getDecoder().decode(value)
}
