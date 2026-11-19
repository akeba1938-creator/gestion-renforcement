package com.ecole.saas.academic.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
