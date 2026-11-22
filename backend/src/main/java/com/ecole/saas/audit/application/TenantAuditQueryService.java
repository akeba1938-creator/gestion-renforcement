package com.ecole.saas.audit.application;

import com.ecole.saas.audit.domain.AuditLog;
import com.ecole.saas.audit.domain.AuditLogNotFoundException;
import com.ecole.saas.audit.domain.AuditQueryCriteria;
import com.ecole.saas.audit.infrastructure.AuditLogRepository;
import com.ecole.saas.shared.application.SubscriptionGatePort;
import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultation sécurisée et filtrée du journal d'audit pour le pilotage établissement.
 * Le tenant est exclusivement issu de {@link TenantContext} (JWT vérifié côté serveur).
 */
@Service
@PreAuthorize(AuditRoles.TENANT_AUDIT_READERS)
public class TenantAuditQueryService {

    private final AuditLogRepository auditLogRepository;
    private final EntityManager entityManager;
    private final SubscriptionGatePort subscriptionGatePort;

    public TenantAuditQueryService(
            AuditLogRepository auditLogRepository,
            EntityManager entityManager,
            SubscriptionGatePort subscriptionGatePort) {
        this.auditLogRepository = auditLogRepository;
        this.entityManager = entityManager;
        this.subscriptionGatePort = subscriptionGatePort;
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> searchAuditLogs(AuditSearchFilter filter, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGatePort.assertReadAllowed();
        return auditLogRepository.findAll(buildSpecification(filter), pageable);
    }

    @Transactional(readOnly = true)
    public AuditLog getAuditLog(UUID auditLogId) {
        Objects.requireNonNull(auditLogId, "L'identifiant d'entrée d'audit est obligatoire.");
        TenantContext.requireCurrent();
        subscriptionGatePort.assertReadAllowed();
        return auditLogRepository.findById(auditLogId)
                .orElseThrow(() -> new AuditLogNotFoundException("Entrée du journal d'audit introuvable."));
    }

    @Transactional(readOnly = true)
    public AuditSummarySnapshot getAuditSummary(Instant from, Instant to) {
        TenantContext.requireCurrent();
        subscriptionGatePort.assertReadAllowed();
        AuditQueryCriteria.validateDateRange(from, to);

        Map<String, Long> byCategory = countGrouped("category", from, to);
        Map<String, Long> bySeverity = countGrouped("severity", from, to);
        Map<String, Long> byOutcome = countGrouped("outcome", from, to);
        long total = byOutcome.values().stream().mapToLong(Long::longValue).sum();

        return new AuditSummarySnapshot(from, to, total, byCategory, bySeverity, byOutcome);
    }

    static Specification<AuditLog> buildSpecification(AuditSearchFilter filter) {
        AuditSearchFilter effective = filter != null
                ? filter
                : new AuditSearchFilter(null, null, null, null, null, null, null, null, null, null, null, null);
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

    private Map<String, Long> countGrouped(String fieldName, Instant from, Instant to) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<AuditLog> root = cq.from(AuditLog.class);

        List<Predicate> predicates = new ArrayList<>();
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
        }

        cq.select(cb.tuple(root.get(fieldName), cb.count(root)));
        if (!predicates.isEmpty()) {
            cq.where(predicates.toArray(Predicate[]::new));
        }
        cq.groupBy(root.get(fieldName));

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Tuple tuple : entityManager.createQuery(cq).getResultList()) {
            Object key = tuple.get(0);
            Long count = tuple.get(1, Long.class);
            if (key != null && count != null) {
                counts.put(key.toString(), count);
            }
        }
        return Collections.unmodifiableMap(counts);
    }
}
