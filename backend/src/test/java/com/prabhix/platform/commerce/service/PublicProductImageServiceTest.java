package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.repository.ProductMediaRepository;
import com.prabhix.platform.commerce.repository.ProductRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.repository.StoredFileRepository;
import com.prabhix.platform.files.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicProductImageServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductMediaRepository mediaRepository;
    @Mock private StoredFileRepository fileRepository;
    @Mock private FileStorageService fileStorageService;

    private PublicProductImageService service;

    private final UUID orgA = UUID.randomUUID();
    private final UUID orgB = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PublicProductImageService(
                productRepository, mediaRepository, fileRepository, fileStorageService);
    }

    @Test
    void rejectsNonImageContentType() {
        StoredFile file = file(orgA, "application/pdf");
        when(fileRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(fileId, orgA))
                .thenReturn(Optional.of(file));

        assertThrows(ApiException.class, () -> service.authorizeProductImage(orgA, fileId));
    }

    @Test
    void rejectsCrossTenantFile() {
        when(fileRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(fileId, orgA))
                .thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.authorizeProductImage(orgA, fileId));
    }

    @Test
    void rejectsFileNotLinkedToActiveProduct() {
        StoredFile file = file(orgA, "image/png");
        when(fileRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(fileId, orgA))
                .thenReturn(Optional.of(file));
        when(productRepository.existsActiveProductHeroImage(orgA, fileId)).thenReturn(false);
        when(productRepository.existsActiveProductGalleryImage(orgA, fileId)).thenReturn(false);
        when(mediaRepository.existsForActiveProduct(orgA, fileId)).thenReturn(false);

        assertThrows(ApiException.class, () -> service.authorizeProductImage(orgA, fileId));
    }

    @Test
    void rejectsOtherTenantEvenWhenFileExists() {
        StoredFile file = file(orgB, "image/jpeg");
        when(fileRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(fileId, orgA))
                .thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.authorizeProductImage(orgA, fileId));
    }

    private StoredFile file(UUID orgId, String contentType) {
        StoredFile file = new StoredFile();
        file.setId(fileId);
        file.setOrganizationId(orgId);
        file.setContentType(contentType);
        file.setScanStatus(StoredFile.ScanStatus.CLEAN);
        return file;
    }
}
