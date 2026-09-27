package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.DiscountCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DiscountCode d WHERE d.id = :id")
    Optional<DiscountCode> lockById(UUID id);

    Optional<DiscountCode> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    Optional<DiscountCode> findByOrganizationIdAndCodeIgnoreCaseAndDeletedAtIsNull(
            UUID organizationId, String code);

    List<DiscountCode> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId);
}
