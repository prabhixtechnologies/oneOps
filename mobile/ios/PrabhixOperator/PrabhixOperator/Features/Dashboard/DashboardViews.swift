import SwiftUI

struct DashboardView: View {
    @State private var viewModel = DashboardViewModel()
    @Bindable var biometricGate: BiometricGate

    var body: some View {
        NavigationStack {
            ScrollView {
                if let k = viewModel.dashboard?.kpis {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        KpiTile(title: "Open threads", value: "\(k.openThreads)")
                        KpiTile(title: "SLA breaches", value: "\(k.slaBreaches)")
                        KpiTile(title: "Avg response", value: String(format: "%.0f min", k.avgFirstResponseMinutes))
                        KpiTile(title: "Seats", value: "\(k.seatsUsed)/\(k.seatsLimit)")
                    }
                    .padding()
                } else if viewModel.loading {
                    ProgressView()
                }
                ForEach(viewModel.dashboard?.recentActivity ?? []) { item in
                    Text("• \(item.description)").padding(.horizontal)
                }

                Toggle("Require Face ID / Touch ID to open app", isOn: Binding(
                    get: { KeychainTokenStore.shared.biometricEnabled },
                    set: { enabled in
                        KeychainTokenStore.shared.setBiometricEnabled(enabled)
                        if enabled { biometricGate.unlocked = true }
                    }
                ))
                .padding()
            }
            .navigationTitle("Dashboard")
            .refreshable { await viewModel.refresh() }
            .task { await viewModel.refresh() }
        }
    }
}

struct KpiTile: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading) {
            Text(title).font(.caption)
            Text(value).font(.title2.bold())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.thinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

struct VisitorsView: View {
    @State private var viewModel = VisitorsViewModel()

    var body: some View {
        NavigationStack {
            List(viewModel.live) { visitor in
                VStack(alignment: .leading) {
                    Text(visitor.displayName ?? visitor.email ?? "Anonymous")
                    Text(visitor.currentTitle ?? visitor.currentPath ?? "/")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Live (\(viewModel.live.count))")
            .refreshable { await viewModel.refresh() }
            .task { await viewModel.refresh() }
        }
    }
}

struct OrgSelectView: View {
    @State private var viewModel = OrgSelectViewModel()
    var onSelected: () -> Void

    var body: some View {
        NavigationStack {
            List(viewModel.orgs) { org in
                Button(org.name) {
                    Task {
                        try? await viewModel.select(org.id)
                        onSelected()
                    }
                }
            }
            .navigationTitle("Organization")
            .task {
                await viewModel.load()
                if viewModel.orgs.count == 1, let first = viewModel.orgs.first {
                    try? await viewModel.select(first.id)
                    onSelected()
                }
            }
        }
    }
}
