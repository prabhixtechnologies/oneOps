import SwiftUI

struct MailInboxView: View {
    @Binding var path: NavigationPath
    @State private var viewModel = MailInboxViewModel()
    @ObservedObject private var realtime = RealtimeService.shared

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                HStack {
                    Circle().fill(realtime.mailConnected ? .green : .orange).frame(width: 8, height: 8)
                    Text(realtime.mailConnected ? "Live" : "Reconnecting…")
                        .font(.caption)
                    Spacer()
                }
                .padding(.horizontal)
                .padding(.top, 8)

                if let error = viewModel.error {
                    Text(error).font(.caption).foregroundStyle(.red).padding(.horizontal)
                }

                List {
                    ForEach(viewModel.threads) { thread in
                        Button {
                            path.append(thread.id)
                        } label: {
                            VStack(alignment: .leading) {
                                Text(thread.subject).font(.headline)
                                Text(thread.snippet ?? thread.customerEmail ?? "")
                                    .lineLimit(2)
                                    .foregroundStyle(.secondary)
                                if thread.slaBreachedAt != nil {
                                    Text("SLA breached").font(.caption).foregroundStyle(.red)
                                }
                            }
                        }
                    }
                    if viewModel.hasMore {
                        ProgressView().task { await viewModel.loadMore() }
                    }
                }
            }
            .navigationTitle("Mail")
            .navigationDestination(for: String.self) { id in
                MailDetailView(threadId: id)
            }
            .refreshable { await viewModel.refresh() }
            .task { await viewModel.refresh() }
            .onChange(of: realtime.lastMailEvent?.type) { _, _ in
                Task { await viewModel.refresh() }
            }
        }
    }
}

struct MailDetailView: View {
    @State private var viewModel: MailDetailViewModel
    @State private var showSummary = false

    init(threadId: String) {
        _viewModel = State(initialValue: MailDetailViewModel(threadId: threadId))
    }

    private var canUseAi: Bool {
        KeychainTokenStore.shared.hasPermission(Permission.aiUse)
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 16) {
                    ForEach(viewModel.detail?.messages ?? []) { msg in
                        VStack(alignment: .leading) {
                            Text("\(msg.fromName ?? msg.fromAddress ?? "Unknown") · \(msg.direction)")
                                .font(.caption)
                            Text(msg.bodyText ?? stripHtml(msg.bodyHtml))
                        }
                        Divider()
                    }
                }
                .padding()
            }

            if canUseAi {
                mailAiBar
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Reply").font(.caption).foregroundStyle(.secondary)
                TextEditor(text: $viewModel.draft)
                    .frame(minHeight: 80, maxHeight: 160)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(.quaternary))
                HStack {
                    Spacer()
                    Button("Send") { Task { await viewModel.sendReply() } }
                        .buttonStyle(.borderedProminent)
                        .disabled(viewModel.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.sending)
                }
            }
            .padding()

            if let error = viewModel.error {
                Text(error).font(.caption).foregroundStyle(.red).padding(.horizontal)
            }
            if let aiNote = viewModel.aiNote {
                Text(aiNote).font(.caption).foregroundStyle(.secondary).padding(.horizontal)
            }
        }
        .navigationTitle(viewModel.detail?.thread.subject ?? "Thread")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.load() }
        .sheet(isPresented: $showSummary) {
            NavigationStack {
                ScrollView {
                    Text(viewModel.summaryText)
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .navigationTitle("Thread summary")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { showSummary = false }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var mailAiBar: some View {
        HStack {
            Button {
                Task { await viewModel.suggestReply() }
            } label: {
                Label(viewModel.aiLoading ? "Generating…" : "Suggest reply", systemImage: "sparkles")
            }
            Button {
                Task {
                    await viewModel.summarize()
                    if !viewModel.summaryText.isEmpty { showSummary = true }
                }
            } label: {
                Label("Summarize", systemImage: "text.alignleft")
            }
            .disabled(viewModel.aiLoading)
        }
        .buttonStyle(.bordered)
        .font(.caption)
        .padding(.horizontal)
        .padding(.top, 8)
    }
}

private func stripHtml(_ html: String?) -> String {
    guard let html else { return "" }
    return html.replacingOccurrences(of: "<[^>]+>", with: "\n", options: .regularExpression)
}
