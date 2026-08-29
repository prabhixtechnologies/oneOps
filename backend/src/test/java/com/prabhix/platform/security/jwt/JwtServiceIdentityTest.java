package com.prabhix.platform.security.jwt;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.support.TestProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Accepting a second signature family is the riskiest part of the identity cutover, so the failure
 * modes are pinned here rather than left to review.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtServiceIdentityTest {

    private static final String IDENTITY_ISSUER = "https://id.prabhixtechnologies.com";
    private static final String KEY_ID = "identity-2026-08";

    @Mock
    private IdentityKeySource identityKeys;

    private KeyPair identityKeyPair;
    private JwtService trusting;
    private JwtService notTrusting;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        identityKeyPair = generator.generateKeyPair();

        when(identityKeys.verificationKey(KEY_ID))
                .thenReturn(Optional.of(identityKeyPair.getPublic()));
        when(identityKeys.verificationKey(anyString())).thenAnswer(invocation ->
                KEY_ID.equals(invocation.getArgument(0))
                        ? Optional.of(identityKeyPair.getPublic())
                        : Optional.empty());

        trusting = jwtService(TestProperties.identityTrusting(IDENTITY_ISSUER));
        notTrusting = jwtService(TestProperties.identityDisabled());
    }

    private JwtService jwtService(PrabhixProperties.Security.Identity identity) {
        PrabhixProperties properties = TestProperties.withSecurity(
                TestProperties.security(Duration.ofMinutes(15), Duration.ofDays(30), identity));
        return new JwtService(properties, new MockEnvironment(), identityKeys);
    }

    @Test
    void identityTokenAuthenticatesButCarriesNoAuthority() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        JwtService.ParsedToken parsed = trusting.parseDetailed(
                identityToken(userId, sessionId, IDENTITY_ISSUER, KEY_ID));

        assertThat(parsed.source()).isEqualTo(JwtService.TokenSource.IDENTITY);
        assertThat(parsed.principal().userId()).isEqualTo(userId);
        assertThat(parsed.principal().sessionId()).isEqualTo(sessionId);
        assertThat(parsed.principal().email()).isEqualTo("owner@prabhixtechnologies.com");

        // The point of the split. Whatever an identity token says, it grants nothing on its own:
        // JwtAuthenticationFilter resolves the organization and permissions from this database.
        assertThat(parsed.principal().organizationId()).isNull();
        assertThat(parsed.principal().permissions()).isEmpty();
        assertThat(parsed.principal().platformAdmin()).isFalse();
    }

    @Test
    void identityClaimsCannotGrantOrganizationOrPermissions() {
        // A forged-looking but correctly signed token: identity would never mint these claims, and a
        // compromised identity that did must still not be able to hand out platform authority.
        String token = Jwts.builder()
                .header().keyId(KEY_ID).and()
                .issuer(IDENTITY_ISSUER)
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .claim("sid", UUID.randomUUID().toString())
                .claim("org", UUID.randomUUID().toString())
                .claim("perms", java.util.List.of("PLATFORM_ADMIN"))
                .claim("padm", true)
                .signWith(identityKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        var principal = trusting.parseDetailed(token).principal();

        assertThat(principal.organizationId()).isNull();
        assertThat(principal.permissions()).isEmpty();
        assertThat(principal.platformAdmin()).isFalse();
    }

    @Test
    void rs256IsRefusedWhenNoIdentityIssuerIsConfigured() {
        String token = identityToken(UUID.randomUUID(), UUID.randomUUID(), IDENTITY_ISSUER, KEY_ID);

        assertThatThrownBy(() -> notTrusting.parseDetailed(token))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void unpublishedKeyIdIsRefused() {
        String token = identityToken(UUID.randomUUID(), UUID.randomUUID(), IDENTITY_ISSUER, "retired-key");

        assertThatThrownBy(() -> trusting.parseDetailed(token))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void identityTokenMustCarryTheIdentityIssuer() {
        // Signed by the right key, but claiming to be a platform token. Accepting this would let an
        // identity token inherit whatever the platform issuer implies.
        String token = identityToken(UUID.randomUUID(), UUID.randomUUID(), "prabhix-platform", KEY_ID);

        assertThatThrownBy(() -> trusting.parseDetailed(token))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    /**
     * The RS256-to-HS256 confusion attack, which is the reason the key is chosen by algorithm family
     * and never by anything the token supplies.
     *
     * <p>Identity's verification key is published to the world. If it could be presented back as an
     * HMAC secret, anybody who fetched the JWKS could mint a token asserting platform admin. The
     * parser here answers HS256 with the local secret and nothing else, so the forgery fails on the
     * signature.
     */
    @Test
    void publicKeyCannotBeUsedAsAnHmacSecret() {
        byte[] publicKeyBytes = identityKeyPair.getPublic().getEncoded();
        String forged = Jwts.builder()
                .header().keyId(KEY_ID).and()
                .issuer(IDENTITY_ISSUER)
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .claim("typ", "access")
                .claim("perms", java.util.List.of("PLATFORM_ADMIN"))
                .claim("padm", true)
                .signWith(Keys.hmacShaKeyFor(padded(publicKeyBytes)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> trusting.parseDetailed(forged))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void platformTokensStillVerifyWhileIdentityIsTrusted() {
        var principal = new com.prabhix.platform.security.PrabhixPrincipal(
                UUID.randomUUID(), "owner@prabhixtechnologies.com", "Owner",
                UUID.randomUUID(), java.util.Set.of(), UUID.randomUUID(), false);

        JwtService.ParsedToken parsed =
                trusting.parseDetailed(trusting.issue(principal).token());

        assertThat(parsed.source()).isEqualTo(JwtService.TokenSource.PLATFORM);
        assertThat(parsed.principal().userId()).isEqualTo(principal.userId());
        assertThat(parsed.principal().organizationId()).isEqualTo(principal.organizationId());
    }

    @Test
    void unsignedTokenIsRefused() {
        String unsigned = Jwts.builder()
                .issuer(IDENTITY_ISSUER)
                .subject(UUID.randomUUID().toString())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .compact();

        assertThatThrownBy(() -> trusting.parseDetailed(unsigned))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    private String identityToken(UUID subject, UUID sessionId, String issuer, String keyId) {
        return Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(issuer)
                .subject(subject.toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .claim("email", "owner@prabhixtechnologies.com")
                .claim("email_verified", true)
                .claim("name", "Owner")
                .claim("sid", sessionId.toString())
                .signWith(identityKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /** HS256 needs at least 256 bits of key; an RSA SPKI encoding is longer, so this only guards intent. */
    private static byte[] padded(byte[] material) {
        if (material.length >= 32) {
            return material;
        }
        byte[] padded = new byte[32];
        System.arraycopy(material, 0, padded, 0, material.length);
        System.arraycopy("padding".getBytes(StandardCharsets.UTF_8), 0,
                padded, material.length, Math.min(7, 32 - material.length));
        return padded;
    }
}
