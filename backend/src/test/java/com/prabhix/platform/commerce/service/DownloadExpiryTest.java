package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.config.CommerceProperties;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.OrderDownload;
import com.prabhix.platform.commerce.domain.CommerceEnums.OrderStatus;
import com.prabhix.platform.commerce.dto.CommerceDtos;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownloadExpiryTest {

    @Mock private OrderDownloadRepository downloadRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CommerceOrderRepository orderRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private PrabhixProperties prabhixProperties;

    private DownloadService downloadService;

    @BeforeEach
    void setUp() {
        CommerceProperties properties = new CommerceProperties(
                "INR", Duration.ofHours(1), 3, Duration.ofDays(14),
                Duration.ofMinutes(15), List.of(), 60);
        downloadService = new DownloadService(
                downloadRepository, orderItemRepository, orderRepository, fileStorageService, properties, prabhixProperties);
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

    @Test
    void reissueRotatesTokenAndReturnsMarketingLandingUrl() {
        UUID orgId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        CommerceOrder order = new CommerceOrder();
        order.setId(orderId);
        order.setOrganizationId(orgId);
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdAndOrganizationId(orderId, orgId)).thenReturn(Optional.of(order));

        OrderDownload download = new OrderDownload();
        download.setOrganizationId(orgId);
        download.setOrderId(orderId);
        download.setOrderItemId(itemId);
        download.setFileId(UUID.randomUUID());
        download.setDownloadToken("old-token");
        download.setDownloadCount(2);
        download.setMaxDownloadCount(5);
        download.setLinkExpiresAt(Instant.now().minusSeconds(30));
        when(downloadRepository.findByOrderItemId(itemId)).thenReturn(Optional.of(download));
        when(downloadRepository.save(any(OrderDownload.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(prabhixProperties.urls()).thenReturn(
                new PrabhixProperties.Urls("http://localhost:3000/", "http://localhost:5173", "http://localhost:8080"));

        CommerceDtos.DownloadLinkResponse response = downloadService.reissue(orgId, orderId, itemId);

        assertNotEquals("old-token", download.getDownloadToken());
        assertEquals(2, download.getDownloadCount());
        assertEquals("http://localhost:3000/download/" + download.getDownloadToken(), response.downloadUrl());
        verifyNoInteractions(fileStorageService);
    }
}
