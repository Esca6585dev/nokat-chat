/**
 * Nokat secret chats v1 — pure WebCrypto primitives (docs/SECRET_CHATS.md).
 *
 * This module has NO imports and only uses erasable TypeScript syntax, so the exact same
 * code runs in the browser (via Next.js) and in Node 22 (`node --experimental-strip-types`)
 * for the test-vector self-test in scripts/e2e-vectors.test.mjs.
 *
 * Never log plaintext or key material from here.
 */

export type Bytes = Uint8Array<ArrayBuffer>;

export const HKDF_INFO = "nokat-secret-chat-v1";
const EC_PARAMS: EcKeyImportParams = { name: "ECDH", namedCurve: "P-256" };
const IV_LEN = 12;

function subtle(): SubtleCrypto {
  const c = globalThis.crypto;
  if (!c || !c.subtle) throw new Error("WebCrypto unavailable (secure context required)");
  return c.subtle;
}

// ---- encoding helpers -------------------------------------------------------

export function utf8(s: string): Bytes {
  return new TextEncoder().encode(s) as Bytes;
}

export function fromUtf8(b: Uint8Array): string {
  return new TextDecoder("utf-8", { fatal: true }).decode(b);
}

export function toBase64(bytes: Uint8Array): string {
  let bin = "";
  const CHUNK = 0x8000;
  for (let i = 0; i < bytes.length; i += CHUNK) {
    bin += String.fromCharCode.apply(null, Array.from(bytes.subarray(i, i + CHUNK)));
  }
  return btoa(bin);
}

export function fromBase64(b64: string): Bytes {
  const bin = atob(b64.trim());
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export function toHex(bytes: Uint8Array): string {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

export function fromHex(hex: string): Bytes {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  return out;
}

export function randomBytes(n: number): Bytes {
  const b = new Uint8Array(n);
  globalThis.crypto.getRandomValues(b);
  return b;
}

function concat(a: Uint8Array, b: Uint8Array): Bytes {
  const out = new Uint8Array(a.length + b.length);
  out.set(a, 0);
  out.set(b, a.length);
  return out;
}

// ---- keys -------------------------------------------------------------------

export interface ChatKeyPair {
  /** Non-extractable ECDH private key (only usable for deriveBits). */
  privateKey: CryptoKey;
  /** Uncompressed P-256 point (65 bytes), standard base64. */
  publicKeyB64: string;
}

/** Fresh P-256 key pair; the private key is NON-extractable (extractable flag applies to it). */
export async function generateChatKeyPair(): Promise<ChatKeyPair> {
  const pair = (await subtle().generateKey(EC_PARAMS, false, ["deriveBits"])) as CryptoKeyPair;
  const raw = new Uint8Array(await subtle().exportKey("raw", pair.publicKey));
  return { privateKey: pair.privateKey, publicKeyB64: toBase64(raw) };
}

export async function importPublicKey(b64: string): Promise<CryptoKey> {
  const raw = fromBase64(b64);
  if (raw.length !== 65 || raw[0] !== 0x04) throw new Error("invalid public key");
  return subtle().importKey("raw", raw, EC_PARAMS, true, []);
}

/** For tests / interop: import a PKCS#8 P-256 private key. */
export async function importPrivateKeyPkcs8(b64: string, extractable = false): Promise<CryptoKey> {
  return subtle().importKey("pkcs8", fromBase64(b64), EC_PARAMS, extractable, ["deriveBits"]);
}

/** Raw ECDH shared secret (32-byte X coordinate). */
export async function deriveSharedSecret(privateKey: CryptoKey, peerPublicB64: string): Promise<Bytes> {
  const pub = await importPublicKey(peerPublicB64);
  const bits = await subtle().deriveBits({ name: "ECDH", public: pub }, privateKey, 256);
  return new Uint8Array(bits);
}

/**
 * Chat key = HKDF-SHA256(ikm = ECDH secret, salt = UTF-8(chat_id), info = "nokat-secret-chat-v1", L = 32)
 * as an AES-256-GCM key. `extractable` is only meant for tests.
 */
export async function deriveChatKey(
  privateKey: CryptoKey,
  peerPublicB64: string,
  chatId: string,
  extractable = false,
): Promise<CryptoKey> {
  const secret = await deriveSharedSecret(privateKey, peerPublicB64);
  const ikm = await subtle().importKey("raw", secret, "HKDF", false, ["deriveKey"]);
  secret.fill(0);
  return subtle().deriveKey(
    { name: "HKDF", hash: "SHA-256", salt: utf8(chatId), info: utf8(HKDF_INFO) },
    ikm,
    { name: "AES-GCM", length: 256 },
    extractable,
    ["encrypt", "decrypt"],
  );
}

// ---- messages ---------------------------------------------------------------

function aad(chatId: string, senderId: string): Bytes {
  return utf8(`${chatId}|${senderId}`);
}

/** base64( IV(12) ‖ AES-256-GCM(plaintext, AAD = chat_id|sender_id) ). `iv` override is for tests only. */
export async function encryptMessage(
  key: CryptoKey,
  plaintext: string,
  chatId: string,
  senderId: string,
  iv: Bytes = randomBytes(IV_LEN),
): Promise<string> {
  const ct = await subtle().encrypt(
    { name: "AES-GCM", iv, additionalData: aad(chatId, senderId), tagLength: 128 },
    key,
    utf8(plaintext),
  );
  return toBase64(concat(iv, new Uint8Array(ct)));
}

export async function decryptMessage(key: CryptoKey, ciphertextB64: string, chatId: string, senderId: string): Promise<string> {
  const data = fromBase64(ciphertextB64);
  if (data.length < IV_LEN + 16) throw new Error("ciphertext too short");
  const pt = await subtle().decrypt(
    { name: "AES-GCM", iv: data.slice(0, IV_LEN), additionalData: aad(chatId, senderId), tagLength: 128 },
    key,
    data.slice(IV_LEN),
  );
  return fromUtf8(new Uint8Array(pt));
}

// ---- files ------------------------------------------------------------------

export interface EncryptedFile {
  /** IV(12) ‖ AES-256-GCM(file bytes), no AAD */
  blob: Bytes;
  /** random 32-byte file key, base64 */
  keyB64: string;
}

export async function encryptFile(
  bytes: Uint8Array,
  keyBytes: Bytes = randomBytes(32),
  iv: Bytes = randomBytes(IV_LEN),
): Promise<EncryptedFile> {
  const key = await subtle().importKey("raw", keyBytes, { name: "AES-GCM" }, false, ["encrypt"]);
  const ct = await subtle().encrypt({ name: "AES-GCM", iv, tagLength: 128 }, key, bytes as Bytes);
  return { blob: concat(iv, new Uint8Array(ct)), keyB64: toBase64(keyBytes) };
}

export async function decryptFile(blob: Uint8Array, keyB64: string): Promise<Bytes> {
  if (blob.length < IV_LEN + 16) throw new Error("blob too short");
  const keyBytes = fromBase64(keyB64);
  if (keyBytes.length !== 32) throw new Error("invalid file key");
  const key = await subtle().importKey("raw", keyBytes, { name: "AES-GCM" }, false, ["decrypt"]);
  const pt = await subtle().decrypt(
    { name: "AES-GCM", iv: blob.slice(0, IV_LEN) as Bytes, tagLength: 128 },
    key,
    blob.slice(IV_LEN) as Bytes,
  );
  return new Uint8Array(pt);
}

// ---- fingerprint ------------------------------------------------------------

/** SHA-256(initiator_raw65 ‖ peer_raw65), first 16 bytes as "ABCD EF01 …" (8 groups). */
export async function fingerprint(initiatorKeyB64: string, peerKeyB64: string): Promise<string> {
  const digest = new Uint8Array(
    await subtle().digest("SHA-256", concat(fromBase64(initiatorKeyB64), fromBase64(peerKeyB64))),
  );
  const hex = toHex(digest.subarray(0, 16)).toUpperCase();
  return hex.match(/.{4}/g)!.join(" ");
}

// ---- payload ----------------------------------------------------------------

export interface SecretFile {
  upload_id: string;
  url: string;
  key: string;
  name: string;
  mime: string;
  size: number;
  width: number;
  height: number;
}

export interface SecretPayload {
  v: 1;
  kind: "text" | "image" | "file";
  text: string;
  file?: SecretFile;
}

export function encodePayload(p: SecretPayload): string {
  return JSON.stringify(p);
}

/** Parse + validate a decrypted payload; throws on anything unexpected. */
export function parsePayload(json: string): SecretPayload {
  const raw = JSON.parse(json) as Record<string, unknown>;
  if (!raw || typeof raw !== "object" || raw.v !== 1) throw new Error("unsupported payload");
  const kind = raw.kind;
  if (kind !== "text" && kind !== "image" && kind !== "file") throw new Error("bad kind");
  const text = typeof raw.text === "string" ? raw.text : "";
  if (kind === "text") return { v: 1, kind, text };
  const f = raw.file as Record<string, unknown> | undefined;
  if (!f || typeof f !== "object" || typeof f.url !== "string" || typeof f.key !== "string") {
    throw new Error("missing file");
  }
  return {
    v: 1,
    kind,
    text,
    file: {
      upload_id: typeof f.upload_id === "string" ? f.upload_id : "",
      url: f.url,
      key: f.key,
      name: typeof f.name === "string" ? f.name : "",
      mime: typeof f.mime === "string" ? f.mime : "application/octet-stream",
      size: typeof f.size === "number" ? f.size : 0,
      width: typeof f.width === "number" ? f.width : 0,
      height: typeof f.height === "number" ? f.height : 0,
    },
  };
}
