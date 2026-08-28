package com.prabhix.platform.security.rbac;

/**
 * SpEL expressions for {@code @PreAuthorize}.
 *
 * <p>{@code @PreAuthorize} needs a compile-time constant, so the expression cannot be built
 * from {@link Permission} at runtime. Centralising the strings here keeps typos out of
 * annotations — a misspelled authority in an inline expression fails open-looking (it just
 * never matches) and is easy to miss in review.
 *
 * <p>{@code PermissionConstantsTest} asserts that this class has one constant per
 * {@link Permission}, so the two cannot drift apart.
 */
public final class Authorize {

    private static final String HAS = "hasAuthority('";
    private static final String END = "')";

    // --- Organization ---
    public static final String ORG_READ = HAS + "ORG_READ" + END;
    public static final String ORG_UPDATE = HAS + "ORG_UPDATE" + END;
    public static final String ORG_DELETE = HAS + "ORG_DELETE" + END;
    public static final String ORG_MEMBER_READ = HAS + "ORG_MEMBER_READ" + END;
    public static final String ORG_MEMBER_INVITE = HAS + "ORG_MEMBER_INVITE" + END;
    public static final String ORG_MEMBER_UPDATE = HAS + "ORG_MEMBER_UPDATE" + END;
    public static final String ORG_MEMBER_REMOVE = HAS + "ORG_MEMBER_REMOVE" + END;
    public static final String ORG_ROLE_READ = HAS + "ORG_ROLE_READ" + END;
    public static final String ORG_ROLE_MANAGE = HAS + "ORG_ROLE_MANAGE" + END;
    public static final String ORG_TEAM_READ = HAS + "ORG_TEAM_READ" + END;
    public static final String ORG_TEAM_MANAGE = HAS + "ORG_TEAM_MANAGE" + END;
    public static final String ORG_API_KEY_MANAGE = HAS + "ORG_API_KEY_MANAGE" + END;

    // --- Mail ---
    public static final String MAIL_READ = HAS + "MAIL_READ" + END;
    public static final String MAIL_READ_ALL = HAS + "MAIL_READ_ALL" + END;
    public static final String MAIL_SEND = HAS + "MAIL_SEND" + END;
    public static final String MAIL_ASSIGN = HAS + "MAIL_ASSIGN" + END;
    public static final String MAIL_THREAD_UPDATE = HAS + "MAIL_THREAD_UPDATE" + END;
    public static final String MAIL_THREAD_DELETE = HAS + "MAIL_THREAD_DELETE" + END;
    public static final String MAIL_NOTE_WRITE = HAS + "MAIL_NOTE_WRITE" + END;
    public static final String MAIL_MAILBOX_READ = HAS + "MAIL_MAILBOX_READ" + END;
    public static final String MAIL_MAILBOX_MANAGE = HAS + "MAIL_MAILBOX_MANAGE" + END;
    public static final String MAIL_DOMAIN_READ = HAS + "MAIL_DOMAIN_READ" + END;
    public static final String MAIL_DOMAIN_MANAGE = HAS + "MAIL_DOMAIN_MANAGE" + END;
    public static final String MAIL_TEMPLATE_READ = HAS + "MAIL_TEMPLATE_READ" + END;
    public static final String MAIL_TEMPLATE_MANAGE = HAS + "MAIL_TEMPLATE_MANAGE" + END;
    public static final String MAIL_SUPPRESSION_MANAGE = HAS + "MAIL_SUPPRESSION_MANAGE" + END;

    // --- Billing ---
    public static final String BILLING_READ = HAS + "BILLING_READ" + END;
    public static final String BILLING_MANAGE = HAS + "BILLING_MANAGE" + END;
    public static final String BILLING_INVOICE_DOWNLOAD = HAS + "BILLING_INVOICE_DOWNLOAD" + END;

    // --- Files ---
    public static final String FILE_READ = HAS + "FILE_READ" + END;
    public static final String FILE_UPLOAD = HAS + "FILE_UPLOAD" + END;
    public static final String FILE_DELETE = HAS + "FILE_DELETE" + END;

    // --- Audit ---
    public static final String AUDIT_READ = HAS + "AUDIT_READ" + END;

    // --- Visitor ---
    public static final String VISITOR_READ = HAS + "VISITOR_READ" + END;
    public static final String VISITOR_ANALYTICS = HAS + "VISITOR_ANALYTICS" + END;
    public static final String VISITOR_MANAGE = HAS + "VISITOR_MANAGE" + END;

    // --- Chat ---
    public static final String CHAT_READ = HAS + "CHAT_READ" + END;
    public static final String CHAT_READ_ALL = HAS + "CHAT_READ_ALL" + END;
    public static final String CHAT_REPLY = HAS + "CHAT_REPLY" + END;
    public static final String CHAT_ASSIGN = HAS + "CHAT_ASSIGN" + END;
    public static final String CHAT_MANAGE = HAS + "CHAT_MANAGE" + END;

    // --- Commerce ---
    public static final String COMMERCE_CATALOG_READ = HAS + "COMMERCE_CATALOG_READ" + END;
    public static final String COMMERCE_CATALOG_MANAGE = HAS + "COMMERCE_CATALOG_MANAGE" + END;
    public static final String COMMERCE_ORDER_READ = HAS + "COMMERCE_ORDER_READ" + END;
    public static final String COMMERCE_ORDER_MANAGE = HAS + "COMMERCE_ORDER_MANAGE" + END;
    public static final String COMMERCE_ORDER_REFUND = HAS + "COMMERCE_ORDER_REFUND" + END;
    public static final String COMMERCE_CUSTOMER_READ = HAS + "COMMERCE_CUSTOMER_READ" + END;
    public static final String COMMERCE_DISCOUNT_MANAGE = HAS + "COMMERCE_DISCOUNT_MANAGE" + END;
    public static final String COMMERCE_SETTINGS_MANAGE = HAS + "COMMERCE_SETTINGS_MANAGE" + END;

    // --- AI ---
    public static final String AI_USE = HAS + "AI_USE" + END;
    public static final String AI_CONFIGURE = HAS + "AI_CONFIGURE" + END;
    public static final String AI_USAGE_READ = HAS + "AI_USAGE_READ" + END;

    // --- Observability ---
    public static final String LOG_READ = HAS + "LOG_READ" + END;
    public static final String LOG_EXPORT = HAS + "LOG_EXPORT" + END;

    // --- Site pipeline ---
    public static final String SITE_LEAD_READ = HAS + "SITE_LEAD_READ" + END;
    public static final String SITE_LEAD_MANAGE = HAS + "SITE_LEAD_MANAGE" + END;
    public static final String SITE_SUBSCRIBER_READ = HAS + "SITE_SUBSCRIBER_READ" + END;
    public static final String SITE_APPLICATION_READ = HAS + "SITE_APPLICATION_READ" + END;
    public static final String SITE_APPLICATION_MANAGE = HAS + "SITE_APPLICATION_MANAGE" + END;

    // --- Platform ---
    public static final String PLATFORM_ADMIN = HAS + "PLATFORM_ADMIN" + END;

    /** Any authenticated caller, with or without an active organization. */
    public static final String AUTHENTICATED = "isAuthenticated()";

    private Authorize() {
    }
}
