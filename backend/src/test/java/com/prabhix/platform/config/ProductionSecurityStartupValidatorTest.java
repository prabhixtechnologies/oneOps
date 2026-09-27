package com.prabhix.platform.config;

import com.prabhix.identity.client.IdentityClientProperties;
import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityStartupValidatorTest {

    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.datasource.password", "super-secret-production-db-password-value");
    }

    @Test
    void skipsChecksForDevProfile() {
        environment.setActiveProfiles("dev");
        environment.setProperty("spring.datasource.password", "oneops");
        var validator = validator(productionPrabhixProperties(), productionIdentityProperties());

        assertThatCode(validator::validateProductionConfiguration).doesNotThrowAnyException();
    }

    @Test
    void skipsChecksForLocalProfile() {
        environment.setActiveProfiles("local");
        var validator = validator(productionPrabhixProperties(), productionIdentityProperties());

        assertThatCode(validator::validateProductionConfiguration).doesNotThrowAnyException();
    }

    @Test
    void skipsChecksForTestProfile() {
        environment.setActiveProfiles("test");
        environment.setProperty("spring.datasource.password", "oneops");
        environment.setProperty("springdoc.swagger-ui.enabled", "true");
        var validator = validator(productionPrabhixProperties(), productionIdentityProperties());

        assertThatCode(validator::validateProductionConfiguration).doesNotThrowAnyException();
    }

    @Test
    void skipsChecksWhenNoProfileIsActive() {
        MockEnvironment bare = new MockEnvironment();
        bare.setProperty("spring.datasource.password", "oneops");
        var validator = new ProductionSecurityStartupValidator(
                bare, productionPrabhixProperties(), productionIdentityProperties());

        assertThatCode(validator::validateProductionConfiguration).doesNotThrowAnyException();
    }

    @Test
    void rejectsDefaultDatabasePassword() {
        environment.setProperty("spring.datasource.password", "oneops");
        var validator = validator(productionPrabhixPropertiesWithBff(), productionIdentityProperties());

        assertThatThrownBy(validator::validateProductionConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    void rejectsWeakIdentityServiceTokenWhenInternalUrlConfigured() {
        var identity = new IdentityClientProperties(
                "https://identity.example",
                null,
                Duration.ofMinutes(10),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                "http://identity:8081",
                "short",
                Duration.ofSeconds(5));
        var validator = validator(productionPrabhixPropertiesWithBff(), identity);

        assertThatThrownBy(validator::validateProductionConfiguration)
                .hasMessageContaining("IDENTITY_SERVICE_TOKEN");
    }

    @Test
    void rejectsBlankPublicBffCredential() {
        var validator = validator(productionPrabhixProperties(), productionIdentityProperties());

        assertThatThrownBy(validator::validateProductionConfiguration)
                .hasMessageContaining("PUBLIC_BFF_CREDENTIAL");
    }

    @Test
    void rejectsDefaultMailCredentialSecret() {
        var props = new PrabhixProperties(
                new PrabhixProperties.Urls("https://marketing", "https://console", "https://api"),
                new PrabhixProperties.Cors(List.of("https://console")),
                productionSecurityWithBff(),
                productionMail(),
                null,
                null,
                TestProperties.limits());
        var validator = validator(props, productionIdentityProperties());

        assertThatThrownBy(validator::validateProductionConfiguration)
                .hasMessageContaining("MAIL_CREDENTIAL_SECRET");
    }

    @Test
    void rejectsDefaultTrackingSecretWhenTrackingEnabled() {
        var tracking = new PrabhixProperties.Mail.Tracking(true, "dev-tracking-secret");
        var validator = validator(
                prabhixPropertiesWithMail(withTracking(tracking)),
                productionIdentityProperties());

        assertThatThrownBy(validator::validateProductionConfiguration)
                .hasMessageContaining("MAIL_TRACKING_SECRET");
    }

    @Test
    void rejectsInboundMailWithoutLmtpReplayProtection() {
        var inbound = new PrabhixProperties.Mail.Inbound(
                false,
                Duration.ofSeconds(60),
                50,
                "lmtp-shared-token-value-0123456789",
                2621440,
                "",
                false,
                Duration.ofMinutes(5),
                Duration.ofMinutes(15));
        var validator = validator(
                prabhixPropertiesWithMail(withInbound(inbound)),
                productionIdentityProperties());

        assertThatThrownBy(validator::validateProductionConfiguration)
                .hasMessageContaining("MAIL_LMTP_REPLAY_REQUIRED");
    }

    @Test
    void rejectsInboundMailWithoutStrongLmtpHmacSecret() {
        var inbound = new PrabhixProperties.Mail.Inbound(
                false,
                Duration.ofSeconds(60),
                50,
                "lmtp-shared-token-value-0123456789",
                2621440,
                "short",
                true,
                Duration.ofMinutes(5),
                Duration.ofMinutes(15));
        var validator = validator(
                prabhixPropertiesWithMail(withInbound(inbound)),
                productionIdentityProperties());

        assertThatThrownBy(validator::validateProductionConfiguration)
                .hasMessageContaining("MAIL_LMTP_HMAC_SECRET");
    }

    @Test
    void rejectsWeakIdentityServiceTokenWhenOnlyTokenConfigured() {
        var identity = new IdentityClientProperties(
                "https://identity.example",
                null,
                Duration.ofMinutes(10),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                null,
                "short",
                Duration.ofSeconds(5));
        var validator = validator(productionPrabhixPropertiesWithBff(), identity);

        assertThatThrownBy(validator::validateProductionConfiguration)
                .hasMessageContaining("IDENTITY_SERVICE_TOKEN");
    }

    @Test
    void rejectsSwaggerEnabledInProduction() {
        environment.setProperty("springdoc.swagger-ui.enabled", "true");
        var validator = validator(productionPrabhixPropertiesWithBff(), productionIdentityProperties());

        assertThatThrownBy(validator::validateProductionConfiguration)
                .hasMessageContaining("SWAGGER_ENABLED");
    }

    @Test
    void passesWithProductionShapedConfiguration() {
        var validator = validator(productionPrabhixPropertiesWithBff(), productionIdentityProperties());

        assertThatCode(validator::validateProductionConfiguration).doesNotThrowAnyException();
    }

    @Test
    void isWeakSharedSecretDetectsCommonPatterns() {
        assertThat(ProductionSecurityStartupValidator.isWeakSharedSecret(null)).isTrue();
        assertThat(ProductionSecurityStartupValidator.isWeakSharedSecret("changeme-please-set-a-real-secret")).isTrue();
        assertThat(ProductionSecurityStartupValidator.isWeakSharedSecret(
                "production-shared-secret-value-0123456789abcdef")).isFalse();
    }

    private ProductionSecurityStartupValidator validator(PrabhixProperties props,
                                                         IdentityClientProperties identity) {
        return new ProductionSecurityStartupValidator(environment, props, identity);
    }

    private static PrabhixProperties productionPrabhixProperties() {
        return new PrabhixProperties(
                new PrabhixProperties.Urls("https://marketing", "https://console", "https://api"),
                new PrabhixProperties.Cors(List.of("https://console")),
                productionSecurityWithoutBff(),
                productionMail(),
                null,
                null,
                TestProperties.limits());
    }

    private static PrabhixProperties productionPrabhixPropertiesWithBff() {
        return prabhixPropertiesWithMail(productionMailWithStrongSecrets());
    }

    private static PrabhixProperties prabhixPropertiesWithMail(PrabhixProperties.Mail mail) {
        return new PrabhixProperties(
                new PrabhixProperties.Urls("https://marketing", "https://console", "https://api"),
                new PrabhixProperties.Cors(List.of("https://console")),
                productionSecurityWithBff(),
                mail,
                null,
                null,
                TestProperties.limits());
    }

    private static PrabhixProperties.Mail productionMail() {
        return TestProperties.mail("SELF_HOSTED_SMTP");
    }

    private static PrabhixProperties.Mail productionMailWithStrongSecrets() {
        return new PrabhixProperties.Mail(
                "no-reply@example.com",
                "Prabhix",
                "support@example.com",
                "SELF_HOSTED_SMTP",
                TestProperties.outbox(),
                TestProperties.inbound(),
                TestProperties.tracking(),
                TestProperties.threading(),
                TestProperties.ses("ap-south-1", ""),
                new PrabhixProperties.Mail.Credentials(
                        "production-mailbox-credential-secret-0123456789abcdef", false),
                TestProperties.domainVerification());
    }

    private static PrabhixProperties.Mail withTracking(PrabhixProperties.Mail.Tracking tracking) {
        var mail = productionMailWithStrongSecrets();
        return new PrabhixProperties.Mail(
                mail.fromAddress(), mail.fromName(), mail.replyTo(), mail.transport(),
                mail.outbox(), mail.inbound(), tracking, mail.threading(), mail.ses(),
                mail.credentials(), mail.domainVerification());
    }

    private static PrabhixProperties.Mail withInbound(PrabhixProperties.Mail.Inbound inbound) {
        var mail = productionMailWithStrongSecrets();
        return new PrabhixProperties.Mail(
                mail.fromAddress(), mail.fromName(), mail.replyTo(), mail.transport(),
                mail.outbox(), inbound, mail.tracking(), mail.threading(), mail.ses(),
                mail.credentials(), mail.domainVerification());
    }

    private static PrabhixProperties.Security productionSecurityWithBff() {
        return new PrabhixProperties.Security(
                "production-signing-secret-not-the-shipped-default-0123456789abcdef",
                Duration.ofMinutes(15),
                new PrabhixProperties.Security.RateLimit(true, 10, 600),
                List.of("127.0.0.1/32"),
                "production-public-bff-credential-0123456789abcdef");
    }

    private static PrabhixProperties.Security productionSecurityWithoutBff() {
        return new PrabhixProperties.Security(
                "production-signing-secret-not-the-shipped-default-0123456789abcdef",
                Duration.ofMinutes(15),
                new PrabhixProperties.Security.RateLimit(true, 10, 600),
                List.of("127.0.0.1/32"),
                "");
    }

    private static IdentityClientProperties productionIdentityProperties() {
        return new IdentityClientProperties(
                "https://identity.example",
                null,
                Duration.ofMinutes(10),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                "http://identity:8081",
                "production-identity-service-token-0123456789abcdef",
                Duration.ofSeconds(5));
    }
}
