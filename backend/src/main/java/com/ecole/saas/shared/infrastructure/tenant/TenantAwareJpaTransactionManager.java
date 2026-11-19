package com.ecole.saas.shared.infrastructure.tenant;

import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    public TenantAwareJpaTransactionManager(EntityManagerFactory entityManagerFactory, DataSource dataSource) {
        super(entityManagerFactory);
        setDataSource(dataSource);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        EntityManagerHolder emHolder = (EntityManagerHolder)
                TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        if (emHolder != null) {
            EntityManager em = emHolder.getEntityManager();
            String tenantValue = TenantContext.getCurrent()
                    .map(id -> id.value().toString())
                    .orElse("");
            em.createNativeQuery("SELECT set_config('app.current_tenant_id', :tenantId, true)")
                    .setParameter("tenantId", tenantValue)
                    .getSingleResult();
        }
    }
}
