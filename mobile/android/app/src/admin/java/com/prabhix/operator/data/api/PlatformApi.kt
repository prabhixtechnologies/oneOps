package com.prabhix.operator.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Cross-tenant endpoints, reachable only by a platform admin — the server gates every route under
 * `/admin` on that authority, so a customer holding a valid token still gets a 403.
 *
 * <p>In the admin source set rather than alongside the rest of the API, which is otherwise shared.
 * `proguard-rules.pro` keeps this whole package verbatim so response parsing cannot be broken by the
 * shrinker, and that keep rule applies to anything present — so an unused interface here would still
 * have shipped these paths in the customer's APK strings. Absent from the source set, there is
 * nothing to keep.
 */
interface PlatformAdminApi {
    @GET("admin/platform/overview")
    suspend fun overview(): PlatformOverview

    @GET("admin/platform/tenants")
    suspend fun tenants(
        @Query("status") status: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): CursorPage<TenantSummary>
}

/**
 * Platform-wide figures from the overview endpoint.
 *
 * <p>Every count has a default and every section is defaulted whole. The server omits null fields,
 * so a section it chose not to send would otherwise throw during deserialization and blank the
 * screen instead of showing the sections that did arrive — the failure mode that took out several
 * web pages before their schemas were relaxed the same way.
 */
@Serializable
data class PlatformOverview(
    val tenants: TenantCounts = TenantCounts(),
    val accounts: AccountCounts = AccountCounts(),
    val queues: QueueDepths = QueueDepths(),
    val activity: ActivityCounts = ActivityCounts(),
    val generatedAt: String? = null,
)

@Serializable
data class TenantCounts(
    val total: Long = 0,
    val active: Long = 0,
    val trial: Long = 0,
    val suspended: Long = 0,
    val cancelled: Long = 0,
    val createdLast30Days: Long = 0,
)

@Serializable
data class AccountCounts(
    val total: Long = 0,
    val active: Long = 0,
    val invited: Long = 0,
    val disabled: Long = 0,
    val lockedOut: Long = 0,
    val platformAdmins: Long = 0,
    val createdLast30Days: Long = 0,
)

/** Backlogs worth acting on. */
@Serializable
data class QueueDepths(
    val mailPending: Long = 0,
    val mailFailed: Long = 0,
    val activeSessions: Long = 0,
)

@Serializable
data class ActivityCounts(
    val errorsLast24h: Long = 0,
    val securityEventsLast24h: Long = 0,
)

@Serializable
data class TenantSummary(
    val id: String,
    val name: String,
    val slug: String,
    val status: String,
    val memberCount: Int = 0,
    val seatLimit: Int = 0,
    val trialEndsAt: String? = null,
    val createdAt: String? = null,
)
