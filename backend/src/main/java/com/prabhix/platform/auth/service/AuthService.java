package com.prabhix.platform.auth.service;

import com.prabhix.identity.client.IdentityToken;
import com.prabhix.identity.client.IdentityTokenException;
import com.prabhix.identity.client.IdentityTokenVerifier;
import com.prabhix.platform.auth.dto.AuthDtos.AuthMeResponse;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import com.prabhix.platform.org.service.OrganizationService;
import com.prabhix.platform.org.service.PermissionResolver;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.jwt.TokenDenyList;
import com.prabhix.platform.security.rbac.Permission;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What is left of authentication once identity owns the credentials.
 *
 * <p>Nobody signs in here. Prabhix Identity holds the passwords, links, one-time codes and Google
 * accounts, runs the hosted login page and mints every token; this service never sees a credential
 * and holds no key that could produce a token. What it still answers is the tenant half: which
 * organization a verified person is acting in, what they may do there, and — on logout — that the
 * token they were just using must stop working here before it expires.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final OrganizationService organizationService;
    private final PermissionResolver permissionResolver;
    private final IdentityTokenVerifier verifier;
    private final TokenDenyList tokenDenyList;
    private final ApplicationEventPublisher events;
    private final StructuredEventLogger eventLogger;

    /**
     * Stops the presented token working here, immediately.
     *
     * <p>Identity's own RP-initiated logout ends the single sign-on session, and the console sends the
     * browser there right after this call; that is the part that stops the next {@code /authorize}
     * silently signing the person back in. This part covers the gap it leaves: the access token the
     * browser still holds is valid until it expires, and nothing at identity can recall it from a
     * product's cache. So the session it names goes on the deny list, and a token that names no
     * session is denied by its own id.
     *
     * <p>The token is verified again rather than reconstructed from the principal, because the principal
     * carries the session id and not the token id, and a logout that could only revoke by session would
     * leave a sessionless token live. It was verified moments ago by the filter; a second signature
     * check is cheaper than a second field on every principal.
     */
    @Transactional
    public void logout(PrabhixPrincipal principal, String bearerToken) {
        if (bearerToken != null) {
            try {
                IdentityToken token = verifier.verify(bearerToken);
                tokenDenyList.revoke(token);
            } catch (IdentityTokenException ex) {
                // The filter accepted this token a moment ago, so this is a token that expired in between
                // — and an expired token needs no revoking. Fall back to the session the principal names.
                tokenDenyList.revokeSession(principal.sessionId());
            }
        } else {
            tokenDenyList.revokeSession(principal.sessionId());
        }
        events.publishEvent(AuditRequested.of(null, principal.userId(), "auth.logout", "user",
                principal.userId()));
        eventLogger.log(LogEventCode.AUTH_LOGOUT, Map.of("userId", principal.userId()));
    }

    /**
     * Makes an organization the one the caller lands in when a request names none.
     *
     * <p>Nothing is minted. The token stays the same — it says who the person is, not where they are —
     * and the organization is resolved again on every request, so the console only has to remember the
     * new id for its {@code X-Prabhix-Org} header. The response is what {@code /auth/me} would say in the
     * chosen organization, so the console can adopt it without a second round trip.
     */
    @Transactional
    public AuthMeResponse selectOrganization(PrabhixPrincipal principal, UUID organizationId) {
        organizationService.requireActiveMembership(organizationId, principal.userId());
        User user = userService.requireActive(principal.userId());
        userService.setDefaultOrganization(user.getId(), organizationId);

        return new AuthMeResponse(
                user.getId(),
                user.getEmail(),
                user.effectiveDisplayName(),
                organizationId,
                principal.sessionId(),
                permissionNames(permissionResolver.resolve(user.getId(), organizationId)),
                user.isPlatformAdmin());
    }

    @Transactional(readOnly = true)
    public AuthMeResponse currentUser(PrabhixPrincipal principal) {
        return new AuthMeResponse(
                principal.userId(),
                principal.email(),
                principal.displayName(),
                principal.organizationId(),
                principal.sessionId(),
                permissionNames(principal.permissions()),
                principal.platformAdmin());
    }

    private static Set<String> permissionNames(Set<Permission> permissions) {
        return permissions.stream().map(Permission::name).collect(Collectors.toUnmodifiableSet());
    }
}
