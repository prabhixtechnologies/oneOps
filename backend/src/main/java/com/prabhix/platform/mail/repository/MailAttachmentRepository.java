package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailAttachmentRepository extends JpaRepository<MailAttachment, UUID> {

    List<MailAttachment> findByMessageId(UUID messageId);

    List<MailAttachment> findByMessageIdAndOrganizationId(UUID messageId, UUID organizationId);

    Optional<MailAttachment> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByFileIdAndOrganizationId(UUID fileId, UUID organizationId);

    Optional<MailAttachment> findByFileIdAndOrganizationId(UUID fileId, UUID organizationId);
}
