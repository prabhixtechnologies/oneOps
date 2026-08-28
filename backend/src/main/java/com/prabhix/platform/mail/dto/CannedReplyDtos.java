package com.prabhix.platform.mail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public final class CannedReplyDtos {

    private CannedReplyDtos() {
    }

    public record CannedReplyResponse(
            UUID id,
            UUID mailboxId,
            String shortcut,
            String title,
            String subject,
            String bodyHtml,
            long usageCount) {
    }

    /**
     * springdoc keys schemas by simple name, so a bare {@code CreateRequest} would be claimed by
     * whichever module registered last and generated clients would get the wrong shape for the
     * loser. Every request DTO here is named for its resource.
     */
    @Schema(name = "CreateMailCannedReplyRequest")
    public record CreateRequest(
            UUID mailboxId,
            String shortcut,
            @NotBlank String title,
            String subject,
            @NotBlank String bodyHtml,
            String bodyText) {
    }

    @Schema(name = "UpdateMailCannedReplyRequest")
    public record UpdateRequest(
            UUID mailboxId,
            String shortcut,
            @NotBlank String title,
            String subject,
            @NotBlank String bodyHtml,
            String bodyText) {
    }
}
