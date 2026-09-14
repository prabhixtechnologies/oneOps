package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.spi.EntitlementGate;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailDomain;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.domain.MailboxMember;
import com.prabhix.platform.mail.dto.MailboxDtos;
import com.prabhix.platform.mail.provisioning.MailboxCredentialsCipher;
import com.prabhix.platform.mail.repository.MailDomainRepository;
import com.prabhix.platform.mail.repository.MailRoutingRuleRepository;
import com.prabhix.platform.mail.repository.MailboxMemberRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.repository.TeamRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailboxServicePersonalCreateTest {

    @Mock private MailboxRepository mailboxRepository;
    @Mock private MailboxMemberRepository memberRepository;
    @Mock private MailRoutingRuleRepository routingRuleRepository;
    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private MailDomainRepository mailDomainRepository;
    @Mock private EntitlementGate entitlements;

    private MailboxService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final PrabhixProperties properties = TestProperties.defaults();

    @BeforeEach
    void setUp() {
        service = new MailboxService(
                mailboxRepository, memberRepository, routingRuleRepository,
                membershipRepository, teamRepository, mailDomainRepository,
                properties, entitlements, new MailboxCredentialsCipher(properties));
    }

    @Test
    void createPersonalSetsOwnerAndMember() {
        UUID mailboxId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();

        when(mailboxRepository.countByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(0L);
        when(mailboxRepository.findByAddressIgnoreCaseAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        when(mailboxRepository.save(any())).thenAnswer(inv -> {
            Mailbox mailbox = inv.getArgument(0);
            mailbox.setId(mailboxId);
            return mailbox;
        });

        OrganizationMembership membership = new OrganizationMembership();
        membership.setUserId(ownerId);
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, ownerId))
                .thenReturn(Optional.of(membership));

        MailDomain domain = new MailDomain();
        domain.setId(domainId);
        domain.setOrganizationId(orgId);
        domain.setDomain("example.com");
        domain.setStatus(MailEnums.DomainStatus.VERIFIED);
        when(mailDomainRepository.findByDomainIgnoreCaseAndDeletedAtIsNull("example.com"))
                .thenReturn(Optional.of(domain));
        when(memberRepository.existsByMailboxIdAndUserId(mailboxId, ownerId)).thenReturn(false);
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(orgId, new MailboxDtos.CreateMailboxRequest(
                "alice@example.com", "Alice", MailEnums.MailboxKind.PERSONAL,
                null, null, null, ownerId));

        ArgumentCaptor<Mailbox> savedBox = ArgumentCaptor.forClass(Mailbox.class);
        verify(mailboxRepository).save(savedBox.capture());
        Mailbox mailbox = savedBox.getValue();
        assertEquals(MailEnums.MailboxKind.PERSONAL, mailbox.getKind());
        assertEquals(ownerId, mailbox.getOwnerUserId());
        assertEquals(domainId, mailbox.getMailDomainId());
        assertEquals("alice@example.com", mailbox.getAddress());

        ArgumentCaptor<MailboxMember> savedMember = ArgumentCaptor.forClass(MailboxMember.class);
        verify(memberRepository).save(savedMember.capture());
        MailboxMember member = savedMember.getValue();
        assertEquals(mailboxId, member.getMailboxId());
        assertEquals(ownerId, member.getUserId());
        assertEquals(orgId, member.getOrganizationId());
        assertEquals(MailEnums.MemberAccessLevel.LEAD, member.getAccessLevel());
    }

    @Test
    void createPersonalWithoutOwnerIsRejected() {
        when(mailboxRepository.countByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(0L);

        ApiException ex = assertThrows(ApiException.class, () -> service.create(orgId,
                new MailboxDtos.CreateMailboxRequest(
                        "alice@example.com", "Alice", MailEnums.MailboxKind.PERSONAL,
                        null, null, null, null)));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
    }

    @Test
    void createPersonalOnUnverifiedDomainIsRejected() {
        when(mailboxRepository.countByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(0L);
        when(mailboxRepository.findByAddressIgnoreCaseAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        OrganizationMembership membership = new OrganizationMembership();
        membership.setUserId(ownerId);
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, ownerId))
                .thenReturn(Optional.of(membership));

        MailDomain domain = new MailDomain();
        domain.setId(UUID.randomUUID());
        domain.setOrganizationId(orgId);
        domain.setDomain("example.com");
        domain.setStatus(MailEnums.DomainStatus.PENDING);
        when(mailDomainRepository.findByDomainIgnoreCaseAndDeletedAtIsNull("example.com"))
                .thenReturn(Optional.of(domain));

        ApiException ex = assertThrows(ApiException.class, () -> service.create(orgId,
                new MailboxDtos.CreateMailboxRequest(
                        "alice@example.com", "Alice", MailEnums.MailboxKind.PERSONAL,
                        null, null, null, ownerId)));
        assertEquals(ErrorCode.MAIL_DOMAIN_NOT_VERIFIED, ex.getCode());
    }
}
