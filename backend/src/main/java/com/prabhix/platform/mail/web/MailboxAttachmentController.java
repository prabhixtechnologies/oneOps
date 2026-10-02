package com.prabhix.platform.mail.web;

import com.prabhix.platform.mail.mailbox.MailAttachmentService;
import com.prabhix.platform.mail.mailbox.MailboxDtos;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oneops/mailbox/attachments")
@RequiredArgsConstructor
public class MailboxAttachmentController {

    private final MailAttachmentService attachments;

    @GetMapping(params = "messageId")
    @PreAuthorize(Authorize.MAIL_READ)
    public List<MailboxDtos.AttachmentMetadataView> list(@CurrentUser PrabhixPrincipal principal,
                                                         @RequestParam UUID messageId) {
        return attachments.listForMessage(principal, messageId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Authorize.FILE_UPLOAD)
    public MailboxDtos.PendingAttachmentView upload(@CurrentUser PrabhixPrincipal principal,
                                                    @RequestParam("file") MultipartFile file)
            throws java.io.IOException {
        return attachments.upload(principal, file);
    }

    @GetMapping(params = "attachmentId")
    @PreAuthorize(Authorize.FILE_READ)
    public ResponseEntity<Resource> downloadAttachment(@CurrentUser PrabhixPrincipal principal,
                                                       @RequestParam UUID attachmentId) {
        return toResponse(attachments.downloadByAttachmentId(principal, attachmentId));
    }

    @GetMapping(path = "/pending", params = "fileId")
    @PreAuthorize(Authorize.FILE_READ)
    public MailboxDtos.PendingAttachmentView describePending(@CurrentUser PrabhixPrincipal principal,
                                                             @RequestParam UUID fileId) {
        return attachments.describePending(principal, fileId);
    }

    @GetMapping(params = "fileId")
    @PreAuthorize(Authorize.FILE_READ)
    public ResponseEntity<Resource> downloadFile(@CurrentUser PrabhixPrincipal principal,
                                                 @RequestParam UUID fileId) {
        return toResponse(attachments.downloadByFileId(principal, fileId));
    }

    @DeleteMapping(params = "fileId")
    @PreAuthorize(Authorize.FILE_DELETE)
    public ResponseEntity<Void> deletePending(@CurrentUser PrabhixPrincipal principal,
                                              @RequestParam UUID fileId) {
        attachments.deletePending(principal, fileId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Resource> toResponse(MailAttachmentService.MailAttachmentDownload download) {
        ContentDisposition disposition = download.inline()
                ? ContentDisposition.inline()
                .filename(safeName(download.filename()), StandardCharsets.UTF_8)
                .build()
                : ContentDisposition.attachment()
                .filename(safeName(download.filename()), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mediaType(download.contentType()))
                .contentLength(download.content().length)
                .body(new ByteArrayResource(download.content()));
    }

    private static String safeName(String filename) {
        return filename == null || filename.isBlank() ? "download" : filename;
    }

    private MediaType mediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (org.springframework.http.InvalidMediaTypeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
