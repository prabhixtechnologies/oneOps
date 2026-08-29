package com.prabhix.operator.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * What the platform is still asked about authentication, now that Identity does the authenticating.
 *
 * <p>The login, OTP, magic-link and refresh endpoints are gone from here. They still exist on the
 * server for the web's non-OIDC path, but this app cannot reach a password field any more: sign-in is
 * a Custom Tab on Identity's hosted page, and refresh goes to Identity's token endpoint, which is the
 * only service that has ever seen the refresh token.
 *
 * <p>What remains is the platform answering questions only it can: who this token belongs to according
 * to its own database, and what they may do.
 */
interface AuthApi {
    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest)

    @GET("auth/me")
    suspend fun me(): AuthMeResponse
}

interface OrganizationApi {
    @GET("organizations")
    suspend fun list(): List<OrganizationView>
}

