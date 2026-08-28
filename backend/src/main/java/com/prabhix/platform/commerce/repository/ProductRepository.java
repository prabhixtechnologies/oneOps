package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    Optional<Product> findByOrganizationIdAndSlugAndDeletedAtIsNull(UUID organizationId, String slug);

    @Query(value = """
            SELECT p.* FROM commerce_products p
            WHERE p.organization_id = :orgId AND p.deleted_at IS NULL
              AND (:status IS NULL OR p.status = :status)
              AND (:featured IS NULL OR p.featured = :featured)
              AND (:type IS NULL OR p.product_type = :type)
              AND (p.created_at < :cursorAt OR (p.created_at = :cursorAt AND p.id < :cursorId))
            ORDER BY p.created_at DESC, p.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Product> listWithCursor(UUID orgId, String status, Boolean featured, String type,
                                 Instant cursorAt, UUID cursorId, int limit);

    @Query(value = """
            SELECT p.* FROM commerce_products p
            WHERE p.organization_id = :orgId AND p.deleted_at IS NULL AND p.status = 'ACTIVE'
              AND (p.created_at < :cursorAt OR (p.created_at = :cursorAt AND p.id < :cursorId))
            ORDER BY p.featured DESC, p.sort_order ASC, p.created_at DESC, p.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Product> listActivePublic(UUID orgId, Instant cursorAt, UUID cursorId, int limit);

    boolean existsByOrganizationIdAndSlugAndDeletedAtIsNull(UUID organizationId, String slug);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM commerce_products p
                WHERE p.organization_id = :orgId AND p.deleted_at IS NULL AND p.status = 'ACTIVE'
                  AND p.published_at IS NOT NULL AND p.hero_image_file_id = :fileId
            )
            """, nativeQuery = true)
    boolean existsActiveProductHeroImage(UUID orgId, UUID fileId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM commerce_products p
                WHERE p.organization_id = :orgId AND p.deleted_at IS NULL AND p.status = 'ACTIVE'
                  AND p.published_at IS NOT NULL
                  AND EXISTS (
                        SELECT 1 FROM jsonb_array_elements_text(p.gallery_file_ids) elem
                        WHERE elem = CAST(:fileId AS text))
            )
            """, nativeQuery = true)
    boolean existsActiveProductGalleryImage(UUID orgId, UUID fileId);

    @Query(value = """
            SELECT p.* FROM commerce_products p
            WHERE p.organization_id = :orgId AND p.deleted_at IS NULL AND p.status = 'ACTIVE'
              AND p.published_at IS NOT NULL
              AND (:search IS NULL OR p.name ILIKE ('%' || :search || '%')
                   OR p.tagline ILIKE ('%' || :search || '%'))
              AND (:type IS NULL OR p.product_type = :type)
              AND (:categoryId IS NULL OR EXISTS (
                    SELECT 1 FROM commerce_product_category_links l
                    WHERE l.product_id = p.id AND l.category_id = :categoryId))
              AND (p.created_at < :cursorAt OR (p.created_at = :cursorAt AND p.id < :cursorId))
            ORDER BY p.featured DESC, p.sort_order ASC, p.created_at DESC, p.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Product> listActivePublicFiltered(UUID orgId, String search, String type, UUID categoryId,
                                           Instant cursorAt, UUID cursorId, int limit);

    @Query(value = """
            SELECT p.* FROM commerce_products p
            WHERE p.organization_id = :orgId AND p.deleted_at IS NULL AND p.status = 'ACTIVE'
              AND p.published_at IS NOT NULL
              AND (:search IS NULL OR p.name ILIKE ('%' || :search || '%')
                   OR p.tagline ILIKE ('%' || :search || '%'))
              AND (:type IS NULL OR p.product_type = :type)
              AND (:categoryId IS NULL OR EXISTS (
                    SELECT 1 FROM commerce_product_category_links l
                    WHERE l.product_id = p.id AND l.category_id = :categoryId))
              AND (p.name > :cursorName OR (p.name = :cursorName AND p.id < :cursorId))
            ORDER BY p.name ASC, p.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Product> listActivePublicByName(UUID orgId, String search, String type, UUID categoryId,
                                         String cursorName, UUID cursorId, int limit);

    @Query(value = """
            SELECT p.* FROM commerce_products p
            LEFT JOIN LATERAL (
                SELECT MIN(v.price_minor) AS min_price
                FROM commerce_product_variants v
                WHERE v.product_id = p.id AND v.deleted_at IS NULL AND v.active = true
            ) prices ON true
            WHERE p.organization_id = :orgId AND p.deleted_at IS NULL AND p.status = 'ACTIVE'
              AND p.published_at IS NOT NULL
              AND (:search IS NULL OR p.name ILIKE ('%' || :search || '%')
                   OR p.tagline ILIKE ('%' || :search || '%'))
              AND (:type IS NULL OR p.product_type = :type)
              AND (:categoryId IS NULL OR EXISTS (
                    SELECT 1 FROM commerce_product_category_links l
                    WHERE l.product_id = p.id AND l.category_id = :categoryId))
              AND (COALESCE(prices.min_price, 0) > :cursorPrice
                   OR (COALESCE(prices.min_price, 0) = :cursorPrice AND p.id < :cursorId))
            ORDER BY COALESCE(prices.min_price, 0) ASC, p.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Product> listActivePublicByPriceAsc(UUID orgId, String search, String type, UUID categoryId,
                                              long cursorPrice, UUID cursorId, int limit);

    @Query(value = """
            SELECT p.* FROM commerce_products p
            LEFT JOIN LATERAL (
                SELECT MIN(v.price_minor) AS min_price
                FROM commerce_product_variants v
                WHERE v.product_id = p.id AND v.deleted_at IS NULL AND v.active = true
            ) prices ON true
            WHERE p.organization_id = :orgId AND p.deleted_at IS NULL AND p.status = 'ACTIVE'
              AND p.published_at IS NOT NULL
              AND (:search IS NULL OR p.name ILIKE ('%' || :search || '%')
                   OR p.tagline ILIKE ('%' || :search || '%'))
              AND (:type IS NULL OR p.product_type = :type)
              AND (:categoryId IS NULL OR EXISTS (
                    SELECT 1 FROM commerce_product_category_links l
                    WHERE l.product_id = p.id AND l.category_id = :categoryId))
              AND (COALESCE(prices.min_price, 0) < :cursorPrice
                   OR (COALESCE(prices.min_price, 0) = :cursorPrice AND p.id < :cursorId))
            ORDER BY COALESCE(prices.min_price, 0) DESC, p.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Product> listActivePublicByPriceDesc(UUID orgId, String search, String type, UUID categoryId,
                                               long cursorPrice, UUID cursorId, int limit);
}
