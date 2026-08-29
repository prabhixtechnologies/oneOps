package com.prabhix.platform.mail.provisioning;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.repository.MailboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Issues the password a mail client uses to reach a mailbox over IMAP or SMTP.
 *
 * <p>This is what lets Dovecot authenticate from Postgres instead of from
 * {@code dovecot/bootstrap.passwd} — a file in the repository that had to be edited and the
 * container restarted to add a mailbox, and whose hashes were therefore visible to anyone with
 * repository access.
 *
 * <p>Separate from the console password on purpose. A mail client stores this on the device in a
 * form it can replay on every poll, and a shared mailbox has several members and no single owner, so
 * revoking it must not touch anybody's ability to sign in. Nothing here can read a password back:
 * the plaintext exists only in the response that creates it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailboxPasswordService {

    /**
     * Strength 12, matching {@code prabhix.security.password.bcrypt-strength}, and the value Dovecot
     * reads as BLF-CRYPT. BCrypt hashes carry their own cost factor, so raising this later does not
     * invalidate passwords already issued.
     */
    private static final int BCRYPT_STRENGTH = 12;

    /**
     * 20 characters from a 32-symbol alphabet is 100 bits. Generous, but this password is never
     * typed from memory — it is pasted into a mail client once — and it is exposed to the internet
     * on port 993, where a weak one is guessed rather than reasoned about.
     */
    private static final int PASSWORD_LENGTH = 20;

    /**
     * Crockford's base32 without I, L, O and U: no character pair that a person transcribing between
     * a browser and a phone can confuse, and no vowels, so no unintended words.
     */
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private final MailboxRepository mailboxRepository;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    /**
     * Generates a new password, stores only its hash, and returns the plaintext once.
     *
     * <p>Rotating replaces the old password immediately, which signs out every mail client
     * configured with it. That is the point — it is the revocation path for a lost phone — but it is
     * also why the console has to say so before calling this.
     */
    @Transactional
    public Issued issue(UUID organizationId, UUID mailboxId) {
        Mailbox mailbox = require(organizationId, mailboxId);
        String password = generate();
        mailbox.setPasswordHash(encoder.encode(password));
        mailbox.setPasswordUpdatedAt(Instant.now());
        mailboxRepository.save(mailbox);

        // Deliberately without the password, and without a hint of it. Application logs are shipped
        // off the box and kept for 90 days.
        log.info("Issued a mail password for mailbox {} ({})", mailbox.getAddress(), mailboxId);
        return new Issued(mailbox.getAddress(), password, mailbox.getPasswordUpdatedAt());
    }

    /**
     * Removes the password, leaving the mailbox reachable by the platform but not by a mail client.
     *
     * <p>Dovecot's passdb query filters on {@code password_hash IS NOT NULL}, so this is a real
     * revocation rather than a flag: there is nothing left for a client to authenticate against.
     */
    @Transactional
    public void revoke(UUID organizationId, UUID mailboxId) {
        Mailbox mailbox = require(organizationId, mailboxId);
        if (mailbox.getPasswordHash() == null) {
            return;
        }
        mailbox.setPasswordHash(null);
        mailbox.setPasswordUpdatedAt(null);
        mailboxRepository.save(mailbox);
        log.info("Revoked the mail password for mailbox {} ({})", mailbox.getAddress(), mailboxId);
    }

    private Mailbox require(UUID organizationId, UUID mailboxId) {
        return mailboxRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(mailboxId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Mailbox"));
    }

    private String generate() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            // nextInt(bound) rather than nextInt() % length: the modulus of a uniform int over a
            // non-power-of-two alphabet is not uniform, and 32 only looks safe until someone edits
            // the alphabet.
            password.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return password.toString();
    }

    /**
     * @param password the only time it exists in readable form; the caller has to show it and cannot
     *     retrieve it again
     */
    public record Issued(String address, String password, Instant issuedAt) {
    }
}
