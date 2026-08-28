package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.repository.ProductMediaRepository;
import com.prabhix.platform.commerce.repository.ProductRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.repository.StoredFileRepository;
import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.security.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicProductImageService {

    private final ProductRepository productRepository;
    private final ProductMediaRepository mediaRepository;
    private final StoredFileRepository fileRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public ResponseEntity<Void> redirectImage(UUID organizationId, UUID fileId) {
        StoredFile file = authorizeProductImage(organizationId, fileId);
        String url = TenantContext.callAs(organizationId, () ->
                fileStorageService.signedUrl(organizationId, file.getId())
                        .orElseThrow(() -> ApiException.of(ErrorCode.STORAGE_ERROR,
                                "Could not generate image URL")));
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
    }

    @Transactional(readOnly = true)
    public StoredFile authorizeProductImage(UUID organizationId, UUID fileId) {
        StoredFile file = fileRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(fileId, organizationId)
                .orElseThrow(() -> ApiException.notFound("File"));
        if (!isImageContentType(file.getContentType())) {
            throw ApiException.forbidden("Only product images may be accessed on this endpoint");
        }
        if (!file.isDownloadable()) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "That image is not available");
        }
        if (!isLinkedToActiveProduct(organizationId, fileId)) {
            throw ApiException.forbidden("That image is not part of a published product");
        }
        return file;
    }

    boolean isLinkedToActiveProduct(UUID organizationId, UUID fileId) {
        if (productRepository.existsActiveProductHeroImage(organizationId, fileId)) {
            return true;
        }
        if (productRepository.existsActiveProductGalleryImage(organizationId, fileId)) {
            return true;
        }
        return mediaRepository.existsForActiveProduct(organizationId, fileId);
    }

    static boolean isImageContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.startsWith("image/") && !lower.contains("svg");
    }

    public String publicImagePath(String orgSlug, UUID fileId) {
        return "/api/v1/commerce/public/" + orgSlug + "/images/" + fileId;
    }
}
