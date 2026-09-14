package com.prabhix.platform.chat.service;

import com.prabhix.platform.chat.config.ChatProperties;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * The token a website visitor holds for one chat conversation.
 *
 * <p>Not a bearer token for a person: it names an organization, a conversation and an anonymous
 * visitor, and it is only ever accepted by the public chat endpoints. It is the one JWT this service
 * still signs itself, with the internal signing secret — identity has no concept of an anonymous
 * visitor, and a visitor has no account for identity to describe.
 */
@Service
public class ChatTokenService {

    private static final String CLAIM_ORG = "org";
    private static final String CLAIM_CONVERSATION = "conv";
    private static final String CLAIM_VISITOR = "vis";
    private static final String TYPE_CHAT = "chat-visitor";

    /**
     * Fixed rather than configured. It was the platform's bearer-token issuer, which no longer exists
     * as a setting, and keeping the value means conversations opened before that change still verify.
     */
    static final String ISSUER = "prabhix-platform";

    private final SecretKey signingKey;
    private final String issuer = ISSUER;
    private final ChatProperties chatProperties;

    public ChatTokenService(PrabhixProperties properties, ChatProperties chatProperties) {
        this.signingKey = Keys.hmacShaKeyFor(
                properties.security().internalSigningSecret().getBytes(StandardCharsets.UTF_8));
        this.chatProperties = chatProperties;
    }

    public String issue(UUID organizationId, UUID conversationId, UUID visitorId) {
        Instant expiry = Instant.now().plus(chatProperties.conversationTokenTtl());
        return Jwts.builder()
                .issuer(issuer)
                .subject(TYPE_CHAT)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_ORG, organizationId.toString())
                .claim(CLAIM_CONVERSATION, conversationId.toString())
                .claim(CLAIM_VISITOR, visitorId != null ? visitorId.toString() : "")
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public ConversationToken parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!TYPE_CHAT.equals(claims.getSubject())) {
                throw invalid();
            }
            return new ConversationToken(
                    UUID.fromString(claims.get(CLAIM_ORG, String.class)),
                    UUID.fromString(claims.get(CLAIM_CONVERSATION, String.class)),
                    parseOptionalUuid(claims.get(CLAIM_VISITOR, String.class)));
        } catch (ExpiredJwtException ex) {
            throw ApiException.of(ErrorCode.TOKEN_EXPIRED, "Conversation token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            throw invalid();
        }
    }

    public void assertConversation(ConversationToken token, UUID conversationId) {
        if (!token.conversationId().equals(conversationId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Conversation token does not match");
        }
    }

    private UUID parseOptionalUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private ApiException invalid() {
        return ApiException.of(ErrorCode.TOKEN_INVALID, "Conversation token is not valid");
    }

    public record ConversationToken(UUID organizationId, UUID conversationId, UUID visitorId) {
    }
}
