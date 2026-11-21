package com.ecole.saas.audit.application;

import com.ecole.saas.audit.AuditEventSummary;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.audit.PlatformAuditRecordCommand;
import com.ecole.saas.audit.application.AuditActorResolver.ResolvedActor;
import com.ecole.saas.audit.domain.AuditLog;
import com.ecole.saas.audit.domain.PlatformAuditLog;
import com.ecole.saas.audit.infrastructure.AuditLogRepository;
import com.ecole.saas.audit.infrastructure.PlatformAuditLogRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Implémentation applicative de la frontière publique {@link AuditLogPort}.
 * Garantit l'enregistrement strictement en ajout seul, l'assainissement systématique
 * des métadonnées et la séparation entre journal établissement (RLS tenant) et journal plateforme.
 */
@Service
public class AuditLogService implements AuditLogPort {

    private final AuditLogRepository auditLogRepository;
    private final PlatformAuditLogRepository platformAuditLogRepository;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final Clock clock;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            PlatformAuditLogRepository platformAuditLogRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.platformAuditLogRepository = platformAuditLogRepository;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Override
    @Transactional
    public void recordTenantEvent(AuditRecordCommand command) {
        Objects.requireNonNull(command, "La commande d'audit est obligatoire.");
        TenantContext.requireCurrent();
        persistTenantEvent(command);
    }

    @Override
    public void recordTenantEventForSchool(UUID schoolId, AuditRecordCommand command) {
        Objects.requireNonNull(schoolId, "L'identifiant d'établissement est obligatoire.");
        Objects.requireNonNull(command, "La commande d'audit est obligatoire.");
        TenantContext.runWithTenant(
                TenantId.of(schoolId),
                () -> requiresNewTransactionTemplate.executeWithoutResult(status -> persistTenantEvent(command)));
    }

    @Override
    @Transactional
    public void recordPlatformEvent(PlatformAuditRecordCommand command) {
        Objects.requireNonNull(command, "La commande d'audit plateforme est obligatoire.");
        if (TenantContext.getCurrent().isPresent()) {
            throw new IllegalStateException(
                    "Impossible d'enregistrer un événement d'audit plateforme lorsqu'un contexte établissement est actif.");
        }
        ResolvedActor actor = AuditActorResolver.resolve(
                command.actorId(), command.actorType(), command.actorRoles());
        PlatformAuditLog entry = new PlatformAuditLog(
                command.category(),
                command.action(),
                command.outcome(),
                command.severity(),
                actor.actorType(),
                actor.actorId(),
                actor.actorRoles(),
                command.targetSchoolId(),
                command.resourceType(),
                command.resourceId(),
                command.summary(),
                command.reason(),
                command.metadata(),
                actor.correlationId(),
                clock.instant());
        platformAuditLogRepository.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuditEventSummary> findTenantEventById(UUID eventId) {
        Objects.requireNonNull(eventId, "L'identifiant d'événement d'audit est obligatoire.");
        TenantContext.requireCurrent();
        return auditLogRepository.findById(eventId).map(AuditLogService::toSummary);
    }

    private void persistTenantEvent(AuditRecordCommand command) {
        ResolvedActor actor = AuditActorResolver.resolve(
                command.actorId(), command.actorType(), command.actorRoles());
        AuditLog entry = new AuditLog(
                command.category(),
                command.action(),
                command.outcome(),
                command.severity(),
                actor.actorType(),
                actor.actorId(),
                actor.actorRoles(),
                command.resourceType(),
                command.resourceId(),
                command.summary(),
                command.metadata(),
                actor.correlationId(),
                clock.instant());
        auditLogRepository.save(entry);
    }

    private static AuditEventSummary toSummary(AuditLog log) {
        return new AuditEventSummary(
                log.getId(),
                log.getCategory(),
                log.getAction(),
                log.getOutcome(),
                log.getSeverity(),
                log.getActorType(),
                log.getActorId(),
                log.getActorRoles(),
                log.getResourceType(),
                log.getResourceId(),
                log.getSummary(),
                log.parsedMetadata(),
                log.getCorrelationId(),
                log.getOccurredAt());
    }
}
