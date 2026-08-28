package com.prabhix.platform.mail.dto;

import com.prabhix.platform.mail.domain.MailEnums;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TemplateDtos {

    private TemplateDtos() {
    }

    public record TemplateResponse(
            UUID id,
            String templateKey,
            String locale,
            String name,
            String subject,
            MailEnums.TemplateCategory category,
            boolean enabled) {
    }

    public record PreviewRequest(Map<String, Object> variables) {
    }

    public record PreviewResponse(String subject, String bodyHtml, String bodyText) {
    }

    /** A variable the sending code supplies, surfaced so the editor can show what is available. */
    public record TemplateVariable(String name, String example, boolean required) {
    }

    public record TemplateDetailResponse(
            String key,
            String name,
            String locale,
            String subject,
            String htmlBody,
            String textBody,
            List<TemplateVariable> variables,
            Instant updatedAt) {
    }

    /**
     * Copy is editable; the variable list is not. Variables are dictated by the code that
     * sends the mail, so letting the editor redeclare them would only ever break rendering.
     */
    public record UpdateTemplateRequest(
            String name,
            String subject,
            String htmlBody,
            String textBody) {
    }
}
