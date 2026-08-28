package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.ProductMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, UUID> {

    List<ProductMedia> findByProductIdAndOrganizationIdOrderBySortOrderAsc(UUID productId, UUID organizationId);

    void deleteByProductIdAndOrganizationId(UUID productId, UUID organizationId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM commerce_product_media m
                JOIN commerce_products p ON p.id = m.product_id
                WHERE m.organization_id = :orgId AND m.file_id = :fileId
                  AND p.deleted_at IS NULL AND p.status = 'ACTIVE' AND p.published_at IS NOT NULL
            )
            """, nativeQuery = true)
    boolean existsForActiveProduct(UUID orgId, UUID fileId);
}
