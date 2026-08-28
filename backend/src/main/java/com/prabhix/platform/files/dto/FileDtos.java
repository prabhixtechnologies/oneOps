package com.prabhix.platform.files.dto;

import com.prabhix.platform.files.domain.StoredFile;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public final class FileDtos {

    private FileDtos() {
    }

    @Schema(name = "StoredFileSummary")
    public record FileSummary(
            UUID id,
            String filename,
            String contentType,
            long sizeBytes,
            StoredFile.FilePurpose purpose,
            StoredFile.ScanStatus scanStatus,
            Instant createdAt,
            UUID uploadedBy) {
    }
}
