package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailCannedReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailCannedReplyRepository extends JpaRepository<MailCannedReply, UUID> {

    List<MailCannedReply> findByOrganizationIdAndDeletedAtIsNullOrderByTitle(UUID organizationId);

    Optional<MailCannedReply> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
