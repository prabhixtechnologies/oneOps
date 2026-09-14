package com.prabhix.platform.files.service;

import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.files.repository.StoredFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FileQueryServiceIsolationTest {

    @Mock
    StoredFileRepository repository;

    FileQueryService service;

    private final UUID orgA = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PrabhixProperties props = new PrabhixProperties(
                null, null, null, null, null,
                new PrabhixProperties.Storage("", "ap-south-1", "bucket", "", "", true, null),
                new PrabhixProperties.Limits(100, 100, 1024, 25, 200));
        service = new FileQueryService(repository, props);
    }

    @Test
    void listScopesToOrganization() {
        Cursor beginning = Cursor.beginning();
        service.list(orgA, null, null, null, null, 25);
        verify(repository).listWithCursor(eq(orgA), eq(null), eq(null), eq(null),
                eq(beginning.timestamp()), eq(beginning.id()), eq(26));
    }
}
