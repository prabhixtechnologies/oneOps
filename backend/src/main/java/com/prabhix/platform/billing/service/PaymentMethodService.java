package com.prabhix.platform.billing.service;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingPayment;
import com.prabhix.platform.billing.dto.BillingDtos.PaymentMethodView;
import com.prabhix.platform.billing.repository.BillingPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final BillingPaymentRepository paymentRepository;

    /** Derived from captured payment metadata; Razorpay does not vault instruments for us. */
    @Transactional(readOnly = true)
    public List<PaymentMethodView> listMethods(UUID organizationId) {
        List<BillingPayment> payments = paymentRepository.findByOrganizationIdAndStatusOrderByCapturedAtDesc(
                organizationId, BillingEnums.PaymentStatus.CAPTURED);

        Map<String, PaymentMethodView> deduped = new LinkedHashMap<>();
        for (BillingPayment payment : payments) {
            String key = methodKey(payment);
            if (key == null || deduped.containsKey(key)) {
                continue;
            }
            deduped.put(key, new PaymentMethodView(
                    payment.getMethod(),
                    displayLabel(payment),
                    payment.getCapturedAt(),
                    false));
        }
        return new ArrayList<>(deduped.values());
    }

    private String methodKey(BillingPayment payment) {
        if (payment.getMethod() == null) {
            return null;
        }
        return payment.getMethod() + ":"
                + (payment.getVpa() != null ? payment.getVpa()
                : payment.getMethodDetail() != null ? payment.getMethodDetail()
                : payment.getBank() != null ? payment.getBank() : payment.getId().toString());
    }

    private String displayLabel(BillingPayment payment) {
        return switch (payment.getMethod()) {
            case "card" -> "Card ending " + nullToDash(payment.getMethodDetail());
            case "upi" -> "UPI " + nullToDash(payment.getVpa());
            case "netbanking" -> "Netbanking (" + nullToDash(payment.getBank()) + ")";
            case "wallet" -> "Wallet (" + nullToDash(payment.getWallet()) + ")";
            default -> payment.getMethod() == null ? "Unknown" : payment.getMethod();
        };
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
