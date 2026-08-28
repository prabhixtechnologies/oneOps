import Foundation

enum AiService {
    static func suggestChatReply(conversationId: String) async throws -> AiDraftSuggestion {
        try await APIClient.shared.request(
            path: "chat/conversations/\(conversationId)/ai/reply/suggest",
            method: "POST"
        )
    }

    static func rewriteChatDraft(conversationId: String, draft: String, action: String) async throws -> AiRewriteResult {
        try await APIClient.shared.request(
            path: "chat/conversations/\(conversationId)/ai/rewrite",
            method: "POST",
            body: AiRewriteRequest(draft: draft, action: action)
        )
    }

    static func suggestMailReply(threadId: String) async throws -> AiDraftSuggestion {
        try await APIClient.shared.request(
            path: "mail/threads/\(threadId)/ai/reply/suggest",
            method: "POST"
        )
    }

    static func summarizeMailThread(threadId: String) async throws -> AiTextResult {
        try await APIClient.shared.request(
            path: "mail/threads/\(threadId)/ai/summarize",
            method: "POST"
        )
    }

    static func unavailableHint(_ result: AiAvailabilityHint) -> String? {
        guard !result.available else { return nil }
        if result.unavailableBecauseNotConfigured == true {
            return "AI is not configured for this organization."
        }
        return "AI is unavailable right now."
    }
}

protocol AiAvailabilityHint {
    var available: Bool { get }
    var unavailableBecauseNotConfigured: Bool? { get }
}

extension AiDraftSuggestion: AiAvailabilityHint {}
extension AiTextResult: AiAvailabilityHint {}
extension AiRewriteResult: AiAvailabilityHint {
    var unavailableBecauseNotConfigured: Bool? { nil }
}
