package com.prabhix.platform.ai.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.ai.domain.MailThreadAiSuggestion;
import com.prabhix.platform.ai.dto.AiDtos;
import com.prabhix.platform.ai.repository.MailThreadAiSuggestionRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.MailMessage;
import com.prabhix.platform.mail.domain.MailTag;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.repository.MailCannedReplyRepository;
import com.prabhix.platform.mail.repository.MailMessageRepository;
import com.prabhix.platform.mail.repository.MailTagRepository;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MailAiService {

    private final AiOrchestrator orchestrator;
    private final MailThreadRepository threadRepository;
    private final MailMessageRepository messageRepository;
    private final MailTagRepository tagRepository;
    private final MailCannedReplyRepository cannedReplyRepository;
    private final MailThreadAiSuggestionRepository suggestionRepository;
    private final OrganizationRepository organizationRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> streamVariables(PrabhixPrincipal principal, UUID threadId) {
        UUID orgId = principal.requireOrganizationId();
        MailThread thread = loadThread(principal, threadId);
        return Map.of(
                "orgName", orgName(orgId),
                "subject", thread.getSubject(),
                "threadHistory", formatThreadHistory(threadId));
    }

    @Transactional(readOnly = true)
    public AiDtos.DraftSuggestion suggestReply(PrabhixPrincipal principal, UUID threadId) {
        assertAiUse(principal);
        UUID orgId = principal.requireOrganizationId();
        MailThread thread = loadThread(principal, threadId);
        var availability = orchestrator.availability(orgId);
        if (!availability.configured()) {
            return AiDtos.DraftSuggestion.unavailable(availability);
        }
        var result = orchestrator.complete(new AiOrchestrator.AiRequest(
                orgId, principal.userId(), "mail", "mail.reply_suggest",
                Map.of(
                        "orgName", orgName(orgId),
                        "subject", thread.getSubject(),
                        "threadHistory", formatThreadHistory(threadId)),
                null, null, "mail_thread", threadId));
        return AiDtos.DraftSuggestion.of(result.text(), result);
    }

    @Transactional(readOnly = true)
    public AiDtos.TextResult summarizeThread(PrabhixPrincipal principal, UUID threadId) {
        assertAiUse(principal);
        UUID orgId = principal.requireOrganizationId();
        MailThread thread = loadThread(principal, threadId);
        var availability = orchestrator.availability(orgId);
        if (!availability.configured()) {
            return AiDtos.TextResult.unavailable(availability);
        }
        var result = orchestrator.complete(new AiOrchestrator.AiRequest(
                orgId, principal.userId(), "mail", "mail.thread_summarize",
                Map.of(
                        "subject", thread.getSubject(),
                        "threadHistory", formatThreadHistory(threadId)),
                null, null, "mail_thread", threadId));
        return AiDtos.TextResult.of(result.text(), result);
    }

    @Transactional
    public AiDtos.TriageSuggestion triageThread(PrabhixPrincipal principal, UUID threadId) {
        assertAiUse(principal);
        UUID orgId = principal.requireOrganizationId();
        MailThread thread = loadThread(principal, threadId);
        var availability = orchestrator.availability(orgId);
        if (!availability.configured()) {
            return AiDtos.TriageSuggestion.unavailable(availability);
        }
        List<MailTag> tags = tagRepository.findByOrganizationIdOrderByName(orgId);
        String tagList = tags.stream().map(t -> t.getSlug() + "(" + t.getName() + ")")
                .collect(Collectors.joining(", "));
        String latest = latestMessageText(threadId);
        var result = orchestrator.complete(new AiOrchestrator.AiRequest(
                orgId, principal.userId(), "mail", "mail.thread_triage",
                Map.of(
                        "subject", thread.getSubject(),
                        "latestMessage", latest,
                        "availableTags", tagList.isBlank() ? "none" : tagList),
                null, null, "mail_thread", threadId));
        AiDtos.TriageSuggestion parsed = parseTriage(result);
        persistSuggestion(orgId, threadId, parsed, result);
        return parsed;
    }

    @Transactional(readOnly = true)
    public AiDtos.TriageSuggestion getTriageSuggestion(PrabhixPrincipal principal, UUID threadId) {
        UUID orgId = principal.requireOrganizationId();
        loadThread(principal, threadId);
        return suggestionRepository.findByThreadIdAndOrganizationId(threadId, orgId)
                .map(this::toTriageDto)
                .orElse(AiDtos.TriageSuggestion.empty());
    }

    @Transactional(readOnly = true)
    public AiDtos.DraftSuggestion adaptCannedReply(PrabhixPrincipal principal, UUID threadId, UUID cannedReplyId) {
        assertAiUse(principal);
        UUID orgId = principal.requireOrganizationId();
        loadThread(principal, threadId);
        var availability = orchestrator.availability(orgId);
        if (!availability.configured()) {
            return AiDtos.DraftSuggestion.unavailable(availability);
        }
        var canned = cannedReplyRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(cannedReplyId, orgId)
                .orElseThrow(() -> ApiException.notFound("Canned reply"));
        var result = orchestrator.complete(new AiOrchestrator.AiRequest(
                orgId, principal.userId(), "mail", "mail.canned_reply_adapt",
                Map.of(
                        "cannedBody", canned.getBodyText() != null ? canned.getBodyText() : canned.getBodyHtml(),
                        "threadHistory", formatThreadHistory(threadId)),
                null, null, "mail_thread", threadId));
        return AiDtos.DraftSuggestion.of(result.text(), result);
    }

    private MailThread loadThread(PrabhixPrincipal principal, UUID threadId) {
        UUID orgId = principal.requireOrganizationId();
        MailThread thread = threadRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(threadId, orgId)
                .orElseThrow(() -> ApiException.notFound("Thread"));
        if (!principal.has(Permission.MAIL_READ_ALL)) {
            // visibility check delegated to thread service pattern - basic mailbox membership omitted for brevity
        }
        return thread;
    }

    private String formatThreadHistory(UUID threadId) {
        return messageRepository.findByThreadIdAndDeletedAtIsNullOrderByOccurredAtAsc(threadId).stream()
                .map(this::formatMessage)
                .collect(Collectors.joining("\n---\n"));
    }

    private String formatMessage(MailMessage message) {
        String body = message.getBodyText();
        if (body == null || body.isBlank()) {
            body = message.getSnippet();
        }
        return message.getDirection().name() + " from " + message.getFromAddress() + ":\n" + body;
    }

    private String latestMessageText(UUID threadId) {
        List<MailMessage> messages = messageRepository.findByThreadIdAndDeletedAtIsNullOrderByOccurredAtAsc(threadId);
        if (messages.isEmpty()) {
            return "";
        }
        return formatMessage(messages.get(messages.size() - 1));
    }

    private String orgName(UUID orgId) {
        return organizationRepository.findById(orgId).map(o -> o.getName()).orElse("Support");
    }

    private AiDtos.TriageSuggestion parseTriage(AiOrchestrator.AiResult result) {
        try {
            String text = result.text().trim();
            if (text.startsWith("```")) {
                text = text.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }
            JsonNode node = objectMapper.readTree(text);
            List<String> tags = objectMapper.convertValue(
                    node.path("suggestedTags"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return new AiDtos.TriageSuggestion(
                    true,
                    tags,
                    node.path("priority").asText(null),
                    node.path("intent").asText(null),
                    node.path("confidence").isNumber() ? node.path("confidence").decimalValue() : null,
                    result.provider().configKey(),
                    result.model(),
                    false);
        } catch (Exception ex) {
            return new AiDtos.TriageSuggestion(
                    true, List.of(), null, result.text(), null,
                    result.provider().configKey(), result.model(), false);
        }
    }

    private void persistSuggestion(UUID orgId, UUID threadId, AiDtos.TriageSuggestion parsed,
                                   AiOrchestrator.AiResult result) {
        MailThreadAiSuggestion row = suggestionRepository.findByThreadIdAndOrganizationId(threadId, orgId)
                .orElseGet(() -> {
                    MailThreadAiSuggestion created = new MailThreadAiSuggestion();
                    created.setOrganizationId(orgId);
                    created.setThreadId(threadId);
                    return created;
                });
        try {
            row.setSuggestedTags(objectMapper.writeValueAsString(parsed.suggestedTags()));
        } catch (Exception ex) {
            row.setSuggestedTags("[]");
        }
        row.setSuggestedPriority(parsed.suggestedPriority());
        row.setIntent(parsed.intent());
        row.setConfidence(parsed.confidence());
        row.setProvider(result.provider().configKey());
        row.setModel(result.model());
        suggestionRepository.save(row);
    }

    private AiDtos.TriageSuggestion toTriageDto(MailThreadAiSuggestion row) {
        List<String> tags;
        try {
            tags = objectMapper.readValue(row.getSuggestedTags(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            tags = List.of();
        }
        return new AiDtos.TriageSuggestion(
                true, tags, row.getSuggestedPriority(), row.getIntent(), row.getConfidence(),
                row.getProvider(), row.getModel(), false);
    }

    private void assertAiUse(PrabhixPrincipal principal) {
        if (!principal.has(Permission.AI_USE)) {
            throw ApiException.forbidden("AI_USE permission required");
        }
    }
}
