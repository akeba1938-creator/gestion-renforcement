package com.ecole.saas.audit.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.audit.AuditSeverity;
import com.ecole.saas.audit.PlatformAuditRecordCommand;
import com.ecole.saas.audit.domain.AuditLog;
import com.ecole.saas.audit.domain.AuditLogNotFoundException;
import com.ecole.saas.audit.domain.AuditQueryCriteria;
import com.ecole.saas.audit.domain.PlatformAuditLog;
import com.ecole.saas.audit.infrastructure.AuditLogRepository;
import com.ecole.saas.audit.infrastructure.PlatformAuditLogRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consultation plateforme du journal d'audit ({@code ADMIN_SAAS}) :
 * <ul>
 *   <li>Consultation de {@code platform_audit_logs} hors contexte tenant.</li>
 *   <li>Consultation supervisée des {@code audit_logs} d'un établissement cible avec motif obligatoire
 *       et double journalisation (plateforme + établissement).</li>
 * </ul>
 */
@Service
@PreAuthorize(AuditRoles.PLATFORM_ADMIN)
public class PlatformAuditQueryService {

    private final PlatformAuditLogRepository platformAuditLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogPort auditLogPort;
    private final TransactionTemplate readOnlyTransactionTemplate;

    public PlatformAuditQueryService(
            PlatformAuditLogRepository platformAuditLogRepository,
            AuditLogRepository auditLogRepository,
            AuditLogPort auditLogPort,
            PlatformTransactionManager transactionManager) {
        this.platformAuditLogRepository = platformAuditLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogPort = auditLogPort;
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

    @Transactional(readOnly = true)
    public Page<PlatformAuditLog> searchPlatformAuditLogs(PlatformAuditSearchFilter filter, Pageable pageable) {
        ensurePlatformScope();
        return platformAuditLogRepository.findAll(buildPlatformSpecification(filter), pageable);
    }

    @Transactional(readOnly = true)
    public PlatformAuditLog getPlatformAuditLog(UUID auditLogId) {
        Objects.requireNonNull(auditLogId, "L'identifiant d'entrée d'audit plateforme est obligatoire.");
        ensurePlatformScope();
        return platformAuditLogRepository.findById(auditLogId)
                .orElseThrow(() -> new AuditLogNotFoundException("Entrée du journal d'audit plateforme introuvable."));
    }

    public Page<AuditLog> searchSchoolAuditLogs(
            UUID schoolId,
            String rawReason,
            AuditSearchFilter filter,
            Pageable pageable) {
        Objects.requireNonNull(schoolId, "L'identifiant d'établissement est obligatoire.");
        ensurePlatformScope();

        String sanitizedReason = AuditQueryCriteria.requireSupportAccessReason(rawReason);
        Specification<AuditLog> specification = TenantAuditQueryService.buildSpecification(filter);

        // 1. Journalise l'accès supervisé dans le journal établissement (et vérifie l'existence via FK tenant_id)
        try {
            auditLogPort.recordTenantEventForSchool(
                    schoolId,
                    AuditRecordCommand.warning(
                            AuditCategory.AUDIT,
                            AuditAction.AUDIT_TENANT_LOGS_ACCESSED,
                            AuditOutcome.SUCCESS,
                            "AUDIT_LOG",
                            schoolId,
                            "Consultation supervisée du journal d'audit par un administrateur SaaS.",
                            Map.of("reason", sanitizedReason)));
        } catch (DataIntegrityViolationException ex) {
            throw new AuditLogNotFoundException("Établissement introuvable.");
        }

        // 2. Journalise l'accès supervisé dans le journal plateforme (gouvernance ADMIN_SAAS)
        auditLogPort.recordPlatformEvent(new PlatformAuditRecordCommand(
                AuditCategory.AUDIT,
                AuditAction.AUDIT_TENANT_LOGS_ACCESSED,
                AuditOutcome.SUCCESS,
                AuditSeverity.WARNING,
                schoolId,
                "AUDIT_LOG",
                schoolId,
                "Consultation supervisée du journal d'audit d'un établissement.",
                sanitizedReason,
                null,
                null,
                null,
                Map.of("targetSchoolId", schoolId.toString())));

        // 3. Lecture isolée par RLS pour l'établissement cible uniquement
        return TenantContext.executeWithTenant(
                TenantId.of(schoolId),
                () -> readOnlyTransactionTemplate.execute(
                        status -> auditLogRepository.findAll(specification, pageable)));
    }

    private static void ensurePlatformScope() {
        if (TenantContext.getCurrent().isPresent()) {
            throw new IllegalStateException(
                    "Contexte d'établissement interdit pour une consultation plateforme.");
        }
    }

    private static Specification<PlatformAuditLog> buildPlatformSpecification(PlatformAuditSearchFilter filter) {
        PlatformAuditSearchFilter effective = filter != null
                ? filter
                : new PlatformAuditSearchFilter(
                        null, null, null, null, null, null, null, null, null, null, null, null, null);
        AuditQueryCriteria.validateDateRange(effective.from(), effective.to());
        String normalizedResourceType = AuditQueryCriteria.normalizeResourceTypeFilter(effective.resourceType());
        String normalizedSearch = AuditQueryCriteria.normalizeSearchFilter(effective.search());
        String trimmedCorrelation = (effective.correlationId() == null || effective.correlationId().isBlank())
                ? null
                : effective.correlationId().trim();

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (effective.category() != null) {
                predicates.add(cb.equal(root.get("category"), effective.category()));
            }
            if (effective.action() != null) {
                predicates.add(cb.equal(root.get("action"), effective.action()));
            }
            if (effective.outcome() != null) {
                predicates.add(cb.equal(root.get("outcome"), effective.outcome()));
            }
            if (effective.severity() != null) {
                predicates.add(cb.equal(root.get("severity"), effective.severity()));
            }
            if (effective.actorId() != null) {
                predicates.add(cb.equal(root.get("actorId"), effective.actorId()));
            }
            if (effective.actorType() != null) {
                predicates.add(cb.equal(root.get("actorType"), effective.actorType()));
            }
            if (effective.targetSchoolId() != null) {
                predicates.add(cb.equal(root.get("targetSchoolId"), effective.targetSchoolId()));
            }
            if (normalizedResourceType != null) {
                predicates.add(cb.equal(root.get("resourceType"), normalizedResourceType));
            }
            if (effective.resourceId() != null) {
                predicates.add(cb.equal(root.get("resourceId"), effective.resourceId()));
            }
            if (trimmedCorrelation != null) {
                predicates.add(cb.equal(root.get("correlationId"), trimmedCorrelation));
            }
            if (effective.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), effective.from()));
            }
            if (effective.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), effective.to()));
            }
            if (normalizedSearch != null) {
                predicates.add(cb.like(cb.lower(root.get("summary")), "%" + normalizedSearch + "%"));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
