import BackgroundTasks
import SwiftUI
import SwiftData

@main
struct PrabhixOperatorApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @Environment(\.scenePhase) private var scenePhase
    @State private var auth = AuthViewModel()
    @State private var navigation = AppNavigationState()
    @State private var biometricGate = BiometricGate()

    var body: some Scene {
        WindowGroup {
            Group {
                switch OfflineStore.state {
                case .ready(let container):
                    rootView
                        .modelContainer(container)
                        .task {
                            await OfflineFlushService.flushPending(context: container.mainContext)
                        }
                case .failed(let message):
                    StorageErrorView(message: message)
                }
            }
            .onAppear {
                AppNavigationState.current = navigation
                NotificationDelegate.shared.flushPendingNavigation()
            }
            .onOpenURL { url in
                navigation.handleDeepLink(url)
            }
        }
        .onChange(of: scenePhase) { _, phase in
            guard case .ready(let container) = OfflineStore.state else { return }
            switch phase {
            case .active:
                Task {
                    await OfflineFlushService.flushPending(context: container.mainContext)
                }
            case .background:
                BackgroundFlush.schedule()
            default:
                break
            }
        }
    }

    @ViewBuilder
    private var rootView: some View {
        if !auth.isLoggedIn {
            LoginView(viewModel: auth) {
                auth = auth
            }
        } else if !auth.hasOrganization {
            OrgSelectView {
                auth = auth
            }
        } else if biometricGate.isRequired && !biometricGate.unlocked {
            BiometricUnlockView(gate: biometricGate) {
                Task {
                    await auth.logout()
                    biometricGate.reset()
                }
            }
        } else {
            TabView(selection: $navigation.selectedTab) {
                DashboardView(biometricGate: biometricGate).tabItem { Label("Home", systemImage: "house") }.tag(0)
                ChatInboxView(path: $navigation.chatPath)
                    .tabItem { Label("Chat", systemImage: "bubble.left.and.bubble.right") }
                    .tag(1)
                MailInboxView(path: $navigation.mailPath)
                    .tabItem { Label("Mail", systemImage: "envelope") }
                    .tag(2)
                VisitorsView().tabItem { Label("Live", systemImage: "person.3") }.tag(3)
            }
            .task {
                if KeychainTokenStore.shared.session != nil {
                    await RealtimeService.shared.start()
                }
            }
            .onAppear { navigation.markTabsActive() }
            .onChange(of: auth.isLoggedIn) { _, loggedIn in
                if !loggedIn { biometricGate.reset() }
            }
        }
    }
}

struct StorageErrorView: View {
    let message: String

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "externaldrive.badge.exclamationmark")
                .font(.largeTitle)
            Text("Storage unavailable")
                .font(.title2.bold())
            Text(message)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
            Text("Restart the app. Chat and mail still work online; only offline cache and queued sends are affected.")
                .font(.caption)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}

enum OfflineStore {
    enum LoadState {
        case ready(ModelContainer)
        case failed(String)
    }

    static let state: LoadState = {
        do {
            return .ready(try ModelContainer(for: CachedConversation.self, OutboundChatMessage.self))
        } catch {
            do {
                return .ready(try ModelContainer(
                    for: CachedConversation.self, OutboundChatMessage.self,
                    configurations: ModelConfiguration(isStoredInMemoryOnly: true)
                ))
            } catch let fallbackError {
                return .failed(fallbackError.localizedDescription)
            }
        }
    }()
}

enum BackgroundFlush {
    static let identifier = "com.prabhix.operator.flush"

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            schedule()
            let work = Task { @MainActor in
                if case .ready(let container) = OfflineStore.state {
                    await OfflineFlushService.flushPending(context: container.mainContext)
                }
                task.setTaskCompleted(success: true)
            }
            task.expirationHandler = { work.cancel() }
        }
    }

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 5 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                       didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = NotificationDelegate.shared
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { _, _ in }
        application.registerForRemoteNotifications()
        BackgroundFlush.register()
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        Task { await PushService.shared.setDeviceToken(token) }
    }
}

import UserNotifications
