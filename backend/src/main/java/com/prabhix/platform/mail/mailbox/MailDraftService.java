package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.MailThreadDraft;
import com.prabhix.platform.mail.repository.MailThreadDraftRepository;
import com.prabhix.platform.common.util.Json;
import com.prabhix.platform.security.PrabhixPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Drafts that survive a closed tab.
 *
 * <p>The table has existed since V5 and nothing ever wrote to it, so a half-written message lived in
 * browser state and died with it. Two shapes are stored here: a reply draft, which belongs to a thread
 * and of which there is one per author, and a new message, which belongs to nothing yet and of which
 * there can be any number.
 *
 * <p>A draft is private to its author even inside a shared mailbox. Colleagues can read the mail; an
 * unfinished sentence is not mail yet.
 */
@Service
@RequiredArgsConstructor
public class MailDraftService {

    private static final int MAX_DRAFTS = 200;

    private final MailThreadDraftRepository draftRepository;
    private final MailboxAccess access;

    @Transactional(readOnly = true)
    public List<MailboxDtos.DraftView> mine(PrabhixPrincipal principal) {
        return draftRepository.findMine(principal.requireOrganizationId(), principal.userId())
                .stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public MailboxDtos.DraftView get(PrabhixPrincipal principal, UUID draftId) {
        return toView(require(principal, draftId));
    }

    /**
     * Saves a draft, replacing the author's existing one for the same thread.
     *
     * <p>The upsert-by-thread is what makes autosave safe to call on a timer: a client that saves every
     * few seconds while somebody types produces one row, not one row per keystroke.
     */
    @Transactional
    public MailboxDtos.DraftView save(PrabhixPrincipal principal, MailboxDtos.SaveDraftRequest request) {
        UUID orgId = principal.requireOrganizationId();
        UUID mailboxId = request.mailboxId();

        if (request.threadId() != null) {
            mailboxId = access.requireThread(principal, request.threadId()).getMailboxId();
        } else {
            if (mailboxId == null) {
                throw ApiException.invalidState("A new message needs a mailbox to be sent from");
            }
            access.requireMailbox(principal, mailboxId);
        }

        MailThreadDraft draft = request.threadId() != null
                ? draftRepository.findByThreadIdAndAuthorUserId(request.threadId(), principal.userId())
                .orElseGet(MailThreadDraft::new)
                : new MailThreadDraft();

        if (draft.getId() == null) {
            long count = draftRepository.findMine(orgId, principal.userId()).size();
            if (count >= MAX_DRAFTS) {
                throw ApiException.of(com.prabhix.platform.common.error.ErrorCode.LIMIT_EXCEEDED,
                        "You have too many unsent drafts. Send or discard some first.");
            }
            draft.setOrganizationId(orgId);
            draft.setAuthorUserId(principal.userId());
            draft.setThreadId(request.threadId());
        }

        draft.setMailboxId(mailboxId);
        draft.setReplyMode(request.replyMode() != null ? request.replyMode() : MailEnums.ReplyMode.REPLY);
        draft.setToAddresses(Json.toJson(clean(request.to())));
        draft.setCcAddresses(Json.toJson(clean(request.cc())));
        draft.setBccAddresses(Json.toJson(clean(request.bcc())));
        draft.setSubject(request.subject());
        draft.setBodyHtml(request.bodyHtml());
        draft.setAttachmentIds(Json.toJson(
                request.attachmentIds() != null
                        ? request.attachmentIds().stream().map(UUID::toString).toList()
                        : List.of()));
        return toView(draftRepository.save(draft));
    }

    @Transactional
    public void discard(PrabhixPrincipal principal, UUID draftId) {
        draftRepository.delete(require(principal, draftId));
    }

    /** Removes a draft once its message is on its way. No-op if the id is unknown or not the caller's. */
    @Transactional
    public void discardQuietly(PrabhixPrincipal principal, UUID draftId) {
        if (draftId == null) {
            return;
        }
        draftRepository.findByIdAndOrganizationIdAndAuthorUserId(
                        draftId, principal.requireOrganizationId(), principal.userId())
                .ifPresent(draftRepository::delete);
    }

    private MailThreadDraft require(PrabhixPrincipal principal, UUID draftId) {
        return draftRepository.findByIdAndOrganizationIdAndAuthorUserId(
                        draftId, principal.requireOrganizationId(), principal.userId())
                .orElseThrow(() -> ApiException.notFound("Draft"));
    }

    private List<String> clean(List<String> addresses) {
        if (addresses == null) {
            return List.of();
        }
        return addresses.stream()
                .filter(a -> a != null && !a.isBlank())
                .map(a -> a.trim().toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
    }

    private MailboxDtos.DraftView toView(MailThreadDraft d) {
        return new MailboxDtos.DraftView(
                d.getId(), d.getThreadId(), d.getMailboxId(), d.getReplyMode(),
                Json.parseStringList(d.getToAddresses()),
                Json.parseStringList(d.getCcAddresses()),
                Json.parseStringList(d.getBccAddresses()),
                d.getSubject(), d.getBodyHtml(),
                Json.parseStringList(d.getAttachmentIds()).stream().map(UUID::fromString).toList(),
                d.getUpdatedAt());
    }
}
