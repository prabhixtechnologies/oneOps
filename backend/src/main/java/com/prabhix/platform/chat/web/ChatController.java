package com.prabhix.platform.chat.web;

import com.prabhix.platform.chat.dto.ChatDtos;
import com.prabhix.platform.chat.service.ChatConversationService;
import com.prabhix.platform.chat.service.ChatMessageService;
import com.prabhix.platform.chat.service.ChatSettingsService;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Chat", description = "Agent chat inbox and configuration")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatConversationService conversationService;
    private final ChatMessageService messageService;
    private final ChatSettingsService settingsService;

    @GetMapping("/conversations")
    @PreAuthorize(Authorize.CHAT_READ)
    public CursorPage<ChatDtos.ConversationSummary> list(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(required = false) String queue,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return conversationService.list(principal, queue, status, cursor, limit);
    }

    @GetMapping("/conversations/counts")
    @PreAuthorize(Authorize.CHAT_READ)
    public ChatDtos.InboxCounts counts(@CurrentUser PrabhixPrincipal principal) {
        return conversationService.counts(principal);
    }

    @GetMapping("/conversations/{id}")
    @PreAuthorize(Authorize.CHAT_READ)
    public ChatDtos.ConversationDetail get(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        return conversationService.get(principal, id);
    }

    @PostMapping("/conversations/{id}/messages")
    @PreAuthorize(Authorize.CHAT_REPLY)
    public ChatDtos.MessageView send(@CurrentUser PrabhixPrincipal principal,
                                     @PathVariable UUID id,
                                     @Valid @RequestBody ChatDtos.SendMessageRequest request,
                                     @RequestParam(defaultValue = "false") boolean note,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return messageService.sendAgent(principal, id, request, note || request.isInternal(), idempotencyKey);
    }

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize(Authorize.CHAT_READ)
    public CursorPage<ChatDtos.MessageView> messages(
            @CurrentUser PrabhixPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return messageService.listAgent(principal, id, cursor, limit);
    }

    @PostMapping("/conversations/{id}/assign")
    @PreAuthorize(Authorize.CHAT_ASSIGN)
    public ChatDtos.ConversationSummary assign(@CurrentUser PrabhixPrincipal principal,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody ChatDtos.AssignRequest request) {
        return conversationService.assign(principal, id, request);
    }

    @PatchMapping("/conversations/{id}")
    @PreAuthorize(Authorize.CHAT_REPLY)
    public ChatDtos.ConversationSummary update(@CurrentUser PrabhixPrincipal principal,
                                               @PathVariable UUID id,
                                               @Valid @RequestBody ChatDtos.UpdateConversationRequest request) {
        return conversationService.update(principal, id, request);
    }

    @PostMapping("/visitors/{visitorId}/conversations")
    @PreAuthorize(Authorize.CHAT_REPLY)
    public ChatDtos.StartWithVisitorResponse startWithVisitor(
            @CurrentUser PrabhixPrincipal principal,
            @PathVariable UUID visitorId,
            @Valid @RequestBody ChatDtos.StartWithVisitorRequest request) {
        return conversationService.startWithLiveVisitor(principal, visitorId, request);
    }

    @GetMapping("/settings")
    @PreAuthorize(Authorize.CHAT_MANAGE)
    public ChatDtos.SettingsView settings(@CurrentUser PrabhixPrincipal principal) {
        return settingsService.get(principal);
    }

    @PatchMapping("/settings")
    @PreAuthorize(Authorize.CHAT_MANAGE)
    public ChatDtos.SettingsView updateSettings(@CurrentUser PrabhixPrincipal principal,
                                                @Valid @RequestBody ChatDtos.SettingsUpdateRequest request) {
        return settingsService.update(principal, request);
    }

    @GetMapping("/canned-replies")
    @PreAuthorize(Authorize.CHAT_READ)
    public List<ChatDtos.CannedReplyView> cannedReplies(@CurrentUser PrabhixPrincipal principal) {
        return settingsService.listCannedReplies(principal);
    }

    @PostMapping("/canned-replies")
    @PreAuthorize(Authorize.CHAT_MANAGE)
    public ChatDtos.CannedReplyView createCannedReply(@CurrentUser PrabhixPrincipal principal,
                                                      @Valid @RequestBody ChatDtos.CannedReplyRequest request) {
        return settingsService.createCannedReply(principal, request);
    }

    @DeleteMapping("/canned-replies/{id}")
    @PreAuthorize(Authorize.CHAT_MANAGE)
    public void deleteCannedReply(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        settingsService.deleteCannedReply(principal, id);
    }
}
