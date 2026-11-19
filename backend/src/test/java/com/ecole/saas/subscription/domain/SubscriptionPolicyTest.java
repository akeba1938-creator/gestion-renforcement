package com.ecole.saas.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecole.saas.subscription.EffectiveAccessState;
import com.ecole.saas.subscription.QuotaCheckResult;
import com.ecole.saas.subscription.QuotaResource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionPolicyTest {

    private static final Instant START = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void shouldEvaluateEffectiveAccessStatesCorrectly() {
        assertThat(SubscriptionPolicy.evaluateAccessState(null, START))
                .isEqualTo(EffectiveAccessState.MISSING);
        assertThat(EffectiveAccessState.MISSING.isReadAllowed()).isFalse();
        assertThat(EffectiveAccessState.MISSING.isWriteAllowed()).isFalse();

        // Essai actif puis expiré
        Instant trialEnd = START.plus(14, ChronoUnit.DAYS);
        Subscription trial = new Subscription(
                UUID.randomUUID(), SubscriptionStatus.TRIAL, BillingCycle.MONTHLY, 0L, 100, 10, 1_024, START, trialEnd);
        assertThat(SubscriptionPolicy.evaluateAccessState(trial, START.plus(7, ChronoUnit.DAYS)))
                .isEqualTo(EffectiveAccessState.TRIAL_ACTIVE);
        assertThat(SubscriptionPolicy.evaluateAccessState(trial, trialEnd))
                .isEqualTo(EffectiveAccessState.EXPIRED);
        assertThat(EffectiveAccessState.EXPIRED.isReadAllowed()).isTrue();
        assertThat(EffectiveAccessState.EXPIRED.isWriteAllowed()).isFalse();

        // Actif puis expiré
        Instant end = START.plus(30, ChronoUnit.DAYS);
        Subscription active = new Subscription(
                UUID.randomUUID(), SubscriptionStatus.ACTIVE, BillingCycle.MONTHLY, 50_000L, 500, 20, 5_120, START, end);
        assertThat(SubscriptionPolicy.evaluateAccessState(active, end.minusSeconds(1)))
                .isEqualTo(EffectiveAccessState.ACTIVE);
        assertThat(SubscriptionPolicy.evaluateAccessState(active, end))
                .isEqualTo(EffectiveAccessState.EXPIRED);

        // PAST_DUE avec grâce puis hors grâce
        Instant graceEnd = end.plus(5, ChronoUnit.DAYS);
        active.markPastDue(graceEnd, end, "Facture en retard");
        assertThat(SubscriptionPolicy.evaluateAccessState(active, end.plus(2, ChronoUnit.DAYS)))
                .isEqualTo(EffectiveAccessState.PAST_DUE_GRACE);
        assertThat(EffectiveAccessState.PAST_DUE_GRACE.isReadAllowed()).isTrue();
        assertThat(EffectiveAccessState.PAST_DUE_GRACE.isWriteAllowed()).isTrue();
        assertThat(SubscriptionPolicy.evaluateAccessState(active, graceEnd))
                .isEqualTo(EffectiveAccessState.EXPIRED);

        // SUSPENDED et CANCELLED
        active.suspend(graceEnd, "Impayé prolongé");
        assertThat(SubscriptionPolicy.evaluateAccessState(active, graceEnd))
                .isEqualTo(EffectiveAccessState.SUSPENDED);
        assertThat(EffectiveAccessState.SUSPENDED.isReadAllowed()).isFalse();
        assertThat(EffectiveAccessState.SUSPENDED.isWriteAllowed()).isFalse();

        active.cancel(graceEnd.plus(1, ChronoUnit.DAYS), "Résilié");
        assertThat(SubscriptionPolicy.evaluateAccessState(active, graceEnd.plus(1, ChronoUnit.DAYS)))
                .isEqualTo(EffectiveAccessState.CANCELLED);
        assertThat(EffectiveAccessState.CANCELLED.isReadAllowed()).isFalse();
        assertThat(EffectiveAccessState.CANCELLED.isWriteAllowed()).isFalse();
    }

    @Test
    void shouldCheckAndEnforceEffectiveQuotasAcrossAllResources() {
        Instant end = START.plus(30, ChronoUnit.DAYS);
        Instant now = START.plus(5, ChronoUnit.DAYS);
        Subscription sub = new Subscription(
                UUID.randomUUID(), SubscriptionStatus.ACTIVE, BillingCycle.MONTHLY, 50_000L, 100, 10, 1_024, START, end);
        sub.adjustExtraQuotas(25, 5, 512, "Extensions");

        // Plafonds effectifs : 125 élèves, 15 utilisateurs, 1536 Mo
        QuotaCheckResult exactBoundary = SubscriptionPolicy.evaluateQuota(sub, QuotaResource.STUDENTS, 124, 1, now);
        assertThat(exactBoundary.allowed()).isTrue();
        assertThat(exactBoundary.limit()).isEqualTo(125L);
        assertThat(exactBoundary.remaining()).isEqualTo(1L);
        assertThatCode(() -> SubscriptionPolicy.assertQuotaAvailable(sub, QuotaResource.STUDENTS, 124, 1, now))
                .doesNotThrowAnyException();

        // Dépassement de 1 élève
        QuotaCheckResult exceeded = SubscriptionPolicy.evaluateQuota(sub, QuotaResource.STUDENTS, 125, 1, now);
        assertThat(exceeded.allowed()).isFalse();
        assertThat(exceeded.remaining()).isZero();
        assertThat(exceeded.reason()).contains("élèves");
        assertThatThrownBy(() -> SubscriptionPolicy.assertQuotaAvailable(sub, QuotaResource.STUDENTS, 125, 1, now))
                .isInstanceOf(SubscriptionQuotaExceededException.class);

        // Utilisateurs et stockage
        assertThatCode(() -> SubscriptionPolicy.assertQuotaAvailable(sub, QuotaResource.USERS, 14, 1, now))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> SubscriptionPolicy.assertQuotaAvailable(sub, QuotaResource.USERS, 15, 1, now))
                .isInstanceOf(SubscriptionQuotaExceededException.class);

        assertThatCode(() -> SubscriptionPolicy.assertQuotaAvailable(sub, QuotaResource.STORAGE_MB, 1_500, 36, now))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> SubscriptionPolicy.assertQuotaAvailable(sub, QuotaResource.STORAGE_MB, 1_500, 37, now))
                .isInstanceOf(SubscriptionQuotaExceededException.class);

        // Protection contre débordement long
        QuotaCheckResult overflowAttempt = SubscriptionPolicy.evaluateQuota(
                sub, QuotaResource.STORAGE_MB, 10, Long.MAX_VALUE, now);
        assertThat(overflowAttempt.allowed()).isFalse();
    }

    @Test
    void shouldDenyQuotaChecksWhenSubscriptionIsExpiredSuspendedOrMissing() {
        Instant end = START.plus(30, ChronoUnit.DAYS);
        Subscription sub = new Subscription(
                UUID.randomUUID(), SubscriptionStatus.ACTIVE, BillingCycle.MONTHLY, 50_000L, 100, 10, 1_024, START, end);

        // Expiré (lecture seule) -> écriture refusée avec SubscriptionAccessDeniedException (403)
        assertThatThrownBy(() -> SubscriptionPolicy.assertQuotaAvailable(sub, QuotaResource.STUDENTS, 0, 1, end))
                .isInstanceOf(SubscriptionAccessDeniedException.class);

        // Suspendu -> refusé
        sub.suspend(START.plus(5, ChronoUnit.DAYS), "Suspension");
        assertThatThrownBy(() -> SubscriptionPolicy.assertOperational(sub, START.plus(6, ChronoUnit.DAYS)))
                .isInstanceOf(SubscriptionAccessDeniedException.class);

        // Absent -> refusé
        assertThatThrownBy(() -> SubscriptionPolicy.assertQuotaAvailable(null, QuotaResource.USERS, 0, 1, START))
                .isInstanceOf(SubscriptionAccessDeniedException.class);
    }

    @Test
    void shouldComputeTrialAndBillingPeriodEndDates() {
        assertThat(SubscriptionPolicy.computeTrialEnd(START, 14))
                .isEqualTo(Instant.parse("2026-10-15T00:00:00Z"));
        assertThat(SubscriptionPolicy.computePeriodEnd(START, BillingCycle.MONTHLY))
                .isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(SubscriptionPolicy.computePeriodEnd(START, BillingCycle.YEARLY))
                .isEqualTo(Instant.parse("2027-10-01T00:00:00Z"));

        assertThatThrownBy(() -> SubscriptionPolicy.computeTrialEnd(START, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
