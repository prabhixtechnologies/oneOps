package com.prabhix.platform.org.service;

import com.prabhix.identity.client.IdentityInternalClient;
import com.prabhix.identity.client.IdentityUser;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.org.service.UserForeignKeyCatalog.ColumnRef;
import com.prabhix.platform.org.service.UserForeignKeyCatalog.UniqueScope;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Merges duplicate product users created by the pre-identity invite accept path (same email, different ids).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteUserReconciliationService {

    private final UserRepository userRepository;
    private final IdentityInternalClient identity;
    private final NamedParameterJdbcTemplate jdbc;

    public record ReferenceCount(String table, String column, long count) {
    }

    public record UniqueConflict(String table, String scopeColumn, String scopeValue, String detail) {
    }

    public record DuplicateUserRow(UUID userId, Instant createdAt, boolean deleted, long totalReferences) {
    }

    public record EmailDuplicateGroup(
            String normalizedEmail,
            UUID authoritativeIdentityUserId,
            String identityLookupStatus,
            List<DuplicateUserRow> platformUsers,
            List<ReferenceCount> referenceCounts,
            List<UniqueConflict> uniqueConflicts,
            boolean applyAllowed) {
    }

    public record DryRunReport(List<EmailDuplicateGroup> groups) {
    }

    public record ApplyResult(String normalizedEmail, UUID authoritativeUserId, int referencesRepointed, int usersRetired) {
    }

    @Transactional(readOnly = true)
    public DryRunReport dryRun() {
        Map<String, List<User>> byEmail = loadDuplicateEmailGroups();
        List<EmailDuplicateGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<User>> entry : byEmail.entrySet()) {
            groups.add(buildGroup(entry.getKey(), entry.getValue()));
        }
        return new DryRunReport(groups);
    }

    @Transactional(readOnly = true)
    public EmailDuplicateGroup dryRunEmail(String normalizedEmail) {
        String key = normalizeEmail(normalizedEmail);
        List<User> users = userRepository.findAll().stream()
                .filter(user -> key.equals(normalizeEmail(user.getEmail())))
                .toList();
        if (users.size() < 2) {
            throw ApiException.of(ErrorCode.MALFORMED_REQUEST, "No duplicate platform users for this email");
        }
        return buildGroup(key, users);
    }

    @Transactional
    public ApplyResult apply(String normalizedEmail) {
        EmailDuplicateGroup group = dryRunEmail(normalizedEmail);
        if (!group.applyAllowed()) {
            throw ApiException.of(ErrorCode.CONFLICT,
                    "Reconciliation blocked: " + group.identityLookupStatus()
                            + (group.uniqueConflicts().isEmpty() ? "" : "; unique key conflicts present"));
        }
        UUID authoritative = Objects.requireNonNull(group.authoritativeIdentityUserId());
        List<UUID> duplicates = group.platformUsers().stream()
                .map(DuplicateUserRow::userId)
                .filter(id -> !id.equals(authoritative))
                .toList();

        int repointed = 0;
        for (UUID duplicateId : duplicates) {
            repointed += repointReferences(authoritative, duplicateId);
            retireDuplicate(authoritative, duplicateId);
        }
        return new ApplyResult(group.normalizedEmail(), authoritative, repointed, duplicates.size());
    }

    private EmailDuplicateGroup buildGroup(String normalizedEmail, List<User> users) {
        IdentityResolution identityResolution = resolveAuthoritativeIdentity(normalizedEmail, users);
        List<DuplicateUserRow> rows = users.stream()
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(user -> new DuplicateUserRow(
                        user.getId(),
                        user.getCreatedAt(),
                        user.isDeleted(),
                        totalReferences(user.getId())))
                .toList();

        List<ReferenceCount> counts = new ArrayList<>();
        for (User user : users) {
            counts.addAll(referenceCountsForUser(user.getId()));
        }

        List<UniqueConflict> conflicts = new ArrayList<>();
        UUID authoritative = identityResolution.authoritativeId();
        if (authoritative != null) {
            for (User user : users) {
                if (!user.getId().equals(authoritative)) {
                    conflicts.addAll(detectUniqueConflicts(authoritative, user.getId()));
                }
            }
        }

        boolean applyAllowed = authoritative != null
                && identityResolution.status().equals("OK")
                && conflicts.isEmpty()
                && users.stream().anyMatch(user -> user.getId().equals(authoritative));

        return new EmailDuplicateGroup(
                normalizedEmail,
                authoritative,
                identityResolution.status(),
                rows,
                counts,
                conflicts,
                applyAllowed);
    }

    private IdentityResolution resolveAuthoritativeIdentity(String normalizedEmail, List<User> users) {
        if (!identity.enabled()) {
            return new IdentityResolution(null, "IDENTITY_DISABLED");
        }
        List<IdentityUser> found = identity.lookup(List.of(), List.of(normalizedEmail));
        if (found.isEmpty()) {
            return new IdentityResolution(null, "IDENTITY_USER_NOT_FOUND");
        }
        if (found.size() > 1) {
            return new IdentityResolution(null, "IDENTITY_AMBIGUOUS");
        }
        UUID identityId = found.get(0).id();
        boolean platformHasAuthoritative = users.stream().anyMatch(user -> user.getId().equals(identityId));
        if (!platformHasAuthoritative) {
            return new IdentityResolution(identityId, "PLATFORM_MISSING_AUTHORITATIVE_ROW");
        }
        return new IdentityResolution(identityId, "OK");
    }

    private record IdentityResolution(UUID authoritativeId, String status) {
    }

    private Map<String, List<User>> loadDuplicateEmailGroups() {
        return userRepository.findAll().stream()
                .collect(Collectors.groupingBy(user -> normalizeEmail(user.getEmail())))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private long totalReferences(UUID userId) {
        return referenceCountsForUser(userId).stream().mapToLong(ReferenceCount::count).sum();
    }

    private List<ReferenceCount> referenceCountsForUser(UUID userId) {
        List<ReferenceCount> counts = new ArrayList<>();
        for (ColumnRef ref : UserForeignKeyCatalog.UPDATABLE_COLUMNS) {
            long count = countReferences(ref.table(), ref.column(), userId);
            if (count > 0) {
                counts.add(new ReferenceCount(ref.table(), ref.column(), count));
            }
        }
        return counts;
    }

    private long countReferences(String table, String column, UUID userId) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = :userId";
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource("userId", userId), Long.class);
        return count == null ? 0L : count;
    }

    private List<UniqueConflict> detectUniqueConflicts(UUID authoritativeId, UUID duplicateId) {
        List<UniqueConflict> conflicts = new ArrayList<>();
        for (UniqueScope scope : UserForeignKeyCatalog.UNIQUE_SCOPES) {
            String sql = """
                    SELECT s.%s AS scope_value, COUNT(*) AS cnt
                    FROM %s s
                    WHERE s.%s IN (:auth, :dup)
                    GROUP BY s.%s
                    HAVING COUNT(*) > 1
                    """.formatted(scope.scopeColumn(), scope.table(), scope.userColumn(), scope.scopeColumn());
            jdbc.query(sql, new MapSqlParameterSource()
                            .addValue("auth", authoritativeId)
                            .addValue("dup", duplicateId),
                    (rs, rowNum) -> {
                        conflicts.add(new UniqueConflict(
                                scope.table(),
                                scope.scopeColumn(),
                                rs.getString("scope_value"),
                                "Both user ids occupy the same " + scope.scopeColumn()));
                        return null;
                    });
        }
        return conflicts;
    }

    private int repointReferences(UUID authoritativeId, UUID duplicateId) {
        int updated = 0;
        for (ColumnRef ref : UserForeignKeyCatalog.UPDATABLE_COLUMNS) {
            String sql = "UPDATE " + ref.table()
                    + " SET " + ref.column() + " = :auth WHERE " + ref.column() + " = :dup";
            updated += jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("auth", authoritativeId)
                    .addValue("dup", duplicateId));
        }
        return updated;
    }

    private void retireDuplicate(UUID authoritativeId, UUID duplicateId) {
        User duplicate = userRepository.findById(duplicateId)
                .orElseThrow(() -> ApiException.notFound("User"));
        if (duplicate.getId().equals(authoritativeId)) {
            return;
        }
        duplicate.setDeletedAt(Instant.now());
        duplicate.setReconciledFromUserId(authoritativeId);
        duplicate.setEmail(retiredEmail(duplicate.getEmail(), duplicateId));
        userRepository.save(duplicate);
    }

    private static String retiredEmail(String email, UUID duplicateId) {
        String base = normalizeEmail(email);
        String suffix = "+reconciled-" + duplicateId;
        int max = 320 - suffix.length();
        if (base.length() > max) {
            base = base.substring(0, max);
        }
        return base + suffix;
    }
}
