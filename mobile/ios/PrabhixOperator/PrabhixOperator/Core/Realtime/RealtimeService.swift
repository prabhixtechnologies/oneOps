import Foundation

@MainActor
final class RealtimeService: ObservableObject {
    static let shared = RealtimeService()

    @Published private(set) var chatConnected = false
    @Published private(set) var mailConnected = false
    @Published private(set) var lastChatEvent: ChatStreamPayload?
    @Published private(set) var lastMailEvent: MailStreamPayload?

    private var chatTask: Task<Void, Never>?
    private var mailTask: Task<Void, Never>?
    private var backoffSeconds = 1

    func start() {
        chatTask?.cancel()
        mailTask?.cancel()
        chatTask = Task { await listenChat() }
        mailTask = Task { await listenMail() }
    }

    func stop() {
        chatTask?.cancel()
        mailTask?.cancel()
        chatConnected = false
        mailConnected = false
    }

    func restart() {
        stop()
        start()
    }

    private func listenChat() async {
        while !Task.isCancelled {
            do {
                try await stream(path: "chat/stream") { line in
                    if line == "ping" { return }
                    if let data = line.data(using: .utf8),
                       let payload = try? JSONDecoder().decode(ChatStreamPayload.self, from: data) {
                        await MainActor.run {
                            self.lastChatEvent = payload
                            self.chatConnected = true
                            self.backoffSeconds = 1
                        }
                    }
                }
            } catch {
                await MainActor.run { self.chatConnected = false }
                try? await Task.sleep(nanoseconds: UInt64(backoffSeconds) * 1_000_000_000)
                backoffSeconds = min(backoffSeconds * 2, 60)
            }
        }
    }

    private func listenMail() async {
        while !Task.isCancelled {
            do {
                try await stream(path: "mail/stream") { line in
                    if line == "ping" { return }
                    if let data = line.data(using: .utf8),
                       let payload = try? JSONDecoder().decode(MailStreamPayload.self, from: data) {
                        await MainActor.run {
                            self.lastMailEvent = payload
                            self.mailConnected = true
                        }
                    }
                }
            } catch {
                await MainActor.run { self.mailConnected = false }
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
        }
    }

    private func stream(path: String, onData: @escaping (String) async -> Void) async throws {
        guard let token = await TokenRefresher(client: APIClient.shared).accessToken() else { return }
        var request = URLRequest(url: AppConfig.apiBaseURL.appendingPathComponent(path))
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.setValue(AppConfig.deviceHeader, forHTTPHeaderField: "X-Prabhix-Device")
        if let org = KeychainTokenStore.shared.session?.organizationId {
            request.setValue(org, forHTTPHeaderField: "X-Prabhix-Org")
        }

        let (bytes, response) = try await URLSession.shared.bytes(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }

        var dataLine = ""
        for try await line in bytes.lines {
            if Task.isCancelled { break }
            if line.hasPrefix("data:") {
                dataLine = String(line.dropFirst(5)).trimmingCharacters(in: .whitespaces)
            } else if line.isEmpty, !dataLine.isEmpty {
                await onData(dataLine)
                dataLine = ""
            }
        }
    }
}
