package com.prabhix.platform.commerce.web;

import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.service.DiscountAdminService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oneops/commerce/discounts")
@RequiredArgsConstructor
public class CommerceDiscountController {

    private final DiscountAdminService discountAdminService;

    @GetMapping
    @PreAuthorize(Authorize.COMMERCE_DISCOUNT_MANAGE)
    public List<CommerceDtos.DiscountView> list(@CurrentUser PrabhixPrincipal principal) {
        return discountAdminService.list(principal.requireOrganizationId());
    }

    @PostMapping
    @PreAuthorize(Authorize.COMMERCE_DISCOUNT_MANAGE)
    public CommerceDtos.DiscountView create(@CurrentUser PrabhixPrincipal principal,
                                            @Valid @RequestBody CommerceDtos.CreateDiscountRequest request) {
        return discountAdminService.create(principal, request);
    }

    @PatchMapping
    @PreAuthorize(Authorize.COMMERCE_DISCOUNT_MANAGE)
    public CommerceDtos.DiscountView update(@CurrentUser PrabhixPrincipal principal,
                                            @RequestParam UUID id,
                                            @Valid @RequestBody CommerceDtos.UpdateDiscountRequest request) {
        return discountAdminService.update(principal, id, request);
    }
}
