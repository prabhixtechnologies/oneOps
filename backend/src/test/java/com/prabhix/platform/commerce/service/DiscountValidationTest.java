package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.DiscountCode;
import com.prabhix.platform.commerce.domain.CommerceEnums.DiscountType;
import com.prabhix.platform.commerce.repository.DiscountCodeRepository;
import com.prabhix.platform.commerce.repository.DiscountRedemptionRepository;
import com.prabhix.platform.commerce.repository.ProductCategoryRepository;
import com.prabhix.platform.commerce.repository.ProductRepository;
import com.prabhix.platform.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class DiscountValidationTest {

    @Mock private DiscountCodeRepository discountCodeRepository;
    @Mock private DiscountRedemptionRepository redemptionRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductCategoryRepository categoryRepository;

    private DiscountService discountService;

    @BeforeEach
    void setUp() {
        discountService = new DiscountService(
                discountCodeRepository, redemptionRepository, productRepository, categoryRepository);
    }

    @Test
    void rejectsExpiredCode() {
        DiscountCode code = new DiscountCode();
        code.setDiscountType(DiscountType.FIXED_AMOUNT);
        code.setAmountMinor(500L);
        code.setMinOrderMinor(0);
        code.setValidUntil(Instant.now().minusSeconds(60));

        assertThrows(ApiException.class, () ->
                discountService.validateRedemption(UUID.randomUUID(), code, 5000, List.of(), null));
    }

    @Test
    void rejectsBelowMinimumOrder() {
        DiscountCode code = new DiscountCode();
        code.setDiscountType(DiscountType.PERCENTAGE);
        code.setPercentage(10);
        code.setMinOrderMinor(10_000);

        assertThrows(ApiException.class, () ->
                discountService.validateRedemption(UUID.randomUUID(), code, 5000, List.of(), null));
    }
}
