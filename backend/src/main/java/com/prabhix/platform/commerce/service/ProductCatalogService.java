package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.Product;
import com.prabhix.platform.commerce.domain.ProductCategory;
import com.prabhix.platform.commerce.domain.ProductMedia;
import com.prabhix.platform.commerce.domain.ProductVariant;
import com.prabhix.platform.commerce.domain.CommerceEnums.ProductStatus;
import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.repository.ProductCategoryRepository;
import com.prabhix.platform.commerce.repository.ProductMediaRepository;
import com.prabhix.platform.commerce.repository.ProductRepository;
import com.prabhix.platform.commerce.repository.ProductVariantRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.security.PrabhixPrincipal;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductMediaRepository mediaRepository;
    private final ProductCategoryRepository categoryRepository;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;
    private final PublicProductImageService publicImageService;

    @Transactional(readOnly = true)
    public CursorPage<CommerceDtos.ProductSummary> listPublic(UUID organizationId, String orgSlug,
                                                              String search, String type, UUID categoryId,
                                                              String sort, String cursor, Integer limit) {
        int pageSize = clampLimit(limit);
        String normalizedSort = normalizeSort(sort);
        String q = blankToNull(search);
        String productType = blankToNull(type);
        List<Product> rows = fetchPublicPage(organizationId, q, productType, categoryId,
                normalizedSort, cursor, pageSize + 1);
        return CursorPage.of(rows, pageSize,
                p -> toSummary(p, organizationId, orgSlug),
                p -> encodePublicCursor(p, normalizedSort));
    }

    @Transactional(readOnly = true)
    public CursorPage<CommerceDtos.ProductSummary> listPublic(UUID organizationId, String orgSlug,
                                                              String cursor, Integer limit) {
        return listPublic(organizationId, orgSlug, null, null, null, "featured", cursor, limit);
    }

    @Transactional(readOnly = true)
    public CommerceDtos.ProductDetail getPublicBySlug(UUID organizationId, String orgSlug, String slug) {
        Product product = productRepository.findByOrganizationIdAndSlugAndDeletedAtIsNull(organizationId, slug)
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(() -> ApiException.of(ErrorCode.PRODUCT_NOT_FOUND, "That product was not found"));
        return toDetail(product, orgSlug);
    }

    @Transactional(readOnly = true)
    public CursorPage<CommerceDtos.ProductSummary> listAdmin(
            UUID organizationId, String status, Boolean featured, String type, String cursor, Integer limit) {
        int pageSize = clampLimit(limit);
        Cursor key = Cursor.decode(cursor);
        Cursor c = key == null ? Cursor.beginning() : key;
        var rows = productRepository.listWithCursor(
                organizationId, status, featured, type, c.timestamp(), c.id(), pageSize + 1);
        return CursorPage.of(rows, pageSize, p -> toSummary(p, organizationId),
                p -> Cursor.of(p.getCreatedAt(), p.getId()).encode());
    }

    @Transactional(readOnly = true)
    public CommerceDtos.ProductDetail getAdmin(UUID organizationId, UUID productId) {
        Product product = productRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(productId, organizationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.PRODUCT_NOT_FOUND, "That product was not found"));
        return toDetail(product);
    }

    /**
     * The public storefront queries require {@code published_at IS NOT NULL} as well as
     * ACTIVE status, so the stamp has to be applied wherever status is set. Leaving it to
     * each caller once meant an ACTIVE product could sit invisible in the shop forever with
     * nothing in the UI to explain why. The stamp is never cleared: it records when the
     * product first went live, and ACTIVE alone governs visibility.
     */
    private void applyStatus(Product product, ProductStatus status) {
        product.setStatus(status);
        if (status == ProductStatus.ACTIVE && product.getPublishedAt() == null) {
            product.setPublishedAt(Instant.now());
        }
    }

    @Transactional
    public CommerceDtos.ProductDetail create(PrabhixPrincipal principal, CommerceDtos.CreateProductRequest request) {
        UUID orgId = principal.requireOrganizationId();
        if (productRepository.existsByOrganizationIdAndSlugAndDeletedAtIsNull(orgId, request.slug())) {
            throw ApiException.conflict("A product with that slug already exists");
        }
        Product product = new Product();
        product.setOrganizationId(orgId);
        product.setSlug(request.slug());
        product.setName(request.name());
        product.setTagline(request.tagline());
        product.setDescription(request.description());
        product.setProductType(request.productType());
        applyStatus(product, request.status() == null ? ProductStatus.DRAFT : request.status());
        product.setHsnCode(request.hsnCode());
        if (request.attributes() != null) {
            product.setAttributes(request.attributes());
        }
        product = productRepository.save(product);
        events.publishEvent(AuditRequested.of(orgId, principal.userId(),
                "commerce.product.created", "commerce_product", product.getId()));
        return toDetail(product);
    }

    @Transactional
    public CommerceDtos.ProductDetail update(PrabhixPrincipal principal, UUID productId,
                                             CommerceDtos.UpdateProductRequest request) {
        UUID orgId = principal.requireOrganizationId();
        Product product = productRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(productId, orgId)
                .orElseThrow(() -> ApiException.of(ErrorCode.PRODUCT_NOT_FOUND, "That product was not found"));
        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.tagline() != null) {
            product.setTagline(request.tagline());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.status() != null) {
            applyStatus(product, request.status());
        }
        if (request.featured() != null) {
            product.setFeatured(request.featured());
        }
        if (request.sortOrder() != null) {
            product.setSortOrder(request.sortOrder());
        }
        if (request.heroImageFileId() != null) {
            product.setHeroImageFileId(request.heroImageFileId());
        }
        if (request.galleryFileIds() != null) {
            product.setGalleryFileIds(request.galleryFileIds());
        }
        if (request.seoTitle() != null) {
            product.setSeoTitle(request.seoTitle());
        }
        if (request.seoDescription() != null) {
            product.setSeoDescription(request.seoDescription());
        }
        if (request.hsnCode() != null) {
            product.setHsnCode(request.hsnCode());
        }
        if (request.attributes() != null) {
            product.setAttributes(request.attributes());
        }
        product = productRepository.save(product);
        if (request.categoryIds() != null) {
            replaceCategories(product.getId(), request.categoryIds());
        }
        return toDetail(product);
    }

    @Transactional
    public CommerceDtos.VariantView createVariant(PrabhixPrincipal principal, UUID productId,
                                                    CommerceDtos.CreateVariantRequest request) {
        UUID orgId = principal.requireOrganizationId();
        productRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(productId, orgId)
                .orElseThrow(() -> ApiException.of(ErrorCode.PRODUCT_NOT_FOUND, "That product was not found"));
        if (variantRepository.findByOrganizationIdAndSkuAndDeletedAtIsNull(orgId, request.sku()).isPresent()) {
            throw ApiException.conflict("A variant with that SKU already exists");
        }
        ProductVariant variant = new ProductVariant();
        variant.setOrganizationId(orgId);
        variant.setProductId(productId);
        applyVariantFields(variant, request);
        variant = variantRepository.save(variant);
        return toVariantView(variant);
    }

    @Transactional
    public CommerceDtos.VariantView updateVariant(PrabhixPrincipal principal, UUID productId, UUID variantId,
                                                  CommerceDtos.UpdateVariantRequest request) {
        UUID orgId = principal.requireOrganizationId();
        productRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(productId, orgId)
                .orElseThrow(() -> ApiException.of(ErrorCode.PRODUCT_NOT_FOUND, "That product was not found"));
        ProductVariant variant = variantRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(variantId, orgId)
                .filter(v -> v.getProductId().equals(productId))
                .orElseThrow(() -> ApiException.of(ErrorCode.VARIANT_NOT_FOUND, "That variant was not found"));
        if (request.sku() != null && !request.sku().equals(variant.getSku())) {
            variantRepository.findByOrganizationIdAndSkuAndDeletedAtIsNull(orgId, request.sku())
                    .filter(existing -> !existing.getId().equals(variantId))
                    .ifPresent(v -> {
                        throw ApiException.conflict("A variant with that SKU already exists");
                    });
            variant.setSku(request.sku());
        }
        if (request.name() != null) {
            variant.setName(request.name());
        }
        if (request.priceMinor() != null) {
            variant.setPriceMinor(request.priceMinor());
        }
        if (request.compareAtPriceMinor() != null) {
            variant.setCompareAtPriceMinor(request.compareAtPriceMinor());
        }
        if (request.trackInventory() != null) {
            variant.setTrackInventory(request.trackInventory());
        }
        if (request.stockOnHand() != null) {
            variant.setStockOnHand(request.stockOnHand());
        }
        if (request.billingInterval() != null) {
            variant.setBillingInterval(request.billingInterval());
        }
        if (request.downloadFileId() != null) {
            variant.setDownloadFileId(request.downloadFileId());
        }
        if (request.licenseTerms() != null) {
            variant.setLicenseTerms(request.licenseTerms());
        }
        if (request.serviceDurationDays() != null) {
            variant.setServiceDurationDays(request.serviceDurationDays());
        }
        if (request.deliverySlaDays() != null) {
            variant.setDeliverySlaDays(request.deliverySlaDays());
        }
        if (request.active() != null) {
            variant.setActive(request.active());
        }
        return toVariantView(variantRepository.save(variant));
    }

    @Transactional
    public void archiveVariant(PrabhixPrincipal principal, UUID productId, UUID variantId) {
        UUID orgId = principal.requireOrganizationId();
        productRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(productId, orgId)
                .orElseThrow(() -> ApiException.of(ErrorCode.PRODUCT_NOT_FOUND, "That product was not found"));
        ProductVariant variant = variantRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(variantId, orgId)
                .filter(v -> v.getProductId().equals(productId))
                .orElseThrow(() -> ApiException.of(ErrorCode.VARIANT_NOT_FOUND, "That variant was not found"));
        variant.setActive(false);
        variant.setDeletedAt(Instant.now());
        variantRepository.save(variant);
    }

    @Transactional
    public List<CommerceDtos.CategoryView> listCategories(UUID organizationId) {
        return categoryRepository.findByOrganizationIdAndDeletedAtIsNullOrderBySortOrderAsc(organizationId)
                .stream().map(this::toCategoryView).toList();
    }

    @Transactional
    public CommerceDtos.CategoryView createCategory(PrabhixPrincipal principal,
                                                    CommerceDtos.CreateCategoryRequest request) {
        UUID orgId = principal.requireOrganizationId();
        ProductCategory category = new ProductCategory();
        category.setOrganizationId(orgId);
        category.setSlug(request.slug());
        category.setName(request.name());
        category.setDescription(request.description());
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        category = categoryRepository.save(category);
        return toCategoryView(category);
    }

    private void replaceCategories(UUID productId, List<UUID> categoryIds) {
        entityManager.createNativeQuery(
                        "DELETE FROM commerce_product_category_links WHERE product_id = :productId")
                .setParameter("productId", productId)
                .executeUpdate();
        for (UUID categoryId : categoryIds) {
            entityManager.createNativeQuery(
                            "INSERT INTO commerce_product_category_links (product_id, category_id) VALUES (:p, :c)")
                    .setParameter("p", productId)
                    .setParameter("c", categoryId)
                    .executeUpdate();
        }
    }

    private void applyVariantFields(ProductVariant variant, CommerceDtos.CreateVariantRequest request) {
        variant.setName(request.name());
        variant.setSku(request.sku());
        variant.setPriceMinor(request.priceMinor());
        variant.setCompareAtPriceMinor(request.compareAtPriceMinor());
        if (request.trackInventory() != null) {
            variant.setTrackInventory(request.trackInventory());
        }
        if (request.stockOnHand() != null) {
            variant.setStockOnHand(request.stockOnHand());
        }
        variant.setBillingInterval(request.billingInterval());
        variant.setDownloadFileId(request.downloadFileId());
        variant.setLicenseTerms(request.licenseTerms());
        variant.setServiceDurationDays(request.serviceDurationDays());
        variant.setDeliverySlaDays(request.deliverySlaDays());
    }

    private CommerceDtos.ProductSummary toSummary(Product product, UUID organizationId, String orgSlug) {
        long fromPrice = variantRepository
                .findByProductIdAndOrganizationIdAndDeletedAtIsNullOrderBySortOrderAsc(
                        product.getId(), organizationId).stream()
                .filter(ProductVariant::isActive)
                .map(ProductVariant::getPriceMinor)
                .min(Comparator.naturalOrder())
                .orElse(0L);
        return new CommerceDtos.ProductSummary(
                product.getId(),
                product.getSlug(),
                product.getName(),
                product.getTagline(),
                product.getProductType(),
                product.isFeatured(),
                product.getHeroImageFileId(),
                imageUrl(orgSlug, product.getHeroImageFileId()),
                fromPrice,
                "INR");
    }

    private CommerceDtos.ProductSummary toSummary(Product product, UUID organizationId) {
        return toSummary(product, organizationId, null);
    }

    private CommerceDtos.ProductDetail toDetail(Product product) {
        return toDetail(product, null);
    }

    private CommerceDtos.ProductDetail toDetail(Product product, String orgSlug) {
        List<ProductVariant> variants = variantRepository
                .findByProductIdAndOrganizationIdAndDeletedAtIsNullOrderBySortOrderAsc(
                        product.getId(), product.getOrganizationId());
        List<UUID> categoryIds = categoryRepository.findCategoryIdsForProduct(product.getId());
        List<UUID> galleryIds = product.getGalleryFileIds() == null ? List.of() : product.getGalleryFileIds();
        return new CommerceDtos.ProductDetail(
                product.getId(),
                product.getSlug(),
                product.getName(),
                product.getTagline(),
                product.getDescription(),
                product.getProductType(),
                product.getStatus(),
                product.isFeatured(),
                product.getHeroImageFileId(),
                imageUrl(orgSlug, product.getHeroImageFileId()),
                galleryIds,
                galleryIds.stream().map(id -> imageUrl(orgSlug, id)).toList(),
                product.getSeoTitle(),
                product.getSeoDescription(),
                product.getHsnCode(),
                product.getAttributes(),
                variants.stream().map(this::toVariantView).toList(),
                categoryIds,
                product.getPublishedAt());
    }

    private String imageUrl(String orgSlug, UUID fileId) {
        if (orgSlug == null || fileId == null) {
            return null;
        }
        return publicImageService.publicImagePath(orgSlug, fileId);
    }

    private List<Product> fetchPublicPage(UUID organizationId, String search, String type, UUID categoryId,
                                          String sort, String cursor, int limit) {
        return switch (sort) {
            case "name" -> {
                PublicNameCursor c = PublicNameCursor.decode(cursor);
                yield productRepository.listActivePublicByName(
                        organizationId, search, type, categoryId, c.name(), c.id(), limit);
            }
            case "price-asc" -> {
                PublicPriceCursor c = PublicPriceCursor.decode(cursor);
                yield productRepository.listActivePublicByPriceAsc(
                        organizationId, search, type, categoryId, c.priceMinor(), c.id(), limit);
            }
            case "price-desc" -> {
                PublicPriceCursor c = PublicPriceCursor.decode(cursor);
                yield productRepository.listActivePublicByPriceDesc(
                        organizationId, search, type, categoryId, c.priceMinor(), c.id(), limit);
            }
            default -> {
                Cursor c = cursor != null ? Cursor.decode(cursor) : Cursor.beginning();
                yield productRepository.listActivePublicFiltered(
                        organizationId, search, type, categoryId, c.timestamp(), c.id(), limit);
            }
        };
    }

    private String encodePublicCursor(Product product, String sort) {
        return switch (sort) {
            case "name" -> PublicNameCursor.of(product.getName(), product.getId()).encode();
            case "price-asc", "price-desc" -> {
                long price = variantRepository
                        .findByProductIdAndOrganizationIdAndDeletedAtIsNullOrderBySortOrderAsc(
                                product.getId(), product.getOrganizationId()).stream()
                        .filter(ProductVariant::isActive)
                        .map(ProductVariant::getPriceMinor)
                        .min(Comparator.naturalOrder())
                        .orElse(0L);
                yield PublicPriceCursor.of(price, product.getId()).encode();
            }
            default -> Cursor.of(product.getCreatedAt(), product.getId()).encode();
        };
    }

    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "featured";
        }
        return switch (sort) {
            case "featured", "name", "price-asc", "price-desc" -> sort;
            default -> "featured";
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PublicNameCursor(String name, UUID id) {
        static PublicNameCursor beginning() {
            return new PublicNameCursor("", new UUID(-1L, -1L));
        }

        static PublicNameCursor decode(String encoded) {
            if (encoded == null || encoded.isBlank()) {
                return beginning();
            }
            if (encoded.startsWith("n:")) {
                int split = encoded.lastIndexOf(':');
                return new PublicNameCursor(encoded.substring(2, split), UUID.fromString(encoded.substring(split + 1)));
            }
            return beginning();
        }

        static PublicNameCursor of(String name, UUID id) {
            return new PublicNameCursor(name == null ? "" : name, id);
        }

        String encode() {
            return "n:" + name + ":" + id;
        }
    }

    private record PublicPriceCursor(long priceMinor, UUID id) {
        static PublicPriceCursor beginning() {
            return new PublicPriceCursor(Long.MAX_VALUE, new UUID(-1L, -1L));
        }

        static PublicPriceCursor decode(String encoded) {
            if (encoded == null || encoded.isBlank()) {
                return beginning();
            }
            if (encoded.startsWith("p:")) {
                int split = encoded.lastIndexOf(':');
                return new PublicPriceCursor(
                        Long.parseLong(encoded.substring(2, split)),
                        UUID.fromString(encoded.substring(split + 1)));
            }
            return beginning();
        }

        static PublicPriceCursor of(long priceMinor, UUID id) {
            return new PublicPriceCursor(priceMinor, id);
        }

        String encode() {
            return "p:" + priceMinor + ":" + id;
        }
    }

    private CommerceDtos.VariantView toVariantView(ProductVariant variant) {
        Integer available = variant.isTrackInventory()
                ? variant.getStockOnHand() - variant.getStockReserved() : null;
        return new CommerceDtos.VariantView(
                variant.getId(),
                variant.getName(),
                variant.getSku(),
                variant.getPriceMinor(),
                variant.getCompareAtPriceMinor(),
                variant.getCurrency(),
                variant.isTrackInventory(),
                available,
                variant.getBillingInterval(),
                variant.getDownloadFileId(),
                variant.getServiceDurationDays(),
                variant.getDeliverySlaDays(),
                variant.isActive());
    }

    private CommerceDtos.CategoryView toCategoryView(ProductCategory category) {
        return new CommerceDtos.CategoryView(
                category.getId(), category.getSlug(), category.getName(),
                category.getDescription(), category.getSortOrder());
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
