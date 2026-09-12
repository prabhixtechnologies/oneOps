import Foundation

@MainActor
@Observable
final class AuthViewModel {
    var loading = false
    var error: String?

    var isLoggedIn: Bool { KeychainTokenStore.shared.session != nil }
    var hasOrganization: Bool { KeychainTokenStore.shared.session?.organizationId != nil }

    func loginWithIdentity() async {
        loading = true
        error = nil
        defer { loading = false }
        do {
            try await AuthService.loginWithIdentity()
        } catch let apiErr as ApiError {
            error = apiErr.errorDescription
        } catch {
            self.error = error.localizedDescription
        }
    }

    func logout() async {
        await AuthService.logout()
    }
}
