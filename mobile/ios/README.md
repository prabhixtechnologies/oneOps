# Prabhix OneOps — iOS

Native SwiftUI operator app (iPhone + iPad, portrait and landscape) for chat, mail, live visitors, and dashboard KPIs.

The target and directory are still named `PrabhixOperator`, which is what the app was called before
the product was named. The bundle identifier and the on-disk paths are deliberately left alone; only
the user-visible label is "Prabhix OneOps", set by `PRABHIX_APP_LABEL` in `project.yml`.

## Status: not yet compiled

Read this before starting. There are ~2,000 lines of Swift here that **no compiler has ever seen**,
because the project has only ever been developed from Windows and Xcode does not run there. It is a
detailed draft, not a working app. Everything else in this repository — backend, web, Android — is
built and verified in CI; this is not.

Expect the first build to produce a real list of errors, and budget for that rather than treating it
as a surprise. Nothing about the design is blocked; only the tooling is.

**There is no `.xcodeproj` in this repository either**, and there should not be: it cannot be created
or reviewed from Windows. Instead [project.yml](project.yml) is a text spec that generates it, which
*can* be kept correct from any machine. `Package.swift` compiles only the shared `Core` directory,
for a rough syntax check without a full project.

## Prerequisites

- macOS with **Xcode 16+**
- [XcodeGen](https://github.com/yonaskolb/XcodeGen): `brew install xcodegen`
- **iOS 17+** deployment target
- Backend reachable at `/api/v1` (see [API base URL](#api-base-url))
- Apple Developer account, for push on physical devices

## Setup

```bash
cd mobile/ios
cp Config.xcconfig.template Config.xcconfig   # then set DEVELOPMENT_TEAM
xcodegen generate
open PrabhixOperator.xcodeproj
```

That is the whole setup. `project.yml` already declares both targets, the Info.plist wiring, the
orientations, the push and background-task entitlements and the signing style, so there are no
manual Xcode steps to get subtly wrong or to repeat on the next machine.

Re-run `xcodegen generate` after editing `project.yml`. Source files are referenced by directory, so
**adding a Swift file needs no regeneration** — only adding a new directory does.

### Config.xcconfig

Gitignored; holds only what differs per developer:

```
API_BASE_URL = http://192.168.x.x:8080/api/v1   # LAN IP for a physical device
DEVELOPMENT_TEAM = YOUR_TEAM_ID
CODE_SIGN_STYLE = Automatic
```

## Two apps, one source tree

The same split as the web console and Android: the product sold to customers, and the private admin
app used to run the company.

| | Target | Bundle id | Deep link | `X-Prabhix-Device` |
|---|---|---|---|---|
| OneOps | `PrabhixOperator` | `com.prabhix.operator` | `prabhix://` | `mobile-ios` |
| Admin | `PrabhixAdmin` | `com.prabhix.admin` | `prabhix-admin://` | `mobile-ios-admin` |

Two bundle ids, so both install side by side and the App Store treats them as the two products they
are. The deep-link schemes differ because both apps would otherwise claim `prabhix://chat/{id}` and
iOS would pick one arbitrarily — a customer's conversation opening in the wrong app.

Per-target values are build settings that `Info.plist` substitutes and
[AppConfig](PrabhixOperator/PrabhixOperator/Core/AppConfig.swift) reads back at runtime, which is the
iOS counterpart of Android's `BuildConfig` fields, deliberately using the same names:

| Build setting | Purpose |
|---|---|
| `PRABHIX_APP_LABEL` | Names the app, and names it again in the sessions list |
| `PRABHIX_DEVICE_HEADER` | Tells the backend which app a request came from |
| `PRABHIX_DEEP_LINK_SCHEME` | Unique per app, per above |
| `PRABHIX_ADMIN` | A compilation condition, set on the admin target only |

`PRABHIX_ADMIN` is a compilation condition rather than a runtime flag for the same reason the web
build uses a Vite define and Android uses source sets: `#if` removes the code, so the customer's
binary does not contain the platform surface at all. A runtime check would ship it and hide it.

### Remaining work: the admin screens

The two targets are defined and differ correctly, but they currently build the *same* screens — all
eight of which are tenant-scoped. The platform surface (platform overview and tenant directory,
plus the "view as" banner) exists on web and Android and not here. When it is written it goes in its
own directory added to the admin target only, exactly like `app/src/admin` on Android;
`project.yml` marks the spot. Mirror
[PlatformFeature.kt](../android/app/src/admin/java/com/prabhix/operator/ui/platform/PlatformFeature.kt)
for the seam and
[PlatformScreen.kt](../android/app/src/admin/java/com/prabhix/operator/ui/platform/PlatformScreen.kt)
for the screen.

## Info.plist

Values marked `$(...)` come from the build settings above.

| Key | Purpose |
|-----|---------|
| `CFBundleName` | `$(PRABHIX_APP_LABEL)` |
| `PrabhixAPIBase` | REST base URL on device builds |
| `PrabhixDeviceHeader`, `PrabhixDeepLinkScheme` | Read by `AppConfig` |
| `CFBundleURLTypes` | Deep-link scheme, per target |
| `NSAppTransportSecurity` → `NSAllowsLocalNetworking` | Local backend during development |
| `NSFaceIDUsageDescription` | Biometric unlock prompt |
| `UIBackgroundModes` | `fetch`, `processing`, `remote-notification` |
| `BGTaskSchedulerPermittedIdentifiers` | `$(PRODUCT_BUNDLE_IDENTIFIER).flush` |
| Orientation arrays | iPhone + iPad portrait and landscape |

The background-task identifier is derived from the bundle id in both the plist and
`AppConfig.backgroundTaskIdentifier`, because `BGTaskScheduler` throws on an identifier the plist
does not list, and two hardcoded copies are how that happens.

## API base URL

| Target | URL |
|--------|-----|
| Simulator | `http://localhost:8080/api/v1` (hard-coded in `AppConfig.swift`) |
| Physical device | `http://<LAN_IP>:8080/api/v1` via `Config.xcconfig` → `PrabhixAPIBase` |
| Production | `https://api.prabhixtechnologies.com/api/v1` (fallback in `AppConfig.swift`) |

## Push (APNs)

The capability and the entitlement are declared in `project.yml`, so there is nothing to click. The
app side is written: on launch `AppDelegate` requests notification permission, registers for remote
notifications, and posts the token to `POST /api/v1/devices/push-tokens`, which the backend already
serves. Tap handling maps `conversationId` → `{scheme}://chat/{id}` and `threadId` →
`{scheme}://mail/{id}`, the same payload contract as Android.

What is missing is credentials. Set `APNS_TEAM_ID`, `APNS_KEY_ID`, `APNS_PRIVATE_KEY` and
`APNS_BUNDLE_ID` on the backend, along with `PUSH_PROVIDER=APNS`; until `PUSH_PROVIDER` is set the
backend logs instead of sending.

**The backend cannot yet serve both iOS apps.** `apns-topic` is the bundle id, so
`com.prabhix.operator` and `com.prabhix.admin` are two topics, but `ApnsPushProvider` sends every
notification to the single `APNS_BUNDLE_ID` it is configured with, and `push_tokens` records only
`FCM` or `APNS` — not which app or which APNs environment a token came from. So whichever bundle id
is configured works and the other silently does not. One APNs auth key covers both apps under the
same Apple Developer team, so the credential is not the problem; the missing piece is per-token
routing.

Android does not have this problem because FCM identifies the app from the token itself, which is
why it needed nothing beyond registering both apps in Firebase.

Resolving it means storing the app and environment alongside the token and choosing the topic and
host per send. That work belongs with the rest of iOS and is not worth doing before the app
compiles, since nothing can register an iOS token until then.

Deep links and notification taps require the operator to be signed in with an organization selected.

## Architecture

- **UI:** SwiftUI + `@Observable` MVVM, per-tab `NavigationStack(path:)` with `navigationDestination(for: String.self)`
- **Navigation:** `AppNavigationState` coordinates tab selection, chat/mail paths, deep links, and push payloads
- **Network:** `URLSession` + `async/await`, actor-based single-flight token refresh (`APIClient`)
- **Tokens:** Keychain (`KeychainTokenStore`) — never UserDefaults
- **Realtime:** SSE via `URLSession.bytes`, exponential backoff (`RealtimeService`) for `chat/stream` and `mail/stream`
- **Offline:** SwiftData `CachedConversation` inbox cache + `OutboundChatMessage` queue; `OfflineFlushService` drains on launch, foreground, and `BGAppRefreshTask`
- **Biometrics:** Optional Face ID / Touch ID gate (`BiometricGate`); toggle on Dashboard
- **AI:** Non-stream REST endpoints for chat/mail suggest, rewrite, summarize (`AiService`)

## Screens

Login (Identity OIDC via ASWebAuthenticationSession) → Organization picker → Tabs:

- **Dashboard** — KPIs, optional biometric toggle
- **Chat** — mine / unassigned / all queues, conversation detail with reply, notes, AI suggest/rewrite
- **Mail** — thread list, detail with reply composer, AI suggest/summarize
- **Live** — active visitors

Deep links: `{scheme}://chat/{conversationId}`, `{scheme}://mail/{threadId}`, where the scheme is
`prabhix` or `prabhix-admin` per the table above.

The admin flavor gains a fifth **Platform** tab once those screens are written — see
[Remaining work](#remaining-work-the-admin-screens).

## Library / OS versions

Swift 5.10, iOS 17+, SwiftData, `BGTaskScheduler`, Keychain, `LocalAuthentication`, **no third-party
dependencies**. XcodeGen is a build-time tool, not a dependency of the app.

## Build

```bash
cd mobile/ios
xcodegen generate
for scheme in PrabhixOperator PrabhixAdmin; do
  xcodebuild -project PrabhixOperator.xcodeproj \
    -scheme "$scheme" \
    -destination 'platform=iOS Simulator,name=iPhone 16' \
    build
done
```

[.github/workflows/ios.yml](../../.github/workflows/ios.yml) runs exactly this on a macOS runner. It
is **not** part of the main CI pipeline and must be started by hand from the Actions tab, for two
reasons: macOS runners cost roughly ten times a Linux minute, and the build does not pass yet, so
requiring it would block every unrelated pull request. Turn it into a required check once it goes
green — that is the moment iOS stops being deferred.

## Optional: Core SPM package

`Package.swift` compiles `PrabhixOperator/PrabhixOperator/Core/` as `PrabhixOperatorCore`, for a
syntax check without a full project — useful as the first step of the shakedown, since it needs no
signing and no simulator:

```bash
cd mobile/ios && swift build
```

The app targets compile the same files directly; do not link the SPM product into them. Note that
`Core` alone may not compile even when the app does, because the app builds `Core` and `Features`
as one module and nothing has yet forced `Core` to be self-contained.
