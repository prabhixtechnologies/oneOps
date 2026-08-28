package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.config.CommerceProperties;
import com.prabhix.platform.commerce.domain.OrderDownload;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.commerce.repository.OrderDownloadRepository;
import com.prabhix.platform.commerce.repository.OrderItemRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.files.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownloadExpiryTest {

    @Mock private OrderDownloadRepository downloadRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CommerceOrderRepository orderRepository;
    @Mock private FileStorageService fileStorageService;

    private DownloadService downloadService;

    @BeforeEach
    void setUp() {
        CommerceProperties properties = new CommerceProperties(
                "INR", Duration.ofHours(1), 3, Duration.ofDays(14),
                Duration.ofMinutes(15), List.of(), 60);
        downloadService = new DownloadService(
                downloadRepository, orderItemRepository, orderRepository, fileStorageService, properties);
    }

    @Test
    void expiredLinkRejected() {
        OrderDownload download = new OrderDownload();
        download.setOrganizationId(UUID.randomUUID());
        download.setFileId(UUID.randomUUID());
        download.setLinkExpiresAt(Instant.now().minusSeconds(30));
        download.setDownloadCount(0);
        download.setMaxDownloadCount(3);

        assertThrows(ApiException.class, () -> downloadService.validateDownload(download));
    }

    @Test
    void countCapRejected() {
        OrderDownload download = new OrderDownload();
        download.setOrganizationId(UUID.randomUUID());
        download.setFileId(UUID.randomUUID());
        download.setLinkExpiresAt(Instant.now().plusSeconds(3600));
        download.setDownloadCount(3);
        download.setMaxDownloadCount(3);

        assertThrows(ApiException.class, () -> downloadService.validateDownload(download));
    }

    @Test
    void missingTokenNotFound() {
        when(downloadRepository.findByDownloadToken("bad")).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> downloadService.issueDownload("bad"));
    }
}
