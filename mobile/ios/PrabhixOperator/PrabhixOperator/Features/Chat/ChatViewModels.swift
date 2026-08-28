import Foundation
import SwiftData

@MainActor
@Observable
final class ChatInboxViewModel {
    var queue: ChatQueue = .mine
    var conversations: [ConversationSummary] = []
    var counts: ChatInboxCounts?
    var nextCursor: String?
    var hasMore = false
    var loading = false
    var error: String?
    var usingCache = false

    var canViewAll: Bool { KeychainTokenStore.shared.hasPermission(Permission.chatReadAll) }

    func refresh(context: ModelContext) async {
        loading = true
        usingCache = false
        defer { loading = false }
        do {
            async let page: CursorPage<ConversationSummary> = APIClient.shared.request(
                path: "chat/conversations",
                query: [
                    URLQueryItem(name: "queue", value: queue.rawValue),
                    URLQueryItem(name: "limit", value: "25"),
                ]
            )
            async let c: ChatInboxCounts = APIClient.shared.request(path: "chat/conversations/counts")
            let (p, counts) = try await (page, c)
            conversations = p.items
            nextCursor = p.nextCursor
            hasMore = p.hasMore
            self.counts = counts
            ConversationCacheService.save(p.items, queue: queue, context: context)
        } catch {
            if let cached = ConversationCacheService.load(queue: queue, context: context) {
                conversations = cached
                hasMore = false
                nextCursor = nil
                usingCache = true
                self.error = "Showing cached inbox — \( (error as? ApiError)?.errorDescription ?? error.localizedDescription)"
            } else {
                self.error = (error as? ApiError)?.errorDescription ?? error.localizedDescription
            }
        }
    }

    func loadMore(context: ModelContext) async {
        guard hasMore, let cursor = nextCursor, !loading else { return }
        loading = true
        defer { loading = false }
        do {
            let page: CursorPage<ConversationSummary> = try await APIClient.shared.request(
                path: "chat/conversations",
                query: [
                    URLQueryItem(name: "queue", value: queue.rawValue),
                    URLQueryItem(name: "cursor", value: cursor),
                    URLQueryItem(name: "limit", value: "25"),
                ]
            )
            conversations.append(contentsOf: page.items)
            nextCursor = page.nextCursor
            hasMore = page.hasMore
            ConversationCacheService.save(conversations, queue: queue, context: context)
        } catch {
            self.error = (error as? ApiError)?.errorDescription ?? error.localizedDescription
        }
    }
}

@MainActor
@Observable
final class ChatDetailViewModel {
    let conversationId: String
    var detail: ConversationDetail?
    var draft = ""
    var noteMode = false
    var syncNote: String?
    var aiNote: String?
    var aiLoading = false
    var sending = false

    init(conversationId: String) {
        self.conversationId = conversationId
    }

    func load() async {
        do {
            detail = try await APIClient.shared.request(path: "chat/conversations/\(conversationId)")
        } catch {
            syncNote = (error as? ApiError)?.errorDescription ?? error.localizedDescription
        }
    }

    func send(context: ModelContext?) async {
        guard !draft.isEmpty else { return }
        sending = true
        defer { sending = false }
        let text = draft
        let isNote = noteMode
        let clientId = UUID().uuidString
        do {
            let _: MessageView = try await APIClient.shared.request(
                path: "chat/conversations/\(conversationId)/messages",
                method: "POST",
                query: [URLQueryItem(name: "note", value: isNote ? "true" : "false")],
                body: SendMessageRequest(body: text, fileId: nil, internal: isNote ? true : nil),
                idempotencyKey: clientId
            )
            draft = ""
            syncNote = nil
            await load()
        } catch {
            guard let context else {
                syncNote = (error as? ApiError)?.errorDescription ?? error.localizedDescription
                return
            }
            OfflineFlushService.enqueue(context: context, clientId: clientId,
                                        conversationId: conversationId, body: text, isNote: isNote)
            draft = ""
            syncNote = "Queued — will send when back online"
        }
    }

    func suggestReply() async {
        aiLoading = true
        aiNote = nil
        defer { aiLoading = false }
        do {
            let result = try await AiService.suggestChatReply(conversationId: conversationId)
            if let hint = AiService.unavailableHint(result) {
                aiNote = hint
                return
            }
            draft = result.draft
        } catch let apiErr as ApiError {
            aiNote = apiErr.errorDescription
        } catch {
            aiNote = error.localizedDescription
        }
    }

    func rewriteDraft(action: String) async {
        guard !draft.isEmpty else {
            aiNote = "Write a draft first."
            return
        }
        aiLoading = true
        aiNote = nil
        defer { aiLoading = false }
        do {
            let result = try await AiService.rewriteChatDraft(
                conversationId: conversationId,
                draft: draft,
                action: action
            )
            if let hint = AiService.unavailableHint(result) {
                aiNote = hint
                return
            }
            draft = result.text
        } catch let apiErr as ApiError {
            aiNote = apiErr.errorDescription
        } catch {
            aiNote = error.localizedDescription
        }
    }
}
