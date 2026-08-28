package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.config.PrabhixProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Refuses to boot outside dev/test when outbound mail would only be logged, mirroring
 * {@link com.prabhix.platform.security.jwt.JwtService#validateSecretStrength()}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailTransportStartupValidator {

    private static final Set<String> LOCAL_PROFILES = Set.of("dev", "test", "local");

    private final PrabhixProperties properties;
    private final Environment environment;

    @PostConstruct
    void validateTransport() {
        if (!"LOGGING".equalsIgnoreCase(properties.mail().transport())) {
            return;
        }
        boolean localProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(LOCAL_PROFILES::contains)
                || environment.getActiveProfiles().length == 0;
        if (localProfile) {
            log.warn("Mail transport is LOGGING; outbound messages are not delivered. "
                    + "Set MAIL_TRANSPORT to SELF_HOSTED_SMTP, SMTP_RELAY, or SES in production.");
            return;
        }
        throw new IllegalStateException(
                "MAIL_TRANSPORT is LOGGING outside dev/test. OTPs and receipts would never be delivered.");
    }
}
