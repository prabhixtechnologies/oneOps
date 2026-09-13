package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.config.CommerceProperties;
import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.CommerceCustomer;
import com.prabhix.platform.commerce.domain.OrderAddress;
import com.prabhix.platform.commerce.domain.OrderDownload;
import com.prabhix.platform.commerce.domain.OrderEvent;
import com.prabhix.platform.commerce.domain.OrderItem;
import com.prabhix.platform.commerce.domain.CommerceEnums.OrderStatus;
import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.repository.CommerceCustomerRepository;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.commerce.repository.OrderAddressRepository;
import com.prabhix.platform.commerce.repository.OrderDownloadRepository;
import com.prabhix.platform.commerce.repository.OrderEventRepository;
import com.prabhix.platform.commerce.repository.OrderItemRepository;
import com.prabhix.platform.commerce.repository.OrderShipmentRepository;
import com.prabhix.platform.commerce.repository.ProductRepository;
import com.prabhix.platform.commerce.domain.OrderShipment;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.security.PrabhixPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommerceOrderService {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;

    private final CommerceOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderAddressRepository addressRepository;
    private final OrderEventRepository eventRepository;
    private final CommerceCustomerRepository customerRepository;
    private final OrderDownloadRepository downloadRepository;
    private final OrderShipmentRepository shipmentRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public CursorPage<CommerceDtos.OrderSummary> list(
            UUID organizationId, String status, Instant from, Instant to,
            String search, String cursor, Integer limit) {
        int pageSize = clampLimit(limit);
        Cursor decoded = Cursor.decode(cursor);
        Cursor key = decoded == null ? Cursor.beginning() : decoded;
        var rows = orderRepository.listWithCursor(
                organizationId, status, from, to, blankToNull(search),
                key.timestamp(), key.id(), pageSize + 1);
        return CursorPage.of(rows, pageSize, this::toSummary,
                o -> Cursor.of(o.getCreatedAt(), o.getId()).encode());
    }

    @Transactional(readOnly = true)
    public CommerceDtos.OrderDetail get(UUID organizationId, UUID orderId) {
        CommerceOrder order = load(organizationId, orderId);
        return toDetail(order);
    }

    @Transactional(readOnly = true)
    public CommerceDtos.OrderDetail getByAccessToken(String accessToken) {
        CommerceOrder order = orderRepository.findByAccessToken(accessToken)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "That order was not found"));
        return toDetail(order, false);
    }

    @Transactional
    public CommerceDtos.OrderDetail fulfill(PrabhixPrincipal principal, UUID orderId,
                                            CommerceDtos.FulfillOrderRequest request) {
        CommerceOrder order = load(principal.requireOrganizationId(), orderId);
        if (order.getStatus() != OrderStatus.PAID) {
            throw ApiException.invalidState("Only paid orders can be fulfilled");
        }
        Instant shippedAt = Instant.now();
        List<OrderShipment> shipments = shipmentRepository.findByOrderIdAndOrganizationId(
                order.getId(), order.getOrganizationId());
        if (!shipments.isEmpty()) {
            for (OrderShipment shipment : shipments) {
                if (request.carrier() != null && !request.carrier().isBlank()) {
                    shipment.setCarrier(request.carrier());
                }
                if (request.trackingNumber() != null && !request.trackingNumber().isBlank()) {
                    shipment.setTrackingNumber(request.trackingNumber());
                }
                shipment.setStatus(com.prabhix.platform.commerce.domain.CommerceEnums.ShipmentStatus.SHIPPED);
                shipment.setShippedAt(shippedAt);
                shipmentRepository.save(shipment);
            }
        }
        order.setStatus(OrderStatus.FULFILLED);
        order.setFulfilledAt(shippedAt);
        orderRepository.save(order);
        appendEvent(order, "FULFILLED", buildFulfillmentMessage(request));
        events.publishEvent(AuditRequested.of(
                order.getOrganizationId(), principal.userId(),
                "commerce.order.fulfilled", "commerce_order", order.getId()));
        return toDetail(order);
    }

    @Transactional
    public CommerceDtos.OrderDetail cancel(PrabhixPrincipal principal, UUID orderId) {
        UUID orgId = principal.requireOrganizationId();
        CommerceOrder order = load(orgId, orderId);
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REFUNDED) {
            return toDetail(order);
        }
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            stockService.releaseForOrder(order);
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        orderRepository.save(order);
        appendEvent(order, "CANCELLED", "Order cancelled");
        events.publishEvent(AuditRequested.of(orgId, principal.userId(),
                "commerce.order.cancelled", "commerce_order", order.getId()));
        return toDetail(order);
    }

    @Transactional
    public CommerceDtos.OrderDetail annotate(PrabhixPrincipal principal, UUID orderId,
                                               CommerceDtos.UpdateOrderRequest request) {
        CommerceOrder order = load(principal.requireOrganizationId(), orderId);
        order.setInternalNote(request.internalNote());
        orderRepository.save(order);
        return toDetail(order);
    }

    CommerceOrder load(UUID organizationId, UUID orderId) {
        return orderRepository.findByIdAndOrganizationId(orderId, organizationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "That order was not found"));
    }

    private CommerceDtos.OrderSummary toSummary(CommerceOrder order) {
        String email = customerRepository.findById(order.getCustomerId())
                .map(CommerceCustomer::getEmail).orElse(null);
        return new CommerceDtos.OrderSummary(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalMinor(),
                order.getCurrency(),
                email,
                order.getCreatedAt(),
                order.getPaidAt());
    }

    private CommerceDtos.OrderDetail toDetail(CommerceOrder order) {
        return toDetail(order, true);
    }

    private CommerceDtos.OrderDetail toDetail(CommerceOrder order, boolean includeInternalNote) {
        CommerceCustomer customer = order.getCustomerId() == null ? null
                : customerRepository.findById(order.getCustomerId()).orElse(null);
        List<OrderItem> items = orderItemRepository.findByOrderIdAndOrganizationId(
                order.getId(), order.getOrganizationId());
        List<OrderAddress> addresses = addressRepository.findByOrderIdAndOrganizationId(
                order.getId(), order.getOrganizationId());
        List<OrderEvent> eventsList = eventRepository.findByOrderIdAndOrganizationIdOrderByCreatedAtAsc(
                order.getId(), order.getOrganizationId());
        List<OrderShipment> shipments = shipmentRepository.findByOrderIdAndOrganizationId(
                order.getId(), order.getOrganizationId());
        return new CommerceDtos.OrderDetail(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getAccessToken(),
                order.getSubtotalMinor(),
                order.getDiscountMinor(),
                order.getCgstMinor(),
                order.getSgstMinor(),
                order.getIgstMinor(),
                order.getShippingMinor(),
                order.getTotalMinor(),
                order.getCurrency(),
                customer == null ? null : customer.getEmail(),
                customer == null ? null : customer.getName(),
                items.stream().map(i -> new CommerceDtos.OrderItemView(
                        i.getId(), i.getProductName(), i.getVariantName(), i.getSku(),
                        i.getProductType(), i.getQuantity(), i.getUnitPriceMinor(), i.getLineSubtotalMinor()))
                        .toList(),
                addresses.stream().map(a -> new CommerceDtos.OrderAddressView(
                        a.getAddressType(), a.getName(), a.getLine1(), a.getLine2(),
                        a.getCity(), a.getState(), a.getPincode(), a.getPhone())).toList(),
                eventsList.stream().map(e -> new CommerceDtos.OrderEventView(
                        e.getEventType(), e.getMessage(), e.getCreatedAt())).toList(),
                order.getInvoiceId(),
                order.getPaidAt(),
                order.getFulfilledAt(),
                includeInternalNote ? order.getInternalNote() : null,
                shipments.stream().map(s -> new CommerceDtos.ShipmentView(
                        s.getOrderItemId(),
                        s.getStatus(),
                        s.getCarrier(),
                        s.getTrackingNumber(),
                        s.getShippedAt())).toList());
    }

    private String buildFulfillmentMessage(CommerceDtos.FulfillOrderRequest request) {
        if (request.trackingNumber() != null && !request.trackingNumber().isBlank()) {
            String carrier = request.carrier() == null || request.carrier().isBlank()
                    ? "Carrier" : request.carrier();
            return "Shipped via " + carrier + ", tracking " + request.trackingNumber();
        }
        return "Order fulfilled";
    }

    private void appendEvent(CommerceOrder order, String type, String message) {
        OrderEvent event = new OrderEvent();
        event.setOrganizationId(order.getOrganizationId());
        event.setOrderId(order.getId());
        event.setEventType(type);
        event.setMessage(message);
        eventRepository.save(event);
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
