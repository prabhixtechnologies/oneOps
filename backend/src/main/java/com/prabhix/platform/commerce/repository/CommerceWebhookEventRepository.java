package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.CommerceWebhookEvent;
import com.prabhix.platform.commerce.domain.CommerceEnums.WebhookEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommerceWebhookEventRepository extends JpaRepository<CommerceWebhookEvent, java.util.UUID> {

    Optional<CommerceWebhookEvent> findByProviderAndProviderEventId(String provider, String providerEventId);

    List<CommerceWebhookEvent> findTop50ByStatusInOrderByReceivedAtAsc(List<WebhookEventStatus> statuses);
}
