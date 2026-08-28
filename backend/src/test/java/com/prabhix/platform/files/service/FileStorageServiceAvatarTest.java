package com.prabhix.platform.files.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.repository.StoredFileRepository;
import com.prabhix.platform.files.scan.MalwareScanner;
import com.prabhix.platform.security.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceAvatarTest {

    @Mock
    StoredFileRepository repository;

    @Mock
    MalwareScanner malwareScanner;

    FileStorageService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PrabhixProperties props = new PrabhixProperties(
                null, null, null, null, null, null,
                new PrabhixProperties.Storage("", "ap-south-1", "bucket", "", "", true, null),
                new PrabhixProperties.Limits(100, 100, 1024, 25, 200));
        service = new FileStorageService(repository, props, Optional.empty(), malwareScanner);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void storeAvatarWorksWithoutActiveOrganization() {
        when(repository.findDuplicate(any(), any(), anyLong())).thenReturn(Optional.empty());
        when(malwareScanner.scan(any(), any())).thenReturn(
                new MalwareScanner.ScanResult(StoredFile.ScanStatus.CLEAN, null));
        when(repository.save(any())).thenAnswer(invocation -> {
            StoredFile file = invocation.getArgument(0);
            file.setId(UUID.randomUUID());
            return file;
        });

        StoredFile stored = service.storeAvatar(new byte[] {1, 2, 3}, "avatar.png", "image/png", userId);

        ArgumentCaptor<StoredFile> captor = ArgumentCaptor.forClass(StoredFile.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertEquals(FileStorageService.PLATFORM_FILES_ORGANIZATION_ID,
                captor.getAllValues().get(0).getOrganizationId());
        assertEquals(StoredFile.FilePurpose.AVATAR, stored.getPurpose());
    }

    @Test
    void ordinaryStoreRequiresTenant() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.store(new byte[] {1}, "doc.pdf", "application/pdf",
                        StoredFile.FilePurpose.IMPORT, userId));
        assertEquals(ErrorCode.ORGANIZATION_REQUIRED, ex.getCode());
    }
}
