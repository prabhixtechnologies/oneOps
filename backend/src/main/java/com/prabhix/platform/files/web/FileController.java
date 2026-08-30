package com.prabhix.platform.files.web;

import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.dto.FileDtos;
import com.prabhix.platform.files.service.FileQueryService;
import com.prabhix.platform.files.service.FileStorageService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;
    private final FileQueryService fileQueryService;

    @GetMapping
    @PreAuthorize(Authorize.FILE_READ)
    public CursorPage<FileDtos.FileSummary> list(@CurrentUser PrabhixPrincipal principal,
                                                 @RequestParam(required = false) StoredFile.FilePurpose purpose,
                                                 @RequestParam(required = false) StoredFile.ScanStatus scanStatus,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) String cursor,
                                                 @RequestParam(required = false) Integer limit) {
        return fileQueryService.list(
                principal.requireOrganizationId(), purpose, scanStatus, q, cursor, limit);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Authorize.FILE_READ)
    public ResponseEntity<Resource> download(@CurrentUser PrabhixPrincipal principal,
                                             @PathVariable UUID id) {
        Optional<String> redirect = fileStorageService.signedUrl(principal.requireOrganizationId(), id);
        if (redirect.isPresent()) {
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, redirect.get())
                    .build();
        }

        StoredFile metadata = fileStorageService.requireMetadata(principal.requireOrganizationId(), id);
        byte[] content = fileStorageService.read(principal.requireOrganizationId(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(metadata.getOriginalFilename()))
                .contentType(mediaType(metadata.getContentType()))
                .contentLength(content.length)
                .body(new ByteArrayResource(content));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Authorize.FILE_UPLOAD)
    public FileUploadResponse upload(@CurrentUser PrabhixPrincipal principal,
                                     @RequestParam("file") MultipartFile file,
                                     @RequestParam(defaultValue = "DOCUMENT") StoredFile.FilePurpose purpose)
            throws java.io.IOException {
        StoredFile stored = fileStorageService.store(
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType(),
                purpose,
                principal.userId());
        return new FileUploadResponse(stored.getId(), stored.getOriginalFilename(), stored.getContentType(),
                stored.getSizeBytes(), stored.getScanStatus());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Authorize.FILE_DELETE)
    public ResponseEntity<Void> delete(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        fileStorageService.softDelete(principal.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }

    private String contentDisposition(String filename) {
        return ContentDisposition.attachment()
                .filename(filename == null || filename.isBlank() ? "download" : filename, StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    /** Uploads may arrive without a usable content type, and a blank one breaks media type parsing. */
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

    public record FileUploadResponse(UUID id, String filename, String contentType, long sizeBytes,
                                     StoredFile.ScanStatus scanStatus) {
    }
}
