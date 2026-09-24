package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.CommerceEnums;
import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.OrderItem;
import com.prabhix.platform.commerce.domain.OrderShipment;
import com.prabhix.platform.commerce.domain.ProductVariant;
import com.prabhix.platform.commerce.domain.ServiceEngagement;
import com.prabhix.platform.commerce.domain.CommerceSubscription;
import com.prabhix.platform.commerce.repository.CommerceCustomerRepository;
import com.prabhix.platform.commerce.repository.CommerceSubscriptionRepository;
import com.prabhix.platform.commerce.repository.OrderDownloadRepository;
import com.prabhix.platform.commerce.repository.OrderItemRepository;
import com.prabhix.platform.commerce.repository.OrderShipmentRepository;
import com.prabhix.platform.commerce.repository.ProductVariantRepository;
import com.prabhix.platform.commerce.repository.ServiceEngagementRepository;
import com.prabhix.platform.commerce.config.CommerceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommerceOrderProvisioningServiceTest {

    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderDownloadRepository downloadRepository;
    @Mock private OrderShipmentRepository shipmentRepository;
    @Mock private ServiceEngagementRepository engagementRepository;
    @Mock private CommerceSubscriptionRepository subscriptionRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private CommerceCustomerRepository customerRepository;

    private CommerceOrderProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new CommerceOrderProvisioningService(
                orderItemRepository,
                downloadRepository,
                shipmentRepository,
                engagementRepository,
                subscriptionRepository,
                variantRepository,
                customerRepository,
                new CommerceProperties("INR", Duration.ofHours(24), 5, Duration.ofDays(14),
                        Duration.ofMinutes(15), List.of(), 60));
    }

    @Test
    void provisionsPhysicalShipmentAndServiceEngagement() {
        UUID orgId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        CommerceOrder order = new CommerceOrder();
        order.setId(orderId);
        order.setOrganizationId(orgId);
        order.setCustomerId(UUID.randomUUID());
        order.setCurrency("INR");

        UUID physicalItemId = UUID.randomUUID();
        UUID serviceItemId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        OrderItem physical = item(physicalItemId, orderId, orgId, CommerceEnums.ProductType.PHYSICAL, variantId);
        OrderItem serviceItem = item(serviceItemId, orderId, orgId, CommerceEnums.ProductType.SERVICE, variantId);

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setServiceDurationDays(30);
        variant.setDeliverySlaDays(7);

        when(orderItemRepository.findByOrderIdAndOrganizationId(orderId, orgId))
                .thenReturn(List.of(physical, serviceItem));
        when(shipmentRepository.findByOrderItemId(physicalItemId)).thenReturn(Optional.empty());
        when(engagementRepository.findByOrderItemId(serviceItemId)).thenReturn(Optional.empty());
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));

        service.provisionPaidOrder(order);

        verify(shipmentRepository).save(any(OrderShipment.class));
        verify(engagementRepository).save(any(ServiceEngagement.class));
    }

    @Test
    void provisionsSubscriptionEntitlement() {
        UUID orgId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        CommerceOrder order = new CommerceOrder();
        order.setId(orderId);
        order.setOrganizationId(orgId);
        order.setCustomerId(UUID.randomUUID());
        order.setCurrency("INR");

        UUID itemId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        OrderItem subscriptionItem = item(itemId, orderId, orgId, CommerceEnums.ProductType.SUBSCRIPTION, variantId);

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setBillingInterval(CommerceEnums.BillingInterval.MONTHLY);

        when(orderItemRepository.findByOrderIdAndOrganizationId(orderId, orgId))
                .thenReturn(List.of(subscriptionItem));
        when(subscriptionRepository.findByOrderItemId(itemId)).thenReturn(Optional.empty());
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));

        service.provisionPaidOrder(order);

        verify(subscriptionRepository).save(any(CommerceSubscription.class));
    }

    private static OrderItem item(UUID id, UUID orderId, UUID orgId,
                                  CommerceEnums.ProductType type, UUID variantId) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrderId(orderId);
        item.setOrganizationId(orgId);
        item.setProductId(UUID.randomUUID());
        item.setVariantId(variantId);
        item.setProductType(type);
        item.setUnitPricePaise(1000);
        item.setQuantity(1);
        return item;
    }
}
