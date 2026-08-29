package com.prabhix.operator.data.repository

import com.prabhix.operator.data.api.PlatformAdminApi
import com.prabhix.operator.data.api.PlatformOverview
import com.prabhix.operator.data.api.TenantSummary
import com.prabhix.operator.data.auth.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-tenant reads for the admin app: the platform overview and the tenant directory.
 *
 * <p>Reads only, and only the two endpoints the `/admin/platform` namespace offers. There is no
 * impersonation here on purpose — entering a customer means opening their screens, and those are in
 * the OneOps console on the web. An override that changed which organization this app's requests
 * name would have nothing to show for it.
 *
 * <p>The web console's equivalent is `features/ops/api.ts`.
 */
@Singleton
class PlatformRepository @Inject constructor(
    private val platformAdminApi: PlatformAdminApi,
    private val tokenStore: TokenStore,
) {
    fun isPlatformAdmin(): Boolean = tokenStore.session()?.platformAdmin == true

    suspend fun overview(): PlatformOverview = platformAdminApi.overview()

    /** One page of the tenant directory. [cursor] is null for the first page. */
    suspend fun tenants(status: String? = null, cursor: String? = null): TenantPage {
        val page = platformAdminApi.tenants(status = status, cursor = cursor, limit = PAGE_SIZE)
        return TenantPage(page.items, page.nextCursor.takeIf { page.hasMore })
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}

data class TenantPage(val items: List<TenantSummary>, val nextCursor: String?)
