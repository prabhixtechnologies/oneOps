package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.DiscountCode;
import com.prabhix.platform.commerce.domain.CommerceEnums.DiscountType;
import com.prabhix.platform.commerce.domain.Product;
import com.prabhix.platform.commerce.repository.DiscountCodeRepository;
import com.prabhix.platform.commerce.repository.DiscountRedemptionRepository;
import com.prabhix.platform.commerce.repository.ProductCategoryRepository;
import com.prabhix.platform.commerce.repository.ProductRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountCodeRepository discountCodeRepository;
    private final DiscountRedemptionRepository redemptionRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public DiscountCode findActiveCode(UUID organizationId, String code) {
        return discountCodeRepository.findByOrganizationIdAndCodeIgnoreCaseAndDeletedAtIsNull(organizationId, code)
                .filter(DiscountCode::isActive)
                .orElseThrow(() -> ApiException.of(ErrorCode.DISCOUNT_INVALID, "That discount code is not valid"));
    }

    @Transactional(readOnly = true)
    public long computeDiscountPaise(
            UUID organizationId,
            DiscountCode discount,
            long subtotalPaise,
            List<UUID> variantProductIds,
            UUID customerId) {
        validateRedemption(organizationId, discount, subtotalPaise, variantProductIds, customerId);
        return CommerceAmountCalculator.computeDiscountAmount(
                discount.getDiscountType(),
                discount.getPercentage(),
                discount.getAmountPaise(),
                subtotalPaise);
    }

    void validateRedemption(
            UUID organizationId,
            DiscountCode discount,
            long subtotalPaise,
            List<UUID> productIds,
            UUID customerId) {
        Instant now = Instant.now();
        if (discount.getValidFrom() != null && now.isBefore(discount.getValidFrom())) {
            throw ApiException.of(ErrorCode.DISCOUNT_INVALID, "That discount code is not active yet");
        }
        if (discount.getValidUntil() != null && now.isAfter(discount.getValidUntil())) {
            throw ApiException.of(ErrorCode.DISCOUNT_INVALID, "That discount code has expired");
        }
        if (subtotalPaise < discount.getMinOrderPaise()) {
            throw ApiException.of(ErrorCode.DISCOUNT_INVALID,
                    "Order total must be at least "
                            + CommerceAmountCalculator.formatMoneyInr(discount.getMinOrderPaise()));
        }
        if (discount.getMaxUsesTotal() != null && discount.getUsesCount() >= discount.getMaxUsesTotal()) {
            throw ApiException.of(ErrorCode.DISCOUNT_INVALID, "That discount code has reached its usage limit");
        }
        if (customerId != null && discount.getMaxUsesPerCustomer() != null) {
            long used = redemptionRepository.countByCodeAndCustomer(discount.getId(), customerId);
            if (used >= discount.getMaxUsesPerCustomer()) {
                throw ApiException.of(ErrorCode.DISCOUNT_INVALID,
                        "You have already used this discount code the maximum number of times");
            }
        }
        if (!discount.getProductIds().isEmpty()) {
            boolean matches = productIds.stream().anyMatch(discount.getProductIds()::contains);
            if (!matches) {
                throw ApiException.of(ErrorCode.DISCOUNT_INVALID,
                        "That discount code does not apply to items in your cart");
            }
        }
        if (!discount.getCategoryIds().isEmpty()) {
            boolean matches = productIds.stream().anyMatch(productId -> {
                List<UUID> categories = categoryRepository.findCategoryIdsForProduct(productId);
                return categories.stream().anyMatch(discount.getCategoryIds()::contains);
            });
            if (!matches) {
                throw ApiException.of(ErrorCode.DISCOUNT_INVALID,
                        "That discount code does not apply to items in your cart");
            }
        }
    }

    @Transactional
    public void recordRedemption(UUID organizationId, DiscountCode discount, UUID orderId,
                                 UUID cartId, UUID customerId, long amountPaise) {
        discount.setUsesCount(discount.getUsesCount() + 1);
        discountCodeRepository.save(discount);
        var redemption = new com.prabhix.platform.commerce.domain.DiscountRedemption();
        redemption.setOrganizationId(organizationId);
        redemption.setDiscountCodeId(discount.getId());
        redemption.setOrderId(orderId);
        redemption.setCartId(cartId);
        redemption.setCustomerId(customerId);
        redemption.setAmountPaise(amountPaise);
        redemptionRepository.save(redemption);
    }
}
