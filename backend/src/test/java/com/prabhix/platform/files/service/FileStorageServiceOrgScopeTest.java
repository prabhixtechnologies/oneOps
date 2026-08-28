package com.prabhix.platform.files.service;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.files.repository.StoredFileRepository;
import com.prabhix.platform.files.scan.MalwareScanner;
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
class FileStorageServiceOrgScopeTest {

    @Mock
    StoredFileRepository repository;

    @Mock
    MalwareScanner malwareScanner;

    FileStorageService service;

    private final UUID orgA = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PrabhixProperties props = new PrabhixProperties(
                null, null, null, null, null, null,
                new PrabhixProperties.Storage("", "ap-south-1", "bucket", "", "", true, null),
                new PrabhixProperties.Limits(100, 100, 1024, 25, 200));
        service = new FileStorageService(repository, props, Optional.empty(), malwareScanner);
    }

    @Test
    void readRejectsCrossTenantFile() {
        when(repository.findByIdAndOrganizationIdAndDeletedAtIsNull(fileId, orgA))
                .thenReturn(Optional.empty());

        assertThrows(com.prabhix.platform.common.error.ApiException.class,
                () -> service.read(orgA, fileId));
    }
}
