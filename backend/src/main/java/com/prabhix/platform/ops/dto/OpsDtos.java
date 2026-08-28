package com.prabhix.platform.ops.dto;

import com.prabhix.platform.org.domain.Organization;

import java.time.Instant;
import java.util.UUID;

/**
 * Responses for the platform operator hub.
 *
 * <p>Everything here spans tenants, which is what separates it from the org-scoped dashboards.
 * These shapes are counts and directory rows only — no tenant content — so an operator can judge
 * the health of the platform without reading anybody's mail.
 */
public final class OpsDtos {

    public record PlatformOverview(
            TenantCounts tenants,
            AccountCounts accounts,
            QueueDepths queues,
            ActivityCounts activity,
            Instant generatedAt) {
    }

    public record TenantCounts(
            long total,
            long active,
            long trial,
            long suspended,
            long cancelled,
            long createdLast30Days) {
    }

    public record AccountCounts(
            long total,
            long active,
            long invited,
            long disabled,
            long lockedOut,
            long platformAdmins,
            long createdLast30Days) {
    }

    /** Backlogs worth paging someone about. */
    public record QueueDepths(
            long mailPending,
            long mailFailed,
            long activeSessions) {
    }

    public record ActivityCounts(
            long errorsLast24h,
            long securityEventsLast24h) {
    }

    public record TenantSummary(
            UUID id,
            String name,
            String slug,
            Organization.OrganizationStatus status,
            int memberCount,
            int seatLimit,
            Instant trialEndsAt,
            Instant createdAt) {
    }

    private OpsDtos() {
    }
}
