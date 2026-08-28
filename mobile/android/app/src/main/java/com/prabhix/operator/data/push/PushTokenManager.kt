package com.prabhix.operator.data.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.prabhix.operator.BuildConfig
import com.prabhix.operator.data.api.PushApi
import com.prabhix.operator.data.api.PushTokenRequest
import com.prabhix.operator.data.auth.TokenStore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushTokenManager @Inject constructor(
    private val pushApi: PushApi,
    private val tokenStore: TokenStore,
) {
    private var lastToken: String? = null

    suspend fun registerIfPossible() {
        if (tokenStore.session() == null) return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            registerToken(token)
        } catch (t: Throwable) {
            Log.w(TAG, "Push registration skipped (endpoint may not exist yet): ${t.message}")
        }
    }

    suspend fun registerToken(token: String) {
        if (tokenStore.session() == null) return
        try {
            lastToken = token
            pushApi.registerPushToken(
                PushTokenRequest(
                    token = token,
                    platform = "FCM",
                    deviceId = tokenStore.deviceId(),
                    deviceName = android.os.Build.MODEL,
                    appVersion = BuildConfig.VERSION_NAME,
                ),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Push registration failed: ${t.message}")
        }
    }

    suspend fun unregisterIfRegistered() {
        val token = lastToken ?: return
        runCatching { pushApi.unregisterPushToken(token) }
        lastToken = null
    }

    companion object {
        private const val TAG = "PushTokenManager"
    }
}
