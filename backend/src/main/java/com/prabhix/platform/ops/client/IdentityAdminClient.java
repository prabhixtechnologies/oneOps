package com.prabhix.platform.ops.client;

import com.prabhix.identity.client.IdentityAdmin;
import com.prabhix.identity.client.IdentityAdmin.Actor;
import com.prabhix.identity.client.IdentityAdmin.AuthEvent;
import com.prabhix.identity.client.IdentityAdmin.ClientSummary;
import com.prabhix.identity.client.IdentityAdmin.EventQuery;
import com.prabhix.identity.client.IdentityAdmin.KeySummary;
import com.prabhix.identity.client.IdentityAdmin.Page;
import com.prabhix.identity.client.IdentityAdmin.UserDetail;
import com.prabhix.identity.client.IdentityAdmin.UserSearch;
import com.prabhix.identity.client.IdentityAdmin.UserSummary;
import com.prabhix.identity.client.IdentityClientException;
import com.prabhix.identity.client.IdentityInternalClient;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.ops.domain.StaffRole;
import com.prabhix.platform.ops.service.PlatformStaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Identity's internal admin API, with the staff-role gate that Identity itself cannot enforce.
 *
 * <p>Identity knows who a person is, not whether they may administer the platform. That decision
 * lives here, and it is SECURITY (or OWNER). Every call still names the acting user so Identity's
 * own audit trail records a person.
 */
@Component
@RequiredArgsConstructor
public class IdentityAdminClient {

    private final IdentityInternalClient identity;
    private final PlatformStaffService staff;

    public Page<UserSummary> searchUsers(UUID actorId, UserSearch search) {
        return call(() -> identity.searchUsers(search, actor(actorId, null)));
    }

    public UserDetail getUser(UUID actorId, UUID userId) {
        return call(() -> identity.getUser(userId, actor(actorId, null)));
    }

    public void disableUser(UUID actorId, UUID userId, String reason) {
        call(() -> {
            identity.disableUser(userId, actor(actorId, reason));
            return null;
        });
    }

    public void enableUser(UUID actorId, UUID userId, String reason) {
        call(() -> {
            identity.enableUser(userId, actor(actorId, reason));
            return null;
        });
    }

    public void unlockUser(UUID actorId, UUID userId, String reason) {
        call(() -> {
            identity.unlockUser(userId, actor(actorId, reason));
            return null;
        });
    }

    public void forcePasswordReset(UUID actorId, UUID userId, String reason) {
        call(() -> {
            identity.forcePasswordReset(userId, actor(actorId, reason));
            return null;
        });
    }

    public void revokeSessions(UUID actorId, UUID userId, String reason) {
        call(() -> {
            identity.revokeSessions(userId, actor(actorId, reason));
            return null;
        });
    }

    public List<ClientSummary> clients(UUID actorId) {
        return call(() -> identity.clients(actor(actorId, null)));
    }

    public List<KeySummary> keys(UUID actorId) {
        return call(() -> identity.keys(actor(actorId, null)));
    }

    public Page<AuthEvent> events(UUID actorId, EventQuery query) {
        return call(() -> identity.events(query, actor(actorId, null)));
    }

    private Actor actor(UUID userId, String reason) {
        staff.requireAny(userId, Set.of(StaffRole.SECURITY));
        return new IdentityAdmin.Actor(userId, reason);
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (IdentityClientException ex) {
            throw map(ex);
        }
    }

    public static ApiException map(IdentityClientException ex) {
        return switch (ex.kind()) {
            case DISABLED, UNAVAILABLE -> ApiException.of(ErrorCode.DEPENDENCY_UNAVAILABLE, ex.getMessage());
            case NOT_FOUND -> ApiException.of(ErrorCode.NOT_FOUND, ex.getMessage());
            case REJECTED -> ApiException.of(ErrorCode.FORBIDDEN, ex.getMessage());
        };
    }
}
