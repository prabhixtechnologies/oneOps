package com.prabhix.operator.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest)

    @GET("auth/me")
    suspend fun me(): AuthMeResponse

    @POST("auth/otp/request")
    suspend fun requestOtp(@Body body: EmailRequest): AckResponse

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyRequest): TokenResponse

    @POST("auth/magic-link/request")
    suspend fun requestMagicLink(@Body body: EmailRequest): AckResponse

    @POST("auth/magic-link/verify")
    suspend fun verifyMagicLink(@Body body: MagicLinkVerifyRequest): TokenResponse
}

interface OrganizationApi {
    @GET("organizations")
    suspend fun list(): List<OrganizationView>

    @POST("organizations/{id}/select")
    suspend fun select(@Path("id") id: String): TokenResponse
}

