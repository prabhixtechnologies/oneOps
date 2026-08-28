package com.prabhix.platform.ai.service;

import com.prabhix.platform.ai.dto.AiDtos;
import com.prabhix.platform.commerce.domain.CommerceEnums;
import com.prabhix.platform.commerce.repository.ProductRepository;
import com.prabhix.platform.common.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommerceAiService {

    private final AiOrchestrator orchestrator;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public AiDtos.ProductDescriptionResult draftDescription(UUID organizationId, UUID userId, UUID productId) {
        var product = productRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(productId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Product"));
        var availability = orchestrator.availability(organizationId);
        if (!availability.configured()) {
            return new AiDtos.ProductDescriptionResult(false, "", null, null);
        }
        CommerceEnums.ProductType type = product.getProductType();
        var result = orchestrator.complete(new AiOrchestrator.AiRequest(
                organizationId, userId, "commerce", "commerce.product_description",
                Map.of(
                        "name", product.getName(),
                        "tagline", product.getTagline() != null ? product.getTagline() : "",
                        "productType", type != null ? type.name() : "PRODUCT",
                        "attributes", product.getAttributes() != null ? product.getAttributes().toString() : "{}"),
                null, null, "commerce_product", productId));
        return new AiDtos.ProductDescriptionResult(
                true, result.text(), result.provider().configKey(), result.model());
    }
}
