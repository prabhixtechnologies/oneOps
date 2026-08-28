import Foundation

enum AppConfig {
    static var apiBaseURL: URL {
        if let override = ProcessInfo.processInfo.environment["PRABHIX_API_BASE"],
           let url = URL(string: override) {
            return url
        }
        #if targetEnvironment(simulator)
        return URL(string: "http://localhost:8080/api/v1")!
        #else
        // Override via Config.xcconfig / build setting PRABHIX_API_BASE
        return URL(string: Bundle.main.object(forInfoDictionaryKey: "PrabhixAPIBase") as? String
            ?? "https://api.prabhixtechnologies.com/api/v1")!
        #endif
    }

    static let deviceHeader = "mobile-ios"
}
