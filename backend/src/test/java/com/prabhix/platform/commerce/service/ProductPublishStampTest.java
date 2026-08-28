package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.CommerceEnums.ProductStatus;
import com.prabhix.platform.commerce.domain.CommerceEnums.ProductType;
import com.prabhix.platform.commerce.domain.Product;
import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.repository.ProductCategoryRepository;
import com.prabhix.platform.commerce.repository.ProductMediaRepository;
import com.prabhix.platform.commerce.repository.ProductRepository;
import com.prabhix.platform.commerce.repository.ProductVariantRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The public storefront query filters on {@code status = 'ACTIVE' AND published_at IS NOT NULL}.
 * If a write path sets one without the other, the product is live as far as the console is
 * concerned but invisible in the shop, with nothing on screen to explain it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductPublishStampTest {

    @Mock ProductRepository productRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock ProductMediaRepository mediaRepository;
    @Mock ProductCategoryRepository categoryRepository;
    @Mock EntityManager entityManager;
    @Mock ApplicationEventPublisher events;
    @Mock PublicProductImageService publicImageService;

    ProductCatalogService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProductCatalogService(productRepository, variantRepository, mediaRepository,
                categoryRepository, entityManager, events, publicImageService);
        when(productRepository.save(any())).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(variantRepository.findByProductIdAndOrganizationIdAndDeletedAtIsNullOrderBySortOrderAsc(
                any(), any())).thenReturn(List.of());
    }

    @Test
    void creatingADraftLeavesItUnpublished() {
        service.create(principal(), createRequest(null));

        assertNull(captureSaved().getPublishedAt(),
                "a draft must not be stamped as published");
    }

    @Test
    void creatingAnActiveProductStampsPublishedAt() {
        service.create(principal(), createRequest(ProductStatus.ACTIVE));

        Product saved = captureSaved();
        assertEquals(ProductStatus.ACTIVE, saved.getStatus());
        assertNotNull(saved.getPublishedAt(),
                "an ACTIVE product without published_at never appears in the storefront");
    }

    @Test
    void publishingViaUpdateStampsPublishedAt() {
        Product existing = new Product();
        existing.setId(UUID.randomUUID());
        existing.setOrganizationId(orgId);
        existing.setStatus(ProductStatus.DRAFT);
        when(productRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(existing.getId(), orgId))
                .thenReturn(Optional.of(existing));

        service.update(principal(), existing.getId(), updateStatus(ProductStatus.ACTIVE));

        assertNotNull(existing.getPublishedAt());
    }

    /** Unpublishing hides the product by status; the original go-live date is history. */
    @Test
    void unpublishingKeepsTheOriginalPublishDate() {
        Product existing = new Product();
        existing.setId(UUID.randomUUID());
        existing.setOrganizationId(orgId);
        existing.setStatus(ProductStatus.DRAFT);
        when(productRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(existing.getId(), orgId))
                .thenReturn(Optional.of(existing));

        service.update(principal(), existing.getId(), updateStatus(ProductStatus.ACTIVE));
        var firstPublish = existing.getPublishedAt();
        service.update(principal(), existing.getId(), updateStatus(ProductStatus.ARCHIVED));

        assertEquals(firstPublish, existing.getPublishedAt());
        assertEquals(ProductStatus.ARCHIVED, existing.getStatus());
    }

    private Product captureSaved() {
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        org.mockito.Mockito.verify(productRepository, org.mockito.Mockito.atLeastOnce())
                .save(captor.capture());
        return captor.getValue();
    }

    private CommerceDtos.CreateProductRequest createRequest(ProductStatus status) {
        return new CommerceDtos.CreateProductRequest(
                "toolkit", "Toolkit", "A tagline", "A description",
                ProductType.DIGITAL, "998434", null, status);
    }

    private CommerceDtos.UpdateProductRequest updateStatus(ProductStatus status) {
        return new CommerceDtos.UpdateProductRequest(
                null, null, null, status, null, null, null, null, null, null, null, null, null);
    }

    private PrabhixPrincipal principal() {
        return new PrabhixPrincipal(userId, "owner@prabhix.test", "Owner", orgId,
                Set.of(), null, false);
    }
}
