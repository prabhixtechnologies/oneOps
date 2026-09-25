package com.prabhix.platform.commerce.web;

import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.service.ProductCatalogService;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oneops/commerce/products")
@RequiredArgsConstructor
public class CommerceProductController {

    private final ProductCatalogService catalogService;

    @GetMapping
    @PreAuthorize(Authorize.COMMERCE_CATALOG_READ)
    public CursorPage<CommerceDtos.ProductSummary> list(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return catalogService.listAdmin(
                principal.requireOrganizationId(), status, featured, type, cursor, limit);
    }

    @GetMapping(params = "id")
    @PreAuthorize(Authorize.COMMERCE_CATALOG_READ)
    public CommerceDtos.ProductDetail get(@CurrentUser PrabhixPrincipal principal, @RequestParam UUID id) {
        return catalogService.getAdmin(principal.requireOrganizationId(), id);
    }

    @PostMapping
    @PreAuthorize(Authorize.COMMERCE_CATALOG_MANAGE)
    public CommerceDtos.ProductDetail create(@CurrentUser PrabhixPrincipal principal,
                                             @Valid @RequestBody CommerceDtos.CreateProductRequest request) {
        return catalogService.create(principal, request);
    }

    @PutMapping
    @PreAuthorize(Authorize.COMMERCE_CATALOG_MANAGE)
    public CommerceDtos.ProductDetail update(@CurrentUser PrabhixPrincipal principal,
                                             @RequestParam UUID id,
                                             @Valid @RequestBody CommerceDtos.UpdateProductRequest request) {
        return catalogService.update(principal, id, request);
    }

    @PostMapping("/variants")
    @PreAuthorize(Authorize.COMMERCE_CATALOG_MANAGE)
    public CommerceDtos.VariantView createVariant(@CurrentUser PrabhixPrincipal principal,
                                                  @RequestParam UUID id,
                                                  @Valid @RequestBody CommerceDtos.CreateVariantRequest request) {
        return catalogService.createVariant(principal, id, request);
    }

    @PutMapping("/variants")
    @PreAuthorize(Authorize.COMMERCE_CATALOG_MANAGE)
    public CommerceDtos.VariantView updateVariant(@CurrentUser PrabhixPrincipal principal,
                                                  @RequestParam UUID productId,
                                                  @RequestParam UUID variantId,
                                                  @Valid @RequestBody CommerceDtos.UpdateVariantRequest request) {
        return catalogService.updateVariant(principal, productId, variantId, request);
    }

    @DeleteMapping("/variants")
    @PreAuthorize(Authorize.COMMERCE_CATALOG_MANAGE)
    public void archiveVariant(@CurrentUser PrabhixPrincipal principal,
                               @RequestParam UUID productId,
                               @RequestParam UUID variantId) {
        catalogService.archiveVariant(principal, productId, variantId);
    }

    @GetMapping("/categories")
    @PreAuthorize(Authorize.COMMERCE_CATALOG_READ)
    public List<CommerceDtos.CategoryView> categories(@CurrentUser PrabhixPrincipal principal) {
        return catalogService.listCategories(principal.requireOrganizationId());
    }

    @PostMapping("/categories")
    @PreAuthorize(Authorize.COMMERCE_CATALOG_MANAGE)
    public CommerceDtos.CategoryView createCategory(@CurrentUser PrabhixPrincipal principal,
                                                    @Valid @RequestBody CommerceDtos.CreateCategoryRequest request) {
        return catalogService.createCategory(principal, request);
    }
}
