package com.ecole.saas.audit.infrastructure;

import com.ecole.saas.audit.domain.PlatformAuditLog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Dépôt strictement en ajout seul sur {@code platform_audit_logs} : aucune méthode de suppression n'est exposée.
 */
public interface PlatformAuditLogRepository
        extends Repository<PlatformAuditLog, UUID>, JpaSpecificationExecutor<PlatformAuditLog> {

    PlatformAuditLog save(PlatformAuditLog entry);

    Optional<PlatformAuditLog> findById(UUID id);
}
