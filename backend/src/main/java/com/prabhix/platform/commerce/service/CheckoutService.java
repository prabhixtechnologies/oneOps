package com.prabhix.platform.commerce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.prabhix.platform.billing.razorpay.RazorpayClient;
import com.prabhix.platform.billing.service.BillingAmountCalculator;
import com.prabhix.platform.commerce.config.CommerceProperties;
import com.prabhix.platform.commerce.domain.Cart;
import com.prabhix.platform.commerce.domain.CartItem;
import com.prabhix.platform.commerce.domain.CommerceCustomer;
import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.CommercePayment;
import com.prabhix.platform.commerce.domain.CommerceSettings;
import com.prabhix.platform.commerce.domain.DiscountCode;
import com.prabhix.platform.commerce.domain.OrderAddress;
import com.prabhix.platform.commerce.domain.OrderEvent;
import com.prabhix.platform.commerce.domain.OrderItem;
import com.prabhix.platform.commerce.domain.Product;
import com.prabhix.platform.commerce.domain.ProductVariant;
import com.prabhix.platform.commerce.domain.CommerceEnums.AddressType;
import com.prabhix.platform.commerce.domain.CommerceEnums.OrderStatus;
import com.prabhix.platform.commerce.domain.CommerceEnums.PaymentStatus;
import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.repository.CartItemRepository;
import com.prabhix.platform.commerce.repository.CommerceCustomerRepository;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.commerce.repository.CommercePaymentRepository;
import com.prabhix.platform.commerce.repository.DiscountCodeRepository;
import com.prabhix.platform.commerce.repository.OrderAddressRepository;
import com.prabhix.platform.commerce.repository.OrderEventRepository;
import com.prabhix.platform.commerce.repository.OrderItemRepository;
import com.prabhix.platform.commerce.repository.ProductRepository;
import com.prabhix.platform.commerce.repository.ProductVariantRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final CartService cartService;
    private final CartItemRepository cartItemRepository;
    private final CommerceOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderAddressRepository addressRepository;
    private final OrderEventRepository eventRepository;
    private final CommerceCustomerRepository customerRepository;
    private final CommercePaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final DiscountService discountService;
    private final CommerceSettingsService settingsService;
    private final OrderNumberService orderNumberService;
    private final StockService stockService;
    private final RazorpayClient razorpayClient;
    private final PrabhixProperties properties;
    private final CommerceProperties commerceProperties;

    @Transactional
    public CommerceDtos.CheckoutResponse startCheckout(
            UUID organizationId,
            String cartToken,
            CommerceDtos.CheckoutRequest request) {
        Cart cart = cartService.loadActiveCart(organizationId, cartToken);
        List<CartItem> cartItems = cartItemRepository.findByCartIdAndOrganizationId(
                cart.getId(), organizationId);
        if (cartItems.isEmpty()) {
            throw ApiException.of(ErrorCode.CART_EMPTY, "Your cart is empty");
        }

        CommerceSettings settings = settingsService.resolve(organizationId);
        CommerceCustomer customer = upsertCustomer(organizationId, request);

        String buyerState = request.billingAddress().state();
        var totals = CommerceAmountCalculator.computeOrderTotals(
                cart.getSubtotalMinor(),
                cart.getDiscountMinor(),
                cart.getShippingMinor(),
                settings.getGstPercent(),
                buyerState,
                settings.getSellerState());

        LocalDate today = LocalDate.now();
        String fy = BillingAmountCalculator.financialYear(today);
        int sequence = orderNumberService.nextSequence(organizationId, fy);
        String orderNumber = BillingAmountCalculator.formatInvoiceNumber(
                settings.getOrderNumberPrefix(), fy, sequence);

        CommerceOrder order = new CommerceOrder();
        order.setOrganizationId(organizationId);
        order.setOrderNumber(orderNumber);
        order.setFinancialYear(fy);
        order.setSequenceNumber(sequence);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCustomerId(customer.getId());
        order.setCartId(cart.getId());
        order.setAccessToken(CommerceTokens.opaqueToken());
        order.setCurrency(cart.getCurrency());
        order.setSubtotalMinor(totals.subtotalMinor());
        order.setDiscountMinor(totals.discountMinor());
        order.setCgstMinor(totals.cgstMinor());
        order.setSgstMinor(totals.sgstMinor());
        order.setIgstMinor(totals.igstMinor());
        order.setShippingMinor(totals.shippingMinor());
        order.setTotalMinor(totals.totalMinor());
        order.setDiscountCodeId(cart.getDiscountCodeId());
        order.setBuyerState(buyerState);
        order.setSellerState(settings.getSellerState());
        order.setGstPercent(settings.getGstPercent());
        order.setStockHoldExpiresAt(Instant.now().plus(commerceProperties.stockHoldTtl()));
        order = orderRepository.save(order);

        List<OrderItem> orderItems = buildOrderItems(organizationId, order.getId(), cartItems);
        orderItemRepository.saveAll(orderItems);
        saveAddresses(organizationId, order.getId(), request);
        appendEvent(order, "ORDER_CREATED", "Checkout started");

        boolean subscriptionCheckout = orderItems.stream()
                .anyMatch(item -> item.getProductType() == com.prabhix.platform.commerce.domain.CommerceEnums.ProductType.SUBSCRIPTION);
        order.setSubscriptionCheckout(subscriptionCheckout);

        stockService.reserveForOrder(order, orderItems);

        CommercePayment payment = new CommercePayment();
        payment.setOrganizationId(organizationId);
        payment.setOrderId(order.getId());
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setAmountMinor(order.getTotalMinor());
        payment.setCurrency(order.getCurrency());
        paymentRepository.save(payment);

        JsonNode razorpayOrder = razorpayClient.createOrder(
                order.getTotalMinor(),
                order.getCurrency(),
                order.getOrderNumber(),
                subscriptionCheckout
                        ? Map.of(
                                "commerce_order_id", order.getId().toString(),
                                "organization_id", organizationId.toString(),
                                "subscription_checkout", "true")
                        : Map.of(
                                "commerce_order_id", order.getId().toString(),
                                "organization_id", organizationId.toString()));
        order.setRazorpayOrderId(razorpayOrder.path("id").asText());
        payment.setRazorpayOrderId(order.getRazorpayOrderId());
        orderRepository.save(order);
        paymentRepository.save(payment);

        if (cart.getDiscountCodeId() != null) {
            DiscountCode discount = discountCodeRepository.findById(cart.getDiscountCodeId()).orElseThrow();
            discountService.recordRedemption(
                    organizationId, discount, order.getId(), cart.getId(), customer.getId(), cart.getDiscountMinor());
        }

        return new CommerceDtos.CheckoutResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getAccessToken(),
                order.getTotalMinor(),
                order.getCurrency(),
                order.getRazorpayOrderId(),
                properties.billing().razorpay().keyId(),
                Map.of("commerce_order_id", order.getId().toString()),
                subscriptionCheckout);
    }

    private CommerceCustomer upsertCustomer(UUID organizationId, CommerceDtos.CheckoutRequest request) {
        return customerRepository.findByOrganizationIdAndEmailIgnoreCase(organizationId, request.email())
                .map(existing -> {
                    if (request.name() != null) {
                        existing.setName(request.name());
                    }
                    if (request.phone() != null) {
                        existing.setPhone(request.phone());
                    }
                    existing.setMarketingConsent(request.marketingConsent());
                    if (request.visitorId() != null) {
                        existing.setVisitorId(request.visitorId());
                    }
                    return customerRepository.save(existing);
                })
                .orElseGet(() -> {
                    CommerceCustomer created = new CommerceCustomer();
                    created.setOrganizationId(organizationId);
                    created.setEmail(request.email());
                    created.setName(request.name());
                    created.setPhone(request.phone());
                    created.setMarketingConsent(request.marketingConsent());
                    created.setVisitorId(request.visitorId());
                    return customerRepository.save(created);
                });
    }

    private List<OrderItem> buildOrderItems(UUID organizationId, UUID orderId, List<CartItem> cartItems) {
        List<OrderItem> items = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            ProductVariant variant = variantRepository.findById(cartItem.getVariantId()).orElseThrow();
            Product product = productRepository.findById(variant.getProductId()).orElseThrow();
            OrderItem item = new OrderItem();
            item.setOrganizationId(organizationId);
            item.setOrderId(orderId);
            item.setProductId(product.getId());
            item.setVariantId(variant.getId());
            item.setProductName(product.getName());
            item.setVariantName(variant.getName());
            item.setSku(variant.getSku());
            item.setProductType(product.getProductType());
            item.setQuantity(cartItem.getQuantity());
            item.setUnitPriceMinor(variant.getPriceMinor());
            item.setLineSubtotalMinor(CommerceAmountCalculator.lineTotal(
                    variant.getPriceMinor(), cartItem.getQuantity()));
            item.setHsnCode(product.getHsnCode());
            items.add(item);
        }
        return items;
    }

    private void saveAddresses(UUID organizationId, UUID orderId, CommerceDtos.CheckoutRequest request) {
        addressRepository.save(toAddress(organizationId, orderId, AddressType.BILLING, request.billingAddress()));
        CommerceDtos.AddressRequest shipping = request.shippingAddress() == null
                ? request.billingAddress() : request.shippingAddress();
        addressRepository.save(toAddress(organizationId, orderId, AddressType.SHIPPING, shipping));
    }

    private OrderAddress toAddress(UUID organizationId, UUID orderId, AddressType type,
                                   CommerceDtos.AddressRequest req) {
        OrderAddress address = new OrderAddress();
        address.setOrganizationId(organizationId);
        address.setOrderId(orderId);
        address.setAddressType(type);
        address.setName(req.name());
        address.setLine1(req.line1());
        address.setLine2(req.line2());
        address.setCity(req.city());
        address.setState(req.state());
        address.setPincode(req.pincode());
        address.setPhone(req.phone());
        return address;
    }

    private void appendEvent(CommerceOrder order, String type, String message) {
        OrderEvent event = new OrderEvent();
        event.setOrganizationId(order.getOrganizationId());
        event.setOrderId(order.getId());
        event.setEventType(type);
        event.setMessage(message);
        eventRepository.save(event);
    }
}
