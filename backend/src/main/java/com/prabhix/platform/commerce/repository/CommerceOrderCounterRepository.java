package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.CommerceOrderCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CommerceOrderCounterRepository
        extends JpaRepository<CommerceOrderCounter, CommerceOrderCounter.CommerceOrderCounterId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CommerceOrderCounter c WHERE c.organizationId = :orgId AND c.financialYear = :fy")
    Optional<CommerceOrderCounter> lockByOrgAndFinancialYear(@Param("orgId") UUID orgId, @Param("fy") String fy);
}
