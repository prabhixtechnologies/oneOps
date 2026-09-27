package com.prabhix.platform.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigPublicPathsTest {

    @Test
    void appReleaseEndpointIsPublic() throws Exception {
        Field field = SecurityConfig.class.getDeclaredField("PUBLIC_PATHS");
        field.setAccessible(true);
        String[] paths = (String[]) field.get(null);
        assertThat(Arrays.asList(paths)).contains("/api/v1/oneops/public/app-release");
    }
}
