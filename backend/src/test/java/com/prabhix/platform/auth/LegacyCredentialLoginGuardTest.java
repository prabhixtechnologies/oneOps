package com.prabhix.platform.auth;

import com.prabhix.platform.auth.config.AuthProperties;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyCredentialLoginGuardTest {

    @Test
    void allowsWhenLegacyEnabled() {
        LegacyCredentialLoginGuard guard = new LegacyCredentialLoginGuard(
                new AuthProperties(true), TestProperties.defaults());

        assertThatCode(guard::requireLegacyEnabled).doesNotThrowAnyException();
    }

    @Test
    void refusesWithGoneWhenLegacyDisabled() {
        PrabhixProperties properties = TestProperties.withSecurity(
                TestProperties.security(
                        java.time.Duration.ofMinutes(15),
                        java.time.Duration.ofDays(30),
                        TestProperties.identityTrusting("http://localhost:8081")));
        LegacyCredentialLoginGuard guard = new LegacyCredentialLoginGuard(
                new AuthProperties(false), properties);

        assertThatThrownBy(guard::requireLegacyEnabled)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo(ErrorCode.AUTH_MOVED_TO_IDENTITY);
                    assertThat(api.getCode().status().value()).isEqualTo(410);
                    assertThat(api.getMessage()).contains("Identity").contains("http://localhost:8081");
                });
    }
}
