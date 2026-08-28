package com.prabhix.platform.files.service;

import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.repository.StoredFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentValidationServiceTest {

    @Mock
    StoredFileRepository repository;

    AttachmentValidationService service;

    private final UUID orgA = UUID.randomUUID();
    private final UUID orgB = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AttachmentValidationService(repository);
    }

    @Test
    void rejectsForeignFileId() {
        when(repository.findAllByIdInAndOrganizationId(List.of(fileId), orgA))
                .thenReturn(List.of());

        assertThrows(com.prabhix.platform.common.error.ApiException.class,
                () -> service.requireCleanAttachments(orgA, List.of(fileId)));
    }

    @Test
    void acceptsOwnedCleanFile() {
        StoredFile file = new StoredFile();
        file.setId(fileId);
        file.setOrganizationId(orgA);
        file.setScanStatus(StoredFile.ScanStatus.CLEAN);
        when(repository.findAllByIdInAndOrganizationId(List.of(fileId), orgA))
                .thenReturn(List.of(file));

        service.requireCleanAttachments(orgA, List.of(fileId));
    }
}
