package com.prabhix.platform.ops.service;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.repository.BillingPaymentRepository;
import com.prabhix.platform.ops.dto.OpsDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;

@Service
@RequiredArgsConstructor
public class PlatformBillingRevenueService {

    private final BillingPaymentRepository paymentRepository;

    /**
     * Cross-tenant SaaS payment rollup. Amounts returned in major units (rupees).
     */
    public OpsDtos.PlatformBillingRevenue revenue() {
        EnumMap<BillingEnums.PaymentStatus, Long> paise = new EnumMap<>(BillingEnums.PaymentStatus.class);
        EnumMap<BillingEnums.PaymentStatus, Long> counts = new EnumMap<>(BillingEnums.PaymentStatus.class);
        for (BillingEnums.PaymentStatus status : BillingEnums.PaymentStatus.values()) {
            paise.put(status, 0L);
            counts.put(status, 0L);
        }
        for (Object[] row : paymentRepository.aggregateByStatus()) {
            BillingEnums.PaymentStatus status = (BillingEnums.PaymentStatus) row[0];
            long sum = ((Number) row[1]).longValue();
            long count = ((Number) row[2]).longValue();
            paise.put(status, sum);
            counts.put(status, count);
        }
        long capturedPaise = paise.getOrDefault(BillingEnums.PaymentStatus.CAPTURED, 0L);
        long pendingPaise = paise.getOrDefault(BillingEnums.PaymentStatus.CREATED, 0L)
                + paise.getOrDefault(BillingEnums.PaymentStatus.AUTHORIZED, 0L);
        long failedCount = counts.getOrDefault(BillingEnums.PaymentStatus.FAILED, 0L)
                + counts.getOrDefault(BillingEnums.PaymentStatus.REFUNDED, 0L);
        return new OpsDtos.PlatformBillingRevenue(
                toRupees(capturedPaise),
                toRupees(pendingPaise),
                counts.getOrDefault(BillingEnums.PaymentStatus.CAPTURED, 0L),
                counts.getOrDefault(BillingEnums.PaymentStatus.CREATED, 0L)
                        + counts.getOrDefault(BillingEnums.PaymentStatus.AUTHORIZED, 0L),
                failedCount,
                "INR",
                Instant.now());
    }

    private static BigDecimal toRupees(long amountPaise) {
        return BigDecimal.valueOf(amountPaise, 2).setScale(2, RoundingMode.HALF_UP);
    }
}
