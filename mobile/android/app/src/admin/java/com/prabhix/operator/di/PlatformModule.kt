package com.prabhix.operator.di

import com.prabhix.operator.data.api.PlatformAdminApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

/**
 * Binds the cross-tenant API for the admin flavor only.
 *
 * <p>Separate from [NetworkModule] on purpose. A `@Provides` method there would be reachable from
 * the dependency graph in both flavors, which keeps the interface — and so the endpoint paths in its
 * annotations — inside the customer's APK even with every screen that uses it removed.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {

    @Provides
    fun platformAdminApi(retrofit: Retrofit): PlatformAdminApi =
        retrofit.create(PlatformAdminApi::class.java)
}
