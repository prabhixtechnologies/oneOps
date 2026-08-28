package com.prabhix.platform.commerce.dto;

import com.prabhix.platform.commerce.domain.CommerceEnums;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CommerceDtos {

    private CommerceDtos() {
    }

    // --- Catalog ---

    public record ProductSummary(
            UUID id,
            String slug,
            String name,
            String tagline,
            CommerceEnums.ProductType productType,
            boolean featured,
            UUID heroImageFileId,
            String heroImageUrl,
            long fromPriceMinor,
            String currency) {
    }

    public record VariantView(
            UUID id,
            String name,
            String sku,
            long priceMinor,
            Long compareAtPriceMinor,
            String currency,
            boolean trackInventory,
            Integer stockAvailable,
            CommerceEnums.BillingInterval billingInterval,
            UUID downloadFileId,
            Integer serviceDurationDays,
            Integer deliverySlaDays,
            boolean active) {
    }

    public record ProductDetail(
            UUID id,
            String slug,
            String name,
            String tagline,
            String description,
            CommerceEnums.ProductType productType,
            CommerceEnums.ProductStatus status,
            boolean featured,
            UUID heroImageFileId,
            String heroImageUrl,
            List<UUID> galleryFileIds,
            List<String> galleryImageUrls,
            String seoTitle,
            String seoDescription,
            String hsnCode,
            Map<String, Object> attributes,
            List<VariantView> variants,
            List<UUID> categoryIds,
            Instant publishedAt) {
    }

    public record CreateProductRequest(
            @NotBlank @Size(max = 120) String slug,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 300) String tagline,
            String description,
            @NotNull CommerceEnums.ProductType productType,
            String hsnCode,
            Map<String, Object> attributes,
            /** Optional. Defaults to DRAFT so a half-built product cannot reach the storefront. */
            CommerceEnums.ProductStatus status) {
    }

    public record UpdateProductRequest(
            @Size(max = 200) String name,
            @Size(max = 300) String tagline,
            String description,
            CommerceEnums.ProductStatus status,
            Boolean featured,
            Integer sortOrder,
            UUID heroImageFileId,
            List<UUID> galleryFileIds,
            String seoTitle,
            String seoDescription,
            String hsnCode,
            Map<String, Object> attributes,
            List<UUID> categoryIds) {
    }

    public record CreateVariantRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 80) String sku,
            @Min(0) long priceMinor,
            Long compareAtPriceMinor,
            Boolean trackInventory,
            Integer stockOnHand,
            CommerceEnums.BillingInterval billingInterval,
            UUID downloadFileId,
            String licenseTerms,
            Integer serviceDurationDays,
            Integer deliverySlaDays) {
    }

    public record UpdateVariantRequest(
            @Size(max = 200) String name,
            @Size(max = 80) String sku,
            @Min(0) Long priceMinor,
            Long compareAtPriceMinor,
            Boolean trackInventory,
            Integer stockOnHand,
            CommerceEnums.BillingInterval billingInterval,
            UUID downloadFileId,
            String licenseTerms,
            Integer serviceDurationDays,
            Integer deliverySlaDays,
            Boolean active) {
    }

    public record CategoryView(UUID id, String slug, String name, String description, int sortOrder) {
    }

    public record CreateCategoryRequest(
            @NotBlank @Size(max = 120) String slug,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description,
            Integer sortOrder) {
    }

    // --- Cart ---

    public record CartItemView(
            UUID id,
            UUID variantId,
            String productName,
            String variantName,
            String sku,
            int quantity,
            long unitPriceMinor,
            long lineTotalMinor) {
    }

    public record CartView(
            String cartToken,
            String currency,
            List<CartItemView> items,
            long subtotalMinor,
            long discountMinor,
            long taxMinor,
            long shippingMinor,
            long totalMinor,
            String discountCode,
            Instant expiresAt) {
    }

    public record CreateCartResponse(String cartToken, CartView cart) {
    }

    public record AddCartItemRequest(@NotNull UUID variantId, @Min(1) int quantity) {
    }

    public record UpdateCartItemRequest(@Min(1) int quantity) {
    }

    public record ApplyDiscountRequest(@NotBlank String code) {
    }

    // --- Checkout ---

    public record AddressRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank @Size(max = 12) String pincode,
            @Size(max = 32) String phone) {
    }

    public record CheckoutRequest(
            @NotBlank @Email String email,
            @Size(max = 160) String name,
            @Size(max = 32) String phone,
            boolean marketingConsent,
            UUID visitorId,
            @NotNull AddressRequest billingAddress,
            AddressRequest shippingAddress) {
    }

    public record CheckoutResponse(
            UUID orderId,
            String orderNumber,
            String accessToken,
            long totalMinor,
            String currency,
            String razorpayOrderId,
            String razorpayKeyId,
            Map<String, String> razorpayNotes,
            boolean subscriptionCheckout) {
    }

    public record VerifyPaymentRequest(
            @NotBlank String razorpayOrderId,
            @NotBlank String razorpayPaymentId,
            @NotBlank String razorpaySignature) {
    }

    public record VerifyPaymentResponse(UUID orderId, String orderNumber, String status) {
    }

    // --- Orders ---

    public record OrderSummary(
            UUID id,
            String orderNumber,
            CommerceEnums.OrderStatus status,
            long totalMinor,
            String currency,
            String customerEmail,
            Instant createdAt,
            Instant paidAt) {
    }

    public record OrderItemView(
            UUID id,
            String productName,
            String variantName,
            String sku,
            CommerceEnums.ProductType productType,
            int quantity,
            long unitPriceMinor,
            long lineSubtotalMinor) {
    }

    public record OrderAddressView(
            CommerceEnums.AddressType addressType,
            String name,
            String line1,
            String line2,
            String city,
            String state,
            String pincode,
            String phone) {
    }

    public record OrderEventView(String eventType, String message, Instant createdAt) {
    }

    public record OrderDetail(
            UUID id,
            String orderNumber,
            CommerceEnums.OrderStatus status,
            String accessToken,
            long subtotalMinor,
            long discountMinor,
            long cgstMinor,
            long sgstMinor,
            long igstMinor,
            long shippingMinor,
            long totalMinor,
            String currency,
            String customerEmail,
            String customerName,
            List<OrderItemView> items,
            List<OrderAddressView> addresses,
            List<OrderEventView> events,
            UUID invoiceId,
            Instant paidAt,
            Instant fulfilledAt,
            String internalNote,
            List<ShipmentView> shipments) {
    }

    public record UpdateOrderRequest(String internalNote) {
    }

    public record FulfillOrderRequest(
            @Size(max = 80) String carrier,
            @Size(max = 120) String trackingNumber) {
    }

    public record ShipmentView(
            UUID orderItemId,
            CommerceEnums.ShipmentStatus status,
            String carrier,
            String trackingNumber,
            Instant shippedAt) {
    }

    public record RefundRequest(UUID orderId, Long amountMinor) {
    }

    public record RefundView(UUID paymentId, long refundedMinor, long totalRefundedMinor, String status) {
    }

    // --- Downloads ---

    public record DownloadView(
            UUID orderItemId,
            String productName,
            int downloadCount,
            int maxDownloadCount,
            Instant linkExpiresAt,
            String downloadUrl) {
    }

    public record DownloadLinkResponse(String downloadUrl, Instant expiresAt) {
    }

    // --- Customers ---

    public record CustomerSummary(
            UUID id,
            String email,
            String name,
            String phone,
            boolean marketingConsent,
            Instant createdAt) {
    }

    public record CustomerDetail(
            UUID id,
            String email,
            String name,
            String phone,
            boolean marketingConsent,
            UUID visitorId,
            Instant createdAt,
            List<OrderSummary> orders) {
    }

    // --- Discounts ---

    public record DiscountView(
            UUID id,
            String code,
            String description,
            CommerceEnums.DiscountType discountType,
            Integer percentage,
            Long amountMinor,
            long minOrderMinor,
            Integer maxUsesTotal,
            Integer maxUsesPerCustomer,
            int usesCount,
            Instant validFrom,
            Instant validUntil,
            List<UUID> productIds,
            List<UUID> categoryIds,
            boolean active) {
    }

    public record CreateDiscountRequest(
            @NotBlank @Size(max = 40) String code,
            @Size(max = 300) String description,
            @NotNull CommerceEnums.DiscountType discountType,
            Integer percentage,
            Long amountMinor,
            @Min(0) Long minOrderMinor,
            Integer maxUsesTotal,
            Integer maxUsesPerCustomer,
            Instant validFrom,
            Instant validUntil,
            List<UUID> productIds,
            List<UUID> categoryIds) {
    }

    public record UpdateDiscountRequest(
            String description,
            Boolean active,
            Instant validFrom,
            Instant validUntil,
            Integer maxUsesTotal) {
    }

    // --- Settings & dashboard ---

    public record SettingsView(
            String sellerState,
            String sellerName,
            String sellerGstin,
            String sellerAddress,
            String orderNumberPrefix,
            int gstPercent,
            long flatShippingMinor,
            Long freeShippingAboveMinor) {
    }

    public record UpdateSettingsRequest(
            String sellerState,
            String sellerName,
            String sellerGstin,
            String sellerAddress,
            String orderNumberPrefix,
            Integer gstPercent,
            Long flatShippingMinor,
            Long freeShippingAboveMinor) {
    }

    public record DashboardView(
            long revenueMinor30d,
            long orderCount30d,
            List<TopProductView> topProducts,
            double conversionRate) {
    }

    public record TopProductView(UUID productId, String productName, long quantitySold) {
    }
}
