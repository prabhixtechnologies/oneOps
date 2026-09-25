package com.prabhix.platform.commerce.web;

import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.service.CartService;
import com.prabhix.platform.commerce.service.CheckoutService;
import com.prabhix.platform.commerce.service.CommerceOrgResolver;
import com.prabhix.platform.commerce.service.CommerceInvoiceService;
import com.prabhix.platform.commerce.service.CommerceOrderService;
import com.prabhix.platform.commerce.service.CommercePaymentCompletionService;
import com.prabhix.platform.commerce.service.CommerceRateLimiter;
import com.prabhix.platform.commerce.service.DownloadService;
import com.prabhix.platform.commerce.service.ProductCatalogService;
import com.prabhix.platform.commerce.service.PublicProductImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Commerce (public)", description = "Storefront catalog, cart, and checkout")
@RestController
@RequestMapping("/api/v1/oneops/commerce/public")
@RequiredArgsConstructor
public class CommercePublicController {

    private final CommerceOrgResolver orgResolver;
    private final CommerceRateLimiter rateLimiter;
    private final ProductCatalogService catalogService;
    private final CartService cartService;
    private final CheckoutService checkoutService;
    private final CommercePaymentCompletionService paymentCompletionService;
    private final CommerceOrderService orderService;
    private final DownloadService downloadService;
    private final PublicProductImageService publicImageService;
    private final CommerceInvoiceService invoiceService;

    @GetMapping("/products")
    @Operation(summary = "Browse active products")
    public com.prabhix.platform.common.web.CursorPage<CommerceDtos.ProductSummary> products(
            @RequestParam String orgSlug,
            HttpServletRequest http,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID category,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        rateLimiter.checkIp(clientIp(http));
        return orgResolver.runAs(orgSlug, () ->
                catalogService.listPublic(orgResolver.resolveId(orgSlug), orgSlug,
                        search, type, category, sort, cursor, limit));
    }

    @GetMapping(value = "/products", params = "slug")
    @Operation(summary = "Product detail by slug")
    public CommerceDtos.ProductDetail product(@RequestParam String orgSlug,
                                              @RequestParam String slug,
                                              HttpServletRequest http) {
        rateLimiter.checkIp(clientIp(http));
        return orgResolver.runAs(orgSlug, () ->
                catalogService.getPublicBySlug(orgResolver.resolveId(orgSlug), orgSlug, slug));
    }

    @GetMapping("/images")
    @Operation(summary = "Public product image redirect")
    public org.springframework.http.ResponseEntity<Void> productImage(@RequestParam String orgSlug,
                                                                      @RequestParam UUID fileId,
                                                                      HttpServletRequest http) {
        rateLimiter.checkIp(clientIp(http));
        return orgResolver.runAs(orgSlug, () ->
                publicImageService.redirectImage(orgResolver.resolveId(orgSlug), fileId));
    }

    @PostMapping("/carts")
    @Operation(summary = "Create a guest cart")
    public CommerceDtos.CreateCartResponse createCart(@RequestParam String orgSlug,
                                                      HttpServletRequest http,
                                                      @RequestParam(required = false) UUID visitorId) {
        rateLimiter.checkIp(clientIp(http));
        return orgResolver.runAs(orgSlug, () ->
                cartService.createCart(orgResolver.resolveId(orgSlug), visitorId));
    }

    @GetMapping("/carts")
    public CommerceDtos.CartView getCart(@RequestParam String orgSlug,
                                         @RequestParam String cartToken,
                                         HttpServletRequest http) {
        rateLimiter.checkIp(clientIp(http));
        return orgResolver.runAs(orgSlug, () ->
                cartService.getCart(orgResolver.resolveId(orgSlug), cartToken));
    }

    @PostMapping("/carts/items")
    public CommerceDtos.CartView addItem(@RequestParam String orgSlug,
                                         @RequestParam String cartToken,
                                         HttpServletRequest http,
                                         @Valid @RequestBody CommerceDtos.AddCartItemRequest request) {
        rateLimiter.checkIp(clientIp(http));
        return orgResolver.runAs(orgSlug, () ->
                cartService.addItem(orgResolver.resolveId(orgSlug), cartToken, request));
    }

    @PutMapping("/carts/items")
    public CommerceDtos.CartView updateItem(@RequestParam String orgSlug,
                                            @RequestParam String cartToken,
                                            @RequestParam UUID itemId,
                                            HttpServletRequest http,
                                            @Valid @RequestBody CommerceDtos.UpdateCartItemRequest request) {
        rateLimiter.checkIp(clientIp(http));
        return orgResolver.runAs(orgSlug, () ->
                cartService.updateItem(orgResolver.resolveId(orgSlug), cartToken, itemId, request));
    }

    @DeleteMapping("/carts/items")
    public CommerceDtos.CartView removeItem(@RequestParam String orgSlug,
                                            @RequestParam String cartToken,
                                            @RequestParam UUID itemId,
                                            HttpServletRequest http) {
        rateLimiter.checkIp(clientIp(http));
        return orgResolver.runAs(orgSlug, () ->
                cartService.removeItem(orgResolver.resolveId(orgSlug), cartToken, itemId));
    }

    @PostMapping("/carts/discount")
    public CommerceDtos.CartView applyDiscount(@RequestParam String orgSlug,
                                               @RequestParam String cartToken,
                                               HttpServletRequest http,
                                               @Valid @RequestBody CommerceDtos.ApplyDiscountRequest request) {
        rateLimiter.checkIp(clientIp(http));
        return orgResolver.runAs(orgSlug, () ->
                cartService.applyDiscount(orgResolver.resolveId(orgSlug), cartToken, request));
    }

    @DeleteMapping("/carts/discount")
    public CommerceDtos.CartView clearDiscount(@RequestParam String orgSlug,
                                               @RequestParam String cartToken,
                                               HttpServletRequest http) {
        rateLimiter.checkIp(clientIp(http));
        return orgResolver.runAs(orgSlug, () ->
                cartService.clearDiscount(orgResolver.resolveId(orgSlug), cartToken));
    }

    @PostMapping("/carts/checkout")
    public CommerceDtos.CheckoutResponse checkout(@RequestParam String orgSlug,
                                                  @RequestParam String cartToken,
                                                  HttpServletRequest http,
                                                  @Valid @RequestBody CommerceDtos.CheckoutRequest request) {
        rateLimiter.checkIp(clientIp(http));
        return orgResolver.runAs(orgSlug, () ->
                checkoutService.startCheckout(orgResolver.resolveId(orgSlug), cartToken, request));
    }

    @PostMapping("/payments/verify")
    public CommerceDtos.VerifyPaymentResponse verifyPayment(@RequestParam String orgSlug,
                                                              HttpServletRequest http,
                                                              @Valid @RequestBody CommerceDtos.VerifyPaymentRequest request) {
        rateLimiter.checkIp(clientIp(http));
        UUID orgId = orgResolver.resolveId(orgSlug);
        var order = orgResolver.runAs(orgSlug, () ->
                paymentCompletionService.verifyCheckoutPayment(orgId, request));
        return new CommerceDtos.VerifyPaymentResponse(
                order.getId(), order.getOrderNumber(), order.getStatus().name());
    }

    @GetMapping("/orders/downloads")
    @Operation(summary = "List digital downloads for a paid order")
    public java.util.List<CommerceDtos.DownloadView> orderDownloads(@RequestParam String orgSlug,
                                                                     @RequestParam String accessToken,
                                                                     HttpServletRequest http) {
        rateLimiter.checkIp(clientIp(http));
        return orgResolver.runAs(orgSlug, () ->
                downloadService.listForAccessToken(accessToken));
    }

    @GetMapping("/orders/invoice")
    @Operation(summary = "Download order invoice PDF")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> orderInvoice(
            @RequestParam String orgSlug,
            @RequestParam String accessToken,
            HttpServletRequest http) {
        rateLimiter.checkIp(clientIp(http));
        return orgResolver.runAs(orgSlug, () ->
                invoiceService.downloadPdfByAccessToken(accessToken));
    }

    @GetMapping("/orders")
    public CommerceDtos.OrderDetail order(@RequestParam String orgSlug,
                                          @RequestParam String accessToken,
                                          HttpServletRequest http) {
        rateLimiter.checkIp(clientIp(http));
        return orderService.getByAccessToken(accessToken);
    }

    @GetMapping("/downloads")
    public Object download(@RequestParam String orgSlug,
                           @RequestParam String downloadToken,
                           HttpServletRequest http) {
        rateLimiter.checkIp(clientIp(http));
        CommerceDtos.DownloadLinkResponse link = orgResolver.runAs(orgSlug, () ->
                downloadService.issueDownload(downloadToken));
        String accept = http.getHeader("Accept");
        boolean wantsJson = accept != null
                && accept.contains("application/json")
                && !accept.contains("text/html");
        if (wantsJson) {
            return link;
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", link.downloadUrl())
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
