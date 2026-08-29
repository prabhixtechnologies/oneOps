package com.prabhix.operator.data.auth

import com.prabhix.operator.BuildConfig
import com.prabhix.operator.data.api.ApiErrorBody
import com.prabhix.operator.data.api.ApiException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    private val viewingOrgStore: ViewingOrgStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val session = tokenStore.session()
        val builder = original.newBuilder()
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Prabhix-Device", BuildConfig.DEVICE_HEADER)

        session?.let {
            builder.header("Authorization", "Bearer ${it.accessToken}")
            // While staff are viewing a customer, every request asks for that customer's rows
            // instead of their own. The signed-in identity is unchanged — only the tenant scope is —
            // and the server audits each one against the organization named here.
            val org = viewingOrgStore.organizationIdOverride() ?: it.organizationId
            org?.let { id -> builder.header("X-Prabhix-Org", id) }
        }

        return chain.proceed(builder.build())
    }
}

/**
 * Takes a [Provider] rather than the refresher itself to break a construction cycle: the refresher
 * needs [com.prabhix.operator.data.api.AuthApi], which comes from the Retrofit instance built on the
 * very OkHttpClient this authenticator is installed into. Resolution is deferred to the first 401,
 * by which point the graph is fully built.
 */
@Singleton
class AuthAuthenticator @Inject constructor(
    private val tokenRefresher: Provider<TokenRefresher>,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val newToken = runBlocking { tokenRefresher.get().refreshIfNeeded(force = true) } ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

object ApiErrorParser {
    fun parse(json: Json, body: String?): ApiException? {
        if (body.isNullOrBlank()) return null
        return try {
            ApiException.FromBody(json.decodeFromString(ApiErrorBody.serializer(), body))
        } catch (_: Exception) {
            null
        }
    }
}
