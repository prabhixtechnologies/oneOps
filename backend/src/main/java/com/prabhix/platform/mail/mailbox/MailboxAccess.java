package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.mail.repository.MailboxMemberRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
import com.prabhix.platform.org.repository.TeamMemberRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Answers "may this person touch this mailbox" in one place.
 *
 * <p>The rule already existed inside {@code ThreadService} as a private method, which was fine while the
 * helpdesk was the only caller. Folders, flags, drafts and compose all need the same answer, and four
 * copies of an access check is three chances for one of them to drift the day someone adds a permission.
 */
@Component
@RequiredArgsConstructor
public class MailboxAccess {

    private final MailboxRepository mailboxRepository;
    private final MailboxMemberRepository memberRepository;
    private final MailThreadRepository threadRepository;
    private final TeamMemberRepository teamMemberRepository;

    /**
     * The ids of every mailbox this person may read, or an empty list for a holder of
     * {@code MAIL_READ_ALL} — for whom "which ids" is the wrong question and callers check the
     * permission instead.
     *
     * <p>Resolves the caller's teams first. {@code findAccessibleMailboxIds} has always taken a list
     * of team ids, and every caller passed {@code List.of()}, so a mailbox granted to a team was
     * invisible to that team's members: the grant could be written through the members table and
     * then had no effect on anything. Team grants are the reason to have teams, so an empty list
     * turned the feature off while leaving it configurable.
     */
    @Transactional(readOnly = true)
    public List<UUID> accessibleMailboxIds(PrabhixPrincipal principal) {
        UUID orgId = principal.requireOrganizationId();
        List<UUID> teamIds = teamMemberRepository.findTeamIdsByUser(orgId, principal.userId());
        return memberRepository.findAccessibleMailboxIds(orgId, principal.userId(), teamIds);
    }

    /**
     * Every mailbox this person can see. Members plus, for a holder of {@code MAIL_READ_ALL}, everything
     * in the organization — that permission is what a support lead has and it is the reason a shared
     * mailbox is usable without adding every person to every one of them.
     */
    @Transactional(readOnly = true)
    public List<Mailbox> visibleMailboxes(PrabhixPrincipal principal) {
        UUID orgId = principal.requireOrganizationId();
        if (principal.has(Permission.MAIL_READ_ALL)) {
            return mailboxRepository.findByOrganizationIdAndDeletedAtIsNullOrderByName(orgId);
        }
        List<UUID> ids = accessibleMailboxIds(principal);
        if (ids.isEmpty()) {
            return List.of();
        }
        return mailboxRepository.findByOrganizationIdAndDeletedAtIsNullOrderByName(orgId).stream()
                .filter(m -> ids.contains(m.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Mailbox requireMailbox(PrabhixPrincipal principal, UUID mailboxId) {
        UUID orgId = principal.requireOrganizationId();
        Mailbox mailbox = mailboxRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(mailboxId, orgId)
                .orElseThrow(() -> ApiException.notFound("Mailbox"));
        if (principal.has(Permission.MAIL_READ_ALL)) {
            return mailbox;
        }
        // Was existsByMailboxIdAndUserId, which only ever sees direct user grants. A person whose
        // access came from a team was refused here while visibleMailboxes listed the mailbox for
        // them, so the mailbox appeared in the sidebar and then 403'd on open.
        if (!accessibleMailboxIds(principal).contains(mailboxId)) {
            throw ApiException.forbidden("You do not have access to this mailbox");
        }
        return mailbox;
    }

    @Transactional(readOnly = true)
    public MailThread requireThread(PrabhixPrincipal principal, UUID threadId) {
        MailThread thread = threadRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(
                        threadId, principal.requireOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Thread"));
        requireMailbox(principal, thread.getMailboxId());
        return thread;
    }
}
