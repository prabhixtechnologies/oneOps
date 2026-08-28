package com.prabhix.platform.ops.service;

import com.prabhix.platform.auth.repository.DeviceSessionRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailEnums.OutboxStatus;
import com.prabhix.platform.mail.repository.MailOutboxRepository;
import com.prabhix.platform.observability.repository.EventLogRepository;
import com.prabhix.platform.ops.dto.OpsDtos;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.domain.Organization.OrganizationStatus;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.user.domain.User.UserStatus;
import com.prabhix.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Cross-tenant counts and the tenant directory behind the ops hub.
 *
 * <p>Every read here is a COUNT or a keyset page, so the endpoint stays cheap enough to poll.
 * Nothing is cached: an operator looking at this screen is usually doing so because something is
 * wrong, and a stale number is worse than a slightly slower page.
 */
@Service
@RequiredArgsConstructor
public class PlatformOverviewService {

    private static final Duration RECENT_WINDOW = Duration.ofDays(30);
    private static final Duration ACTIVITY_WINDOW = Duration.ofHours(24);

    /** Anything not yet handed to a transport, including rows a worker has claimed mid-flight. */
    private static final Set<OutboxStatus> MAIL_IN_FLIGHT =
            EnumSet.of(OutboxStatus.PENDING, OutboxStatus.CLAIMED, OutboxStatus.SENDING);

    /** DEAD counts as failed: attempts are exhausted, so it needs a human either way. */
    private static final Set<OutboxStatus> MAIL_FAILED =
            EnumSet.of(OutboxStatus.FAILED, OutboxStatus.DEAD);

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final MailOutboxRepository mailOutboxRepository;
    private final DeviceSessionRepository deviceSessionRepository;
    private final EventLogRepository eventLogRepository;
    private final PrabhixProperties properties;

    @Transactional(readOnly = true)
    public OpsDtos.PlatformOverview overview() {
        Instant now = Instant.now();
        Instant recentSince = now.minus(RECENT_WINDOW);
        Instant activitySince = now.minus(ACTIVITY_WINDOW);

        OpsDtos.TenantCounts tenants = new OpsDtos.TenantCounts(
                organizationRepository.countByDeletedAtIsNull(),
                organizationRepository.countByStatusAndDeletedAtIsNull(OrganizationStatus.ACTIVE),
                organizationRepository.countByStatusAndDeletedAtIsNull(OrganizationStatus.TRIAL),
                organizationRepository.countByStatusAndDeletedAtIsNull(OrganizationStatus.SUSPENDED),
                organizationRepository.countByStatusAndDeletedAtIsNull(OrganizationStatus.CANCELLED),
                organizationRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(recentSince));

        OpsDtos.AccountCounts accounts = new OpsDtos.AccountCounts(
                userRepository.countByDeletedAtIsNull(),
                userRepository.countByStatusAndDeletedAtIsNull(UserStatus.ACTIVE),
                userRepository.countByStatusAndDeletedAtIsNull(UserStatus.INVITED),
                userRepository.countByStatusAndDeletedAtIsNull(UserStatus.DISABLED),
                userRepository.countByLockedUntilAfterAndDeletedAtIsNull(now),
                userRepository.countByPlatformAdminTrueAndDeletedAtIsNull(),
                userRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(recentSince));

        OpsDtos.QueueDepths queues = new OpsDtos.QueueDepths(
                mailOutboxRepository.countByStatusIn(MAIL_IN_FLIGHT),
                mailOutboxRepository.countByStatusIn(MAIL_FAILED),
                deviceSessionRepository.countByRevokedAtIsNull());

        OpsDtos.ActivityCounts activity = new OpsDtos.ActivityCounts(
                eventLogRepository.countErrorsSince(activitySince),
                eventLogRepository.countSecurityEventsSince(activitySince));

        return new OpsDtos.PlatformOverview(tenants, accounts, queues, activity, now);
    }

    @Transactional(readOnly = true)
    public CursorPage<OpsDtos.TenantSummary> listTenants(String status, String cursor, Integer limit) {
        Cursor decoded = Optional.ofNullable(Cursor.decode(cursor)).orElseGet(Cursor::beginning);
        int pageSize = properties.limits().clampPageSize(limit) + 1;

        List<Organization> fetched = organizationRepository.listWithCursor(
                normalizeStatus(status), decoded.timestamp(), decoded.id(), pageSize);

        return CursorPage.of(fetched, pageSize - 1, this::toTenantSummary,
                o -> Cursor.of(o.getCreatedAt(), o.getId()).encode());
    }

    private OpsDtos.TenantSummary toTenantSummary(Organization org) {
        return new OpsDtos.TenantSummary(
                org.getId(), org.getName(), org.getSlug(), org.getStatus(),
                org.getMemberCount(), org.getSeatLimit(), org.getTrialEndsAt(), org.getCreatedAt());
    }

    /**
     * Rejects an unknown status rather than passing it through. The value reaches SQL as a bound
     * parameter so there is nothing to inject, but a typo would quietly return an empty list and
     * look like "no tenants in that state".
     */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrganizationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                    "Unknown organization status: " + status);
        }
    }
}
