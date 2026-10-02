package com.prabhix.platform.files.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prabhix.platform.security.PrabhixPrincipal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentValidationService {

    private final StoredFileRepository repository;

    @Transactional(readOnly = true)
    public List<StoredFile> requireCleanAttachments(UUID organizationId, List<UUID> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        List<UUID> distinct = fileIds.stream().distinct().toList();
        List<StoredFile> files = repository.findAllByIdInAndOrganizationId(distinct, organizationId);
        Set<UUID> found = new HashSet<>();
        for (StoredFile file : files) {
            found.add(file.getId());
            if (!file.isDownloadable()) {
                throw ApiException.of(ErrorCode.FORBIDDEN,
                        file.getScanStatus() == StoredFile.ScanStatus.INFECTED
                                ? "An attachment was quarantined by our malware scanner"
                                : "An attachment is still being scanned. Try again shortly.");
            }
        }
        if (found.size() != distinct.size()) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "One or more attachments were not found");
        }
        return new ArrayList<>(files);
    }

    /**
     * Pending compose uploads must belong to the caller and be mail attachments, not arbitrary files
     * from another feature that happen to share the org.
     */
    @Transactional(readOnly = true)
    public List<StoredFile> requireOwnedMailAttachments(PrabhixPrincipal principal,
                                                        UUID organizationId,
                                                        List<UUID> fileIds) {
        List<StoredFile> files = requireCleanAttachments(organizationId, fileIds);
        for (StoredFile file : files) {
            if (file.getPurpose() != StoredFile.FilePurpose.MAIL_ATTACHMENT) {
                throw ApiException.forbidden("One or more attachments are not mail files");
            }
            if (!Objects.equals(principal.userId(), file.getUploadedBy())) {
                throw ApiException.forbidden("You can only send attachments you uploaded");
            }
        }
        return files;
    }
}
