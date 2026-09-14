package com.prabhix.platform.mail.web;

import com.prabhix.platform.mail.mailbox.ComposeService;
import com.prabhix.platform.mail.mailbox.MailAliasService;
import com.prabhix.platform.mail.mailbox.MailDraftService;
import com.prabhix.platform.mail.mailbox.MailFlagService;
import com.prabhix.platform.mail.mailbox.MailFolderService;
import com.prabhix.platform.mail.mailbox.MailboxAccess;
import com.prabhix.platform.mail.mailbox.MailboxDtos;
import com.prabhix.platform.mail.mailbox.MailboxListService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The mailbox API, as a mail client uses it: folders, flags, drafts, compose and addresses.
 *
 * <p>Separate from {@link ThreadController}, which is the helpdesk's view of the same threads. Both are
 * legitimate and they want different things — one assigns and measures, the other files and stars — and
 * folding them into one controller would give every screen every field it does not need.
 */
@RestController
@RequestMapping("/api/v1/mailbox")
@RequiredArgsConstructor
public class MailboxViewController {

    private final MailboxListService list;
    private final MailFolderService folders;
    private final MailFlagService flags;
    private final MailDraftService drafts;
    private final ComposeService compose;
    private final MailAliasService aliases;

    // -----------------------------------------------------------------------------------------------
    // Sidebar and reading
    // -----------------------------------------------------------------------------------------------

    @GetMapping
    @PreAuthorize(Authorize.MAIL_READ)
    public List<MailboxDtos.MailboxSummaryView> sidebar(@CurrentUser PrabhixPrincipal principal,
                                                        @RequestParam(required = false) String mode) {
        return list.sidebar(principal, MailboxAccess.Visibility.fromQuery(mode));
    }

    @GetMapping("/folders/{folderId}/threads")
    @PreAuthorize(Authorize.MAIL_READ)
    public List<MailboxDtos.MailThreadView> threadsIn(@CurrentUser PrabhixPrincipal principal,
                                                      @PathVariable UUID folderId,
                                                      @RequestParam(required = false) Integer limit,
                                                      @RequestParam(required = false) Integer offset) {
        return list.threadsIn(principal, folderId, limit, offset);
    }

    @GetMapping("/threads/{threadId}")
    @PreAuthorize(Authorize.MAIL_READ)
    public MailboxDtos.MailThreadView thread(@CurrentUser PrabhixPrincipal principal,
                                             @PathVariable UUID threadId) {
        return list.thread(principal, threadId);
    }

    @GetMapping("/threads/{threadId}/messages")
    @PreAuthorize(Authorize.MAIL_READ)
    public List<MailboxDtos.MessageView> messages(@CurrentUser PrabhixPrincipal principal,
                                                 @PathVariable UUID threadId) {
        return list.messages(principal, threadId);
    }

    @GetMapping("/starred")
    @PreAuthorize(Authorize.MAIL_READ)
    public List<MailboxDtos.MailThreadView> starred(@CurrentUser PrabhixPrincipal principal) {
        return flags.starred(principal);
    }

    // -----------------------------------------------------------------------------------------------
    // Folders
    // -----------------------------------------------------------------------------------------------

    @GetMapping("/{mailboxId}/folders")
    @PreAuthorize(Authorize.MAIL_READ)
    public List<MailboxDtos.FolderView> folders(@CurrentUser PrabhixPrincipal principal,
                                                @PathVariable UUID mailboxId) {
        return folders.list(principal, mailboxId);
    }

    @PostMapping("/{mailboxId}/folders")
    @PreAuthorize(Authorize.MAIL_READ)
    public MailboxDtos.FolderView createFolder(@CurrentUser PrabhixPrincipal principal,
                                               @PathVariable UUID mailboxId,
                                               @Valid @RequestBody MailboxDtos.SaveFolderRequest request) {
        return folders.create(principal, mailboxId, request);
    }

    @PatchMapping("/folders/{folderId}")
    @PreAuthorize(Authorize.MAIL_READ)
    public MailboxDtos.FolderView updateFolder(@CurrentUser PrabhixPrincipal principal,
                                               @PathVariable UUID folderId,
                                               @Valid @RequestBody MailboxDtos.SaveFolderRequest request) {
        return folders.rename(principal, folderId, request);
    }

    @DeleteMapping("/folders/{folderId}")
    @PreAuthorize(Authorize.MAIL_READ)
    public void deleteFolder(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID folderId) {
        folders.delete(principal, folderId);
    }

    @PostMapping("/folders/{folderId}/move")
    @PreAuthorize(Authorize.MAIL_THREAD_UPDATE)
    public int move(@CurrentUser PrabhixPrincipal principal,
                    @PathVariable UUID folderId,
                    @Valid @RequestBody MailboxDtos.MoveRequest request) {
        return folders.move(principal, folderId, request.threadIds());
    }

    // -----------------------------------------------------------------------------------------------
    // Flags
    // -----------------------------------------------------------------------------------------------

    @PatchMapping("/threads/{threadId}/flags")
    @PreAuthorize(Authorize.MAIL_READ)
    public MailboxDtos.MailThreadView flag(@CurrentUser PrabhixPrincipal principal,
                                           @PathVariable UUID threadId,
                                           @Valid @RequestBody MailboxDtos.FlagRequest request) {
        return flags.apply(principal, threadId, request);
    }

    @PostMapping("/threads/flags")
    @PreAuthorize(Authorize.MAIL_READ)
    public int flagBulk(@CurrentUser PrabhixPrincipal principal,
                        @Valid @RequestBody MailboxDtos.BulkFlagRequest request) {
        return flags.applyBulk(principal, request);
    }

    // -----------------------------------------------------------------------------------------------
    // Drafts and compose
    // -----------------------------------------------------------------------------------------------

    @GetMapping("/drafts")
    @PreAuthorize(Authorize.MAIL_READ)
    public List<MailboxDtos.DraftView> myDrafts(@CurrentUser PrabhixPrincipal principal) {
        return drafts.mine(principal);
    }

    @GetMapping("/drafts/{draftId}")
    @PreAuthorize(Authorize.MAIL_READ)
    public MailboxDtos.DraftView draft(@CurrentUser PrabhixPrincipal principal,
                                       @PathVariable UUID draftId) {
        return drafts.get(principal, draftId);
    }

    @PutMapping("/drafts")
    @PreAuthorize(Authorize.MAIL_READ)
    public MailboxDtos.DraftView saveDraft(@CurrentUser PrabhixPrincipal principal,
                                           @Valid @RequestBody MailboxDtos.SaveDraftRequest request) {
        return drafts.save(principal, request);
    }

    @DeleteMapping("/drafts/{draftId}")
    @PreAuthorize(Authorize.MAIL_READ)
    public void discardDraft(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID draftId) {
        drafts.discard(principal, draftId);
    }

    @PostMapping("/compose")
    @PreAuthorize(Authorize.MAIL_SEND)
    public MailboxDtos.ComposeResponse compose(@CurrentUser PrabhixPrincipal principal,
                                               @Valid @RequestBody MailboxDtos.ComposeRequest request) {
        return compose.send(principal, request);
    }

    // -----------------------------------------------------------------------------------------------
    // Addresses
    // -----------------------------------------------------------------------------------------------

    @GetMapping("/{mailboxId}/aliases")
    @PreAuthorize(Authorize.MAIL_READ)
    public List<MailboxDtos.AliasView> aliases(@CurrentUser PrabhixPrincipal principal,
                                               @PathVariable UUID mailboxId) {
        return aliases.list(principal, mailboxId);
    }

    @PostMapping("/{mailboxId}/aliases")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public MailboxDtos.AliasView createAlias(@CurrentUser PrabhixPrincipal principal,
                                             @PathVariable UUID mailboxId,
                                             @Valid @RequestBody MailboxDtos.CreateAliasRequest request) {
        return aliases.create(principal, mailboxId, request);
    }

    @DeleteMapping("/{mailboxId}/aliases/{aliasId}")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public void deleteAlias(@CurrentUser PrabhixPrincipal principal,
                            @PathVariable UUID mailboxId,
                            @PathVariable UUID aliasId) {
        aliases.delete(principal, mailboxId, aliasId);
    }
}
