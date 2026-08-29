package com.prabhix.operator.data.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the access token fresh.
 *
 * <p>Refreshes against Identity's token endpoint rather than the platform's `/auth/refresh`. Both
 * tokens are now minted by Identity, and the platform has no record of the refresh token at all — it
 * only verifies access tokens against the published JWKS — so asking it to refresh would fail.
 *
 * <p>The mutex matters more than it looks: refresh tokens rotate, so two concurrent 401s racing to
 * refresh would have one of them redeem a token the other already consumed, and Identity treats a
 * reused refresh token as theft and kills the chain. Serialising means the second caller waits and
 * then finds the token already valid.
 */
@Singleton
class TokenRefresher @Inject constructor(
    private val identity: IdentityAuthenticator,
    private val tokenStore: TokenStore,
) {
    private val mutex = Mutex()

    suspend fun refreshIfNeeded(force: Boolean = false): String? = mutex.withLock {
        val session = tokenStore.session() ?: return null
        val stillValid = !force && System.currentTimeMillis() < session.expiresAtEpochMs - 60_000
        if (stillValid) return session.accessToken

        val refreshed = identity.refresh(session.refreshToken)
        tokenStore.saveOidcTokens(
            accessToken = refreshed.accessToken,
            refreshToken = refreshed.refreshToken,
            idToken = refreshed.idToken,
            expiresAtEpochMs = refreshed.expiresAtEpochMs,
        )
        refreshed.accessToken
    }
}
