package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.OrderItem;
import com.prabhix.platform.commerce.domain.CommerceEnums.OrderStatus;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.commerce.repository.OrderItemRepository;
import com.prabhix.platform.commerce.repository.ProductVariantRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductVariantRepository variantRepository;
    private final CommerceOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public void reserveForOrder(CommerceOrder order, List<OrderItem> items) {
        for (OrderItem item : items) {
            int updated = variantRepository.reserveStock(
                    order.getOrganizationId(), item.getVariantId(), item.getQuantity());
            if (updated == 0) {
                throw ApiException.of(ErrorCode.OUT_OF_STOCK,
                        "Insufficient stock for " + item.getVariantName());
            }
        }
    }

    @Transactional
    public void releaseForOrder(CommerceOrder order) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }
        List<OrderItem> items = orderItemRepository.findByOrderIdAndOrganizationId(
                order.getId(), order.getOrganizationId());
        for (OrderItem item : items) {
            variantRepository.releaseStock(order.getOrganizationId(), item.getVariantId(), item.getQuantity());
        }
    }

    @Transactional
    public void commitForOrder(CommerceOrder order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdAndOrganizationId(
                order.getId(), order.getOrganizationId());
        for (OrderItem item : items) {
            variantRepository.commitStock(order.getOrganizationId(), item.getVariantId(), item.getQuantity());
        }
    }

    @Scheduled(fixedDelayString = "${prabhix.commerce.stock-hold-sweep-interval:PT2M}")
    @Transactional
    public void releaseExpiredHolds() {
        Instant now = Instant.now();
        List<CommerceOrder> expired = orderRepository.findExpiredStockHolds(now);
        for (CommerceOrder order : expired) {
            try {
                releaseForOrder(order);
                order.setStatus(OrderStatus.CANCELLED);
                order.setCancelledAt(now);
                orderRepository.save(order);
                log.info("Released expired stock hold for order {}", order.getOrderNumber());
            } catch (Exception ex) {
                log.warn("Failed to release stock hold for order {}: {}", order.getId(), ex.getMessage());
            }
        }
    }
}
