package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MailAttachmentRepository extends JpaRepository<MailAttachment, UUID> {

    List<MailAttachment> findByMessageId(UUID messageId);
}
