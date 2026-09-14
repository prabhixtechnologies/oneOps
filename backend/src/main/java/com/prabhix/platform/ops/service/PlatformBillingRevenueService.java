package com.prabhix.platform.ops.service;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.repository.BillingPaymentRepository;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.ops.client.MobiStackAdminClient;
import com.prabhix.platform.ops.dto.OpsDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformBillingRevenueService {

    private static final List<BillingEnums.SubscriptionStatus> LIVE = List.of(
            BillingEnums.SubscriptionStatus.TRIALING,
            BillingEnums.SubscriptionStatus.ACTIVE,
            BillingEnums.SubscriptionStatus.PAST_DUE);

    private final BillingPaymentRepository paymentRepository;
    private final BillingSubscriptionRepository subscriptionRepository;
    private final MobiStackAdminClient mobistack;

    /**
     * Cross-tenant SaaS payment rollup plus subscription health. Amounts in major units (rupees).
     */
    public OpsDtos.PlatformBillingRevenue revenue(UUID actorId) {
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

        long active = subscriptionRepository.countByStatusIn(LIVE);
        long cancelledLast30 = subscriptionRepository.countByCancelledAtGreaterThanEqual(
                Instant.now().minus(30, ChronoUnit.DAYS));
        Number mrrRaw = subscriptionRepository.sumLockedPaiseByStatusIn(LIVE);
        long mrrPaise = mrrRaw == null ? 0L : mrrRaw.longValue();
        BigDecimal mrr = toRupees(mrrPaise);
        BigDecimal churn = churnRate(cancelledLast30, active);

        OpsDtos.ProductRevenue oneops = new OpsDtos.ProductRevenue(
                "oneops",
                toRupees(capturedPaise),
                toRupees(pendingPaise),
                counts.getOrDefault(BillingEnums.PaymentStatus.CAPTURED, 0L),
                mrr,
                active,
                churn,
                cancelledLast30,
                null);

        List<OpsDtos.ProductRevenue> products = new ArrayList<>();
        products.add(oneops);
        products.add(mobiStackProduct(actorId));

        return new OpsDtos.PlatformBillingRevenue(
                toRupees(capturedPaise),
                toRupees(pendingPaise),
                counts.getOrDefault(BillingEnums.PaymentStatus.CAPTURED, 0L),
                counts.getOrDefault(BillingEnums.PaymentStatus.CREATED, 0L)
                        + counts.getOrDefault(BillingEnums.PaymentStatus.AUTHORIZED, 0L),
                failedCount,
                "INR",
                Instant.now(),
                mrr,
                churn,
                active,
                cancelledLast30,
                products);
    }

    private OpsDtos.ProductRevenue mobiStackProduct(UUID actorId) {
        if (!mobistack.enabled() || actorId == null) {
            return new OpsDtos.ProductRevenue("mobistack", BigDecimal.ZERO, BigDecimal.ZERO, 0,
                    BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, "MobiStack admin is not configured");
        }
        try {
            Object raw = mobistack.get(actorId, "/billing/revenue");
            if (raw instanceof Map<?, ?> map) {
                return new OpsDtos.ProductRevenue(
                        "mobistack",
                        decimal(map.get("capturedTotal")),
                        decimal(map.get("pendingTotal")),
                        longVal(map.get("capturedCount")),
                        BigDecimal.ZERO,
                        0,
                        BigDecimal.ZERO,
                        0,
                        null);
            }
            return new OpsDtos.ProductRevenue("mobistack", BigDecimal.ZERO, BigDecimal.ZERO, 0,
                    BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, "Unexpected MobiStack revenue payload");
        } catch (ApiException ex) {
            log.warn("MobiStack revenue unavailable: {}", ex.getMessage());
            return new OpsDtos.ProductRevenue("mobistack", BigDecimal.ZERO, BigDecimal.ZERO, 0,
                    BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, ex.getMessage());
        }
    }

    private static BigDecimal churnRate(long cancelled, long active) {
        long denom = cancelled + active;
        if (denom <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(cancelled).divide(BigDecimal.valueOf(denom), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal toRupees(long amountPaise) {
        return BigDecimal.valueOf(amountPaise, 2).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static long longVal(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
