package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.config.CommerceProperties;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.OrderDownload;
import com.prabhix.platform.commerce.domain.OrderItem;
import com.prabhix.platform.commerce.domain.CommerceEnums.OrderStatus;
import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.commerce.repository.OrderDownloadRepository;
import com.prabhix.platform.commerce.repository.OrderItemRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.security.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DownloadService {

    private final OrderDownloadRepository downloadRepository;
    private final OrderItemRepository orderItemRepository;
    private final CommerceOrderRepository orderRepository;
    private final FileStorageService fileStorageService;
    private final CommerceProperties properties;
    private final PrabhixProperties prabhixProperties;

    @Transactional(readOnly = true)
    public List<CommerceDtos.DownloadView> listForAccessToken(String accessToken) {
        CommerceOrder order = orderRepository.findByAccessToken(accessToken)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "That order was not found"));
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.FULFILLED) {
            throw ApiException.invalidState("Downloads are available only for paid orders");
        }
        return listForOrder(order.getOrganizationId(), order.getId());
    }

    CommerceOrder requireOrderByAccessToken(String accessToken) {
        return orderRepository.findByAccessToken(accessToken)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "That order was not found"));
    }

    @Transactional(readOnly = true)
    public List<CommerceDtos.DownloadView> listForOrder(UUID organizationId, UUID orderId) {
        List<OrderDownload> downloads = downloadRepository.findByOrderIdAndOrganizationId(orderId, organizationId);
        return downloads.stream().map(this::toView).toList();
    }

    @Transactional
    public CommerceDtos.DownloadLinkResponse issueDownload(String downloadToken) {
        OrderDownload download = downloadRepository.findByDownloadToken(downloadToken)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "Download link was not found"));
        validateDownload(download);
        download.setDownloadCount(download.getDownloadCount() + 1);
        downloadRepository.save(download);
        String url = TenantContext.callAs(download.getOrganizationId(), () ->
                fileStorageService.signedUrl(download.getOrganizationId(), download.getFileId())
                        .orElseThrow(() -> ApiException.of(ErrorCode.STORAGE_ERROR, "Could not generate download link")));
        return new CommerceDtos.DownloadLinkResponse(url, download.getLinkExpiresAt());
    }

    @Transactional
    public CommerceDtos.DownloadLinkResponse reissue(UUID organizationId, UUID orderId, UUID orderItemId) {
        CommerceOrder order = orderRepository.findByIdAndOrganizationId(orderId, organizationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "That order was not found"));
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.FULFILLED) {
            throw ApiException.invalidState("Downloads are available only for paid orders");
        }
        OrderDownload download = downloadRepository.findByOrderItemId(orderItemId)
                .filter(d -> d.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> ApiException.notFound("Download entitlement"));
        download.setLinkExpiresAt(Instant.now().plus(properties.downloadLinkTtl()));
        download.setDownloadToken(CommerceTokens.opaqueToken());
        downloadRepository.save(download);
        return new CommerceDtos.DownloadLinkResponse(
                marketingBase() + "/download/" + download.getDownloadToken(),
                download.getLinkExpiresAt());
    }

    private String marketingBase() {
        PrabhixProperties.Urls urls = prabhixProperties.urls();
        String base = urls == null || urls.marketing() == null || urls.marketing().isBlank()
                ? "http://localhost:3000"
                : urls.marketing();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    void validateDownload(OrderDownload download) {
        if (download.getLinkExpiresAt().isBefore(Instant.now())) {
            throw ApiException.of(ErrorCode.DOWNLOAD_LINK_EXPIRED, "That download link has expired");
        }
        if (download.getDownloadCount() >= download.getMaxDownloadCount()) {
            throw ApiException.of(ErrorCode.DOWNLOAD_LINK_EXPIRED, "Download limit reached for this file");
        }
    }

    private CommerceDtos.DownloadView toView(OrderDownload download) {
        OrderItem item = orderItemRepository.findById(download.getOrderItemId()).orElse(null);
        return new CommerceDtos.DownloadView(
                download.getOrderItemId(),
                item == null ? "" : item.getProductName(),
                download.getDownloadCount(),
                download.getMaxDownloadCount(),
                download.getLinkExpiresAt(),
                null);
    }
}
