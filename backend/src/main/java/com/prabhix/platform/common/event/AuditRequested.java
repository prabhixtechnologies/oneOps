package com.prabhix.platform.common.event;

import java.util.Map;
import java.util.UUID;

/**
 * "Record this in the audit log." Published by any module; consumed only by {@code audit}.
 *
 * <p>Published, not called, for two reasons: no module needs a dependency on the audit
 * module, and the listener runs after commit, so a slow or failing audit write can never
 * roll back the business action it describes.
 *
 * @param organizationId tenant, or {@code null} for platform-level actions
 * @param actorUserId    who did it, or {@code null} for scheduled and system work
 * @param actorEmail     denormalised so the entry stays readable after a user is deleted
 * @param action         dotted verb, e.g. {@code mail.thread.assigned}
 * @param resourceType   e.g. {@code mail_thread}
 * @param resourceId     the affected row
 * @param resourceLabel  human-readable name, captured now because it may change later
 * @param changes        field to before/after pairs, for update actions
 * @param metadata       anything else worth keeping
 * @param success        false records a rejected attempt, which is often the interesting case
 */
public record AuditRequested(
        UUID organizationId,
        UUID actorUserId,
        String actorEmail,
        String action,
        String resourceType,
        UUID resourceId,
        String resourceLabel,
        Map<String, Object> changes,
        Map<String, Object> metadata,
        boolean success) implements PlatformEvent {

    public AuditRequested {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action is required");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AuditRequested of(UUID organizationId,
                                    UUID actorUserId,
                                    String action,
                                    String resourceType,
                                    UUID resourceId) {
        return new AuditRequested(organizationId, actorUserId, null, action,
                resourceType, resourceId, null, null, Map.of(), true);
    }

    public static AuditRequested labelled(UUID organizationId,
                                          UUID actorUserId,
                                          String action,
                                          String resourceType,
                                          UUID resourceId,
                                          String resourceLabel) {
        return new AuditRequested(organizationId, actorUserId, null, action,
                resourceType, resourceId, resourceLabel, null, Map.of(), true);
    }

    public static AuditRequested changed(UUID organizationId,
                                         UUID actorUserId,
                                         String action,
                                         String resourceType,
                                         UUID resourceId,
                                         Map<String, Object> changes) {
        return new AuditRequested(organizationId, actorUserId, null, action,
                resourceType, resourceId, null, changes, Map.of(), true);
    }

    public static AuditRequested failure(UUID organizationId,
                                         UUID actorUserId,
                                         String action,
                                         String reason) {
        return new AuditRequested(organizationId, actorUserId, null, action,
                null, null, null, null, Map.of("reason", reason), false);
    }
}
