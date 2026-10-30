# Nokat Chat — Android

Native Android client for Nokat Chat, built with Kotlin, Jetpack Compose (Material 3) and a single activity.
It talks to the Go Fiber backend in `../backend` using the REST and WebSocket contract in [`../docs/API.md`](../docs/API.md).

## Requirements

- Android Studio Ladybug (2024.2) or newer, with Android SDK 35 installed
- JDK 17 (the JBR bundled with Android Studio works)
- A device or emulator on Android 8.0 (API 26) or newer

| Component | Version |
|---|---|
| Gradle (wrapper) | 8.11.1 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin (+ compose and serialization plugins) | 2.0.21 |
| Compose BOM | 2024.12.01 (Material 3) |
| navigation-compose | 2.8.5 (type-safe routes) |
| lifecycle | 2.8.7 |
| activity-compose | 1.9.3 |
| kotlinx-serialization-json | 1.7.3 |
| OkHttp | 4.12.0 (REST + WebSocket) |
| Retrofit | 2.11.0 + `converter-kotlinx-serialization` |
| DataStore Preferences | 1.1.1 |
| core-splashscreen | 1.0.1 |
| Coil | 2.7.0 (remote avatars) |

All versions live in `gradle/libs.versions.toml`.

## Open and run

1. In Android Studio, choose **File → Open** and select this `android/` folder. Do not open the repository root.
2. Let Gradle sync. Studio writes `local.properties` with your SDK path; that file is git-ignored.
3. Start the backend, which listens on `:8080` by default (see `../backend`). For local testing, run it with `OTP_DEV_MODE=true`. The server then returns the OTP in `dev_code`, and the app shows it on the code screen so you can tap to fill it in.
4. Run the `app` configuration on an emulator.

From the command line:

```bash
./gradlew :app:installDebug        # build and install the debug APK
./gradlew :app:assembleRelease     # release build (unsigned; minify is disabled)
```

## Backend base URL

The app reads the base URL from `BuildConfig.API_BASE_URL`. The WebSocket URL comes from the same value: `<base>/ws?token=…`. OkHttp switches `http` to `ws` and `https` to `wss` on its own.

| Where you run | Base URL |
|---|---|
| Android emulator to backend on your machine (default) | `http://10.0.2.2:8080/api/v1` |
| Physical device over USB with `adb reverse tcp:8080 tcp:8080` | `http://localhost:8080/api/v1` |
| Physical device over Wi-Fi | `http://<your-LAN-IP>:8080/api/v1` (see the cleartext note below) |
| Production | `https://chat.example.com/api/v1` |

You can change it in any of these ways, highest priority first:

- **Command line:** `./gradlew :app:installDebug -Pnokat.apiBaseUrl=http://192.168.1.20:8080/api/v1`
- **Per machine:** add `nokat.apiBaseUrl=…` to `~/.gradle/gradle.properties`.
- **Project default:** edit `nokat.apiBaseUrl` in `gradle.properties`.

The fallback in `app/build.gradle.kts` is `http://10.0.2.2:8080/api/v1`.
The current value is shown at the bottom of **Settings**.

### Cleartext HTTP (development only)

- **Release builds** allow HTTPS only (`app/src/main/res/xml/network_security_config.xml`).
- **Debug builds** override that file with `app/src/debug/res/xml/network_security_config.xml`, which allows plain `http://` and `ws://` only to `10.0.2.2`, `localhost` and `127.0.0.1`.
- **Physical device over Wi-Fi:** add your machine's LAN IP as another `<domain>` in the debug file, or use `adb reverse` together with a `localhost` base URL.

## Architecture

```
com.nokat.chat
├── NokatApplication / MainActivity    single activity, splash, edge-to-edge
├── di/AppContainer                    manual DI (no Hilt): Json, OkHttp, Retrofit, repos, socket
├── data
│   ├── model/        @Serializable API models + request/response bodies
│   ├── remote/       Retrofit NokatApi, AuthInterceptor (Bearer + 401 → logout), error mapping
│   ├── local/        SessionStore (DataStore: token + user JSON)
│   ├── session/      SessionManager (in-memory session StateFlow)
│   ├── realtime/     RealtimeClient (OkHttp WebSocket) + WsEvent
│   └── repo/         AuthRepository, UserRepository, ChatRepository
├── ui
│   ├── theme/        brand colour schemes (light/dark) + messenger colours
│   ├── common/       Avatar, GradientTopBar, MessageTicks, buttons, VM factory helper
│   ├── navigation/   type-safe routes + NavHost
│   ├── auth/         Login (phone) and OTP (6 boxes, auto-submit, 30 s resend)
│   ├── profile/      first-run profile setup (shares ProfileViewModel with settings)
│   ├── chats/        chat list (search, unread, typing, online, pull to refresh)
│   ├── newchat/      user search → direct chat, new group (title + multi-select)
│   ├── conversation/ messages, composer, paging, optimistic send
│   └── settings/     edit profile, logout
└── util/             time formatting (Turkmen labels), phone normalisation, UiText
```

- **UI pattern:** MVVM. ViewModels expose `StateFlow`s, or Compose `mutableStateOf` for text input. Screens collect them with `collectAsStateWithLifecycle`.
- **Realtime:** one `RealtimeClient` per process.
  - It exposes a `SharedFlow<WsEvent>`.
  - It reconnects with exponential backoff from 1 s up to 30 s, and retries at once when the network comes back.
  - It sends an app-level `ping` every 25 s and emits `Connected` after every reconnect, so the chat list and the open conversation refetch.
- **Messages:**
  - The list uses `reverseLayout` with stable keys.
  - Older pages load through `?before=<oldest id>&limit=50`.
  - Sending is optimistic: a local bubble appears with a `client_id`. The REST response and the WebSocket echo both replace it by `client_id` or `id`. A failed send shows a retry action.
  - Typing events are throttled to one `typing:true` every 3 s, and `typing:false` is sent after 4 s idle. Remote typing indicators expire after 6 s.
  - The chat is marked read while it is visible (on resume and on each new incoming message).
- **Attachments:**
  - The paperclip in the composer opens a menu. "Surat" opens the system Photo Picker (backported to API 26+ by androidx.activity) and "Faýl" opens the document picker. Neither needs storage permissions.
  - Files are streamed from the `ContentResolver` to `POST /uploads` (multipart field `file`) with progress, then sent with `attachment_id`.
  - Files over 20 MB are rejected before upload with a snackbar.
  - The composer text becomes the caption of the first attachment.
  - Root-relative attachment URLs are resolved against the origin of the base URL (`FileUrls`).
  - Tapping a photo opens a fullscreen viewer with pinch zoom, share and download. Tapping a file opens its URL with `ACTION_VIEW`.
- **Language:** all UI strings are in Turkmen, in `res/values/strings.xml`.

## Secret chats (end-to-end encryption)

The client implements [`../docs/SECRET_CHATS.md`](../docs/SECRET_CHATS.md) v1. Like Telegram, each secret chat is bound to one device on each side.

| Part | Where |
|---|---|
| Crypto: P-256 ECDH, HKDF-SHA256, AES-256-GCM with AAD `chat_id\|sender_id`, fingerprint | `data/crypto/SecretCrypto.kt` (pure JVM, no `android.*`) |
| Per-chat private keys | `data/crypto/SecretKeyStore.kt`: PKCS#8 wrapped with an Android Keystore AES-256-GCM key (alias `nokat_e2e_wrap`) in app-private prefs |
| Key lifecycle, decryption cache, encrypted files | `data/repo/SecretChatManager.kt` |

- **Starting a chat:** "Gizlin söhbet aç" appears in the chat-list menu, in the new-chat screen, and in a direct chat's ⋮ menu.
- **Accepting:** the peer sees an invitation with "Kabul et" or "Ret et". Accepting generates this device's key; declining deletes the chat.
- **Active chats:**
  - The header shows a teal lock. Tapping it shows the 8-group fingerprint ("Şifrleme açary") to compare with the other person.
  - "Gizlin söhbeti poz" deletes the chat for both sides.
  - Devices without the key see "🔒 Bu gizlin söhbet başga enjamda açyldy" and cannot send.
- **Screen capture:** the window has `FLAG_SECURE` while a secret chat is open, so screenshots and recordings are blocked.
- **Where plaintext lives:**
  - Plaintext and derived chat keys are kept in memory only.
  - Photos and files are encrypted with a random key per file and uploaded as `encrypted.bin`. They are downloaded and decrypted in memory for display.
  - Opening a file in another app writes a decrypted copy to `cacheDir/secret/`, shared through `FileProvider`. That folder is wiped when the chat screen closes and on logout.
- **Keys are deleted:** when a chat is deleted (`chat.deleted` or locally) and on logout, including the automatic logout after a 401.

### Verifying the crypto on a plain JVM

`tools/crypto-check/SecretCryptoCheck.java` is a 1:1 Java twin of `SecretCrypto.kt`. It checks against `docs/secret-chat-test-vectors.json`:

- shared secret and chat key, from both sides
- decrypting the vector ciphertext
- re-encrypting with the fixed IV, which must reproduce the ciphertext byte for byte
- that the wrong AAD is rejected
- fingerprint
- file blob, both directions
- 300 random key pairs

Run it with Java 11 or newer; no Android SDK is needed:

```bash
cd android/tools/crypto-check
java SecretCryptoCheck.java ../../../docs/secret-chat-test-vectors.json   # prints ALL OK
```

The Kotlin file was also compiled with kotlinc 2.0.21 and run against the same vectors.

## Notes and limitations

- There are no push notifications (FCM). The socket stays connected while the app process is alive and the user is signed in.
- Avatars are deterministic gradients with initials. If `avatar_url` is an absolute `http(s)` URL, Coil loads it. Uploading avatars is not implemented.
- R8/minify is disabled for release. Before enabling it, check `proguard-rules.pro` and test.
