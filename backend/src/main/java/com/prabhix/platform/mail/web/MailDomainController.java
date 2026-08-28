package com.prabhix.platform.mail.web;

import com.prabhix.platform.mail.dto.DomainDtos;
import com.prabhix.platform.mail.provisioning.DomainVerificationService;
import com.prabhix.platform.mail.provisioning.MailDomainService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mail/domains")
@RequiredArgsConstructor
public class MailDomainController {

    private final MailDomainService domainService;
    private final DomainVerificationService verificationService;

    @GetMapping
    @PreAuthorize(Authorize.MAIL_DOMAIN_READ)
    public List<DomainDtos.DomainResponse> list(@CurrentUser PrabhixPrincipal principal) {
        return domainService.list(principal.requireOrganizationId());
    }

    @PostMapping
    @PreAuthorize(Authorize.MAIL_DOMAIN_MANAGE)
    public DomainDtos.DomainResponse create(@CurrentUser PrabhixPrincipal principal,
                                            @Valid @RequestBody DomainDtos.CreateDomainRequest request) {
        return domainService.add(principal.requireOrganizationId(), request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Authorize.MAIL_DOMAIN_MANAGE)
    public void delete(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        domainService.remove(principal.requireOrganizationId(), id);
    }

    @GetMapping("/{id}/dns")
    @PreAuthorize(Authorize.MAIL_DOMAIN_READ)
    public DomainDtos.DnsReport dns(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        return verificationService.getDnsReport(id, principal.requireOrganizationId());
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize(Authorize.MAIL_DOMAIN_MANAGE)
    public DomainDtos.DnsReport verify(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        return verificationService.verify(id, principal.requireOrganizationId());
    }
}
