package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailAliasRepository extends JpaRepository<MailAlias, UUID> {

    Optional<MailAlias> findByAddressIgnoreCase(String address);

    List<MailAlias> findByMailboxIdAndOrganizationId(UUID mailboxId, UUID organizationId);
}
