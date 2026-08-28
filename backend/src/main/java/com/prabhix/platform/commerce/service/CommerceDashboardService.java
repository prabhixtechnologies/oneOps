package com.prabhix.platform.commerce.service;

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
import java.util.List;
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
        List<CommerceDtos.TopProductView> topProducts = new ArrayList<>();
        for (Object[] row : orderItemRepository.topProductsSince(organizationId, since30d, 5)) {
            UUID productId = (UUID) row[0];
            long qty = ((Number) row[1]).longValue();
            String name = productRepository.findById(productId).map(p -> p.getName()).orElse("Product");
            topProducts.add(new CommerceDtos.TopProductView(productId, name, qty));
        }
        long carts = cartRepository.countByOrganizationId(organizationId);
        double conversion = carts == 0 ? 0.0 : (double) orderCount / carts;
        return new CommerceDtos.DashboardView(revenue, orderCount, topProducts, conversion);
    }
}
