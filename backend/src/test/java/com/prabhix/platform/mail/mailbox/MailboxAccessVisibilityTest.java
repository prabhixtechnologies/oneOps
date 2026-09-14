package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.mail.repository.MailboxMemberRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import com.prabhix.platform.org.repository.TeamMemberRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailboxAccessVisibilityTest {

    @Mock private MailboxRepository mailboxRepository;
    @Mock private MailboxMemberRepository memberRepository;
    @Mock private MailThreadRepository threadRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private StructuredEventLogger eventLogger;

    @InjectMocks private MailboxAccess mailboxAccess;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID mineId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    @Test
    void mineModeListsOnlyMemberBoxesEvenWithReadAll() {
        Mailbox mine = mailbox(mineId);
        Mailbox other = mailbox(otherId);
        when(teamMemberRepository.findTeamIdsByUser(orgId, userId)).thenReturn(List.of());
        when(memberRepository.findAccessibleMailboxIds(orgId, userId, List.of()))
                .thenReturn(List.of(mineId));
        when(mailboxRepository.findByOrganizationIdAndOwnerUserIdAndDeletedAtIsNull(orgId, userId))
                .thenReturn(List.of());
        when(mailboxRepository.findByOrganizationIdAndDeletedAtIsNullOrderByName(orgId))
                .thenReturn(List.of(mine, other));

        assertEquals(List.of(mine), mailboxAccess.visibleMailboxes(readAllPrincipal()));
    }

    @Test
    void companyModeListsEveryMailboxWhenCallerHasReadAll() {
        Mailbox mine = mailbox(mineId);
        Mailbox other = mailbox(otherId);
        when(mailboxRepository.findByOrganizationIdAndDeletedAtIsNullOrderByName(orgId))
                .thenReturn(List.of(mine, other));

        assertEquals(List.of(mine, other),
                mailboxAccess.visibleMailboxes(readAllPrincipal(), MailboxAccess.Visibility.COMPANY));
    }

    @Test
    void companyModeIsForbiddenWithoutReadAll() {
        ApiException ex = assertThrows(ApiException.class,
                () -> mailboxAccess.visibleMailboxes(memberPrincipal(), MailboxAccess.Visibility.COMPANY));
        assertEquals(ErrorCode.FORBIDDEN, ex.getCode());
    }

    @Test
    void adminReadEmitsWhenReaderIsNotAMember() {
        Mailbox other = mailbox(otherId);
        when(teamMemberRepository.findTeamIdsByUser(orgId, userId)).thenReturn(List.of());
        when(memberRepository.findAccessibleMailboxIds(orgId, userId, List.of()))
                .thenReturn(List.of());
        when(mailboxRepository.findByOrganizationIdAndOwnerUserIdAndDeletedAtIsNull(orgId, userId))
                .thenReturn(List.of());

        UUID threadId = UUID.randomUUID();
        mailboxAccess.recordAdminRead(readAllPrincipal(), other, threadId);

        verify(eventLogger).log(eq(LogEventCode.MAIL_ADMIN_READ), eq(Map.of(
                "mailboxId", otherId,
                "address", other.getAddress(),
                "threadId", threadId)));
    }

    @Test
    void adminReadIsSilentForAnOwner() {
        Mailbox mine = mailbox(mineId);
        mine.setOwnerUserId(userId);

        mailboxAccess.recordAdminRead(readAllPrincipal(), mine, UUID.randomUUID());

        verify(eventLogger, never()).log(eq(LogEventCode.MAIL_ADMIN_READ), org.mockito.ArgumentMatchers.any());
    }

    private Mailbox mailbox(UUID id) {
        Mailbox mailbox = new Mailbox();
        mailbox.setId(id);
        mailbox.setOrganizationId(orgId);
        mailbox.setAddress(id.equals(mineId) ? "me@acme.com" : "other@acme.com");
        return mailbox;
    }

    private PrabhixPrincipal readAllPrincipal() {
        return new PrabhixPrincipal(userId, "lead@acme.com", "Lead", orgId,
                Set.of(Permission.MAIL_READ, Permission.MAIL_READ_ALL), UUID.randomUUID(), false);
    }

    private PrabhixPrincipal memberPrincipal() {
        return new PrabhixPrincipal(userId, "agent@acme.com", "Agent", orgId,
                Set.of(Permission.MAIL_READ), UUID.randomUUID(), false);
    }
}
