package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByCartIdAndOrganizationId(UUID cartId, UUID organizationId);

    Optional<CartItem> findByCartIdAndVariantIdAndOrganizationId(UUID cartId, UUID variantId, UUID organizationId);

    void deleteByCartIdAndOrganizationId(UUID cartId, UUID organizationId);
}
