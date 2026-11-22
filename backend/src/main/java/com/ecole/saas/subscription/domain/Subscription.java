package com.ecole.saas.subscription.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import com.ecole.saas.subscription.EffectiveAccessState;
import com.ecole.saas.subscription.EffectiveQuotas;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription extends TenantScopedEntity {

    private static final int MAX_REASON_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    @Column(name = "amount_xof", nullable = false)
    private long amountXof;

    @Column(name = "max_students", nullable = false)
    private int maxStudents;

    @Column(name = "max_users", nullable = false)
    private int maxUsers;

    @Column(name = "max_storage_mb", nullable = false)
    private int maxStorageMb;

    @Column(name = "extra_students", nullable = false)
    private int extraStudents;

    @Column(name = "extra_users", nullable = false)
    private int extraUsers;

    @Column(name = "extra_storage_mb", nullable = false)
    private int extraStorageMb;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "grace_period_ends_at")
    private Instant gracePeriodEndsAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Subscription() {
    }

    public Subscription(
            UUID planId,
            SubscriptionStatus status,
            BillingCycle billingCycle,
            long amountXof,
            int maxStudents,
            int maxUsers,
            int maxStorageMb,
            Instant startsAt,
            Instant endsAt) {
        this.planId = Objects.requireNonNull(planId, "L'identifiant du forfait est obligatoire.");
        this.status = Objects.requireNonNull(status, "Le statut de l'abonnement est obligatoire.");
        this.billingCycle = Objects.requireNonNull(billingCycle, "Le cycle de facturation est obligatoire.");
        if (amountXof < 0) {
            throw new IllegalArgumentException("Le montant en FCFA doit être positif ou nul.");
        }
        if (maxStudents <= 0 || maxUsers <= 0 || maxStorageMb <= 0) {
            throw new IllegalArgumentException("Les quotas d'abonnement doivent être strictement positifs.");
        }
        this.startsAt = Objects.requireNonNull(startsAt, "La date de début est obligatoire.");
        this.endsAt = Objects.requireNonNull(endsAt, "La date de fin est obligatoire.");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");
        }
        this.amountXof = amountXof;
        this.maxStudents = maxStudents;
        this.maxUsers = maxUsers;
        this.maxStorageMb = maxStorageMb;
        this.extraStudents = 0;
        this.extraUsers = 0;
        this.extraStorageMb = 0;
        this.trialEndsAt = status == SubscriptionStatus.TRIAL ? endsAt : null;
    }

    public static Subscription startTrial(
            SubscriptionPlan plan,
            BillingCycle targetBillingCycle,
            Instant startsAt,
            Instant trialEndsAt) {
        Objects.requireNonNull(plan, "Le forfait est obligatoire.");
        if (!plan.isActive()) {
            throw new SubscriptionPlanInactiveException("Impossible de démarrer un essai avec un forfait inactif.");
        }
        if (plan.getId() == null) {
            throw new IllegalArgumentException("Le forfait doit être persisté avant la création d'un abonnement.");
        }
        return new Subscription(
                plan.getId(),
                SubscriptionStatus.TRIAL,
                targetBillingCycle,
                0L,
                plan.getMaxStudents(),
                plan.getMaxUsers(),
                plan.getMaxStorageMb(),
                startsAt,
                trialEndsAt);
    }

    public static Subscription createActive(
            SubscriptionPlan plan,
            BillingCycle billingCycle,
            Instant startsAt,
            Instant endsAt) {
        Objects.requireNonNull(plan, "Le forfait est obligatoire.");
        if (!plan.isActive()) {
            throw new SubscriptionPlanInactiveException("Impossible de souscrire à un forfait inactif.");
        }
        if (plan.getId() == null) {
            throw new IllegalArgumentException("Le forfait doit être persisté avant la création d'un abonnement.");
        }
        return new Subscription(
                plan.getId(),
                SubscriptionStatus.ACTIVE,
                billingCycle,
                plan.priceFor(billingCycle),
                plan.getMaxStudents(),
                plan.getMaxUsers(),
                plan.getMaxStorageMb(),
                startsAt,
                endsAt);
    }

    public SubscriptionEventType activate(
            BillingCycle billingCycle,
            long amountXof,
            Instant newStartsAt,
            Instant newEndsAt,
            String reason) {
        if (status != SubscriptionStatus.TRIAL
                && status != SubscriptionStatus.PAST_DUE
                && status != SubscriptionStatus.SUSPENDED) {
            throw new InvalidSubscriptionTransitionException(
                    "Impossible d'activer un abonnement au statut " + status + ".");
        }
        Objects.requireNonNull(billingCycle, "Le cycle de facturation est obligatoire.");
        Objects.requireNonNull(newStartsAt, "La date de début est obligatoire.");
        Objects.requireNonNull(newEndsAt, "La date de fin est obligatoire.");
        if (amountXof < 0) {
            throw new IllegalArgumentException("Le montant en FCFA doit être positif ou nul.");
        }
        if (!newEndsAt.isAfter(newStartsAt)) {
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");
        }
        SubscriptionStatus previous = this.status;
        this.status = SubscriptionStatus.ACTIVE;
        this.billingCycle = billingCycle;
        this.amountXof = amountXof;
        this.startsAt = newStartsAt;
        this.endsAt = newEndsAt;
        this.gracePeriodEndsAt = null;
        this.suspendedAt = null;
        this.statusReason = normalizeOptionalReason(reason);
        return previous == SubscriptionStatus.SUSPENDED
                ? SubscriptionEventType.REACTIVATED
                : SubscriptionEventType.ACTIVATED;
    }

    public void renew(
            BillingCycle billingCycle,
            long amountXof,
            Instant newEndsAt,
            Instant now,
            String reason) {
        Objects.requireNonNull(now, "L'instant courant est obligatoire.");
        if (status != SubscriptionStatus.ACTIVE
                && status != SubscriptionStatus.PAST_DUE
                && status != SubscriptionStatus.SUSPENDED) {
            throw new InvalidSubscriptionTransitionException(
                    "Impossible de renouveler un abonnement au statut " + status + ".");
        }
        Objects.requireNonNull(billingCycle, "Le cycle de facturation est obligatoire.");
        Objects.requireNonNull(newEndsAt, "La nouvelle date de fin est obligatoire.");
        if (amountXof < 0) {
            throw new IllegalArgumentException("Le montant en FCFA doit être positif ou nul.");
        }
        if (!newEndsAt.isAfter(startsAt) || !newEndsAt.isAfter(now)) {
            throw new IllegalArgumentException(
                    "La nouvelle date de fin de renouvellement doit être postérieure à la date courante et à la date de début.");
        }
        this.status = SubscriptionStatus.ACTIVE;
        this.billingCycle = billingCycle;
        this.amountXof = amountXof;
        this.endsAt = newEndsAt;
        this.gracePeriodEndsAt = null;
        this.suspendedAt = null;
        this.statusReason = normalizeOptionalReason(reason);
    }

    public void changePlan(
            SubscriptionPlan newPlan,
            BillingCycle newBillingCycle,
            boolean resetExtraQuotas,
            String reason) {
        if (status.isTerminal()) {
            throw new InvalidSubscriptionTransitionException(
                    "Impossible de changer le forfait d'un abonnement annulé.");
        }
        Objects.requireNonNull(newPlan, "Le nouveau forfait est obligatoire.");
        Objects.requireNonNull(newBillingCycle, "Le cycle de facturation est obligatoire.");
        if (!newPlan.isActive()) {
            throw new SubscriptionPlanInactiveException("Impossible de basculer vers un forfait inactif.");
        }
        if (newPlan.getId() == null) {
            throw new IllegalArgumentException("Le nouveau forfait doit être persisté.");
        }
        int nextExtraStudents = resetExtraQuotas ? 0 : this.extraStudents;
        int nextExtraUsers = resetExtraQuotas ? 0 : this.extraUsers;
        int nextExtraStorageMb = resetExtraQuotas ? 0 : this.extraStorageMb;
        validateEffectiveQuotaTotals(
                newPlan.getMaxStudents(),
                nextExtraStudents,
                newPlan.getMaxUsers(),
                nextExtraUsers,
                newPlan.getMaxStorageMb(),
                nextExtraStorageMb);

        this.planId = newPlan.getId();
        this.billingCycle = newBillingCycle;
        this.amountXof = this.status == SubscriptionStatus.TRIAL ? 0L : newPlan.priceFor(newBillingCycle);
        this.maxStudents = newPlan.getMaxStudents();
        this.maxUsers = newPlan.getMaxUsers();
        this.maxStorageMb = newPlan.getMaxStorageMb();
        this.extraStudents = nextExtraStudents;
        this.extraUsers = nextExtraUsers;
        this.extraStorageMb = nextExtraStorageMb;
        this.statusReason = normalizeOptionalReason(reason);
    }

    public void adjustExtraQuotas(int extraStudents, int extraUsers, int extraStorageMb, String reason) {
        if (status.isTerminal()) {
            throw new InvalidSubscriptionTransitionException(
                    "Impossible d'ajuster les quotas d'un abonnement annulé.");
        }
        if (extraStudents < 0 || extraUsers < 0 || extraStorageMb < 0) {
            throw new IllegalArgumentException("Les extensions de quotas doivent être positives ou nulles.");
        }
        validateEffectiveQuotaTotals(
                this.maxStudents,
                extraStudents,
                this.maxUsers,
                extraUsers,
                this.maxStorageMb,
                extraStorageMb);
        this.extraStudents = extraStudents;
        this.extraUsers = extraUsers;
        this.extraStorageMb = extraStorageMb;
        this.statusReason = normalizeRequiredReason(reason, "Le motif d'ajustement des quotas est obligatoire.");
    }

    public void markPastDue(Instant gracePeriodEndsAt, Instant now, String reason) {
        Objects.requireNonNull(now, "L'instant courant est obligatoire.");
        if (status != SubscriptionStatus.ACTIVE) {
            throw new InvalidSubscriptionTransitionException(
                    "Seul un abonnement actif peut passer au statut impayé (PAST_DUE).");
        }
        if (gracePeriodEndsAt != null && (!gracePeriodEndsAt.isAfter(now) || !gracePeriodEndsAt.isAfter(startsAt))) {
            throw new IllegalArgumentException(
                    "La fin de période de grâce doit être postérieure à la date courante et à la date de début.");
        }
        this.status = SubscriptionStatus.PAST_DUE;
        this.gracePeriodEndsAt = gracePeriodEndsAt;
        this.statusReason = normalizeRequiredReason(reason, "Le motif de passage en impayé est obligatoire.");
    }

    public void suspend(Instant now, String reason) {
        Objects.requireNonNull(now, "L'instant courant est obligatoire.");
        if (status != SubscriptionStatus.TRIAL
                && status != SubscriptionStatus.ACTIVE
                && status != SubscriptionStatus.PAST_DUE) {
            throw new InvalidSubscriptionTransitionException(
                    "Impossible de suspendre un abonnement au statut " + status + ".");
        }
        this.status = SubscriptionStatus.SUSPENDED;
        this.suspendedAt = now;
        this.gracePeriodEndsAt = null;
        this.statusReason = normalizeRequiredReason(reason, "Le motif de suspension est obligatoire.");
    }

    public void cancel(Instant now, String reason) {
        Objects.requireNonNull(now, "L'instant courant est obligatoire.");
        if (status.isTerminal()) {
            throw new InvalidSubscriptionTransitionException("Cet abonnement est déjà annulé.");
        }
        this.status = SubscriptionStatus.CANCELLED;
        this.cancelledAt = now;
        this.gracePeriodEndsAt = null;
        this.statusReason = normalizeRequiredReason(reason, "Le motif d'annulation est obligatoire.");
    }

    public EffectiveQuotas effectiveQuotas() {
        return EffectiveQuotas.of(
                maxStudents,
                extraStudents,
                maxUsers,
                extraUsers,
                maxStorageMb,
                extraStorageMb);
    }

    public EffectiveAccessState evaluateAccessState(Instant now) {
        return SubscriptionPolicy.evaluateAccessState(this, now);
    }

    private static void validateEffectiveQuotaTotals(
            int baseStudents,
            int extraStudents,
            int baseUsers,
            int extraUsers,
            int baseStorageMb,
            int extraStorageMb) {
        try {
            Math.addExact(baseStudents, extraStudents);
            Math.addExact(baseUsers, extraUsers);
            Math.addExact(baseStorageMb, extraStorageMb);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Les quotas effectifs dépassent la limite autorisée.", ex);
        }
    }

    private static String normalizeRequiredReason(String reason, String errorMessage) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Le motif ne peut pas dépasser " + MAX_REASON_LENGTH + " caractères.");
        }
        return trimmed;
    }

    private static String normalizeOptionalReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Le motif ne peut pas dépasser " + MAX_REASON_LENGTH + " caractères.");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public long getAmountXof() {
        return amountXof;
    }

    public int getMaxStudents() {
        return maxStudents;
    }

    public int getMaxUsers() {
        return maxUsers;
    }

    public int getMaxStorageMb() {
        return maxStorageMb;
    }

    public int getExtraStudents() {
        return extraStudents;
    }

    public int getExtraUsers() {
        return extraUsers;
    }

    public int getExtraStorageMb() {
        return extraStorageMb;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Instant getTrialEndsAt() {
        return trialEndsAt;
    }

    public Instant getGracePeriodEndsAt() {
        return gracePeriodEndsAt;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public long getVersion() {
        return version;
    }
}
