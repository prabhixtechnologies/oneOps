package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.repository.StoredFileRepository;
import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.mail.domain.MailAttachment;
import com.prabhix.platform.mail.domain.MailMessage;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.repository.MailAttachmentRepository;
import com.prabhix.platform.mail.repository.MailMessageRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailAttachmentServiceTest {

    @Mock private MailAttachmentRepository attachmentRepository;
    @Mock private MailMessageRepository messageRepository;
    @Mock private StoredFileRepository storedFileRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private MailboxAccess mailboxAccess;

    @InjectMocks private MailAttachmentService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final UUID threadId = UUID.randomUUID();
    private final UUID mailboxId = UUID.randomUUID();

    private PrabhixPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = new PrabhixPrincipal(
                userId, "u@example.com", "User", orgId, Set.of(Permission.MAIL_READ), UUID.randomUUID(), false);
    }

    @Test
    void listForMessageChecksMailboxAccess() {
        MailMessage message = message(messageId);
        when(messageRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(messageId, orgId))
                .thenReturn(Optional.of(message));
        when(mailboxAccess.requireThread(principal, threadId)).thenReturn(new MailThread());
        when(mailboxAccess.requireMailbox(principal, mailboxId)).thenReturn(new com.prabhix.platform.mail.domain.Mailbox());
        when(attachmentRepository.findByMessageIdAndOrganizationId(messageId, orgId)).thenReturn(List.of());

        service.listForMessage(principal, messageId);

        verify(mailboxAccess).requireMailbox(principal, mailboxId);
    }

    @Test
    void tenantIsolationOnAttachmentDownload() {
        UUID attachmentId = UUID.randomUUID();
        when(attachmentRepository.findByIdAndOrganizationId(attachmentId, orgId)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> service.downloadByAttachmentId(principal, attachmentId));
        assertEquals(ErrorCode.NOT_FOUND, ex.getCode());
    }

    @Test
    void describePendingReturnsMetadataWithoutReadingBytes() {
        UUID fileId = UUID.randomUUID();
        StoredFile file = storedFile(fileId, userId);
        file.setOriginalFilename("note.pdf");
        file.setSizeBytes(12);
        when(storedFileRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(fileId, orgId))
                .thenReturn(Optional.of(file));

        MailboxDtos.PendingAttachmentView view = service.describePending(principal, fileId);

        assertEquals("note.pdf", view.filename());
        assertEquals(12, view.sizeBytes());
        verify(fileStorageService, never()).read(orgId, fileId);
    }

    @Test
    void pendingFileDownloadRequiresUploader() {
        UUID fileId = UUID.randomUUID();
        StoredFile file = storedFile(fileId, otherUserId);
        when(storedFileRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(fileId, orgId))
                .thenReturn(Optional.of(file));
        when(attachmentRepository.findByFileIdAndOrganizationId(fileId, orgId)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> service.downloadByFileId(principal, fileId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getCode());
    }

    @Test
    void deletePendingRejectsLinkedFiles() {
        UUID fileId = UUID.randomUUID();
        when(attachmentRepository.existsByFileIdAndOrganizationId(fileId, orgId)).thenReturn(true);

        assertThrows(ApiException.class, () -> service.deletePending(principal, fileId));
        verify(fileStorageService, never()).softDelete(orgId, fileId);
    }

    @Test
    void uploadStoresMailAttachmentPurpose() throws Exception {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "note.pdf", "application/pdf", "hello".getBytes());
        StoredFile stored = storedFile(UUID.randomUUID(), userId);
        stored.setOriginalFilename("note.pdf");
        when(fileStorageService.store(
                any(), eq("note.pdf"), eq("application/pdf"),
                eq(StoredFile.FilePurpose.MAIL_ATTACHMENT), eq(userId)))
                .thenReturn(stored);

        MailboxDtos.PendingAttachmentView view = service.upload(principal, multipart);

        assertEquals(stored.getId(), view.fileId());
        assertEquals("note.pdf", view.filename());
    }

    @Test
    void uploadRejectsBlockedMime() {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "run.exe", "application/x-msdownload", new byte[]{1});

        ApiException ex = assertThrows(ApiException.class, () -> service.upload(principal, multipart));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
    }

    private MailMessage message(UUID id) {
        MailMessage message = new MailMessage();
        message.setId(id);
        message.setOrganizationId(orgId);
        message.setThreadId(threadId);
        message.setMailboxId(mailboxId);
        return message;
    }

    private StoredFile storedFile(UUID id, UUID uploadedBy) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setOrganizationId(orgId);
        file.setPurpose(StoredFile.FilePurpose.MAIL_ATTACHMENT);
        file.setUploadedBy(uploadedBy);
        file.setOriginalFilename("f.bin");
        file.setContentType("application/octet-stream");
        file.setSizeBytes(4);
        file.setScanStatus(StoredFile.ScanStatus.CLEAN);
        return file;
    }
}
