package com.prabhix.platform.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Every tunable the platform reads, bound from the {@code prabhix.*} tree in
 * {@code application.yml}.
 *
 * <p>Records with {@code @DefaultValue} on nested types mean a missing config block yields a
 * populated object with sane defaults rather than a {@code NullPointerException} at first use.
 */
@Validated
@ConfigurationProperties(prefix = "prabhix")
public record PrabhixProperties(
        @DefaultValue Urls urls,
        @DefaultValue Cors cors,
        @DefaultValue Security security,
        @DefaultValue Otp otp,
        @DefaultValue Mail mail,
        @DefaultValue Billing billing,
        @DefaultValue Storage storage,
        @DefaultValue Limits limits) {

    public record Urls(
            @DefaultValue("http://localhost:3000") String marketing,
            @DefaultValue("http://localhost:5173") String console,
            @DefaultValue("http://localhost:8080") String api) {
    }

    public record Cors(@DefaultValue({"http://localhost:3000", "http://localhost:5173"})
                       List<String> allowedOrigins) {
    }

    public record Security(
            @DefaultValue Jwt jwt,
            @DefaultValue Identity identity,
            @DefaultValue RateLimit rateLimit,
            @DefaultValue Password password,
            @DefaultValue SessionCookie sessionCookie) {

        /**
         * Trust for tokens issued by Prabhix Identity, verified against its published JWKS.
         *
         * <p>Blank {@code issuer} disables it, which is the default and the state of every deployment
         * until identity is actually running. While disabled the backend accepts only its own HS256
         * tokens, exactly as before.
         *
         * <p>Both signature families are accepted at once on purpose. An access token lives 15 minutes
         * and a refresh token 30 days, so a hard switch would sign out everyone holding a token minted
         * a moment earlier. Accepting both means the changeover is invisible, and HS256 can be dropped
         * once no token that old can still be in circulation.
         *
         * <p>An identity token deliberately carries no organization and no permissions. Those are
         * resolved per request from this database instead — which is also strictly better than the
         * HS256 path, where permissions freeze into the token at sign-in and a revoked role keeps
         * working until it expires.
         */
        public record Identity(
                @DefaultValue("") String issuer,
                /** Defaults to {@code {issuer}/.well-known/jwks.json}; set only if that is not where it is. */
                @DefaultValue("") String jwksUri,
                /**
                 * How long a fetched key set is reused. An unknown key id forces a refresh regardless,
                 * so this is the ceiling on how long a *retired* key stays accepted, not on how long a
                 * new one takes to be noticed.
                 */
                @DefaultValue("PT10M") Duration jwksCacheTtl,
                /** Floor between refreshes, so a stream of tokens naming absent key ids cannot be used to hammer identity. */
                @DefaultValue("PT30S") Duration jwksMinRefreshInterval,
                /**
                 * Where {@code /internal/users/lookup} lives, for filling in the local users mirror.
                 * An internal address, not the issuer: {@code /internal} is not routed publicly.
                 */
                @DefaultValue("http://identity:8081") String internalBaseUrl,
                /**
                 * Shared secret presented as {@code X-Prabhix-Service-Token}. Blank means the mirror
                 * cannot be filled in on demand, and a subject with no local row is refused.
                 */
                @DefaultValue("") String serviceToken) {

            public boolean canMirror() {
                return serviceToken != null && !serviceToken.isBlank();
            }

            public boolean enabled() {
                return issuer != null && !issuer.isBlank();
            }

            public String effectiveJwksUri() {
                if (jwksUri != null && !jwksUri.isBlank()) {
                    return jwksUri;
                }
                String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
                return base + "/.well-known/jwks.json";
            }
        }

        /**
         * The browser session cookie that lets one sign-in cover every console hostname.
         *
         * <p>{@code domain} must be the parent of every host that should share the session
         * ({@code .prabhixtechnologies.com}), and blank in local development, where a host-only
         * cookie on localhost is what works. Setting it to a domain the response is not served from
         * makes the browser drop the cookie silently, which presents as "login does nothing".
         *
         * <p>{@code SameSite=Lax} is what keeps this off the CSRF surface: the cookie is not
         * attached to cross-site POSTs, so a form on another origin cannot drive the exchange
         * endpoint, and no other endpoint reads cookies at all.
         */
        public record SessionCookie(
                @DefaultValue("pbx_session") String name,
                @DefaultValue("") String domain,
                @DefaultValue("true") boolean secure,
                @DefaultValue("Lax") String sameSite) {

            public String domainOrNull() {
                return domain == null || domain.isBlank() ? null : domain;
            }
        }

        public record Jwt(
                @NotBlank @DefaultValue("dev-only-insecure-secret-change-me-0123456789abcdefghijklmnop")
                String secret,
                @DefaultValue("prabhix-platform") String issuer,
                @DefaultValue("PT15M") Duration accessTokenTtl,
                @DefaultValue("P30D") Duration refreshTokenTtl) {
        }

        public record RateLimit(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("10") int authAttemptsPerMinute,
                @DefaultValue("600") int apiRequestsPerMinute) {
        }

        public record Password(
                @DefaultValue("10") @Min(8) int minLength,
                @DefaultValue("12") @Min(10) int bcryptStrength) {
        }
    }

    public record Otp(
            @DefaultValue("6") int length,
            @DefaultValue("PT10M") Duration ttl,
            @DefaultValue("5") int maxAttempts) {
    }

    public record Mail(
            @DefaultValue("no-reply@prabhixtechnologies.com") String fromAddress,
            @DefaultValue("Prabhix Technologies") String fromName,
            @DefaultValue("support@prabhixtechnologies.com") String replyTo,
            @DefaultValue("LOGGING") String transport,
            @DefaultValue Outbox outbox,
            @DefaultValue Inbound inbound,
            @DefaultValue Tracking tracking,
            @DefaultValue Threading threading,
            @DefaultValue Ses ses,
            @DefaultValue Credentials credentials,
            @DefaultValue DomainVerification domainVerification) {

        public record Outbox(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("50") int batchSize,
                @DefaultValue("PT5S") Duration pollInterval,
                @DefaultValue("6") int maxAttempts) {
        }

        public record Inbound(
                @DefaultValue("false") boolean imapEnabled,
                @DefaultValue("PT60S") Duration pollInterval,
                @DefaultValue("50") int fetchBatchSize,
                @DefaultValue("") String lmtpToken) {
        }

        public record Tracking(
                @DefaultValue("false") boolean enabled,
                @DefaultValue("dev-tracking-secret") String secret) {
        }

        public record Threading(
                @DefaultValue("PBX") String tokenPrefix,
                @DefaultValue("P30D") Duration subjectFallbackWindow) {
        }

        /**
         * When access keys are blank the SDK's default credential chain is used, which is how
         * this runs on EC2 with an instance role instead of long-lived secrets.
         */
        public record Ses(
                @DefaultValue("ap-south-1") String region,
                @DefaultValue("") String accessKey,
                @DefaultValue("") String secretKey,
                @DefaultValue("") String configurationSet) {

            public boolean hasStaticCredentials() {
                return accessKey != null && !accessKey.isBlank()
                        && secretKey != null && !secretKey.isBlank();
            }
        }

        /**
         * Key for the AES-GCM envelope around stored IMAP and SMTP mailbox passwords. Rotating it
         * makes existing ciphertext unreadable, so mailbox credentials must be re-entered.
         */
        public record Credentials(
                @DefaultValue("dev-only-mailbox-credential-key-change-me") String secret) {
        }

        public record DomainVerification(
                @DefaultValue("true") boolean recheckEnabled,
                @DefaultValue("0 20 4 * * *") String recheckCron,
                @DefaultValue("P1D") Duration recheckInterval,
                @DefaultValue("50") int recheckBatchSize) {
        }
    }

    public record Billing(
            @DefaultValue Razorpay razorpay,
            @DefaultValue("INR") String currency,
            @DefaultValue Invoice invoice,
            @DefaultValue("14") int trialDays) {

        public record Razorpay(
                @DefaultValue("") String keyId,
                @DefaultValue("") String keySecret,
                @DefaultValue("") String webhookSecret,
                @DefaultValue("https://api.razorpay.com/v1") String apiBase) {

            /** Gateway calls are skipped entirely when credentials are absent, so local dev works. */
            public boolean configured() {
                return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
            }
        }

        public record Invoice(
                @DefaultValue("PBX") String prefix,
                @DefaultValue("18") int gstPercent) {
        }
    }

    public record Storage(
            @DefaultValue("") String endpoint,
            @DefaultValue("ap-south-1") String region,
            @DefaultValue("prabhix-local") String bucket,
            @DefaultValue("") String accessKey,
            @DefaultValue("") String secretKey,
            @DefaultValue("true") boolean pathStyleAccess,
            @DefaultValue("PT15M") Duration signedUrlTtl) {

        public boolean configured() {
            return accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank();
        }
    }

    public record Limits(
            @DefaultValue("100000") int maxMembersPerOrganization,
            @DefaultValue("200") int maxMailboxesPerOrganization,
            @DefaultValue("26214400") long maxAttachmentSizeBytes,
            @DefaultValue("25") int defaultPageSize,
            @DefaultValue("200") int maxPageSize) {

        /** Clamps a client-supplied page size so one caller cannot ask for a million rows. */
        public int clampPageSize(Integer requested) {
            if (requested == null || requested < 1) {
                return defaultPageSize;
            }
            return Math.min(requested, maxPageSize);
        }
    }
}
