package com.prabhix.platform.security.rbac;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every capability the platform can grant.
 *
 * <p>Code authorizes on permissions, never on roles. Roles are just bundles that customers
 * can reshape; permissions are the fixed vocabulary, so a customer inventing a "Billing
 * Clerk" role needs no code change.
 *
 * <p>The enum name is the authority string embedded in the JWT and checked by
 * {@code @PreAuthorize}. Renaming one is a breaking change and needs a migration that
 * rewrites {@code role_permissions}.
 */
public enum Permission {

    // --- Organization ---
    ORG_READ("View organization profile and settings"),
    ORG_UPDATE("Change organization name, branding, and preferences"),
    ORG_DELETE("Permanently delete the organization"),
    ORG_MEMBER_READ("View the member directory"),
    ORG_MEMBER_INVITE("Invite people to the organization"),
    ORG_MEMBER_UPDATE("Change a member's role or teams"),
    ORG_MEMBER_REMOVE("Remove a member from the organization"),
    ORG_ROLE_READ("View roles and their permissions"),
    ORG_ROLE_MANAGE("Create, edit, and delete custom roles"),
    ORG_TEAM_READ("View teams"),
    ORG_TEAM_MANAGE("Create, edit, and delete teams"),
    ORG_API_KEY_MANAGE("Issue and revoke API keys"),

    // --- Mail: shared inboxes and helpdesk ---
    MAIL_READ("Read threads in inboxes you belong to"),
    MAIL_READ_ALL("Read threads in every inbox, including ones you do not belong to"),
    MAIL_SEND("Reply to and forward mail"),
    MAIL_ASSIGN("Assign threads to people or teams"),
    MAIL_THREAD_UPDATE("Change thread status, priority, and tags"),
    MAIL_THREAD_DELETE("Delete threads"),
    MAIL_NOTE_WRITE("Add internal notes to a thread"),
    MAIL_MAILBOX_READ("View shared inbox configuration"),
    MAIL_MAILBOX_MANAGE("Create and configure shared inboxes, routing rules, and SLA policies"),
    MAIL_DOMAIN_READ("View mail domains and their DNS status"),
    MAIL_DOMAIN_MANAGE("Add, verify, and remove mail domains"),
    MAIL_TEMPLATE_READ("View transactional email templates"),
    MAIL_TEMPLATE_MANAGE("Create and edit transactional email templates"),
    MAIL_SUPPRESSION_MANAGE("View and clear the suppression list"),

    // --- Billing ---
    BILLING_READ("View plan, usage, and invoices"),
    BILLING_MANAGE("Change plan, pay, and update billing details"),
    BILLING_INVOICE_DOWNLOAD("Download invoices"),

    // --- Files ---
    FILE_READ("Download files and attachments"),
    FILE_UPLOAD("Upload files and attachments"),
    FILE_DELETE("Delete files"),

    // --- Audit ---
    AUDIT_READ("View the audit log"),

    // --- Visitor tracking ---
    VISITOR_READ("View live visitors and visitor profiles"),
    VISITOR_ANALYTICS("View visitor analytics and aggregates"),
    VISITOR_MANAGE("Delete visitor data and manage consent"),

    // --- Live chat ---
    CHAT_READ("Read chat conversations assigned to you"),
    CHAT_READ_ALL("Read all chat conversations in the organization"),
    CHAT_REPLY("Send chat messages and internal notes"),
    CHAT_ASSIGN("Assign and transfer chat conversations"),
    CHAT_MANAGE("Configure chat settings and canned replies"),

    // --- Commerce: catalog, orders, fulfilment ---
    COMMERCE_CATALOG_READ("View products, variants, and prices"),
    COMMERCE_CATALOG_MANAGE("Create and edit products, variants, and prices"),
    COMMERCE_ORDER_READ("View customer orders"),
    COMMERCE_ORDER_MANAGE("Fulfil, cancel, and annotate orders"),
    COMMERCE_ORDER_REFUND("Refund orders"),
    COMMERCE_CUSTOMER_READ("View storefront customers"),
    COMMERCE_DISCOUNT_MANAGE("Create and edit discount codes"),
    COMMERCE_SETTINGS_MANAGE("Configure the storefront, tax, and shipping"),

    // --- AI ---
    AI_USE("Use AI assistance in mail, chat, and lead workflows"),
    AI_CONFIGURE("Choose AI providers, models, and prompts"),
    AI_USAGE_READ("View AI usage and cost"),

    // --- Observability ---
    LOG_READ("Search application and business logs"),
    LOG_EXPORT("Export logs"),

    // --- Marketing site pipeline: Prabhix staff only, never granted to a customer role ---
    SITE_LEAD_READ("View marketing site leads"),
    SITE_LEAD_MANAGE("Update lead status and notes"),
    SITE_SUBSCRIBER_READ("View newsletter subscribers"),
    SITE_APPLICATION_READ("View job applications"),
    SITE_APPLICATION_MANAGE("Update application status"),

    // --- Platform administration: Prabhix staff only, never granted to a customer role ---
    PLATFORM_ADMIN("Administer the whole platform across all organizations");

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** Permissions a customer-facing role is allowed to contain. */
    public static Set<Permission> assignable() {
        return Arrays.stream(values())
                .filter(permission -> permission != PLATFORM_ADMIN
                        && permission != SITE_LEAD_READ
                        && permission != SITE_LEAD_MANAGE
                        && permission != SITE_SUBSCRIBER_READ
                        && permission != SITE_APPLICATION_READ
                        && permission != SITE_APPLICATION_MANAGE)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Lenient lookup, so an unknown code in the database is ignored instead of fatal. */
    public static Optional<Permission> parse(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(permission -> permission.name().equalsIgnoreCase(code.trim()))
                .findFirst();
    }
}
