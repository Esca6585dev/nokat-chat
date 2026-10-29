import { API_URL } from "./config";
import { clearSession, getToken } from "./auth";
import type {
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
  chats: () => request<Chat[] | null>("GET", "/chats").then((r) => r ?? []),
  chat: (id: ID) => request<Chat>("GET", `/chats/${encodeURIComponent(id)}`),
  createDirect: (userId: ID) => request<Chat>("POST", "/chats/direct", { user_id: userId }),
  createGroup: (title: string, memberIds: ID[]) =>
    request<Chat>("POST", "/chats/group", { title, member_ids: memberIds }),
  messages: (chatId: ID, opts: { before?: ID; limit?: number } = {}) => {
    const p = new URLSearchParams();
    if (opts.before) p.set("before", opts.before);
    p.set("limit", String(opts.limit ?? 50));
    return request<Message[] | null>(
      "GET",
      `/chats/${encodeURIComponent(chatId)}/messages?${p.toString()}`,
    ).then((r) => r ?? []);
  },
  sendMessage: (chatId: ID, text: string, clientId?: string) =>
    request<Message>("POST", `/chats/${encodeURIComponent(chatId)}/messages`, {
      text,
      client_id: clientId,
    }),
  markRead: (chatId: ID) =>
    request<{ ok: boolean }>("POST", `/chats/${encodeURIComponent(chatId)}/read`, {}),

  health: () => request<{ status: string }>("GET", "/health", undefined, false),
};
