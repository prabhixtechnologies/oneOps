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
            @DefaultValue RateLimit rateLimit,
            @DefaultValue Password password,
            @DefaultValue SessionCookie sessionCookie) {

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
