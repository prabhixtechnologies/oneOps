package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailThreadEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MailThreadEventRepository extends JpaRepository<MailThreadEvent, UUID> {

    List<MailThreadEvent> findByThreadIdOrderByCreatedAtAsc(UUID threadId);
}
