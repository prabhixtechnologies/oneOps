package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, UUID> {

    Optional<DiscountCode> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    Optional<DiscountCode> findByOrganizationIdAndCodeIgnoreCaseAndDeletedAtIsNull(
            UUID organizationId, String code);

    List<DiscountCode> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId);
}
