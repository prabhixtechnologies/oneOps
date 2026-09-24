package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.DiscountCode;
import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.repository.DiscountCodeRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.security.PrabhixPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscountAdminService {

    private final DiscountCodeRepository discountCodeRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<CommerceDtos.DiscountView> list(UUID organizationId) {
        return discountCodeRepository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(organizationId)
                .stream().map(this::toView).toList();
    }

    @Transactional
    public CommerceDtos.DiscountView create(PrabhixPrincipal principal, CommerceDtos.CreateDiscountRequest request) {
        UUID orgId = principal.requireOrganizationId();
        if (discountCodeRepository.findByOrganizationIdAndCodeIgnoreCaseAndDeletedAtIsNull(
                orgId, request.code()).isPresent()) {
            throw ApiException.conflict("A discount code with that value already exists");
        }
        DiscountCode discount = new DiscountCode();
        discount.setOrganizationId(orgId);
        applyCreate(discount, request);
        discount = discountCodeRepository.save(discount);
        events.publishEvent(AuditRequested.of(orgId, principal.userId(),
                "commerce.discount.created", "commerce_discount", discount.getId()));
        return toView(discount);
    }

    @Transactional
    public CommerceDtos.DiscountView update(PrabhixPrincipal principal, UUID discountId,
                                            CommerceDtos.UpdateDiscountRequest request) {
        UUID orgId = principal.requireOrganizationId();
        DiscountCode discount = discountCodeRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(discountId, orgId)
                .orElseThrow(() -> ApiException.notFound("Discount code"));
        if (request.description() != null) {
            discount.setDescription(request.description());
        }
        if (request.active() != null) {
            discount.setActive(request.active());
        }
        if (request.validFrom() != null) {
            discount.setValidFrom(request.validFrom());
        }
        if (request.validUntil() != null) {
            discount.setValidUntil(request.validUntil());
        }
        if (request.maxUsesTotal() != null) {
            discount.setMaxUsesTotal(request.maxUsesTotal());
        }
        discount = discountCodeRepository.save(discount);
        return toView(discount);
    }

    private void applyCreate(DiscountCode discount, CommerceDtos.CreateDiscountRequest request) {
        discount.setCode(request.code().toUpperCase());
        discount.setDescription(request.description());
        discount.setDiscountType(request.discountType());
        discount.setPercentage(request.percentage());
        discount.setAmountPaise(request.amountPaise());
        if (request.minOrderPaise() != null) {
            discount.setMinOrderPaise(request.minOrderPaise());
        }
        discount.setMaxUsesTotal(request.maxUsesTotal());
        discount.setMaxUsesPerCustomer(request.maxUsesPerCustomer());
        discount.setValidFrom(request.validFrom());
        discount.setValidUntil(request.validUntil());
        if (request.productIds() != null) {
            discount.setProductIds(request.productIds());
        }
        if (request.categoryIds() != null) {
            discount.setCategoryIds(request.categoryIds());
        }
    }

    private CommerceDtos.DiscountView toView(DiscountCode discount) {
        return new CommerceDtos.DiscountView(
                discount.getId(),
                discount.getCode(),
                discount.getDescription(),
                discount.getDiscountType(),
                discount.getPercentage(),
                discount.getAmountPaise(),
                discount.getMinOrderPaise(),
                discount.getMaxUsesTotal(),
                discount.getMaxUsesPerCustomer(),
                discount.getUsesCount(),
                discount.getValidFrom(),
                discount.getValidUntil(),
                discount.getProductIds(),
                discount.getCategoryIds(),
                discount.isActive());
    }
}
