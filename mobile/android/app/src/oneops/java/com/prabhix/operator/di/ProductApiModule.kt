package com.prabhix.operator.di

import com.prabhix.operator.data.api.ChatAiApi
import com.prabhix.operator.data.api.ChatApi
import com.prabhix.operator.data.api.DashboardApi
import com.prabhix.operator.data.api.MailAiApi
import com.prabhix.operator.data.api.MailApi
import com.prabhix.operator.data.api.PushApi
import com.prabhix.operator.data.api.VisitorApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

/**
 * The product's Retrofit interfaces, provided only to the OneOps app.
 *
 * <p>The shared [NetworkModule] provides the client, the converter and sign-in; this adds the
 * tenant-scoped endpoints on top. Splitting the providers this way is what makes the boundary real
 * rather than stylistic: the admin app's dependency graph contains no reference to a chat or mail
 * API, so R8 has nothing to keep and the endpoints are absent from the APK.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProductApiModule {
    @Provides fun chatApi(retrofit: Retrofit): ChatApi = retrofit.create(ChatApi::class.java)
    @Provides fun mailApi(retrofit: Retrofit): MailApi = retrofit.create(MailApi::class.java)
    @Provides fun visitorApi(retrofit: Retrofit): VisitorApi = retrofit.create(VisitorApi::class.java)
    @Provides fun dashboardApi(retrofit: Retrofit): DashboardApi = retrofit.create(DashboardApi::class.java)
    @Provides fun pushApi(retrofit: Retrofit): PushApi = retrofit.create(PushApi::class.java)
    @Provides fun chatAiApi(retrofit: Retrofit): ChatAiApi = retrofit.create(ChatAiApi::class.java)
    @Provides fun mailAiApi(retrofit: Retrofit): MailAiApi = retrofit.create(MailAiApi::class.java)
}
