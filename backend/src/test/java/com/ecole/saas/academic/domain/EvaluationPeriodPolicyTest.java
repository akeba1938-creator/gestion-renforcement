package com.ecole.saas.academic.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvaluationPeriodPolicyTest {

    @Test
    void shouldGenerateThreeContiguousTrimestersCoveringAcademicYear() {
        AcademicYear year = new AcademicYear(
                "2026-2027",
                "Année 2026-2027",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 7, 31),
                PeriodSystem.TRIMESTER,
                AcademicYearStatus.DRAFT,
                false);
        // Utiliser un UUID de test via une période construite sur une année fictive sans JPA
        AcademicYear yearWithId = yearWithFixedId(year, UUID.randomUUID());

        List<EvaluationPeriod> periods = EvaluationPeriodPolicy.buildStandardPeriods(yearWithId);

        assertThat(periods).hasSize(3);
        assertThat(periods).extracting(EvaluationPeriod::getCode).containsExactly("T1", "T2", "T3");
        assertThat(periods).extracting(EvaluationPeriod::getSequenceOrder).containsExactly(1, 2, 3);
        assertThat(periods.get(0).getStartDate()).isEqualTo(yearWithId.getStartDate());
        assertThat(periods.get(1).getStartDate()).isEqualTo(periods.get(0).getEndDate().plusDays(1));
        assertThat(periods.get(2).getStartDate()).isEqualTo(periods.get(1).getEndDate().plusDays(1));
        assertThat(periods.get(2).getEndDate()).isEqualTo(yearWithId.getEndDate());
        assertThat(periods).allSatisfy(p -> assertThat(p.getEndDate()).isAfter(p.getStartDate()));
    }

    @Test
    void shouldGenerateTwoContiguousSemesters() {
        AcademicYear year = yearWithFixedId(
                new AcademicYear(
                        "2026-2027",
                        "Année semestrielle",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2027, 6, 30),
                        PeriodSystem.SEMESTER,
                        AcademicYearStatus.DRAFT,
                        false),
                UUID.randomUUID());

        List<EvaluationPeriod> periods = EvaluationPeriodPolicy.buildStandardPeriods(year);

        assertThat(periods).hasSize(2);
        assertThat(periods).extracting(EvaluationPeriod::getCode).containsExactly("S1", "S2");
        assertThat(periods.get(0).getStartDate()).isEqualTo(year.getStartDate());
        assertThat(periods.get(1).getStartDate()).isEqualTo(periods.get(0).getEndDate().plusDays(1));
        assertThat(periods.get(1).getEndDate()).isEqualTo(year.getEndDate());
    }

    @Test
    void shouldRejectOutOfBoundsOverlapAndSystemLimitViolations() {
        UUID yearId = UUID.randomUUID();
        AcademicYear year = yearWithFixedId(
                new AcademicYear(
                        "2026-2027",
                        "Année 2026-2027",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2027, 7, 31),
                        PeriodSystem.SEMESTER,
                        AcademicYearStatus.ACTIVE,
                        true),
                yearId);

        assertThatThrownBy(() -> EvaluationPeriodPolicy.validateWithinAcademicYear(
                year, LocalDate.of(2026, 9, 15), LocalDate.of(2027, 1, 31)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> EvaluationPeriodPolicy.validateAgainstPeriodSystem(
                year, PeriodSystem.TRIMESTER, 0))
                .isInstanceOf(EvaluationPeriodConflictException.class);

        assertThatThrownBy(() -> EvaluationPeriodPolicy.validateAgainstPeriodSystem(
                year, PeriodSystem.SEMESTER, 2))
                .isInstanceOf(EvaluationPeriodConflictException.class);

        EvaluationPeriod existing = new EvaluationPeriod(
                yearId, "S1", "Semestre 1", PeriodSystem.SEMESTER, 1,
                LocalDate.of(2026, 10, 1), LocalDate.of(2027, 2, 15), 1);

        assertThatThrownBy(() -> EvaluationPeriodPolicy.validateNoOverlap(
                LocalDate.of(2027, 2, 15), LocalDate.of(2027, 6, 30), List.of(existing), null))
                .isInstanceOf(EvaluationPeriodConflictException.class);
    }

    @Test
    void shouldEnforceEvaluationPeriodStatusLifecycleAndLockImmutability() {
        EvaluationPeriod period = new EvaluationPeriod(
                UUID.randomUUID(),
                "t1",
                "Trimestre 1",
                PeriodSystem.TRIMESTER,
                1,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 20),
                1);
        Instant now = Instant.parse("2026-12-22T12:00:00Z");

        assertThat(period.getCode()).isEqualTo("T1");
        assertThat(period.getStatus()).isEqualTo(EvaluationPeriodStatus.PLANNED);
        assertThatThrownBy(period::close).isInstanceOf(InvalidEvaluationPeriodTransitionException.class);

        period.open();
        assertThat(period.getStatus()).isEqualTo(EvaluationPeriodStatus.OPEN);
        assertThatThrownBy(() -> period.lock(now)).isInstanceOf(InvalidEvaluationPeriodTransitionException.class);

        period.close();
        assertThat(period.getStatus()).isEqualTo(EvaluationPeriodStatus.CLOSED);

        period.reopen();
        assertThat(period.getStatus()).isEqualTo(EvaluationPeriodStatus.OPEN);

        period.close();
        period.lock(now);
        assertThat(period.getStatus()).isEqualTo(EvaluationPeriodStatus.LOCKED);
        assertThat(period.getLockedAt()).isEqualTo(now);

        assertThatThrownBy(period::reopen).isInstanceOf(InvalidEvaluationPeriodTransitionException.class);
        assertThatThrownBy(() -> period.updateDetails(
                "Modifié", 1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 21), 1))
                .isInstanceOf(InvalidEvaluationPeriodTransitionException.class);
    }

    private static AcademicYear yearWithFixedId(AcademicYear year, UUID id) {
        try {
            var field = AcademicYear.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(year, id);
            return year;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
