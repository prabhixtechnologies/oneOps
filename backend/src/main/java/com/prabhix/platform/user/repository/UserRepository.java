package com.prabhix.platform.user.repository;

import com.prabhix.platform.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    // Counts for the ops hub. Every one of these excludes soft-deleted rows, so the totals agree
    // with what an operator sees in the tenant directory.
    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(User.UserStatus status);

    long countByPlatformAdminTrueAndDeletedAtIsNull();

    long countByLockedUntilAfterAndDeletedAtIsNull(Instant now);

    long countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(Instant since);
}
