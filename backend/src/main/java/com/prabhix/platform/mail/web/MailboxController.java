package com.prabhix.platform.mail.web;

import com.prabhix.platform.mail.dto.MailboxDtos;
import com.prabhix.platform.mail.helpdesk.MailboxService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mail/mailboxes")
@RequiredArgsConstructor
public class MailboxController {

    private final MailboxService mailboxService;

    @GetMapping
    @PreAuthorize(Authorize.MAIL_MAILBOX_READ)
    public List<MailboxDtos.MailboxResponse> list(@CurrentUser PrabhixPrincipal principal) {
        return mailboxService.list(principal.requireOrganizationId());
    }

    @GetMapping("/{id}")
    @PreAuthorize(Authorize.MAIL_MAILBOX_READ)
    public MailboxDtos.MailboxDetailResponse get(@CurrentUser PrabhixPrincipal principal,
                                                   @PathVariable UUID id) {
        return mailboxService.get(principal.requireOrganizationId(), id);
    }

    @PostMapping
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public MailboxDtos.MailboxResponse create(@CurrentUser PrabhixPrincipal principal,
                                              @Valid @RequestBody MailboxDtos.CreateMailboxRequest request) {
        return mailboxService.create(principal.requireOrganizationId(), request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public MailboxDtos.MailboxDetailResponse update(@CurrentUser PrabhixPrincipal principal,
                                                    @PathVariable UUID id,
                                                    @Valid @RequestBody MailboxDtos.UpdateMailboxRequest request) {
        return mailboxService.update(principal.requireOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public void delete(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        mailboxService.delete(principal.requireOrganizationId(), id);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public MailboxDtos.MailboxMemberResponse addMember(@CurrentUser PrabhixPrincipal principal,
                                                       @PathVariable UUID id,
                                                       @Valid @RequestBody MailboxDtos.AddMailboxMemberRequest request) {
        return mailboxService.addMember(principal.requireOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public void removeMember(@CurrentUser PrabhixPrincipal principal,
                               @PathVariable UUID id,
                               @PathVariable UUID userId) {
        mailboxService.removeMember(principal.requireOrganizationId(), id, userId);
    }
}
