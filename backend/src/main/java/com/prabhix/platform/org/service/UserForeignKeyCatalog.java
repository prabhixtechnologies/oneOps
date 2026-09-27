package com.prabhix.platform.org.service;

import java.util.List;

/**
 * Product tables that reference {@code users.id}, used for invite-era duplicate reconciliation.
 */
final class UserForeignKeyCatalog {

    private UserForeignKeyCatalog() {
    }

    record ColumnRef(String table, String column) {
    }

    /**
     * Pairs of (scope column, user column) where both users must not occupy the same scope value.
     */
    record UniqueScope(String table, String scopeColumn, String userColumn) {
    }

    static final List<ColumnRef> UPDATABLE_COLUMNS = List.of(
            new ColumnRef("organization_memberships", "user_id"),
            new ColumnRef("organization_memberships", "invited_by"),
            new ColumnRef("team_members", "user_id"),
            new ColumnRef("teams", "lead_user_id"),
            new ColumnRef("invitations", "invited_by"),
            new ColumnRef("invitations", "accepted_by"),
            new ColumnRef("notifications", "user_id"),
            new ColumnRef("push_tokens", "user_id"),
            new ColumnRef("device_sessions", "user_id"),
            new ColumnRef("refresh_tokens", "user_id"),
            new ColumnRef("auth_identities", "user_id"),
            new ColumnRef("auth_challenges", "user_id"),
            new ColumnRef("platform_staff_roles", "user_id"),
            new ColumnRef("mail_mailboxes", "owner_user_id"),
            new ColumnRef("mail_mailbox_members", "user_id"),
            new ColumnRef("mail_messages", "sent_by_user_id"),
            new ColumnRef("mail_threads", "assignee_user_id"),
            new ColumnRef("mail_thread_drafts", "author_user_id"),
            new ColumnRef("mail_thread_notes", "author_user_id"),
            new ColumnRef("mail_thread_events", "actor_user_id"),
            new ColumnRef("mail_thread_flags", "user_id"),
            new ColumnRef("commerce_orders", "customer_user_id"),
            new ColumnRef("visitors", "identified_user_id"),
            new ColumnRef("audit_logs", "actor_user_id"),
            new ColumnRef("event_logs", "actor_user_id"));

    static final List<UniqueScope> UNIQUE_SCOPES = List.of(
            new UniqueScope("organization_memberships", "organization_id", "user_id"),
            new UniqueScope("team_members", "team_id", "user_id"),
            new UniqueScope("mail_mailbox_members", "mailbox_id", "user_id"),
            new UniqueScope("mail_thread_flags", "thread_id", "user_id"),
            new UniqueScope("platform_staff_roles", "role", "user_id"),
            new UniqueScope("device_sessions", "device_id", "user_id"));
}
