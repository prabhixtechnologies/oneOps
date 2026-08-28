import Foundation

@MainActor
@Observable
final class MailInboxViewModel {
    var threads: [ThreadSummary] = []
    var nextCursor: String?
    var hasMore = false
    var loading = false
    var error: String?

    func refresh() async {
        loading = true
        defer { loading = false }
        do {
            let page: CursorPage<ThreadSummary> = try await APIClient.shared.request(
                path: "mail/threads",
                query: [URLQueryItem(name: "limit", value: "25")]
            )
            threads = page.items
            nextCursor = page.nextCursor
            hasMore = page.hasMore
            error = nil
        } catch {
            error = (error as? ApiError)?.errorDescription ?? error.localizedDescription
        }
    }

    func loadMore() async {
        guard hasMore, let cursor = nextCursor, !loading else { return }
        loading = true
        defer { loading = false }
        do {
            let page: CursorPage<ThreadSummary> = try await APIClient.shared.request(
                path: "mail/threads",
                query: [
                    URLQueryItem(name: "cursor", value: cursor),
                    URLQueryItem(name: "limit", value: "25"),
                ]
            )
            threads.append(contentsOf: page.items)
            nextCursor = page.nextCursor
            hasMore = page.hasMore
        } catch {
            error = (error as? ApiError)?.errorDescription ?? error.localizedDescription
        }
    }
}

@MainActor
@Observable
final class MailDetailViewModel {
    let threadId: String
    var detail: ThreadDetail?
    var draft = ""
    var error: String?
    var aiNote: String?
    var aiLoading = false
    var sending = false
    var summaryText = ""

    init(threadId: String) { self.threadId = threadId }

    func load() async {
        do {
            detail = try await APIClient.shared.request(path: "mail/threads/\(threadId)")
            error = nil
        } catch {
            error = (error as? ApiError)?.errorDescription ?? error.localizedDescription
        }
    }

    func sendReply() async {
        let trimmed = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        guard let to = detail?.thread.customerEmail else {
            error = "No customer email on this thread."
            return
        }
        sending = true
        defer { sending = false }
        do {
            let bodyHtml = plainTextToHtml(trimmed)
            let _: MailMessageSummary = try await APIClient.shared.request(
                path: "mail/threads/\(threadId)/reply",
                method: "POST",
                body: ReplyRequest(to: [to], cc: nil, subject: nil, bodyHtml: bodyHtml, attachmentIds: nil)
            )
            draft = ""
            error = nil
            await load()
        } catch {
            error = (error as? ApiError)?.errorDescription ?? error.localizedDescription
        }
    }

    func suggestReply() async {
        aiLoading = true
        aiNote = nil
        defer { aiLoading = false }
        do {
            let result = try await AiService.suggestMailReply(threadId: threadId)
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

    func summarize() async {
        aiLoading = true
        aiNote = nil
        summaryText = ""
        defer { aiLoading = false }
        do {
            let result = try await AiService.summarizeMailThread(threadId: threadId)
            if let hint = AiService.unavailableHint(result) {
                aiNote = hint
                return
            }
            summaryText = result.text
        } catch let apiErr as ApiError {
            aiNote = apiErr.errorDescription
        } catch {
            aiNote = error.localizedDescription
        }
    }

    private func plainTextToHtml(_ text: String) -> String {
        let escaped = text
            .replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
        return "<p>\(escaped.replacingOccurrences(of: "\n", with: "<br/>"))</p>"
    }
}
