package com.prabhix.platform.observability.taxonomy;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Typed catalogue of every operational event the platform may emit.
 *
 * <p>Callers must pass a {@link LogEventCode} to {@code StructuredEventLogger} — raw
 * strings are not accepted, which keeps the taxonomy enforced at compile time.
 */
public enum LogEventCode {

    // --- Auth / session ---
    AUTH_LOGIN_SUCCEEDED("auth.login.succeeded", LogCategory.AUTH, LogSeverity.INFO, false, true),
    AUTH_LOGIN_FAILED("auth.login.failed", LogCategory.AUTH, LogSeverity.WARN, true, false),
    AUTH_LOGOUT("auth.logout", LogCategory.AUTH, LogSeverity.INFO, false, false),
    AUTH_TOKEN_REFRESHED("auth.token.refreshed", LogCategory.AUTH, LogSeverity.DEBUG, false, false),
    AUTH_TOKEN_REVOKED("auth.token.revoked", LogCategory.AUTH, LogSeverity.INFO, true, false),
    AUTH_MAGIC_LINK_SENT("auth.magic_link.sent", LogCategory.AUTH, LogSeverity.INFO, false, true),
    AUTH_MAGIC_LINK_CONSUMED("auth.magic_link.consumed", LogCategory.AUTH, LogSeverity.INFO, false, false),
    AUTH_OTP_SENT("auth.otp.sent", LogCategory.AUTH, LogSeverity.INFO, false, true),
    AUTH_OTP_FAILED("auth.otp.failed", LogCategory.AUTH, LogSeverity.WARN, true, false),
    AUTH_PASSWORD_RESET_REQUESTED("auth.password.reset_requested", LogCategory.AUTH, LogSeverity.INFO, false, true),
    AUTH_PASSWORD_RESET_COMPLETED("auth.password.reset_completed", LogCategory.AUTH, LogSeverity.INFO, true, false),
    AUTH_ACCOUNT_LOCKED("auth.account.locked", LogCategory.AUTH, LogSeverity.WARN, true, false),
    AUTH_SSO_STARTED("auth.sso.started", LogCategory.AUTH, LogSeverity.INFO, false, false),
    AUTH_SSO_COMPLETED("auth.sso.completed", LogCategory.AUTH, LogSeverity.INFO, false, false),
    AUTH_SSO_FAILED("auth.sso.failed", LogCategory.AUTH, LogSeverity.WARN, true, false),

    // --- Organization / RBAC / membership ---
    ORG_CREATED("org.created", LogCategory.ORG, LogSeverity.INFO, false, false),
    ORG_UPDATED("org.updated", LogCategory.ORG, LogSeverity.INFO, false, false),
    ORG_DELETED("org.deleted", LogCategory.ORG, LogSeverity.WARN, true, false),
    ORG_MEMBER_INVITED("org.member.invited", LogCategory.ORG, LogSeverity.INFO, false, true),
    ORG_MEMBER_JOINED("org.member.joined", LogCategory.ORG, LogSeverity.INFO, false, false),
    ORG_MEMBER_REMOVED("org.member.removed", LogCategory.ORG, LogSeverity.WARN, true, false),
    ORG_ROLE_ASSIGNED("org.role.assigned", LogCategory.ORG, LogSeverity.INFO, true, false),
    ORG_ROLE_UPDATED("org.role.updated", LogCategory.ORG, LogSeverity.INFO, true, false),
    ORG_PERMISSION_DENIED("org.permission.denied", LogCategory.ORG, LogSeverity.WARN, true, false),
    ORG_API_KEY_CREATED("org.api_key.created", LogCategory.ORG, LogSeverity.INFO, true, false),
    ORG_API_KEY_REVOKED("org.api_key.revoked", LogCategory.ORG, LogSeverity.WARN, true, false),
    ORG_TEAM_CREATED("org.team.created", LogCategory.ORG, LogSeverity.INFO, false, false),
    ORG_TEAM_UPDATED("org.team.updated", LogCategory.ORG, LogSeverity.INFO, false, false),

    // --- Mail inbound / outbound / helpdesk ---
    MAIL_OUTBOUND_QUEUED("mail.outbound.queued", LogCategory.MAIL, LogSeverity.DEBUG, false, true),
    MAIL_OUTBOUND_SENT("mail.outbound.sent", LogCategory.MAIL, LogSeverity.INFO, false, true),
    MAIL_OUTBOUND_FAILED("mail.outbound.failed", LogCategory.MAIL, LogSeverity.ERROR, false, true),
    MAIL_OUTBOUND_BOUNCED("mail.outbound.bounced", LogCategory.MAIL, LogSeverity.WARN, false, true),
    MAIL_OUTBOUND_SUPPRESSED("mail.outbound.suppressed", LogCategory.MAIL, LogSeverity.INFO, false, true),
    MAIL_INBOUND_RECEIVED("mail.inbound.received", LogCategory.MAIL, LogSeverity.INFO, false, true),
    MAIL_INBOUND_REJECTED("mail.inbound.rejected", LogCategory.MAIL, LogSeverity.WARN, false, true),
    MAIL_THREAD_CREATED("mail.thread.created", LogCategory.MAIL, LogSeverity.INFO, false, false),
    MAIL_THREAD_ASSIGNED("mail.thread.assigned", LogCategory.MAIL, LogSeverity.INFO, false, false),
    MAIL_THREAD_CLOSED("mail.thread.closed", LogCategory.MAIL, LogSeverity.INFO, false, false),
    MAIL_MAILBOX_CREATED("mail.mailbox.created", LogCategory.MAIL, LogSeverity.INFO, false, false),
    MAIL_DOMAIN_VERIFIED("mail.domain.verified", LogCategory.MAIL, LogSeverity.INFO, false, false),
    MAIL_DOMAIN_VERIFICATION_FAILED("mail.domain.verification_failed", LogCategory.MAIL, LogSeverity.WARN, false, false),
    MAIL_TEMPLATE_RENDERED("mail.template.rendered", LogCategory.MAIL, LogSeverity.DEBUG, false, false),
    MAIL_TRACKING_OPENED("mail.tracking.opened", LogCategory.MAIL, LogSeverity.DEBUG, false, true),
    MAIL_TRACKING_CLICKED("mail.tracking.clicked", LogCategory.MAIL, LogSeverity.DEBUG, false, true),
    MAIL_SLA_BREACHED("mail.sla.breached", LogCategory.MAIL, LogSeverity.WARN, false, false),

    // --- Billing / payments ---
    BILLING_SUBSCRIPTION_CREATED("billing.subscription.created", LogCategory.BILLING, LogSeverity.INFO, false, false),
    BILLING_SUBSCRIPTION_CHANGED("billing.subscription.changed", LogCategory.BILLING, LogSeverity.INFO, false, false),
    BILLING_SUBSCRIPTION_CANCELLED("billing.subscription.cancelled", LogCategory.BILLING, LogSeverity.WARN, false, false),
    BILLING_PAYMENT_INITIATED("billing.payment.initiated", LogCategory.BILLING, LogSeverity.INFO, false, false),
    BILLING_PAYMENT_CAPTURED("billing.payment.captured", LogCategory.BILLING, LogSeverity.INFO, false, false),
    BILLING_PAYMENT_FAILED("billing.payment.failed", LogCategory.BILLING, LogSeverity.ERROR, false, false),
    BILLING_PAYMENT_REFUNDED("billing.payment.refunded", LogCategory.BILLING, LogSeverity.INFO, false, false),
    BILLING_INVOICE_ISSUED("billing.invoice.issued", LogCategory.BILLING, LogSeverity.INFO, false, false),
    BILLING_WEBHOOK_RECEIVED("billing.webhook.received", LogCategory.BILLING, LogSeverity.DEBUG, false, false),
    BILLING_WEBHOOK_REJECTED("billing.webhook.rejected", LogCategory.BILLING, LogSeverity.WARN, true, false),
    BILLING_ENTITLEMENT_CHANGED("billing.entitlement.changed", LogCategory.BILLING, LogSeverity.INFO, false, false),

    // --- Commerce ---
    COMMERCE_PRODUCT_PUBLISHED("commerce.product.published", LogCategory.COMMERCE, LogSeverity.INFO, false, false),
    COMMERCE_CART_UPDATED("commerce.cart.updated", LogCategory.COMMERCE, LogSeverity.DEBUG, false, false),
    COMMERCE_CHECKOUT_STARTED("commerce.checkout.started", LogCategory.COMMERCE, LogSeverity.INFO, false, false),
    COMMERCE_ORDER_CREATED("commerce.order.created", LogCategory.COMMERCE, LogSeverity.INFO, false, false),
    COMMERCE_ORDER_PAID("commerce.order.paid", LogCategory.COMMERCE, LogSeverity.INFO, false, false),
    COMMERCE_ORDER_FULFILLED("commerce.order.fulfilled", LogCategory.COMMERCE, LogSeverity.INFO, false, false),
    COMMERCE_ORDER_CANCELLED("commerce.order.cancelled", LogCategory.COMMERCE, LogSeverity.WARN, false, false),
    COMMERCE_ORDER_REFUNDED("commerce.order.refunded", LogCategory.COMMERCE, LogSeverity.INFO, false, false),
    COMMERCE_STOCK_RESERVED("commerce.stock.reserved", LogCategory.COMMERCE, LogSeverity.DEBUG, false, false),
    COMMERCE_STOCK_RELEASED("commerce.stock.released", LogCategory.COMMERCE, LogSeverity.DEBUG, false, false),
    COMMERCE_DOWNLOAD_ISSUED("commerce.download.issued", LogCategory.COMMERCE, LogSeverity.INFO, false, false),
    COMMERCE_WEBHOOK_RECEIVED("commerce.webhook.received", LogCategory.COMMERCE, LogSeverity.DEBUG, false, false),
    COMMERCE_DISCOUNT_APPLIED("commerce.discount.applied", LogCategory.COMMERCE, LogSeverity.INFO, false, false),

    // --- Chat ---
    CHAT_CONVERSATION_STARTED("chat.conversation.started", LogCategory.CHAT, LogSeverity.INFO, false, true),
    CHAT_CONVERSATION_ASSIGNED("chat.conversation.assigned", LogCategory.CHAT, LogSeverity.INFO, false, false),
    CHAT_CONVERSATION_CLOSED("chat.conversation.closed", LogCategory.CHAT, LogSeverity.INFO, false, false),
    CHAT_MESSAGE_RECEIVED("chat.message.received", LogCategory.CHAT, LogSeverity.DEBUG, false, true),
    CHAT_MESSAGE_SENT("chat.message.sent", LogCategory.CHAT, LogSeverity.DEBUG, false, true),
    CHAT_OFFLINE_MESSAGE("chat.offline.message", LogCategory.CHAT, LogSeverity.INFO, false, true),
    CHAT_QUEUE_WAIT("chat.queue.wait", LogCategory.CHAT, LogSeverity.INFO, false, false),
    CHAT_SETTINGS_UPDATED("chat.settings.updated", LogCategory.CHAT, LogSeverity.INFO, false, false),

    // --- Visitor tracking ---
    VISITOR_SESSION_STARTED("visitor.session.started", LogCategory.VISITOR, LogSeverity.DEBUG, false, true),
    VISITOR_PAGE_VIEW("visitor.page.view", LogCategory.VISITOR, LogSeverity.DEBUG, false, true),
    VISITOR_IDENTIFIED("visitor.identified", LogCategory.VISITOR, LogSeverity.INFO, false, true),
    VISITOR_CONSENT_GRANTED("visitor.consent.granted", LogCategory.VISITOR, LogSeverity.INFO, false, true),
    VISITOR_CONSENT_REVOKED("visitor.consent.revoked", LogCategory.VISITOR, LogSeverity.INFO, false, true),
    VISITOR_RETENTION_PURGED("visitor.retention.purged", LogCategory.VISITOR, LogSeverity.INFO, false, false),

    // --- Files / storage ---
    FILE_UPLOADED("file.uploaded", LogCategory.FILE, LogSeverity.INFO, false, false),
    FILE_DOWNLOADED("file.downloaded", LogCategory.FILE, LogSeverity.INFO, false, false),
    FILE_DELETED("file.deleted", LogCategory.FILE, LogSeverity.INFO, false, false),
    FILE_SCAN_CLEAN("file.scan.clean", LogCategory.FILE, LogSeverity.DEBUG, false, false),
    FILE_SCAN_INFECTED("file.scan.infected", LogCategory.FILE, LogSeverity.WARN, true, false),
    FILE_SCAN_FAILED("file.scan.failed", LogCategory.FILE, LogSeverity.ERROR, false, false),

    // --- AI ---
    AI_COMPLETION_REQUESTED("ai.completion.requested", LogCategory.AI, LogSeverity.DEBUG, false, false),
    AI_COMPLETION_SUCCEEDED("ai.completion.succeeded", LogCategory.AI, LogSeverity.INFO, false, false),
    AI_COMPLETION_FAILED("ai.completion.failed", LogCategory.AI, LogSeverity.ERROR, false, false),
    AI_COMPLETION_BLOCKED("ai.completion.blocked", LogCategory.AI, LogSeverity.WARN, true, false),
    AI_QUOTA_EXCEEDED("ai.quota.exceeded", LogCategory.AI, LogSeverity.WARN, false, false),
    AI_STREAM_STARTED("ai.stream.started", LogCategory.AI, LogSeverity.DEBUG, false, false),
    AI_STREAM_COMPLETED("ai.stream.completed", LogCategory.AI, LogSeverity.DEBUG, false, false),
    AI_SETTINGS_UPDATED("ai.settings.updated", LogCategory.AI, LogSeverity.INFO, false, false),

    // --- Jobs / schedulers ---
    JOB_STARTED("job.started", LogCategory.JOB, LogSeverity.DEBUG, false, false),
    JOB_COMPLETED("job.completed", LogCategory.JOB, LogSeverity.DEBUG, false, false),
    JOB_FAILED("job.failed", LogCategory.JOB, LogSeverity.ERROR, false, false),
    JOB_OUTBOX_DRAIN("job.outbox.drain", LogCategory.JOB, LogSeverity.DEBUG, false, false),
    JOB_RETENTION_RUN("job.retention.run", LogCategory.JOB, LogSeverity.INFO, false, false),
    JOB_PARTITION_MAINTENANCE("job.partition.maintenance", LogCategory.JOB, LogSeverity.INFO, false, false),

    // --- Integrations ---
    INTEGRATION_WEBHOOK_DELIVERED("integration.webhook.delivered", LogCategory.INTEGRATION, LogSeverity.INFO, false, false),
    INTEGRATION_WEBHOOK_FAILED("integration.webhook.failed", LogCategory.INTEGRATION, LogSeverity.ERROR, false, false),
    INTEGRATION_PROVIDER_ERROR("integration.provider.error", LogCategory.INTEGRATION, LogSeverity.ERROR, false, false),

    // --- Security ---
    SECURITY_CROSS_TENANT_BLOCKED("security.cross_tenant.blocked", LogCategory.SECURITY, LogSeverity.WARN, true, false),
    SECURITY_RATE_LIMITED("security.rate_limited", LogCategory.SECURITY, LogSeverity.WARN, true, false),
    SECURITY_INVALID_TOKEN("security.invalid_token", LogCategory.SECURITY, LogSeverity.WARN, true, false),
    SECURITY_SUSPICIOUS_REQUEST("security.suspicious.request", LogCategory.SECURITY, LogSeverity.WARN, true, false),

    // --- Platform / HTTP / system health ---
    HTTP_REQUEST_COMPLETED("http.request.completed", LogCategory.PLATFORM, LogSeverity.INFO, false, false),
    HTTP_REQUEST_SLOW("http.request.slow", LogCategory.PLATFORM, LogSeverity.WARN, false, false),
    HTTP_REQUEST_ERROR("http.request.error", LogCategory.PLATFORM, LogSeverity.ERROR, false, false),
    PLATFORM_STARTUP("platform.startup", LogCategory.PLATFORM, LogSeverity.INFO, false, false),
    PLATFORM_SHUTDOWN("platform.shutdown", LogCategory.PLATFORM, LogSeverity.INFO, false, false),
    PLATFORM_HEALTH_DEGRADED("platform.health.degraded", LogCategory.PLATFORM, LogSeverity.WARN, false, false),
    PLATFORM_UNHANDLED_ERROR("platform.unhandled.error", LogCategory.PLATFORM, LogSeverity.ERROR, false, false);

    private static final Map<String, LogEventCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(LogEventCode::code, Function.identity()));

    private final String code;
    private final LogCategory category;
    private final LogSeverity severity;
    private final boolean securitySensitive;
    private final boolean carriesPii;

    LogEventCode(String code,
                 LogCategory category,
                 LogSeverity severity,
                 boolean securitySensitive,
                 boolean carriesPii) {
        this.code = code;
        this.category = category;
        this.severity = severity;
        this.securitySensitive = securitySensitive;
        this.carriesPii = carriesPii;
    }

    public String code() {
        return code;
    }

    public LogCategory category() {
        return category;
    }

    public LogSeverity severity() {
        return severity;
    }

    public boolean securitySensitive() {
        return securitySensitive;
    }

    public boolean carriesPii() {
        return carriesPii;
    }

    public static LogEventCode fromCode(String code) {
        LogEventCode found = BY_CODE.get(code);
        if (found == null) {
            throw new IllegalArgumentException("Unknown event code: " + code);
        }
        return found;
    }
}
