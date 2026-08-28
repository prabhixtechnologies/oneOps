package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.CommercePayment;
import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.CommerceEnums.OrderStatus;
import com.prabhix.platform.commerce.domain.CommerceEnums.PaymentStatus;
import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.commerce.repository.CommercePaymentRepository;
import com.prabhix.platform.billing.razorpay.RazorpayClient;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.security.PrabhixPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommerceRefundService {

    private final CommercePaymentRepository paymentRepository;
    private final CommerceOrderRepository orderRepository;
    private final RazorpayClient razorpayClient;
    private final ApplicationEventPublisher events;

    @Transactional
    public CommerceDtos.RefundView refund(PrabhixPrincipal principal, CommerceDtos.RefundRequest request) {
        UUID orgId = principal.requireOrganizationId();
        CommerceOrder order = orderRepository.findByIdAndOrganizationId(request.orderId(), orgId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "That order was not found"));
        CommercePayment payment = paymentRepository.findByOrderIdAndOrganizationId(order.getId(), orgId)
                .orElseThrow(() -> ApiException.notFound("Payment"));
        if (payment.getStatus() != PaymentStatus.CAPTURED && payment.getStatus() != PaymentStatus.REFUNDED) {
            throw ApiException.invalidState("Only captured payments can be refunded");
        }
        long refundMinor = request.amountMinor() == null || request.amountMinor() <= 0
                ? payment.getAmountMinor() - payment.getRefundedMinor()
                : request.amountMinor();
        if (refundMinor <= 0 || payment.getRefundedMinor() + refundMinor > payment.getAmountMinor()) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Refund amount is invalid");
        }
        if (payment.getRazorpayPaymentId() != null) {
            razorpayClient.refundPayment(payment.getRazorpayPaymentId(), refundMinor);
        }
        payment.setRefundedMinor(payment.getRefundedMinor() + refundMinor);
        if (payment.getRefundedMinor() >= payment.getAmountMinor()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            order.setStatus(OrderStatus.REFUNDED);
            orderRepository.save(order);
        }
        paymentRepository.save(payment);
        events.publishEvent(AuditRequested.of(orgId, principal.userId(),
                "commerce.payment.refunded", "commerce_payment", payment.getId()));
        return new CommerceDtos.RefundView(
                payment.getId(), refundMinor, payment.getRefundedMinor(), payment.getStatus().name());
    }
}
