package com.prabhix.platform.mail.provisioning;

import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.repository.MailboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-way migration for mailbox passwords written before AES-GCM storage was introduced.
 *
 * <p>The cutover runs this before normal ready-event work. It is idempotent: encrypted and blank
 * values are untouched, and no credential value is ever logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyMailboxCredentialMigration {

    private final MailboxRepository mailboxes;
    private final MailboxCredentialsCipher cipher;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void encryptLegacyCredentials() {
        int migrated = 0;
        for (Mailbox mailbox : mailboxes.findAll()) {
            boolean changed = false;
            if (!cipher.isEncrypted(mailbox.getImapPasswordEnc())) {
                cipher.storeImapPassword(mailbox, mailbox.getImapPasswordEnc());
                changed = true;
            }
            if (!cipher.isEncrypted(mailbox.getSmtpPasswordEnc())) {
                cipher.storeSmtpPassword(mailbox, mailbox.getSmtpPasswordEnc());
                changed = true;
            }
            if (changed) {
                mailboxes.save(mailbox);
                migrated++;
            }
        }
        if (migrated > 0) {
            log.info("Encrypted legacy mailbox credentials for {} mailbox(es)", migrated);
        }
    }
}
