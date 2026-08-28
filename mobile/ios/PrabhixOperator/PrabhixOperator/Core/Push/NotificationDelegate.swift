import Foundation
import UserNotifications

/// Foreground presentation and tap-to-open share the same deep-link router as `prabhix://` URLs.
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationDelegate()

    private var pendingPayload: [AnyHashable: Any]?

    func flushPendingNavigation() {
        guard let payload = pendingPayload else { return }
        pendingPayload = nil
        Task { @MainActor in
            AppNavigationState.current?.handlePushPayload(payload)
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        Task { @MainActor in
            if AppNavigationState.current != nil {
                AppNavigationState.current?.handlePushPayload(userInfo)
            } else {
                pendingPayload = userInfo
            }
        }
        completionHandler()
    }
}
