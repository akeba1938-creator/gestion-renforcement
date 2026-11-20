package com.ecole.saas.center.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CenterPoliciesAndCalculatorsTest {

    @Test
    void scheduleConflictCheckerShouldDetectOverlapsAndAllowAdjacentTimes() {
        UUID groupId = UUID.randomUUID();
        CenterScheduleSlot slot = new CenterScheduleSlot(
                groupId,
                UUID.randomUUID(),
                null,
                DayOfWeek.WEDNESDAY,
                LocalTime.of(15, 0),
                LocalTime.of(17, 0),
                "Salle 1",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31));
        setId(slot, UUID.randomUUID());

        // Créneau adjacent 17:00-19:00 : aucun conflit
        CenterScheduleConflictChecker.assertNoSlotConflict(
                null,
                DayOfWeek.WEDNESDAY,
                LocalTime.of(17, 0),
                LocalTime.of(19, 0),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31),
                List.of(slot),
                "Conflit");

        // Créneau qui chevauche 16:30-18:00 : conflit
        assertThatThrownBy(() -> CenterScheduleConflictChecker.assertNoSlotConflict(
                        null,
                        DayOfWeek.WEDNESDAY,
                        LocalTime.of(16, 30),
                        LocalTime.of(18, 0),
                        LocalDate.of(2026, 10, 15),
                        LocalDate.of(2026, 11, 15),
                        List.of(slot),
                        "Conflit horaire"))
                .isInstanceOf(CenterConflictException.class)
                .extracting(e -> ((CenterConflictException) e).getProblemCode())
                .isEqualTo("center-schedule-conflict");

        // Même horaire sur une période d'effet disjointe (janvier 2027) : aucun conflit
        CenterScheduleConflictChecker.assertNoSlotConflict(
                null,
                DayOfWeek.WEDNESDAY,
                LocalTime.of(15, 0),
                LocalTime.of(17, 0),
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 3, 31),
                List.of(slot),
                "Conflit");
    }

    @Test
    void sessionGeneratorShouldProduceDeterministicOccurrencesWithinGroupDates() {
        CenterGroup group = new CenterGroup(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                "BFEM-M",
                "Soutien Maths BFEM",
                null,
                CenterGroupType.EXAM_PREP,
                "Salle A",
                20,
                LocalDate.of(2026, 10, 5), // lundi
                LocalDate.of(2026, 10, 18));
        setId(group, UUID.randomUUID());

        CenterScheduleSlot wednesday = new CenterScheduleSlot(
                group.getId(), UUID.randomUUID(), null, DayOfWeek.WEDNESDAY,
                LocalTime.of(15, 0), LocalTime.of(17, 0), null, null, null);
        setId(wednesday, UUID.randomUUID());

        CenterScheduleSlot saturday = new CenterScheduleSlot(
                group.getId(), UUID.randomUUID(), null, DayOfWeek.SATURDAY,
                LocalTime.of(9, 0), LocalTime.of(11, 0), "Salle S", null, null);
        setId(saturday, UUID.randomUUID());

        List<CenterSessionGenerator.PlannedOccurrence> generated = CenterSessionGenerator.generateOccurrences(
                group,
                List.of(saturday, wednesday),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 25));

        // Semaine 1 (5-11 oct): mer 7, sam 10 ; Semaine 2 (12-18 oct): mer 14, sam 17 => 4 occurrences
        assertThat(generated).hasSize(4);
        assertThat(generated).extracting(CenterSessionGenerator.PlannedOccurrence::sessionDate)
                .containsExactly(
                        LocalDate.of(2026, 10, 7),
                        LocalDate.of(2026, 10, 10),
                        LocalDate.of(2026, 10, 14),
                        LocalDate.of(2026, 10, 17));
        assertThat(generated.get(0).roomName()).isEqualTo("Salle A");
        assertThat(generated.get(1).roomName()).isEqualTo("Salle S");

        assertThatThrownBy(() -> CenterSessionGenerator.generateOccurrences(
                        group, List.of(wednesday), LocalDate.of(2026, 10, 1), LocalDate.of(2027, 2, 1)))
                .isInstanceOf(CenterValidationException.class);
    }

    @Test
    void packConsumptionPolicyShouldBeIdempotentAndRefundOnAbsenceOrCancellation() {
        CenterSessionPack catalog = new CenterSessionPack(
                null, null, null, "PACK-4", "Pack 4", null, CenterPackBillingMode.SESSION_PACK, 4, 60, 20_000L);
        setId(catalog, UUID.randomUUID());

        CenterStudentPack studentPack = new CenterStudentPack(
                UUID.randomUUID(), catalog, null, null, null,
                LocalDate.of(2026, 10, 1), null, UUID.randomUUID(), null);
        setId(studentPack, UUID.randomUUID());

        CenterSessionAttendance attendance = new CenterSessionAttendance(
                UUID.randomUUID(), studentPack.getStudentId(), UUID.randomUUID(),
                CenterAttendanceStatus.PRESENT, null, 5, true, null, UUID.randomUUID());

        LocalDate sessionDate = LocalDate.of(2026, 10, 8);
        CenterPackConsumptionPolicy.reconcileAttendancePack(attendance, null, studentPack, sessionDate);
        assertThat(studentPack.getConsumedSessions()).isEqualTo(1);
        assertThat(attendance.getConsumedStudentPackId()).isEqualTo(studentPack.getId());

        // Re-sauvegarde en LATE : déjà consommé -> idempotent (reste à 1)
        attendance.updateAttendance(CenterAttendanceStatus.LATE, 10, 4, true, null, UUID.randomUUID());
        CenterPackConsumptionPolicy.reconcileAttendancePack(attendance, studentPack, studentPack, sessionDate);
        assertThat(studentPack.getConsumedSessions()).isEqualTo(1);

        // Correction en ABSENT -> restitution du crédit
        attendance.updateAttendance(CenterAttendanceStatus.ABSENT, null, null, false, null, UUID.randomUUID());
        CenterPackConsumptionPolicy.reconcileAttendancePack(attendance, studentPack, studentPack, sessionDate);
        assertThat(studentPack.getConsumedSessions()).isZero();
        assertThat(attendance.getConsumedStudentPackId()).isNull();

        // Re-passage en PRESENT puis annulation de séance -> restitution
        attendance.updateAttendance(CenterAttendanceStatus.PRESENT, null, 5, true, null, UUID.randomUUID());
        CenterPackConsumptionPolicy.reconcileAttendancePack(attendance, null, studentPack, sessionDate);
        assertThat(studentPack.getConsumedSessions()).isEqualTo(1);

        CenterPackConsumptionPolicy.refundOnSessionCancellation(attendance, studentPack, sessionDate);
        assertThat(studentPack.getConsumedSessions()).isZero();
        assertThat(attendance.getConsumedStudentPackId()).isNull();
    }

    @Test
    void participationCalculatorShouldComputeExactRatesAndIntegerFcfaRemuneration() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        List<CenterSessionAttendance> records = List.of(
                new CenterSessionAttendance(sessionId, studentId, enrollmentId, CenterAttendanceStatus.PRESENT, null, 5, true, null, actorId),
                new CenterSessionAttendance(sessionId, studentId, enrollmentId, CenterAttendanceStatus.LATE, 12, 4, true, null, actorId),
                new CenterSessionAttendance(sessionId, studentId, enrollmentId, CenterAttendanceStatus.ABSENT, null, null, false, null, actorId));

        CenterParticipationCalculator.ParticipationStats stats =
                CenterParticipationCalculator.computeParticipation(records);

        assertThat(stats.totalSessionsRecorded()).isEqualTo(3);
        assertThat(stats.presentCount()).isEqualTo(1);
        assertThat(stats.lateCount()).isEqualTo(1);
        assertThat(stats.totalLateMinutes()).isEqualTo(12);
        assertThat(stats.absentCount()).isEqualTo(1);
        assertThat(stats.attendanceRatePercent()).isEqualByComparingTo(new BigDecimal("66.67"));
        assertThat(stats.averageParticipationRating()).isEqualByComparingTo(new BigDecimal("4.50"));
        assertThat(stats.homeworkCompletionRatePercent()).isEqualByComparingTo(new BigDecimal("66.67"));

        // 90 minutes à 5 000 FCFA/h = 7 500 FCFA + 120 minutes à 4 000 FCFA/h = 8 000 FCFA => 15 500 FCFA exact
        long remunerationXof = CenterParticipationCalculator.computeEstimatedRemunerationXof(List.of(
                new CenterParticipationCalculator.CompletedSessionWork(90, 5_000L),
                new CenterParticipationCalculator.CompletedSessionWork(120, 4_000L)));
        assertThat(remunerationXof).isEqualTo(15_500L);
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
