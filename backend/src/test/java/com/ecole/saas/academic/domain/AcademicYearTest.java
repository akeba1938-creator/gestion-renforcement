package com.ecole.saas.academic.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AcademicYearTest {

    @Test
    void shouldCreateValidAcademicYear() {
        AcademicYear year = new AcademicYear(
                "2026-2027",
                "Année scolaire 2026-2027",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 7, 31),
                AcademicYearStatus.ACTIVE,
                true);

        assertThat(year.getCode()).isEqualTo("2026-2027");
        assertThat(year.getPeriodSystem()).isEqualTo(PeriodSystem.TRIMESTER);
        assertThat(year.isCurrent()).isTrue();
    }

    @Test
    void shouldRejectEndDateBeforeOrEqualStartDate() {
        LocalDate start = LocalDate.of(2026, 10, 1);

        assertThatThrownBy(() -> new AcademicYear(
                "2026-2027",
                "Invalide",
                start,
                start,
                AcademicYearStatus.DRAFT,
                false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectCurrentFlagOnNonActiveAcademicYear() {
        assertThatThrownBy(() -> new AcademicYear(
                "2026-2027",
                "Brouillon courant interdit",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 7, 31),
                PeriodSystem.TRIMESTER,
                AcademicYearStatus.DRAFT,
                true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldEnforceStrictLifecycleAndImmutabilityAfterClose() {
        AcademicYear year = new AcademicYear(
                "2026-2027",
                "Année 2026-2027",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 7, 31),
                PeriodSystem.SEMESTER,
                AcademicYearStatus.DRAFT,
                false);
        Instant now = Instant.parse("2027-07-31T18:00:00Z");

        assertThatThrownBy(year::markAsCurrent)
                .isInstanceOf(InvalidAcademicYearTransitionException.class);
        assertThatThrownBy(() -> year.close(now))
                .isInstanceOf(InvalidAcademicYearTransitionException.class);

        year.activate(true);
        assertThat(year.getStatus()).isEqualTo(AcademicYearStatus.ACTIVE);
        assertThat(year.isCurrent()).isTrue();

        assertThatThrownBy(() -> year.archive(now))
                .isInstanceOf(InvalidAcademicYearTransitionException.class);

        year.close(now);
        assertThat(year.getStatus()).isEqualTo(AcademicYearStatus.CLOSED);
        assertThat(year.isCurrent()).isFalse();
        assertThat(year.getClosedAt()).isEqualTo(now);

        assertThatThrownBy(() -> year.updateLabel("Modification interdite"))
                .isInstanceOf(AcademicYearImmutableException.class);

        year.archive(now.plusSeconds(3600));
        assertThat(year.getStatus()).isEqualTo(AcademicYearStatus.ARCHIVED);
        assertThat(year.getArchivedAt()).isEqualTo(now.plusSeconds(3600));

        assertThatThrownBy(() -> year.activate(false))
                .isInstanceOf(InvalidAcademicYearTransitionException.class);
    }
}
