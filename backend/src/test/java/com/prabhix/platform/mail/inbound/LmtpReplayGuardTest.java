package com.prabhix.platform.mail.inbound;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LmtpReplayGuardTest {

    private static final String SECRET = "test-lmtp-hmac-secret";

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;

    private LmtpReplayGuard guard;

    @BeforeEach
    void setUp() {
        var inbound = new com.prabhix.platform.config.PrabhixProperties.Mail.Inbound(
                false, Duration.ofSeconds(60), 50, "token", 2621440, SECRET, true,
                Duration.ofMinutes(5), Duration.ofMinutes(15));
        var mail = TestProperties.mail("LOGGING");
        mail = new com.prabhix.platform.config.PrabhixProperties.Mail(
                mail.fromAddress(), mail.fromName(), mail.replyTo(), mail.transport(),
                mail.outbox(), inbound, mail.tracking(), mail.threading(), mail.ses(),
                mail.credentials(), mail.domainVerification());
        guard = new LmtpReplayGuard(TestProperties.withMail(mail), redis);
    }

    @Test
    void acceptsValidSignatureAndFreshNonce() {
        String recipient = "inbox@example.com";
        String raw = "Zm9v";
        String timestamp = String.valueOf(java.time.Instant.now().getEpochSecond());
        String nonce = "nonce-1";
        String signature = LmtpReplayGuard.sign(SECRET, LmtpReplayGuard.canonical(recipient, raw, timestamp, nonce));

        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertThatCode(() -> guard.verifyIfRequired(recipient, raw, timestamp, nonce, signature))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsReplayedNonce() {
        String recipient = "inbox@example.com";
        String raw = "Zm9v";
        String timestamp = String.valueOf(java.time.Instant.now().getEpochSecond());
        String nonce = "nonce-2";
        String signature = LmtpReplayGuard.sign(SECRET, LmtpReplayGuard.canonical(recipient, raw, timestamp, nonce));

        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> guard.verifyIfRequired(recipient, raw, timestamp, nonce, signature))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void skipsWhenReplayNotRequired() {
        var inbound = TestProperties.inbound();
        var mail = TestProperties.mail("LOGGING");
        guard = new LmtpReplayGuard(TestProperties.withMail(mail), redis);

        assertThatCode(() -> guard.verifyIfRequired("a@b.com", "Zm9v", null, null, null))
                .doesNotThrowAnyException();
    }
}
