import Foundation

actor TokenRefresher {
    private let store = KeychainTokenStore.shared
    private var inFlight: Task<String?, Never>?

    func accessToken(force: Bool = false) async -> String? {
        if let task = inFlight { return await task.value }
        let task = Task<String?, Never> {
            guard let session = store.session else { return nil }
            if !force, session.expiresAt.timeIntervalSinceNow > 60 {
                return session.accessToken
            }
            do {
                // Refresh against Identity — the platform no longer mints refresh tokens.
                let refreshed = try await IdentityAuthenticator.refresh(session.refreshToken)
                store.saveOidcTokens(
                    accessToken: refreshed.accessToken,
                    refreshToken: refreshed.refreshToken,
                    idToken: refreshed.idToken,
                    expiresAt: refreshed.expiresAt
                )
                return refreshed.accessToken
            } catch {
                return nil
            }
        }
        inFlight = task
        let token = await task.value
        inFlight = nil
        return token
    }
}

final class APIClient {
    static let shared = APIClient()

    private let jsonEncoder = JSONEncoder()
    private let jsonDecoder = JSONDecoder()
    private let store = KeychainTokenStore.shared
    private let refresher = TokenRefresher()

    func request<T: Decodable, B: Encodable>(
        path: String,
        method: String = "GET",
        query: [URLQueryItem] = [],
        body: B,
        authenticated: Bool = true,
        idempotencyKey: String? = nil
    ) async throws -> T {
        let data = try await rawRequest(path: path, method: method, query: query, body: body,
                                       authenticated: authenticated, idempotencyKey: idempotencyKey)
        return try jsonDecoder.decode(T.self, from: data)
    }

    func request<T: Decodable>(
        path: String,
        method: String = "GET",
        query: [URLQueryItem] = [],
        authenticated: Bool = true
    ) async throws -> T {
        let data = try await rawRequest(path: path, method: method, query: query, body: Optional<String>.none, authenticated: authenticated)
        return try jsonDecoder.decode(T.self, from: data)
    }

    func rawRequest<B: Encodable>(
        path: String,
        method: String,
        query: [URLQueryItem] = [],
        body: B?,
        authenticated: Bool,
        retryOn401: Bool = true,
        idempotencyKey: String? = nil
    ) async throws -> Data {
        var components = URLComponents(url: AppConfig.apiBaseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { components.queryItems = query }
        guard let url = components.url else { throw ApiError.network(URLError(.badURL)) }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(UUID().uuidString, forHTTPHeaderField: "X-Correlation-Id")
        request.setValue(AppConfig.deviceHeader, forHTTPHeaderField: "X-Prabhix-Device")
        if let idempotencyKey {
            request.setValue(idempotencyKey, forHTTPHeaderField: "Idempotency-Key")
        }

        if authenticated {
            guard let token = await refresher.accessToken() else { throw ApiError.unauthorized }
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            if let org = store.session?.organizationId {
                request.setValue(org, forHTTPHeaderField: "X-Prabhix-Org")
            }
        }

        if let body {
            request.httpBody = try jsonEncoder.encode(body)
        }

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ApiError.network(URLError(.badServerResponse))
        }

        if http.statusCode == 401, authenticated, retryOn401 {
            _ = await refresher.accessToken(force: true)
            return try await rawRequest(path: path, method: method, query: query, body: body,
                                       authenticated: authenticated, retryOn401: false,
                                       idempotencyKey: idempotencyKey)
        }

        guard (200..<300).contains(http.statusCode) else {
            if let apiErr = try? jsonDecoder.decode(ApiErrorBody.self, from: data) {
                throw ApiError.fromBody(apiErr)
            }
            throw ApiError.network(URLError(.badServerResponse))
        }
        return data
    }
}

enum AuthService {
    static func loginWithIdentity() async throws {
        let tokens = try await IdentityAuthenticator.signIn()
        KeychainTokenStore.shared.saveOidcTokens(
            accessToken: tokens.accessToken,
            refreshToken: tokens.refreshToken,
            idToken: tokens.idToken,
            expiresAt: tokens.expiresAt
        )
        let me: AuthMeResponse = try await APIClient.shared.request(path: "auth/me")
        KeychainTokenStore.shared.saveProfile(me)
        await RealtimeService.shared.start()
        await PushService.shared.registerIfPossible()
    }

    static func logout() async {
        let idHint = KeychainTokenStore.shared.session?.idToken
        await PushService.shared.unregisterIfNeeded()
        await RealtimeService.shared.stop()
        KeychainTokenStore.shared.clear()
        if let idHint {
            // Best-effort: end the shared browser session. Failure must not block local sign-out.
            _ = try? await URLSession.shared.data(from: IdentityAuthenticator.endSessionURL(idTokenHint: idHint))
        }
    }

    static func selectOrganization(_ id: String) async throws {
        KeychainTokenStore.shared.setOrganizationId(id)
        let me: AuthMeResponse = try await APIClient.shared.request(path: "auth/me")
        KeychainTokenStore.shared.saveProfile(me)
        await RealtimeService.shared.restart()
    }
}

private struct EmptyResponse: Decodable {}

enum UIDeviceName {
    /// Names the app as well as the phone, so an operator with both apps installed gets two
    /// distinguishable rows in the sessions list rather than the device name twice. Matches the
    /// Android format.
    static var current: String {
        #if os(iOS)
        return "\(UIDevice.current.name) · \(AppConfig.appLabel)"
        #else
        return "iOS Device · \(AppConfig.appLabel)"
        #endif
    }
}

#if os(iOS)
import UIKit
#endif
