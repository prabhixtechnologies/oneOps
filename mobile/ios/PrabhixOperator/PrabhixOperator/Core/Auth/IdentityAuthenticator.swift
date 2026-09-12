import AuthenticationServices
import CryptoKit
import Foundation
import UIKit

/**
 * Signs in against Prabhix Identity through `ASWebAuthenticationSession`.
 *
 * Same contract as Android's IdentityAuthenticator: password never enters this process, PKCE is
 * mandatory, refresh goes to Identity (not the platform), and SSO rides the system browser cookie jar.
 */
enum IdentityAuthenticator {
    struct Tokens {
        let accessToken: String
        let refreshToken: String?
        let idToken: String?
        let expiresAt: Date
    }

    private static var issuer: String { AppConfig.identityIssuer }
    private static var clientId: String { AppConfig.oauthClientId }
    private static var redirectURI: String { AppConfig.oauthRedirectURI }
    private static var callbackScheme: String { AppConfig.oauthCallbackScheme }

    @MainActor
    static func signIn() async throws -> Tokens {
        let verifier = pkceVerifier()
        let challenge = pkceChallenge(verifier)
        let state = UUID().uuidString
        let anchor = PresentationAnchor()

        var components = URLComponents(string: "\(issuer)/oauth2/authorize")!
        components.queryItems = [
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "client_id", value: clientId),
            URLQueryItem(name: "redirect_uri", value: redirectURI),
            URLQueryItem(name: "scope", value: "openid profile email"),
            URLQueryItem(name: "code_challenge", value: challenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "state", value: state),
        ]
        if AppConfig.isAdminApp {
            components.queryItems?.append(URLQueryItem(name: "prompt", value: "select_account"))
        }
        guard let authURL = components.url else {
            throw ApiError.network(URLError(.badURL))
        }

        let callbackURL: URL = try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(
                url: authURL,
                callbackURLScheme: callbackScheme
            ) { url, error in
                anchor.session = nil
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                guard let url else {
                    continuation.resume(throwing: ApiError.network(URLError(.badServerResponse)))
                    return
                }
                continuation.resume(returning: url)
            }
            session.presentationContextProvider = anchor
            session.prefersEphemeralWebBrowserSession = false
            anchor.session = session
            if !session.start() {
                anchor.session = nil
                continuation.resume(throwing: ApiError.network(URLError(.cannotLoadFromNetwork)))
            }
        }

        let items = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let returnedState = items.first(where: { $0.name == "state" })?.value
        guard returnedState == state else {
            throw ApiError.fromBody(ApiErrorBody(
                code: "STATE_MISMATCH",
                message: "Sign-in response did not match the request.",
                fieldErrors: nil,
                traceId: nil,
                path: nil
            ))
        }
        if let err = items.first(where: { $0.name == "error" })?.value {
            throw ApiError.fromBody(ApiErrorBody(
                code: err,
                message: items.first(where: { $0.name == "error_description" })?.value ?? err,
                fieldErrors: nil,
                traceId: nil,
                path: nil
            ))
        }
        guard let code = items.first(where: { $0.name == "code" })?.value else {
            throw ApiError.network(URLError(.badServerResponse))
        }

        return try await exchangeCode(code: code, verifier: verifier)
    }

    static func refresh(_ refreshToken: String) async throws -> Tokens {
        var request = URLRequest(url: URL(string: "\(issuer)/oauth2/token")!)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = formEncode([
            "grant_type": "refresh_token",
            "refresh_token": refreshToken,
            "client_id": clientId,
        ]).data(using: .utf8)
        return try await parseTokenResponse(request)
    }

    static func endSessionURL(idTokenHint: String?) -> URL {
        var components = URLComponents(string: "\(issuer)/connect/logout")!
        if let idTokenHint {
            components.queryItems = [URLQueryItem(name: "id_token_hint", value: idTokenHint)]
        }
        return components.url!
    }

    private static func exchangeCode(code: String, verifier: String) async throws -> Tokens {
        var request = URLRequest(url: URL(string: "\(issuer)/oauth2/token")!)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = formEncode([
            "grant_type": "authorization_code",
            "code": code,
            "redirect_uri": redirectURI,
            "client_id": clientId,
            "code_verifier": verifier,
        ]).data(using: .utf8)
        return try await parseTokenResponse(request)
    }

    private static func parseTokenResponse(_ request: URLRequest) async throws -> Tokens {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            if let body = try? JSONDecoder().decode(ApiErrorBody.self, from: data) {
                throw ApiError.fromBody(body)
            }
            throw ApiError.network(URLError(.badServerResponse))
        }
        struct OAuthTokenResponse: Decodable {
            let access_token: String
            let refresh_token: String?
            let id_token: String?
            let expires_in: Int?
        }
        let decoded = try JSONDecoder().decode(OAuthTokenResponse.self, from: data)
        let expires = Date().addingTimeInterval(TimeInterval(decoded.expires_in ?? 3600))
        return Tokens(
            accessToken: decoded.access_token,
            refreshToken: decoded.refresh_token,
            idToken: decoded.id_token,
            expiresAt: expires
        )
    }

    private static func pkceVerifier() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes).base64URLEncodedString()
    }

    private static func pkceChallenge(_ verifier: String) -> String {
        let digest = SHA256.hash(data: Data(verifier.utf8))
        return Data(digest).base64URLEncodedString()
    }

    private static func formEncode(_ values: [String: String]) -> String {
        values.map { key, value in
            "\(percentEncode(key))=\(percentEncode(value))"
        }.joined(separator: "&")
    }

    private static func percentEncode(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }
}

private final class PresentationAnchor: NSObject, ASWebAuthenticationPresentationContextProviding {
    var session: ASWebAuthenticationSession?

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        if let key = scenes.flatMap(\.windows).first(where: \.isKeyWindow) {
            return key
        }
        return scenes.flatMap(\.windows).first ?? ASPresentationAnchor()
    }
}

private extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
