package com.prabhix.operator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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

        requestNotificationPermissionIfNeeded()

        if (tokenStore.session() != null && tokenStore.biometricEnabled()) {
            promptBiometric(onSuccess = { render(chatId) })
        } else {
            render(chatId)
        }
    }

    /**
     * Asks for notification permission on Android 13 and later.
     *
     * <p>Without this, push is silently useless on any recent phone: the manifest permission is
     * granted at install time only up to Android 12, and from 13 an ungranted app receives its FCM
     * messages but is not allowed to post a notification for them. Nothing errors — the message
     * arrives, the notification is dropped, and the app looks like it simply does not do push.
     *
     * <p>Deliberately fire-and-forget. Whether someone allows notifications does not change what the
     * app can show while open, so there is nothing to do with the answer, and blocking the first
     * screen on a permission dialog would be worse than not asking.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Nothing to handle in the callback, but the launcher must exist for the dialog to show.
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
            .launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun render(chatId: String?) {
        setContent {
            val dark = remember { mutableStateOf(false) }
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                var session by remember { mutableStateOf(tokenStore.session()) }
                PrabhixNavHost(
                    isLoggedIn = session != null,
                    hasOrg = session?.organizationId != null,
                    isPlatformAdmin = session?.platformAdmin == true,
                    deepLinkChatId = chatId,
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
                .setTitle("Unlock ${BuildConfig.APP_LABEL}")
                .setSubtitle("Verify to access customer conversations")
                .setNegativeButtonText("Cancel")
                .build(),
        )
    }
}
