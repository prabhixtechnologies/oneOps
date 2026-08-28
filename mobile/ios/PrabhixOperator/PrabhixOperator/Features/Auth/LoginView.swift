import SwiftUI

struct LoginView: View {
    @Bindable var viewModel: AuthViewModel
    var onSuccess: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                Picker("Sign in method", selection: $viewModel.authMode) {
                    ForEach(AuthViewModel.AuthMode.allCases, id: \.self) { mode in
                        Text(mode.rawValue.capitalized).tag(mode)
                    }
                }
                .pickerStyle(.segmented)

                TextField("Email", text: $viewModel.email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)

                switch viewModel.authMode {
                case .password:
                    SecureField("Password", text: $viewModel.password)
                    Button("Sign in") {
                        Task {
                            await viewModel.login()
                            if viewModel.isLoggedIn { onSuccess() }
                        }
                    }
                case .otp:
                    if viewModel.otpSent {
                        TextField("OTP code", text: $viewModel.otpCode)
                        Button("Verify") {
                            Task {
                                await viewModel.verifyOtp()
                                if viewModel.isLoggedIn { onSuccess() }
                            }
                        }
                    } else {
                        Button("Send OTP") { Task { await viewModel.requestOtp() } }
                    }
                case .magicLink:
                    if viewModel.magicLinkSent {
                        Text("Check your email for the sign-in link.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else {
                        Button("Send magic link") {
                            Task { await viewModel.requestMagicLink() }
                        }
                    }
                    TextField("Paste magic link token", text: $viewModel.magicToken)
                        .autocapitalization(.none)
                    Button("Verify link") {
                        Task {
                            await viewModel.verifyMagicLink()
                            if viewModel.isLoggedIn { onSuccess() }
                        }
                    }
                    .disabled(viewModel.magicToken.isEmpty || viewModel.loading)
                }

                if let error = viewModel.error {
                    Text(error).foregroundStyle(.red)
                }
            }
            .navigationTitle("Prabhix Operator")
        }
    }
}
