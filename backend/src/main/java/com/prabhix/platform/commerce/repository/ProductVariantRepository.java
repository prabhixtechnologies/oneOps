package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    List<ProductVariant> findByProductIdAndOrganizationIdAndDeletedAtIsNullOrderBySortOrderAsc(
            UUID productId, UUID organizationId);

    Optional<ProductVariant> findByOrganizationIdAndSkuAndDeletedAtIsNull(UUID organizationId, String sku);

    @Modifying
    @Query(value = """
            UPDATE commerce_product_variants
            SET stock_reserved = stock_reserved + :qty, updated_at = now()
            WHERE id = :variantId AND organization_id = :orgId AND deleted_at IS NULL
              AND (NOT track_inventory OR stock_on_hand - stock_reserved >= :qty)
            """, nativeQuery = true)
    int reserveStock(UUID orgId, UUID variantId, int qty);

    @Modifying
    @Query(value = """
            UPDATE commerce_product_variants
            SET stock_reserved = stock_reserved - :qty, updated_at = now()
            WHERE id = :variantId AND organization_id = :orgId AND deleted_at IS NULL
              AND stock_reserved >= :qty
            """, nativeQuery = true)
    int releaseStock(UUID orgId, UUID variantId, int qty);

    @Modifying
    @Query(value = """
            UPDATE commerce_product_variants
            SET stock_on_hand = stock_on_hand - :qty,
                stock_reserved = stock_reserved - :qty,
                updated_at = now()
            WHERE id = :variantId AND organization_id = :orgId AND deleted_at IS NULL
              AND stock_reserved >= :qty
              AND (NOT track_inventory OR stock_on_hand >= :qty)
            """, nativeQuery = true)
    int commitStock(UUID orgId, UUID variantId, int qty);
}
