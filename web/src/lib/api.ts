import { API_URL } from "./config";
import { clearSession, getToken } from "./auth";
import type {
  Attachment,
  Chat,
  ID,
  Message,
  OtpRequestResponse,
  OtpVerifyResponse,
  ProfilePatch,
  User,
} from "@/types";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
    this.name = "ApiError";
  }
}

type Method = "GET" | "POST" | "PATCH" | "DELETE";

export const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;

/** Backward compatibility: missing `kind` → "text", missing `attachment` → null. */
export function normalizeMessage<T extends Message>(m: T): T {
  const attachment = m.attachment ?? null;
  const kind = m.kind || (attachment ? attachment.kind : "text");
  return { ...m, kind, attachment, text: m.text ?? "" };
}

export function normalizeChat(c: Chat): Chat {
  return {
    ...c,
    members: c.members ?? [],
    unread_count: c.unread_count ?? 0,
    last_message: c.last_message ? normalizeMessage(c.last_message) : null,
  };
}

let unauthorizedHandler: (() => void) | null = null;

/** Called whenever an authenticated request returns 401. */
export function onUnauthorized(fn: (() => void) | null) {
  unauthorizedHandler = fn;
}

async function request<T>(method: Method, path: string, body?: unknown, auth = true): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (auth) {
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, "network");
  }

  const text = await res.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }

  if (!res.ok) {
    const msg =
      data && typeof data === "object" && "error" in data && typeof (data as { error: unknown }).error === "string"
        ? (data as { error: string }).error
        : res.statusText || `HTTP ${res.status}`;
    if (res.status === 401 && auth) {
      clearSession();
      unauthorizedHandler?.();
    }
    throw new ApiError(res.status, msg);
  }
  return data as T;
}

export const api = {
  // Auth
  requestOtp: (phone: string) =>
    request<OtpRequestResponse>("POST", "/auth/otp/request", { phone }, false),
  verifyOtp: (phone: string, code: string) =>
    request<OtpVerifyResponse>("POST", "/auth/otp/verify", { phone, code }, false),

  // Profile & users
  me: () => request<User>("GET", "/me"),
  updateMe: (patch: ProfilePatch) => request<User>("PATCH", "/me", patch),
  searchUsers: (q: string) =>
    request<User[] | null>("GET", `/users/search?q=${encodeURIComponent(q)}`).then((r) => r ?? []),
  user: (id: ID) => request<User>("GET", `/users/${encodeURIComponent(id)}`),

  // Chats
  chats: () => request<Chat[] | null>("GET", "/chats").then((r) => (r ?? []).map(normalizeChat)),
  chat: (id: ID) => request<Chat>("GET", `/chats/${encodeURIComponent(id)}`).then(normalizeChat),
  createDirect: (userId: ID) =>
    request<Chat>("POST", "/chats/direct", { user_id: userId }).then(normalizeChat),
  createGroup: (title: string, memberIds: ID[]) =>
    request<Chat>("POST", "/chats/group", { title, member_ids: memberIds }).then(normalizeChat),
  messages: (chatId: ID, opts: { before?: ID; limit?: number } = {}) => {
    const p = new URLSearchParams();
    if (opts.before) p.set("before", opts.before);
    p.set("limit", String(opts.limit ?? 50));
    return request<Message[] | null>(
      "GET",
      `/chats/${encodeURIComponent(chatId)}/messages?${p.toString()}`,
    ).then((r) => (r ?? []).map(normalizeMessage));
  },
  sendMessage: (chatId: ID, text: string, clientId?: string, attachmentId?: ID) =>
    request<Message>("POST", `/chats/${encodeURIComponent(chatId)}/messages`, {
      text,
      client_id: clientId,
      ...(attachmentId ? { attachment_id: attachmentId } : {}),
    }).then(normalizeMessage),
  markRead: (chatId: ID) =>
    request<{ ok: boolean }>("POST", `/chats/${encodeURIComponent(chatId)}/read`, {}),

  health: () => request<{ status: string }>("GET", "/health", undefined, false),
};

export interface UploadHandle {
  promise: Promise<Attachment>;
  abort: () => void;
}

/**
 * POST /uploads (multipart field `file`) via XMLHttpRequest so we get upload progress.
 * Rejects with ApiError (status 0 = network, -1 = aborted, 413 = too large).
 */
export function uploadFile(file: File, onProgress?: (fraction: number) => void): UploadHandle {
  const xhr = new XMLHttpRequest();
  const promise = new Promise<Attachment>((resolve, reject) => {
    xhr.open("POST", `${API_URL}/uploads`);
    xhr.responseType = "text";
    const token = getToken();
    if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    xhr.setRequestHeader("Accept", "application/json");

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) onProgress(Math.min(1, e.loaded / e.total));
    };
    xhr.onload = () => {
      let data: unknown = null;
      try {
        data = xhr.responseText ? JSON.parse(xhr.responseText) : null;
      } catch {
        data = null;
      }
      if (xhr.status >= 200 && xhr.status < 300 && data) {
        onProgress?.(1);
        resolve(data as Attachment);
        return;
      }
      const msg =
        data && typeof data === "object" && "error" in data ? String((data as { error: unknown }).error) : `HTTP ${xhr.status}`;
      if (xhr.status === 401) {
        clearSession();
        unauthorizedHandler?.();
      }
      reject(new ApiError(xhr.status, msg));
    };
    xhr.onerror = () => reject(new ApiError(0, "network"));
    xhr.onabort = () => reject(new ApiError(-1, "aborted"));

    const form = new FormData();
    form.append("file", file, file.name);
    xhr.send(form);
  });
  return { promise, abort: () => xhr.abort() };
}
