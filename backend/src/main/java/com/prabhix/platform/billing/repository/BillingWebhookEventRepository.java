package com.prabhix.platform.billing.repository;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingWebhookEventRepository extends JpaRepository<BillingWebhookEvent, UUID> {

    Optional<BillingWebhookEvent> findByProviderAndProviderEventId(
            BillingEnums.WebhookProvider provider, String providerEventId);

    List<BillingWebhookEvent> findTop50ByStatusInOrderByReceivedAtAsc(
            List<BillingEnums.WebhookEventStatus> statuses);
}
