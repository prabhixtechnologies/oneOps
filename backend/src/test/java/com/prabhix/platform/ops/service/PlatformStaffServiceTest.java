package com.prabhix.platform.ops.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.ops.domain.PlatformStaffRole;
import com.prabhix.platform.ops.domain.StaffRole;
import com.prabhix.platform.ops.repository.PlatformStaffRoleRepository;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformStaffServiceTest {

    private PlatformStaffRoleRepository staffRoles;
    private UserRepository users;
    private PlatformStaffService service;

    private final UUID owner = UUID.randomUUID();
    private final UUID support = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        staffRoles = mock(PlatformStaffRoleRepository.class);
        users = mock(UserRepository.class);
        service = new PlatformStaffService(staffRoles, users);

        when(staffRoles.findByUserIdAndRevokedAtIsNull(owner)).thenReturn(List.of(grant(owner, StaffRole.OWNER)));
        when(staffRoles.findByUserIdAndRevokedAtIsNull(support)).thenReturn(List.of(grant(support, StaffRole.SUPPORT)));
        when(staffRoles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ownerImpliesEveryRole() {
        service.requireAny(owner, StaffRole.BREAK_GLASS);
        service.requireAny(owner, StaffRole.ROLE_ADMIN);
        service.requireAny(owner, StaffRole.TENANT_ACCESS);
    }

    @Test
    void supportCannotBreakGlass() {
        // The whole reason for splitting the boolean: reading a ticket must not also mean being able
        // to sign every customer out.
        assertThatThrownBy(() -> service.requireAny(support, StaffRole.BREAK_GLASS))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void supportCanReachIntoATenant() {
        service.requireAny(support, StaffRole.TENANT_ACCESS);
    }

    @Test
    void billingCannotReachIntoATenant() {
        UUID billing = UUID.randomUUID();
        when(staffRoles.findByUserIdAndRevokedAtIsNull(billing))
                .thenReturn(List.of(grant(billing, StaffRole.BILLING)));

        assertThatThrownBy(() -> service.requireAny(billing, StaffRole.TENANT_ACCESS))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void someoneWithNoRolesHoldsNothing() {
        UUID nobody = UUID.randomUUID();
        when(staffRoles.findByUserIdAndRevokedAtIsNull(nobody)).thenReturn(List.of());

        assertThat(service.rolesFor(nobody)).isEmpty();
        assertThatThrownBy(() -> service.requireAny(nobody, StaffRole.TENANT_ACCESS))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void onlyOwnersMayGrant() {
        assertThatThrownBy(() -> service.grant(support, target, StaffRole.SUPPORT, null))
                .isInstanceOf(ApiException.class);
        verify(staffRoles, never()).save(any());
    }

    @Test
    void grantingSetsTheCoarseStaffFlag() {
        User user = activeUser(target);
        when(users.findById(target)).thenReturn(Optional.of(user));
        when(staffRoles.findByUserIdAndRoleAndRevokedAtIsNull(target, StaffRole.SUPPORT))
                .thenReturn(Optional.empty());

        service.grant(owner, target, StaffRole.SUPPORT, "Joined support");

        assertThat(user.isPlatformAdmin()).isTrue();
        verify(users).save(user);
    }

    @Test
    void grantingTwiceIsIdempotent() {
        PlatformStaffRole existing = grant(target, StaffRole.SUPPORT);
        when(users.findById(target)).thenReturn(Optional.of(activeUser(target)));
        when(staffRoles.findByUserIdAndRoleAndRevokedAtIsNull(target, StaffRole.SUPPORT))
                .thenReturn(Optional.of(existing));

        // Granting twice then revoking once must not leave the person still holding the role.
        assertThat(service.grant(owner, target, StaffRole.SUPPORT, null)).isSameAs(existing);
        verify(staffRoles, never()).save(any());
    }

    @Test
    void revokingTheLastRoleClearsTheCoarseFlag() {
        User user = activeUser(target);
        user.setPlatformAdmin(true);
        when(users.findById(target)).thenReturn(Optional.of(user));
        when(staffRoles.findByUserIdAndRoleAndRevokedAtIsNull(target, StaffRole.SUPPORT))
                .thenReturn(Optional.of(grant(target, StaffRole.SUPPORT)));
        when(staffRoles.findByUserIdAndRevokedAtIsNull(target)).thenReturn(List.of());

        service.revoke(owner, target, StaffRole.SUPPORT, "Left the team");

        assertThat(user.isPlatformAdmin()).isFalse();
    }

    @Test
    void revokingOneOfTwoRolesKeepsTheCoarseFlag() {
        User user = activeUser(target);
        user.setPlatformAdmin(true);
        when(users.findById(target)).thenReturn(Optional.of(user));
        when(staffRoles.findByUserIdAndRoleAndRevokedAtIsNull(target, StaffRole.SUPPORT))
                .thenReturn(Optional.of(grant(target, StaffRole.SUPPORT)));
        when(staffRoles.findByUserIdAndRevokedAtIsNull(target))
                .thenReturn(List.of(grant(target, StaffRole.BILLING)));

        service.revoke(owner, target, StaffRole.SUPPORT, null);

        assertThat(user.isPlatformAdmin()).isTrue();
    }

    @Test
    void refusesToRevokeTheLastOwner() {
        // Recovering from this means editing the database by hand, during whatever incident prompted it.
        when(staffRoles.findByUserIdAndRoleAndRevokedAtIsNull(owner, StaffRole.OWNER))
                .thenReturn(Optional.of(grant(owner, StaffRole.OWNER)));
        when(staffRoles.countByRoleAndRevokedAtIsNull(StaffRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> service.revoke(owner, owner, StaffRole.OWNER, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void revokingAnOwnerIsFineWhenAnotherRemains() {
        UUID secondOwner = UUID.randomUUID();
        when(users.findById(secondOwner)).thenReturn(Optional.of(activeUser(secondOwner)));
        when(staffRoles.findByUserIdAndRoleAndRevokedAtIsNull(secondOwner, StaffRole.OWNER))
                .thenReturn(Optional.of(grant(secondOwner, StaffRole.OWNER)));
        when(staffRoles.countByRoleAndRevokedAtIsNull(StaffRole.OWNER)).thenReturn(2L);
        when(staffRoles.findByUserIdAndRevokedAtIsNull(secondOwner)).thenReturn(List.of());

        service.revoke(owner, secondOwner, StaffRole.OWNER, "Left the company");

        verify(staffRoles).save(any());
    }

    @Test
    void grantingToAMissingUserFails() {
        when(users.findById(target)).thenReturn(Optional.empty());
        when(staffRoles.findByUserIdAndRoleAndRevokedAtIsNull(target, StaffRole.SUPPORT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(owner, target, StaffRole.SUPPORT, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private static PlatformStaffRole grant(UUID userId, StaffRole role) {
        PlatformStaffRole grant = new PlatformStaffRole();
        grant.setUserId(userId);
        grant.setRole(role);
        return grant;
    }

    private static User activeUser(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("staff@prabhixtechnologies.com");
        user.setFullName("Staff Member");
        return user;
    }
}
