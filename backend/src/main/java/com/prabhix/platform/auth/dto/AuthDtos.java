package com.prabhix.platform.auth.dto;

import java.util.Set;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * Who the caller is on this platform, given the identity token they presented.
     *
     * <p>Nothing here is a credential. Identity signs the token and says who someone is; this says what
     * that person may do here — the organization the request resolved to, the permissions this
     * database grants them in it, and whether they are platform staff — and none of it is read from the
     * token, so it is current on every request rather than at the moment the token was minted.
     *
     * @param organizationId null when the person has several organizations and no default, so the
     *     console knows to ask rather than guess.
     * @param sessionId identity's session for this sign-in ({@code sid}), which is what a logout here
     *     revokes.
     */
    public record AuthMeResponse(
            UUID userId,
            String email,
            String displayName,
            UUID organizationId,
            UUID sessionId,
            Set<String> permissions,
            boolean platformAdmin) {
    }
}
