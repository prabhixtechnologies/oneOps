package com.prabhix.platform.chat.web;

import com.prabhix.platform.ai.dto.AiDtos;
import com.prabhix.platform.ai.service.ChatAiService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oneops/chat/conversations")
@RequiredArgsConstructor
public class ChatAiController {

    private final ChatAiService chatAiService;

    @PostMapping("/ai/reply/suggest")
    @PreAuthorize(Authorize.AI_USE)
    public AiDtos.DraftSuggestion suggestReply(@CurrentUser PrabhixPrincipal principal, @RequestParam UUID id) {
        return chatAiService.suggestReply(principal, id);
    }

    @PostMapping("/ai/rewrite")
    @PreAuthorize(Authorize.AI_USE)
    public AiDtos.RewriteResult rewrite(@CurrentUser PrabhixPrincipal principal,
                                        @RequestParam UUID id,
                                        @Valid @RequestBody AiDtos.RewriteRequest request) {
        return chatAiService.rewriteMessage(principal, id, request);
    }

    @PostMapping("/ai/handoff-summary")
    @PreAuthorize(Authorize.AI_USE)
    public AiDtos.HandoffSummaryResult handoffSummary(@CurrentUser PrabhixPrincipal principal,
                                                      @RequestParam UUID id) {
        return chatAiService.createHandoffSummary(principal, id);
    }

    @PostMapping("/ai/sentiment")
    @PreAuthorize(Authorize.AI_USE)
    public AiDtos.SentimentResult sentiment(@CurrentUser PrabhixPrincipal principal, @RequestParam UUID id) {
        return chatAiService.analyzeSentiment(principal, id);
    }
}
