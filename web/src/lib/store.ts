"use client";

import { create } from "zustand";
import { api, ApiError, MAX_UPLOAD_BYTES, normalizeChat, normalizeMessage, uploadFile, type UploadHandle } from "./api";
import { errorMessage } from "./errors";
import { t } from "./i18n";
import { cacheUser, getCachedUser, getToken } from "./auth";
import { attachmentUrl } from "./config";
import * as e2e from "./e2e";
import { newClientId } from "./format";
import { RealtimeClient, type WsStatus } from "./ws";
import type { Attachment, Chat, ID, Message, MessageStatus, ServerEvent, UIMessage, User } from "@/types";

const PAGE_SIZE = 50;
const TYPING_TTL = 6_000;

export interface ChatMessages {
  items: UIMessage[];
  loaded: boolean;
  loading: boolean;
  loadingOlder: boolean;
  hasMore: boolean;
  error?: string;
}

interface State {
  me: User | null;
  chats: Chat[];
  chatsLoaded: boolean;
  messages: Record<ID, ChatMessages>;
  activeChatId: ID | null;
  /** chatId → set of userIds currently typing */
  typing: Record<ID, ID[]>;
  wsStatus: WsStatus;
  focused: boolean;
  toast: { id: number; text: string } | null;
  lightbox: { url: string; name: string; blob?: Blob } | null;
  /** Secret chats: does THIS device hold the private key for my side? (absent = not checked yet) */
  secretKeys: Record<ID, boolean>;

  showToast: (text: string) => void;
  /** `blob` = decrypted media of a secret chat (lightbox makes its own object URL). */
  openLightbox: (url: string, name: string, blob?: Blob) => void;
  closeLightbox: () => void;

  init: () => void;
  teardown: () => void;
  setMe: (u: User) => void;
  setFocused: (f: boolean) => void;
  loadChats: () => Promise<void>;
  setActive: (id: ID | null) => void;
  loadMessages: (chatId: ID) => Promise<void>;
  loadOlder: (chatId: ID) => Promise<void>;
  send: (chatId: ID, text: string) => Promise<void>;
  /** Optimistically send a photo/file (upload with progress, then POST message with attachment_id). */
  sendAttachment: (chatId: ID, file: File, caption: string, meta?: LocalFileMeta) => void;
  /** Abort an in-flight upload (or discard a failed one) and drop its optimistic bubble. */
  cancelSend: (chatId: ID, clientId: string) => void;
  retry: (chatId: ID, clientId: string) => Promise<void>;
  markRead: (chatId: ID) => void;
  sendTyping: (chatId: ID, typing: boolean) => void;
  upsertChat: (chat: Chat) => void;
  openDirect: (userId: ID) => Promise<Chat>;
  createGroup: (title: string, memberIds: ID[]) => Promise<Chat>;
  handleEvent: (ev: ServerEvent) => void;

  // Secret chats
  startSecretChat: (userId: ID) => Promise<Chat | null>;
  acceptSecret: (chatId: ID) => Promise<void>;
  /** DELETE /chats/:id (decline an invitation or delete a secret chat for both sides). */
  deleteChat: (chatId: ID) => Promise<boolean>;
  /** Remove a chat locally (after chat.deleted): messages, typing, keys. */
  removeChatLocal: (chatId: ID) => void;
}

export interface LocalFileMeta {
  previewUrl?: string;
  width?: number;
  height?: number;
}

const IMAGE_MIMES = new Set(["image/jpeg", "image/png", "image/gif", "image/webp"]);
export const isImageFile = (f: { type: string }) => IMAGE_MIMES.has(f.type);

// ---- helpers ---------------------------------------------------------------

const statusRank: Record<MessageStatus, number> = { sent: 0, delivered: 1, read: 2 };

function maxStatus(a: MessageStatus, b: MessageStatus): MessageStatus {
  return statusRank[a] >= statusRank[b] ? a : b;
}

/** Timestamp in ms; tolerant of differing RFC3339 precisions ("…:00Z" vs "…:00.123Z"). */
function ts(s: string | undefined | null): number {
  if (!s) return 0;
  const n = Date.parse(s);
  return isNaN(n) ? 0 : n;
}

function byTime(a: { created_at: string }, b: { created_at: string }) {
  return ts(a.created_at) - ts(b.created_at);
}

/** Insert or replace a message (dedupe by id, then by client_id), keeping time order. */
function mergeMessage(items: UIMessage[], msg: UIMessage): UIMessage[] {
  let idx = items.findIndex((m) => m.id === msg.id);
  if (idx < 0 && msg.client_id) idx = items.findIndex((m) => m.client_id === msg.client_id);
  if (idx >= 0) {
    const prev = items[idx];
    const next: UIMessage = {
      ...prev,
      ...msg,
      status: maxStatus(prev.status, msg.status),
      local: msg.local,
      upload: msg.upload,
      // keep the local object URL so the image doesn't flicker when the server copy arrives
      localPreview: msg.localPreview ?? prev.localPreview,
    };
    const copy = items.slice();
    copy[idx] = next;
    // Server timestamp may differ from the optimistic one: re-sort only if needed.
    if (next.created_at !== prev.created_at) return copy.sort(byTime);
    return copy;
  }
  const copy = items.concat(msg);
  // Fast path: already in order.
  if (items.length === 0 || ts(items[items.length - 1].created_at) <= ts(msg.created_at)) return copy;
  return copy.sort(byTime);
}

function mergeMany(items: UIMessage[], incoming: Message[]): UIMessage[] {
  let out = items;
  for (const m of incoming) out = mergeMessage(out, m);
  return out;
}

function sortChats(chats: Chat[]): Chat[] {
  return chats.slice().sort((a, b) => ts(b.updated_at) - ts(a.updated_at));
}


const emptyMessages = (): ChatMessages => ({
  items: [],
  loaded: false,
  loading: false,
  loadingOlder: false,
  hasMore: true,
});

// ---- module-level runtime (not part of reactive state) -----------------------

let rt: RealtimeClient | null = null;
const typingTimers = new Map<string, ReturnType<typeof setTimeout>>();
const lastReadSent = new Map<ID, number>();
const pendingChatFetch = new Set<ID>();
let toastSeq = 0;
let toastTimer: ReturnType<typeof setTimeout> | null = null;

/** Optimistic sends in flight (or failed), keyed by client_id. */
interface PendingSend {
  chatId: ID;
  text: string;
  file?: File;
  attachmentId?: ID;
  upload?: UploadHandle;
  cancelled?: boolean;
  /** secret chats: the encrypted blob's upload + its per-file key */
  secretUpload?: Attachment;
  fileKey?: string;
  width?: number;
  height?: number;
}

const ENC_OVERHEAD = 12 + 16; // IV + GCM tag

/** Turn a decrypted payload into the plaintext view of a message (memory only). */
function applyPayload(msg: UIMessage, p: e2e.SecretPayload): UIMessage {
  const f = p.file;
  return {
    ...msg,
    kind: p.kind,
    text: p.text,
    attachment: f
      ? {
          id: f.upload_id,
          url: f.url,
          name: f.name,
          size: f.size,
          mime: f.mime,
          kind: p.kind === "image" ? "image" : "file",
          width: f.width,
          height: f.height,
        }
      : null,
    e2e: "ok",
    secretFile: f ? { url: f.url, key: f.key, mime: f.mime, name: f.name } : undefined,
  };
}

/** Keep an already-decrypted list preview when the server sends the same last message again. */
function keepDecryptedPreview(next: Chat, prev: Chat | undefined): Chat {
  const old = prev?.last_message as UIMessage | null | undefined;
  if (old && old.e2e && next.last_message && next.last_message.id === old.id) {
    return { ...next, last_message: { ...old, status: next.last_message.status } };
  }
  return next;
}
const pending = new Map<string, PendingSend>();
const objectUrls = new Set<string>();

export const useChatStore = create<State>()((set, get) => {
  const patchChat = (chatId: ID, fn: (c: Chat) => Chat) =>
    set((s) => ({ chats: s.chats.map((c) => (c.id === chatId ? fn(c) : c)) }));

  const patchMessages = (chatId: ID, fn: (m: ChatMessages) => ChatMessages) =>
    set((s) => ({ messages: { ...s.messages, [chatId]: fn(s.messages[chatId] ?? emptyMessages()) } }));

  const setTyping = (chatId: ID, userId: ID, typing: boolean) => {
    const key = `${chatId}:${userId}`;
    const timer = typingTimers.get(key);
    if (timer) clearTimeout(timer);
    typingTimers.delete(key);
    set((s) => {
      const cur = s.typing[chatId] ?? [];
      const has = cur.includes(userId);
      if (typing === has) return s;
      const next = typing ? cur.concat(userId) : cur.filter((u) => u !== userId);
      return { typing: { ...s.typing, [chatId]: next } };
    });
    if (typing) {
      typingTimers.set(
        key,
        setTimeout(() => setTyping(chatId, userId, false), TYPING_TTL),
      );
    }
  };

  /** Update chat preview + ordering for a message. */
  const applyToChat = (msg: Message, incrementUnread: boolean) => {
    const s = get();
    const chat = s.chats.find((c) => c.id === msg.chat_id);
    if (!chat) return false;
    const prev = chat.last_message;
    const isNewer =
      !prev ||
      prev.id === msg.id ||
      (!!msg.client_id && prev.client_id === msg.client_id) ||
      ts(prev.created_at) <= ts(msg.created_at);
    const updated: Chat = {
      ...chat,
      last_message: isNewer ? msg : prev,
      updated_at: isNewer && ts(msg.created_at) > ts(chat.updated_at) ? msg.created_at : chat.updated_at,
      unread_count: chat.unread_count + (incrementUnread ? 1 : 0),
    };
    set({ chats: sortChats(s.chats.map((c) => (c.id === chat.id ? updated : c))) });
    return true;
  };

  const fetchChat = async (chatId: ID) => {
    if (pendingChatFetch.has(chatId)) return;
    pendingChatFetch.add(chatId);
    try {
      const chat = await api.chat(chatId);
      get().upsertChat(chat);
    } catch {
      /* ignore */
    } finally {
      pendingChatFetch.delete(chatId);
    }
  };

  /** Decrypt a secret-chat message into its plaintext view; non-encrypted messages pass through. */
  const decryptIncoming = async (msg: UIMessage): Promise<UIMessage> => {
    if (msg.kind !== "encrypted" || !msg.ciphertext) return msg;
    const me = get().me;
    const chat = get().chats.find((c) => c.id === msg.chat_id);
    if (!me || !chat) return { ...msg, e2e: "nokey" };
    const key = await e2e.getChatKey(chat, me.id);
    if (!key) return { ...msg, e2e: "nokey" };
    try {
      const plain = await e2e.decryptMessage(key, msg.ciphertext, chat.id, msg.sender_id);
      return applyPayload(msg, e2e.parsePayload(plain));
    } catch {
      return { ...msg, e2e: "failed" };
    }
  };

  const decryptMany = (msgs: Message[]) => Promise.all(msgs.map((m) => decryptIncoming(m)));

  /** Check key presence for a secret chat and decrypt its list preview / undecrypted messages. */
  const ensureSecret = async (chatId: ID) => {
    const me = get().me;
    const chat = get().chats.find((c) => c.id === chatId);
    if (!me || !chat || chat.type !== "secret") return;
    const has = await e2e.hasMatchingKey(chat, me.id);
    if (get().secretKeys[chatId] !== has) set((s) => ({ secretKeys: { ...s.secretKeys, [chatId]: has } }));
    const last = chat.last_message as UIMessage | null;
    if (last && last.kind === "encrypted" && (!last.e2e || last.e2e === "nokey")) {
      const dec = await decryptIncoming({ ...last, e2e: undefined });
      patchChat(chatId, (c) => (c.last_message && c.last_message.id === last.id ? { ...c, last_message: dec } : c));
    }
    // Messages loaded before the key / peer key was available.
    const items = get().messages[chatId]?.items;
    if (items?.some((m) => m.e2e === "nokey")) {
      const fixed = new Map<ID, UIMessage>();
      for (const m of items) if (m.e2e === "nokey") fixed.set(m.id, await decryptIncoming({ ...m, e2e: undefined }));
      patchMessages(chatId, (x) => ({
        ...x,
        items: x.items.map((m) => (m.e2e === "nokey" && fixed.has(m.id) ? fixed.get(m.id)! : m)),
      }));
    }
  };

  const ensureAllSecrets = () => {
    for (const c of get().chats) if (c.type === "secret") void ensureSecret(c.id);
  };

  /** Apply an incoming (already decrypted if secret) message to state. */
  const processNew = (msg: UIMessage) => {
    const me = get().me;
    const mine = !!me && msg.sender_id === me.id;
    if (get().messages[msg.chat_id]) {
      patchMessages(msg.chat_id, (m) => ({ ...m, items: mergeMessage(m.items, { ...msg, local: undefined }) }));
    }
    setTyping(msg.chat_id, msg.sender_id, false);
    const viewing = isViewing(msg.chat_id);
    const known = applyToChat(msg, !mine && !viewing);
    if (!known) void fetchChat(msg.chat_id);
    if (!mine && viewing) get().markRead(msg.chat_id);
  };

  const setLocal = (chatId: ID, clientId: string, patch: Partial<UIMessage>) =>
    patchMessages(chatId, (m) => ({
      ...m,
      items: m.items.map((x) => (x.client_id === clientId && x.local ? { ...x, ...patch } : x)),
    }));

  /** Upload (if needed) then POST the message; marks the optimistic bubble failed on error. */
  const deliver = async (clientId: string) => {
    const rec = pending.get(clientId);
    if (!rec) return;
    const { chatId } = rec;
    const isSecret = get().chats.find((c) => c.id === chatId)?.type === "secret";
    if (isSecret) return deliverSecret(clientId, rec);
    setLocal(chatId, clientId, { local: "sending", ...(rec.file && !rec.attachmentId ? { upload: { progress: 0 } } : {}) });
    try {
      if (rec.file && !rec.attachmentId) {
        let last = 0;
        const handle = uploadFile(rec.file, (p) => {
          if (p - last >= 0.01 || p === 1) {
            last = p;
            setLocal(chatId, clientId, { upload: { progress: p } });
          }
        });
        rec.upload = handle;
        const att = await handle.promise;
        rec.upload = undefined;
        rec.attachmentId = att.id;
        if (rec.cancelled) return;
        setLocal(chatId, clientId, { attachment: att, upload: undefined });
      }
      if (rec.cancelled) return;
      const saved = await api.sendMessage(chatId, rec.text, clientId, rec.attachmentId);
      pending.delete(clientId);
      const merged: UIMessage = { ...saved, client_id: saved.client_id || clientId, local: undefined, upload: undefined };
      patchMessages(chatId, (m) => ({ ...m, items: mergeMessage(m.items, merged) }));
      applyToChat(merged, false);
    } catch (e) {
      rec.upload = undefined;
      if (rec.cancelled || (e instanceof ApiError && e.status === -1)) return;
      if (e instanceof ApiError && e.status === 413) get().showToast(t.fileTooLarge);
      else if (rec.file && !rec.attachmentId) get().showToast(t.uploadFailed);
      setLocal(chatId, clientId, { local: "failed", upload: undefined });
    }
  };

  /** Secret chat send: encrypt file → upload "encrypted.bin" → encrypt payload → POST ciphertext. */
  const deliverSecret = async (clientId: string, rec: PendingSend) => {
    const { chatId } = rec;
    const needsUpload = !!rec.file && !rec.secretUpload;
    setLocal(chatId, clientId, { local: "sending", ...(needsUpload ? { upload: { progress: 0 } } : {}) });
    try {
      const me = get().me;
      const chat = get().chats.find((c) => c.id === chatId);
      if (!me || !chat) throw new Error("no chat");
      const key = await e2e.getChatKey(chat, me.id);
      if (!key) throw new Error("no key");

      let file: e2e.SecretFile | undefined;
      const image = !!rec.file && isImageFile(rec.file);
      if (rec.file) {
        if (!rec.secretUpload || !rec.fileKey) {
          if (image && (!rec.width || !rec.height) && typeof createImageBitmap === "function") {
            try {
              const bmp = await createImageBitmap(rec.file);
              rec.width = bmp.width;
              rec.height = bmp.height;
              bmp.close();
            } catch {
              /* not decodable: send without dimensions */
            }
          }
          const enc = await e2e.encryptFile(new Uint8Array(await rec.file.arrayBuffer()));
          if (rec.cancelled) return;
          const encFile = new File([enc.blob], "encrypted.bin", { type: "application/octet-stream" });
          let last = 0;
          const handle = uploadFile(encFile, (p) => {
            if (p - last >= 0.01 || p === 1) {
              last = p;
              setLocal(chatId, clientId, { upload: { progress: p } });
            }
          });
          rec.upload = handle;
          const att = await handle.promise;
          rec.upload = undefined;
          rec.secretUpload = att;
          rec.fileKey = enc.keyB64;
          // We already have the plaintext: never re-download our own upload.
          e2e.primeDecryptedBlob(attachmentUrl(att.url), enc.keyB64, rec.file);
          if (rec.cancelled) return;
          setLocal(chatId, clientId, { upload: undefined });
        }
        const att = rec.secretUpload!;
        file = {
          upload_id: att.id,
          url: att.url,
          key: rec.fileKey!,
          name: rec.file.name || (image ? "image" : "file"),
          mime: rec.file.type || "application/octet-stream",
          size: rec.file.size,
          width: image ? (rec.width ?? 0) : 0,
          height: image ? (rec.height ?? 0) : 0,
        };
      }
      const payload: e2e.SecretPayload = {
        v: 1,
        kind: rec.file ? (image ? "image" : "file") : "text",
        text: rec.text,
        ...(file ? { file } : {}),
      };
      const ciphertext = await e2e.encryptMessage(key, e2e.encodePayload(payload), chat.id, me.id);
      if (rec.cancelled) return;
      const saved = await api.sendEncrypted(chatId, ciphertext, clientId, rec.secretUpload?.id);
      pending.delete(clientId);
      const view = await decryptIncoming({ ...saved, client_id: saved.client_id || clientId });
      const merged: UIMessage = { ...view, local: undefined, upload: undefined };
      patchMessages(chatId, (m) => ({ ...m, items: mergeMessage(m.items, merged) }));
      applyToChat(merged, false);
    } catch (e) {
      rec.upload = undefined;
      if (rec.cancelled || (e instanceof ApiError && e.status === -1)) return;
      if (e instanceof ApiError && e.status === 413) get().showToast(t.fileTooLarge);
      else if (rec.file && !rec.secretUpload) get().showToast(t.uploadFailed);
      setLocal(chatId, clientId, { local: "failed", upload: undefined });
    }
  };

  const isViewing = (chatId: ID) => {
    const s = get();
    return s.activeChatId === chatId && s.focused;
  };

  return {
    me: null,
    chats: [],
    chatsLoaded: false,
    messages: {},
    activeChatId: null,
    typing: {},
    wsStatus: "idle",
    focused: true,
    toast: null,
    lightbox: null,
    secretKeys: {},

    showToast: (text) => {
      if (toastTimer) clearTimeout(toastTimer);
      set({ toast: { id: ++toastSeq, text } });
      toastTimer = setTimeout(() => set({ toast: null }), 3500);
    },
    openLightbox: (url, name, blob) => set({ lightbox: { url, name, blob } }),
    closeLightbox: () => set({ lightbox: null }),

    init: () => {
      const token = getToken();
      if (!token) return;
      const cached = getCachedUser();
      if (cached && !get().me) set({ me: cached });

      api
        .me()
        .then((u) => {
          cacheUser(u);
          set({ me: u });
          ensureAllSecrets();
        })
        .catch(() => {});
      get().loadChats();

      if (rt) rt.stop();
      rt = new RealtimeClient(token, {
        onEvent: (ev) => get().handleEvent(ev),
        onStatus: (wsStatus) => set({ wsStatus }),
        onOpen: (reconnect) => {
          if (!reconnect) return;
          // Catch up on anything missed while disconnected.
          get().loadChats();
          const active = get().activeChatId;
          if (active) {
            api
              .messages(active, { limit: PAGE_SIZE })
              .then(decryptMany)
              .then((msgs) => patchMessages(active, (m) => ({ ...m, items: mergeMany(m.items, msgs) })))
              .catch(() => {});
          }
        },
      });
      rt.start();
    },

    teardown: () => {
      rt?.stop();
      rt = null;
      typingTimers.forEach((t) => clearTimeout(t));
      typingTimers.clear();
      lastReadSent.clear();
      pending.forEach((p) => p.upload?.abort());
      pending.clear();
      objectUrls.forEach((u) => URL.revokeObjectURL(u));
      objectUrls.clear();
      e2e.clearMediaCache();
      set({
        toast: null,
        lightbox: null,
        secretKeys: {},
        me: null,
        chats: [],
        chatsLoaded: false,
        messages: {},
        activeChatId: null,
        typing: {},
        wsStatus: "idle",
      });
    },

    setMe: (u) => {
      cacheUser(u);
      set({ me: u });
    },

    setFocused: (focused) => {
      set({ focused });
      const active = get().activeChatId;
      if (focused && active) {
        const chat = get().chats.find((c) => c.id === active);
        if (chat && chat.unread_count > 0) get().markRead(active);
      }
    },

    loadChats: async () => {
      try {
        const chats = await api.chats();
        const prev = new Map(get().chats.map((c) => [c.id, c]));
        set({
          chats: sortChats(chats.map((c) => keepDecryptedPreview(normalizeChat(c), prev.get(c.id)))),
          chatsLoaded: true,
        });
        ensureAllSecrets();
      } catch {
        set({ chatsLoaded: true });
      }
    },

    setActive: (id) => {
      const prev = get().activeChatId;
      if (prev && prev !== id) rt?.send({ type: "typing", data: { chat_id: prev, typing: false } });
      set({ activeChatId: id });
      if (!id) return;
      const m = get().messages[id];
      if (!m || (!m.loaded && !m.loading)) void get().loadMessages(id);
      const chat = get().chats.find((c) => c.id === id);
      if (chat && chat.unread_count > 0 && get().focused) get().markRead(id);
    },

    loadMessages: async (chatId) => {
      patchMessages(chatId, (m) => ({ ...m, loading: true, error: undefined }));
      try {
        const msgs = await decryptMany(await api.messages(chatId, { limit: PAGE_SIZE }));
        patchMessages(chatId, (m) => ({
          ...m,
          items: mergeMany(m.items, msgs),
          loaded: true,
          loading: false,
          hasMore: msgs.length >= PAGE_SIZE,
        }));
      } catch (e) {
        patchMessages(chatId, (m) => ({ ...m, loading: false, error: (e as Error).message }));
      }
    },

    loadOlder: async (chatId) => {
      const m = get().messages[chatId];
      if (!m || !m.loaded || m.loadingOlder || !m.hasMore) return;
      const oldest = m.items.find((x) => !x.local);
      if (!oldest) return;
      patchMessages(chatId, (x) => ({ ...x, loadingOlder: true }));
      try {
        const older = await decryptMany(await api.messages(chatId, { before: oldest.id, limit: PAGE_SIZE }));
        patchMessages(chatId, (x) => ({
          ...x,
          items: mergeMany(x.items, older),
          loadingOlder: false,
          hasMore: older.length >= PAGE_SIZE,
        }));
      } catch {
        patchMessages(chatId, (x) => ({ ...x, loadingOlder: false }));
      }
    },

    send: async (chatId, text) => {
      const me = get().me;
      const body = text.trim();
      if (!me || !body) return;
      const clientId = newClientId();
      const optimistic: UIMessage = {
        id: clientId,
        chat_id: chatId,
        sender_id: me.id,
        kind: "text",
        text: body,
        attachment: null,
        client_id: clientId,
        status: "sent",
        created_at: new Date().toISOString(),
        local: "sending",
        ...(get().chats.find((c) => c.id === chatId)?.type === "secret" ? { e2e: "ok" as const } : {}),
      };
      patchMessages(chatId, (m) => ({ ...m, items: mergeMessage(m.items, optimistic) }));
      applyToChat(optimistic, false);
      rt?.send({ type: "typing", data: { chat_id: chatId, typing: false } });
      pending.set(clientId, { chatId, text: body });
      await deliver(clientId);
    },

    sendAttachment: (chatId, file, caption, meta = {}) => {
      const me = get().me;
      if (!me) return;
      const secret = get().chats.find((c) => c.id === chatId)?.type === "secret";
      if (file.size > MAX_UPLOAD_BYTES - (secret ? ENC_OVERHEAD : 0)) {
        get().showToast(t.fileTooLarge);
        return;
      }
      const clientId = newClientId();
      const image = isImageFile(file);
      if (meta.previewUrl) objectUrls.add(meta.previewUrl);
      const localAttachment: Attachment = {
        id: "",
        url: "",
        name: file.name || (image ? "image" : "file"),
        size: file.size,
        mime: file.type || "application/octet-stream",
        kind: image ? "image" : "file",
        width: meta.width ?? 0,
        height: meta.height ?? 0,
      };
      const body = caption.trim();
      const optimistic: UIMessage = {
        id: clientId,
        chat_id: chatId,
        sender_id: me.id,
        kind: image ? "image" : "file",
        text: body,
        attachment: localAttachment,
        client_id: clientId,
        status: "sent",
        created_at: new Date().toISOString(),
        local: "sending",
        localPreview: meta.previewUrl,
        upload: { progress: 0 },
        ...(secret ? { e2e: "ok" as const } : {}),
      };
      patchMessages(chatId, (m) => ({ ...m, items: mergeMessage(m.items, optimistic) }));
      applyToChat(optimistic, false);
      pending.set(clientId, { chatId, text: body, file, width: meta.width, height: meta.height });
      void deliver(clientId);
    },

    cancelSend: (chatId, clientId) => {
      const rec = pending.get(clientId);
      if (rec) {
        rec.cancelled = true;
        rec.upload?.abort();
        pending.delete(clientId);
      }
      const removed = get().messages[chatId]?.items.find((x) => x.client_id === clientId && x.local);
      if (!removed) return;
      patchMessages(chatId, (m) => ({ ...m, items: m.items.filter((x) => x !== removed) }));
      // Restore the chat preview to the newest remaining message.
      const items = get().messages[chatId]?.items ?? [];
      const lastLeft = items[items.length - 1] ?? null;
      patchChat(chatId, (c) =>
        c.last_message && (c.last_message.client_id === clientId || c.last_message.id === clientId)
          ? { ...c, last_message: lastLeft }
          : c,
      );
    },

    retry: async (chatId, clientId) => {
      const msg = get().messages[chatId]?.items.find((x) => x.client_id === clientId && x.local === "failed");
      if (!msg) return;
      if (!pending.has(clientId)) {
        // Text messages can always be re-sent; attachments need the original File.
        if (msg.kind !== "text") return;
        pending.set(clientId, { chatId, text: msg.text });
      }
      await deliver(clientId);
    },

    markRead: (chatId) => {
      patchChat(chatId, (c) => (c.unread_count ? { ...c, unread_count: 0 } : c));
      const now = Date.now();
      const last = lastReadSent.get(chatId) ?? 0;
      if (now - last < 800) return;
      lastReadSent.set(chatId, now);
      if (!rt?.send({ type: "read", data: { chat_id: chatId } })) {
        api.markRead(chatId).catch(() => {});
      }
    },

    sendTyping: (chatId, typing) => {
      rt?.send({ type: "typing", data: { chat_id: chatId, typing } });
    },

    upsertChat: (chat) => {
      const c = normalizeChat(chat);
      set((s) => {
        const exists = s.chats.some((x) => x.id === c.id);
        const chats = exists
          ? s.chats.map((x) => (x.id === c.id ? keepDecryptedPreview({ ...x, ...c }, x) : x))
          : s.chats.concat(c);
        return { chats: sortChats(chats) };
      });
      if (c.type === "secret") void ensureSecret(c.id);
    },

    openDirect: async (userId) => {
      const existing = get().chats.find(
        (c) => c.type === "direct" && c.members.some((m) => m.id === userId) && (c.members.length > 1 || userId === get().me?.id),
      );
      if (existing) return existing;
      const chat = normalizeChat(await api.createDirect(userId));
      get().upsertChat(chat);
      return chat;
    },

    createGroup: async (title, memberIds) => {
      const chat = normalizeChat(await api.createGroup(title, memberIds));
      get().upsertChat(chat);
      return chat;
    },

    startSecretChat: async (userId) => {
      try {
        // Generate first; persist under the chat id once the server returned it.
        const pair = await e2e.generateChatKeyPair();
        const chat = await api.createSecret(userId, pair.publicKeyB64);
        await e2e.saveChatKey(chat.id, pair);
        set((s) => ({ secretKeys: { ...s.secretKeys, [chat.id]: true } }));
        get().upsertChat(chat);
        return chat;
      } catch (e) {
        get().showToast(e instanceof ApiError ? errorMessage(e) : t.secretUnsupported);
        return null;
      }
    },

    acceptSecret: async (chatId) => {
      let pair: e2e.ChatKeyPair;
      try {
        pair = await e2e.generateChatKeyPair();
        // Save before accepting so a chat.updated arriving first already finds the key.
        await e2e.saveChatKey(chatId, pair);
      } catch {
        get().showToast(t.secretUnsupported);
        return;
      }
      try {
        const chat = await api.acceptSecret(chatId, pair.publicKeyB64);
        set((s) => ({ secretKeys: { ...s.secretKeys, [chatId]: true } }));
        get().upsertChat(chat);
      } catch (e) {
        await e2e.deleteChatKey(chatId);
        get().showToast(e instanceof ApiError && e.status === 409 ? t.secretAlreadyAccepted : errorMessage(e));
        void fetchChat(chatId);
      }
    },

    deleteChat: async (chatId) => {
      try {
        await api.deleteChat(chatId);
      } catch (e) {
        if (!(e instanceof ApiError && e.status === 404)) {
          get().showToast(errorMessage(e));
          return false;
        }
      }
      get().removeChatLocal(chatId);
      return true;
    },

    removeChatLocal: (chatId) => {
      void e2e.deleteChatKey(chatId);
      pending.forEach((p, id) => {
        if (p.chatId === chatId) {
          p.cancelled = true;
          p.upload?.abort();
          pending.delete(id);
        }
      });
      set((s) => {
        const messages = { ...s.messages };
        delete messages[chatId];
        const typing = { ...s.typing };
        delete typing[chatId];
        const secretKeys = { ...s.secretKeys };
        delete secretKeys[chatId];
        return {
          chats: s.chats.filter((c) => c.id !== chatId),
          messages,
          typing,
          secretKeys,
          activeChatId: s.activeChatId === chatId ? null : s.activeChatId,
          lightbox: s.activeChatId === chatId ? null : s.lightbox,
        };
      });
    },

    handleEvent: (ev) => {
      const me = get().me;
      switch (ev.type) {
        case "message.new": {
          const raw: UIMessage = normalizeMessage(ev.data);
          if (raw.kind === "encrypted") {
            void (async () => {
              if (!get().chats.some((c) => c.id === raw.chat_id)) await fetchChat(raw.chat_id);
              processNew(await decryptIncoming(raw));
            })();
          } else {
            processNew(raw);
          }
          break;
        }
        case "chat.updated": {
          get().upsertChat(ev.data);
          break;
        }
        case "chat.deleted": {
          if (ev.data?.chat_id) get().removeChatLocal(ev.data.chat_id);
          break;
        }
        case "message.read": {
          const { chat_id, user_id, read_at } = ev.data;
          if (me && user_id === me.id) {
            patchChat(chat_id, (c) => ({ ...c, unread_count: 0 }));
            break;
          }
          // Small tolerance for clock skew between server timestamps.
          const cutoff = (read_at ? ts(read_at) : Date.now()) + 1000;
          const markItem = <T extends Message>(m: T): T =>
            me && m.sender_id === me.id && m.status !== "read" && ts(m.created_at) <= cutoff ? { ...m, status: "read" } : m;
          if (get().messages[chat_id]) {
            patchMessages(chat_id, (m) => ({ ...m, items: m.items.map((x) => (x.local ? x : markItem(x))) }));
          }
          patchChat(chat_id, (c) => (c.last_message ? { ...c, last_message: markItem(c.last_message) } : c));
          break;
        }
        case "chat.new": {
          get().upsertChat(ev.data);
          break;
        }
        case "typing": {
          const { chat_id, user_id, typing } = ev.data;
          if (me && user_id === me.id) break;
          setTyping(chat_id, user_id, typing);
          break;
        }
        case "presence": {
          const { user_id, online, last_seen } = ev.data;
          set((s) => ({
            chats: s.chats.map((c) =>
              c.members.some((m) => m.id === user_id)
                ? {
                    ...c,
                    members: c.members.map((m) =>
                      m.id === user_id ? { ...m, online, last_seen: last_seen || m.last_seen } : m,
                    ),
                  }
                : c,
            ),
          }));
          if (!online) {
            // Clear any lingering typing indicators from that user.
            for (const [chatId, users] of Object.entries(get().typing)) {
              if (users.includes(user_id)) setTyping(chatId, user_id, false);
            }
          }
          break;
        }
        case "pong":
        default:
          break;
      }
    },
  };
});
