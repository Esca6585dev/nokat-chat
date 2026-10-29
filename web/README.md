# Nokat Chat — Web client

Next.js 15 (App Router, TypeScript) + Tailwind CSS v4 web client for **Nokat Chat**.
It is a pure client-side app that talks to the Go backend described in [`../docs/API.md`](../docs/API.md)
over REST and WebSocket.

## Requirements

- Node.js 20+ (22 recommended), npm 10+
- A running Nokat Chat backend (default `http://localhost:8080/api/v1`). The backend must allow CORS
  from the web origin (e.g. `http://localhost:3000`).

## Configuration

Copy `.env.example` to `.env.local` and adjust:

| Variable | Default | Description |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080/api/v1` | REST base URL. The WebSocket URL is derived from it (`http→ws`, `https→wss`) + `/ws`. A same-origin relative path such as `/api/v1` also works. |

`NEXT_PUBLIC_*` values are inlined at **build time**, so rebuild after changing them.

## Development

```bash
npm install
npm run dev        # http://localhost:3000
```

With the backend running in `OTP_DEV_MODE=true`, the OTP screen shows a "Test kody: 123456"
hint (tap it to auto-fill).

## Production

```bash
npm run build
npm start          # or: node .next/standalone/server.js (copy public/ and .next/static/ next to it)
npm run lint
```

### Docker

The image uses Next.js `output: "standalone"`:

```bash
docker build -t nokat-chat-web --build-arg NEXT_PUBLIC_API_URL=https://chat.example.com/api/v1 .
docker run -p 3000:3000 nokat-chat-web
```

## Project structure

```
src/
  app/
    page.tsx          # login: phone → OTP
    setup/page.tsx    # first-run profile setup
    chat/page.tsx     # two-pane messenger (single pane on mobile)
    layout.tsx, globals.css
  components/         # Sidebar, ChatListItem, Conversation, MessageList, MessageBubble,
                      # Composer, EmojiPicker, NewChatDialog, SettingsDrawer, ProfileForm, auth/*
  lib/
    api.ts            # typed REST client
    ws.ts             # WebSocket client (ping 25 s, exponential backoff 1 s → 30 s)
    store.ts          # zustand store: chats, messages, typing, presence, optimistic sends
    auth.ts           # token/user persistence in localStorage
    format.ts         # dates, names, initials, avatar gradients
    i18n.ts           # ALL UI strings (Turkmen) — translate here
  types.ts            # mirrors API models and WS events
public/logo.svg       # brand logo / favicon
```

## Features

- Phone + OTP login (default country +993), 6-box OTP input with paste support and 30 s resend timer
- Profile setup and settings drawer (name, username, bio, logout)
- Chat list with search (local + global user search), avatars, unread badges, online dots, typing previews
- Direct chats and groups
- Conversation with date separators, grouped bubbles, status ticks (✓ sent, ✓✓ read), infinite
  scroll for history, optimistic sends with `client_id` dedupe and retry on failure
- Realtime: `message.new`, `message.read`, `chat.new`, `typing`, `presence`; reconnect + refetch
- Read receipts only while the chat is open and the window is focused
- Light/dark theme via `prefers-color-scheme`, responsive down to phone widths
