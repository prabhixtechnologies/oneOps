package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    Optional<ProductCategory> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    Optional<ProductCategory> findByOrganizationIdAndSlugAndDeletedAtIsNull(UUID organizationId, String slug);

    List<ProductCategory> findByOrganizationIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID organizationId);

    @Query(value = """
            SELECT category_id FROM commerce_product_category_links WHERE product_id = :productId
            """, nativeQuery = true)
    List<UUID> findCategoryIdsForProduct(UUID productId);
}
