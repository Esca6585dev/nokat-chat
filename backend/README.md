# Nokat Chat — backend (Go + Fiber)

REST + WebSocket API described in [`../docs/API.md`](../docs/API.md).

## Run locally

```bash
cd backend
go run ./cmd/server          # http://localhost:8080/api/v1, SQLite file nokat.db
```

With `OTP_DEV_MODE=true` (default) the OTP code is printed to the log and returned as `dev_code`,
so you can log in without an SMS gateway. For production set `OTP_DEV_MODE=false`, a strong
`JWT_SECRET`, and `SMS_WEBHOOK_URL` pointing at your SMS provider (receives `POST {"phone","text"}`).

See `.env.example` for all settings. PostgreSQL: `DB_DRIVER=postgres DB_DSN=postgres://...`.

## Layout

```
cmd/server        entrypoint
internal/config   env configuration
internal/db       GORM (SQLite / PostgreSQL) + migrations
internal/models   tables: users, otp_codes, chats, chat_members, messages
internal/auth     phone normalisation, OTP hashing, JWT, SMS senders
internal/hub      in-memory WebSocket connection hub (multi-device per user)
internal/api      HTTP handlers, DTOs, WebSocket protocol
```

Security notes: OTP codes are stored only as HMAC hashes, expire after 2 minutes, allow 5 attempts,
and can be re-requested every 30 s; `/auth/*` is additionally rate-limited per IP.
