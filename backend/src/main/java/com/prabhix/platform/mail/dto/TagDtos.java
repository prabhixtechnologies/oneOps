package com.prabhix.platform.mail.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public final class TagDtos {

    private TagDtos() {
    }

    public record TagResponse(UUID id, String slug, String name, String colour, int usageCount) {
    }

    public record CreateTagRequest(
            @NotBlank String name,
            String slug,
            String colour,
            String description) {
    }

    public record UpdateTagRequest(
            @NotBlank String name,
            String colour,
            String description) {
    }

    public record ThreadTagRequest(@NotNull UUID tagId) {
    }

    public record BulkThreadTagRequest(
            @NotEmpty List<UUID> threadIds,
            @NotNull UUID tagId) {
    }
}
