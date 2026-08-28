package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailWebhookEvent;
import com.prabhix.platform.mail.domain.MailEnums;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MailWebhookEventRepository extends JpaRepository<MailWebhookEvent, UUID> {

    Optional<MailWebhookEvent> findByProviderAndProviderEventId(
            MailEnums.WebhookProvider provider, String providerEventId);
}
