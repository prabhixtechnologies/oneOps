package com.prabhix.platform.billing.service;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingPayment;
import com.prabhix.platform.billing.dto.BillingDtos.RefundRequest;
import com.prabhix.platform.billing.dto.BillingDtos.RefundView;
import com.prabhix.platform.billing.razorpay.RazorpayClient;
import com.prabhix.platform.billing.repository.BillingInvoiceRepository;
import com.prabhix.platform.billing.repository.BillingPaymentRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final BillingPaymentRepository paymentRepository;
    private final BillingInvoiceRepository invoiceRepository;
    private final RazorpayClient razorpayClient;
    private final PrabhixProperties properties;
    private final ApplicationEventPublisher events;

    @Transactional
    public RefundView refund(UUID organizationId, UUID userId, RefundRequest request) {
        BillingPayment payment = paymentRepository.findById(request.paymentId())
                .filter(p -> organizationId.equals(p.getOrganizationId()))
                .orElseThrow(() -> ApiException.notFound("Payment"));

        if (payment.getStatus() != BillingEnums.PaymentStatus.CAPTURED) {
            throw ApiException.invalidState("Only captured payments can be refunded");
        }

        long refundPaise = request.amountPaise() == null || request.amountPaise() <= 0
                ? payment.getAmountPaise() - payment.getRefundedPaise()
                : request.amountPaise();
        if (refundPaise <= 0 || payment.getRefundedPaise() + refundPaise > payment.getAmountPaise()) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Refund amount is invalid");
        }

        if (properties.billing().razorpay().configured()) {
            razorpayClient.refundPayment(payment.getRazorpayPaymentId(), refundPaise);
        }

        payment.setRefundedPaise(payment.getRefundedPaise() + refundPaise);
        if (payment.getRefundedPaise() >= payment.getAmountPaise()) {
            payment.setStatus(BillingEnums.PaymentStatus.REFUNDED);
        }
        paymentRepository.save(payment);

        invoiceRepository.findByOrderId(payment.getOrderId()).ifPresent(invoice -> {
            if (payment.getStatus() == BillingEnums.PaymentStatus.REFUNDED) {
                invoice.setStatus(BillingEnums.InvoiceStatus.REFUNDED);
                invoiceRepository.save(invoice);
            }
        });

        events.publishEvent(AuditRequested.changed(
                organizationId, userId, "billing.payment.refunded", "billing_payment",
                payment.getId(), Map.of("refundPaise", refundPaise)));

        return new RefundView(payment.getId(), refundPaise, payment.getRefundedPaise(), payment.getStatus());
    }
}
