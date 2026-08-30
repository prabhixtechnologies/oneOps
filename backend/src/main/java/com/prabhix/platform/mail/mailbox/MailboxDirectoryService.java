package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.spi.MailboxDirectory;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.repository.MailboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Mail's implementation of {@link MailboxDirectory}. */
@Service
@RequiredArgsConstructor
public class MailboxDirectoryService implements MailboxDirectory {

    private final MailboxRepository mailboxRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> addressOf(UUID organizationId, UUID mailboxId) {
        if (organizationId == null || mailboxId == null) {
            return Optional.empty();
        }
        return mailboxRepository
                .findByIdAndOrganizationIdAndDeletedAtIsNull(mailboxId, organizationId)
                .map(Mailbox::getAddress);
    }
}
