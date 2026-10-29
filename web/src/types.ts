// Mirrors the models in docs/API.md (Nokat Chat API v1).

export type ID = string;

export interface User {
  id: ID;
  phone: string;
  name: string;
  username: string;
  bio: string;
  avatar_url: string;
  online: boolean;
  last_seen: string;
  created_at: string;
}

export type MessageStatus = "sent" | "delivered" | "read";

export interface Message {
  id: ID;
  chat_id: ID;
  sender_id: ID;
  text: string;
  client_id?: string;
  status: MessageStatus;
  created_at: string;
}

/** Client-only delivery state for optimistic messages. */
export type LocalState = "sending" | "failed";

export interface UIMessage extends Message {
  local?: LocalState;
}

export type ChatType = "direct" | "group";

export interface Chat {
  id: ID;
  type: ChatType;
  title: string;
  avatar_url: string;
  members: User[];
  last_message: Message | null;
  unread_count: number;
  updated_at: string;
}

// ---- Auth ----

export interface OtpRequestResponse {
  ok: boolean;
  expires_in: number;
  dev_code?: string;
}

export interface OtpVerifyResponse {
  token: string;
  user: User;
  is_new: boolean;
}

export interface ProfilePatch {
  name?: string;
  username?: string;
  bio?: string;
  avatar_url?: string;
}

export interface ApiErrorBody {
  error: string;
}

// ---- WebSocket ----

export interface ReadEvent {
  chat_id: ID;
  user_id: ID;
  read_at: string;
}

export interface TypingEvent {
  chat_id: ID;
  user_id: ID;
  typing: boolean;
}

export interface PresenceEvent {
  user_id: ID;
  online: boolean;
  last_seen: string;
}

export type ServerEvent =
  | { type: "message.new"; data: Message }
  | { type: "message.read"; data: ReadEvent }
  | { type: "chat.new"; data: Chat }
  | { type: "typing"; data: TypingEvent }
  | { type: "presence"; data: PresenceEvent }
  | { type: "pong"; data: Record<string, never> };

export type ClientEvent =
  | { type: "message.send"; data: { chat_id: ID; text: string; client_id: string } }
  | { type: "typing"; data: { chat_id: ID; typing: boolean } }
  | { type: "read"; data: { chat_id: ID } }
  | { type: "ping"; data: Record<string, never> };
