package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.outbound.transport.MailTransport;
import com.prabhix.platform.mail.outbound.transport.MailTransportRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports whether outbound mail can actually leave, for the transport that is configured.
 *
 * <p>This replaces Spring Boot's {@code MailHealthIndicator}, which is disabled in
 * {@code application.yml}. That one calls {@code JavaMailSender.testConnection()} unconditionally,
 * so it answers a question we may not have asked: with {@code MAIL_TRANSPORT=SES} it still probes
 * {@code spring.mail.host} and reports the whole application DOWN because a machine that sends
 * through an API has nothing listening on its SMTP port. Production served a 503 on
 * {@code /actuator/health} for exactly that reason.
 *
 * <p>Asking the router instead means the check follows the configured chain, including its
 * fallbacks, and goes DOWN only when nothing can deliver — which is the condition worth paging on.
 */
@Component
@RequiredArgsConstructor
public class MailTransportHealthIndicator implements HealthIndicator {

    private final MailTransportRouter transportRouter;
    private final PrabhixProperties properties;

    @Override
    public Health health() {
        String configured = properties.mail().transport();
        try {
            MailTransport selected = transportRouter.select();
            return Health.up()
                    .withDetail("configured", configured)
                    .withDetail("selected", selected.providerId())
                    .build();
        } catch (IllegalStateException ex) {
            // The router throws only when every transport in the chain is unreachable or
            // circuit-broken. Mail queues and retries rather than being lost, so this is a
            // degradation to investigate, not a reason to fail readiness — and it does not, because
            // readiness is a separate health group that does not include this indicator.
            return Health.down()
                    .withDetail("configured", configured)
                    .withDetail("reason", ex.getMessage())
                    .build();
        }
    }
}
