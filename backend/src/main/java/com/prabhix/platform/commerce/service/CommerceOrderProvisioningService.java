package com.prabhix.platform.commerce.service;

import tools.jackson.databind.JsonNode;
import com.prabhix.platform.commerce.domain.CommerceCustomer;
import com.prabhix.platform.commerce.domain.CommerceEnums;
import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.CommerceSubscription;
import com.prabhix.platform.commerce.domain.OrderItem;
import com.prabhix.platform.commerce.domain.OrderShipment;
import com.prabhix.platform.commerce.domain.ProductVariant;
import com.prabhix.platform.commerce.domain.ServiceEngagement;
import com.prabhix.platform.commerce.repository.CommerceCustomerRepository;
import com.prabhix.platform.commerce.repository.CommerceSubscriptionRepository;
import com.prabhix.platform.commerce.repository.OrderDownloadRepository;
import com.prabhix.platform.commerce.repository.OrderItemRepository;
import com.prabhix.platform.commerce.repository.OrderShipmentRepository;
import com.prabhix.platform.commerce.repository.ProductVariantRepository;
import com.prabhix.platform.commerce.repository.ServiceEngagementRepository;
import com.prabhix.platform.commerce.config.CommerceProperties;
import com.prabhix.platform.commerce.domain.CommerceEnums.ProductType;
import com.prabhix.platform.commerce.domain.OrderDownload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommerceOrderProvisioningService {

    private final OrderItemRepository orderItemRepository;
    private final OrderDownloadRepository downloadRepository;
    private final OrderShipmentRepository shipmentRepository;
    private final ServiceEngagementRepository engagementRepository;
    private final CommerceSubscriptionRepository subscriptionRepository;
    private final ProductVariantRepository variantRepository;
    private final CommerceCustomerRepository customerRepository;
    private final CommerceProperties commerceProperties;

    @Transactional
    public void provisionPaidOrder(CommerceOrder order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdAndOrganizationId(
                order.getId(), order.getOrganizationId());
        Instant downloadExpires = Instant.now().plus(commerceProperties.downloadLinkTtl());
        for (OrderItem item : items) {
            switch (item.getProductType()) {
                case DIGITAL -> provisionDigital(order, item, downloadExpires);
                case PHYSICAL -> provisionPhysical(order, item);
                case SERVICE -> provisionService(order, item);
                case SUBSCRIPTION -> provisionSubscription(order, item);
            }
        }
    }

    @Transactional
    public void captureTokenFromPayment(CommerceOrder order, JsonNode payment) {
        if (!order.isSubscriptionCheckout()) {
            return;
        }
        String tokenId = payment.path("token_id").asText(null);
        if (tokenId == null || tokenId.isBlank()) {
            return;
        }
        List<OrderItem> items = orderItemRepository.findByOrderIdAndOrganizationId(
                order.getId(), order.getOrganizationId());
        for (OrderItem item : items) {
            if (item.getProductType() != ProductType.SUBSCRIPTION) {
                continue;
            }
            subscriptionRepository.findByOrderItemId(item.getId()).ifPresent(sub -> {
                sub.setRazorpayTokenId(tokenId);
                subscriptionRepository.save(sub);
            });
        }
    }

    private void provisionDigital(CommerceOrder order, OrderItem item, Instant expires) {
        ProductVariant variant = variantRepository.findById(item.getVariantId()).orElse(null);
        if (variant == null || variant.getDownloadFileId() == null) {
            return;
        }
        if (downloadRepository.findByOrderItemId(item.getId()).isPresent()) {
            return;
        }
        OrderDownload download = new OrderDownload();
        download.setOrganizationId(order.getOrganizationId());
        download.setOrderId(order.getId());
        download.setOrderItemId(item.getId());
        download.setFileId(variant.getDownloadFileId());
        download.setDownloadToken(CommerceTokens.opaqueToken());
        download.setMaxDownloadCount(commerceProperties.maxDownloadCount());
        download.setLinkExpiresAt(expires);
        downloadRepository.save(download);
    }

    private void provisionPhysical(CommerceOrder order, OrderItem item) {
        if (shipmentRepository.findByOrderItemId(item.getId()).isPresent()) {
            return;
        }
        OrderShipment shipment = new OrderShipment();
        shipment.setOrganizationId(order.getOrganizationId());
        shipment.setOrderId(order.getId());
        shipment.setOrderItemId(item.getId());
        shipment.setStatus(CommerceEnums.ShipmentStatus.PENDING_PICK);
        shipmentRepository.save(shipment);
    }

    private void provisionService(CommerceOrder order, OrderItem item) {
        if (engagementRepository.findByOrderItemId(item.getId()).isPresent()) {
            return;
        }
        ProductVariant variant = variantRepository.findById(item.getVariantId()).orElse(null);
        Instant starts = Instant.now();
        ServiceEngagement engagement = new ServiceEngagement();
        engagement.setOrganizationId(order.getOrganizationId());
        engagement.setOrderId(order.getId());
        engagement.setOrderItemId(item.getId());
        engagement.setStartsAt(starts);
        if (variant != null) {
            engagement.setDurationDays(variant.getServiceDurationDays());
            engagement.setDeliverySlaDays(variant.getDeliverySlaDays());
            if (variant.getServiceDurationDays() != null && variant.getServiceDurationDays() > 0) {
                engagement.setEndsAt(starts.plus(variant.getServiceDurationDays(), ChronoUnit.DAYS));
            }
            if (variant.getDeliverySlaDays() != null && variant.getDeliverySlaDays() > 0) {
                engagement.setSlaDueAt(starts.plus(variant.getDeliverySlaDays(), ChronoUnit.DAYS));
            }
        }
        engagement.setStatus(CommerceEnums.EngagementStatus.ACTIVE);
        engagementRepository.save(engagement);
    }

    private void provisionSubscription(CommerceOrder order, OrderItem item) {
        if (subscriptionRepository.findByOrderItemId(item.getId()).isPresent()) {
            return;
        }
        ProductVariant variant = variantRepository.findById(item.getVariantId()).orElse(null);
        CommerceEnums.BillingInterval interval = variant == null || variant.getBillingInterval() == null
                ? CommerceEnums.BillingInterval.MONTHLY
                : variant.getBillingInterval();
        if (interval == CommerceEnums.BillingInterval.ONE_TIME) {
            return;
        }

        CommerceCustomer customer = customerRepository.findById(order.getCustomerId()).orElse(null);
        Instant periodStart = Instant.now();
        Instant periodEnd = periodStart.plus(periodDays(interval), ChronoUnit.DAYS);

        CommerceSubscription subscription = new CommerceSubscription();
        subscription.setOrganizationId(order.getOrganizationId());
        subscription.setCustomerId(order.getCustomerId());
        subscription.setOrderId(order.getId());
        subscription.setOrderItemId(item.getId());
        subscription.setProductId(item.getProductId());
        subscription.setVariantId(item.getVariantId());
        subscription.setBillingInterval(interval);
        subscription.setCurrentPeriodStart(periodStart);
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setNextBillingAt(periodEnd);
        subscription.setLockedPriceMinor(item.getUnitPriceMinor());
        subscription.setCurrency(order.getCurrency());
        subscription.setStatus(CommerceEnums.SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(subscription);
    }

    static int periodDays(CommerceEnums.BillingInterval interval) {
        return switch (interval) {
            case ANNUAL -> 365;
            case MONTHLY -> 30;
            default -> 30;
        };
    }
}
