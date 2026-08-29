package com.prabhix.platform.ops.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.ops.domain.PlatformStaffRole;
import com.prabhix.platform.ops.domain.StaffRole;
import com.prabhix.platform.ops.repository.PlatformStaffRoleRepository;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Granting, revoking and checking Prabhix staff roles.
 *
 * <p>The only writer of {@code users.platform_admin}. That flag is now a derived "is staff at all"
 * gate — set when the first role is granted, cleared when the last is revoked — and keeping exactly one
 * writer is what stops it drifting from the table it summarises. Twenty call sites and the security
 * config read it, so it stays rather than being replaced everywhere at once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformStaffService {

    private final PlatformStaffRoleRepository staffRoles;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public Set<StaffRole> rolesFor(UUID userId) {
        List<PlatformStaffRole> live = staffRoles.findByUserIdAndRevokedAtIsNull(userId);
        if (live.isEmpty()) {
            return Set.of();
        }
        EnumSet<StaffRole> roles = EnumSet.noneOf(StaffRole.class);
        live.forEach(grant -> roles.add(grant.getRole()));
        return roles;
    }

    /**
     * Refuses unless the caller holds one of these roles, or OWNER.
     *
     * @throws ApiException with {@link ErrorCode#FORBIDDEN}, deliberately not saying which role would
     *     have been sufficient — an error that enumerates the platform's privilege model to whoever
     *     probes it is a gift
     */
    @Transactional(readOnly = true)
    public void requireAny(UUID userId, Collection<StaffRole> permitted) {
        Set<StaffRole> held = rolesFor(userId);
        boolean allowed = held.stream()
                .anyMatch(role -> permitted.stream().anyMatch(role::implies));
        if (!allowed) {
            log.warn("Staff action refused: user {} holds {} and needed one of {}",
                    userId, held, permitted);
            throw ApiException.of(ErrorCode.FORBIDDEN,
                    "Your platform role does not include this action.");
        }
    }

    @Transactional
    public PlatformStaffRole grant(UUID actorId, UUID userId, StaffRole role, String note) {
        requireAny(actorId, StaffRole.ROLE_ADMIN);

        User user = users.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such user"));

        // Idempotent. Granting twice and revoking once must not leave the person still holding it,
        // which is the worst outcome a revocation path can have — the unique index enforces it too.
        return staffRoles.findByUserIdAndRoleAndRevokedAtIsNull(userId, role).orElseGet(() -> {
            PlatformStaffRole grant = new PlatformStaffRole();
            grant.setUserId(userId);
            grant.setRole(role);
            grant.setGrantedBy(actorId);
            grant.setNote(note);
            PlatformStaffRole saved = staffRoles.save(grant);

            if (!user.isPlatformAdmin()) {
                user.setPlatformAdmin(true);
                users.save(user);
            }
            log.warn("Staff role {} granted to user {} by {}", role, userId, actorId);
            return saved;
        });
    }

    @Transactional
    public void revoke(UUID actorId, UUID userId, StaffRole role, String note) {
        requireAny(actorId, StaffRole.ROLE_ADMIN);

        PlatformStaffRole grant = staffRoles.findByUserIdAndRoleAndRevokedAtIsNull(userId, role)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND,
                        "That person does not hold that role"));

        // A platform with no owner has nobody who can grant the role back, so recovering means editing
        // the database by hand — during whatever incident prompted the revocation.
        if (role == StaffRole.OWNER && staffRoles.countByRoleAndRevokedAtIsNull(StaffRole.OWNER) <= 1) {
            throw ApiException.of(ErrorCode.INVALID_STATE,
                    "That is the last platform owner. Grant the role to someone else first.");
        }

        grant.setRevokedAt(Instant.now());
        grant.setRevokedBy(actorId);
        if (note != null && !note.isBlank()) {
            grant.setNote(note);
        }
        staffRoles.save(grant);

        // Clears the coarse flag only when nothing is left, so revoking SUPPORT from someone who is
        // also BILLING does not lock them out of the console entirely.
        if (staffRoles.findByUserIdAndRevokedAtIsNull(userId).isEmpty()) {
            users.findById(userId).ifPresent(user -> {
                user.setPlatformAdmin(false);
                users.save(user);
            });
        }
        log.warn("Staff role {} revoked from user {} by {}", role, userId, actorId);
    }

    @Transactional(readOnly = true)
    public List<PlatformStaffRole> listAll() {
        return staffRoles.findByRevokedAtIsNullOrderByGrantedAtDesc();
    }
}
