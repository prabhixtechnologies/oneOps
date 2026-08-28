package com.prabhix.platform.commerce.web;

import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.service.CommerceCustomerService;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/commerce/customers")
@RequiredArgsConstructor
public class CommerceCustomerController {

    private final CommerceCustomerService customerService;

    @GetMapping
    @PreAuthorize(Authorize.COMMERCE_CUSTOMER_READ)
    public CursorPage<CommerceDtos.CustomerSummary> list(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return customerService.list(principal.requireOrganizationId(), cursor, limit);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Authorize.COMMERCE_CUSTOMER_READ)
    public CommerceDtos.CustomerDetail get(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        return customerService.get(principal.requireOrganizationId(), id);
    }
}
