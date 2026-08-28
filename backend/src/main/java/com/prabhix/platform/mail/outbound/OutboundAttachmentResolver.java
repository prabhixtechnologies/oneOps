package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.repository.StoredFileRepository;
import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.mail.outbound.transport.MailTransport;
import com.prabhix.platform.mail.util.MailJson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboundAttachmentResolver {

    private final StoredFileRepository fileRepository;
    private final FileStorageService fileStorageService;

    public List<MailTransport.AttachmentPart> resolve(UUID organizationId, String attachmentIdsJson) {
        List<String> ids = MailJson.parseStringList(attachmentIdsJson);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<UUID> fileIds = ids.stream().map(UUID::fromString).toList();
        List<StoredFile> files = fileRepository.findAllByIdInAndOrganizationId(fileIds, organizationId);
        List<MailTransport.AttachmentPart> parts = new ArrayList<>();
        for (StoredFile file : files) {
            if (!file.isDownloadable()) {
                continue;
            }
            MailTransport.AttachmentPart part = new MailTransport.AttachmentPart();
            part.setFilename(file.getOriginalFilename());
            part.setContentType(file.getContentType());
            part.setContent(fileStorageService.read(organizationId, file.getId()));
            parts.add(part);
        }
        return parts;
    }
}
