package com.ecole.saas.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReportingMetricsCalculatorTest {

    @Test
    void shouldComputeExactPercentagesWithoutFloatingPoint() {
        assertThat(ReportingMetricsCalculator.percentage(3, 4)).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(ReportingMetricsCalculator.percentage(2, 3)).isEqualByComparingTo(new BigDecimal("66.67"));
        assertThat(ReportingMetricsCalculator.percentage(1, 3)).isEqualByComparingTo(new BigDecimal("33.33"));
        assertThat(ReportingMetricsCalculator.percentage(5, 0)).isNull();
        assertThat(ReportingMetricsCalculator.percentage(5, -2)).isNull();
    }

    @Test
    void shouldComputeCollectionRateAndDetectXofOverflow() {
        assertThat(ReportingMetricsCalculator.collectionRatePercent(50_000L, 80_000L))
                .isEqualByComparingTo(new BigDecimal("62.50"));
        assertThat(ReportingMetricsCalculator.collectionRatePercent(0L, 0L)).isNull();
        assertThat(ReportingMetricsCalculator.addXof(120_000L, 80_000L)).isEqualTo(200_000L);
        assertThatThrownBy(() -> ReportingMetricsCalculator.addXof(Long.MAX_VALUE, 1L))
                .isInstanceOf(ReportingValidationException.class);
    }

    @Test
    void shouldValidateDateRanges() {
        ReportingMetricsCalculator.validateDateRange(null, null);
        ReportingMetricsCalculator.validateDateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 20));
        ReportingMetricsCalculator.validateDateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));

        assertThatThrownBy(() -> ReportingMetricsCalculator.validateDateRange(
                LocalDate.of(2026, 12, 20), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(ReportingValidationException.class);

        assertThatThrownBy(() -> ReportingMetricsCalculator.validateDateRange(
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 2, 1)))
                .isInstanceOf(ReportingValidationException.class);
    }
}
