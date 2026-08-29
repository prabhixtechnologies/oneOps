package com.prabhix.platform.mail.util;

import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Set;

/**
 * Whether this process runs somewhere that outbound mail is allowed to go nowhere.
 *
 * <p>Two places need the same answer and must not disagree: the startup validator refuses to boot
 * when the configured transport is {@code LOGGING}, and the router refuses to fall back to logging
 * when a real transport is unreachable. If one considered an environment local and the other did
 * not, production would either fail to start or resume discarding mail.
 */
public final class LocalMailProfiles {

    private static final Set<String> LOCAL = Set.of("dev", "test", "local");

    private LocalMailProfiles() {
    }

    /**
     * An empty profile list counts as local: it is what a bare {@code mvn spring-boot:run} or an
     * unannotated unit test gets, never a deployed container, which always sets {@code prod}.
     */
    public static boolean isLocal(Environment environment) {
        String[] active = environment.getActiveProfiles();
        return active.length == 0 || Arrays.stream(active).anyMatch(LOCAL::contains);
    }
}
