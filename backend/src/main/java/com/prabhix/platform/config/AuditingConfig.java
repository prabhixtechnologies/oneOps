package com.prabhix.platform.config;

import com.prabhix.platform.security.PrabhixPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

@Configuration
public class AuditingConfig {

    /**
     * Feeds {@code @CreatedBy} / {@code @LastModifiedBy}.
     *
     * <p>Returns empty for background work such as the outbox drainer and IMAP fetchers,
     * which run with no authenticated principal. Those rows keep a null actor, which is
     * accurate — attributing them to a user would be a lie in the audit trail.
     */
    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.empty();
            }
            if (authentication.getPrincipal() instanceof PrabhixPrincipal principal) {
                return Optional.of(principal.userId());
            }
            return Optional.empty();
        };
    }
}
