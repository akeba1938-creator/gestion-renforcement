package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.FinanceSequenceType;
import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Générateur atomique de numéros séquentiels isolés par établissement
 * (factures, reçus sans trou et registre comptable).
 */
@Repository
public class FinanceSequenceRepository {

    private final EntityManager entityManager;

    public FinanceSequenceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public long nextSequenceValue(FinanceSequenceType sequenceType, int sequenceYear) {
        Objects.requireNonNull(sequenceType, "Le type de séquence est obligatoire.");
        UUID tenantId = TenantContext.requireCurrent().value();
        Number nextValue = (Number) entityManager
                .createNativeQuery("""
                        INSERT INTO finance_sequences (tenant_id, sequence_type, sequence_year, last_value, created_at, updated_at)
                        VALUES (:tenantId, :sequenceType, :sequenceYear, 1, now(), now())
                        ON CONFLICT (tenant_id, sequence_type, sequence_year)
                        DO UPDATE SET
                            last_value = finance_sequences.last_value + 1,
                            updated_at = now()
                        RETURNING last_value
                        """)
                .setParameter("tenantId", tenantId)
                .setParameter("sequenceType", sequenceType.name())
                .setParameter("sequenceYear", sequenceYear)
                .getSingleResult();
        return nextValue.longValue();
    }
}
