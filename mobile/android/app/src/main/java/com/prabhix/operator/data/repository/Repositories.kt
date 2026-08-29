package com.prabhix.operator.data.repository

import android.content.Intent
import com.prabhix.operator.data.api.ApiException
import com.prabhix.operator.data.api.AuthApi
import com.prabhix.operator.data.api.LogoutRequest
import com.prabhix.operator.data.api.OrganizationApi
import com.prabhix.operator.data.auth.ApiErrorParser
import com.prabhix.operator.data.auth.IdentityAuthenticator
import com.prabhix.operator.data.auth.TokenStore
import com.prabhix.operator.data.session.SessionLifecycle
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthorizationRequest
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signing in, choosing an organization, and signing out. Shared: both apps have one sign-in.
 *
 * <p>What happens *after* a session exists differs between them, and that difference is behind
 * [SessionLifecycle] rather than expressed here. Calling `RealtimeHub` and `PushTokenManager`
 * directly, as this class used to, put the product's streaming and push layers into the admin app —
 * Hilt held providers for them either way, so nothing could be stripped.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val organizationApi: OrganizationApi,
    private val identity: IdentityAuthenticator,
    private val tokenStore: TokenStore,
    private val sessionLifecycle: SessionLifecycle,
    private val json: Json,
) {
    fun session() = tokenStore.session()
    fun hasPermission(p: String) = tokenStore.hasPermission(p)

    /** The browser intent to launch, plus the request needed to validate what comes back. */
    fun beginSignIn(): IdentityAuthenticator.Authorization = identity.prepare()

    /**
     * Finishes the browser flow: code for tokens, then authorization from the platform.
     *
     * <p>Two calls rather than one because the split is real. Identity says who you are and nothing
     * more — its tokens carry no organization and no permissions, deliberately, so a compromised
     * identity service cannot grant itself access to a tenant. `/auth/me` is the platform answering
     * what this person may do, from its own database, and it is the only source of that answer.
     */
    suspend fun completeSignIn(request: AuthorizationRequest, data: Intent?): Result<Unit> = safeCall {
        val tokens = identity.exchange(request, data)
        tokenStore.saveOidcTokens(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            idToken = tokens.idToken,
            expiresAtEpochMs = tokens.expiresAtEpochMs,
        )

        val me = authApi.me()
        tokenStore.saveProfile(me.userId, me.email, me.displayName, me.platformAdmin)
        tokenStore.saveAuthorization(me.organizationId, me.permissions)
        sessionLifecycle.onSignedIn()
    }

    /**
     * Switches the organization this app acts in.
     *
     * <p>No longer mints a new token. The active tenant is a request header now, validated against
     * membership on every call, so switching is a local change followed by asking the platform what
     * this person may do there — which is also the check that the switch was allowed at all: naming an
     * organization you are not a member of fails, rather than appearing to work until the first screen
     * loads empty.
     */
    suspend fun selectOrganization(orgId: String): Result<Unit> = safeCall {
        val previous = tokenStore.organizationId()
        tokenStore.setOrganizationId(orgId)
        try {
            val me = authApi.me()
            tokenStore.saveAuthorization(me.organizationId ?: orgId, me.permissions)
        } catch (t: Throwable) {
            // Put it back. Leaving the header pointing at an organization the server refuses would
            // make every subsequent request fail, with no way back through the UI.
            tokenStore.setOrganizationId(previous)
            throw t
        }
        sessionLifecycle.onOrganizationChanged()
    }

    suspend fun organizations() = organizationApi.list()

    /**
     * Signs out here, and returns where to send the browser so the shared session ends too.
     *
     * <p>Clearing local tokens alone would leave the browser still signed in, so the next tap on
     * "Sign in" would come straight back with a new session and no prompt — which looks like the sign
     * out silently failed. The caller opens the returned URI in a tab.
     */
    suspend fun logout(): android.net.Uri {
        val idToken = tokenStore.idToken()
        runCatching {
            val refresh = tokenStore.session()?.refreshToken
            authApi.logout(LogoutRequest(refresh))
        }
        sessionLifecycle.onSignedOut()
        tokenStore.clear()
        return identity.endSessionUri(idToken)
    }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (t: Throwable) {
        Result.failure(wrap(t))
    }

    private fun wrap(t: Throwable): Throwable = when (t) {
        is ApiException -> t
        is HttpException -> {
            ApiErrorParser.parse(json, t.response()?.errorBody()?.string())
                ?: ApiException.Network(t)
        }
        else -> ApiException.Network(t)
    }
}

