import Foundation

actor TokenRefresher {
    private let client: APIClient
    private let store = KeychainTokenStore.shared
    private var inFlight: Task<String?, Never>?

    init(client: APIClient) {
        self.client = client
    }

    func accessToken(force: Bool = false) async -> String? {
        if let task = inFlight { return await task.value }
        let task = Task<String?, Never> {
            guard var session = store.session else { return nil }
            if !force, session.expiresAt.timeIntervalSinceNow > 60 {
                return session.accessToken
            }
            do {
                let refreshed: TokenResponse = try await client.request(
                    path: "auth/refresh",
                    method: "POST",
                    body: RefreshRequest(refreshToken: session.refreshToken),
                    authenticated: false
                )
                store.save(tokens: refreshed)
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
    private lazy var refresher = TokenRefresher(client: self)

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
    static func login(email: String, password: String) async throws {
        let tokens: TokenResponse = try await APIClient.shared.request(
            path: "auth/login",
            method: "POST",
            body: LoginRequest(
                email: email,
                password: password,
                deviceId: KeychainTokenStore.shared.deviceId,
                deviceName: UIDeviceName.current
            ),
            authenticated: false
        )
        KeychainTokenStore.shared.save(tokens: tokens)
        let me: AuthMeResponse = try await APIClient.shared.request(path: "auth/me")
        KeychainTokenStore.shared.saveProfile(me)
        await RealtimeService.shared.start()
        await PushService.shared.registerIfPossible()
    }

    static func logout() async {
        if let refresh = KeychainTokenStore.shared.session?.refreshToken {
            _ = try? await APIClient.shared.request(
                path: "auth/logout",
                method: "POST",
                body: LogoutRequest(refreshToken: refresh)
            ) as EmptyResponse?
        }
        await PushService.shared.unregisterIfNeeded()
        await RealtimeService.shared.stop()
        KeychainTokenStore.shared.clear()
    }

    static func selectOrganization(_ id: String) async throws {
        let tokens: TokenResponse = try await APIClient.shared.request(
            path: "organizations/\(id)/select",
            method: "POST"
        )
        KeychainTokenStore.shared.save(tokens: tokens)
        KeychainTokenStore.shared.setOrganizationId(id)
        await RealtimeService.shared.restart()
    }
}

private struct EmptyResponse: Decodable {}

enum UIDeviceName {
    static var current: String {
        #if os(iOS)
        return UIDevice.current.name
        #else
        return "iOS Device"
        #endif
    }
}

#if os(iOS)
import UIKit
#endif
