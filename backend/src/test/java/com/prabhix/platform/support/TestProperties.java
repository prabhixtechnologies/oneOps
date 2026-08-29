package com.prabhix.platform.support;

import com.prabhix.platform.config.PrabhixProperties;

import java.time.Duration;

/**
 * Builds {@link PrabhixProperties} for unit tests.
 *
 * <p>These are deeply nested records, so constructing them positionally in each test means every
 * new config field breaks unrelated tests. Tests state only the values they care about here and
 * everything else gets a working default.
 */
public final class TestProperties {

    private TestProperties() {
    }

    public static PrabhixProperties defaults() {
        return withMail(mail("LOGGING"));
    }

    public static PrabhixProperties withMail(PrabhixProperties.Mail mail) {
        return build(null, mail);
    }

    public static PrabhixProperties withSecurity(PrabhixProperties.Security security) {
        return build(security, mail("LOGGING"));
    }

    private static PrabhixProperties build(PrabhixProperties.Security security,
                                           PrabhixProperties.Mail mail) {
        return new PrabhixProperties(
                new PrabhixProperties.Urls("http://localhost:3000", "http://localhost:5173",
                        "http://localhost:8080"),
                new PrabhixProperties.Cors(java.util.List.of()),
                security, null, mail, null, null, limits());
    }

    public static PrabhixProperties.Security security(Duration accessTokenTtl) {
        return security(accessTokenTtl, Duration.ofDays(30));
    }

    public static PrabhixProperties.Security security(Duration accessTokenTtl,
                                                     Duration refreshTokenTtl) {
        return security(accessTokenTtl, refreshTokenTtl, identityDisabled());
    }

    public static PrabhixProperties.Security security(Duration accessTokenTtl,
                                                     Duration refreshTokenTtl,
                                                     PrabhixProperties.Security.Identity identity) {
        return new PrabhixProperties.Security(
                new PrabhixProperties.Security.Jwt(
                        "test-jwt-secret-long-enough-for-hmac-sha256-0123456789abcdefgh",
                        "prabhix-platform", accessTokenTtl, refreshTokenTtl),
                identity, null, null, sessionCookie());
    }

    /** A blank issuer, which is how every deployment starts and what most tests want. */
    public static PrabhixProperties.Security.Identity identityDisabled() {
        return new PrabhixProperties.Security.Identity(
                "", "", Duration.ofMinutes(10), Duration.ofSeconds(30));
    }

    /** Trusts an identity issuer, for the tests that present an RS256 token. */
    public static PrabhixProperties.Security.Identity identityTrusting(String issuer) {
        return new PrabhixProperties.Security.Identity(
                issuer, issuer + "/.well-known/jwks.json",
                Duration.ofMinutes(10), Duration.ofSeconds(30));
    }

    /** Host-only and insecure, matching how a browser accepts cookies on localhost. */
    public static PrabhixProperties.Security.SessionCookie sessionCookie() {
        return new PrabhixProperties.Security.SessionCookie("pbx_session", "", false, "Lax");
    }

    public static PrabhixProperties.Limits limits() {
        return new PrabhixProperties.Limits(100000, 200, 26214400L, 25, 200);
    }

    public static PrabhixProperties.Mail mail(String transport) {
        return mail(transport, threading(), ses("ap-south-1", ""));
    }

    public static PrabhixProperties.Mail mail(String transport,
                                              PrabhixProperties.Mail.Threading threading,
                                              PrabhixProperties.Mail.Ses ses) {
        return new PrabhixProperties.Mail(
                "no-reply@prabhix.test", "Prabhix", "support@prabhix.test", transport,
                outbox(), inbound(), tracking(), threading, ses, credentials(), domainVerification());
    }

    public static PrabhixProperties.Mail.Outbox outbox() {
        return new PrabhixProperties.Mail.Outbox(true, 50, Duration.ofSeconds(5), 6);
    }

    public static PrabhixProperties.Mail.Inbound inbound() {
        return new PrabhixProperties.Mail.Inbound(false, Duration.ofSeconds(60), 50, "");
    }

    public static PrabhixProperties.Mail.Tracking tracking() {
        return new PrabhixProperties.Mail.Tracking(false, "test-tracking-secret");
    }

    public static PrabhixProperties.Mail.Threading threading() {
        return new PrabhixProperties.Mail.Threading("PBX", Duration.ofDays(30));
    }

    public static PrabhixProperties.Mail.Ses ses(String region, String configurationSet) {
        return new PrabhixProperties.Mail.Ses(region, "", "", configurationSet);
    }

    public static PrabhixProperties.Mail.Credentials credentials() {
        return new PrabhixProperties.Mail.Credentials("test-mailbox-credential-key");
    }

    public static PrabhixProperties.Mail.DomainVerification domainVerification() {
        return new PrabhixProperties.Mail.DomainVerification(
                true, "0 20 4 * * *", Duration.ofDays(1), 50);
    }
}
