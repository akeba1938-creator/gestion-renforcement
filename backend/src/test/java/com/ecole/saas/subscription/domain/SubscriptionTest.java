package com.ecole.saas.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecole.saas.subscription.EffectiveQuotas;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

    private static final Instant START = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void shouldStoreAmountsAsWholeFcfaIntegers() {
        Instant end = START.plus(30, ChronoUnit.DAYS);

        Subscription subscription = new Subscription(
                UUID.randomUUID(),
                SubscriptionStatus.ACTIVE,
                BillingCycle.MONTHLY,
                75_000L,
                500,
                25,
                5_120,
                START,
                end);

        assertThat(subscription.getAmountXof()).isEqualTo(75_000L);
        assertThat(subscription.getMaxStudents()).isEqualTo(500);
        assertThat(subscription.effectiveQuotas().maxStudents()).isEqualTo(500);
    }

    @Test
    void shouldRejectNegativeAmountOrInvalidPeriod() {
        Instant end = START.plus(30, ChronoUnit.DAYS);

        assertThatThrownBy(() -> new Subscription(
                UUID.randomUUID(),
                SubscriptionStatus.ACTIVE,
                BillingCycle.MONTHLY,
                -1L,
                100,
                10,
                1_024,
                START,
                end))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Subscription(
                UUID.randomUUID(),
                SubscriptionStatus.ACTIVE,
                BillingCycle.MONTHLY,
                50_000L,
                100,
                10,
                1_024,
                end,
                START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldStartTrialWithZeroFcfaAndRejectInactivePlan() {
        SubscriptionPlan activePlan = persistedPlan("STARTER", 25_000L, 250_000L, 200, 10, 2_048);
        Instant trialEnd = START.plus(14, ChronoUnit.DAYS);

        Subscription trial = Subscription.startTrial(activePlan, BillingCycle.MONTHLY, START, trialEnd);

        assertThat(trial.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);
        assertThat(trial.getAmountXof()).isZero();
        assertThat(trial.getTrialEndsAt()).isEqualTo(trialEnd);
        assertThat(trial.getEndsAt()).isEqualTo(trialEnd);
        assertThat(trial.effectiveQuotas()).isEqualTo(EffectiveQuotas.of(200, 0, 10, 0, 2_048, 0));

        activePlan.deactivate();
        assertThatThrownBy(() -> Subscription.startTrial(activePlan, BillingCycle.MONTHLY, START, trialEnd))
                .isInstanceOf(SubscriptionPlanInactiveException.class);
        assertThatThrownBy(() -> Subscription.createActive(activePlan, BillingCycle.YEARLY, START, START.plus(365, ChronoUnit.DAYS)))
                .isInstanceOf(SubscriptionPlanInactiveException.class);
    }

    @Test
    void shouldEnforceLifecycleStateMachineTransitions() {
        SubscriptionPlan plan = persistedPlan("PRO", 50_000L, 500_000L, 500, 25, 5_120);
        Instant trialEnd = START.plus(14, ChronoUnit.DAYS);
        Subscription sub = Subscription.startTrial(plan, BillingCycle.MONTHLY, START, trialEnd);

        // Interdit de passer directement de TRIAL à PAST_DUE ou RENEW
        assertThatThrownBy(() -> sub.markPastDue(trialEnd.plus(5, ChronoUnit.DAYS), START, "Impayé"))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
        assertThatThrownBy(() -> sub.renew(BillingCycle.MONTHLY, 50_000L, trialEnd.plus(30, ChronoUnit.DAYS), START, "Renouvellement"))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);

        // TRIAL -> ACTIVE
        Instant activeStart = START.plus(10, ChronoUnit.DAYS);
        Instant activeEnd = activeStart.plus(30, ChronoUnit.DAYS);
        SubscriptionEventType activationEvent = sub.activate(
                BillingCycle.MONTHLY, 50_000L, activeStart, activeEnd, "Paiement reçu");
        assertThat(activationEvent).isEqualTo(SubscriptionEventType.ACTIVATED);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getAmountXof()).isEqualTo(50_000L);

        // ACTIVE -> activate interdit (utiliser renew)
        assertThatThrownBy(() -> sub.activate(BillingCycle.MONTHLY, 50_000L, activeStart, activeEnd.plus(30, ChronoUnit.DAYS), null))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);

        // ACTIVE -> PAST_DUE (avec période de grâce)
        Instant graceEnd = activeEnd.plus(7, ChronoUnit.DAYS);
        sub.markPastDue(graceEnd, activeEnd, "Facture échue");
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(sub.getGracePeriodEndsAt()).isEqualTo(graceEnd);

        // PAST_DUE -> SUSPENDED
        Instant suspendedAt = graceEnd.plus(1, ChronoUnit.DAYS);
        sub.suspend(suspendedAt, "Non paiement après grâce");
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(sub.getSuspendedAt()).isEqualTo(suspendedAt);
        assertThat(sub.getGracePeriodEndsAt()).isNull();

        // SUSPENDED -> ACTIVE (réactivation)
        Instant reactivatedStart = suspendedAt.plus(2, ChronoUnit.DAYS);
        Instant reactivatedEnd = reactivatedStart.plus(365, ChronoUnit.DAYS);
        SubscriptionEventType reactivationEvent = sub.activate(
                BillingCycle.YEARLY, 500_000L, reactivatedStart, reactivatedEnd, "Régularisation annuelle");
        assertThat(reactivationEvent).isEqualTo(SubscriptionEventType.REACTIVATED);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getSuspendedAt()).isNull();
        assertThat(sub.getAmountXof()).isEqualTo(500_000L);

        // ACTIVE -> RENEWED
        Instant renewedEnd = reactivatedEnd.plus(365, ChronoUnit.DAYS);
        sub.renew(BillingCycle.YEARLY, 500_000L, renewedEnd, reactivatedStart.plus(300, ChronoUnit.DAYS), "Renouvellement");
        assertThat(sub.getEndsAt()).isEqualTo(renewedEnd);

        // ACTIVE -> CANCELLED (état terminal)
        Instant cancelledAt = reactivatedStart.plus(310, ChronoUnit.DAYS);
        sub.cancel(cancelledAt, "Résiliation à la demande du promoteur");
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(sub.getCancelledAt()).isEqualTo(cancelledAt);

        // Aucune transition ni mutation autorisée depuis CANCELLED
        assertThatThrownBy(() -> sub.activate(BillingCycle.MONTHLY, 50_000L, cancelledAt, cancelledAt.plus(30, ChronoUnit.DAYS), null))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
        assertThatThrownBy(() -> sub.suspend(cancelledAt, "Test"))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
        assertThatThrownBy(() -> sub.cancel(cancelledAt, "Double annulation"))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
        assertThatThrownBy(() -> sub.adjustExtraQuotas(10, 0, 0, "Extension"))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
        assertThatThrownBy(() -> sub.changePlan(plan, BillingCycle.MONTHLY, false, "Upgrade"))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);
    }

    @Test
    void shouldAdjustExtraQuotasAndChangePlanPreservingOrResettingExtras() {
        SubscriptionPlan starter = persistedPlan("STARTER", 25_000L, 250_000L, 200, 10, 2_048);
        SubscriptionPlan enterprise = persistedPlan("ENTERPRISE", 120_000L, 1_200_000L, 1_500, 60, 20_480);

        Subscription sub = Subscription.createActive(
                starter, BillingCycle.MONTHLY, START, START.plus(30, ChronoUnit.DAYS));

        sub.adjustExtraQuotas(50, 5, 1_024, "Extension rentrée scolaire");
        assertThat(sub.effectiveQuotas()).isEqualTo(EffectiveQuotas.of(200, 50, 10, 5, 2_048, 1_024));
        assertThat(sub.effectiveQuotas().maxStudents()).isEqualTo(250);
        assertThat(sub.effectiveQuotas().maxUsers()).isEqualTo(15);
        assertThat(sub.effectiveQuotas().maxStorageMb()).isEqualTo(3_072);

        // Changement de forfait en conservant les extensions
        sub.changePlan(enterprise, BillingCycle.YEARLY, false, "Montée en gamme");
        assertThat(sub.getPlanId()).isEqualTo(enterprise.getId());
        assertThat(sub.getAmountXof()).isEqualTo(1_200_000L);
        assertThat(sub.effectiveQuotas()).isEqualTo(EffectiveQuotas.of(1_500, 50, 60, 5, 20_480, 1_024));

        // Changement de forfait avec réinitialisation des extensions
        sub.changePlan(starter, BillingCycle.MONTHLY, true, "Retour forfait initial");
        assertThat(sub.getAmountXof()).isEqualTo(25_000L);
        assertThat(sub.effectiveQuotas()).isEqualTo(EffectiveQuotas.of(200, 0, 10, 0, 2_048, 0));

        // Rejet de débordement d'entier ou extensions négatives
        assertThatThrownBy(() -> sub.adjustExtraQuotas(-1, 0, 0, "Invalide"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sub.adjustExtraQuotas(Integer.MAX_VALUE, 0, 0, "Overflow"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SubscriptionPlan persistedPlan(
            String code,
            long monthlyPriceXof,
            long yearlyPriceXof,
            int maxStudents,
            int maxUsers,
            int maxStorageMb) {
        SubscriptionPlan plan = new SubscriptionPlan(
                code, "Forfait " + code, null, monthlyPriceXof, yearlyPriceXof, maxStudents, maxUsers, maxStorageMb, 14);
        try {
            Field idField = SubscriptionPlan.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(plan, UUID.randomUUID());
            return plan;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
