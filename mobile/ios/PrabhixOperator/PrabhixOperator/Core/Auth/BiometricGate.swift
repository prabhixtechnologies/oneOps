import LocalAuthentication
import SwiftUI

@MainActor
@Observable
final class BiometricGate {
    var unlocked = false
    var lastError: String?

    var isRequired: Bool {
        KeychainTokenStore.shared.session != nil && KeychainTokenStore.shared.biometricEnabled
    }

    func reset() {
        unlocked = false
        lastError = nil
    }

    func authenticate(reason: String = "Unlock Prabhix Operator") async -> Bool {
        lastError = nil
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            // No biometrics enrolled — do not block access.
            unlocked = true
            return true
        }
        do {
            let ok = try await context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason)
            unlocked = ok
            if !ok { lastError = "Authentication failed." }
            return ok
        } catch {
            lastError = error.localizedDescription
            unlocked = false
            return false
        }
    }
}

struct BiometricUnlockView: View {
    @Bindable var gate: BiometricGate
    var onCancel: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "lock.shield")
                .font(.system(size: 48))
            Text("Unlock Prabhix Operator")
                .font(.title2.bold())
            Text("Verify to access customer conversations")
                .foregroundStyle(.secondary)
            if let error = gate.lastError {
                Text(error).foregroundStyle(.red).font(.caption)
            }
            Button("Unlock with Face ID / Touch ID") {
                Task { await gate.authenticate() }
            }
            .buttonStyle(.borderedProminent)
            Button("Sign out", role: .destructive) { onCancel() }
        }
        .padding()
        .task {
            if !gate.unlocked {
                await gate.authenticate()
            }
        }
    }
}
