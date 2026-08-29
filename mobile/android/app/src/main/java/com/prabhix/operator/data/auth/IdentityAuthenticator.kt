package com.prabhix.operator.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.prabhix.operator.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.NoClientAuthentication
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Signs in against Prabhix Identity through the system browser.
 *
 * <p>The app no longer has a password field. Sign-in opens a Custom Tab on Identity's hosted login
 * page, which has three consequences worth the change: the password is typed into the browser and is
 * never in this process's memory, someone already signed in on the web is signed in here without
 * typing anything at all because the Custom Tab shares the browser's cookie jar, and whatever Identity
 * adds later — a second factor, a passkey, an SSO button — works in both apps with no release here.
 *
 * <p>A WebView would defeat all three: it has its own cookie store, so no shared session, and the host
 * app can read what is typed into it, which is exactly the property that makes federated providers
 * refuse to render in one.
 *
 * <p>PKCE is generated per request by AppAuth and is mandatory server-side, so an authorization code
 * intercepted from the redirect is worthless without the verifier that never left this process.
 */
@Singleton
class IdentityAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Endpoint locations, built rather than discovered.
     *
     * <p>Discovery would be one more network round trip on a cold start, and one more thing to fail on
     * a bad connection, to learn paths that are fixed by Spring Authorization Server and pinned by
     * Identity's own tests. The issuer is the only part that varies between builds.
     */
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("$ISSUER/oauth2/authorize"),
        Uri.parse("$ISSUER/oauth2/token"),
        null,
        Uri.parse("$ISSUER/connect/logout"),
    )

    private val authService by lazy { AuthorizationService(context) }

    /**
     * The intent to launch for sign-in, and the request it belongs to.
     *
     * <p>Returned together because completing the flow needs the original request: AppAuth checks the
     * returned state against it and takes the PKCE verifier from it. Losing the request means the
     * response cannot be validated, so the two travel as one value.
     */
    data class Authorization(val request: AuthorizationRequest, val intent: Intent)

    fun prepare(): Authorization {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            BuildConfig.OAUTH_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(BuildConfig.OAUTH_REDIRECT_URI),
        )
            // No `offline_access`. Spring Authorization Server issues a refresh token because the
            // client is registered for the refresh_token grant, not because the scope was asked for,
            // and asking for a scope the client is not registered with fails the whole request.
            .setScopes("openid", "profile", "email")
            // Asks the browser to show its account picker if more than one Prabhix account is signed
            // in there. Without it, a shared phone silently reuses whichever session the browser
            // happens to hold, which is the wrong default for the admin app in particular.
            .setPrompt(if (BuildConfig.APPLICATION_ID.contains("admin")) "select_account" else null)
            .build()
        return Authorization(request, authService.getAuthorizationRequestIntent(request))
    }

    /**
     * Turns the redirect back into tokens.
     *
     * @throws AuthorizationException if the user cancelled, or the response fails validation
     */
    suspend fun exchange(request: AuthorizationRequest, data: Intent?): Tokens {
        if (data == null) {
            throw AuthorizationException.fromTemplate(
                AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW, null)
        }
        AuthorizationException.fromIntent(data)?.let { throw it }

        val response = AuthorizationResponse.fromIntent(data)
            ?: throw AuthorizationException.fromTemplate(
                AuthorizationException.GeneralErrors.JSON_DESERIALIZATION_ERROR, null)

        // Rejects a redirect that does not belong to the request we started. Without this an attacker
        // who can trigger the redirect can log the victim in as somebody else.
        if (response.state != request.state) {
            throw AuthorizationException.fromTemplate(
                AuthorizationException.AuthorizationRequestErrors.STATE_MISMATCH, null)
        }

        return perform(response.createTokenExchangeRequest())
    }

    suspend fun refresh(refreshToken: String): Tokens = perform(
        TokenRequest.Builder(serviceConfig, BuildConfig.OAUTH_CLIENT_ID)
            .setGrantType("refresh_token")
            .setRefreshToken(refreshToken)
            .build(),
    )

    /** Where to send the browser so the shared session ends, not just this app's copy of it. */
    fun endSessionUri(idTokenHint: String?): Uri {
        val builder = Uri.parse("$ISSUER/connect/logout").buildUpon()
        idTokenHint?.let { builder.appendQueryParameter("id_token_hint", it) }
        return builder.build()
    }

    private suspend fun perform(request: TokenRequest): Tokens =
        suspendCancellableCoroutine { continuation ->
            // NoClientAuthentication because these are public clients: an app on a phone cannot keep a
            // secret, so it is not given one. PKCE is what proves the exchange, not a shared secret.
            val auth: ClientAuthentication = NoClientAuthentication.INSTANCE
            authService.performTokenRequest(request, auth) { response, exception ->
                when {
                    response != null -> continuation.resume(response.toTokens())
                    exception != null -> continuation.resumeWithException(exception)
                    else -> continuation.resumeWithException(
                        IllegalStateException("Token endpoint returned neither tokens nor an error"))
                }
            }
        }

    private fun TokenResponse.toTokens(): Tokens {
        val access = accessToken
            ?: throw IllegalStateException("Token endpoint returned no access token")
        return Tokens(
            accessToken = access,
            // Absent when the server declines to rotate, which should not happen here — the clients
            // ask for offline_access and rotation is on — but keeping the old one beats storing null
            // and forcing a full sign-in on the next refresh.
            refreshToken = refreshToken,
            idToken = idToken,
            expiresAtEpochMs = accessTokenExpirationTime
                ?: (System.currentTimeMillis() + DEFAULT_TTL_MS),
        )
    }

    data class Tokens(
        val accessToken: String,
        val refreshToken: String?,
        val idToken: String?,
        val expiresAtEpochMs: Long,
    )

    private companion object {
        val ISSUER: String = BuildConfig.IDENTITY_ISSUER.trimEnd('/')

        /** Only used if the server omits an expiry, which would itself be a bug worth noticing. */
        const val DEFAULT_TTL_MS = 15 * 60 * 1000L
    }
}
