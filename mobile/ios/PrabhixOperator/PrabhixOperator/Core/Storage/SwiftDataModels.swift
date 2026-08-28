import Foundation
import SwiftData

@Model
final class CachedConversation {
    @Attribute(.unique) var id: String
    var queue: String
    var visitorName: String?
    var preview: String?
    var unread: Int
    var lastMessageAt: String?
    var cachedAt: Date

    init(id: String, queue: String, visitorName: String?, preview: String?, unread: Int, lastMessageAt: String?) {
        self.id = id
        self.queue = queue
        self.visitorName = visitorName
        self.preview = preview
        self.unread = unread
        self.lastMessageAt = lastMessageAt
        self.cachedAt = Date()
    }
}

@Model
final class OutboundChatMessage {
    @Attribute(.unique) var clientId: String
    var conversationId: String
    var body: String
    var isNote: Bool
    var createdAt: Date

    init(clientId: String, conversationId: String, body: String, isNote: Bool) {
        self.clientId = clientId
        self.conversationId = conversationId
        self.body = body
        self.isNote = isNote
        self.createdAt = Date()
    }
}

enum ConversationCacheService {
    @MainActor
    static func save(_ conversations: [ConversationSummary], queue: ChatQueue, context: ModelContext) {
        let queueKey = queue.rawValue
        for conv in conversations {
            let id = conv.id
            let descriptor = FetchDescriptor<CachedConversation>(
                predicate: #Predicate { $0.id == id }
            )
            let existing = try? context.fetch(descriptor).first
            if let existing {
                existing.queue = queueKey
                existing.visitorName = conv.visitorName
                existing.preview = conv.lastMessagePreview
                existing.unread = conv.unreadAgentCount
                existing.lastMessageAt = conv.lastMessageAt
                existing.cachedAt = Date()
            } else {
                context.insert(CachedConversation(
                    id: conv.id,
                    queue: queueKey,
                    visitorName: conv.visitorName,
                    preview: conv.lastMessagePreview,
                    unread: conv.unreadAgentCount,
                    lastMessageAt: conv.lastMessageAt
                ))
            }
        }
        try? context.save()
    }

    @MainActor
    static func load(queue: ChatQueue, context: ModelContext) -> [ConversationSummary]? {
        let queueKey = queue.rawValue
        let descriptor = FetchDescriptor<CachedConversation>(
            predicate: #Predicate { $0.queue == queueKey },
            sortBy: [SortDescriptor(\.cachedAt, order: .reverse)]
        )
        guard let cached = try? context.fetch(descriptor), !cached.isEmpty else { return nil }
        return cached.map { row in
            ConversationSummary(
                id: row.id,
                status: "OPEN",
                priority: "NORMAL",
                subject: nil,
                visitorName: row.visitorName,
                visitorEmail: nil,
                assignedAgentId: nil,
                tags: [],
                unreadAgentCount: row.unread,
                lastMessageAt: row.lastMessageAt,
                lastMessagePreview: row.preview,
                visitorId: nil
            )
        }
    }
}

/// Drains messages composed while offline. The queue row's `clientId` is sent as the
/// `Idempotency-Key`, so a send that reached the server but whose response was lost is
/// collapsed server-side rather than posted twice.
enum OfflineFlushService {

    @MainActor
    static func flushPending(context: ModelContext) async {
        let pending: [OutboundChatMessage]
        do {
            var descriptor = FetchDescriptor<OutboundChatMessage>(
                sortBy: [SortDescriptor(\.createdAt, order: .forward)]
            )
            descriptor.fetchLimit = 100
            pending = try context.fetch(descriptor)
        } catch {
            return
        }
        guard !pending.isEmpty else { return }

        for item in pending {
            do {
                let _: MessageView = try await APIClient.shared.request(
                    path: "chat/conversations/\(item.conversationId)/messages",
                    method: "POST",
                    query: [URLQueryItem(name: "note", value: item.isNote ? "true" : "false")],
                    body: SendMessageRequest(body: item.body, fileId: nil,
                                             internal: item.isNote ? true : nil),
                    idempotencyKey: item.clientId
                )
                context.delete(item)
            } catch ApiError.unauthorized {
                break
            } catch let error as ApiError {
                if case .network = error {
                    break
                }
                context.delete(item)
            } catch {
                break
            }
        }
        try? context.save()
    }

    @MainActor
    static func enqueue(context: ModelContext, clientId: String, conversationId: String,
                        body: String, isNote: Bool) {
        context.insert(OutboundChatMessage(clientId: clientId, conversationId: conversationId,
                                           body: body, isNote: isNote))
        try? context.save()
    }
}
