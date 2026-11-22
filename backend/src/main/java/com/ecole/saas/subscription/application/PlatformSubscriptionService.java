package com.ecole.saas.subscription.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditActorType;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.audit.AuditSeverity;
import com.ecole.saas.audit.PlatformAuditRecordCommand;
import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.school.SchoolDirectory.SchoolSummary;
import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import com.ecole.saas.subscription.SubscriptionSnapshot;
import com.ecole.saas.subscription.domain.ActiveSubscriptionAlreadyExistsException;
import com.ecole.saas.subscription.domain.BillingCycle;
import com.ecole.saas.subscription.domain.Subscription;
import com.ecole.saas.subscription.domain.SubscriptionEvent;
import com.ecole.saas.subscription.domain.SubscriptionEventType;
import com.ecole.saas.subscription.domain.SubscriptionNotFoundException;
import com.ecole.saas.subscription.domain.SubscriptionPlan;
import com.ecole.saas.subscription.domain.SubscriptionPlanNotFoundException;
import com.ecole.saas.subscription.domain.SubscriptionPolicy;
import com.ecole.saas.subscription.domain.SubscriptionStatus;
import com.ecole.saas.subscription.infrastructure.SubscriptionEventRepository;
import com.ecole.saas.subscription.infrastructure.SubscriptionPlanRepository;
import com.ecole.saas.subscription.infrastructure.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service applicatif d'administration plateforme des abonnements d'établissements ({@code ADMIN_SAAS}).
 * Positionne systématiquement le {@link TenantContext} avant l'ouverture de chaque transaction afin
 * que Hibernate {@code @TenantId} et la RLS PostgreSQL soient appliqués de bout en bout.
 */
@Service
@PreAuthorize("hasRole('ADMIN_SAAS')")
public class PlatformSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(PlatformSubscriptionService.class);

    private final SchoolDirectory schoolDirectory;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEventRepository eventRepository;
    private final AuditLogPort auditLogPort;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final Clock clock;

    public PlatformSubscriptionService(
            SchoolDirectory schoolDirectory,
            SubscriptionPlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionEventRepository eventRepository,
            AuditLogPort auditLogPort,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.schoolDirectory = schoolDirectory;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.eventRepository = eventRepository;
        this.auditLogPort = auditLogPort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
        this.clock = clock;
    }

    public SubscriptionSnapshot startTrial(
            UUID schoolId,
            UUID planId,
            BillingCycle targetBillingCycle,
            Integer trialDaysOverride,
            UUID actorId) {
        SchoolSummary school = requireActiveSchool(schoolId);
        Instant now = clock.instant();
        SubscriptionSnapshot snapshot = executeInSchoolTenant(school.id(), false, () -> {
            ensureNoOpenSubscription();
            SubscriptionPlan plan = requirePlan(planId);
            int effectiveTrialDays = trialDaysOverride != null ? trialDaysOverride : plan.getTrialDays();
            Instant trialEndsAt = SubscriptionPolicy.computeTrialEnd(now, effectiveTrialDays);
            BillingCycle cycle = targetBillingCycle != null ? targetBillingCycle : BillingCycle.MONTHLY;

            Subscription subscription = Subscription.startTrial(plan, cycle, now, trialEndsAt);
            Subscription saved = subscriptionRepository.save(subscription);
            SubscriptionSupport.recordEvent(
                    eventRepository,
                    saved,
                    SubscriptionEventType.TRIAL_STARTED,
                    null,
                    "Démarrage de la période d'essai (" + effectiveTrialDays + " jours).",
                    actorId,
                    now);
            auditTenantSubscription(
                    AuditAction.SUBSCRIPTION_TRIAL_STARTED,
                    AuditSeverity.INFO,
                    saved.getId(),
                    "Démarrage d'une période d'essai d'abonnement.",
                    actorId,
                    Map.of("planCode", plan.getCode(), "trialDays", effectiveTrialDays));
            log.info(
                    "Période d'essai démarrée (schoolId={}, subscriptionId={}, planCode={})",
                    school.id(),
                    saved.getId(),
                    plan.getCode());
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
        auditPlatformSubscription(
                AuditAction.SUBSCRIPTION_TRIAL_STARTED,
                AuditSeverity.INFO,
                school.id(),
                snapshot.subscriptionId(),
                "Démarrage d'une période d'essai d'abonnement.",
                null,
                actorId,
                Map.of("planCode", snapshot.planCode(), "status", snapshot.status()));
        return snapshot;
    }

    public SubscriptionSnapshot createActiveSubscription(
            UUID schoolId,
            UUID planId,
            BillingCycle billingCycle,
            Instant startsAt,
            Instant endsAt,
            UUID actorId) {
        SchoolSummary school = requireActiveSchool(schoolId);
        Objects.requireNonNull(billingCycle, "Le cycle de facturation est obligatoire.");
        Instant now = clock.instant();
        Instant effectiveStartsAt = startsAt != null ? startsAt : now;
        Instant effectiveEndsAt = endsAt != null
                ? endsAt
                : SubscriptionPolicy.computePeriodEnd(effectiveStartsAt, billingCycle);

        SubscriptionSnapshot snapshot = executeInSchoolTenant(school.id(), false, () -> {
            ensureNoOpenSubscription();
            SubscriptionPlan plan = requirePlan(planId);
            Subscription subscription = Subscription.createActive(plan, billingCycle, effectiveStartsAt, effectiveEndsAt);
            Subscription saved = subscriptionRepository.save(subscription);
            SubscriptionSupport.recordEvent(
                    eventRepository,
                    saved,
                    SubscriptionEventType.ACTIVATED,
                    null,
                    "Création d'un abonnement actif.",
                    actorId,
                    now);
            auditTenantSubscription(
                    AuditAction.SUBSCRIPTION_ACTIVATED,
                    AuditSeverity.INFO,
                    saved.getId(),
                    "Création d'un abonnement actif.",
                    actorId,
                    Map.of("planCode", plan.getCode(), "billingCycle", billingCycle.name(), "amountXof", saved.getAmountXof()));
            log.info(
                    "Abonnement actif créé (schoolId={}, subscriptionId={}, planCode={})",
                    school.id(),
                    saved.getId(),
                    plan.getCode());
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
        auditPlatformSubscription(
                AuditAction.SUBSCRIPTION_ACTIVATED,
                AuditSeverity.INFO,
                school.id(),
                snapshot.subscriptionId(),
                "Création d'un abonnement actif.",
                null,
                actorId,
                Map.of("planCode", snapshot.planCode(), "amountXof", snapshot.amountXof()));
        return snapshot;
    }

    public SubscriptionSnapshot getCurrentSubscription(UUID schoolId) {
        SchoolSummary school = requireExistingSchool(schoolId);
        Instant now = clock.instant();
        return executeInSchoolTenant(school.id(), true, () -> {
            Subscription current = SubscriptionSupport.findCurrentSubscription(subscriptionRepository)
                    .orElseThrow(() -> new SubscriptionNotFoundException(
                            "Aucun abonnement n'est associé à cet établissement."));
            return SubscriptionSupport.toSnapshot(current, planRepository, now);
        });
    }

    public Page<SubscriptionSnapshot> listSubscriptions(UUID schoolId, Pageable pageable) {
        SchoolSummary school = requireExistingSchool(schoolId);
        Instant now = clock.instant();
        return executeInSchoolTenant(school.id(), true, () -> SubscriptionSupport.toSnapshotPage(
                subscriptionRepository.findAll(pageable),
                planRepository,
                now));
    }

    public Page<SubscriptionEvent> listEvents(UUID schoolId, UUID subscriptionId, Pageable pageable) {
        SchoolSummary school = requireExistingSchool(schoolId);
        return executeInSchoolTenant(school.id(), true, () -> {
            requireSubscriptionReadOnly(subscriptionId);
            return eventRepository.findBySubscriptionIdOrderBySequenceNoDesc(subscriptionId, pageable);
        });
    }

    public SubscriptionSnapshot activateSubscription(
            UUID schoolId,
            UUID subscriptionId,
            BillingCycle billingCycle,
            Instant startsAt,
            Instant endsAt,
            String reason,
            UUID actorId) {
        SchoolSummary school = requireExistingSchool(schoolId);
        Instant now = clock.instant();
        SubscriptionSnapshot snapshot = executeInSchoolTenant(school.id(), false, () -> {
            Subscription subscription = requireSubscription(subscriptionId);
            if (subscription.getStatus() == SubscriptionStatus.SUSPENDED) {
                ensureNoOpenSubscription();
            }
            SubscriptionPlan plan = requirePlan(subscription.getPlanId());
            BillingCycle effectiveCycle = billingCycle != null ? billingCycle : subscription.getBillingCycle();
            Instant effectiveStartsAt = startsAt != null ? startsAt : now;
            Instant effectiveEndsAt = endsAt != null
                    ? endsAt
                    : SubscriptionPolicy.computePeriodEnd(effectiveStartsAt, effectiveCycle);
            long amountXof = plan.priceFor(effectiveCycle);

            SubscriptionStatus previousStatus = subscription.getStatus();
            SubscriptionEventType eventType = subscription.activate(
                    effectiveCycle,
                    amountXof,
                    effectiveStartsAt,
                    effectiveEndsAt,
                    reason);
            Subscription saved = subscriptionRepository.save(subscription);
            SubscriptionSupport.recordEvent(
                    eventRepository,
                    saved,
                    eventType,
                    previousStatus,
                    saved.getStatusReason(),
                    actorId,
                    now);
            AuditAction auditAction = eventType == SubscriptionEventType.REACTIVATED
                    ? AuditAction.SUBSCRIPTION_REACTIVATED
                    : AuditAction.SUBSCRIPTION_ACTIVATED;
            auditTenantSubscription(
                    auditAction,
                    AuditSeverity.INFO,
                    saved.getId(),
                    eventType == SubscriptionEventType.REACTIVATED
                            ? "Réactivation de l'abonnement de l'établissement."
                            : "Activation de l'abonnement de l'établissement.",
                    actorId,
                    Map.of("planCode", plan.getCode(), "previousStatus", previousStatus.name(), "amountXof", amountXof));
            log.info(
                    "Abonnement activé (schoolId={}, subscriptionId={}, eventType={})",
                    school.id(),
                    saved.getId(),
                    eventType);
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
        auditPlatformSubscription(
                AuditAction.SUBSCRIPTION_ACTIVATED,
                AuditSeverity.INFO,
                school.id(),
                snapshot.subscriptionId(),
                "Activation/réactivation de l'abonnement d'un établissement.",
                reason,
                actorId,
                Map.of("planCode", snapshot.planCode(), "amountXof", snapshot.amountXof()));
        return snapshot;
    }

    public SubscriptionSnapshot renewSubscription(
            UUID schoolId,
            UUID subscriptionId,
            BillingCycle billingCycle,
            Instant endsAt,
            String reason,
            UUID actorId) {
        SchoolSummary school = requireExistingSchool(schoolId);
        Instant now = clock.instant();
        SubscriptionSnapshot snapshot = executeInSchoolTenant(school.id(), false, () -> {
            Subscription subscription = requireSubscription(subscriptionId);
            if (subscription.getStatus() == SubscriptionStatus.SUSPENDED) {
                ensureNoOpenSubscription();
            }
            SubscriptionPlan plan = requirePlan(subscription.getPlanId());
            BillingCycle effectiveCycle = billingCycle != null ? billingCycle : subscription.getBillingCycle();
            Instant anchor = subscription.getEndsAt().isAfter(now) ? subscription.getEndsAt() : now;
            Instant effectiveEndsAt = endsAt != null
                    ? endsAt
                    : SubscriptionPolicy.computePeriodEnd(anchor, effectiveCycle);
            long amountXof = plan.priceFor(effectiveCycle);

            SubscriptionStatus previousStatus = subscription.getStatus();
            subscription.renew(effectiveCycle, amountXof, effectiveEndsAt, now, reason);
            Subscription saved = subscriptionRepository.save(subscription);
            SubscriptionSupport.recordEvent(
                    eventRepository,
                    saved,
                    SubscriptionEventType.RENEWED,
                    previousStatus,
                    saved.getStatusReason(),
                    actorId,
                    now);
            auditTenantSubscription(
                    AuditAction.SUBSCRIPTION_RENEWED,
                    AuditSeverity.INFO,
                    saved.getId(),
                    "Renouvellement de l'abonnement de l'établissement.",
                    actorId,
                    Map.of("planCode", plan.getCode(), "billingCycle", effectiveCycle.name(), "amountXof", amountXof));
            log.info("Abonnement renouvelé (schoolId={}, subscriptionId={})", school.id(), saved.getId());
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
        auditPlatformSubscription(
                AuditAction.SUBSCRIPTION_RENEWED,
                AuditSeverity.INFO,
                school.id(),
                snapshot.subscriptionId(),
                "Renouvellement d'abonnement établissement.",
                reason,
                actorId,
                Map.of("planCode", snapshot.planCode(), "amountXof", snapshot.amountXof()));
        return snapshot;
    }

    public SubscriptionSnapshot changePlan(
            UUID schoolId,
            UUID subscriptionId,
            UUID newPlanId,
            BillingCycle billingCycle,
            boolean resetExtraQuotas,
            String reason,
            UUID actorId) {
        SchoolSummary school = requireExistingSchool(schoolId);
        Instant now = clock.instant();
        SubscriptionSnapshot snapshot = executeInSchoolTenant(school.id(), false, () -> {
            Subscription subscription = requireSubscription(subscriptionId);
            SubscriptionPlan newPlan = requirePlan(newPlanId);
            BillingCycle effectiveCycle = billingCycle != null ? billingCycle : subscription.getBillingCycle();

            SubscriptionStatus previousStatus = subscription.getStatus();
            subscription.changePlan(newPlan, effectiveCycle, resetExtraQuotas, reason);
            Subscription saved = subscriptionRepository.save(subscription);
            SubscriptionSupport.recordEvent(
                    eventRepository,
                    saved,
                    SubscriptionEventType.PLAN_CHANGED,
                    previousStatus,
                    saved.getStatusReason(),
                    actorId,
                    now);
            auditTenantSubscription(
                    AuditAction.SUBSCRIPTION_PLAN_CHANGED,
                    AuditSeverity.INFO,
                    saved.getId(),
                    "Changement de forfait d'abonnement.",
                    actorId,
                    Map.of("newPlanCode", newPlan.getCode(), "resetExtraQuotas", resetExtraQuotas));
            log.info(
                    "Forfait d'abonnement modifié (schoolId={}, subscriptionId={}, newPlanCode={})",
                    school.id(),
                    saved.getId(),
                    newPlan.getCode());
            return SubscriptionSupport.toSnapshot(saved, newPlan, now);
        });
        auditPlatformSubscription(
                AuditAction.SUBSCRIPTION_PLAN_CHANGED,
                AuditSeverity.INFO,
                school.id(),
                snapshot.subscriptionId(),
                "Changement de forfait d'abonnement d'un établissement.",
                reason,
                actorId,
                Map.of("newPlanCode", snapshot.planCode()));
        return snapshot;
    }

    public SubscriptionSnapshot adjustExtraQuotas(
            UUID schoolId,
            UUID subscriptionId,
            int extraStudents,
            int extraUsers,
            int extraStorageMb,
            String reason,
            UUID actorId) {
        SchoolSummary school = requireExistingSchool(schoolId);
        Instant now = clock.instant();
        SubscriptionSnapshot snapshot = executeInSchoolTenant(school.id(), false, () -> {
            Subscription subscription = requireSubscription(subscriptionId);
            SubscriptionPlan plan = requirePlan(subscription.getPlanId());

            SubscriptionStatus previousStatus = subscription.getStatus();
            subscription.adjustExtraQuotas(extraStudents, extraUsers, extraStorageMb, reason);
            Subscription saved = subscriptionRepository.save(subscription);
            SubscriptionSupport.recordEvent(
                    eventRepository,
                    saved,
                    SubscriptionEventType.QUOTAS_ADJUSTED,
                    previousStatus,
                    saved.getStatusReason(),
                    actorId,
                    now);
            auditTenantSubscription(
                    AuditAction.SUBSCRIPTION_QUOTAS_ADJUSTED,
                    AuditSeverity.INFO,
                    saved.getId(),
                    "Ajustement des extensions de quotas de l'abonnement.",
                    actorId,
                    Map.of(
                            "extraStudents", extraStudents,
                            "extraUsers", extraUsers,
                            "extraStorageMb", extraStorageMb));
            log.info("Quotas effectifs ajustés (schoolId={}, subscriptionId={})", school.id(), saved.getId());
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
        auditPlatformSubscription(
                AuditAction.SUBSCRIPTION_QUOTAS_ADJUSTED,
                AuditSeverity.INFO,
                school.id(),
                snapshot.subscriptionId(),
                "Ajustement des extensions de quotas d'un établissement.",
                reason,
                actorId,
                Map.of("extraStudents", extraStudents, "extraUsers", extraUsers, "extraStorageMb", extraStorageMb));
        return snapshot;
    }

    public SubscriptionSnapshot markPastDue(
            UUID schoolId,
            UUID subscriptionId,
            Instant gracePeriodEndsAt,
            String reason,
            UUID actorId) {
        SchoolSummary school = requireExistingSchool(schoolId);
        Instant now = clock.instant();
        SubscriptionSnapshot snapshot = executeInSchoolTenant(school.id(), false, () -> {
            Subscription subscription = requireSubscription(subscriptionId);
            SubscriptionPlan plan = requirePlan(subscription.getPlanId());

            SubscriptionStatus previousStatus = subscription.getStatus();
            subscription.markPastDue(gracePeriodEndsAt, now, reason);
            Subscription saved = subscriptionRepository.save(subscription);
            SubscriptionSupport.recordEvent(
                    eventRepository,
                    saved,
                    SubscriptionEventType.MARKED_PAST_DUE,
                    previousStatus,
                    saved.getStatusReason(),
                    actorId,
                    now);
            auditTenantSubscription(
                    AuditAction.SUBSCRIPTION_MARKED_PAST_DUE,
                    AuditSeverity.WARNING,
                    saved.getId(),
                    "Abonnement marqué en impayé avec période de grâce.",
                    actorId,
                    Map.of("previousStatus", previousStatus.name()));
            log.info("Abonnement passé en impayé (schoolId={}, subscriptionId={})", school.id(), saved.getId());
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
        auditPlatformSubscription(
                AuditAction.SUBSCRIPTION_MARKED_PAST_DUE,
                AuditSeverity.WARNING,
                school.id(),
                snapshot.subscriptionId(),
                "Passage d'un abonnement établissement en statut impayé.",
                reason,
                actorId,
                Map.of("status", snapshot.status()));
        return snapshot;
    }

    public SubscriptionSnapshot suspendSubscription(
            UUID schoolId,
            UUID subscriptionId,
            String reason,
            UUID actorId) {
        SchoolSummary school = requireExistingSchool(schoolId);
        Instant now = clock.instant();
        SubscriptionSnapshot snapshot = executeInSchoolTenant(school.id(), false, () -> {
            Subscription subscription = requireSubscription(subscriptionId);
            SubscriptionPlan plan = requirePlan(subscription.getPlanId());

            SubscriptionStatus previousStatus = subscription.getStatus();
            subscription.suspend(now, reason);
            Subscription saved = subscriptionRepository.save(subscription);
            SubscriptionSupport.recordEvent(
                    eventRepository,
                    saved,
                    SubscriptionEventType.SUSPENDED,
                    previousStatus,
                    saved.getStatusReason(),
                    actorId,
                    now);
            auditTenantSubscription(
                    AuditAction.SUBSCRIPTION_SUSPENDED,
                    AuditSeverity.CRITICAL,
                    saved.getId(),
                    "Suspension de l'abonnement de l'établissement.",
                    actorId,
                    Map.of("previousStatus", previousStatus.name()));
            log.info("Abonnement suspendu (schoolId={}, subscriptionId={})", school.id(), saved.getId());
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
        auditPlatformSubscription(
                AuditAction.SUBSCRIPTION_SUSPENDED,
                AuditSeverity.CRITICAL,
                school.id(),
                snapshot.subscriptionId(),
                "Suspension de l'abonnement d'un établissement.",
                reason,
                actorId,
                Map.of("status", snapshot.status()));
        return snapshot;
    }

    public SubscriptionSnapshot cancelSubscription(
            UUID schoolId,
            UUID subscriptionId,
            String reason,
            UUID actorId) {
        SchoolSummary school = requireExistingSchool(schoolId);
        Instant now = clock.instant();
        SubscriptionSnapshot snapshot = executeInSchoolTenant(school.id(), false, () -> {
            Subscription subscription = requireSubscription(subscriptionId);
            SubscriptionPlan plan = requirePlan(subscription.getPlanId());

            SubscriptionStatus previousStatus = subscription.getStatus();
            subscription.cancel(now, reason);
            Subscription saved = subscriptionRepository.save(subscription);
            SubscriptionSupport.recordEvent(
                    eventRepository,
                    saved,
                    SubscriptionEventType.CANCELLED,
                    previousStatus,
                    saved.getStatusReason(),
                    actorId,
                    now);
            auditTenantSubscription(
                    AuditAction.SUBSCRIPTION_CANCELLED,
                    AuditSeverity.CRITICAL,
                    saved.getId(),
                    "Annulation définitive de l'abonnement de l'établissement.",
                    actorId,
                    Map.of("previousStatus", previousStatus.name()));
            log.info("Abonnement annulé (schoolId={}, subscriptionId={})", school.id(), saved.getId());
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
        auditPlatformSubscription(
                AuditAction.SUBSCRIPTION_CANCELLED,
                AuditSeverity.CRITICAL,
                school.id(),
                snapshot.subscriptionId(),
                "Annulation définitive de l'abonnement d'un établissement.",
                reason,
                actorId,
                Map.of("status", snapshot.status()));
        return snapshot;
    }

    private SchoolSummary requireExistingSchool(UUID schoolId) {
        return schoolDirectory.findSummaryById(schoolId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCategory.NOT_FOUND,
                        "school-not-found",
                        "Établissement introuvable",
                        "L'établissement demandé est introuvable."));
    }

    private SchoolSummary requireActiveSchool(UUID schoolId) {
        SchoolSummary school = requireExistingSchool(schoolId);
        if (!school.active()) {
            throw new BusinessException(
                    ErrorCategory.CONFLICT,
                    "school-inactive",
                    "Établissement inactif",
                    "Impossible de créer un abonnement pour un établissement inactif.");
        }
        return school;
    }

    private void ensureNoOpenSubscription() {
        if (subscriptionRepository.existsByStatusIn(SubscriptionStatus.OPEN_STATUSES)) {
            throw new ActiveSubscriptionAlreadyExistsException(
                    "Cet établissement possède déjà un abonnement ouvert (essai, actif ou impayé).");
        }
    }

    private SubscriptionPlan requirePlan(UUID planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new SubscriptionPlanNotFoundException("Forfait introuvable."));
    }

    private Subscription requireSubscription(UUID subscriptionId) {
        return subscriptionRepository.findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException("Abonnement introuvable."));
    }

    private Subscription requireSubscriptionReadOnly(UUID subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException("Abonnement introuvable."));
    }

    private void auditTenantSubscription(
            AuditAction action,
            AuditSeverity severity,
            UUID subscriptionId,
            String summary,
            UUID actorId,
            Map<String, Object> metadata) {
        auditLogPort.recordTenantEvent(new AuditRecordCommand(
                AuditCategory.SUBSCRIPTION,
                action,
                AuditOutcome.SUCCESS,
                severity,
                "SUBSCRIPTION",
                subscriptionId,
                summary,
                actorId,
                AuditActorType.PLATFORM_ADMIN,
                "ADMIN_SAAS",
                metadata));
    }

    private void auditPlatformSubscription(
            AuditAction action,
            AuditSeverity severity,
            UUID schoolId,
            UUID subscriptionId,
            String summary,
            String reason,
            UUID actorId,
            Map<String, Object> metadata) {
        auditLogPort.recordPlatformEvent(new PlatformAuditRecordCommand(
                AuditCategory.SUBSCRIPTION,
                action,
                AuditOutcome.SUCCESS,
                severity,
                schoolId,
                "SUBSCRIPTION",
                subscriptionId,
                summary,
                reason,
                actorId,
                AuditActorType.PLATFORM_ADMIN,
                "ADMIN_SAAS",
                metadata));
    }

    private <T> T executeInSchoolTenant(UUID schoolId, boolean readOnly, Supplier<T> action) {
        TransactionTemplate template = readOnly ? readOnlyTransactionTemplate : transactionTemplate;
        return TenantContext.executeWithTenant(
                TenantId.of(schoolId),
                () -> template.execute(status -> action.get()));
    }
}
