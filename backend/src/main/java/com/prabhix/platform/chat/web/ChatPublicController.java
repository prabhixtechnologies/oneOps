package com.prabhix.platform.chat.web;

import com.prabhix.platform.chat.dto.ChatDtos;
import com.prabhix.platform.chat.service.ChatConversationService;
import com.prabhix.platform.chat.service.ChatMessageService;
import com.prabhix.platform.chat.service.ChatVisitorFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Chat (public)", description = "Visitor-facing chat endpoints secured by conversation token")
@RestController
@RequestMapping("/api/v1/oneops/chat/public")
@RequiredArgsConstructor
public class ChatPublicController {

    private final ChatConversationService conversationService;
    private final ChatMessageService messageService;
    private final ChatVisitorFileService visitorFileService;

    @PostMapping("/conversations")
    @Operation(summary = "Start a chat conversation with pre-chat form")
    public ChatDtos.StartConversationResponse start(@RequestParam String orgSlug,
                                                    @Valid @RequestBody ChatDtos.PreChatRequest request) {
        return conversationService.startPublic(orgSlug, request);
    }

    @PostMapping("/conversations/messages")
    @Operation(summary = "Send a visitor message")
    public ChatDtos.MessageView send(@RequestParam String orgSlug,
                                     @RequestParam UUID id,
                                     @RequestHeader("X-Chat-Token") String token,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                     @Valid @RequestBody ChatDtos.SendMessageRequest request) {
        return messageService.sendVisitor(orgSlug, id, token, request, idempotencyKey);
    }

    @PostMapping(value = "/conversations/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file for a visitor chat message")
    public ChatDtos.UploadAck uploadAttachment(@RequestParam UUID id,
                                               @RequestHeader("X-Chat-Token") String token,
                                               @RequestParam("file") MultipartFile file) {
        UUID fileId = visitorFileService.upload(token, id, file);
        return new ChatDtos.UploadAck(fileId);
    }

    @GetMapping("/conversations/messages")
    @Operation(summary = "List visitor-visible messages")
    public com.prabhix.platform.common.web.CursorPage<ChatDtos.MessageView> messages(
            @RequestParam UUID id,
            @RequestHeader("X-Chat-Token") String token,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return messageService.listVisitor(token, id, cursor, limit);
    }
}
