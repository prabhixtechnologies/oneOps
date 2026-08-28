package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.DiscountRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface DiscountRedemptionRepository extends JpaRepository<DiscountRedemption, UUID> {

    @Query(value = """
            SELECT count(*) FROM commerce_discount_redemptions
            WHERE discount_code_id = :codeId AND customer_id = :customerId
            """, nativeQuery = true)
    long countByCodeAndCustomer(UUID codeId, UUID customerId);
}
