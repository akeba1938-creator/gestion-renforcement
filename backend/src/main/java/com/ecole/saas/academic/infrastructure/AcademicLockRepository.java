package com.ecole.saas.academic.infrastructure;

import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Verrous transactionnels consultatifs PostgreSQL ({@code pg_advisory_xact_lock}) pour sérialiser
 * les modifications de capacité de classe avec les inscriptions/transferts concurrents d'élèves.
 * Utilise strictement la même clé de verrou que {@code StudentLockRepository.lockClassRoomCapacity}.
 */
@Repository
public class AcademicLockRepository {

    private final EntityManager entityManager;

    public AcademicLockRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void lockClassRoomCapacity(UUID classRoomId) {
        UUID tenantId = TenantContext.requireCurrent().value();
        String lockKey = tenantId + ":classroom-capacity:" + classRoomId;
        entityManager
                .createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))) AS advisory_lock")
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }
}
