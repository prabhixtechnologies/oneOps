package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.CommerceCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommerceCustomerRepository extends JpaRepository<CommerceCustomer, UUID> {

    Optional<CommerceCustomer> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<CommerceCustomer> findByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    @Query(value = """
            SELECT c.* FROM commerce_customers c
            WHERE c.organization_id = :orgId
              AND (c.created_at < :cursorAt OR (c.created_at = :cursorAt AND c.id < :cursorId))
            ORDER BY c.created_at DESC, c.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<CommerceCustomer> listWithCursor(UUID orgId, Instant cursorAt, UUID cursorId, int limit);
}
