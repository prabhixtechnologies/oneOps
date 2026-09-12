import SwiftUI

struct LoginView: View {
    @Bindable var viewModel: AuthViewModel
    var onSuccess: () -> Void

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 20) {
                Text("Sign in")
                    .font(.largeTitle.bold())
                Text("One Prabhix account for every product. Credentials are entered only on Identity.")
                    .foregroundStyle(.secondary)

                if let error = viewModel.error {
                    Text(error).foregroundStyle(.red)
                }

                Button {
                    Task {
                        await viewModel.loginWithIdentity()
                        if viewModel.isLoggedIn { onSuccess() }
                    }
                } label: {
                    Text(viewModel.loading ? "Opening…" : "Continue to sign in")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.loading)

                Spacer()
            }
            .padding()
            .navigationTitle(AppConfig.appLabel)
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
