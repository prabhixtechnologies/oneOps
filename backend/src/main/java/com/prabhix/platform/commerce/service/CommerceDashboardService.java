package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.Product;
import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.repository.CartRepository;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.commerce.repository.OrderItemRepository;
import com.prabhix.platform.commerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommerceDashboardService {

    private final CommerceOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;

    @Transactional(readOnly = true)
    public CommerceDtos.DashboardView getDashboard(UUID organizationId) {
        Instant since30d = Instant.now().minus(30, ChronoUnit.DAYS);
        long revenue = orderRepository.sumRevenueSince(organizationId, since30d);
        long orderCount = orderRepository.countOrdersSince(organizationId, since30d);
        List<Object[]> rows = orderItemRepository.topProductsSince(organizationId, since30d, 5);
        List<UUID> productIds = new ArrayList<>();
        for (Object[] row : rows) {
            productIds.add((UUID) row[0]);
        }
        Map<UUID, String> names = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Product product : productRepository.findAllById(productIds)) {
                names.put(product.getId(), product.getName());
            }
        }
        List<CommerceDtos.TopProductView> topProducts = new ArrayList<>();
        for (Object[] row : rows) {
            UUID productId = (UUID) row[0];
            long qty = ((Number) row[1]).longValue();
            topProducts.add(new CommerceDtos.TopProductView(
                    productId, names.getOrDefault(productId, "Product"), qty));
        }
        long carts = cartRepository.countByOrganizationId(organizationId);
        double conversion = carts == 0 ? 0.0 : (double) orderCount / carts;
        return new CommerceDtos.DashboardView(revenue, orderCount, topProducts, conversion);
    }
}
