package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.repository.StoredFileRepository;
import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.mail.domain.MailAttachment;
import com.prabhix.platform.mail.domain.MailMessage;
import com.prabhix.platform.mail.repository.MailAttachmentRepository;
import com.prabhix.platform.mail.repository.MailMessageRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MailAttachmentService {

    private final MailAttachmentRepository attachmentRepository;
    private final MailMessageRepository messageRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;
    private final MailboxAccess mailboxAccess;

    @Transactional(readOnly = true)
    public List<MailboxDtos.AttachmentMetadataView> listForMessage(PrabhixPrincipal principal,
                                                                   UUID messageId) {
        UUID orgId = principal.requireOrganizationId();
        MailMessage message = requireReadableMessage(principal, orgId, messageId);
        return attachmentRepository.findByMessageIdAndOrganizationId(message.getId(), orgId).stream()
                .map(this::toMetadata)
                .toList();
    }

    @Transactional
    public MailboxDtos.PendingAttachmentView upload(PrabhixPrincipal principal, MultipartFile file)
            throws IOException {
        MailAttachmentMimePolicy.assertAllowed(file.getContentType(), file.getOriginalFilename());
        StoredFile stored = fileStorageService.store(
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType(),
                StoredFile.FilePurpose.MAIL_ATTACHMENT,
                principal.userId());
        return new MailboxDtos.PendingAttachmentView(
                stored.getId(),
                stored.getOriginalFilename(),
                stored.getContentType(),
                stored.getSizeBytes(),
                stored.getScanStatus());
    }

    @Transactional(readOnly = true)
    public MailAttachmentDownload downloadByAttachmentId(PrabhixPrincipal principal, UUID attachmentId) {
        UUID orgId = principal.requireOrganizationId();
        MailAttachment attachment = attachmentRepository.findByIdAndOrganizationId(attachmentId, orgId)
                .orElseThrow(() -> ApiException.notFound("Attachment"));
        requireReadableMessage(principal, orgId, attachment.getMessageId());
        StoredFile file = storedFileRepository
                .findByIdAndOrganizationIdAndDeletedAtIsNull(attachment.getFileId(), orgId)
                .orElseThrow(() -> ApiException.notFound("File"));
        byte[] content = fileStorageService.read(orgId, file.getId());
        return new MailAttachmentDownload(
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.isInline(),
                content);
    }

    @Transactional(readOnly = true)
    public MailboxDtos.PendingAttachmentView describePending(PrabhixPrincipal principal, UUID fileId) {
        UUID orgId = principal.requireOrganizationId();
        StoredFile file = storedFileRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(fileId, orgId)
                .orElseThrow(() -> ApiException.notFound("File"));
        assertPendingOwner(principal, file);
        return new MailboxDtos.PendingAttachmentView(
                file.getId(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getScanStatus());
    }

    @Transactional(readOnly = true)
    public MailAttachmentDownload downloadByFileId(PrabhixPrincipal principal, UUID fileId) {
        UUID orgId = principal.requireOrganizationId();
        StoredFile file = storedFileRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(fileId, orgId)
                .orElseThrow(() -> ApiException.notFound("File"));
        if (file.getPurpose() != StoredFile.FilePurpose.MAIL_ATTACHMENT) {
            throw ApiException.forbidden("That file is not a mail attachment");
        }
        Optional<MailAttachment> linked =
                attachmentRepository.findByFileIdAndOrganizationId(fileId, orgId);
        if (linked.isPresent()) {
            requireReadableMessage(principal, orgId, linked.get().getMessageId());
        } else {
            assertPendingOwner(principal, file);
        }
        byte[] content = fileStorageService.read(orgId, file.getId());
        return new MailAttachmentDownload(
                file.getOriginalFilename(),
                file.getContentType(),
                false,
                content);
    }

    @Transactional
    public void deletePending(PrabhixPrincipal principal, UUID fileId) {
        UUID orgId = principal.requireOrganizationId();
        if (attachmentRepository.existsByFileIdAndOrganizationId(fileId, orgId)) {
            throw ApiException.invalidState("That attachment already belongs to a message and cannot be removed");
        }
        StoredFile file = storedFileRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(fileId, orgId)
                .orElseThrow(() -> ApiException.notFound("File"));
        assertPendingOwner(principal, file);
        fileStorageService.softDelete(orgId, fileId);
    }

    private MailMessage requireReadableMessage(PrabhixPrincipal principal, UUID orgId, UUID messageId) {
        MailMessage message = messageRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(messageId, orgId)
                .orElseThrow(() -> ApiException.notFound("Message"));
        mailboxAccess.requireThread(principal, message.getThreadId());
        mailboxAccess.requireMailbox(principal, message.getMailboxId());
        return message;
    }

    private void assertPendingOwner(PrabhixPrincipal principal, StoredFile file) {
        if (file.getPurpose() != StoredFile.FilePurpose.MAIL_ATTACHMENT) {
            throw ApiException.forbidden("That file is not a mail attachment");
        }
        if (!Objects.equals(principal.userId(), file.getUploadedBy())) {
            throw ApiException.forbidden("You can only access attachments you uploaded");
        }
    }

    private MailboxDtos.AttachmentMetadataView toMetadata(MailAttachment attachment) {
        StoredFile.ScanStatus scanStatus = storedFileRepository
                .findByIdAndOrganizationIdAndDeletedAtIsNull(
                        attachment.getFileId(), attachment.getOrganizationId())
                .map(StoredFile::getScanStatus)
                .orElse(StoredFile.ScanStatus.PENDING);
        return new MailboxDtos.AttachmentMetadataView(
                attachment.getId(),
                attachment.getMessageId(),
                attachment.getFileId(),
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.isInline(),
                attachment.getContentId(),
                scanStatus);
    }

    public record MailAttachmentDownload(
            String filename,
            String contentType,
            boolean inline,
            byte[] content) {
    }
}
