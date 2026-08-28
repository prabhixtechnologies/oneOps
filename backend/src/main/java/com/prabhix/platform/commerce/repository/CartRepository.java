package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByCartToken(String cartToken);

    Optional<Cart> findByCartTokenAndOrganizationId(String cartToken, UUID organizationId);

    Optional<Cart> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query(value = """
            SELECT count(*) FROM commerce_carts WHERE organization_id = :orgId
            """, nativeQuery = true)
    long countByOrganizationId(UUID orgId);
}
