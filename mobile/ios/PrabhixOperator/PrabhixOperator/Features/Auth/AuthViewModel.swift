import Foundation

@MainActor
@Observable
final class AuthViewModel {
    var email = ""
    var password = ""
    var otpCode = ""
    var magicToken = ""
    var authMode: AuthMode = .password
    var loading = false
    var error: String?
    var otpSent = false
    var magicLinkSent = false

    var isLoggedIn: Bool { KeychainTokenStore.shared.session != nil }
    var hasOrganization: Bool { KeychainTokenStore.shared.session?.organizationId != nil }

    enum AuthMode: String, CaseIterable { case password, otp, magicLink }

    func login() async {
        loading = true
        error = nil
        defer { loading = false }
        do {
            try await AuthService.login(email: email, password: password)
        } catch let apiErr as ApiError {
            error = apiErr.errorDescription
        } catch {
            self.error = error.localizedDescription
        }
    }

    func requestOtp() async {
        loading = true
        defer { loading = false }
        do {
            let _: AckBody = try await APIClient.shared.request(
                path: "auth/otp/request",
                method: "POST",
                body: EmailRequest(email: email),
                authenticated: false
            )
            otpSent = true
        } catch {
            error = (error as? ApiError)?.errorDescription ?? error.localizedDescription
        }
    }

    func verifyOtp() async {
        loading = true
        defer { loading = false }
        do {
            let tokens: TokenResponse = try await APIClient.shared.request(
                path: "auth/otp/verify",
                method: "POST",
                body: OtpVerifyRequest(email: email, code: otpCode),
                authenticated: false
            )
            try await completeLogin(tokens: tokens)
        } catch {
            error = (error as? ApiError)?.errorDescription ?? error.localizedDescription
        }
    }

    func requestMagicLink() async {
        loading = true
        defer { loading = false }
        do {
            let _: AckBody = try await APIClient.shared.request(
                path: "auth/magic-link/request",
                method: "POST",
                body: EmailRequest(email: email),
                authenticated: false
            )
            magicLinkSent = true
        } catch {
            error = (error as? ApiError)?.errorDescription ?? error.localizedDescription
        }
    }

    func verifyMagicLink() async {
        loading = true
        defer { loading = false }
        do {
            let tokens: TokenResponse = try await APIClient.shared.request(
                path: "auth/magic-link/verify",
                method: "POST",
                body: MagicLinkVerifyRequest(token: magicToken),
                authenticated: false
            )
            try await completeLogin(tokens: tokens)
        } catch {
            error = (error as? ApiError)?.errorDescription ?? error.localizedDescription
        }
    }

    private func completeLogin(tokens: TokenResponse) async throws {
        KeychainTokenStore.shared.save(tokens: tokens)
        let me: AuthMeResponse = try await APIClient.shared.request(path: "auth/me")
        KeychainTokenStore.shared.saveProfile(me)
        await RealtimeService.shared.start()
        await PushService.shared.registerIfPossible()
    }

    func logout() async {
        await AuthService.logout()
    }
}

private struct AckBody: Decodable { let message: String }
