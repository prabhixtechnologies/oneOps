package com.prabhix.platform.mail.inbound;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.provisioning.MailboxCredentialsCipher;
import com.prabhix.platform.mail.repository.MailboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImapFetchScheduler {

    private final PrabhixProperties properties;
    private final MailboxRepository mailboxRepository;
    private final MailIngestionService ingestionService;
    private final MailboxCredentialsCipher credentialsCipher;

    @Scheduled(fixedDelayString = "${prabhix.mail.inbound.poll-interval}")
    @Transactional
    public void pollMailboxes() {
        if (!properties.mail().inbound().imapEnabled()) {
            return;
        }
        var mailboxes = mailboxRepository.findDueForImapPoll(MailEnums.MailboxStatus.ACTIVE);
        for (Mailbox mailbox : mailboxes) {
            if (shouldSkipDueToBackoff(mailbox)) {
                continue;
            }
            try {
                fetchMailbox(mailbox);
                mailbox.setImapConsecutiveErrors(0);
                mailbox.setImapLastError(null);
            } catch (Exception ex) {
                log.error("IMAP fetch failed for {}: {}", mailbox.getAddress(), ex.getMessage());
                mailbox.setImapConsecutiveErrors(mailbox.getImapConsecutiveErrors() + 1);
                mailbox.setImapLastError(truncate(ex.getMessage(), 500));
            }
            mailbox.setImapLastPolledAt(Instant.now());
            mailboxRepository.save(mailbox);
        }
    }

    private boolean shouldSkipDueToBackoff(Mailbox mailbox) {
        if (mailbox.getImapConsecutiveErrors() <= 0) {
            return false;
        }
        Duration backoff = Duration.ofSeconds(Math.min(3600, 30L * (1L << Math.min(mailbox.getImapConsecutiveErrors() - 1, 6))));
        Instant last = mailbox.getImapLastPolledAt();
        return last != null && last.plus(backoff).isAfter(Instant.now());
    }

    private void fetchMailbox(Mailbox mailbox) throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", mailbox.isImapUseSsl() ? "imaps" : "imap");
        Session session = Session.getInstance(props);
        Store store = session.getStore(mailbox.isImapUseSsl() ? "imaps" : "imap");
        String password = credentialsCipher.readImapPassword(mailbox);
        if (password == null) {
            password = "";
        }
        store.connect(mailbox.getImapHost(), mailbox.getImapPort() != null ? mailbox.getImapPort() : 993,
                mailbox.getImapUsername(), password);

        try {
            Folder folder = store.getFolder(mailbox.getImapFolder());
            folder.open(Folder.READ_ONLY);
            long uidValidity = ((UIDFolder) folder).getUIDValidity();
            if (mailbox.getImapUidValidity() != null && !mailbox.getImapUidValidity().equals(uidValidity)) {
                log.warn("UIDVALIDITY changed for {} — resetting cursor", mailbox.getAddress());
                mailbox.setImapLastUid(0);
            }
            mailbox.setImapUidValidity(uidValidity);

            int batchSize = properties.mail().inbound().fetchBatchSize();
            long startUid = mailbox.getImapLastUid() + 1;
            Message[] messages = ((UIDFolder) folder).getMessagesByUID(startUid, UIDFolder.LASTUID);
            int count = 0;
            long maxUid = mailbox.getImapLastUid();
            for (Message message : messages) {
                if (count >= batchSize) {
                    break;
                }
                long uid = ((UIDFolder) folder).getUID(message);
                if (uid <= mailbox.getImapLastUid()) {
                    continue;
                }
                byte[] raw = readRaw(message);
                ingestionService.stageRaw(mailbox.getOrganizationId(), mailbox.getId(),
                        MailEnums.InboundSource.IMAP, uid, raw);
                maxUid = Math.max(maxUid, uid);
                count++;
            }
            mailbox.setImapLastUid(maxUid);
            folder.close(false);
        } finally {
            store.close();
        }
    }

    private byte[] readRaw(Message message) throws Exception {
        if (message instanceof MimeMessage mime) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            mime.writeTo(out);
            return out.toByteArray();
        }
        throw ApiException.of(ErrorCode.IMAP_CONNECTION_FAILED, "Unsupported message type");
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
