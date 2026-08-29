package com.prabhix.operator.data.repository

import com.prabhix.operator.data.api.PlatformAdminApi
import com.prabhix.operator.data.api.PlatformOverview
import com.prabhix.operator.data.api.TenantSummary
import com.prabhix.operator.data.auth.TokenStore
import com.prabhix.operator.data.auth.ViewingOrg
import com.prabhix.operator.data.auth.ViewingOrgStore
import com.prabhix.operator.data.sse.RealtimeHub
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-tenant reads and tenant impersonation, for the admin app.
 *
 * <p>The web console's equivalent lives in `features/ops/api.ts` plus `lib/use-viewing-org.ts`; the
 * two are together here because on mobile they are one screen's worth of work.
 */
@Singleton
class PlatformRepository @Inject constructor(
    private val platformAdminApi: PlatformAdminApi,
    private val viewingOrgStore: ViewingOrgStore,
    private val realtimeHub: RealtimeHub,
    private val tokenStore: TokenStore,
) {
    val viewing: StateFlow<ViewingOrg?> = viewingOrgStore.current

    fun isPlatformAdmin(): Boolean = tokenStore.session()?.platformAdmin == true

    suspend fun overview(): PlatformOverview = platformAdminApi.overview()

    /** One page of the tenant directory. [cursor] is null for the first page. */
    suspend fun tenants(status: String? = null, cursor: String? = null): TenantPage {
        val page = platformAdminApi.tenants(status = status, cursor = cursor, limit = PAGE_SIZE)
        return TenantPage(page.items, page.nextCursor.takeIf { page.hasMore })
    }

    /**
     * Enters a customer's organization.
     *
     * <p>The live streams are restarted rather than left alone: they were opened against the
     * previous organization and would keep delivering its messages into screens now showing this
     * one's, which is how a reply ends up in the wrong company's conversation.
     */
    fun startViewing(tenant: TenantSummary) {
        viewingOrgStore.start(ViewingOrg(id = tenant.id, name = tenant.name))
        realtimeHub.restart()
    }

    fun stopViewing() {
        viewingOrgStore.stop()
        realtimeHub.restart()
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}

data class TenantPage(val items: List<TenantSummary>, val nextCursor: String?)
