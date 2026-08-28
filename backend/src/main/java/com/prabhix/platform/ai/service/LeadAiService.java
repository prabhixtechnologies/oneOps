package com.prabhix.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.ai.dto.AiDtos;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.site.domain.SiteLead;
import com.prabhix.platform.site.repository.SiteLeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeadAiService {

    private final AiOrchestrator orchestrator;
    private final SiteLeadRepository leadRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AiDtos.LeadEnrichmentResult enrichLead(UUID organizationId, UUID userId, UUID leadId) {
        SiteLead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> ApiException.notFound("Lead"));
        var availability = orchestrator.availability(organizationId);
        if (!availability.configured()) {
            return new AiDtos.LeadEnrichmentResult(false, 0, null, null, List.of());
        }
        var result = orchestrator.complete(new AiOrchestrator.AiRequest(
                organizationId, userId, "lead", "lead.score_enrich",
                Map.of(
                        "name", lead.getName(),
                        "company", lead.getCompany() != null ? lead.getCompany() : "",
                        "interest", lead.getInterest().name(),
                        "message", lead.getMessage()),
                null, null, "site_lead", leadId));
        return parseLeadResult(result.text());
    }

    private AiDtos.LeadEnrichmentResult parseLeadResult(String text) {
        try {
            String json = text.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }
            JsonNode node = objectMapper.readTree(json);
            List<String> actions = objectMapper.convertValue(
                    node.path("suggestedActions"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return new AiDtos.LeadEnrichmentResult(
                    true,
                    node.path("score").asInt(50),
                    node.path("priority").asText(null),
                    node.path("summary").asText(null),
                    actions);
        } catch (Exception ex) {
            return new AiDtos.LeadEnrichmentResult(true, 50, "medium", text, List.of());
        }
    }
}
