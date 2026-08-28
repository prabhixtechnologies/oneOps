import SwiftData
import SwiftUI

struct ChatInboxView: View {
    @Binding var path: NavigationPath
    @State private var viewModel = ChatInboxViewModel()
    @ObservedObject private var realtime = RealtimeService.shared
    @Environment(\.modelContext) private var modelContext

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                Picker("Queue", selection: $viewModel.queue) {
                    Text("Mine").tag(ChatQueue.mine)
                    Text("Unassigned").tag(ChatQueue.unassigned)
                    if viewModel.canViewAll {
                        Text("All").tag(ChatQueue.all)
                    }
                }
                .pickerStyle(.segmented)
                .padding()

                HStack {
                    Circle().fill(realtime.chatConnected ? .green : .orange).frame(width: 8, height: 8)
                    Text(realtime.chatConnected ? "Live" : "Reconnecting…")
                        .font(.caption)
                    Spacer()
                    if viewModel.usingCache {
                        Text("Offline cache").font(.caption).foregroundStyle(.orange)
                    }
                    if let counts = viewModel.counts {
                        Text("Unassigned \(counts.unassigned) · Unread \(counts.mineUnread)")
                            .font(.caption)
                    }
                }
                .padding(.horizontal)

                if let error = viewModel.error {
                    Text(error).font(.caption).foregroundStyle(.red).padding(.horizontal)
                }

                List {
                    ForEach(viewModel.conversations) { conv in
                        Button {
                            path.append(conv.id)
                        } label: {
                            VStack(alignment: .leading) {
                                Text(conv.visitorName ?? conv.visitorEmail ?? "Visitor")
                                    .font(.headline)
                                Text(conv.lastMessagePreview ?? "")
                                    .lineLimit(1)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    if viewModel.hasMore {
                        ProgressView()
                            .task { await viewModel.loadMore(context: modelContext) }
                    }
                }
                .refreshable { await viewModel.refresh(context: modelContext) }
            }
            .navigationTitle("Chat")
            .navigationDestination(for: String.self) { id in
                ChatDetailView(conversationId: id)
            }
            .task { await viewModel.refresh(context: modelContext) }
            .onChange(of: viewModel.queue) { _, _ in
                Task { await viewModel.refresh(context: modelContext) }
            }
            .onChange(of: realtime.lastChatEvent?.conversationId) { _, _ in
                Task { await viewModel.refresh(context: modelContext) }
            }
        }
    }
}

struct ChatDetailView: View {
    @State private var viewModel: ChatDetailViewModel
    @Environment(\.modelContext) private var modelContext

    init(conversationId: String) {
        _viewModel = State(initialValue: ChatDetailViewModel(conversationId: conversationId))
    }

    private var canUseAi: Bool {
        KeychainTokenStore.shared.hasPermission(Permission.aiUse)
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        ForEach(viewModel.detail?.messages ?? []) { msg in
                            MessageBubbleView(message: msg)
                                .id(msg.id)
                        }
                    }
                    .padding()
                }
                .onChange(of: viewModel.detail?.messages.count) { _, _ in
                    if let last = viewModel.detail?.messages.last?.id {
                        proxy.scrollTo(last, anchor: .bottom)
                    }
                }
            }

            if canUseAi {
                chatAiBar
            }

            HStack(alignment: .bottom) {
                Toggle("Note", isOn: $viewModel.noteMode)
                    .toggleStyle(.button)
                TextField(viewModel.noteMode ? "Internal note" : "Reply", text: $viewModel.draft, axis: .vertical)
                    .lineLimit(1...6)
                Button("Send") { Task { await viewModel.send(context: modelContext) } }
                    .disabled(viewModel.draft.isEmpty || viewModel.sending)
            }
            .padding()
            if let note = viewModel.syncNote {
                Text(note).font(.caption).foregroundStyle(.secondary).padding(.horizontal)
            }
            if let aiNote = viewModel.aiNote {
                Text(aiNote).font(.caption).foregroundStyle(.secondary).padding(.horizontal)
            }
        }
        .navigationTitle(viewModel.detail?.conversation.visitorName ?? "Chat")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.load() }
    }

    @ViewBuilder
    private var chatAiBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack {
                Button {
                    Task { await viewModel.suggestReply() }
                } label: {
                    Label(viewModel.aiLoading ? "Generating…" : "Suggest", systemImage: "sparkles")
                }
                .disabled(viewModel.aiLoading)

                Menu("Rewrite") {
                    Button("Improve tone") { Task { await viewModel.rewriteDraft(action: "Improve") } }
                    Button("Shorten") { Task { await viewModel.rewriteDraft(action: "Shorten") } }
                    Button("Translate to English") { Task { await viewModel.rewriteDraft(action: "Translate") } }
                }
                .disabled(viewModel.aiLoading || viewModel.draft.isEmpty)
            }
            .buttonStyle(.bordered)
            .font(.caption)
            .padding(.horizontal)
            .padding(.top, 8)
        }
    }
}

struct MessageBubbleView: View {
    let message: MessageView

    var body: some View {
        let isNote = message.senderType == "NOTE"
        let isVisitor = message.senderType == "VISITOR"
        HStack {
            if !isVisitor { Spacer() }
            VStack(alignment: .leading) {
                if isNote {
                    Text("Internal note").font(.caption).italic()
                }
                Text(message.body)
            }
            .padding(10)
            .background(isNote ? Color.yellow.opacity(0.3) : (isVisitor ? Color.gray.opacity(0.2) : Color.blue.opacity(0.2)))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            if isVisitor { Spacer() }
        }
    }
}
