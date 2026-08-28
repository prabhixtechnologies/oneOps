package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailDeliveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MailDeliveryEventRepository extends JpaRepository<MailDeliveryEvent, UUID> {
}
