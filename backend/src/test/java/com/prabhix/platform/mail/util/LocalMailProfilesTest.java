package com.prabhix.platform.mail.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMailProfilesTest {

    @Test
    void treatsDevTestAndLocalAsLocal() {
        assertThat(LocalMailProfiles.isLocal(env("dev"))).isTrue();
        assertThat(LocalMailProfiles.isLocal(env("test"))).isTrue();
        assertThat(LocalMailProfiles.isLocal(env("local"))).isTrue();
    }

    @Test
    void treatsEmptyProfilesAsLocal() {
        assertThat(LocalMailProfiles.isLocal(new MockEnvironment())).isTrue();
    }

    @Test
    void productionAndIntegrationProfilesAreNotLocal() {
        assertThat(LocalMailProfiles.isLocal(env("prod"))).isFalse();
        assertThat(LocalMailProfiles.isLocal(env("integration"))).isFalse();
    }

    private static MockEnvironment env(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
