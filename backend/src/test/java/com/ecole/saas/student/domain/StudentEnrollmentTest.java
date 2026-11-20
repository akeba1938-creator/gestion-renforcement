package com.ecole.saas.student.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudentEnrollmentTest {

    @Test
    void shouldCreateTransferClassAndCompleteEnrollment() {
        UUID studentId = UUID.randomUUID();
        UUID yearId = UUID.randomUUID();
        UUID classA = UUID.randomUUID();
        UUID classB = UUID.randomUUID();

        StudentEnrollment enrollment = new StudentEnrollment(
                studentId,
                yearId,
                classA,
                EnrollmentType.NEW_ADMISSION,
                LocalDate.of(2026, 10, 1),
                "Collège Saint-Michel",
                false,
                "Dossier complet");

        assertThat(enrollment.isActive()).isTrue();
        assertThat(enrollment.getClassRoomId()).isEqualTo(classA);

        assertThatThrownBy(() -> enrollment.transferClass(classA, "Même classe"))
                .isInstanceOf(IllegalArgumentException.class);

        enrollment.transferClass(classB, "Équilibrage des effectifs");
        assertThat(enrollment.getClassRoomId()).isEqualTo(classB);
        assertThat(enrollment.getStatusReason()).isEqualTo("Équilibrage des effectifs");

        assertThatThrownBy(() -> enrollment.complete(LocalDate.of(2026, 9, 30), "Date antérieure"))
                .isInstanceOf(IllegalArgumentException.class);

        enrollment.complete(LocalDate.of(2027, 7, 15), "Passage en classe supérieure");
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(enrollment.getEndDate()).isEqualTo(LocalDate.of(2027, 7, 15));

        assertThatThrownBy(() -> enrollment.transferClass(classA, "Après fin"))
                .isInstanceOf(InvalidEnrollmentStateException.class);
        assertThatThrownBy(() -> enrollment.withdraw(LocalDate.of(2027, 7, 20), "Retrait"))
                .isInstanceOf(InvalidEnrollmentStateException.class);
    }

    @Test
    void shouldRequireReasonForWithdrawTransferOutAndCancel() {
        StudentEnrollment e1 = new StudentEnrollment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                EnrollmentType.RE_ENROLLMENT,
                LocalDate.of(2026, 10, 1),
                null,
                true,
                null);

        assertThatThrownBy(() -> e1.withdraw(LocalDate.of(2026, 11, 1), " "))
                .isInstanceOf(IllegalArgumentException.class);
        e1.withdraw(LocalDate.of(2026, 11, 1), "Départ de la famille");
        assertThat(e1.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);

        StudentEnrollment e2 = new StudentEnrollment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                EnrollmentType.TRANSFER_IN,
                LocalDate.of(2026, 10, 1),
                null,
                false,
                null);
        assertThatThrownBy(() -> e2.transferOut(LocalDate.of(2026, 12, 1), null))
                .isInstanceOf(IllegalArgumentException.class);
        e2.transferOut(LocalDate.of(2026, 12, 1), "Transfert vers un lycée public");
        assertThat(e2.getStatus()).isEqualTo(EnrollmentStatus.TRANSFERRED);

        StudentEnrollment e3 = new StudentEnrollment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                EnrollmentType.NEW_ADMISSION,
                LocalDate.of(2026, 10, 1),
                null,
                false,
                null);
        assertThatThrownBy(() -> e3.cancel("")).isInstanceOf(IllegalArgumentException.class);
        e3.cancel("Erreur de saisie");
        assertThat(e3.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }
}
