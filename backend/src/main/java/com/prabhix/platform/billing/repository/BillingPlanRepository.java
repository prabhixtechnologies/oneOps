package com.prabhix.platform.billing.repository;

import com.prabhix.platform.billing.domain.BillingPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingPlanRepository extends JpaRepository<BillingPlan, UUID> {

    Optional<BillingPlan> findByPlanKey(String planKey);

    List<BillingPlan> findByIsPublicTrueAndIsActiveTrueOrderByRankAsc();

    Page<BillingPlan> findByIsPublicTrueAndIsActiveTrueOrderByRankAsc(Pageable pageable);
}
