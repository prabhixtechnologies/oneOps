# Prabhix Operator — iOS

Native SwiftUI operator app (iPhone + iPad, portrait and landscape) for chat, mail, live visitors, and dashboard KPIs.

**There is no `.xcodeproj` in this repository.** Source lives under `PrabhixOperator/PrabhixOperator/`. `Package.swift` at this directory root builds only the shared `Core` library for linting — **not** the app target. You must create an Xcode app project locally (steps below).

## Prerequisites

- macOS with **Xcode 16+**
- **iOS 17+** deployment target
- Spring Boot backend at `http://localhost:8080` with API base `/api/v1`
- Apple Developer account for push on physical devices

## First-time Xcode project setup

### 1. Create the app target

1. Open Xcode → **File → New → Project…**
2. Choose **iOS → App**
3. Settings:
   - **Product Name:** `PrabhixOperator`
   - **Team:** your Apple Developer team
   - **Organization Identifier:** `com.prabhix`
   - **Bundle Identifier:** `com.prabhix.operator` (must match APNs topic if using push)
   - **Interface:** SwiftUI
   - **Language:** Swift
   - **Storage:** SwiftData (checked)
4. Save the project as `mobile/ios/PrabhixOperator/PrabhixOperator.xcodeproj` (alongside the existing `PrabhixOperator/` source folder).

### 2. Replace template sources

Delete Xcode’s default `ContentView.swift`, `Item.swift`, and any generated model files.

In the project navigator, **Add Files to "PrabhixOperator"…** and select the entire folder:

`mobile/ios/PrabhixOperator/PrabhixOperator/`

- **Copy items if needed:** unchecked (reference files in place)
- **Create groups**
- **Add to targets:** PrabhixOperator

Ensure `@main` exists only in `PrabhixOperatorApp.swift`.

### 3. Info.plist

Point the target’s **Info.plist File** build setting to:

`PrabhixOperator/PrabhixOperator/Info.plist`

The bundled plist already includes:

| Key | Purpose |
|-----|---------|
| `PrabhixAPIBase` | Default REST base URL on device builds |
| `CFBundleURLTypes` | Custom URL scheme `prabhix://` for deep links |
| `NSAppTransportSecurity` → `NSAllowsLocalNetworking` | Local backend during development |
| `NSFaceIDUsageDescription` | Biometric unlock prompt |
| `UIBackgroundModes` | `fetch`, `processing`, `remote-notification` |
| `BGTaskSchedulerPermittedIdentifiers` | `com.prabhix.operator.flush` |
| Orientation arrays | iPhone + iPad portrait and landscape |

### 4. Config.xcconfig

```bash
cp mobile/ios/Config.xcconfig.template mobile/ios/Config.xcconfig
```

Edit `Config.xcconfig`:

```
API_BASE_URL = http://192.168.x.x:8080/api/v1   # LAN IP for physical device
DEVELOPMENT_TEAM = YOUR_TEAM_ID
PRODUCT_BUNDLE_IDENTIFIER = com.prabhix.operator
```

In Xcode: select the **project** → **Info** tab → **Configurations** → set **Debug** and **Release** to use `Config.xcconfig`.

Wire the API URL into the app:

1. Target **Build Settings** → add User-Defined setting `PRABHIX_API_BASE` = `$(API_BASE_URL)`
2. Target **Build Settings** → **Info.plist Values** (or Info tab) → set `PrabhixAPIBase` to `$(PRABHIX_API_BASE)`

Simulator builds ignore `PrabhixAPIBase` and use `http://localhost:8080/api/v1` from `AppConfig.swift`.

Launch-argument override (any build): `-PRABHIX_API_BASE http://host:8080/api/v1`

### 5. Capabilities (Signing & Capabilities tab)

Enable on the **PrabhixOperator** target:

| Capability | Notes |
|------------|--------|
| **Push Notifications** | Required for APNs; token registration is in `AppDelegate` |
| **Background Modes** | Background fetch, Background processing, Remote notifications (must match Info.plist) |
| **Background Tasks** | Identifier `com.prabhix.operator.flush` registered in `BackgroundFlush` |

### 6. Signing

- **Automatically manage signing** with your team
- Bundle ID `com.prabhix.operator` must match provisioning profile and APNs `apns-topic`

## API base URL

| Target | URL |
|--------|-----|
| Simulator | `http://localhost:8080/api/v1` (hard-coded in `AppConfig.swift`) |
| Physical device | `http://<LAN_IP>:8080/api/v1` via `Config.xcconfig` → `PrabhixAPIBase` |
| Production | `https://api.prabhixtechnologies.com/api/v1` (fallback in `AppConfig.swift`) |

## Push (APNs)

1. Enable **Push Notifications** capability (step 5).
2. Configure backend APNs credentials (`backend` push settings) with bundle ID `com.prabhix.operator`.
3. On launch, `AppDelegate` requests notification permission, registers for remote notifications, and sends the device token via `POST /api/v1/devices/push-tokens`.
4. Tap handling: payload keys `conversationId` → `prabhix://chat/{id}`, `threadId` → `prabhix://mail/{id}` (same as Android).

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

Login (password / OTP / magic link) → Organization picker → Tabs:

- **Dashboard** — KPIs, optional biometric toggle
- **Chat** — mine / unassigned / all queues, conversation detail with reply, notes, AI suggest/rewrite
- **Mail** — thread list, detail with reply composer, AI suggest/summarize
- **Live** — active visitors

Deep links: `prabhix://chat/{conversationId}`, `prabhix://mail/{threadId}`

## Library / OS versions

Swift 5.10, iOS 17+, SwiftData, `BGTaskScheduler`, Keychain, `LocalAuthentication`, **no third-party dependencies**.

## Build

After completing setup above:

```bash
cd mobile/ios
xcodebuild -project PrabhixOperator/PrabhixOperator.xcodeproj \
  -scheme PrabhixOperator \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  build
```

This has **not** been verified in CI — requires macOS + Xcode.

## Optional: Core SPM package

`Package.swift` exposes `PrabhixOperatorCore` from `PrabhixOperator/Core/` for isolated compilation checks only. The app target includes the same files directly; do not link the SPM product into the app unless you split targets intentionally.
