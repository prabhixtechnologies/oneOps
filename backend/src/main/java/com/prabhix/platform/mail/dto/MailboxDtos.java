package com.prabhix.platform.mail.dto;

import com.prabhix.platform.mail.domain.MailEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MailboxDtos {

    private MailboxDtos() {
    }

    public record MailboxResponse(
            UUID id,
            String address,
            String name,
            MailEnums.MailboxKind kind,
            MailEnums.MailboxStatus status,
            int openThreadCount,
            int unassignedCount) {
    }

    public record MailboxDetailResponse(
            UUID id,
            String name,
            String email,
            String description,
            int memberCount,
            int openThreadCount,
            String slaPolicyId,
            String signature,
            Instant createdAt,
            /**
             * When a mail-client password was last issued, or null if the mailbox has none.
             *
             * <p>Deliberately a timestamp and not a boolean: "no password" and "password issued
             * eighteen months ago and still on someone's old phone" are both worth acting on, and a
             * flag cannot tell them apart. The hash itself never leaves the server.
             */
            Instant mailPasswordUpdatedAt,
            List<MailboxMemberResponse> members,
            List<RoutingRuleResponse> routingRules,
            BusinessHoursResponse businessHours) {
    }

    public record MailboxMemberResponse(UUID userId, String name, String email) {
    }

    public record RoutingRuleResponse(
            UUID id,
            String name,
            int priority,
            List<Map<String, Object>> conditions,
            String match,
            List<Map<String, Object>> actions,
            boolean continueAfterMatch,
            boolean enabled) {
    }

    public record BusinessHoursResponse(
            String timezone,
            List<Integer> workingDays,
            String startTime,
            String endTime,
            List<String> holidays) {
    }

    public record CreateMailboxRequest(
            @NotBlank String address,
            @NotBlank String name,
            MailEnums.MailboxKind kind,
            String description,
            String imapPassword,
            String smtpPassword) {
    }

    public record UpdateMailboxRequest(
            String name,
            String description,
            String signature,
            BusinessHoursResponse businessHours,
            String imapPassword,
            String smtpPassword) {
    }

    public record AddMailboxMemberRequest(
            @NotNull UUID userId,
            MailEnums.MemberAccessLevel accessLevel) {
    }
}
