package com.prabhix.platform.billing.service;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingOrder;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionRenewalJobTest {

    @Mock private BillingSubscriptionRepository subscriptionRepository;
    @Mock private BillingRenewalService renewalService;
    @Mock private EntitlementService entitlementService;
    @Mock private StringRedisTemplate redis;

    private SubscriptionRenewalJob job;

    @BeforeEach
    void setUp() {
        job = new SubscriptionRenewalJob(subscriptionRepository, renewalService, entitlementService, redis);
    }

    @Test
    void failedRenewalMarksSubscriptionPastDue() {
        UUID subId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        BillingSubscription subscription = new BillingSubscription();
        subscription.setId(subId);
        subscription.setOrganizationId(orgId);
        subscription.setNextBillingAt(Instant.now().minusSeconds(30));
        subscription.setCancelAtPeriodEnd(false);
        BillingOrder order = new BillingOrder();

        when(subscriptionRepository.findDueForRenewal(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(subscription));
        when(subscriptionRepository.lockById(subId)).thenReturn(Optional.of(subscription));
        when(renewalService.createRenewalOrder(subscription)).thenReturn(order);
        when(renewalService.attemptOffSessionCharge(subscription, order))
                .thenReturn(new BillingRenewalService.ChargeAttemptResult(false, order, "no token"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.renewDueSubscriptions();

        verify(subscriptionRepository).save(subscription);
        org.junit.jupiter.api.Assertions.assertEquals(
                BillingEnums.SubscriptionStatus.PAST_DUE, subscription.getStatus());
        verify(entitlementService).evictCache(orgId);
    }

    @Test
    void cancelAtPeriodEndExpiresWithoutCharge() {
        UUID subId = UUID.randomUUID();
        BillingSubscription subscription = new BillingSubscription();
        subscription.setId(subId);
        subscription.setOrganizationId(UUID.randomUUID());
        subscription.setNextBillingAt(Instant.now().minusSeconds(30));
        subscription.setCancelAtPeriodEnd(true);

        when(subscriptionRepository.findDueForRenewal(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(subscription));
        when(subscriptionRepository.lockById(subId)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.renewDueSubscriptions();

        verify(renewalService, never()).createRenewalOrder(any());
        org.junit.jupiter.api.Assertions.assertEquals(
                BillingEnums.SubscriptionStatus.CANCELLED, subscription.getStatus());
    }
}
