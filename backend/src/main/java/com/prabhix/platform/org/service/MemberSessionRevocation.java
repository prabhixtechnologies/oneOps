package com.prabhix.platform.org.service;

import com.prabhix.identity.client.IdentityClientException;
import com.prabhix.identity.client.IdentityInternalClient;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.security.jwt.TokenDenyList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Ends the sessions of someone whose access just changed.
 *
 * <p>The permission cache is consulted on the next request. Tokens already issued would keep the
 * old access until they expired, so a role change or a removal also revokes the account.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSessionRevocation {

    private final TokenDenyList denyList;
    private final IdentityInternalClient identity;

    public void revoke(UUID userId, String reason) {
        if (userId == null) {
            return;
        }
        denyList.revokeUser(userId);
        if (!identity.enabled()) {
            log.warn("Identity is not configured; {} only updated the local deny list for {}", reason, userId);
            return;
        }
        try {
            identity.revokeTokens(userId);
        } catch (IdentityClientException ex) {
            throw ApiException.of(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "The role change could not sign that person out. Try again.");
        }
    }
}
