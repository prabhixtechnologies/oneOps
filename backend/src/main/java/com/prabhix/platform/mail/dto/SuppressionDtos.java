package com.prabhix.platform.mail.dto;

import com.prabhix.platform.mail.domain.MailEnums;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public final class SuppressionDtos {

    private SuppressionDtos() {
    }

    public record SuppressionResponse(
            UUID id,
            String address,
            MailEnums.SuppressionReason reason,
            String detail,
            Instant expiresAt) {
    }

    @Schema(name = "CreateSuppressionRequest")
    public record CreateRequest(
            @NotBlank String address,
            MailEnums.SuppressionReason reason,
            String detail) {
    }
}
