package com.ecole.saas.student.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StudentTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    @Test
    void shouldFormatAndNormalizeMatricules() {
        assertThat(MatriculeFormatter.format("ind-dk", 2026, 1)).isEqualTo("IND-DK-2026-0001");
        assertThat(MatriculeFormatter.format("ECOLE", 2026, 42)).isEqualTo("ECOLE-2026-0042");
        assertThat(MatriculeFormatter.format("ECOLE", 2026, 12345)).isEqualTo("ECOLE-2026-12345");
        assertThat(MatriculeFormatter.normalize("  ind-2026-0001  ")).isEqualTo("IND-2026-0001");

        assertThatThrownBy(() -> MatriculeFormatter.format("ECOLE", 2026, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatriculeFormatter.format("ECOLE", 1999, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatriculeFormatter.format("bad code", 2026, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCreateNormalizeAndUpdateStudentProfile() {
        Student student = new Student(
                "ind-2026-0001",
                "  Awa  ",
                "  Diop ",
                StudentGender.FEMALE,
                LocalDate.of(2012, 5, 14),
                " Dakar ",
                " Sénégalaise ",
                " Sacré-Cœur 3 ",
                " +221771234567 ",
                " Awa.Diop@Ecole.SN ",
                " Allergie arachides ",
                LocalDate.of(2026, 10, 1),
                TODAY);

        assertThat(student.getMatricule()).isEqualTo("IND-2026-0001");
        assertThat(student.getFirstName()).isEqualTo("Awa");
        assertThat(student.getLastName()).isEqualTo("Diop");
        assertThat(student.getEmail()).isEqualTo("awa.diop@ecole.sn");
        assertThat(student.getPhone()).isEqualTo("+221771234567");
        assertThat(student.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(student.isActive()).isTrue();

        student.updateProfile(
                "Awa Marie",
                "Diop",
                StudentGender.FEMALE,
                LocalDate.of(2012, 5, 14),
                "Dakar",
                "Sénégalaise",
                "Mermoz",
                null,
                null,
                null,
                LocalDate.of(2026, 10, 2),
                TODAY);

        assertThat(student.getFirstName()).isEqualTo("Awa Marie");
        assertThat(student.getAddress()).isEqualTo("Mermoz");
        assertThat(student.getPhone()).isNull();
        assertThat(student.getEmail()).isNull();
    }

    @Test
    void shouldRejectInvalidBirthDateAdmissionDatePhoneOrEmail() {
        assertThatThrownBy(() -> new Student(
                        "IND-2026-0001",
                        "Moussa",
                        "Fall",
                        StudentGender.MALE,
                        TODAY.plusDays(1),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        TODAY,
                        TODAY))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Student(
                        "IND-2026-0001",
                        "Moussa",
                        "Fall",
                        StudentGender.MALE,
                        LocalDate.of(2015, 6, 1),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        LocalDate.of(2015, 5, 1),
                        TODAY))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Student(
                        "IND-2026-0001",
                        "Moussa",
                        "Fall",
                        StudentGender.MALE,
                        LocalDate.of(2012, 1, 1),
                        null,
                        null,
                        null,
                        "771234567",
                        null,
                        null,
                        TODAY,
                        TODAY))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Student(
                        "IND-2026-0001",
                        "Moussa",
                        "Fall",
                        StudentGender.MALE,
                        LocalDate.of(2012, 1, 1),
                        null,
                        null,
                        null,
                        null,
                        "not-an-email",
                        null,
                        TODAY,
                        TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldEnforceStudentStatusLifecycleAndImmutabilityAfterArchive() {
        Student student = new Student(
                "IND-2026-0002",
                "Ibrahima",
                "Ndiaye",
                StudentGender.MALE,
                LocalDate.of(2010, 3, 20),
                "Thiès",
                "Sénégalaise",
                null,
                null,
                null,
                null,
                TODAY,
                TODAY);

        assertThatThrownBy(() -> student.markTransferred("   ")).isInstanceOf(IllegalArgumentException.class);

        student.markTransferred("Déménagement à Saint-Louis");
        assertThat(student.getStatus()).isEqualTo(StudentStatus.TRANSFERRED);
        assertThat(student.isActive()).isFalse();
        assertThatThrownBy(student::assertActive).isInstanceOf(StudentNotActiveException.class);

        student.reactivate();
        assertThat(student.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(student.getStatusReason()).isNull();

        student.markGraduated("Baccalauréat S2 obtenu");
        assertThat(student.getStatus()).isEqualTo(StudentStatus.GRADUATED);

        student.reactivate();
        student.markWithdrawn("Départ volontaire");
        assertThat(student.getStatus()).isEqualTo(StudentStatus.WITHDRAWN);

        Instant archivedAt = Instant.parse("2026-10-20T12:00:00Z");
        student.archive("Clôture définitive du dossier", archivedAt);
        assertThat(student.getStatus()).isEqualTo(StudentStatus.ARCHIVED);
        assertThat(student.getArchivedAt()).isEqualTo(archivedAt);

        assertThatThrownBy(student::reactivate).isInstanceOf(InvalidStudentTransitionException.class);
        assertThatThrownBy(() -> student.archive("Double archivage", archivedAt))
                .isInstanceOf(InvalidStudentTransitionException.class);
        assertThatThrownBy(() -> student.updateProfile(
                        "Ibrahima",
                        "Ndiaye",
                        StudentGender.MALE,
                        LocalDate.of(2010, 3, 20),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        TODAY,
                        TODAY))
                .isInstanceOf(InvalidStudentTransitionException.class);
    }
}
