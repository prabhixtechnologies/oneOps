package com.prabhix.platform.mail.web;

import com.prabhix.platform.ai.dto.AiDtos;
import com.prabhix.platform.ai.service.MailAiService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mail/threads")
@RequiredArgsConstructor
public class MailAiController {

    private final MailAiService mailAiService;

    @PostMapping("/{id}/ai/reply/suggest")
    @PreAuthorize(Authorize.AI_USE)
    public AiDtos.DraftSuggestion suggestReply(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        return mailAiService.suggestReply(principal, id);
    }

    @PostMapping("/{id}/ai/summarize")
    @PreAuthorize(Authorize.AI_USE)
    public AiDtos.TextResult summarize(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        return mailAiService.summarizeThread(principal, id);
    }

    @PostMapping("/{id}/ai/triage")
    @PreAuthorize(Authorize.AI_USE)
    public AiDtos.TriageSuggestion triage(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        return mailAiService.triageThread(principal, id);
    }

    @GetMapping("/{id}/ai/triage")
    @PreAuthorize(Authorize.MAIL_READ)
    public AiDtos.TriageSuggestion getTriage(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        return mailAiService.getTriageSuggestion(principal, id);
    }

    @PostMapping("/{id}/ai/canned-replies/{cannedReplyId}/adapt")
    @PreAuthorize(Authorize.AI_USE)
    public AiDtos.DraftSuggestion adaptCannedReply(@CurrentUser PrabhixPrincipal principal,
                                                   @PathVariable UUID id,
                                                   @PathVariable UUID cannedReplyId) {
        return mailAiService.adaptCannedReply(principal, id, cannedReplyId);
    }
}
