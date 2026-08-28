package com.prabhix.platform.chat.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.security.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatVisitorFileService {

    private final ChatTokenService tokenService;
    private final FileStorageService fileStorageService;

    @Transactional
    public UUID upload(String token, UUID conversationId, MultipartFile file) {
        ChatTokenService.ConversationToken parsed = tokenService.parse(token);
        tokenService.assertConversation(parsed, conversationId);
        return TenantContext.callAs(parsed.organizationId(), () -> {
            try {
                StoredFile stored = fileStorageService.store(
                        file.getBytes(),
                        file.getOriginalFilename(),
                        file.getContentType(),
                        StoredFile.FilePurpose.CHAT_ATTACHMENT,
                        null);
                return stored.getId();
            } catch (java.io.IOException ex) {
                throw com.prabhix.platform.common.error.ApiException.of(
                        com.prabhix.platform.common.error.ErrorCode.STORAGE_ERROR,
                        "Could not store that file", ex);
            }
        });
    }
}
