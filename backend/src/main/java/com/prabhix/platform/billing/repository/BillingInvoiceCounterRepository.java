package com.prabhix.platform.billing.repository;

import com.prabhix.platform.billing.domain.BillingInvoiceCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BillingInvoiceCounterRepository extends JpaRepository<BillingInvoiceCounter, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM BillingInvoiceCounter c WHERE c.financialYear = :fy")
    Optional<BillingInvoiceCounter> lockByFinancialYear(@Param("fy") String financialYear);
}
