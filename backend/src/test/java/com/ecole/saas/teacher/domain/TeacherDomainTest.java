package com.ecole.saas.teacher.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeacherDomainTest {

    @Test
    void shouldNormalizeCodeEmailAndValidatePhoneAndHourlyRate() {
        Teacher teacher = new Teacher(
                " ens-001 ",
                null,
                " Cheikh ",
                " Ndiaye ",
                TeacherGender.MALE,
                TeacherType.PART_TIME,
                "+221771234567",
                "+221781234567",
                " Cheikh.Ndiaye@Ecole.SN ",
                "Dakar Plateau",
                "CNI-12345",
                LocalDate.of(2026, 10, 1),
                7500L,
                "Professeur vacataire");

        assertThat(teacher.getEmployeeCode()).isEqualTo("ENS-001");
        assertThat(teacher.getFirstName()).isEqualTo("Cheikh");
        assertThat(teacher.getLastName()).isEqualTo("Ndiaye");
        assertThat(teacher.getFullName()).isEqualTo("Cheikh Ndiaye");
        assertThat(teacher.getEmail()).isEqualTo("cheikh.ndiaye@ecole.sn");
        assertThat(teacher.getHourlyRateXof()).isEqualTo(7500L);
        assertThat(teacher.getStatus()).isEqualTo(TeacherStatus.ACTIVE);
        assertThat(teacher.isActive()).isTrue();
    }

    @Test
    void shouldRejectInvalidPhoneCodeAndNegativeHourlyRate() {
        assertThatThrownBy(() -> new Teacher(
                        "E",
                        null,
                        "Awa",
                        "Fall",
                        null,
                        TeacherType.PERMANENT,
                        "+221771234567",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))
                .isInstanceOf(TeacherValidationException.class);

        assertThatThrownBy(() -> new Teacher(
                        "ENS-002",
                        null,
                        "Awa",
                        "Fall",
                        null,
                        TeacherType.PERMANENT,
                        "771234567",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))
                .isInstanceOf(TeacherValidationException.class);

        assertThatThrownBy(() -> new Teacher(
                        "ENS-003",
                        null,
                        "Awa",
                        "Fall",
                        null,
                        TeacherType.TUTOR,
                        "+221771234567",
                        null,
                        null,
                        null,
                        null,
                        null,
                        -500L,
                        null))
                .isInstanceOf(TeacherValidationException.class);
    }

    @Test
    void shouldEnforceTeacherStatusTransitionsAndArchiveImmutability() {
        Teacher teacher = new Teacher(
                "ENS-010",
                null,
                "Moussa",
                "Diop",
                TeacherGender.MALE,
                TeacherType.PERMANENT,
                "+221770001122",
                null,
                null,
                null,
                null,
                LocalDate.of(2025, 9, 15),
                null,
                null);

        teacher.suspend("Congé sans solde");
        assertThat(teacher.getStatus()).isEqualTo(TeacherStatus.SUSPENDED);
        assertThat(teacher.getStatusReason()).isEqualTo("Congé sans solde");
        assertThatThrownBy(teacher::assertActive).isInstanceOf(TeacherConflictException.class);

        teacher.reactivate();
        assertThat(teacher.getStatus()).isEqualTo(TeacherStatus.ACTIVE);
        assertThat(teacher.getStatusReason()).isNull();

        Instant archivedAt = Instant.parse("2026-11-20T10:00:00Z");
        teacher.archive("Départ fin de contrat", archivedAt);
        assertThat(teacher.getStatus()).isEqualTo(TeacherStatus.ARCHIVED);
        assertThat(teacher.getArchivedAt()).isEqualTo(archivedAt);

        assertThatThrownBy(() -> teacher.reactivate()).isInstanceOf(TeacherConflictException.class);
        assertThatThrownBy(() -> teacher.linkUser(UUID.randomUUID())).isInstanceOf(TeacherConflictException.class);
    }

    @Test
    void shouldValidateAssignmentDatesWeeklyHoursAndDeactivation() {
        TeacherAssignment assignment = new TeacherAssignment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                true,
                6,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 7, 31),
                "Professeur principal");

        assertThat(assignment.isActive()).isTrue();
        assertThat(assignment.isHomeroomTeacher()).isTrue();

        assignment.deactivate(LocalDate.of(2027, 2, 1), "Changement d'affectation");
        assertThat(assignment.isActive()).isFalse();
        assertThat(assignment.isHomeroomTeacher()).isFalse();
        assertThat(assignment.getEndDate()).isEqualTo(LocalDate.of(2027, 2, 1));

        assertThatThrownBy(() -> assignment.updateDetails(false, 4, null, null, null))
                .isInstanceOf(TeacherConflictException.class);

        assertThatThrownBy(() -> new TeacherAssignment(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        false,
                        45,
                        null,
                        null,
                        null))
                .isInstanceOf(TeacherValidationException.class);

        assertThatThrownBy(() -> new TeacherAssignment(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        false,
                        4,
                        LocalDate.of(2026, 10, 10),
                        LocalDate.of(2026, 10, 1),
                        null))
                .isInstanceOf(TeacherValidationException.class);
    }
}
