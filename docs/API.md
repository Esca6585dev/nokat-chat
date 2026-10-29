# Nokat Chat — API contract (v1)

Single source of truth shared by `backend/` (Go Fiber), `web/` (Next.js) and `android/` (Kotlin).

- Base URL: `http://<host>:8080/api/v1`
- JSON everywhere, `Content-Type: application/json`.
- IDs are UUID strings. Timestamps are RFC3339 UTC strings (`2026-10-29T12:00:00Z`).
- Auth: `Authorization: Bearer <token>` on every endpoint except `/auth/*` and `/health`.
- Errors: non-2xx status with body `{"error": "human readable message"}`.
- Phone numbers are E.164 (`+99365123456`). Backend normalises spaces/dashes away and requires a leading `+` and 8–15 digits.

## Models

```jsonc
// User
{
  "id": "uuid",
  "phone": "+99365123456",
  "name": "Aman",            // "" until profile set up
  "username": "aman",        // "" or unique, [a-z0-9_]{3,32}
  "bio": "",
  "avatar_url": "",
  "online": true,
  "last_seen": "2026-10-29T12:00:00Z",
  "created_at": "2026-10-29T12:00:00Z"
}

// Message
{
  "id": "uuid",
  "chat_id": "uuid",
  "sender_id": "uuid",
  "text": "Salam!",
  "client_id": "optional client-generated id, echoed back for dedupe",
  "status": "sent" | "delivered" | "read",
  "created_at": "2026-10-29T12:00:00Z"
}

// Chat
{
  "id": "uuid",
  "type": "direct" | "group",
  "title": "",               // group title; for direct chats clients show the peer's name
  "avatar_url": "",
  "members": [User],
  "last_message": Message | null,
  "unread_count": 0,
  "updated_at": "2026-10-29T12:00:00Z"
}
```

## Auth (phone + OTP)

### `POST /auth/otp/request`
Body `{"phone": "+99365123456"}` →
`200 {"ok": true, "expires_in": 120, "dev_code": "123456"}`

`dev_code` is only present when the server runs with `OTP_DEV_MODE=true` (no SMS gateway configured). Codes are 6 digits, valid 2 minutes, max 5 attempts. Re-request is rate-limited to once per 30 s per phone (`429`).

### `POST /auth/otp/verify`
Body `{"phone": "+99365123456", "code": "123456"}` →
`200 {"token": "<jwt>", "user": User, "is_new": true}`

`is_new` is true when the user has no name yet → clients show the "set up profile" screen. Token lifetime 30 days.

## Profile & users

- `GET /me` → `User`
- `PATCH /me` body `{"name"?, "username"?, "bio"?, "avatar_url"?}` → `User` (`409` if username taken)
- `GET /users/search?q=<text>` → `[User]` — matches name, username or phone prefix; excludes self; max 20.
- `GET /users/:id` → `User`

## Chats

- `GET /chats` → `[Chat]` sorted by `updated_at` desc.
- `POST /chats/direct` body `{"user_id": "uuid"}` → `Chat` (returns existing direct chat if one exists).
- `POST /chats/group` body `{"title": "Team", "member_ids": ["uuid", ...]}` → `Chat`
- `GET /chats/:id` → `Chat`
- `GET /chats/:id/messages?before=<message_id>&limit=50` → `[Message]` ordered **oldest → newest**. `before` is optional (pagination toward older messages).
- `POST /chats/:id/messages` body `{"text": "...", "client_id": "optional"}` → `Message` (also broadcast over WebSocket to all members, including the sender's other sessions).
- `POST /chats/:id/read` body `{}` → `{"ok": true}` — marks everything in the chat as read for the caller.

## WebSocket — `GET /ws?token=<jwt>`

Upgrade to WebSocket at `ws://<host>:8080/api/v1/ws?token=<jwt>`. All frames are JSON text `{"type": "...", "data": {...}}`.

Server → client:

| type | data |
|---|---|
| `message.new` | `Message` |
| `message.read` | `{"chat_id", "user_id", "read_at"}` — `user_id` read all messages in the chat |
| `chat.new` | `Chat` — a chat the user was just added to |
| `typing` | `{"chat_id", "user_id", "typing": true\|false}` |
| `presence` | `{"user_id", "online": bool, "last_seen"}` |
| `pong` | `{}` |

Client → server:

| type | data |
|---|---|
| `message.send` | `{"chat_id", "text", "client_id"}` — same effect as the REST endpoint |
| `typing` | `{"chat_id", "typing": true\|false}` |
| `read` | `{"chat_id"}` |
| `ping` | `{}` — send every ~25 s |

Clients should reconnect with exponential backoff (1 s → 30 s) and refetch `/chats` after reconnecting.

## Health
`GET /health` → `{"status":"ok"}`

## Brand
- Logo: `branding/logo.svg` (gradient `#38BDF8 → #2563EB → #1E40AF`, white speech bubble with a blue dot — "nokat").
- Primary `#2563EB`, primary-dark `#1E40AF`, sky accent `#38BDF8`, light background `#F4F8FF`, outgoing bubble gradient `#2563EB → #3B82F6`, incoming bubble `#FFFFFF` (dark mode `#1E293B`), dark background `#0B1220`.
