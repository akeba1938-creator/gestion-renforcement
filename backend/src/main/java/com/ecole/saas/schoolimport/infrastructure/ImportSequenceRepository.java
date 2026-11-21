package com.ecole.saas.schoolimport.infrastructure;

import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Générateur atomique de numéros séquentiels de lots d'import, isolé par établissement et année.
 */
@Repository
public class ImportSequenceRepository {

    private final EntityManager entityManager;

    public ImportSequenceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public int nextSequenceValue(int sequenceYear) {
        UUID tenantId = TenantContext.requireCurrent().value();
        Number nextValue = (Number) entityManager
                .createNativeQuery("""
                        INSERT INTO schoolimport_sequences (tenant_id, sequence_year, last_value, created_at, updated_at)
                        VALUES (:tenantId, :sequenceYear, 1, now(), now())
                        ON CONFLICT (tenant_id, sequence_year)
                        DO UPDATE SET
                            last_value = schoolimport_sequences.last_value + 1,
                            updated_at = now()
                        RETURNING last_value
                        """)
                .setParameter("tenantId", tenantId)
                .setParameter("sequenceYear", sequenceYear)
                .getSingleResult();
        return nextValue.intValue();
    }
}
