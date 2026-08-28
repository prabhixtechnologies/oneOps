package com.prabhix.platform.chat.repository;

import com.prabhix.platform.chat.domain.ChatCannedReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatCannedReplyRepository extends JpaRepository<ChatCannedReply, UUID> {

    List<ChatCannedReply> findByOrganizationIdAndDeletedAtIsNullOrderByTitleAsc(UUID organizationId);

    Optional<ChatCannedReply> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
