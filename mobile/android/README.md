# Prabhix Operator — Android

Native Kotlin operator app for managing chat, mail, visitors, and dashboard KPIs from a phone at scale.

## Prerequisites

- Android Studio Ladybug (2024.2+) or newer
- JDK 17
- Android SDK 35
- Backend running (see below)
- Firebase project for push (optional until backend push endpoint ships)

## Point at your backend

Default debug API: `http://10.0.2.2:8080/api/v1` (Android Emulator → host `localhost`).

| Target | Override in `app/build.gradle.kts` `debug` block |
|--------|--------------------------------------------------|
| Emulator | `http://10.0.2.2:8080/api/v1` (default) |
| Physical device | `http://<YOUR_LAN_IP>:8080/api/v1` |
| Production | set in `release` `buildConfigField` |

Ensure PostgreSQL, Redis, and the Spring Boot API are running on the host.

## Build

The full Gradle wrapper is committed (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`,
and `gradle-wrapper.properties`), so no local Gradle install is needed — the wrapper downloads
Gradle 8.11.1 on first run.

Point Gradle at your SDK by creating `mobile/android/local.properties` (git-ignored, machine-specific):

```properties
sdk.dir=/path/to/Android/Sdk
```

Then build:

```bash
./gradlew :app:assembleDebug
```

On Windows: `gradlew.bat :app:assembleDebug`

Output: `app/build/outputs/apk/debug/app-debug.apk`

Open the `mobile/android` folder in Android Studio and Run on a device/emulator.

## Push notifications (FCM)

1. Create a Firebase project and add an Android app (`com.prabhix.operator`).
2. Download `google-services.json` into `mobile/android/app/`.
3. Uncomment the Google Services plugin in root and app `build.gradle.kts` (see comments in those files).
4. Backend must implement `POST /api/v1/devices/push-tokens` (documented in `docs/mobile-api-contract.md`).

Until the backend endpoint exists, the app logs a warning and continues without push registration. Rotated FCM tokens are re-registered via `onNewToken`.

## Architecture

- **UI:** Jetpack Compose + Material 3 (phone-first; single-pane navigation on all form factors)
- **State:** MVVM + `StateFlow`
- **DI:** Hilt
- **Network:** Retrofit + OkHttp (`AuthInterceptor` + `Authenticator` for single-flight refresh)
- **Realtime:** OkHttp SSE (`RealtimeHub`) with exponential backoff reconnect; chat and mail streams
- **Lists:** Paging 3 + cursor API
- **Offline:** Room cache + WorkManager outbound queue for chat sends (expedited flush when queued + 15‑min periodic backstop)
- **Secrets:** EncryptedSharedPreferences (Android Keystore-backed)

## Screens

Login (password / OTP / magic link) → Org select → Bottom nav: **Dashboard**, **Chat** (mine/unassigned/all queues), **Mail**, **Live visitors** → Conversation/thread detail.

### Implemented operator features

| Area | Features |
|------|----------|
| **Chat detail** | Reply, internal notes, offline queue with “Queued — will send when online”, assign to me, close/reopen, canned reply insertion, AI reply suggest + draft rewrite (`Improve` / `Shorten` / `Translate`) |
| **Mail detail** | Reply composer (plain text → HTML), canned replies, AI reply suggest, AI thread summarize, live refresh via SSE |
| **Mail inbox** | Paged thread list, live refresh on mail SSE events |
| **Dashboard** | KPI cards, recent activity, logout (unregisters push token) |
| **Deep links** | `prabhix://chat/{id}`, `prabhix://mail/{id}` from FCM notifications |

Permission-gated: `CHAT_REPLY`, `CHAT_ASSIGN`, `MAIL_SEND`, `AI_USE`, etc. AI endpoints return `available: false` when no provider is configured — the app shows a friendly hint instead of an error.

### Not implemented (yet)

- Tablet two-pane master/detail layout
- Chat unassign (no backend endpoint; mail has `/unassign`, chat does not)
- Chat tag/priority editing in mobile UI (API exists)
- Offline outbound queue for mail replies

## Library versions

See `gradle/libs.versions.toml` — AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Hilt 2.52, Paging 3.3.5, Room 2.6.1.
