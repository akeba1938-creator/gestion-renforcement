package com.ecole.saas.shared.infrastructure.tenant;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MultiTenantPersistenceConfig {

    @Bean
    public HibernatePropertiesCustomizer tenantHibernatePropertiesCustomizer(
            TenantIdentifierResolver tenantIdentifierResolver) {
        return properties -> properties.put(
                AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER,
                tenantIdentifierResolver);
    }

    @Bean
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory,
            DataSource dataSource) {
        return new TenantAwareJpaTransactionManager(entityManagerFactory, dataSource);
    }
}
