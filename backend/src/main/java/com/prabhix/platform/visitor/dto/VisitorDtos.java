package com.prabhix.platform.visitor.dto;

import com.prabhix.platform.visitor.domain.VisitorEnums;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VisitorDtos {

    private VisitorDtos() {
    }

    @Schema(description = "Acknowledgement after public ingest; returns durable visitor and session ids.")
    public record IngestAck(String visitorKey, UUID sessionId) {
    }

    public record PageViewInput(
            @NotBlank @Size(max = 2000) String url,
            @NotBlank @Size(max = 500) String path,
            @Size(max = 500) String title,
            @Size(max = 500) String referrer,
            Integer durationMs,
            boolean entry,
            boolean exit) {
    }

    public record CustomEventInput(
            @NotBlank @Size(max = 120) String name,
            Map<String, Object> properties) {
    }

    public record BatchIngestRequest(
            String visitorKey,
            UUID sessionId,
            @NotNull VisitorEnums.ConsentStatus consent,
            @Size(max = 50) List<@Valid PageViewInput> pageViews,
            @Size(max = 50) List<@Valid CustomEventInput> events,
            SessionContext session,
            Map<String, String> utm,
            String referrer,
            PresenceUpdate presence) {
    }

    public record SessionContext(
            String deviceType,
            String browser,
            String os,
            Integer screenWidth,
            Integer screenHeight,
            String language,
            String timezone) {
    }

    public record PresenceUpdate(
            @NotBlank @Size(max = 2000) String url,
            @NotBlank @Size(max = 500) String path,
            @Size(max = 500) String title) {
    }

    public record IdentifyRequest(
            @NotBlank String visitorKey,
            @Size(max = 160) String name,
            String email,
            UUID userId) {
    }

    public record VisitorSummary(
            UUID id,
            String externalKey,
            VisitorEnums.ConsentStatus consentStatus,
            Instant firstSeenAt,
            Instant lastSeenAt,
            String email,
            String displayName,
            boolean identified) {
    }

    public record LiveVisitor(
            UUID visitorId,
            String externalKey,
            String currentPath,
            String currentTitle,
            Instant since,
            String email,
            String displayName) {
    }

    public record PageViewView(
            UUID id,
            String url,
            String path,
            String title,
            Instant viewedAt,
            Integer durationMs,
            boolean entry,
            boolean exit) {
    }

    public record EventView(UUID id, String name, Map<String, Object> properties, Instant occurredAt) {
    }

    public record SessionView(
            UUID id,
            Instant startedAt,
            Instant endedAt,
            Integer durationSeconds,
            String entryUrl,
            String exitUrl,
            String referrer,
            String deviceType,
            String browser,
            String os,
            String geoCountry,
            String geoCity) {
    }

    public record VisitorDetail(VisitorSummary visitor, List<SessionView> sessions) {
    }

    public record AnalyticsSummary(
            List<DimensionCount> topPages,
            List<DimensionCount> topReferrers,
            List<TimeSeriesPoint> sessionsOverTime,
            long totalVisitors,
            long identifiedVisitors,
            long chatConversions) {
    }

    public record DimensionCount(String dimension, long count) {
    }

    public record TimeSeriesPoint(String date, long count) {
    }
}
