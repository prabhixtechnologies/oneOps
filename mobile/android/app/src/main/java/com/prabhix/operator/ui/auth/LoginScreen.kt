package com.prabhix.operator.ui.auth

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabhix.operator.BuildConfig
import com.prabhix.operator.data.auth.IdentityAuthenticator
import com.prabhix.operator.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /**
     * The in-flight authorization request.
     *
     * <p>Held here rather than passed through the intent because completing the flow needs the PKCE
     * verifier and the state value from the original request, and neither may leave the process — the
     * verifier is the whole reason an intercepted authorization code is useless. Surviving process
     * death is not required: if Android kills the app mid-flow the user lands back on this screen and
     * taps once more.
     */
    private var pending: IdentityAuthenticator.Authorization? = null

    fun beginSignIn(): Intent {
        val authorization = authRepository.beginSignIn()
        pending = authorization
        _state.value = AuthUiState(loading = true)
        return authorization.intent
    }

    fun completeSignIn(data: Intent?, onSuccess: () -> Unit) {
        val request = pending?.request
        if (request == null) {
            _state.value = AuthUiState(error = "Sign-in was interrupted. Try again.")
            return
        }
        viewModelScope.launch {
            authRepository.completeSignIn(request, data)
                .onSuccess {
                    pending = null
                    onSuccess()
                }
                .onFailure { _state.value = AuthUiState(error = it.message ?: "Sign-in failed") }
            _state.value = _state.value.copy(loading = false)
        }
    }

    fun cancelled() {
        _state.value = AuthUiState()
    }
}

/**
 * One button. Sign-in happens on Identity's hosted page in a Custom Tab.
 *
 * <p>There is no password field, and that is the point. The password is typed into the browser, so it
 * never reaches this process; the Custom Tab shares the browser's cookie jar, so someone already
 * signed in on the web arrives here already signed in; and whatever Identity adds later — a second
 * factor, a passkey, an SSO button — works in both apps without shipping a release.
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        // A cancelled tab returns RESULT_CANCELED with no data, which is a user backing out rather
        // than a failure, so it resets the screen instead of showing an error.
        if (result.data == null) viewModel.cancelled() else viewModel.completeSignIn(result.data, onLoggedIn)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        // Whichever app this is. Being told which app you opened matters most on this screen, since
        // the sign-in page itself is shared between them.
        Text(BuildConfig.APP_LABEL, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Sign in with your Prabhix account.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(
            onClick = { launcher.launch(viewModel.beginSignIn()) },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue") }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
