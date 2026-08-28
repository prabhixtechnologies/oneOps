package com.prabhix.operator.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.prabhix.operator.BuildConfig
import com.prabhix.operator.data.api.AuthApi
import com.prabhix.operator.data.api.ChatAiApi
import com.prabhix.operator.data.api.ChatApi
import com.prabhix.operator.data.api.DashboardApi
import com.prabhix.operator.data.api.MailAiApi
import com.prabhix.operator.data.api.MailApi
import com.prabhix.operator.data.api.OrganizationApi
import com.prabhix.operator.data.api.PushApi
import com.prabhix.operator.data.api.VisitorApi
import com.prabhix.operator.data.auth.AuthAuthenticator
import com.prabhix.operator.data.auth.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        authAuthenticator: AuthAuthenticator,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(authAuthenticator)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
    @Provides fun organizationApi(retrofit: Retrofit): OrganizationApi = retrofit.create(OrganizationApi::class.java)
    @Provides fun chatApi(retrofit: Retrofit): ChatApi = retrofit.create(ChatApi::class.java)
    @Provides fun mailApi(retrofit: Retrofit): MailApi = retrofit.create(MailApi::class.java)
    @Provides fun visitorApi(retrofit: Retrofit): VisitorApi = retrofit.create(VisitorApi::class.java)
    @Provides fun dashboardApi(retrofit: Retrofit): DashboardApi = retrofit.create(DashboardApi::class.java)
    @Provides fun pushApi(retrofit: Retrofit): PushApi = retrofit.create(PushApi::class.java)
    @Provides fun chatAiApi(retrofit: Retrofit): ChatAiApi = retrofit.create(ChatAiApi::class.java)
    @Provides fun mailAiApi(retrofit: Retrofit): MailAiApi = retrofit.create(MailAiApi::class.java)

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"
}
