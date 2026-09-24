package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.config.CommerceProperties;
import com.prabhix.platform.commerce.domain.Cart;
import com.prabhix.platform.commerce.domain.CartItem;
import com.prabhix.platform.commerce.domain.ProductVariant;
import com.prabhix.platform.commerce.repository.CartItemRepository;
import com.prabhix.platform.commerce.repository.CartRepository;
import com.prabhix.platform.commerce.repository.DiscountCodeRepository;
import com.prabhix.platform.commerce.repository.ProductRepository;
import com.prabhix.platform.commerce.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartTotalRecalculationTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private ProductRepository productRepository;
    @Mock private DiscountCodeRepository discountCodeRepository;
    @Mock private DiscountService discountService;
    @Mock private CommerceSettingsService settingsService;

    private CartService cartService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        CommerceProperties properties = new CommerceProperties(
                "INR", Duration.ofHours(24), 5, Duration.ofDays(14),
                Duration.ofMinutes(15), List.of("http://localhost:3000"), 60);
        cartService = new CartService(
                cartRepository, cartItemRepository, variantRepository, productRepository,
                discountCodeRepository, discountService, settingsService, properties);
    }

    @Test
    void recalculateIgnoresStaleClientPrices() {
        Cart cart = new Cart();
        cart.setId(UUID.randomUUID());
        cart.setOrganizationId(orgId);
        cart.setCartToken("tok");
        cart.setExpiresAt(Instant.now().plusSeconds(3600));

        CartItem item = new CartItem();
        item.setVariantId(variantId);
        item.setQuantity(2);
        item.setUnitPricePaise(999);
        item.setLineTotalPaise(1998);

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setProductId(productId);
        variant.setPricePaise(2_500);
        variant.setActive(true);

        var settings = new com.prabhix.platform.commerce.domain.CommerceSettings();
        settings.setSellerState("Karnataka");
        settings.setGstPercent(18);

        when(cartItemRepository.findByCartIdAndOrganizationId(cart.getId(), orgId)).thenReturn(List.of(item));
        when(variantRepository.findAllById(any())).thenReturn(List.of(variant));
        when(settingsService.resolve(orgId)).thenReturn(settings);
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cartItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        cartService.recalculate(cart);

        assertEquals(5_000, cart.getSubtotalPaise());
        assertEquals(2_500, item.getUnitPricePaise());
        assertEquals(5_000, item.getLineTotalPaise());
    }
}
