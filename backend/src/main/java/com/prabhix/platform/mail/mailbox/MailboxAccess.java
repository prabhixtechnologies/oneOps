package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.mail.repository.MailboxMemberRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import com.prabhix.platform.org.repository.TeamMemberRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public enum Visibility {
        /** Owner or member boxes only — Mailroom's default "My mail". */
        MINE,
        /** Every mailbox in the organization. Requires {@code MAIL_READ_ALL}. */
        COMPANY;

        public static Visibility fromQuery(String mode) {
            if (mode == null || mode.isBlank() || "mine".equalsIgnoreCase(mode)) {
                return MINE;
            }
            if ("company".equalsIgnoreCase(mode) || "organization".equalsIgnoreCase(mode)) {
                return COMPANY;
            }
            throw ApiException.of(com.prabhix.platform.common.error.ErrorCode.VALIDATION_FAILED,
                    "Unknown mailbox mode");
        }
    }

    private final MailboxRepository mailboxRepository;
    private final MailboxMemberRepository memberRepository;
    private final MailThreadRepository threadRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final StructuredEventLogger eventLogger;

    /**
     * The ids of every mailbox this person may read as a member or owner.
     *
     * <p>{@code MAIL_READ_ALL} is a separate question: it lets Company mail list the rest of the
     * organization, and it is not mixed into this list. Callers that used to treat an empty return as
     * "see everything" were collapsing two modes into one sidebar.
     *
     * <p>Resolves the caller's teams first. {@code findAccessibleMailboxIds} has always taken a list
     * of team ids, and every caller passed {@code List.of()}, so a mailbox granted to a team was
     * invisible to that team's members.
     */
    @Transactional(readOnly = true)
    public List<UUID> accessibleMailboxIds(PrabhixPrincipal principal) {
        UUID orgId = principal.requireOrganizationId();
        List<UUID> teamIds = teamMemberRepository.findTeamIdsByUser(orgId, principal.userId());
        Set<UUID> ids = new LinkedHashSet<>(
                memberRepository.findAccessibleMailboxIds(orgId, principal.userId(), teamIds));
        for (Mailbox owned : mailboxRepository
                .findByOrganizationIdAndOwnerUserIdAndDeletedAtIsNull(orgId, principal.userId())) {
            ids.add(owned.getId());
        }
        return new ArrayList<>(ids);
    }

    /**
     * Every mailbox this person can see in <em>My mail</em>: owner and member grants only.
     *
     * <p>Holders of {@code MAIL_READ_ALL} still get this list here. Company mail is
     * {@link #visibleMailboxes(PrabhixPrincipal, Visibility) visibleMailboxes} with
     * {@link Visibility#COMPANY}, not a silent expansion of the default sidebar.
     */
    @Transactional(readOnly = true)
    public List<Mailbox> visibleMailboxes(PrabhixPrincipal principal) {
        return visibleMailboxes(principal, Visibility.MINE);
    }

    @Transactional(readOnly = true)
    public List<Mailbox> visibleMailboxes(PrabhixPrincipal principal, Visibility visibility) {
        UUID orgId = principal.requireOrganizationId();
        if (visibility == Visibility.COMPANY) {
            if (!principal.has(Permission.MAIL_READ_ALL)) {
                throw ApiException.forbidden("Company mail requires permission to read every mailbox");
            }
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
        if (principal.has(Permission.MAIL_READ_ALL) || isOwnerOrMember(principal, mailbox)) {
            return mailbox;
        }
        throw ApiException.forbidden("You do not have access to this mailbox");
    }

    @Transactional(readOnly = true)
    public MailThread requireThread(PrabhixPrincipal principal, UUID threadId) {
        MailThread thread = threadRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(
                        threadId, principal.requireOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Thread"));
        requireMailbox(principal, thread.getMailboxId());
        return thread;
    }

    /**
     * Whether this person owns the mailbox or holds a member/team grant — not whether
     * {@code MAIL_READ_ALL} would let them in.
     */
    @Transactional(readOnly = true)
    public boolean isOwnerOrMember(PrabhixPrincipal principal, Mailbox mailbox) {
        if (principal.userId().equals(mailbox.getOwnerUserId())) {
            return true;
        }
        return accessibleMailboxIds(principal).contains(mailbox.getId());
    }

    /**
     * Records a Company-mail (or helpdesk) read of a mailbox the caller does not belong to.
     *
     * <p>Skipped when they own it or have a member grant: that is ordinary mail, not oversight.
     */
    public void recordAdminRead(PrabhixPrincipal principal, Mailbox mailbox, UUID threadId) {
        if (mailbox == null || isOwnerOrMember(principal, mailbox)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mailboxId", mailbox.getId());
        payload.put("address", mailbox.getAddress());
        if (mailbox.getOwnerUserId() != null) {
            payload.put("ownerUserId", mailbox.getOwnerUserId());
        }
        if (threadId != null) {
            payload.put("threadId", threadId);
        }
        eventLogger.log(LogEventCode.MAIL_ADMIN_READ, payload);
    }
}
