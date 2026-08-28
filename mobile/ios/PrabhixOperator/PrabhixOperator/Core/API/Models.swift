import Foundation

struct CursorPage<T: Decodable>: Decodable {
    let items: [T]
    let nextCursor: String?
    let hasMore: Bool
}

struct ApiErrorBody: Decodable {
    let code: String
    let message: String
    let fieldErrors: [String: String]?
    let traceId: String?
    let path: String?
}

enum ApiError: LocalizedError {
    case fromBody(ApiErrorBody)
    case network(Error)
    case unauthorized

    var errorDescription: String? {
        switch self {
        case .fromBody(let b): return b.message
        case .network(let e): return e.localizedDescription
        case .unauthorized: return "Session expired. Sign in again."
        }
    }

    var code: String {
        switch self {
        case .fromBody(let b): return b.code
        case .network: return "NETWORK"
        case .unauthorized: return "UNAUTHENTICATED"
        }
    }
}

struct TokenResponse: Codable {
    let accessToken: String
    let refreshToken: String
    let expiresInSeconds: Int64
    let organizationId: String?
    let permissions: Set<String>
}

struct LoginRequest: Encodable {
    let email: String
    let password: String
    let deviceId: String?
    let deviceName: String?
    let deviceType: String = "MOBILE"
}

struct RefreshRequest: Encodable { let refreshToken: String }
struct EmailRequest: Encodable { let email: String }
struct OtpVerifyRequest: Encodable { let email: String; let code: String }
struct MagicLinkVerifyRequest: Encodable { let token: String }
struct LogoutRequest: Encodable { let refreshToken: String? }

struct AuthMeResponse: Decodable {
    let userId: String
    let email: String
    let displayName: String
    let organizationId: String?
    let sessionId: String
    let permissions: Set<String>
    let platformAdmin: Bool
}

struct OrganizationView: Decodable, Identifiable {
    let id: String
    let name: String
    let slug: String
    let status: String
}

struct ChatInboxCounts: Decodable {
    let unassigned: Int64
    let mineUnread: Int64
}

struct ConversationSummary: Decodable, Identifiable {
    let id: String
    let status: String
    let priority: String
    let subject: String?
    let visitorName: String?
    let visitorEmail: String?
    let assignedAgentId: String?
    let tags: [String]
    let unreadAgentCount: Int
    let lastMessageAt: String?
    let lastMessagePreview: String?
    let visitorId: String?
}

struct MessageView: Decodable, Identifiable {
    let id: String
    let senderType: String
    let senderUserId: String?
    let body: String
    let fileId: String?
    let occurredAt: String
}

struct ConversationDetail: Decodable {
    let conversation: ConversationSummary
    let messages: [MessageView]
}

struct SendMessageRequest: Encodable {
    let body: String
    let fileId: String?
    let `internal`: Bool?
}

struct ThreadSummary: Decodable, Identifiable {
    let id: String
    let mailboxId: String
    let subject: String
    let status: String
    let priority: String
    let customerEmail: String?
    let snippet: String?
    let unreadCount: Int
    let hasAttachments: Bool
    let lastMessageAt: String?
    let slaDueAt: String?
    let slaBreachedAt: String?
}

struct MailMessageSummary: Decodable, Identifiable {
    let id: String
    let direction: String
    let fromAddress: String?
    let fromName: String?
    let subject: String?
    let snippet: String?
    let bodyText: String?
    let bodyHtml: String?
    let occurredAt: String
    let attachmentCount: Int
}

struct ThreadDetail: Decodable {
    let thread: ThreadSummary
    let messages: [MailMessageSummary]
}

struct ReplyRequest: Encodable {
    let to: [String]
    let cc: [String]?
    let subject: String?
    let bodyHtml: String
    let attachmentIds: [String]?
}

struct LiveVisitor: Decodable, Identifiable {
    var id: String { visitorId }
    let visitorId: String
    let currentPath: String?
    let currentTitle: String?
    let email: String?
    let displayName: String?
}

struct DashboardKpis: Decodable {
    let openThreads: Int64
    let avgFirstResponseMinutes: Double
    let slaBreaches: Int64
    let seatsUsed: Int
    let seatsLimit: Int
    let mrr: Int64
    let currency: String
}

struct DashboardResponse: Decodable {
    let kpis: DashboardKpis
    struct ActivityItem: Decodable, Identifiable {
        let id: String
        let description: String
    }
    let recentActivity: [ActivityItem]
}

struct PushTokenRequest: Encodable {
    let token: String
    let platform: String
    let deviceId: String
    let deviceName: String
    let appVersion: String
}

struct ChatStreamPayload: Decodable {
    let type: String
    let conversationId: String?
}

struct MailStreamPayload: Decodable {
    let type: String
    let payload: [String: String]?
}

struct AiDraftSuggestion: Decodable {
    let available: Bool
    let draft: String
    let provider: String?
    let model: String?
    let piiRedacted: Bool?
    let unavailableBecauseNotConfigured: Bool?
}

struct AiRewriteRequest: Encodable {
    let draft: String
    let action: String
}

struct AiRewriteResult: Decodable {
    let available: Bool
    let text: String
    let provider: String?
    let model: String?
}

struct AiTextResult: Decodable {
    let available: Bool
    let text: String
    let provider: String?
    let model: String?
    let unavailableBecauseNotConfigured: Bool?
}

enum ChatQueue: String, CaseIterable {
    case mine, unassigned, all
}

enum Permission {
    static let chatRead = "CHAT_READ"
    static let chatReadAll = "CHAT_READ_ALL"
    static let chatReply = "CHAT_REPLY"
    static let mailRead = "MAIL_READ"
    static let visitorRead = "VISITOR_READ"
    static let aiUse = "AI_USE"
}
