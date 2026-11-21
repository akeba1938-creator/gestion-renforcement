package com.ecole.saas.center.infrastructure;

import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Verrous transactionnels consultatifs PostgreSQL ({@code pg_advisory_xact_lock}) pour sérialiser
 * les inscriptions concurrentes dans une cohorte et respecter sa capacité maximale.
 */
@Repository
public class CenterLockRepository {

    private final EntityManager entityManager;

    public CenterLockRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void lockGroupCapacity(UUID groupId) {
        UUID tenantId = TenantContext.requireCurrent().value();
        String lockKey = tenantId + ":center-group-capacity:" + groupId;
        entityManager
                .createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))) AS advisory_lock")
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }
}
