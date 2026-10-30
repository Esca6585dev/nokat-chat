<p align="center"><img src="branding/logo.svg" width="120" alt="Nokat Chat"></p>

<h1 align="center">Nokat Chat</h1>
<p align="center">Professional, blue-themed realtime messenger — Android (Kotlin), Web (Next.js), Backend (Go Fiber).</p>

| Part | Stack | Folder |
|---|---|---|
| Backend | Go 1.24, Fiber v2, GORM (SQLite/PostgreSQL), JWT, WebSocket | [`backend/`](backend) |
| Web | Next.js 15, TypeScript, Tailwind CSS | [`web/`](web) |
| Android | Kotlin, Jetpack Compose, Material 3, OkHttp WebSocket | [`android/`](android) |
| API contract | REST + WebSocket | [`docs/API.md`](docs/API.md) |
| Brand | Logo SVG/PNG | [`branding/`](branding) |

## Features
- Registration / login by phone number + 6-digit SMS OTP (dev mode shows the code)
- Profile setup (name, @username, bio)
- Direct chats and groups, user search by name / username / phone
- Photo and file sharing (up to 20 MB) with previews, upload progress and a fullscreen viewer
- **Secret chats** with end-to-end encryption (P-256 ECDH + AES-256-GCM): the server stores only ciphertext — see [`docs/SECRET_CHATS.md`](docs/SECRET_CHATS.md)
- Realtime messages over WebSocket, multi-device per user
- Typing indicator, online / last-seen presence, ✓ sent / ✓✓ read receipts, unread counters
- Message history with pagination, optimistic sending with dedupe (`client_id`)

## Quick start

```bash
# 1. Backend (SQLite, dev OTP)
cd backend && go run ./cmd/server

# 2. Web
cd web && npm install && npm run dev        # http://localhost:3000

# 3. Android: open android/ in Android Studio, run on an emulator
#    (talks to http://10.0.2.2:8080/api/v1 by default)
```

Or everything with PostgreSQL: `docker compose up --build`.
