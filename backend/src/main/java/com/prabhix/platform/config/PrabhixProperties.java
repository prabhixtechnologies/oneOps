package com.prabhix.platform.config;

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

    /**
     * What this API still keeps secret on its own account.
     *
     * <p>Bearer tokens are not in here. Every one of them is issued by Prabhix Identity and verified
     * against its published RS256 keys through {@code prabhix.identity.*}, which the identity starter
     * binds; this service holds no key that could produce one. What remains is the material for
     * things that are not credentials for a person: the chat visitor token and the envelope around
     * stored DKIM private keys.
     */
    public record Security(
            /**
             * HMAC key for chat visitor tokens and the cipher key for DKIM private keys at rest.
             * Never used to verify a bearer token — {@link com.prabhix.platform.security.jwt.JwtAuthenticationFilter}
             * has no HS256 path — so knowing it lets nobody act as a user. Rotating it invalidates
             * open chat conversations and makes stored DKIM keys unreadable until regenerated.
             */
            @NotBlank @DefaultValue("dev-only-insecure-secret-change-me-0123456789abcdefghijklmnop")
            String internalSigningSecret,
            /**
             * How long a revocation stays in the deny list. Must cover identity's access-token
             * lifetime: an entry that expires before the tokens it denies would let a signed-out
             * session back in for the remainder.
             */
            @DefaultValue("PT15M") Duration denyListTtl,
            @DefaultValue RateLimit rateLimit) {

        public record RateLimit(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("10") int authAttemptsPerMinute,
                @DefaultValue("600") int apiRequestsPerMinute) {
        }
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
