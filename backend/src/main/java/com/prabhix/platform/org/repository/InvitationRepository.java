package com.prabhix.platform.org.repository;

import com.prabhix.platform.org.domain.Invitation;
import com.prabhix.platform.org.domain.Invitation.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    Optional<Invitation> findByOrganizationIdAndEmailAndStatus(
            UUID organizationId, String email, InvitationStatus status);

    List<Invitation> findByOrganizationIdAndStatusOrderByCreatedAtDesc(
            UUID organizationId, InvitationStatus status);
}
