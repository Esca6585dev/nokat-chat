package com.nokat.chat.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Per-chat secret-chat private keys.
 *
 * Each private key (PKCS#8) is encrypted with a non-exportable AES-256-GCM key that lives in the
 * Android Keystore (alias [WRAP_ALIAS], no user authentication), and stored together with the
 * chat's own public key in app-private SharedPreferences keyed by chat id:
 * `base64(IV ‖ GCM(pkcs8)) + ":" + base64(publicRaw65)`.
 *
 * All methods block (Keystore and disk I/O): call them from Dispatchers.IO.
 */
class SecretKeyStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun save(chatId: String, keyPair: KeyPair) {
        val pkcs8 = keyPair.private.encoded
        try {
            val wrapped = wrap(pkcs8)
            val value = SecretCrypto.encodeBase64(wrapped) + SEPARATOR + SecretCrypto.encodeBase64(SecretCrypto.publicRaw(keyPair))
            if (!prefs.edit().putString(chatId, value).commit()) error("could not persist key")
        } finally {
            pkcs8.fill(0)
        }
    }

    /** Our own 65-byte public key for [chatId], or null when this device has no key for it. */
    fun publicRaw(chatId: String): ByteArray? {
        val value = prefs.getString(chatId, null) ?: return null
        val publicPart = value.substringAfter(SEPARATOR, "")
        if (publicPart.isEmpty()) return null
        return SecretCrypto.decodeBase64(publicPart)
    }

    /** Unwraps the private key for [chatId]; null if missing or unreadable. */
    fun privateKey(chatId: String): PrivateKey? {
        val value = prefs.getString(chatId, null) ?: return null
        val wrapped = SecretCrypto.decodeBase64(value.substringBefore(SEPARATOR))
        val pkcs8 = unwrap(wrapped)
        try {
            return SecretCrypto.privateFromPkcs8(pkcs8)
        } finally {
            pkcs8.fill(0)
        }
    }

    fun chatIds(): Set<String> = prefs.all.keys.toSet()

    @Synchronized
    fun delete(chatId: String) {
        prefs.edit().remove(chatId).commit()
    }

    /** Logout: forget every key and the wrapping key itself. */
    @Synchronized
    fun deleteAll() {
        prefs.edit().clear().commit()
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(WRAP_ALIAS)) keyStore.deleteEntry(WRAP_ALIAS)
        } catch (e: Exception) {
            // Nothing left that it could decrypt anyway.
        }
    }

    // ---- Keystore wrapping ----

    @Synchronized
    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(WRAP_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAP_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    /** `IV(12) ‖ AES-GCM(plain)`; the Keystore picks the random IV. */
    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain)
        return iv + sealed
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, blob, 0, IV_LENGTH))
        return cipher.doFinal(blob, IV_LENGTH, blob.size - IV_LENGTH)
    }

    private companion object {
        const val PREFS_NAME = "nokat_secret_keys"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val WRAP_ALIAS = "nokat_e2e_wrap"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val SEPARATOR = ":"
    }
}
