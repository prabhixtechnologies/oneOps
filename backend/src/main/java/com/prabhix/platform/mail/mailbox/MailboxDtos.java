package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.mail.domain.MailEnums;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The shapes a mail client speaks. Separate from {@code ThreadDtos}, which is the helpdesk's language:
 * that one talks about assignees and SLA clocks, and this one talks about folders and stars, and the two
 * views of the same thread want different fields.
 */
public final class MailboxDtos {

    private MailboxDtos() {
    }

    // -----------------------------------------------------------------------------------------------
    // Folders
    // -----------------------------------------------------------------------------------------------

    public record FolderView(
            UUID id,
            UUID mailboxId,
            MailEnums.FolderKind kind,
            String name,
            UUID parentId,
            int sortOrder,
            String colour,
            long threadCount,
            long unreadCount) {
    }

    public record SaveFolderRequest(
            @Size(max = 120) String name,
            UUID parentId,
            Integer sortOrder,
            @Size(max = 9) String colour) {
    }

    public record MoveRequest(@NotEmpty List<UUID> threadIds) {
    }

    // -----------------------------------------------------------------------------------------------
    // Threads, as a mail client sees them
    // -----------------------------------------------------------------------------------------------

    public record MailThreadView(
            UUID id,
            UUID mailboxId,
            UUID folderId,
            String subject,
            String snippet,
            String correspondent,
            String correspondentName,
            int messageCount,
            boolean hasAttachments,
            boolean read,
            boolean starred,
            Instant snoozedUntil,
            Instant lastMessageAt,
            MailEnums.MessageDirection lastMessageDirection) {
    }

    public record FlagRequest(Boolean read, Boolean starred, Instant snoozeUntil) {
    }

    public record BulkFlagRequest(
            @NotEmpty List<UUID> threadIds,
            Boolean read,
            Boolean starred,
            Instant snoozeUntil) {
    }

    // -----------------------------------------------------------------------------------------------
    // Drafts and compose
    // -----------------------------------------------------------------------------------------------

    public record DraftView(
            UUID id,
            UUID threadId,
            UUID mailboxId,
            MailEnums.ReplyMode replyMode,
            List<String> to,
            List<String> cc,
            List<String> bcc,
            String subject,
            String bodyHtml,
            List<UUID> attachmentIds,
            Instant updatedAt) {
    }

    public record SaveDraftRequest(
            UUID threadId,
            UUID mailboxId,
            MailEnums.ReplyMode replyMode,
            List<@Email String> to,
            List<@Email String> cc,
            List<@Email String> bcc,
            @Size(max = 500) String subject,
            String bodyHtml,
            List<UUID> attachmentIds) {
    }

    public record ComposeRequest(
            @NotNull UUID mailboxId,
            @NotEmpty List<@Email String> to,
            List<@Email String> cc,
            List<@Email String> bcc,
            @Size(max = 500) String subject,
            String bodyHtml,
            List<UUID> attachmentIds,
            /** Deleted once the message is queued, so a sent draft does not linger in the drafts list. */
            UUID draftId) {
    }

    public record ComposeResponse(UUID threadId, UUID messageId, String subject) {
    }

    // -----------------------------------------------------------------------------------------------
    // Aliases
    // -----------------------------------------------------------------------------------------------

    public record AliasView(UUID id, UUID mailboxId, String address, Instant createdAt) {
    }

    public record CreateAliasRequest(@NotNull @Email String address) {
    }

    // -----------------------------------------------------------------------------------------------
    // The sidebar, in one request
    // -----------------------------------------------------------------------------------------------

    public record MailboxSummaryView(
            UUID id,
            String address,
            String name,
            MailEnums.MailboxKind kind,
            boolean mine,
            List<FolderView> folders) {
    }
}
