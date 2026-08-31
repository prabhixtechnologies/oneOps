package com.prabhix.platform.billing.service;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingOrder;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.repository.BillingPlanRepository;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.common.mail.MailClient;
import com.prabhix.platform.common.mail.MailRequest;
import com.prabhix.platform.config.PrabhixProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DunningJobTest {

    @Mock private BillingSubscriptionRepository subscriptionRepository;
    @Mock private BillingPlanRepository planRepository;
    @Mock private BillingOrgReader orgReader;
    @Mock private BillingRenewalService renewalService;
    @Mock private MailClient mail;
    @Mock private PrabhixProperties properties;
    @Mock private StringRedisTemplate redis;

    private DunningJob job;

    @BeforeEach
    void setUp() {
        job = new DunningJob(
                subscriptionRepository, planRepository, orgReader, renewalService, mail, properties, redis);
    }

    @Test
    void successfulChargeSkipsFailureMail() {
        UUID subId = UUID.randomUUID();
        BillingSubscription subscription = pastDueSubscription(subId);
        BillingOrder order = new BillingOrder();

        when(subscriptionRepository.findDueForDunningRetry(
                eq(BillingEnums.SubscriptionStatus.PAST_DUE),
                eq(DunningSchedule.MAX_ATTEMPTS),
                any(Instant.class),
                any(Pageable.class))).thenReturn(List.of(subscription));
        when(subscriptionRepository.lockById(subId)).thenReturn(Optional.of(subscription));
        when(renewalService.createRenewalOrder(subscription)).thenReturn(order);
        when(renewalService.attemptOffSessionCharge(subscription, order))
                .thenReturn(new BillingRenewalService.ChargeAttemptResult(true, order, null));

        job.processDueRetries();

        verify(mail, never()).send(any(MailRequest.class));
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void failedChargeSchedulesNextRetryAndEmails() {
        UUID subId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        BillingSubscription subscription = pastDueSubscription(subId);
        subscription.setOrganizationId(orgId);
        subscription.setFailedPaymentCount(0);
        BillingOrder order = new BillingOrder();

        when(subscriptionRepository.findDueForDunningRetry(any(), any(int.class), any(), any()))
                .thenReturn(List.of(subscription));
        when(subscriptionRepository.lockById(subId)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(renewalService.createRenewalOrder(subscription)).thenReturn(order);
        when(renewalService.attemptOffSessionCharge(subscription, order))
                .thenReturn(new BillingRenewalService.ChargeAttemptResult(false, order, "declined"));
        when(orgReader.find(orgId)).thenReturn(Optional.of(
                new BillingOrgReader.BillingOrgSnapshot("Acme", null, null, "bill@acme.test", null)));
        when(properties.urls()).thenReturn(new PrabhixProperties.Urls(
                "https://marketing", "https://console", "https://api"));

        job.processDueRetries();

        ArgumentCaptor<BillingSubscription> saved = ArgumentCaptor.forClass(BillingSubscription.class);
        verify(subscriptionRepository).save(saved.capture());
        assertEquals(1, saved.getValue().getFailedPaymentCount());
        verify(mail).send(any(MailRequest.class));
    }

    private static BillingSubscription pastDueSubscription(UUID id) {
        BillingSubscription subscription = new BillingSubscription();
        subscription.setId(id);
        subscription.setOrganizationId(UUID.randomUUID());
        subscription.setPlanId(UUID.randomUUID());
        subscription.setStatus(BillingEnums.SubscriptionStatus.PAST_DUE);
        subscription.setFailedPaymentCount(0);
        subscription.setLockedAmountPaise(1000);
        subscription.setLockedPerSeatPaise(0);
        subscription.setSeats(1);
        subscription.setNextDunningRetryAt(Instant.now().minusSeconds(60));
        return subscription;
    }
}
