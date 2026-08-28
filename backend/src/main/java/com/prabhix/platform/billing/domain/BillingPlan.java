package com.prabhix.platform.billing.domain;

import com.prabhix.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "billing_plans")
public class BillingPlan extends AuditableEntity {

    @Column(name = "plan_key", nullable = false, length = 60, unique = true)
    private String planKey;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "interval_type", nullable = false, length = 16)
    private BillingEnums.PlanInterval intervalType = BillingEnums.PlanInterval.MONTHLY;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "per_seat_paise", nullable = false)
    private long perSeatPaise;

    @Column(name = "included_seats", nullable = false)
    private int includedSeats = 5;

    @Column(name = "max_seats")
    private Integer maxSeats;

    @Column(name = "trial_days", nullable = false)
    private int trialDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entitlements", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> entitlements = Map.of();

    @Column(name = "razorpay_plan_id", length = 80)
    private String razorpayPlanId;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "rank", nullable = false)
    private int rank = 100;
}
