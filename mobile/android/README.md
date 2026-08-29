# Prabhix Android

Two apps from one module, chosen by product flavor:

| Flavor | Application ID | Name | Who it is for |
|--------|----------------|------|---------------|
| `oneops` | `com.prabhix.operator` | Prabhix OneOps | Customers, and Prabhix's own team. Chat, mail, visitors and KPIs for one organization. |
| `admin` | `com.prabhix.admin` | Prabhix Admin | Prabhix staff. The platform overview and tenant directory — one screen, read-only. |

The application ID stays `com.prabhix.operator` despite the app being called OneOps. It is the app's
identity to Play and to every phone that already has it, so renaming it would publish an unrelated
second app and strand existing installs on a version that never updates. Only the label changed.

Both install side by side — different application IDs — and the admin icon is on a near-black
background rather than purple so they are distinguishable on the launcher.

## What actually differs

Almost everything above the network layer. These are two apps, not one app with a flag.

**Admin has one screen**: the platform overview and the tenant directory. No bottom bar, because
there is nowhere else to go. The tenant rows are not tappable — opening a customer means opening the
customer's screens, and those are the product's, on the web, where the handoff carries the
organization across and announces the access. A phone-sized reimplementation of somebody else's
inbox would be a second copy of the product to keep in step with the first.

It also has no push and no deep-link scheme. Every notification this platform sends addresses a
conversation or a mail thread, and admin has no screen to open one in, so it does not advertise a
scheme it would only have to ignore. Firebase is a `oneopsImplementation` dependency for the same
reason.

### How the source sets divide

| Source set | Holds | In which APK |
|---|---|---|
| `src/main/` | Sign-in, organization selection, tokens, the HTTP client, `SessionLifecycle` | Both |
| `src/oneops/` | Chat, mail, dashboard, visitors; the Room database, paging, SSE, push, the send queue, and the product's Retrofit APIs | OneOps only |
| `src/admin/` | The platform screen, the cross-tenant API, its repository and DI | Admin only |

Each flavor supplies its **own** `PrabhixNavHost` with the same signature, which is what lets
`MainActivity` stay shared while referring to screens that exist in only one app.

`SessionLifecycle` (in `src/main/`) is the seam that made the data layer separable. Sign-in is
shared, but what follows it is not: OneOps opens its streams, registers for push and schedules the
send queue, while admin does nothing at all. `AuthRepository` used to call `RealtimeHub` and
`PushTokenManager` directly, which meant Hilt held providers for them in **both** apps — so R8 could
prove nothing unreachable and the staff APK shipped the streaming client and the database it feeds.

That is the general trap here: it is not enough for a screen to be unreachable. If anything in the
dependency graph can provide a thing, it stays in the APK. Hence one `@Provides` per flavor
(`ProductApiModule` vs `PlatformModule`) rather than one shared module listing everything.

Verified by the leakage audit in both directions: no `admin/platform/*` in the OneOps APK, and no
`chat/conversations`, `mail/threads`, `chat/stream`, `prabhix_operator.db`, `outbound_flush` or
Firebase in the admin APK. Release APKs are 2.95 MB (OneOps) and 2.56 MB (admin).

Task names take the flavor: `assembleOneopsDebug`, `assembleAdminRelease`, and so on. A bare
`assembleDebug` builds both.

## Prerequisites

- Android Studio Ladybug (2024.2+) or newer
- JDK 17
- Android SDK 35
- Backend running (see below)
- Firebase project, for push only. Everything else works without it — see
  [Push notifications](#push-notifications-fcm).

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
./gradlew :app:assembleOneopsDebug :app:assembleAdminDebug
```

On Windows: `gradlew.bat :app:assembleOneopsDebug`

Output: `app/build/outputs/apk/{oneops,admin}/debug/app-{oneops,admin}-debug.apk`

In Android Studio, pick the variant from **Build > Select Build Variant**.

Open the `mobile/android` folder in Android Studio and Run on a device/emulator.

## Release build (installable APK)

The `release` build type already points at `https://api.prabhixtechnologies.com/api/v1`, so a
release APK is what you install on a real phone — the debug one targets `10.0.2.2`, which only
resolves inside the emulator.

Release builds are signed from `keystore.properties` in `mobile/android/` (git-ignored, as is the
`.jks` it references):

```properties
storeFile=prabhix-release.jks
storePassword=<store password>
keyAlias=prabhix
keyPassword=<key password>
```

To create the keystore if you do not have it:

```bash
keytool -genkeypair -v -keystore prabhix-release.jks -alias prabhix \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Prabhix Technologies, O=Prabhix Technologies, C=IN"
```

> **Back the keystore up somewhere durable.** Android identifies an app by its signature, so
> losing this file means you can never update an installed app in place — users would have to
> uninstall first, and Play Store updates become impossible under the same package name.

```bash
./gradlew :app:assembleOneopsRelease :app:assembleAdminRelease
```

Output: `app/build/outputs/apk/{oneops,admin}/release/app-{oneops,admin}-release.apk`

Both flavors are signed by the same keystore. Play requires one signing key per application ID only
in the sense that the key must stay the same for a given ID forever; sharing one key across two of
your own apps is fine and one fewer thing to lose.

Without `keystore.properties` the build still succeeds but the APK is **unsigned** and Android
refuses to install it. That is deliberate: it keeps CI and fresh clones building without the
private key, instead of silently shipping something signed by a throwaway debug key.

### Sideloading

Transfer the APK to the phone (USB, Drive, or email), tap it, and allow *Install unknown apps*
for whichever app is doing the transferring. Play Protect may warn that the developer is
unrecognised — expected for a self-signed internal build.

### R8 / ProGuard

`isMinifyEnabled = true` on release. The keep rules in `app/proguard-rules.pro` are
release-blocking: Kotlinx Serialization generates `Companion.serializer()` members that nothing
calls directly, so without those rules R8 strips them and the APK installs fine and then fails to
parse every API response. After changing dependencies or the rules, verify the serializers
survived:

```bash
unzip -p app/build/outputs/apk/oneops/release/app-oneops-release.apk classes.dex | strings | grep '$$serializer' | head
```

## Push notifications (FCM)

The backend side is done — `POST /api/v1/devices/push-tokens` exists and the FCM provider sends
through it. What is missing is the Firebase project.

1. Create one Firebase project and add **four** Android apps to it, one per variant:
   `com.prabhix.operator`, `com.prabhix.operator.debug`, `com.prabhix.admin`,
   `com.prabhix.admin.debug`. Fewer than four and the Google Services plugin fails the build for
   whichever variant it cannot find.
2. Download `google-services.json` and put it at `app/google-services.json`. One file covers all
   four; see `app/google-services.json.template` for the shape. It is git-ignored.
3. Nothing else. `app/build.gradle.kts` applies the Google Services plugin when that file exists and
   skips it when it does not, so the build stays green either way.
4. On the server, set `PUSH_PROVIDER=FCM`, `FCM_PROJECT_ID` and `FCM_SERVICE_ACCOUNT_JSON` from a
   service-account key for the same project. That is a different credential from
   `google-services.json` and belongs only on the server. Until `PUSH_PROVIDER` is set the backend
   uses its logging provider, so tokens register and nothing is delivered.

Two application IDs means two FCM registrations from one phone if both apps are installed, which is
correct: a notification for your own organization's chat should not open the customer product.

Without `google-services.json` the app is fully usable. `FirebaseMessaging.getInstance()` throws
`Default FirebaseApp is not initialized`, which `PushTokenManager` catches and logs. Everything
served over the API keeps working, including live chat and mail via SSE; only background push is
missing, so you get updates while the app is open but not when it is closed.

Note that notification permission is requested at first launch on Android 13 and later. Declaring
`POST_NOTIFICATIONS` in the manifest is not enough there: an ungranted app still receives its FCM
messages but is not allowed to post a notification, which looks exactly like push being broken.

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

Login (password / OTP / magic link) → Org select → Bottom nav: **Dashboard**, **Chat**
(mine/unassigned/all queues), **Mail**, **Live visitors** → Conversation/thread detail.

The admin flavor adds a fifth tab, **Platform**, shown only to an account the server reports as
`platformAdmin`: the platform counts (tenants, accounts, backlogs, last 24 hours) and the tenant
directory. Choosing *View as* on a tenant sends that organization's id on every subsequent request
and reopens the live streams against it, so the rest of the app shows that customer. A banner sits
above every screen for as long as it lasts, and the state is deliberately not persisted — it ends
when the process does, because the dangerous version of this feature is the one you forget you left
on. The server records each access.

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
