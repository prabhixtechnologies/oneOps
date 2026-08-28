package com.prabhix.operator

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.prabhix.operator.data.auth.TokenStore
import com.prabhix.operator.ui.navigation.PrabhixNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// FragmentActivity, not ComponentActivity: BiometricPrompt only accepts a FragmentActivity or a
// Fragment, because it hosts its own fragment to survive configuration changes mid-authentication.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var tokenStore: TokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val deepLink = intent?.data
        val chatId = if (deepLink?.host == "chat") deepLink.lastPathSegment else null
        val mailId = if (deepLink?.host == "mail") deepLink.lastPathSegment else null

        if (tokenStore.session() != null && tokenStore.biometricEnabled()) {
            promptBiometric(onSuccess = { render(chatId, mailId) })
        } else {
            render(chatId, mailId)
        }
    }

    private fun render(chatId: String?, mailId: String?) {
        setContent {
            val dark = remember { mutableStateOf(false) }
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                var session by remember { mutableStateOf(tokenStore.session()) }
                PrabhixNavHost(
                    isLoggedIn = session != null,
                    hasOrg = session?.organizationId != null,
                    deepLinkChatId = chatId,
                    deepLinkMailId = mailId,
                    onSessionEnded = { session = null },
                )
            }
        }
    }

    private fun promptBiometric(onSuccess: () -> Unit) {
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            onSuccess()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finish()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Prabhix Operator")
                .setSubtitle("Verify to access customer conversations")
                .setNegativeButtonText("Cancel")
                .build(),
        )
    }
}
