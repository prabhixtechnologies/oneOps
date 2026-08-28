package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailThreadDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailThreadDraftRepository extends JpaRepository<MailThreadDraft, UUID> {

    Optional<MailThreadDraft> findByThreadIdAndAuthorUserId(UUID threadId, UUID authorUserId);

    List<MailThreadDraft> findByThreadId(UUID threadId);
}
