package com.ecole.saas.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

    @Test
    void shouldStoreAmountsAsWholeFcfaIntegers() {
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Instant end = start.plus(30, ChronoUnit.DAYS);

        Subscription subscription = new Subscription(
                UUID.randomUUID(),
                SubscriptionStatus.ACTIVE,
                BillingCycle.MONTHLY,
                75_000L,
                500,
                25,
                5_120,
                start,
                end);

        assertThat(subscription.getAmountXof()).isEqualTo(75_000L);
        assertThat(subscription.getMaxStudents()).isEqualTo(500);
    }

    @Test
    void shouldRejectNegativeAmountOrInvalidPeriod() {
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Instant end = start.plus(30, ChronoUnit.DAYS);

        assertThatThrownBy(() -> new Subscription(
                UUID.randomUUID(),
                SubscriptionStatus.ACTIVE,
                BillingCycle.MONTHLY,
                -1L,
                100,
                10,
                1_024,
                start,
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
                start))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
