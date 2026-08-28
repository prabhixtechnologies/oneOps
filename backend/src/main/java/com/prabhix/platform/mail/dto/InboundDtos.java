package com.prabhix.platform.mail.dto;

import jakarta.validation.constraints.NotBlank;

public final class InboundDtos {

    private InboundDtos() {
    }

    public record LmtpRequest(
            @NotBlank String recipient,
            @NotBlank String rawMimeBase64) {
    }

    public record LmtpResponse(java.util.UUID inboundRawId) {
    }
}
