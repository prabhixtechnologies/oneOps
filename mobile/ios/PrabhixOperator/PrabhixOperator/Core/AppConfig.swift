import Foundation

enum AppConfig {
    static let productionBaseURL = "https://api.prabhixtechnologies.com/api/v1"

    static var apiBaseURL: URL {
        if let override = ProcessInfo.processInfo.environment["PRABHIX_API_BASE"],
           let url = URL(string: override) {
            return url
        }
        #if targetEnvironment(simulator)
        return URL(string: "http://localhost:8080/api/v1")!
        #else
        // Set by Config.xcconfig, through PRABHIX_API_BASE and Info.plist. `info` rejects a value
        // left as the literal "$(PRABHIX_API_BASE)", and the second fallback covers a configured
        // value that is not a valid URL — either way production is a better answer than a crash on
        // launch, which is what force-unwrapping the parse here used to give.
        if let configured = info("PrabhixAPIBase"), let url = URL(string: configured) {
            return url
        }
        return URL(string: productionBaseURL)!
        #endif
    }

    /// True in the private admin app, false in the OneOps app sold to customers.
    ///
    /// Set by `SWIFT_ACTIVE_COMPILATION_CONDITIONS` on the admin target only, so `#if` removes the
    /// platform surface from the customer's binary rather than hiding it behind a runtime check.
    /// This is the iOS counterpart of Android's `BuildConfig.IS_ADMIN_APP`.
    static var isAdminApp: Bool {
        #if PRABHIX_ADMIN
        return true
        #else
        return false
        #endif
    }

    /// Names the app in the operator's session list, alongside the device model.
    static var appLabel: String {
        info("CFBundleName") ?? "Prabhix"
    }

    /// Distinguishes the two apps in `X-Prabhix-Device`, matching the Android values.
    static var deviceHeader: String {
        info("PrabhixDeviceHeader") ?? "mobile-ios"
    }

    /// Each app registers its own URL scheme. A shared one would let a link open a customer's
    /// conversation in whichever of the two apps iOS happened to pick.
    static var deepLinkScheme: String {
        info("PrabhixDeepLinkScheme") ?? "prabhix"
    }

    /// Derived from the bundle id so it cannot drift from `BGTaskSchedulerPermittedIdentifiers`.
    /// `BGTaskScheduler` throws on an identifier the plist does not list.
    static var backgroundTaskIdentifier: String {
        "\(Bundle.main.bundleIdentifier ?? "com.prabhix.operator").flush"
    }

    private static func info(_ key: String) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String,
              !value.isEmpty,
              // An unresolved build setting arrives literally as "$(NAME)".
              !value.hasPrefix("$(")
        else { return nil }
        return value
    }
}
