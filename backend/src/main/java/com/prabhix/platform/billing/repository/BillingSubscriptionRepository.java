package com.prabhix.platform.billing.repository;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingSubscription;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingSubscriptionRepository extends JpaRepository<BillingSubscription, UUID> {

    Optional<BillingSubscription> findByOrganizationIdAndStatusIn(UUID organizationId,
                                                                  List<BillingEnums.SubscriptionStatus> statuses);

    List<BillingSubscription> findByStatus(BillingEnums.SubscriptionStatus status);

    @Query("""
            SELECT s FROM BillingSubscription s
            WHERE s.status = :status
            ORDER BY s.gracePeriodEndsAt ASC NULLS LAST, s.id ASC
            """)
    List<BillingSubscription> findPastDueBatch(@Param("status") BillingEnums.SubscriptionStatus status,
                                               Pageable pageable);

    @Query("""
            SELECT s FROM BillingSubscription s
            WHERE s.pendingChangeAt IS NOT NULL
              AND s.pendingChangeAt <= :now
            ORDER BY s.pendingChangeAt ASC
            """)
    List<BillingSubscription> findDuePendingChanges(@Param("now") Instant now, Pageable pageable);

    Optional<BillingSubscription> findByRazorpaySubscriptionId(String razorpaySubscriptionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM BillingSubscription s WHERE s.id = :id")
    Optional<BillingSubscription> lockById(@Param("id") UUID id);

    @Query("""
            SELECT s FROM BillingSubscription s
            WHERE s.status IN :statuses
              AND s.nextBillingAt IS NOT NULL
              AND s.nextBillingAt <= :now
              AND s.cancelAtPeriodEnd = false
            ORDER BY s.nextBillingAt ASC, s.id ASC
            """)
    List<BillingSubscription> findDueForRenewal(@Param("statuses") List<BillingEnums.SubscriptionStatus> statuses,
                                                @Param("now") Instant now,
                                                Pageable pageable);

    @Query("""
            SELECT s FROM BillingSubscription s
            WHERE s.status = :status
              AND s.failedPaymentCount < :maxAttempts
              AND (s.nextDunningRetryAt IS NULL OR s.nextDunningRetryAt <= :now)
            ORDER BY s.nextDunningRetryAt ASC NULLS FIRST, s.id ASC
            """)
    List<BillingSubscription> findDueForDunningRetry(@Param("status") BillingEnums.SubscriptionStatus status,
                                                       @Param("maxAttempts") int maxAttempts,
                                                       @Param("now") Instant now,
                                                       Pageable pageable);
}
