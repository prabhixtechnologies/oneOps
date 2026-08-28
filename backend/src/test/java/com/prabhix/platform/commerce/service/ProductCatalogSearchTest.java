package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.Product;
import com.prabhix.platform.commerce.domain.CommerceEnums;
import com.prabhix.platform.commerce.repository.ProductCategoryRepository;
import com.prabhix.platform.commerce.repository.ProductMediaRepository;
import com.prabhix.platform.commerce.repository.ProductRepository;
import com.prabhix.platform.commerce.repository.ProductVariantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCatalogSearchTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private ProductMediaRepository mediaRepository;
    @Mock private ProductCategoryRepository categoryRepository;
    @Mock private EntityManager entityManager;
    @Mock private ApplicationEventPublisher events;
    @Mock private PublicProductImageService publicImageService;

    private ProductCatalogService catalogService;

    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        catalogService = new ProductCatalogService(
                productRepository, variantRepository, mediaRepository, categoryRepository,
                entityManager, events, publicImageService);
    }

    @Test
    void appliesSearchTypeAndCategoryFilters() {
        Product product = sampleProduct();
        when(productRepository.listActivePublicFiltered(
                eq(orgId), eq("widget"), eq("DIGITAL"), eq(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                any(Instant.class), any(UUID.class), eq(26)))
                .thenReturn(List.of(product));
        when(variantRepository.findByProductIdAndOrganizationIdAndDeletedAtIsNullOrderBySortOrderAsc(
                product.getId(), orgId)).thenReturn(List.of());
        when(publicImageService.publicImagePath("acme", product.getHeroImageFileId()))
                .thenReturn("/api/v1/commerce/public/acme/images/" + product.getHeroImageFileId());

        var page = catalogService.listPublic(
                orgId, "acme", "widget", "DIGITAL",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "featured", null, 25);

        assertEquals(1, page.items().size());
        verify(productRepository).listActivePublicFiltered(
                eq(orgId), eq("widget"), eq("DIGITAL"),
                eq(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                any(Instant.class), any(UUID.class), eq(26));
    }

    @Test
    void usesNameSortQuery() {
        when(productRepository.listActivePublicByName(
                eq(orgId), isNull(), isNull(), isNull(), eq(""), any(UUID.class), eq(26)))
                .thenReturn(List.of());

        catalogService.listPublic(orgId, "acme", null, null, null, "name", null, 25);

        verify(productRepository).listActivePublicByName(
                eq(orgId), isNull(), isNull(), isNull(), eq(""), any(UUID.class), eq(26));
    }

    private Product sampleProduct() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setOrganizationId(orgId);
        product.setSlug("widget");
        product.setName("Widget Pro");
        product.setProductType(CommerceEnums.ProductType.DIGITAL);
        product.setCreatedAt(Instant.now());
        product.setHeroImageFileId(UUID.randomUUID());
        return product;
    }
}
