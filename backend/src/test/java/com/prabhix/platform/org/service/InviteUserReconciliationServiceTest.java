package com.prabhix.platform.org.service;

import com.prabhix.identity.client.IdentityInternalClient;
import com.prabhix.identity.client.IdentityUser;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InviteUserReconciliationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private IdentityInternalClient identity;
    @Mock private NamedParameterJdbcTemplate jdbc;

    private InviteUserReconciliationService reconciliationService;

    private final UUID authoritativeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID duplicateId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        reconciliationService = new InviteUserReconciliationService(userRepository, identity, jdbc);
        when(identity.enabled()).thenReturn(true);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
    }

    @Test
    void dryRunMarksApplyBlockedWhenIdentityMissing() {
        User keeper = user(authoritativeId, "keeper@example.com", Instant.parse("2024-01-01T00:00:00Z"));
        User duplicate = user(duplicateId, "keeper@example.com", Instant.parse("2024-02-01T00:00:00Z"));
        when(userRepository.findAll()).thenReturn(List.of(keeper, duplicate));
        when(identity.lookup(List.of(), List.of("keeper@example.com"))).thenReturn(List.of());

        var group = reconciliationService.dryRunEmail("keeper@example.com");

        assertThat(group.applyAllowed()).isFalse();
        assertThat(group.identityLookupStatus()).isEqualTo("IDENTITY_USER_NOT_FOUND");
    }

    @Test
    void applyRepointsAndRetiresWhenIdentityAuthoritative() {
        User keeper = user(authoritativeId, "keeper@example.com", Instant.parse("2024-01-01T00:00:00Z"));
        User duplicate = user(duplicateId, "keeper@example.com", Instant.parse("2024-02-01T00:00:00Z"));
        when(userRepository.findAll()).thenReturn(List.of(keeper, duplicate));
        when(identity.lookup(List.of(), List.of("keeper@example.com")))
                .thenReturn(List.of(new IdentityUser(
                        authoritativeId, "keeper@example.com", true, "Keeper", null, null, null,
                        null, null, "ACTIVE", false, Instant.now())));
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(userRepository.findById(duplicateId)).thenReturn(java.util.Optional.of(duplicate));

        var result = reconciliationService.apply("keeper@example.com");

        assertThat(result.authoritativeUserId()).isEqualTo(authoritativeId);
        assertThat(result.usersRetired()).isEqualTo(1);
        verify(userRepository).save(duplicate);
        assertThat(duplicate.getDeletedAt()).isNotNull();
        assertThat(duplicate.getReconciledFromUserId()).isEqualTo(authoritativeId);
    }

    @Test
    void applyAbortsWhenPlatformMissingAuthoritativeRow() {
        User keeper = user(authoritativeId, "keeper@example.com", Instant.parse("2024-01-01T00:00:00Z"));
        User duplicate = user(duplicateId, "keeper@example.com", Instant.parse("2024-02-01T00:00:00Z"));
        when(userRepository.findAll()).thenReturn(List.of(keeper, duplicate));
        UUID identityOnly = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(identity.lookup(List.of(), List.of("keeper@example.com")))
                .thenReturn(List.of(new IdentityUser(
                        identityOnly, "keeper@example.com", true, "Keeper", null, null, null,
                        null, null, "ACTIVE", false, Instant.now())));

        assertThatThrownBy(() -> reconciliationService.apply("keeper@example.com"))
                .isInstanceOf(ApiException.class);
        verify(userRepository, never()).save(duplicate);
    }

    private User user(UUID id, String email, Instant createdAt) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName("Test");
        user.setCreatedAt(createdAt);
        return user;
    }
}
