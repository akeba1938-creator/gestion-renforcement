package com.ecole.saas.student.infrastructure;

import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Générateur atomique de numéros séquentiels de matricules isolé par établissement et par année.
 * Exécuté dans la transaction courante où {@code app.current_tenant_id} est déjà positionné pour la RLS.
 */
@Repository
public class MatriculeSequenceRepository {

    private final EntityManager entityManager;

    public MatriculeSequenceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public long nextSequenceValue(int sequenceYear) {
        UUID tenantId = TenantContext.requireCurrent().value();
        Number nextValue = (Number) entityManager
                .createNativeQuery("""
                        INSERT INTO student_matricule_sequences (tenant_id, sequence_year, last_value, created_at, updated_at)
                        VALUES (:tenantId, :sequenceYear, 1, now(), now())
                        ON CONFLICT (tenant_id, sequence_year)
                        DO UPDATE SET
                            last_value = student_matricule_sequences.last_value + 1,
                            updated_at = now()
                        RETURNING last_value
                        """)
                .setParameter("tenantId", tenantId)
                .setParameter("sequenceYear", sequenceYear)
                .getSingleResult();
        return nextValue.longValue();
    }
}
