package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.config.CommerceProperties;
import com.prabhix.platform.commerce.domain.Cart;
import com.prabhix.platform.commerce.domain.CartItem;
import com.prabhix.platform.commerce.domain.CommerceSettings;
import com.prabhix.platform.commerce.domain.DiscountCode;
import com.prabhix.platform.commerce.domain.Product;
import com.prabhix.platform.commerce.domain.ProductVariant;
import com.prabhix.platform.commerce.domain.CommerceEnums.ProductStatus;
import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.repository.CartItemRepository;
import com.prabhix.platform.commerce.repository.CartRepository;
import com.prabhix.platform.commerce.repository.DiscountCodeRepository;
import com.prabhix.platform.commerce.repository.ProductRepository;
import com.prabhix.platform.commerce.repository.ProductVariantRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final DiscountService discountService;
    private final CommerceSettingsService settingsService;
    private final CommerceProperties properties;

    @Transactional
    public CommerceDtos.CreateCartResponse createCart(UUID organizationId, UUID visitorId) {
        Cart cart = new Cart();
        cart.setOrganizationId(organizationId);
        cart.setCartToken(CommerceTokens.opaqueToken());
        cart.setVisitorId(visitorId);
        cart.setCurrency(properties.currency());
        cart.setExpiresAt(Instant.now().plus(properties.cartTtl()));
        cart = cartRepository.save(cart);
        recalculate(cart);
        return new CommerceDtos.CreateCartResponse(cart.getCartToken(), toView(cart));
    }

    @Transactional(readOnly = true)
    public CommerceDtos.CartView getCart(UUID organizationId, String cartToken) {
        Cart cart = loadActiveCart(organizationId, cartToken);
        return toView(cart);
    }

    @Transactional
    public CommerceDtos.CartView addItem(UUID organizationId, String cartToken,
                                         CommerceDtos.AddCartItemRequest request) {
        Cart cart = loadActiveCart(organizationId, cartToken);
        ProductVariant variant = variantRepository
                .findByIdAndOrganizationIdAndDeletedAtIsNull(request.variantId(), organizationId)
                .filter(ProductVariant::isActive)
                .orElseThrow(() -> ApiException.of(ErrorCode.VARIANT_NOT_FOUND, "That product variant was not found"));
        Product product = productRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(
                        variant.getProductId(), organizationId)
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(() -> ApiException.of(ErrorCode.PRODUCT_UNAVAILABLE, "That product is not available"));

        CartItem item = cartItemRepository
                .findByCartIdAndVariantIdAndOrganizationId(cart.getId(), variant.getId(), organizationId)
                .orElseGet(() -> {
                    CartItem created = new CartItem();
                    created.setOrganizationId(organizationId);
                    created.setCartId(cart.getId());
                    created.setVariantId(variant.getId());
                    created.setQuantity(0);
                    return created;
                });
        item.setQuantity(item.getQuantity() + request.quantity());
        item.setUnitPriceMinor(variant.getPriceMinor());
        item.setLineTotalMinor(CommerceAmountCalculator.lineTotal(variant.getPriceMinor(), item.getQuantity()));
        cartItemRepository.save(item);
        cart.setExpiresAt(Instant.now().plus(properties.cartTtl()));
        recalculate(cart);
        return toView(cart);
    }

    @Transactional
    public CommerceDtos.CartView updateItem(UUID organizationId, String cartToken, UUID itemId,
                                            CommerceDtos.UpdateCartItemRequest request) {
        Cart cart = loadActiveCart(organizationId, cartToken);
        CartItem item = cartItemRepository.findById(itemId)
                .filter(i -> i.getOrganizationId().equals(organizationId) && i.getCartId().equals(cart.getId()))
                .orElseThrow(() -> ApiException.notFound("Cart item"));
        ProductVariant variant = variantRepository.findById(item.getVariantId())
                .orElseThrow(() -> ApiException.of(ErrorCode.VARIANT_NOT_FOUND, "That product variant was not found"));
        item.setQuantity(request.quantity());
        item.setUnitPriceMinor(variant.getPriceMinor());
        item.setLineTotalMinor(CommerceAmountCalculator.lineTotal(variant.getPriceMinor(), item.getQuantity()));
        cartItemRepository.save(item);
        recalculate(cart);
        return toView(cart);
    }

    @Transactional
    public CommerceDtos.CartView removeItem(UUID organizationId, String cartToken, UUID itemId) {
        Cart cart = loadActiveCart(organizationId, cartToken);
        cartItemRepository.findById(itemId)
                .filter(i -> i.getOrganizationId().equals(organizationId) && i.getCartId().equals(cart.getId()))
                .ifPresent(cartItemRepository::delete);
        recalculate(cart);
        return toView(cart);
    }

    @Transactional
    public CommerceDtos.CartView applyDiscount(UUID organizationId, String cartToken,
                                                 CommerceDtos.ApplyDiscountRequest request) {
        Cart cart = loadActiveCart(organizationId, cartToken);
        DiscountCode discount = discountService.findActiveCode(organizationId, request.code());
        cart.setDiscountCodeId(discount.getId());
        recalculate(cart);
        return toView(cart);
    }

    @Transactional
    public CommerceDtos.CartView clearDiscount(UUID organizationId, String cartToken) {
        Cart cart = loadActiveCart(organizationId, cartToken);
        cart.setDiscountCodeId(null);
        recalculate(cart);
        return toView(cart);
    }

    Cart loadActiveCart(UUID organizationId, String cartToken) {
        Cart cart = cartRepository.findByCartTokenAndOrganizationId(cartToken, organizationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CART_NOT_FOUND, "That cart was not found"));
        if (cart.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.of(ErrorCode.CART_NOT_FOUND, "That cart has expired");
        }
        return cart;
    }

    void recalculate(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartIdAndOrganizationId(
                cart.getId(), cart.getOrganizationId());
        long subtotal = 0;
        List<UUID> productIds = new ArrayList<>();
        for (CartItem item : items) {
            ProductVariant variant = variantRepository.findById(item.getVariantId()).orElse(null);
            if (variant != null) {
                item.setUnitPriceMinor(variant.getPriceMinor());
                item.setLineTotalMinor(CommerceAmountCalculator.lineTotal(variant.getPriceMinor(), item.getQuantity()));
                cartItemRepository.save(item);
                productIds.add(variant.getProductId());
            }
            subtotal += item.getLineTotalMinor();
        }
        long discount = 0;
        if (cart.getDiscountCodeId() != null) {
            DiscountCode discountCode = discountCodeRepository.findById(cart.getDiscountCodeId()).orElse(null);
            if (discountCode != null) {
                try {
                    discount = discountService.computeDiscountMinor(
                            cart.getOrganizationId(), discountCode, subtotal, productIds, null);
                } catch (ApiException ex) {
                    cart.setDiscountCodeId(null);
                    discount = 0;
                }
            }
        }
        CommerceSettings settings = settingsService.resolve(cart.getOrganizationId());
        long shipping = CommerceAmountCalculator.computeShippingMinor(
                subtotal - discount, settings.getFlatShippingMinor(), settings.getFreeShippingAboveMinor());
        var totals = CommerceAmountCalculator.computeOrderTotals(
                subtotal, discount, shipping, settings.getGstPercent(),
                settings.getSellerState(), settings.getSellerState());
        cart.setSubtotalMinor(subtotal);
        cart.setDiscountMinor(discount);
        cart.setTaxMinor(totals.cgstMinor() + totals.sgstMinor() + totals.igstMinor());
        cart.setShippingMinor(shipping);
        cart.setTotalMinor(totals.totalMinor());
        cartRepository.save(cart);
    }

    CommerceDtos.CartView toView(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartIdAndOrganizationId(
                cart.getId(), cart.getOrganizationId());
        String discountCode = null;
        if (cart.getDiscountCodeId() != null) {
            discountCode = discountCodeRepository.findById(cart.getDiscountCodeId())
                    .map(DiscountCode::getCode).orElse(null);
        }
        List<CommerceDtos.CartItemView> itemViews = items.stream().map(item -> {
            ProductVariant variant = variantRepository.findById(item.getVariantId()).orElse(null);
            Product product = variant == null ? null
                    : productRepository.findById(variant.getProductId()).orElse(null);
            return new CommerceDtos.CartItemView(
                    item.getId(),
                    item.getVariantId(),
                    product == null ? "" : product.getName(),
                    variant == null ? "" : variant.getName(),
                    variant == null ? "" : variant.getSku(),
                    item.getQuantity(),
                    item.getUnitPriceMinor(),
                    item.getLineTotalMinor());
        }).toList();
        return new CommerceDtos.CartView(
                cart.getCartToken(),
                cart.getCurrency(),
                itemViews,
                cart.getSubtotalMinor(),
                cart.getDiscountMinor(),
                cart.getTaxMinor(),
                cart.getShippingMinor(),
                cart.getTotalMinor(),
                discountCode,
                cart.getExpiresAt());
    }
}
