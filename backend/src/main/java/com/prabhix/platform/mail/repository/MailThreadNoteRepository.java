package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailThreadNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MailThreadNoteRepository extends JpaRepository<MailThreadNote, UUID> {

    List<MailThreadNote> findByThreadIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID threadId);
}
