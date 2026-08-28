package com.prabhix.platform.config;

import com.prabhix.platform.security.tenant.TenantFilterBridge;
import com.prabhix.platform.security.tenant.TenantFilterEnabler;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Activates the tenant filter at transaction boundaries via a custom
 * {@link JpaTransactionManager}. That is more reliable than a one-shot session observer alone:
 * {@link com.prabhix.platform.security.tenant.TenantContext#runAs} can set a tenant after the
 * session opens (marketing résumé uploads, invoice PDF generation), and {@code doBegin} re-applies
 * the filter whenever a new transaction starts with an active tenant.
 */
@Configuration
public class TenantFilterConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new TenantAwareJpaTransactionManager(entityManagerFactory);
    }

    @Bean
    TenantFilterBridgeRegistrar tenantFilterBridgeRegistrar() {
        return new TenantFilterBridgeRegistrar();
    }

    static final class TenantAwareJpaTransactionManager extends JpaTransactionManager {

        TenantAwareJpaTransactionManager(EntityManagerFactory entityManagerFactory) {
            super(entityManagerFactory);
        }

        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
            super.doBegin(transaction, definition);
            EntityManager entityManager =
                    EntityManagerFactoryUtils.getTransactionalEntityManager(getEntityManagerFactory());
            if (entityManager != null) {
                TenantFilterEnabler.apply(entityManager.unwrap(Session.class));
            }
        }
    }

    static final class TenantFilterBridgeRegistrar {

        @PersistenceContext
        private EntityManager entityManager;

        @PostConstruct
        void register() {
            TenantFilterBridge.setApplier(() -> {
                try {
                    TenantFilterEnabler.apply(entityManager.unwrap(Session.class));
                } catch (IllegalStateException ignored) {
                    // No persistence context on this thread yet.
                }
            });
        }
    }
}
