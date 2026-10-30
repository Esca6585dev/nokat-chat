// JVM verification of the Nokat secret-chat crypto (docs/SECRET_CHATS.md).
//
// This class is a 1:1 Java twin of
// app/src/main/java/com/nokat/chat/data/crypto/SecretCrypto.kt (same methods, same steps),
// so running it validates the algorithm the app uses without an Android SDK or kotlinc.
//
// Run (Java 11+):
//   cd android/tools/crypto-check
//   java SecretCryptoCheck.java ../../../docs/secret-chat-test-vectors.json
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class SecretCryptoCheck {

    // ---------------------------------------------------------------- algorithm (mirrors SecretCrypto.kt)

    static final String HKDF_INFO = "nokat-secret-chat-v1";
    static final int IV_LENGTH = 12;
    static final int TAG_BITS = 128;
    static final int KEY_LENGTH = 32;
    static final int COORDINATE_LENGTH = 32;
    static final int RAW_PUBLIC_LENGTH = 65;
    static final String CURVE = "secp256r1";

    static final SecureRandom RANDOM = new SecureRandom();
    private static ECParameterSpec p256Params;

    /** P-256 domain parameters, taken from a generated key (portable across providers). */
    static synchronized ECParameterSpec p256() throws GeneralSecurityException {
        if (p256Params == null) {
            p256Params = ((ECPublicKey) generateKeyPair().getPublic()).getParams();
        }
        return p256Params;
    }

    static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(CURVE), RANDOM);
        return generator.generateKeyPair();
    }

    /** Uncompressed point 0x04 || X(32) || Y(32), coordinates left-padded to 32 bytes. */
    static byte[] publicRaw(PublicKey publicKey) {
        ECPoint w = ((ECPublicKey) publicKey).getW();
        byte[] out = new byte[RAW_PUBLIC_LENGTH];
        out[0] = 0x04;
        writeFixed(w.getAffineX(), out, 1);
        writeFixed(w.getAffineY(), out, 1 + COORDINATE_LENGTH);
        return out;
    }

    /** Writes an unsigned big-endian integer into exactly 32 bytes (drops a sign byte, left-pads short values). */
    static void writeFixed(BigInteger value, byte[] out, int offset) {
        byte[] bytes = value.toByteArray();
        int start = 0;
        int length = bytes.length;
        while (length > COORDINATE_LENGTH && bytes[start] == 0) {
            start++;
            length--;
        }
        if (length > COORDINATE_LENGTH) throw new IllegalArgumentException("coordinate too large");
        System.arraycopy(bytes, start, out, offset + COORDINATE_LENGTH - length, length);
    }

    static PublicKey publicFromRaw(byte[] raw) throws GeneralSecurityException {
        if (raw.length != RAW_PUBLIC_LENGTH || raw[0] != 0x04) {
            throw new GeneralSecurityException("invalid raw public key");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 1 + COORDINATE_LENGTH));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(raw, 1 + COORDINATE_LENGTH, RAW_PUBLIC_LENGTH));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), p256()));
    }

    static PrivateKey privateFromPkcs8(byte[] pkcs8) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    /** ECDH: the 32-byte X coordinate of the shared point. */
    static byte[] sharedSecret(PrivateKey privateKey, byte[] peerRaw) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(publicFromRaw(peerRaw), true);
        return agreement.generateSecret();
    }

    /** RFC 5869 HKDF-SHA256. */
    static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] out = new byte[length];
        byte[] previous = new byte[0];
        int written = 0;
        int counter = 1;
        while (written < length) {
            mac.update(previous);
            mac.update(info);
            mac.update((byte) counter);
            previous = mac.doFinal();
            int n = Math.min(previous.length, length - written);
            System.arraycopy(previous, 0, out, written, n);
            written += n;
            counter++;
        }
        return out;
    }

    static byte[] deriveChatKey(PrivateKey privateKey, byte[] peerRaw, String chatId) throws GeneralSecurityException {
        byte[] shared = sharedSecret(privateKey, peerRaw);
        try {
            return hkdf(
                shared,
                chatId.getBytes(StandardCharsets.UTF_8),
                HKDF_INFO.getBytes(StandardCharsets.UTF_8),
                KEY_LENGTH);
        } finally {
            Arrays.fill(shared, (byte) 0);
        }
    }

    static byte[] aad(String chatId, String senderId) {
        return (chatId + "|" + senderId).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    /** IV || AES-256-GCM(plaintext) with optional AAD. */
    static byte[] gcmSeal(byte[] key, byte[] plaintext, byte[] aad, byte[] iv) throws GeneralSecurityException {
        byte[] nonce = iv != null ? iv : randomBytes(IV_LENGTH);
        if (nonce.length != IV_LENGTH) throw new GeneralSecurityException("IV must be 12 bytes");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        if (aad != null) cipher.updateAAD(aad);
        byte[] sealed = cipher.doFinal(plaintext);
        byte[] out = new byte[IV_LENGTH + sealed.length];
        System.arraycopy(nonce, 0, out, 0, IV_LENGTH);
        System.arraycopy(sealed, 0, out, IV_LENGTH, sealed.length);
        return out;
    }

    static byte[] gcmOpen(byte[] key, byte[] blob, byte[] aad) throws GeneralSecurityException {
        if (blob.length < IV_LENGTH + TAG_BITS / 8) throw new GeneralSecurityException("ciphertext too short");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, blob, 0, IV_LENGTH));
        if (aad != null) cipher.updateAAD(aad);
        return cipher.doFinal(blob, IV_LENGTH, blob.length - IV_LENGTH);
    }

    static String encryptMessage(byte[] chatKey, String chatId, String senderId, String plaintext, byte[] iv)
            throws GeneralSecurityException {
        byte[] blob = gcmSeal(chatKey, plaintext.getBytes(StandardCharsets.UTF_8), aad(chatId, senderId), iv);
        return Base64.getEncoder().encodeToString(blob);
    }

    static String decryptMessage(byte[] chatKey, String chatId, String senderId, String ciphertextB64)
            throws GeneralSecurityException {
        byte[] blob = Base64.getDecoder().decode(ciphertextB64);
        return new String(gcmOpen(chatKey, blob, aad(chatId, senderId)), StandardCharsets.UTF_8);
    }

    static byte[] newFileKey() {
        return randomBytes(KEY_LENGTH);
    }

    static byte[] encryptFile(byte[] fileKey, byte[] data, byte[] iv) throws GeneralSecurityException {
        return gcmSeal(fileKey, data, null, iv);
    }

    static byte[] decryptFile(byte[] fileKey, byte[] blob) throws GeneralSecurityException {
        return gcmOpen(fileKey, blob, null);
    }

    /** SHA-256(initiator_raw || peer_raw), first 16 bytes, uppercase hex in 8 groups of 4. */
    static String fingerprint(String initiatorKeyB64, String peerKeyB64) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Base64.getDecoder().decode(initiatorKeyB64));
        digest.update(Base64.getDecoder().decode(peerKeyB64));
        String hex = hex(Arrays.copyOf(digest.digest(), 16)).toUpperCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 4) {
            if (i > 0) sb.append(' ');
            sb.append(hex, i, i + 4);
        }
        return sb.toString();
    }

    static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(digits[(b >> 4) & 0xF]).append(digits[b & 0xF]);
        }
        return sb.toString();
    }

    static byte[] unhex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    // ---------------------------------------------------------------- test harness

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "OK    " : "FAIL  ") + name);
        if (!ok) failures++;
    }

    private static String field(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        if (!m.find()) throw new IllegalArgumentException("missing " + key);
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String section(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        int open = json.indexOf('{', i);
        int depth = 0;
        for (int j = open; j < json.length(); j++) {
            char c = json.charAt(j);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return json.substring(open, j + 1);
        }
        throw new IllegalArgumentException("bad section " + key);
    }

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "../../../docs/secret-chat-test-vectors.json";
        String json = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        String initiator = section(json, "initiator");
        String peer = section(json, "peer");
        String file = section(json, "file");
        // Top-level fields: strip nested sections so names like "iv_hex" resolve to the top-level ones.
        String top = json.replace(initiator, "{}").replace(peer, "{}").replace(file, "{}");

        Base64.Decoder b64 = Base64.getDecoder();
        String chatId = field(top, "chat_id");
        String senderId = field(top, "sender_id");
        byte[] initiatorRaw = b64.decode(field(initiator, "public_raw"));
        byte[] peerRaw = b64.decode(field(peer, "public_raw"));
        PrivateKey initiatorPriv = privateFromPkcs8(b64.decode(field(initiator, "private_pkcs8")));
        PrivateKey peerPriv = privateFromPkcs8(b64.decode(field(peer, "private_pkcs8")));

        check("raw public key round-trip (initiator)", Arrays.equals(publicRaw(publicFromRaw(initiatorRaw)), initiatorRaw));
        check("raw public key round-trip (peer)", Arrays.equals(publicRaw(publicFromRaw(peerRaw)), peerRaw));

        String expectedShared = field(top, "shared_secret_hex");
        check("shared secret (initiator side)", hex(sharedSecret(initiatorPriv, peerRaw)).equals(expectedShared));
        check("shared secret (peer side)", hex(sharedSecret(peerPriv, initiatorRaw)).equals(expectedShared));

        byte[] keyA = deriveChatKey(initiatorPriv, peerRaw, chatId);
        byte[] keyB = deriveChatKey(peerPriv, initiatorRaw, chatId);
        String expectedKey = field(top, "chat_key_hex");
        check("chat key (initiator side)", hex(keyA).equals(expectedKey));
        check("chat key (peer side)", hex(keyB).equals(expectedKey));

        String plaintext = field(top, "plaintext_utf8");
        String ciphertext = field(top, "ciphertext");
        check("decrypt vector ciphertext (peer key)", decryptMessage(keyB, chatId, senderId, ciphertext).equals(plaintext));
        check("re-encrypt with fixed IV == vector ciphertext",
            encryptMessage(keyA, chatId, senderId, plaintext, unhex(field(top, "iv_hex"))).equals(ciphertext));

        boolean wrongAadRejected;
        try {
            decryptMessage(keyB, chatId, "someone-else", ciphertext);
            wrongAadRejected = false;
        } catch (GeneralSecurityException e) {
            wrongAadRejected = true;
        }
        check("wrong sender (AAD) is rejected", wrongAadRejected);

        String fp = fingerprint(field(initiator, "public_raw"), field(peer, "public_raw"));
        check("fingerprint == \"" + field(top, "fingerprint") + "\"", fp.equals(field(top, "fingerprint")));

        byte[] fileKey = b64.decode(field(file, "key_b64"));
        byte[] blob = b64.decode(field(file, "blob_b64"));
        byte[] filePlain = field(file, "plaintext_utf8").getBytes(StandardCharsets.UTF_8);
        check("decrypt file blob", Arrays.equals(decryptFile(fileKey, blob), filePlain));
        check("re-encrypt file with fixed IV == blob",
            Arrays.equals(encryptFile(fileKey, filePlain, unhex(field(file, "iv_hex"))), blob));

        // Fresh keys: ECDH symmetry, raw export padding over many keys, random-IV round-trips.
        boolean allOk = true;
        for (int i = 0; i < 300; i++) {
            KeyPair a = generateKeyPair();
            KeyPair b = generateKeyPair();
            byte[] rawA = publicRaw(a.getPublic());
            byte[] rawB = publicRaw(b.getPublic());
            allOk &= rawA.length == RAW_PUBLIC_LENGTH && rawA[0] == 0x04;
            allOk &= Arrays.equals(publicRaw(publicFromRaw(rawA)), rawA);
            byte[] k1 = deriveChatKey(a.getPrivate(), rawB, chatId);
            byte[] k2 = deriveChatKey(b.getPrivate(), rawA, chatId);
            allOk &= Arrays.equals(k1, k2);
            String ct = encryptMessage(k1, chatId, senderId, "salam " + i, null);
            allOk &= decryptMessage(k2, chatId, senderId, ct).equals("salam " + i);
            byte[] fk = newFileKey();
            byte[] data = randomBytes(i * 7);
            allOk &= Arrays.equals(decryptFile(fk, encryptFile(fk, data, null)), data);
        }
        check("300 fresh key pairs: export/import, ECDH symmetry, random-IV round-trips", allOk);

        // Padding edge cases for writeFixed.
        byte[] buf = new byte[32];
        writeFixed(BigInteger.ONE, buf, 0);
        boolean padOk = buf[31] == 1 && buf[0] == 0;
        writeFixed(BigInteger.ONE.shiftLeft(255), buf, 0); // toByteArray() has a leading 0x00 sign byte
        padOk &= (buf[0] & 0xFF) == 0x80;
        check("coordinate padding (short value / sign byte)", padOk);

        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILURE(S)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
