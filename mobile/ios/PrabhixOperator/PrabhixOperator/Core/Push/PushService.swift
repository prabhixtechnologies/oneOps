import Foundation

#if canImport(UIKit)
import UIKit
#endif

actor PushService {
    static let shared = PushService()

    private var lastToken: String?

    func registerIfPossible() async {
        #if canImport(UIKit)
        guard KeychainTokenStore.shared.session != nil else { return }
        // APNs token registration happens in AppDelegate; this sends to backend when available.
        guard let token = lastToken else { return }
        let body = PushTokenRequest(
            token: token,
            platform: "APNS",
            deviceId: KeychainTokenStore.shared.deviceId,
            deviceName: UIDeviceName.current,
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        )
        do {
            let _: EmptyBody = try await APIClient.shared.request(
                path: "devices/push-tokens",
                method: "POST",
                body: body
            )
        } catch {
            print("Push registration skipped (endpoint may not exist): \(error.localizedDescription)")
        }
        #endif
    }

    func setDeviceToken(_ token: String) async {
        lastToken = token
        await registerIfPossible()
    }

    func unregisterIfNeeded() async {
        guard let token = lastToken else { return }
        _ = try? await APIClient.shared.request(
            path: "devices/push-tokens/\(token)",
            method: "DELETE"
        ) as EmptyBody?
        lastToken = nil
    }
}

private struct EmptyBody: Decodable {}
