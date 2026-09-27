package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.DiscountCode;
import com.prabhix.platform.commerce.domain.CommerceEnums.DiscountType;
import com.prabhix.platform.commerce.repository.DiscountCodeRepository;
import com.prabhix.platform.commerce.repository.DiscountRedemptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountRedemptionOnCaptureTest {

    @Mock private DiscountCodeRepository discountCodeRepository;
    @Mock private DiscountRedemptionRepository redemptionRepository;
    @Mock private com.prabhix.platform.commerce.repository.ProductRepository productRepository;
    @Mock private com.prabhix.platform.commerce.repository.ProductCategoryRepository categoryRepository;

    @InjectMocks
    private DiscountService discountService;

    @Test
    void captureRedemptionIsIdempotentPerOrder() {
        UUID orderId = UUID.randomUUID();
        UUID codeId = UUID.randomUUID();
        CommerceOrder order = new CommerceOrder();
        order.setId(orderId);
        order.setOrganizationId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setCartId(UUID.randomUUID());
        order.setDiscountCodeId(codeId);
        order.setDiscountPaise(100);
        order.setSubtotalPaise(1000);

        when(redemptionRepository.findByOrderId(orderId)).thenReturn(Optional.of(new com.prabhix.platform.commerce.domain.DiscountRedemption()));

        discountService.recordRedemptionOnCapture(order);

        verify(discountCodeRepository, never()).lockById(any());
    }

    @Test
    void captureRedemptionRecordsOnce() {
        UUID orderId = UUID.randomUUID();
        UUID codeId = UUID.randomUUID();
        CommerceOrder order = new CommerceOrder();
        order.setId(orderId);
        order.setOrganizationId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setCartId(UUID.randomUUID());
        order.setDiscountCodeId(codeId);
        order.setDiscountPaise(100);
        order.setSubtotalPaise(1000);

        DiscountCode code = new DiscountCode();
        code.setId(codeId);
        code.setDiscountType(DiscountType.PERCENTAGE);
        code.setPercentage(10);
        code.setUsesCount(0);
        code.setMinOrderPaise(0);
        code.setActive(true);
        code.setProductIds(java.util.List.of());
        code.setCategoryIds(java.util.List.of());

        when(redemptionRepository.findByOrderId(orderId))
                .thenReturn(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(new com.prabhix.platform.commerce.domain.DiscountRedemption()));
        when(discountCodeRepository.lockById(codeId)).thenReturn(Optional.of(code));
        when(redemptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        discountService.recordRedemptionOnCapture(order);
        discountService.recordRedemptionOnCapture(order);

        verify(discountCodeRepository, times(1)).lockById(codeId);
    }
}
