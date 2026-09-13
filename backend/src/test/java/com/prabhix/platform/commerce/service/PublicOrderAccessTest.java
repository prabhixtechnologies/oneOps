package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.config.CommerceProperties;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.commerce.domain.CommerceInvoice;
import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.CommerceEnums.OrderStatus;
import com.prabhix.platform.commerce.repository.CommerceInvoiceRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicOrderAccessTest {

    @Mock private OrderDownloadRepository downloadRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CommerceOrderRepository orderRepository;
    @Mock private CommerceInvoiceRepository invoiceRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private PrabhixProperties prabhixProperties;

    private DownloadService downloadService;
    private CommerceInvoiceService invoiceService;

    private final UUID orderId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        CommerceProperties properties = new CommerceProperties(
                "INR", java.time.Duration.ofHours(24), 5,
                java.time.Duration.ofDays(14), java.time.Duration.ofMinutes(15),
                java.util.List.of(), 60);
        downloadService = new DownloadService(
                downloadRepository, orderItemRepository, orderRepository, fileStorageService, properties, prabhixProperties);
        invoiceService = new CommerceInvoiceService(
                invoiceRepository, orderRepository, orderItemRepository,
                null, null, null, null, fileStorageService);
    }

    @Test
    void downloadsRequireValidAccessToken() {
        when(orderRepository.findByAccessToken("good-token")).thenReturn(Optional.of(paidOrder()));
        when(downloadRepository.findByOrderIdAndOrganizationId(orderId, orgId)).thenReturn(List.of());

        assertEquals(0, downloadService.listForAccessToken("good-token").size());
        assertThrows(ApiException.class, () -> downloadService.listForAccessToken("bad-token"));
    }

    @Test
    void invoiceRequiresMatchingAccessToken() throws Exception {
        CommerceOrder order = paidOrder();
        order.setInvoiceId(UUID.randomUUID());
        when(orderRepository.findByAccessToken("good-token")).thenReturn(Optional.of(order));

        CommerceInvoice invoice = new CommerceInvoice();
        invoice.setId(order.getInvoiceId());
        invoice.setOrganizationId(orgId);
        invoice.setInvoiceNumber("INV-1");
        invoice.setPdfFileId(UUID.randomUUID());
        when(invoiceRepository.findByIdAndOrganizationId(order.getInvoiceId(), orgId))
                .thenReturn(Optional.of(invoice));
        when(fileStorageService.read(orgId, invoice.getPdfFileId())).thenReturn(new byte[]{1, 2});

        assertEquals(2, invoiceService.downloadPdfByAccessToken("good-token").getBody().contentLength());
        assertThrows(ApiException.class, () -> invoiceService.downloadPdfByAccessToken("wrong-token"));
    }

    private CommerceOrder paidOrder() {
        CommerceOrder order = new CommerceOrder();
        order.setId(orderId);
        order.setOrganizationId(orgId);
        order.setStatus(OrderStatus.PAID);
        order.setAccessToken("good-token");
        return order;
    }
}
