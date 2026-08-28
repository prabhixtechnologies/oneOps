package com.prabhix.platform.security.rbac;

import java.util.EnumSet;
import java.util.Set;

import static com.prabhix.platform.security.rbac.Permission.*;

/**
 * Roles seeded into every new organization.
 *
 * <p>These are the defaults a customer starts with. They can add custom roles on top, but
 * cannot edit or delete these, so there is always a known-good ladder to fall back to.
 *
 * <p>The permission sets here are the source of truth. {@code V3__rbac_seed.sql} inserts the
 * same data for a fresh database, and {@code RolePermissionSynchronizer} reconciles existing
 * organizations on startup, so adding a permission to a system role rolls out everywhere.
 */
public enum SystemRole {

    /** Full control including deleting the organization. Cannot be removed from the last holder. */
    OWNER("Owner", "Complete control over the organization, its billing, and its data",
            EnumSet.copyOf(Permission.assignable())),

    /** Everything an owner can do except destroy the organization. */
    ADMIN("Admin", "Manages people, inboxes, and settings, but cannot delete the organization",
            EnumSet.of(
                    ORG_READ, ORG_UPDATE,
                    ORG_MEMBER_READ, ORG_MEMBER_INVITE, ORG_MEMBER_UPDATE, ORG_MEMBER_REMOVE,
                    ORG_ROLE_READ, ORG_ROLE_MANAGE, ORG_TEAM_READ, ORG_TEAM_MANAGE,
                    ORG_API_KEY_MANAGE,
                    MAIL_READ, MAIL_READ_ALL, MAIL_SEND, MAIL_ASSIGN, MAIL_THREAD_UPDATE,
                    MAIL_THREAD_DELETE, MAIL_NOTE_WRITE,
                    MAIL_MAILBOX_READ, MAIL_MAILBOX_MANAGE,
                    MAIL_DOMAIN_READ, MAIL_DOMAIN_MANAGE,
                    MAIL_TEMPLATE_READ, MAIL_TEMPLATE_MANAGE, MAIL_SUPPRESSION_MANAGE,
                    BILLING_READ, BILLING_MANAGE, BILLING_INVOICE_DOWNLOAD,
                    FILE_READ, FILE_UPLOAD, FILE_DELETE,
                    VISITOR_READ, VISITOR_ANALYTICS, VISITOR_MANAGE,
                    CHAT_READ, CHAT_READ_ALL, CHAT_REPLY, CHAT_ASSIGN, CHAT_MANAGE,
                    COMMERCE_CATALOG_READ, COMMERCE_CATALOG_MANAGE,
                    COMMERCE_ORDER_READ, COMMERCE_ORDER_MANAGE, COMMERCE_ORDER_REFUND,
                    COMMERCE_CUSTOMER_READ, COMMERCE_DISCOUNT_MANAGE, COMMERCE_SETTINGS_MANAGE,
                    AI_USE, AI_CONFIGURE, AI_USAGE_READ,
                    LOG_READ, LOG_EXPORT,
                    AUDIT_READ)),

    /** Runs a support or sales team: sees every inbox and can reshape teams, but not billing. */
    MANAGER("Manager", "Runs a team: full inbox oversight, assignment, and reporting",
            EnumSet.of(
                    ORG_READ, ORG_MEMBER_READ, ORG_TEAM_READ, ORG_TEAM_MANAGE, ORG_ROLE_READ,
                    MAIL_READ, MAIL_READ_ALL, MAIL_SEND, MAIL_ASSIGN, MAIL_THREAD_UPDATE,
                    MAIL_NOTE_WRITE, MAIL_MAILBOX_READ, MAIL_MAILBOX_MANAGE,
                    MAIL_TEMPLATE_READ, MAIL_TEMPLATE_MANAGE,
                    MAIL_DOMAIN_READ,
                    BILLING_READ,
                    FILE_READ, FILE_UPLOAD,
                    VISITOR_READ, VISITOR_ANALYTICS,
                    CHAT_READ, CHAT_READ_ALL, CHAT_REPLY, CHAT_ASSIGN,
                    COMMERCE_CATALOG_READ, COMMERCE_CATALOG_MANAGE,
                    COMMERCE_ORDER_READ, COMMERCE_ORDER_MANAGE,
                    COMMERCE_CUSTOMER_READ, COMMERCE_DISCOUNT_MANAGE,
                    AI_USE, AI_USAGE_READ,
                    LOG_READ,
                    AUDIT_READ)),

    /** The day-to-day shared-inbox worker. Deliberately has no MAIL_READ_ALL. */
    AGENT("Agent", "Works the shared inboxes they belong to",
            EnumSet.of(
                    ORG_READ, ORG_MEMBER_READ, ORG_TEAM_READ,
                    MAIL_READ, MAIL_SEND, MAIL_ASSIGN, MAIL_THREAD_UPDATE, MAIL_NOTE_WRITE,
                    MAIL_MAILBOX_READ, MAIL_TEMPLATE_READ,
                    FILE_READ, FILE_UPLOAD,
                    VISITOR_READ,
                    CHAT_READ, CHAT_REPLY, CHAT_ASSIGN,
                    COMMERCE_CATALOG_READ, COMMERCE_ORDER_READ, COMMERCE_CUSTOMER_READ,
                    AI_USE)),

    /** Ordinary employee in a large organization: present, but not working tickets. */
    MEMBER("Member", "Standard access for everyone in the organization",
            EnumSet.of(
                    ORG_READ, ORG_MEMBER_READ, ORG_TEAM_READ,
                    MAIL_READ, MAIL_MAILBOX_READ,
                    FILE_READ, FILE_UPLOAD)),

    /** Read-only. Useful for auditors, executives, and integrations. */
    VIEWER("Viewer", "Read-only access",
            EnumSet.of(ORG_READ, ORG_MEMBER_READ, ORG_TEAM_READ, MAIL_READ, FILE_READ,
                    VISITOR_READ, VISITOR_ANALYTICS, CHAT_READ,
                    COMMERCE_CATALOG_READ, COMMERCE_ORDER_READ, COMMERCE_CUSTOMER_READ,
                    AI_USAGE_READ, LOG_READ));

    private final String displayName;
    private final String description;
    private final Set<Permission> permissions;

    SystemRole(String displayName, String description, Set<Permission> permissions) {
        this.displayName = displayName;
        this.description = description;
        this.permissions = Set.copyOf(permissions);
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }
}
