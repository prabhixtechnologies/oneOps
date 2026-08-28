package com.prabhix.platform.ai.web;

import com.prabhix.platform.ai.dto.AiDtos;
import com.prabhix.platform.ai.prompt.PromptService;
import com.prabhix.platform.ai.service.AiOrchestrator;
import com.prabhix.platform.ai.service.AiSettingsService;
import com.prabhix.platform.ai.service.CommerceAiService;
import com.prabhix.platform.ai.service.LeadAiService;
import com.prabhix.platform.ai.usage.AiUsageService;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "AI", description = "Provider-agnostic AI assistance, usage, and configuration")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiOrchestrator orchestrator;
    private final AiUsageService usageService;
    private final PromptService promptService;
    private final AiSettingsService settingsService;
    private final LeadAiService leadAiService;
    private final CommerceAiService commerceAiService;

    @GetMapping("/status")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public AiDtos.AvailabilityView status(@CurrentUser PrabhixPrincipal principal) {
        return AiDtos.AvailabilityView.from(orchestrator.availability(principal.requireOrganizationId()));
    }

    @PostMapping("/assist")
    @PreAuthorize(Authorize.AI_USE)
    public AiDtos.AssistResult assist(@CurrentUser PrabhixPrincipal principal,
                                      @Valid @RequestBody AiDtos.AssistRequest request) {
        UUID orgId = principal.requireOrganizationId();
        var availability = orchestrator.availability(orgId);
        if (!availability.configured()) {
            return new AiDtos.AssistResult(false, "", null, null);
        }
        String taskKey = request.taskKey() != null && !request.taskKey().isBlank()
                ? request.taskKey() : "assist.generic";
        var result = orchestrator.complete(new AiOrchestrator.AiRequest(
                orgId, principal.userId(), "assist", taskKey,
                Map.of(
                        "instruction", request.instruction() != null ? request.instruction() : "",
                        "context", request.context() != null ? request.context() : ""),
                request.providerOverride(), null, null, null));
        return new AiDtos.AssistResult(true, result.text(), result.provider().configKey(), result.model());
    }

    @GetMapping("/usage")
    @PreAuthorize(Authorize.AI_USAGE_READ)
    public CursorPage<AiDtos.UsageRowView> listUsage(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        var page = usageService.list(principal.requireOrganizationId(), cursor, limit);
        return new CursorPage<>(
                page.items().stream().map(AiDtos.UsageRowView::from).toList(),
                page.nextCursor(),
                page.hasMore());
    }

    @GetMapping("/usage/summary")
    @PreAuthorize(Authorize.AI_USAGE_READ)
    public AiDtos.UsageSummaryView usageSummary(@CurrentUser PrabhixPrincipal principal) {
        var summary = usageService.summary(principal.requireOrganizationId());
        return new AiDtos.UsageSummaryView(summary.tokensThisMonth(), summary.costPaiseThisMonth());
    }

    @GetMapping("/prompts")
    @PreAuthorize(Authorize.AI_CONFIGURE)
    public List<AiDtos.PromptView> listPrompts(@CurrentUser PrabhixPrincipal principal) {
        UUID orgId = principal.requireOrganizationId();
        return promptService.listForOrg(orgId).stream()
                .map(p -> AiDtos.PromptView.from(p, orgId))
                .toList();
    }

    @PutMapping("/prompts/{taskKey}")
    @PreAuthorize(Authorize.AI_CONFIGURE)
    public AiDtos.PromptView updatePrompt(@CurrentUser PrabhixPrincipal principal,
                                          @PathVariable String taskKey,
                                          @Valid @RequestBody AiDtos.UpdatePromptRequest request) {
        UUID orgId = principal.requireOrganizationId();
        var saved = promptService.upsertOrgPrompt(
                orgId, taskKey, request.template(),
                request.provider(), request.model(), request.temperature(),
                principal.userId());
        return AiDtos.PromptView.from(saved, orgId);
    }

    @GetMapping("/settings")
    @PreAuthorize(Authorize.AI_CONFIGURE)
    public AiDtos.OrgSettingsView getSettings(@CurrentUser PrabhixPrincipal principal) {
        return settingsService.get(principal.requireOrganizationId());
    }

    @PatchMapping("/settings")
    @PreAuthorize(Authorize.AI_CONFIGURE)
    public AiDtos.OrgSettingsView updateSettings(@CurrentUser PrabhixPrincipal principal,
                                                 @Valid @RequestBody AiDtos.UpdateOrgSettingsRequest request) {
        return settingsService.update(
                principal.requireOrganizationId(), principal.userId(), request);
    }

    @PostMapping("/leads/{leadId}/enrich")
    @PreAuthorize(Authorize.AI_USE)
    public AiDtos.LeadEnrichmentResult enrichLead(@CurrentUser PrabhixPrincipal principal,
                                                  @PathVariable UUID leadId) {
        return leadAiService.enrichLead(
                principal.requireOrganizationId(), principal.userId(), leadId);
    }

    @PostMapping("/commerce/products/{productId}/description")
    @PreAuthorize(Authorize.AI_USE)
    public AiDtos.ProductDescriptionResult draftProductDescription(
            @CurrentUser PrabhixPrincipal principal,
            @PathVariable UUID productId) {
        return commerceAiService.draftDescription(
                principal.requireOrganizationId(), principal.userId(), productId);
    }
}
