package com.ecole.saas.student.infrastructure;

import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Verrous transactionnels consultatifs PostgreSQL ({@code pg_advisory_xact_lock}) pour sérialiser
 * les vérifications concurrentes de quota élèves et de capacité de classe au sein du tenant courant.
 */
@Repository
public class StudentLockRepository {

    private final EntityManager entityManager;

    public StudentLockRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void lockStudentQuotaForTenant() {
        UUID tenantId = TenantContext.requireCurrent().value();
        acquireLock(tenantId + ":student-quota");
    }

    public void lockClassRoomCapacity(UUID classRoomId) {
        UUID tenantId = TenantContext.requireCurrent().value();
        acquireLock(tenantId + ":classroom-capacity:" + classRoomId);
    }

    private void acquireLock(String lockKey) {
        entityManager
                .createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))) AS advisory_lock")
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }
}
