import Foundation

@MainActor
@Observable
final class DashboardViewModel {
    var dashboard: DashboardResponse?
    var loading = false

    func refresh() async {
        loading = true
        defer { loading = false }
        dashboard = try? await APIClient.shared.request(path: "dashboard")
    }
}

@MainActor
@Observable
final class VisitorsViewModel {
    var live: [LiveVisitor] = []

    func refresh() async {
        live = (try? await APIClient.shared.request(path: "visitors/live")) ?? []
    }
}

@MainActor
@Observable
final class OrgSelectViewModel {
    var orgs: [OrganizationView] = []
    var error: String?

    func load() async {
        do {
            orgs = try await APIClient.shared.request(path: "organizations")
        } catch {
            self.error = error.localizedDescription
        }
    }

    func select(_ id: String) async throws {
        try await AuthService.selectOrganization(id)
    }
}
