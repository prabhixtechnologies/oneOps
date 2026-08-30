package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.mail.repository.MailboxMemberRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
import com.prabhix.platform.org.repository.TeamMemberRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * A mailbox granted to a team has to be usable by that team's members.
 *
 * <p>The grant could always be written, and then had no effect: every caller passed an empty team
 * list, so the mailbox appeared in the sidebar for a lead with {@code MAIL_READ_ALL} and 403'd for
 * the people it was actually granted to.
 */
@ExtendWith(MockitoExtension.class)
class MailboxAccessTeamTest {

    @Mock private MailboxRepository mailboxRepository;
    @Mock private MailboxMemberRepository memberRepository;
    @Mock private MailThreadRepository threadRepository;
    @Mock private TeamMemberRepository teamMemberRepository;

    @InjectMocks private MailboxAccess mailboxAccess;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final UUID mailboxId = UUID.randomUUID();

    @Test
    void teamGrantsAreResolvedIntoAccessibleIds() {
        when(teamMemberRepository.findTeamIdsByUser(orgId, userId)).thenReturn(List.of(teamId));
        when(memberRepository.findAccessibleMailboxIds(orgId, userId, List.of(teamId)))
                .thenReturn(List.of(mailboxId));

        assertEquals(List.of(mailboxId), mailboxAccess.accessibleMailboxIds(principal()));
    }

    @Test
    void requireMailboxAcceptsAMailboxReachedThroughATeam() {
        Mailbox mailbox = new Mailbox();
        mailbox.setId(mailboxId);
        mailbox.setOrganizationId(orgId);
        when(mailboxRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(mailboxId, orgId))
                .thenReturn(Optional.of(mailbox));
        when(teamMemberRepository.findTeamIdsByUser(orgId, userId)).thenReturn(List.of(teamId));
        when(memberRepository.findAccessibleMailboxIds(orgId, userId, List.of(teamId)))
                .thenReturn(List.of(mailboxId));

        assertSame(mailbox, mailboxAccess.requireMailbox(principal(), mailboxId));
    }

    @Test
    void requireMailboxStillRefusesAMailboxNobodyGranted() {
        Mailbox mailbox = new Mailbox();
        mailbox.setId(mailboxId);
        mailbox.setOrganizationId(orgId);
        when(mailboxRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(mailboxId, orgId))
                .thenReturn(Optional.of(mailbox));
        when(teamMemberRepository.findTeamIdsByUser(orgId, userId)).thenReturn(List.of());
        when(memberRepository.findAccessibleMailboxIds(orgId, userId, List.of()))
                .thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class,
                () -> mailboxAccess.requireMailbox(principal(), mailboxId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getCode());
    }

    private PrabhixPrincipal principal() {
        return new PrabhixPrincipal(userId, "agent@acme.com", "Agent", orgId,
                Set.of(Permission.MAIL_READ), UUID.randomUUID(), false);
    }
}
