package com.ecole.saas.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SubscriptionPlanTest {

    @Test
    void shouldNormalizeCodeAndComputeCyclePricesInWholeFcfa() {
        SubscriptionPlan plan = new SubscriptionPlan(
                "  pro-ecole_1 ",
                "  Forfait Pro  ",
                "  Pour les écoles moyennes  ",
                50_000L,
                500_000L,
                600,
                30,
                10_240,
                21);

        assertThat(plan.getCode()).isEqualTo("PRO-ECOLE_1");
        assertThat(plan.getName()).isEqualTo("Forfait Pro");
        assertThat(plan.getDescription()).isEqualTo("Pour les écoles moyennes");
        assertThat(plan.getTrialDays()).isEqualTo(21);
        assertThat(plan.isActive()).isTrue();
        assertThat(plan.priceFor(BillingCycle.MONTHLY)).isEqualTo(50_000L);
        assertThat(plan.priceFor(BillingCycle.YEARLY)).isEqualTo(500_000L);
    }

    @Test
    void shouldDefaultTrialDaysToFourteenWithShortConstructor() {
        SubscriptionPlan plan = new SubscriptionPlan(
                "STARTER", "Forfait Découverte", 25_000L, 250_000L, 200, 10, 2_048);

        assertThat(plan.getTrialDays()).isEqualTo(SubscriptionPlan.DEFAULT_TRIAL_DAYS);
        assertThat(plan.getDescription()).isNull();
    }

    @Test
    void shouldUpdateDetailsAndToggleActiveFlag() {
        SubscriptionPlan plan = new SubscriptionPlan(
                "STARTER", "Forfait Starter", "Initial", 25_000L, 250_000L, 200, 10, 2_048, 14);

        plan.updateDetails("Forfait Starter Plus", "   ", 30_000L, 300_000L, 300, 15, 4_096, 30);
        assertThat(plan.getName()).isEqualTo("Forfait Starter Plus");
        assertThat(plan.getDescription()).isNull();
        assertThat(plan.getMonthlyPriceXof()).isEqualTo(30_000L);
        assertThat(plan.getYearlyPriceXof()).isEqualTo(300_000L);
        assertThat(plan.getMaxStudents()).isEqualTo(300);
        assertThat(plan.getMaxUsers()).isEqualTo(15);
        assertThat(plan.getMaxStorageMb()).isEqualTo(4_096);
        assertThat(plan.getTrialDays()).isEqualTo(30);

        plan.deactivate();
        assertThat(plan.isActive()).isFalse();

        plan.activate();
        assertThat(plan.isActive()).isTrue();
    }

    @Test
    void shouldRejectInvalidCodePricesQuotasOrTrialDays() {
        assertThatThrownBy(() -> new SubscriptionPlan(
                "code invalide !", "Nom", null, 10_000L, 100_000L, 100, 5, 1_024, 14))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new SubscriptionPlan(
                "STARTER", "   ", null, 10_000L, 100_000L, 100, 5, 1_024, 14))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new SubscriptionPlan(
                "STARTER", "Nom", null, -1L, 100_000L, 100, 5, 1_024, 14))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new SubscriptionPlan(
                "STARTER", "Nom", null, 10_000L, 100_000L, 0, 5, 1_024, 14))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new SubscriptionPlan(
                "STARTER", "Nom", null, 10_000L, 100_000L, 100, 5, 1_024, -1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new SubscriptionPlan(
                "STARTER", "Nom", null, 10_000L, 100_000L, 100, 5, 1_024, 366))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
