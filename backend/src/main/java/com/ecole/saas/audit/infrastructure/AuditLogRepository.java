package com.ecole.saas.audit.infrastructure;

import com.ecole.saas.audit.domain.AuditLog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Dépôt strictement en ajout seul sur {@code audit_logs} : aucune méthode de suppression n'est exposée.
 */
public interface AuditLogRepository extends Repository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    AuditLog save(AuditLog entry);

    Optional<AuditLog> findById(UUID id);
}
