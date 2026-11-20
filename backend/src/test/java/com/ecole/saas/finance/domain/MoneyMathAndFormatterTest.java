package com.ecole.saas.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MoneyMathAndFormatterTest {

    @Test
    void shouldPerformExactIntegerMoneyOperationsAndDetectOverflow() {
        assertThat(MoneyMath.requireNonNegative(0L, "Montant")).isZero();
        assertThat(MoneyMath.requirePositive(1_500L, "Montant")).isEqualTo(1_500L);
        assertThat(MoneyMath.addExact(25_000L, 15_000L)).isEqualTo(40_000L);
        assertThat(MoneyMath.subtractExact(40_000L, 15_000L)).isEqualTo(25_000L);
        assertThat(MoneyMath.multiplyExact(15_000L, 9)).isEqualTo(135_000L);
        assertThat(MoneyMath.percentageOf(100_000L, 25)).isEqualTo(25_000L);
        assertThat(MoneyMath.percentageOf(10_001L, 10)).isEqualTo(1_000L);

        assertThatThrownBy(() -> MoneyMath.requireNonNegative(-1L, "Montant"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MoneyMath.requirePositive(0L, "Montant"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MoneyMath.addExact(Long.MAX_VALUE, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MoneyMath.multiplyExact(Long.MAX_VALUE / 2, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MoneyMath.percentageOf(100_000L, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MoneyMath.percentageOf(100_000L, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSplitAmountEvenlyInExactIntegersWithoutLosingOneFcfa() {
        List<Long> shares = MoneyMath.splitEvenly(100_000L, 3);
        assertThat(shares).containsExactly(33_334L, 33_333L, 33_333L);
        assertThat(shares.stream().mapToLong(Long::longValue).sum()).isEqualTo(100_000L);

        assertThat(MoneyMath.splitEvenly(90_000L, 3)).containsExactly(30_000L, 30_000L, 30_000L);
        assertThatThrownBy(() -> MoneyMath.splitEvenly(50_000L, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MoneyMath.splitEvenly(50_000L, 37))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNormalizeFeeCodesAndFormatSequentialNumbers() {
        assertThat(FinanceCodeFormatter.normalizeFeeCode(" scol-2026_a ")).isEqualTo("SCOL-2026_A");
        assertThat(FinanceCodeFormatter.formatInvoiceNumber("ind", 2026, 7L)).isEqualTo("FAC-IND-2026-000007");
        assertThat(FinanceCodeFormatter.formatReceiptNumber("ind", 2026, 42L)).isEqualTo("REC-IND-2026-000042");

        assertThatThrownBy(() -> FinanceCodeFormatter.normalizeFeeCode("A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinanceCodeFormatter.normalizeFeeCode("BAD CODE!"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinanceCodeFormatter.formatReceiptNumber("IND", 2026, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldGenerateUniqueUrlSafeOpaqueVerificationTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String token = OpaqueTokenGenerator.generateVerificationToken();
            assertThat(token).hasSizeGreaterThanOrEqualTo(40);
            assertThat(token).matches("^[A-Za-z0-9_-]+$");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(50);
    }
}
