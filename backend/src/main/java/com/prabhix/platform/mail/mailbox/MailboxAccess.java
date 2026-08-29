package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.mail.repository.MailboxMemberRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
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
        List<UUID> ids = memberRepository.findAccessibleMailboxIds(orgId, principal.userId(), List.of());
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
        if (!memberRepository.existsByMailboxIdAndUserId(mailboxId, principal.userId())) {
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
