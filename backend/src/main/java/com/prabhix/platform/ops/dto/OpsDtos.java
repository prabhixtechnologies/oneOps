package com.prabhix.platform.ops.dto;

import com.prabhix.platform.org.domain.Organization;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    /** Cross-tenant SaaS payments — major currency units (rupees). */
    public record PlatformBillingRevenue(
            BigDecimal capturedTotal,
            BigDecimal pendingTotal,
            long capturedCount,
            long pendingCount,
            long failedCount,
            String currency,
            Instant asOf) {
    }

    // --- Infra / AWS ops (consolidated from Infra/ops-tool) ---

    public record AwsDailyCost(String date, double amountUsd, String unit) {
    }

    public record AwsServiceCost(String service, double amountUsd) {
    }

    public record AwsCostsResponse(
            Boolean ok,
            Map<String, String> error,
            String region,
            String start,
            String end,
            Integer days,
            String currency,
            List<AwsDailyCost> daily,
            List<AwsServiceCost> byService,
            Double totalUsd,
            Double mtdUsd,
            Boolean cached,
            Instant fetchedAt) {
    }

    public record AwsInstanceRow(
            String instanceId,
            String name,
            String type,
            String state,
            String az,
            String privateIp,
            String publicIp,
            Instant launchTime,
            Double cpuAverage1h) {
    }

    public record AwsInstancesResponse(
            Boolean ok,
            Map<String, String> error,
            String region,
            List<AwsInstanceRow> instances,
            Integer running,
            Integer total) {
    }

    public record AwsSummaryCosts(
            Double totalUsd,
            Double mtdUsd,
            Integer days,
            List<AwsServiceCost> topServices,
            Boolean cached,
            Boolean ok,
            Map<String, String> error) {
    }

    public record AwsSummaryInstances(
            Integer total,
            Integer running,
            Boolean ok,
            Map<String, String> error) {
    }

    public record AwsSummaryResponse(
            Boolean ok,
            String region,
            String costExplorerRegion,
            AwsSummaryCosts costs,
            AwsSummaryInstances instances,
            Instant fetchedAt) {
    }

    public record ProductHealthRow(
            String name,
            String url,
            boolean ok,
            Integer statusCode,
            Long latencyMs,
            String error) {
    }

    public record ProductHealthResponse(
            Boolean ok,
            Instant checkedAt,
            Integer healthy,
            Integer total,
            List<ProductHealthRow> products) {
    }

    public record GithubCheckRow(
            String repo,
            String name,
            String status,
            String conclusion,
            String htmlUrl) {
    }

    public record GithubChecksResponse(
            Boolean ok,
            String note,
            Instant checkedAt,
            List<GithubCheckRow> checks) {
    }

    public record PnlRevenue(double mobiCaptured, double oneopsCaptured, double total) {
    }

    public record PnlResponse(
            Boolean ok,
            PnlRevenue revenue,
            Double awsMtd,
            String awsMtdSource,
            Double contribution,
            String note,
            Map<String, String> error) {
    }

    private OpsDtos() {
    }
}
