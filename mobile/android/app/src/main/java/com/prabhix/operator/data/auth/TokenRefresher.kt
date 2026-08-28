package com.prabhix.operator.data.auth

import com.prabhix.operator.data.api.AuthApi
import com.prabhix.operator.data.api.RefreshRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenRefresher @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
) {
    private val mutex = Mutex()

    suspend fun refreshIfNeeded(force: Boolean = false): String? = mutex.withLock {
        val session = tokenStore.session() ?: return null
        val stillValid = !force && System.currentTimeMillis() < session.expiresAtEpochMs - 60_000
        if (stillValid) return session.accessToken

        val refreshed = authApi.refresh(RefreshRequest(session.refreshToken))
        tokenStore.saveTokens(refreshed)
        refreshed.accessToken
    }
}
