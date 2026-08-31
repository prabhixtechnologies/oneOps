package com.prabhix.platform.org.web;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.org.dto.OrgDtos.CreateOrganizationRequest;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.service.OrganizationService;
import com.prabhix.platform.user.service.IdentityUserMirror;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * The tenant half of signing up, for identity to call once it has created the person.
 *
 * <p>Self-serve signup makes two things that live in two databases: an account, which identity owns
 * because it owns credentials, and an organization, which this service owns because it owns
 * organizations — the trial window, the owner membership, the seat limit, the slug, and the
 * subscription that opens behind it. Neither service can make both, and the flow is worthless if only
 * one gets made: an account with no organization can sign in and see nothing, and an organization with
 * no account has nobody who can reach it.
 *
 * <p>So identity drives it and calls this. That direction rather than the reverse because the account
 * has to exist first — the membership rows here point at its id — and because a password should never
 * pass through this service at all.
 *
 * <p>Not routed publicly: Caddy answers {@code /internal} with a 404 at the edge, and the shared token
 * below is the second lock rather than the only one. Same token as the mirror lookup in the other
 * direction, deliberately: it is one trust relationship between two services, and a second secret to
 * rotate would be a second secret to forget to rotate.
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalProvisioningController {

    private final PrabhixProperties properties;
    private final OrganizationService organizations;
    private final OrganizationMembershipRepository memberships;
    private final IdentityUserMirror mirror;

    /**
     * @return the organization created, or the one they already own — see below on why that is not an
     *     error.
     */
    @PostMapping("/organizations")
    public ProvisionResponse provision(
            @RequestHeader(value = "X-Prabhix-Service-Token", required = false) String token,
            @Valid @RequestBody ProvisionRequest request) {
        requireServiceToken(token);

        // The mirror row first, because the membership this is about to write has a foreign key to it.
        // Written from what identity sent rather than fetched back from identity: it is the caller, and
        // it is waiting.
        mirror.mirrorFromSignup(request.userId(), request.email(), request.emailVerified(),
                request.fullName());

        // Idempotent, because the caller is a network hop away and a timeout it retries after may have
        // succeeded here. Without this, a retried signup would give somebody a second organization they
        // never asked for and make them its owner, which is worse than a duplicate row: they would land
        // in whichever one the default-organization column happened to name.
        var existing = memberships.findByUserIdAndStatus(request.userId(),
                com.prabhix.platform.org.domain.OrganizationMembership.MembershipStatus.ACTIVE);
        if (!existing.isEmpty()) {
            UUID already = existing.get(0).getOrganizationId();
            log.info("Signup provisioning for {} is a repeat; they already belong to {}",
                    request.userId(), already);
            return new ProvisionResponse(already, false);
        }

        var created = organizations.create(request.userId(),
                new CreateOrganizationRequest(request.organizationName()));
        log.info("Provisioned organization {} for identity user {}", created.id(), request.userId());
        return new ProvisionResponse(created.id(), true);
    }

    /**
     * A blank configured token disables the endpoint rather than accepting a blank header, so a
     * deployment that forgot to set one fails closed. Mirrors identity's check in the other direction.
     */
    private void requireServiceToken(String presented) {
        String expected = properties.security().identity().serviceToken();
        if (expected == null || expected.isBlank()) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Service provisioning is not configured");
        }
        if (presented == null || !constantTimeEquals(expected, presented)) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Invalid service token");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param userId identity's id for the person, which this database keeps as its own primary key so
     *     the two never have to be translated.
     */
    public record ProvisionRequest(
            @NotNull UUID userId,
            @NotBlank @Email String email,
            boolean emailVerified,
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Size(max = 200) String organizationName) {
    }

    /**
     * @param created false when the person already had an organization, so identity can tell a repeat
     *     from a first attempt in its logs without treating either as a failure.
     */
    public record ProvisionResponse(UUID organizationId, boolean created) {
    }
}
