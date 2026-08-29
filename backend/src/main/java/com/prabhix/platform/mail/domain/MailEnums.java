package com.prabhix.platform.mail.domain;

public final class MailEnums {

    private MailEnums() {
    }

    public enum DomainStatus {
        PENDING, VERIFYING, VERIFIED, FAILED, DISABLED
    }

    public enum DomainMode {
        SELF_HOSTED, EXTERNAL_IMAP, RELAY_ONLY
    }

    public enum MailboxKind {
        SHARED, PERSONAL, SYSTEM
    }

    public enum MailboxStatus {
        ACTIVE, PAUSED, ARCHIVED
    }

    public enum MemberAccessLevel {
        MEMBER, LEAD
    }

    /**
     * Where a thread is filed, which is a different question from what its {@link ThreadStatus} is: the
     * status is about the work and the folder is about where the person put it, and the two move
     * independently. The named kinds exist so code can ask for "this mailbox's trash" without matching on
     * a name the owner is free to rename or translate; CUSTOM is everything a person made themselves.
     */
    public enum FolderKind {
        INBOX, SENT, DRAFTS, ARCHIVE, TRASH, SPAM, CUSTOM;

        public boolean isSystem() {
            return this != CUSTOM;
        }
    }

    public enum ThreadStatus {
        OPEN, PENDING_CUSTOMER, ON_HOLD, RESOLVED, CLOSED, SPAM, TRASH
    }

    public enum Priority {
        LOW, NORMAL, HIGH, URGENT
    }

    public enum MessageDirection {
        INBOUND, OUTBOUND
    }

    public enum DeliveryStatus {
        RECEIVED, QUEUED, SENDING, SENT, FAILED, BOUNCED, DRAFT
    }

    public enum ThreadEventType {
        CREATED, MESSAGE_RECEIVED, MESSAGE_SENT, ASSIGNED, UNASSIGNED,
        STATUS_CHANGED, PRIORITY_CHANGED, TAG_ADDED, TAG_REMOVED,
        NOTE_ADDED, SLA_BREACHED, MERGED, MOVED, AUTO_REPLIED, RULE_APPLIED
    }

    public enum MatchMode {
        ALL, ANY
    }

    public enum InboundSource {
        IMAP, LMTP, WEBHOOK, MANUAL
    }

    public enum InboundRawStatus {
        PENDING, PROCESSING, PROCESSED, FAILED, SKIPPED_DUPLICATE
    }

    public enum ReplyMode {
        REPLY, REPLY_ALL, FORWARD
    }

    public enum TemplateCategory {
        TRANSACTIONAL, NOTIFICATION, MARKETING
    }

    public enum OutboxStatus {
        PENDING, CLAIMED, SENDING, SENT, FAILED, DEAD, CANCELLED, SUPPRESSED
    }

    public enum SuppressionReason {
        HARD_BOUNCE, SOFT_BOUNCE, COMPLAINT, UNSUBSCRIBE, MANUAL, INVALID
    }

    public enum DeliveryEventType {
        QUEUED, SENT, DELIVERED, DEFERRED, BOUNCED, COMPLAINED,
        OPENED, CLICKED, UNSUBSCRIBED, FAILED
    }

    public enum WebhookProvider {
        SES_SNS
    }

    public enum WebhookEventStatus {
        PENDING, PROCESSED, FAILED, IGNORED
    }
}
