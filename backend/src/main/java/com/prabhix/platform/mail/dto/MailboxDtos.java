package com.prabhix.platform.mail.dto;

import com.prabhix.platform.mail.domain.MailEnums;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
            /**
             * Minutes allowed for a first response, and for resolution. Null means no target.
             *
             * <p>These replace a single {@code slaPolicyId} string that was never a policy id: it was
             * {@code String.valueOf(slaFirstResponseMins)}, so a screen showing "SLA policy" was
             * showing a number of minutes with no way to write it back. The resolution target existed
             * on the row and was not exposed at all.
             */
            Integer slaFirstResponseMins,
            Integer slaResolutionMins,
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

    /**
     * One grant of access to a mailbox: either to a person or to a team.
     *
     * <p>Addressed by {@code id}, the membership row, rather than by {@code userId}. A team grant has
     * no user id, so a route keyed on the user could not name one — which is why team grants were
     * unreachable over HTTP even though the table has always stored them.
     */
    public record MailboxMemberResponse(
            UUID id,
            UUID userId,
            UUID teamId,
            String name,
            String email,
            MailEnums.MemberAccessLevel accessLevel) {
    }

    public record RoutingRuleResponse(
            UUID id,
            String name,
            String description,
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
            /**
             * Minutes, or 0 to clear the target. Null leaves it as it was, like every other field
             * here — which is why clearing needs a value of its own rather than a null.
             */
            @Min(0) @Max(100000) Integer slaFirstResponseMins,
            @Min(0) @Max(100000) Integer slaResolutionMins,
            String imapPassword,
            String smtpPassword) {
    }

    /**
     * Grants a mailbox to exactly one of a person or a team.
     *
     * <p>Both nullable at the type level and exactly one required by
     * {@link #hasExactlyOneSubject()}: a record cannot express "one of these two" in annotations, and
     * accepting both would create a row that is neither kind of grant.
     */
    public record AddMailboxMemberRequest(
            UUID userId,
            UUID teamId,
            MailEnums.MemberAccessLevel accessLevel) {

        @AssertTrue(message = "Give either a userId or a teamId, not both")
        public boolean hasExactlyOneSubject() {
            return (userId == null) != (teamId == null);
        }
    }

    public record UpdateMailboxMemberRequest(@NotNull MailEnums.MemberAccessLevel accessLevel) {
    }

    public record SaveRoutingRuleRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description,
            Boolean enabled,
            @Min(0) @Max(10000) Integer priority,
            MailEnums.MatchMode match,
            @NotEmpty List<Map<String, Object>> conditions,
            @NotEmpty List<Map<String, Object>> actions,
            Boolean continueAfterMatch) {
    }
}
