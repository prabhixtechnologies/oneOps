package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.CommerceEnums;
import com.prabhix.platform.commerce.domain.CommerceSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommerceSubscriptionRepository extends JpaRepository<CommerceSubscription, UUID> {

    Optional<CommerceSubscription> findByOrderItemId(UUID orderItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CommerceSubscription s WHERE s.id = :id")
    Optional<CommerceSubscription> lockById(@Param("id") UUID id);

    @Query("""
            SELECT s FROM CommerceSubscription s
            WHERE s.status IN :statuses
              AND s.nextBillingAt IS NOT NULL
              AND s.nextBillingAt <= :now
            ORDER BY s.nextBillingAt ASC, s.id ASC
            """)
    List<CommerceSubscription> findDueForRenewal(@Param("statuses") List<CommerceEnums.SubscriptionStatus> statuses,
                                                 @Param("now") Instant now,
                                                 Pageable pageable);

    @Query("""
            SELECT s FROM CommerceSubscription s
            WHERE s.status = :status
              AND s.failedPaymentCount < :maxAttempts
              AND (s.nextDunningRetryAt IS NULL OR s.nextDunningRetryAt <= :now)
            ORDER BY s.nextDunningRetryAt ASC NULLS FIRST, s.id ASC
            """)
    List<CommerceSubscription> findDueForDunningRetry(@Param("status") CommerceEnums.SubscriptionStatus status,
                                                      @Param("maxAttempts") int maxAttempts,
                                                      @Param("now") Instant now,
                                                      Pageable pageable);
}
