package com.ecole.saas.subscription.application;

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
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final Clock clock;

    public PlatformSubscriptionService(
            SchoolDirectory schoolDirectory,
            SubscriptionPlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionEventRepository eventRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.schoolDirectory = schoolDirectory;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.eventRepository = eventRepository;
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
        return executeInSchoolTenant(school.id(), false, () -> {
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
            log.info(
                    "Période d'essai démarrée (schoolId={}, subscriptionId={}, planCode={})",
                    school.id(),
                    saved.getId(),
                    plan.getCode());
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
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

        return executeInSchoolTenant(school.id(), false, () -> {
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
            log.info(
                    "Abonnement actif créé (schoolId={}, subscriptionId={}, planCode={})",
                    school.id(),
                    saved.getId(),
                    plan.getCode());
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
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
            requireSubscription(subscriptionId);
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
        return executeInSchoolTenant(school.id(), false, () -> {
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
            log.info(
                    "Abonnement activé (schoolId={}, subscriptionId={}, eventType={})",
                    school.id(),
                    saved.getId(),
                    eventType);
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
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
        return executeInSchoolTenant(school.id(), false, () -> {
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
            log.info("Abonnement renouvelé (schoolId={}, subscriptionId={})", school.id(), saved.getId());
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
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
        return executeInSchoolTenant(school.id(), false, () -> {
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
            log.info(
                    "Forfait d'abonnement modifié (schoolId={}, subscriptionId={}, newPlanCode={})",
                    school.id(),
                    saved.getId(),
                    newPlan.getCode());
            return SubscriptionSupport.toSnapshot(saved, newPlan, now);
        });
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
        return executeInSchoolTenant(school.id(), false, () -> {
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
            log.info("Quotas effectifs ajustés (schoolId={}, subscriptionId={})", school.id(), saved.getId());
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
    }

    public SubscriptionSnapshot markPastDue(
            UUID schoolId,
            UUID subscriptionId,
            Instant gracePeriodEndsAt,
            String reason,
            UUID actorId) {
        SchoolSummary school = requireExistingSchool(schoolId);
        Instant now = clock.instant();
        return executeInSchoolTenant(school.id(), false, () -> {
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
            log.info("Abonnement passé en impayé (schoolId={}, subscriptionId={})", school.id(), saved.getId());
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
    }

    public SubscriptionSnapshot suspendSubscription(
            UUID schoolId,
            UUID subscriptionId,
            String reason,
            UUID actorId) {
        SchoolSummary school = requireExistingSchool(schoolId);
        Instant now = clock.instant();
        return executeInSchoolTenant(school.id(), false, () -> {
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
            log.info("Abonnement suspendu (schoolId={}, subscriptionId={})", school.id(), saved.getId());
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
    }

    public SubscriptionSnapshot cancelSubscription(
            UUID schoolId,
            UUID subscriptionId,
            String reason,
            UUID actorId) {
        SchoolSummary school = requireExistingSchool(schoolId);
        Instant now = clock.instant();
        return executeInSchoolTenant(school.id(), false, () -> {
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
            log.info("Abonnement annulé (schoolId={}, subscriptionId={})", school.id(), saved.getId());
            return SubscriptionSupport.toSnapshot(saved, plan, now);
        });
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
        return subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException("Abonnement introuvable."));
    }

    private <T> T executeInSchoolTenant(UUID schoolId, boolean readOnly, Supplier<T> action) {
        TransactionTemplate template = readOnly ? readOnlyTransactionTemplate : transactionTemplate;
        return TenantContext.executeWithTenant(
                TenantId.of(schoolId),
                () -> template.execute(status -> action.get()));
    }
}
