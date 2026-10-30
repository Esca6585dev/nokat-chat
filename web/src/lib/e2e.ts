/**
 * Browser-side secret-chat key management (docs/SECRET_CHATS.md).
 *
 * - One P-256 key pair per secret chat; the private key is a NON-extractable CryptoKey stored in
 *   IndexedDB (db "nokat-e2e", store "keys", key = chat_id → {privateKey, publicKeyB64}).
 * - Derived AES-GCM chat keys are cached in memory only.
 * - Plaintext and keys are never logged or written anywhere else.
 */
import {
  decryptFile,
  decryptMessage,
  deriveChatKey,
  encryptFile,
  encryptMessage,
  fingerprint,
  generateChatKeyPair,
  type ChatKeyPair,
} from "./e2e-core";
import type { Chat, ID } from "@/types";

export {
  decryptFile,
  decryptMessage,
  encryptFile,
  encryptMessage,
  fingerprint,
  generateChatKeyPair,
  type ChatKeyPair,
};
export { encodePayload, parsePayload, type SecretFile, type SecretPayload } from "./e2e-core";

const DB_NAME = "nokat-e2e";
const STORE = "keys";

interface StoredKey {
  privateKey: CryptoKey;
  publicKeyB64: string;
}

let dbPromise: Promise<IDBDatabase> | null = null;

function openDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise<IDBDatabase>((resolve, reject) => {
    if (typeof indexedDB === "undefined") {
      reject(new Error("IndexedDB unavailable"));
      return;
    }
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => {
      if (!req.result.objectStoreNames.contains(STORE)) req.result.createObjectStore(STORE);
    };
    req.onsuccess = () => {
      const db = req.result;
      db.onversionchange = () => {
        db.close();
        dbPromise = null;
      };
      resolve(db);
    };
    req.onerror = () => reject(req.error);
    req.onblocked = () => reject(new Error("IndexedDB blocked"));
  });
  dbPromise.catch(() => {
    dbPromise = null;
  });
  return dbPromise;
}

function tx<T>(mode: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  return openDb().then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const t = db.transaction(STORE, mode);
        const req = fn(t.objectStore(STORE));
        t.oncomplete = () => resolve(req.result);
        t.onerror = () => reject(t.error);
        t.onabort = () => reject(t.error);
      }),
  );
}

export async function saveChatKey(chatId: ID, pair: ChatKeyPair): Promise<void> {
  const rec: StoredKey = { privateKey: pair.privateKey, publicKeyB64: pair.publicKeyB64 };
  await tx("readwrite", (s) => s.put(rec, chatId));
}

export async function loadChatKey(chatId: ID): Promise<StoredKey | null> {
  try {
    const rec = await tx<StoredKey | undefined>("readonly", (s) => s.get(chatId) as IDBRequest<StoredKey | undefined>);
    return rec && rec.privateKey && rec.publicKeyB64 ? rec : null;
  } catch {
    return null;
  }
}

export async function deleteChatKey(chatId: ID): Promise<void> {
  forgetChat(chatId);
  try {
    await tx("readwrite", (s) => s.delete(chatId));
  } catch {
    /* ignore */
  }
}

/** Logout: drop every private key on this device. */
export async function wipeAllKeys(): Promise<void> {
  chatKeys.clear();
  if (typeof indexedDB === "undefined") return;
  try {
    const db = await dbPromise?.catch(() => null);
    db?.close();
  } catch {
    /* ignore */
  }
  dbPromise = null;
  await new Promise<void>((resolve) => {
    const req = indexedDB.deleteDatabase(DB_NAME);
    req.onsuccess = req.onerror = req.onblocked = () => resolve();
  });
}

// ---- per-chat derived key cache -------------------------------------------------

const chatKeys = new Map<string, Promise<CryptoKey | null>>();

export function forgetChat(chatId: ID) {
  for (const k of Array.from(chatKeys.keys())) if (k.startsWith(`${chatId}:`)) chatKeys.delete(k);
}

/** My public key in this chat according to the server (what this device must hold to read it). */
export function myPublicKey(chat: Chat, meId: ID): string {
  const s = chat.secret;
  if (!s) return "";
  return s.initiator_id === meId ? s.initiator_key : s.peer_key;
}

export function peerPublicKey(chat: Chat, meId: ID): string {
  const s = chat.secret;
  if (!s) return "";
  return s.initiator_id === meId ? s.peer_key : s.initiator_key;
}

/** Does this device hold the private key that matches my side of the chat? */
export async function hasMatchingKey(chat: Chat, meId: ID): Promise<boolean> {
  const mine = myPublicKey(chat, meId);
  if (!mine) return false;
  const rec = await loadChatKey(chat.id);
  return !!rec && rec.publicKeyB64 === mine;
}

/** AES-GCM chat key, or null when the chat isn't active or this device has no matching key. */
export function getChatKey(chat: Chat, meId: ID): Promise<CryptoKey | null> {
  const s = chat.secret;
  if (!s || s.state !== "active" || !s.initiator_key || !s.peer_key) return Promise.resolve(null);
  const cacheKey = `${chat.id}:${s.initiator_key}:${s.peer_key}:${meId}`;
  let p = chatKeys.get(cacheKey);
  if (!p) {
    p = (async () => {
      const rec = await loadChatKey(chat.id);
      if (!rec || rec.publicKeyB64 !== myPublicKey(chat, meId)) return null;
      try {
        return await deriveChatKey(rec.privateKey, peerPublicKey(chat, meId), chat.id);
      } catch {
        return null;
      }
    })();
    chatKeys.set(cacheKey, p);
    // Don't cache negative results forever (key may be saved a moment later).
    p.then((k) => {
      if (!k) chatKeys.delete(cacheKey);
    });
  }
  return p;
}

export async function chatFingerprint(chat: Chat): Promise<string | null> {
  const s = chat.secret;
  if (!s || !s.initiator_key || !s.peer_key) return null;
  try {
    return await fingerprint(s.initiator_key, s.peer_key);
  } catch {
    return null;
  }
}

// ---- encrypted media (memory-only cache) ----------------------------------------

const blobCache = new Map<string, Promise<Blob>>();

/** Download an encrypted blob, decrypt it with the per-file key and return a typed Blob (cached in memory). */
export function fetchDecryptedBlob(url: string, keyB64: string, mime: string): Promise<Blob> {
  const cacheKey = `${url}#${keyB64}`;
  let p = blobCache.get(cacheKey);
  if (!p) {
    p = (async () => {
      const res = await fetch(url);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const enc = new Uint8Array(await res.arrayBuffer());
      const plain = await decryptFile(enc, keyB64);
      return new Blob([plain], { type: mime || "application/octet-stream" });
    })();
    blobCache.set(cacheKey, p);
    p.catch(() => blobCache.delete(cacheKey));
  }
  return p;
}

/** Seed the cache with a file we just sent so we never re-download our own upload. */
export function primeDecryptedBlob(url: string, keyB64: string, blob: Blob) {
  blobCache.set(`${url}#${keyB64}`, Promise.resolve(blob));
}

export function clearMediaCache() {
  blobCache.clear();
}
