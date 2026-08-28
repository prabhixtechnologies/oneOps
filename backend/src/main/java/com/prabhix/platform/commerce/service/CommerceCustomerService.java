package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.CommerceCustomer;
import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.repository.CommerceCustomerRepository;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommerceCustomerService {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;
    private static final int ORDER_HISTORY_LIMIT = 50;

    private final CommerceCustomerRepository customerRepository;
    private final CommerceOrderRepository orderRepository;

    @Transactional(readOnly = true)
    public CursorPage<CommerceDtos.CustomerSummary> list(UUID organizationId, String cursor, Integer limit) {
        int pageSize = clampLimit(limit);
        Cursor key = Cursor.decode(cursor);
        Cursor c = key == null ? Cursor.beginning() : key;
        var rows = customerRepository.listWithCursor(
                organizationId, c.timestamp(), c.id(), pageSize + 1);
        return CursorPage.of(rows, pageSize, this::toSummary,
                cust -> Cursor.of(cust.getCreatedAt(), cust.getId()).encode());
    }

    @Transactional(readOnly = true)
    public CommerceDtos.CustomerDetail get(UUID organizationId, UUID customerId) {
        CommerceCustomer customer = customerRepository.findByIdAndOrganizationId(customerId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Customer"));
        return toDetail(customer);
    }

    private CommerceDtos.CustomerSummary toSummary(CommerceCustomer customer) {
        return new CommerceDtos.CustomerSummary(
                customer.getId(),
                customer.getEmail(),
                customer.getName(),
                customer.getPhone(),
                customer.isMarketingConsent(),
                customer.getCreatedAt());
    }

    private CommerceDtos.CustomerDetail toDetail(CommerceCustomer customer) {
        List<CommerceOrder> orders = orderRepository.listByCustomer(
                customer.getOrganizationId(), customer.getId(), ORDER_HISTORY_LIMIT);
        return new CommerceDtos.CustomerDetail(
                customer.getId(),
                customer.getEmail(),
                customer.getName(),
                customer.getPhone(),
                customer.isMarketingConsent(),
                customer.getVisitorId(),
                customer.getCreatedAt(),
                orders.stream().map(this::toOrderSummary).toList());
    }

    private CommerceDtos.OrderSummary toOrderSummary(CommerceOrder order) {
        return new CommerceDtos.OrderSummary(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalMinor(),
                order.getCurrency(),
                customerRepository.findById(order.getCustomerId()).map(CommerceCustomer::getEmail).orElse(null),
                order.getCreatedAt(),
                order.getPaidAt());
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
