import SwiftUI

/// Shared navigation state so push taps and URL opens reach the tab stacks that own `NavigationPath`.
@MainActor
@Observable
final class AppNavigationState {
    static weak var current: AppNavigationState?

    var selectedTab = 0
    var chatPath = NavigationPath()
    var mailPath = NavigationPath()
    var tabsActive = false

    private var pendingChatId: String?
    private var pendingMailId: String?

    func markTabsActive() {
        tabsActive = true
        flushPending()
    }

    func openChat(_ id: String) {
        selectedTab = 1
        guard tabsActive else {
            pendingChatId = id
            return
        }
        chatPath.append(id)
    }

    func openMail(_ id: String) {
        selectedTab = 2
        guard tabsActive else {
            pendingMailId = id
            return
        }
        mailPath.append(id)
    }

    func handleDeepLink(_ url: URL) {
        guard url.scheme == AppConfig.deepLinkScheme else { return }
        if url.host == "chat", let id = url.pathComponents.last, id != "/" {
            openChat(id)
        }
        if url.host == "mail", let id = url.pathComponents.last, id != "/" {
            openMail(id)
        }
    }

    func handlePushPayload(_ userInfo: [AnyHashable: Any]) {
        if let id = userInfo["conversationId"] as? String {
            openChat(id)
        } else if let id = userInfo["threadId"] as? String {
            openMail(id)
        }
    }

    private func flushPending() {
        if let id = pendingChatId {
            pendingChatId = nil
            chatPath.append(id)
        }
        if let id = pendingMailId {
            pendingMailId = nil
            mailPath.append(id)
        }
    }
}
