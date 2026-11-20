package com.ecole.saas.center.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CenterDomainEntitiesTest {

    @Test
    void sessionPackShouldNormalizeCodeAndValidateRules() {
        CenterSessionPack pack = new CenterSessionPack(
                UUID.randomUUID(),
                null,
                null,
                "  pack-bac-10 ",
                "Pack 10 séances BAC",
                "Préparation intensive",
                CenterPackBillingMode.SESSION_PACK,
                10,
                60,
                35_000L);

        assertThat(pack.getCode()).isEqualTo("PACK-BAC-10");
        assertThat(pack.getPriceXof()).isEqualTo(35_000L);
        assertThat(pack.isActive()).isTrue();

        pack.deactivate();
        assertThat(pack.isActive()).isFalse();
        pack.activate();
        assertThat(pack.isActive()).isTrue();

        assertThatThrownBy(() -> new CenterSessionPack(
                        null, null, null, "BAD!", "Nom", null, CenterPackBillingMode.SESSION_PACK, 10, null, 10_000L))
                .isInstanceOf(CenterValidationException.class);

        assertThatThrownBy(() -> new CenterSessionPack(
                        null, null, null, "PACK-01", "Nom", null, CenterPackBillingMode.SESSION_PACK, null, null, 10_000L))
                .isInstanceOf(CenterValidationException.class);

        assertThatThrownBy(() -> new CenterSessionPack(
                        null, null, null, "PACK-02", "Nom", null, CenterPackBillingMode.MONTHLY_FLAT, null, 30, -500L))
                .isInstanceOf(CenterValidationException.class);
    }

    @Test
    void studentPackShouldConsumeExhaustRefundAndEnforceDates() {
        CenterSessionPack catalog = new CenterSessionPack(
                null, null, null, "CARNET-2", "Carnet 2 séances", null, CenterPackBillingMode.SESSION_PACK, 2, 30, 15_000L);
        setId(catalog, UUID.randomUUID());

        CenterStudentPack studentPack = new CenterStudentPack(
                UUID.randomUUID(),
                catalog,
                null,
                null,
                null,
                LocalDate.of(2026, 10, 1),
                null,
                UUID.randomUUID(),
                null);

        assertThat(studentPack.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(studentPack.getRemainingSessions()).isEqualTo(2);

        studentPack.consumeOneSession(LocalDate.of(2026, 10, 5));
        assertThat(studentPack.getConsumedSessions()).isEqualTo(1);
        assertThat(studentPack.getRemainingSessions()).isEqualTo(1);
        assertThat(studentPack.getStatus()).isEqualTo(CenterStudentPackStatus.ACTIVE);

        studentPack.consumeOneSession(LocalDate.of(2026, 10, 10));
        assertThat(studentPack.getConsumedSessions()).isEqualTo(2);
        assertThat(studentPack.getRemainingSessions()).isEqualTo(0);
        assertThat(studentPack.getStatus()).isEqualTo(CenterStudentPackStatus.EXHAUSTED);

        assertThatThrownBy(() -> studentPack.consumeOneSession(LocalDate.of(2026, 10, 12)))
                .isInstanceOf(CenterConflictException.class)
                .extracting(e -> ((CenterConflictException) e).getProblemCode())
                .isEqualTo("center-pack-exhausted");

        studentPack.refundOneSession(LocalDate.of(2026, 10, 10));
        assertThat(studentPack.getConsumedSessions()).isEqualTo(1);
        assertThat(studentPack.getStatus()).isEqualTo(CenterStudentPackStatus.ACTIVE);

        assertThatThrownBy(() -> studentPack.consumeOneSession(LocalDate.of(2026, 11, 5)))
                .isInstanceOf(CenterConflictException.class)
                .extracting(e -> ((CenterConflictException) e).getProblemCode())
                .isEqualTo("center-pack-expired");

        studentPack.cancel("Changement de formule");
        assertThat(studentPack.getStatus()).isEqualTo(CenterStudentPackStatus.CANCELLED);
    }

    @Test
    void groupShouldEnforceLifecycleAndCapacityConstraint() {
        CenterGroup group = new CenterGroup(
                UUID.randomUUID(),
                null,
                null,
                null,
                "bac-s-1",
                "Prépa BAC S Groupe 1",
                null,
                CenterGroupType.EXAM_PREP,
                "Salle B2",
                15,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 6, 30));

        assertThat(group.getCode()).isEqualTo("BAC-S-1");
        assertThat(group.getStatus()).isEqualTo(CenterGroupStatus.DRAFT);

        assertThatThrownBy(() -> group.complete("Trop tôt"))
                .isInstanceOf(CenterConflictException.class);

        group.activate();
        assertThat(group.getStatus()).isEqualTo(CenterGroupStatus.ACTIVE);

        assertThatThrownBy(() -> group.updateDetails(
                        null, null, null, "Nom", null, CenterGroupType.EXAM_PREP, "Salle", 5,
                        LocalDate.of(2026, 10, 1), LocalDate.of(2027, 6, 30), 8))
                .isInstanceOf(CenterConflictException.class);

        group.complete("Fin des examens");
        assertThat(group.getStatus()).isEqualTo(CenterGroupStatus.COMPLETED);

        assertThatThrownBy(() -> group.archive(" ", Instant.now()))
                .isInstanceOf(CenterValidationException.class);

        group.archive("Archivage fin d'année", Instant.now());
        assertThat(group.getStatus()).isEqualTo(CenterGroupStatus.ARCHIVED);
        assertThat(group.getArchivedAt()).isNotNull();
    }

    @Test
    void sessionAndAttendanceShouldValidateTransitionsAndInvariants() {
        CenterSession session = new CenterSession(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(16, 0),
                LocalTime.of(18, 0),
                "Salle 1",
                "Fonctions dérivées",
                null,
                UUID.randomUUID());

        assertThat(session.getDurationMinutes()).isEqualTo(120);
        assertThat(session.isAttendanceMutable()).isTrue();

        session.start();
        assertThat(session.getStatus()).isEqualTo(CenterSessionStatus.IN_PROGRESS);

        CenterSessionAttendance attendance = new CenterSessionAttendance(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                CenterAttendanceStatus.LATE,
                15,
                4,
                true,
                "Bon investissement",
                UUID.randomUUID());
        assertThat(attendance.getLateMinutes()).isEqualTo(15);
        assertThat(attendance.getParticipationRating()).isEqualTo(4);

        assertThatThrownBy(() -> new CenterSessionAttendance(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        CenterAttendanceStatus.ABSENT, null, 3, null, null, UUID.randomUUID()))
                .isInstanceOf(CenterValidationException.class);

        assertThatThrownBy(() -> new CenterSessionAttendance(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        CenterAttendanceStatus.LATE, null, null, null, null, UUID.randomUUID()))
                .isInstanceOf(CenterValidationException.class);

        session.complete(UUID.randomUUID(), Instant.now(), null, "Séance terminée");
        assertThat(session.getStatus()).isEqualTo(CenterSessionStatus.COMPLETED);
        assertThat(session.isAttendanceMutable()).isFalse();

        assertThatThrownBy(() -> session.cancel("Trop tard"))
                .isInstanceOf(CenterConflictException.class);
    }

    @Test
    void enrollmentAndScheduleSlotShouldValidateDatesAndTimes() {
        CenterGroupEnrollment enrollment = new CenterGroupEnrollment(
                UUID.randomUUID(), UUID.randomUUID(), null, LocalDate.of(2026, 10, 10), null);
        assertThatThrownBy(() -> enrollment.transitionStatus(
                        CenterGroupEnrollmentStatus.WITHDRAWN, LocalDate.of(2026, 10, 20), "  "))
                .isInstanceOf(CenterValidationException.class);

        enrollment.transitionStatus(
                CenterGroupEnrollmentStatus.WITHDRAWN, LocalDate.of(2026, 10, 20), "Déménagement");
        assertThat(enrollment.getStatus()).isEqualTo(CenterGroupEnrollmentStatus.WITHDRAWN);

        assertThatThrownBy(() -> new CenterScheduleSlot(
                        UUID.randomUUID(), null, null, DayOfWeek.WEDNESDAY,
                        LocalTime.of(17, 0), LocalTime.of(16, 0), null, null, null))
                .isInstanceOf(CenterValidationException.class);
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
