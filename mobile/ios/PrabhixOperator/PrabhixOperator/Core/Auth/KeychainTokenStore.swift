import Foundation
import Security

final class KeychainTokenStore {
    static let shared = KeychainTokenStore()

    private let service = "com.prabhix.operator.session"
    private let deviceIdKey = "device_id"
    private let biometricKey = "biometric_enabled"

    private init() {}

    var deviceId: String {
        if let existing = read(key: deviceIdKey) { return existing }
        let id = UUID().uuidString
        write(key: deviceIdKey, value: id)
        return id
    }

    struct Session: Codable {
        var accessToken: String
        var refreshToken: String
        var expiresAt: Date
        var organizationId: String?
        var permissions: Set<String>
        var userId: String?
        var email: String?
        var displayName: String?
        /// Handed back as `id_token_hint` on logout so Identity ends the browser session.
        var idToken: String?
    }

    var session: Session? {
        guard let data = readData(key: "session") else { return nil }
        return try? JSONDecoder().decode(Session.self, from: data)
    }

    func save(tokens: TokenResponse) {
        var current = session ?? Session(
            accessToken: tokens.accessToken,
            refreshToken: tokens.refreshToken,
            expiresAt: Date().addingTimeInterval(TimeInterval(tokens.expiresInSeconds)),
            organizationId: tokens.organizationId,
            permissions: tokens.permissions,
            userId: nil,
            email: nil,
            displayName: nil,
            idToken: nil
        )
        current.accessToken = tokens.accessToken
        current.refreshToken = tokens.refreshToken
        current.expiresAt = Date().addingTimeInterval(TimeInterval(tokens.expiresInSeconds))
        current.organizationId = tokens.organizationId
        current.permissions = tokens.permissions
        persist(session: current)
    }

    /// Tokens from Identity's token endpoint (no org / permissions — those come from `/auth/me`).
    func saveOidcTokens(
        accessToken: String,
        refreshToken: String?,
        idToken: String?,
        expiresAt: Date
    ) {
        var current = session ?? Session(
            accessToken: accessToken,
            refreshToken: refreshToken ?? "",
            expiresAt: expiresAt,
            organizationId: nil,
            permissions: [],
            userId: nil,
            email: nil,
            displayName: nil,
            idToken: idToken
        )
        current.accessToken = accessToken
        if let refreshToken { current.refreshToken = refreshToken }
        current.expiresAt = expiresAt
        current.idToken = idToken ?? current.idToken
        persist(session: current)
    }

    func saveProfile(_ me: AuthMeResponse) {
        guard var current = session else { return }
        current.userId = me.userId
        current.email = me.email
        current.displayName = me.displayName
        current.organizationId = me.organizationId
        current.permissions = me.permissions
        persist(session: current)
    }

    func setOrganizationId(_ id: String?) {
        guard var current = session else { return }
        current.organizationId = id
        persist(session: current)
    }

    func hasPermission(_ code: String) -> Bool {
        session?.permissions.contains(code) ?? false
    }

    var biometricEnabled: Bool {
        read(key: biometricKey) == "1"
    }

    func setBiometricEnabled(_ enabled: Bool) {
        write(key: biometricKey, value: enabled ? "1" : "0")
    }

    func clear() {
        delete(key: "session")
    }

    private func persist(session: Session) {
        if let data = try? JSONEncoder().encode(session) {
            writeData(key: "session", data: data)
        }
    }

    private func read(key: String) -> String? {
        guard let data = readData(key: key) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func write(key: String, value: String) {
        writeData(key: key, data: Data(value.utf8))
    }

    private func readData(key: String) -> Data? {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return data
    }

    private func writeData(key: String, data: Data) {
        delete(key: key)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    private func delete(key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
