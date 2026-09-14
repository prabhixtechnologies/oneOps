package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.util.LocalMailProfiles;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to boot outside dev/test when outbound mail would only be logged, mirroring
 * {@link com.prabhix.platform.security.InternalSigningSecretValidator}.
 *
 * <p>This guards the configured transport. It is not sufficient on its own: a correctly configured
 * transport that is unreachable at runtime used to fall through to logging anyway, which
 * {@link com.prabhix.platform.mail.outbound.transport.MailTransportRouter} now refuses to do.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailTransportStartupValidator {

    private final PrabhixProperties properties;
    private final Environment environment;

    @PostConstruct
    void validateTransport() {
        if (!"LOGGING".equalsIgnoreCase(properties.mail().transport())) {
            return;
        }
        if (LocalMailProfiles.isLocal(environment)) {
            log.warn("Mail transport is LOGGING; outbound messages are not delivered. "
                    + "Set MAIL_TRANSPORT to SELF_HOSTED_SMTP, SMTP_RELAY, or SES in production.");
            return;
        }
        throw new IllegalStateException(
                "MAIL_TRANSPORT is LOGGING outside dev/test. OTPs and receipts would never be delivered.");
    }
}
