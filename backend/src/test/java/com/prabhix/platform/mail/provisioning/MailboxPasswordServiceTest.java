package com.prabhix.platform.mail.provisioning;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.repository.MailboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MailboxPasswordServiceTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID MAILBOX = UUID.randomUUID();

    @Mock
    MailboxRepository mailboxRepository;

    private MailboxPasswordService service;
    private Mailbox mailbox;

    @BeforeEach
    void setUp() {
        mailbox = new Mailbox();
        mailbox.setOrganizationId(ORG);
        mailbox.setAddress("support@prabhix.test");
        when(mailboxRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(MAILBOX, ORG))
                .thenReturn(Optional.of(mailbox));
        when(mailboxRepository.save(any(Mailbox.class))).thenAnswer(i -> i.getArgument(0));
        service = new MailboxPasswordService(mailboxRepository);
    }

    @Test
    @DisplayName("stores a verifiable hash and returns the plaintext once")
    void issuesAVerifiablePassword() {
        MailboxPasswordService.Issued issued = service.issue(ORG, MAILBOX);

        assertThat(issued.address()).isEqualTo("support@prabhix.test");
        assertThat(issued.password()).hasSize(20);
        assertThat(mailbox.getPasswordHash()).isNotNull().isNotEqualTo(issued.password());
        assertThat(mailbox.getPasswordUpdatedAt()).isNotNull();

        // Dovecot reads this column as BLF-CRYPT, so a hash it cannot verify is a mailbox nobody can
        // log in to — and the failure appears in Dovecot's log, not ours.
        assertThat(new BCryptPasswordEncoder().matches(issued.password(), mailbox.getPasswordHash()))
                .isTrue();
    }

    @Test
    @DisplayName("uses cost 12, so Dovecot's check is not cheap to brute-force")
    void usesTheConfiguredCostFactor() {
        service.issue(ORG, MAILBOX);

        // BCrypt carries its cost in the hash. Port 993 is open to the internet, so this is what
        // stands between a leaked database dump and every mailbox.
        assertThat(mailbox.getPasswordHash()).startsWith("$2a$12$");
    }

    /**
     * Eight iterations, not eight hundred: each one is a cost-12 BCrypt hash, so this loop is most of
     * this class's runtime. It is enough to catch the failure that matters — returning a constant, or
     * an alphabet edit that lets an ambiguous character back in.
     */
    @Test
    @DisplayName("issues a different password every time, from an unambiguous alphabet")
    void issuesDistinctUnambiguousPasswords() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            String password = service.issue(ORG, MAILBOX).password();
            // Read off a screen and typed into a phone's mail client, so I, L, O and U are excluded —
            // which also means it can never spell a word.
            assertThat(password).matches("[0-9A-HJKMNP-TV-Z]{20}");
            seen.add(password);
        }

        assertThat(seen).hasSize(8);
    }

    @Test
    @DisplayName("revoking clears the hash, so the passdb query stops matching")
    void revokeClearsTheHash() {
        service.issue(ORG, MAILBOX);

        service.revoke(ORG, MAILBOX);

        // Dovecot's password_query filters on password_hash IS NOT NULL, so a null column is the
        // revocation itself rather than a flag that some other query might not check.
        assertThat(mailbox.getPasswordHash()).isNull();
        assertThat(mailbox.getPasswordUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("revoking a mailbox that never had a password is not an error")
    void revokeIsIdempotent() {
        service.revoke(ORG, MAILBOX);

        assertThat(mailbox.getPasswordHash()).isNull();
    }

    @Test
    @DisplayName("a mailbox in another organization is not found")
    void refusesAnotherOrganizationsMailbox() {
        UUID other = UUID.randomUUID();
        when(mailboxRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(MAILBOX, other))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(other, MAILBOX)).isInstanceOf(ApiException.class);
    }
}
