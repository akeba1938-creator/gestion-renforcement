package com.ecole.saas.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttendanceDomainAndSummaryTest {

    @Test
    void shouldValidateSessionTimesLifecycleAndRecordRules() {
        UUID actor = UUID.randomUUID();
        assertThatThrownBy(() -> new AttendanceSession(
                        UUID.randomUUID(),
                        null,
                        UUID.randomUUID(),
                        null,
                        null,
                        LocalDate.of(2026, 11, 12),
                        AttendanceTimeSlot.CUSTOM,
                        LocalTime.of(10, 0),
                        LocalTime.of(9, 0),
                        "Cours",
                        actor,
                        null))
                .isInstanceOf(AttendanceValidationException.class);

        AttendanceSession session = new AttendanceSession(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                null,
                LocalDate.of(2026, 11, 12),
                AttendanceTimeSlot.MORNING,
                LocalTime.of(8, 0),
                LocalTime.of(10, 0),
                "Appel du matin",
                actor,
                null);
        session.submit(Instant.now());
        assertThat(session.getStatus()).isEqualTo(AttendanceSessionStatus.SUBMITTED);
        session.lock(actor, Instant.now());
        assertThat(session.getStatus()).isEqualTo(AttendanceSessionStatus.LOCKED);
        assertThatThrownBy(session::assertRollCallEditable).isInstanceOf(AttendanceConflictException.class);
        assertThatThrownBy(session::assertJustifiable).isInstanceOf(AttendanceConflictException.class);

        // Retard sans minutes -> rejeté
        assertThatThrownBy(() -> new AttendanceRecord(
                        UUID.randomUUID(), UUID.randomUUID(), AttendanceRecordStatus.LATE, null, null, actor))
                .isInstanceOf(AttendanceValidationException.class);

        // Présent avec minutes de retard -> rejeté
        assertThatThrownBy(() -> new AttendanceRecord(
                        UUID.randomUUID(), UUID.randomUUID(), AttendanceRecordStatus.PRESENT, 10, null, actor))
                .isInstanceOf(AttendanceValidationException.class);
    }

    @Test
    void shouldJustifyAbsenceRejectPathTraversalAndComputeSummaryMetrics() {
        UUID actor = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        AttendanceRecord r1 = new AttendanceRecord(
                sessionId, studentId, AttendanceRecordStatus.PRESENT, null, null, actor);
        AttendanceRecord r2 = new AttendanceRecord(
                sessionId, studentId, AttendanceRecordStatus.LATE, 15, "Embouteillage", actor);
        AttendanceRecord r3 = new AttendanceRecord(
                sessionId, studentId, AttendanceRecordStatus.ABSENT, null, null, actor);
        AttendanceRecord r4 = new AttendanceRecord(
                sessionId, studentId, AttendanceRecordStatus.ABSENT, null, null, actor);
        AttendanceRecord r5 = new AttendanceRecord(
                sessionId, studentId, AttendanceRecordStatus.EXCUSED, null, "Sortie scolaire", actor);

        // Rejet traversée de chemin sur référence de pièce justificative
        assertThatThrownBy(() -> r3.justify("Certificat médical", "../etc/passwd", actor, Instant.now()))
                .isInstanceOf(AttendanceValidationException.class);

        // Rejet justification d'un présent
        assertThatThrownBy(() -> r1.justify("Motif", null, actor, Instant.now()))
                .isInstanceOf(AttendanceConflictException.class);

        r3.justify("Certificat médical", "tenants/justifs/cert-2026-01.pdf", actor, Instant.now());
        assertThat(r3.isJustified()).isTrue();
        assertThat(r3.getJustificationDocumentRef()).isEqualTo("tenants/justifs/cert-2026-01.pdf");

        AttendanceSummaryCalculator.AttendanceMetrics metrics =
                AttendanceSummaryCalculator.compute(List.of(r1, r2, r3, r4, r5));
        assertThat(metrics.totalSessions()).isEqualTo(5);
        assertThat(metrics.presentCount()).isEqualTo(1);
        assertThat(metrics.lateCount()).isEqualTo(1);
        assertThat(metrics.totalLateMinutes()).isEqualTo(15);
        assertThat(metrics.absentCount()).isEqualTo(2);
        assertThat(metrics.justifiedAbsenceCount()).isEqualTo(1);
        assertThat(metrics.unjustifiedAbsenceCount()).isEqualTo(1);
        assertThat(metrics.excusedCount()).isEqualTo(1);
        // (1 present + 1 late) / 5 = 40.00%
        assertThat(metrics.attendanceRatePercent()).isEqualByComparingTo("40.00");
    }
}
