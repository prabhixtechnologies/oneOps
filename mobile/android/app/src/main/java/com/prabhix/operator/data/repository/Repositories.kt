package com.prabhix.operator.data.repository

import com.prabhix.operator.BuildConfig
import com.prabhix.operator.data.api.ApiException
import com.prabhix.operator.data.api.AuthApi
import com.prabhix.operator.data.api.EmailRequest
import com.prabhix.operator.data.api.LoginRequest
import com.prabhix.operator.data.api.LogoutRequest
import com.prabhix.operator.data.api.MagicLinkVerifyRequest
import com.prabhix.operator.data.api.OrganizationApi
import com.prabhix.operator.data.api.OtpVerifyRequest
import com.prabhix.operator.data.api.TokenResponse
import com.prabhix.operator.data.auth.ApiErrorParser
import com.prabhix.operator.data.auth.TokenStore
import com.prabhix.operator.data.session.SessionLifecycle
import kotlinx.serialization.json.Json
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
    private val tokenStore: TokenStore,
    private val sessionLifecycle: SessionLifecycle,
    private val json: Json,
) {
    fun session() = tokenStore.session()
    fun hasPermission(p: String) = tokenStore.hasPermission(p)

    suspend fun login(email: String, password: String): Result<Unit> = safeCall {
        val response = authApi.login(
            LoginRequest(
                email = email.trim(),
                password = password,
                deviceId = tokenStore.deviceId(),
                // Names the app as well as the phone. Both apps can be installed at once, and two
                // sessions both labelled "Pixel 7" cannot be told apart when revoking one of them.
                deviceName = "${android.os.Build.MODEL} Â· ${BuildConfig.APP_LABEL}",
            ),
        )
        completeLogin(response)
    }

    suspend fun requestOtp(email: String) = authApi.requestOtp(EmailRequest(email.trim()))
    suspend fun verifyOtp(email: String, code: String) = safeCall {
        completeLogin(authApi.verifyOtp(OtpVerifyRequest(email.trim(), code.trim())))
    }

    suspend fun requestMagicLink(email: String) = authApi.requestMagicLink(EmailRequest(email.trim()))
    suspend fun verifyMagicLink(token: String) = safeCall {
        completeLogin(authApi.verifyMagicLink(MagicLinkVerifyRequest(token)))
    }

    private suspend fun completeLogin(response: TokenResponse) {
        tokenStore.saveTokens(response)
        val me = authApi.me()
        tokenStore.saveProfile(me.userId, me.email, me.displayName, me.platformAdmin)
        sessionLifecycle.onSignedIn()
    }

    suspend fun selectOrganization(orgId: String): Result<Unit> = safeCall {
        val tokens = organizationApi.select(orgId)
        tokenStore.saveTokens(tokens)
        tokenStore.setOrganizationId(orgId)
        sessionLifecycle.onOrganizationChanged()
    }

    suspend fun organizations() = organizationApi.list()

    suspend fun logout() {
        runCatching {
            val refresh = tokenStore.session()?.refreshToken
            authApi.logout(LogoutRequest(refresh))
        }
        sessionLifecycle.onSignedOut()
        tokenStore.clear()
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

