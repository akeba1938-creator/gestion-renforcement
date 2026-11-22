package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Verrous transactionnels consultatifs PostgreSQL ({@code pg_advisory_xact_lock}) pour sérialiser
 * les soumissions concurrentes de clôture journalière de caisse par établissement et par date.
 */
@Repository
public class FinanceLockRepository {

    private final EntityManager entityManager;

    public FinanceLockRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void lockCashClosingDate(LocalDate closingDate) {
        UUID tenantId = TenantContext.requireCurrent().value();
        Objects.requireNonNull(closingDate, "La date de clôture est obligatoire.");
        String lockKey = tenantId + ":cash-closing:" + closingDate;
        entityManager
                .createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))) AS advisory_lock")
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }
}
