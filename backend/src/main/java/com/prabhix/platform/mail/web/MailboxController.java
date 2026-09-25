package com.prabhix.platform.mail.web;

import com.prabhix.platform.mail.dto.MailboxDtos;
import com.prabhix.platform.mail.helpdesk.MailboxService;
import com.prabhix.platform.mail.provisioning.MailboxPasswordService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oneops/mail/mailboxes")
@RequiredArgsConstructor
public class MailboxController {

    private final MailboxService mailboxService;
    private final MailboxPasswordService mailPasswordService;

    @GetMapping
    @PreAuthorize(Authorize.MAIL_MAILBOX_READ)
    public List<MailboxDtos.MailboxResponse> list(@CurrentUser PrabhixPrincipal principal) {
        return mailboxService.list(principal.requireOrganizationId());
    }

    @GetMapping(params = "id")
    @PreAuthorize(Authorize.MAIL_MAILBOX_READ)
    public MailboxDtos.MailboxDetailResponse get(@CurrentUser PrabhixPrincipal principal,
                                                   @RequestParam UUID id) {
        return mailboxService.get(principal.requireOrganizationId(), id);
    }

    @PostMapping
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public MailboxDtos.MailboxResponse create(@CurrentUser PrabhixPrincipal principal,
                                              @Valid @RequestBody MailboxDtos.CreateMailboxRequest request) {
        return mailboxService.create(principal.requireOrganizationId(), request);
    }

    @PatchMapping
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public MailboxDtos.MailboxDetailResponse update(@CurrentUser PrabhixPrincipal principal,
                                                    @RequestParam UUID id,
                                                    @Valid @RequestBody MailboxDtos.UpdateMailboxRequest request) {
        return mailboxService.update(principal.requireOrganizationId(), id, request);
    }

    @DeleteMapping
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public void delete(@CurrentUser PrabhixPrincipal principal, @RequestParam UUID id) {
        mailboxService.delete(principal.requireOrganizationId(), id);
    }

    /**
     * Issues the password a mail client authenticates with, returned once and not retrievable.
     *
     * <p>POST rather than PUT: it is not idempotent. Calling it twice produces two different
     * passwords and invalidates the first, which signs out every client configured with it.
     */
    @PostMapping("/mail-password")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public MailboxPasswordService.Issued issueMailPassword(@CurrentUser PrabhixPrincipal principal,
                                                           @RequestParam UUID id) {
        return mailPasswordService.issue(principal.requireOrganizationId(), id);
    }

    @DeleteMapping("/mail-password")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public void revokeMailPassword(@CurrentUser PrabhixPrincipal principal, @RequestParam UUID id) {
        mailPasswordService.revoke(principal.requireOrganizationId(), id);
    }

    @PostMapping("/members")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public MailboxDtos.MailboxMemberResponse addMember(@CurrentUser PrabhixPrincipal principal,
                                                       @RequestParam UUID id,
                                                       @Valid @RequestBody MailboxDtos.AddMailboxMemberRequest request) {
        return mailboxService.addMember(principal.requireOrganizationId(), id, request);
    }

    @PatchMapping("/members")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public MailboxDtos.MailboxMemberResponse updateMember(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam UUID id,
            @RequestParam UUID memberId,
            @Valid @RequestBody MailboxDtos.UpdateMailboxMemberRequest request) {
        return mailboxService.updateMember(principal.requireOrganizationId(), id, memberId, request);
    }

    /**
     * Revokes one grant, named by its membership id.
     *
     * <p>Sits alongside the older {@code /members/by-user/{userId}} route rather than replacing it:
     * only a membership id can name a team grant, and only a user id is available to a caller that
     * has just looked someone up in the directory.
     */
    @DeleteMapping("/members")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public void removeMember(@CurrentUser PrabhixPrincipal principal,
                             @RequestParam UUID id,
                             @RequestParam UUID memberId) {
        mailboxService.removeMember(principal.requireOrganizationId(), id, memberId);
    }

    @DeleteMapping("/members/by-user")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public void removeMemberByUser(@CurrentUser PrabhixPrincipal principal,
                                   @RequestParam UUID id,
                                   @RequestParam UUID userId) {
        mailboxService.removeMemberByUser(principal.requireOrganizationId(), id, userId);
    }

    @PostMapping("/routing-rules")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public MailboxDtos.RoutingRuleResponse createRoutingRule(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam UUID id,
            @Valid @RequestBody MailboxDtos.SaveRoutingRuleRequest request) {
        return mailboxService.createRoutingRule(principal.requireOrganizationId(), id, request);
    }

    @PatchMapping("/routing-rules")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public MailboxDtos.RoutingRuleResponse updateRoutingRule(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam UUID id,
            @RequestParam UUID ruleId,
            @Valid @RequestBody MailboxDtos.SaveRoutingRuleRequest request) {
        return mailboxService.updateRoutingRule(principal.requireOrganizationId(), id, ruleId, request);
    }

    @DeleteMapping("/routing-rules")
    @PreAuthorize(Authorize.MAIL_MAILBOX_MANAGE)
    public void deleteRoutingRule(@CurrentUser PrabhixPrincipal principal,
                                  @RequestParam UUID id,
                                  @RequestParam UUID ruleId) {
        mailboxService.deleteRoutingRule(principal.requireOrganizationId(), id, ruleId);
    }
}
