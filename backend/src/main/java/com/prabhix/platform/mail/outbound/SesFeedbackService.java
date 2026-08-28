package com.prabhix.platform.mail.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.MailOutbox;
import com.prabhix.platform.mail.repository.MailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SesFeedbackService {

    private final SuppressionService suppressionService;
    private final MailOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /** Parses an SES bounce or complaint notification and records suppressions. */
    @Transactional
    public UUID processSesNotification(JsonNode sesNotification) {
        String notificationType = sesNotification.path("notificationType").asText(
                sesNotification.path("eventType").asText(""));
        UUID organizationId = resolveOrganizationId(sesNotification);

        return switch (notificationType) {
            case "Bounce" -> processBounce(sesNotification, organizationId);
            case "Complaint" -> processComplaint(sesNotification, organizationId);
            default -> {
                log.debug("Ignoring SES notification type {}", notificationType);
                yield organizationId;
            }
        };
    }

    private UUID processBounce(JsonNode root, UUID organizationId) {
        JsonNode bounce = root.path("bounce");
        String bounceType = bounce.path("bounceType").asText("");
        String detail = bounce.path("bounceSubType").asText(bounceType);
        if (organizationId == null) {
            log.warn("SES bounce received without resolvable organization; skipping suppression");
            return null;
        }
        for (JsonNode recipient : bounce.path("bouncedRecipients")) {
            String address = recipient.path("emailAddress").asText(null);
            if (address == null || address.isBlank()) {
                continue;
            }
            if ("Permanent".equalsIgnoreCase(bounceType)) {
                suppressionService.recordHardBounce(address, organizationId, detail);
            } else {
                suppressionService.recordSoftBounce(address, organizationId, detail);
            }
        }
        return organizationId;
    }

    private UUID processComplaint(JsonNode root, UUID organizationId) {
        if (organizationId == null) {
            log.warn("SES complaint received without resolvable organization; skipping suppression");
            return null;
        }
        JsonNode complaint = root.path("complaint");
        String detail = complaint.path("complaintFeedbackType").asText("complaint");
        for (JsonNode recipient : complaint.path("complainedRecipients")) {
            String address = recipient.path("emailAddress").asText(null);
            if (address != null && !address.isBlank()) {
                suppressionService.recordComplaint(address, organizationId, detail);
            }
        }
        return organizationId;
    }

    UUID resolveOrganizationId(JsonNode sesNotification) {
        JsonNode tags = sesNotification.path("mail").path("tags");
        if (tags.has("organizationId")) {
            String fromTag = tags.path("organizationId").isArray()
                    ? tags.path("organizationId").get(0).asText(null)
                    : tags.path("organizationId").asText(null);
            UUID parsed = parseUuid(fromTag);
            if (parsed != null) {
                return parsed;
            }
        }

        String providerMessageId = sesNotification.path("mail").path("messageId").asText(null);
        if (providerMessageId != null && !providerMessageId.isBlank()) {
            Optional<MailOutbox> outbox = outboxRepository.findFirstByProviderMessageId(providerMessageId);
            if (outbox.isPresent()) {
                return outbox.get().getOrganizationId();
            }
        }
        return null;
    }

    JsonNode parseSesMessage(String messageBody) {
        try {
            return objectMapper.readTree(messageBody);
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.MALFORMED_REQUEST, "SES notification is not valid JSON");
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
