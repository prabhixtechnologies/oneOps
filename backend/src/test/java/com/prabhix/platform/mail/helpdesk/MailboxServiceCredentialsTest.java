package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.spi.EntitlementGate;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.dto.MailboxDtos;
import com.prabhix.platform.mail.provisioning.MailboxCredentialsCipher;
import com.prabhix.platform.mail.repository.MailRoutingRuleRepository;
import com.prabhix.platform.mail.repository.MailboxMemberRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailboxServiceCredentialsTest {

    @Mock private MailboxRepository mailboxRepository;
    @Mock private MailboxMemberRepository memberRepository;
    @Mock private MailRoutingRuleRepository routingRuleRepository;
    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private com.prabhix.platform.org.repository.TeamRepository teamRepository;
    @Mock private com.prabhix.platform.mail.repository.MailDomainRepository mailDomainRepository;
    @Mock private EntitlementGate entitlements;

    private MailboxCredentialsCipher cipher;
    private MailboxService service;

    private final UUID orgId = UUID.randomUUID();
    private final PrabhixProperties properties = TestProperties.defaults();

    @BeforeEach
    void setUp() {
        cipher = new MailboxCredentialsCipher(properties);
        service = new MailboxService(
                mailboxRepository, memberRepository, routingRuleRepository,
                membershipRepository, teamRepository, mailDomainRepository,
                properties, entitlements, cipher);
    }

    @Test
    void createEncryptsProvidedCredentials() {
        when(mailboxRepository.countByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(0L);
        when(mailboxRepository.findByAddressIgnoreCaseAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        when(mailboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(orgId, new MailboxDtos.CreateMailboxRequest(
                "support@example.com", "Support", null, null, "imap-secret", "smtp-secret", null));

        ArgumentCaptor<Mailbox> saved = ArgumentCaptor.forClass(Mailbox.class);
        verify(mailboxRepository).save(saved.capture());
        Mailbox mailbox = saved.getValue();
        assertTrue(mailbox.getImapPasswordEnc().startsWith("v1:"));
        assertTrue(mailbox.getSmtpPasswordEnc().startsWith("v1:"));
        assertEquals("imap-secret", cipher.readImapPassword(mailbox));
        assertEquals("smtp-secret", cipher.readSmtpPassword(mailbox));
    }

    @Test
    void updateOnlyChangesPasswordsWhenProvided() {
        Mailbox mailbox = new Mailbox();
        mailbox.setId(UUID.randomUUID());
        mailbox.setOrganizationId(orgId);
        mailbox.setAddress("support@example.com");
        mailbox.setName("Support");
        cipher.storeImapPassword(mailbox, "old-imap");

        when(mailboxRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(mailbox.getId(), orgId))
                .thenReturn(Optional.of(mailbox));
        when(memberRepository.findByMailboxId(mailbox.getId())).thenReturn(java.util.List.of());
        when(routingRuleRepository.findByOrganizationIdAndMailboxIdOrderByPriorityAsc(orgId, mailbox.getId()))
                .thenReturn(java.util.List.of());
        when(mailboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(orgId, mailbox.getId(),
                new MailboxDtos.UpdateMailboxRequest(null, null, null, null, null, null, "new-imap", null));

        assertEquals("new-imap", cipher.readImapPassword(mailbox));
    }
}
