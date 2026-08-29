package com.prabhix.platform.org.web;

import com.prabhix.platform.org.domain.OrganizationDomain;
import com.prabhix.platform.org.service.OrganizationDomainService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Managing the domains an organization claims.
 *
 * <p>Guarded by {@code ORG_UPDATE} rather than an invite permission. Verifying a domain and turning on
 * automatic joining decides who can end up inside the organization at all, which is an owner-level
 * decision — not something the person who sends invitations should be able to change unilaterally.
 */
@RestController
@RequestMapping("/api/v1/organization/domains")
@RequiredArgsConstructor
public class OrganizationDomainController {

    private final OrganizationDomainService domains;

    @GetMapping
    @PreAuthorize(Authorize.ORG_UPDATE)
    public List<DomainView> list(@CurrentUser PrabhixPrincipal principal) {
        return domains.list(principal.requireOrganizationId()).stream().map(this::toView).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Authorize.ORG_UPDATE)
    public DomainView claim(@CurrentUser PrabhixPrincipal principal,
                            @Valid @RequestBody ClaimRequest request) {
        return toView(domains.claim(principal.requireOrganizationId(), request.domain()));
    }

    /**
     * Re-runs the DNS check.
     *
     * <p>A POST, not a GET: it performs an outbound lookup and writes the result, so it is neither
     * safe nor cacheable, and a browser prefetching it would be spending someone else's DNS budget.
     */
    @PostMapping("/{id}/verify")
    @PreAuthorize(Authorize.ORG_UPDATE)
    public DomainView verify(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        return toView(domains.verify(principal.requireOrganizationId(), id));
    }

    @PutMapping("/{id}/auto-join")
    @PreAuthorize(Authorize.ORG_UPDATE)
    public DomainView setAutoJoin(@CurrentUser PrabhixPrincipal principal,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody AutoJoinRequest request) {
        return toView(domains.setAutoJoin(
                principal.requireOrganizationId(), id, request.enabled(), request.roleId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Authorize.ORG_UPDATE)
    public void release(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        domains.release(principal.requireOrganizationId(), id);
    }

    private DomainView toView(OrganizationDomain claim) {
        return new DomainView(
                claim.getId(),
                claim.getDomain(),
                claim.isVerified(),
                claim.getVerifiedAt(),
                claim.getLastCheckedAt(),
                claim.getLastCheckError(),
                claim.isAutoJoinEnabled(),
                claim.getAutoJoinRoleId(),
                // Returned on every read, not only on creation. An admin who published the record
                // yesterday and is debugging why it does not verify needs to compare the two values,
                // and hiding the expected one makes that guesswork.
                domains.recordName(claim),
                domains.recordValue(claim));
    }

    public record ClaimRequest(@NotBlank String domain) {
    }

    public record AutoJoinRequest(boolean enabled, UUID roleId) {
    }

    public record DomainView(
            UUID id,
            String domain,
            boolean verified,
            Instant verifiedAt,
            Instant lastCheckedAt,
            String lastCheckError,
            boolean autoJoinEnabled,
            UUID autoJoinRoleId,
            String recordName,
            String recordValue) {
    }
}
