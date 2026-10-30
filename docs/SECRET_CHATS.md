# Nokat Chat — Secret chats (end-to-end encryption) v1

Secret chats are 1-to-1 conversations whose content (text, photos, files, captions) is encrypted on the
sender's device and can only be decrypted on the two participating devices. The server stores and relays
opaque ciphertext only. Model: like Telegram secret chats — a secret chat is bound to **one device on each
side**; other devices of the same users see that the chat exists but cannot read it.

What the server still sees (metadata): who has a secret chat with whom, message timestamps and sizes,
read/typing events. It never sees text, file names, file contents or captions.

## Cryptography (MUST be implemented bit-exactly on every client)

| Item | Choice |
|---|---|
| Key agreement | ECDH on **P-256** (secp256r1). A **fresh key pair per secret chat** per side (never reused across chats). |
| Public key encoding | Uncompressed point `0x04‖X‖Y` (65 bytes), **standard base64** with padding. (WebCrypto `exportKey("raw")`.) |
| Shared secret | ECDH output = 32-byte X coordinate (WebCrypto `deriveBits(…, 256)`, JCA `KeyAgreement("ECDH")`). |
| Chat key | `HKDF-SHA256(ikm = shared secret, salt = UTF-8(chat_id), info = UTF-8("nokat-secret-chat-v1"), L = 32)` → AES-256 key. |
| Message cipher | **AES-256-GCM**, 12-byte random IV, 128-bit tag, **AAD = UTF-8(chat_id + "\|" + sender_id)**. |
| Wire format | `ciphertext = base64( IV(12) ‖ GCM output (ct ‖ tag) )` — standard base64 with padding. |
| Fingerprint | `SHA-256(initiator_key_raw65 ‖ peer_key_raw65)`; display the first 16 bytes as **uppercase hex in 8 groups of 4** separated by spaces, e.g. `9F2A 03BC …`. Both users compare it (in person / by call) to rule out a man-in-the-middle. |

Private keys never leave the device and are deleted when the secret chat is deleted or the user logs out.
Web: non-extractable `CryptoKey` in IndexedDB. Android: private key wrapped with an Android Keystore AES-GCM key.

### Encrypted payload (the plaintext inside `ciphertext`)

UTF-8 JSON:

```jsonc
{
  "v": 1,
  "kind": "text" | "image" | "file",
  "text": "Salam!",                 // message text or caption, may be ""
  "file": {                          // only for image/file
    "upload_id": "uuid",            // Attachment.id returned by POST /uploads (the encrypted blob)
    "url": "/api/v1/files/….bin",   // Attachment.url of the encrypted blob (root-relative)
    "key": "<base64 32 bytes>",     // random AES-256 key for this file only
    "name": "photo.jpg",
    "mime": "image/jpeg",
    "size": 123456,                  // size of the ORIGINAL (plaintext) file
    "width": 1280, "height": 960     // 0 for non-images
  }
}
```

**File encryption:** `blob = IV(12) ‖ AES-256-GCM(file bytes)` with the random file key, **no AAD**. Upload the
blob to `POST /uploads` with file name `"encrypted.bin"`; the server therefore sees an opaque
`application/octet-stream`. Send the message with `attachment_id` = that upload id so the server can link it.
To display: download `url` (public, unguessable), split IV, decrypt with `file.key`, show from memory.

Test vectors: [`secret-chat-test-vectors.json`](secret-chat-test-vectors.json) (fixed keys + fixed IV) — every
client implementation must reproduce `chat_key_hex`, `ciphertext` and `fingerprint` exactly.

## API

Chat gains `type: "secret"` and a `secret` object (null for other chats):

```jsonc
"secret": {
  "state": "pending" | "active",
  "initiator_id": "uuid",        // user who started it
  "initiator_key": "<base64 65>",
  "peer_key": "<base64 65>" | ""  // "" while pending
}
```

Message gains `"ciphertext": "<base64>"` (string, "" for normal messages). In secret chats every message has
`kind: "encrypted"`, `text: ""`, `attachment` set if a blob was linked (its metadata is meaningless —
use the decrypted `file` object), and the content in `ciphertext`.

| Endpoint | Description |
|---|---|
| `POST /chats/secret` `{"user_id", "public_key"}` | Start a new secret chat (always a new chat; several may exist with the same user). → `201 Chat` with `state: "pending"`. The peer's devices receive `chat.new`. |
| `POST /chats/:id/secret/accept` `{"public_key"}` | Only the non-initiator, only while pending; the first device to accept wins (`409` afterwards). → `Chat` (`active`). Both users receive `chat.updated`. |
| `DELETE /chats/:id` | Secret chats only: deletes the chat and all its messages/blobs **for both sides**. Members receive `chat.deleted` `{"chat_id"}`. |
| `POST /chats/:id/messages` `{"ciphertext", "client_id", "attachment_id"?}` | In secret chats `text` must be empty and `ciphertext` required (max 64 KB); chat must be `active`. In normal chats `ciphertext` is rejected. |

WebSocket `message.send` accepts `ciphertext` too. New server → client events: `chat.updated` (`Chat`),
`chat.deleted` (`{"chat_id"}`). Typing and read receipts work as usual.

Client UX rules:
- A device that holds the private key for a chat can read it. Others show "🔒 Bu gizlin söhbet başga enjamda" and cannot send.
- Pending chat: initiator sees "Garaşylýar…"; the peer sees an invitation with **Kabul et**.
- Chat list preview: decrypt locally when possible, otherwise "🔒 Şifrlenen hat". Never send plaintext anywhere.
- Secret chats are marked with a lock icon and a green/teal accent; header shows the fingerprint on tap.
- Never log plaintext, never put it in notifications, never cache it unencrypted on disk.
