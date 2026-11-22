package com.ecole.saas.attendance.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "attendance_sessions")
public class AttendanceSession extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "evaluation_period_id")
    private UUID evaluationPeriodId;

    @Column(name = "class_room_id", nullable = false, updatable = false)
    private UUID classRoomId;

    @Column(name = "subject_id", updatable = false)
    private UUID subjectId;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_slot", nullable = false, length = 20)
    private AttendanceTimeSlot timeSlot;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "label", length = 120)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceSessionStatus status;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "locked_by")
    private UUID lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "notes", length = 500)
    private String notes;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AttendanceSession() {
    }

    public AttendanceSession(
            UUID academicYearId,
            UUID evaluationPeriodId,
            UUID classRoomId,
            UUID subjectId,
            UUID teacherId,
            LocalDate sessionDate,
            AttendanceTimeSlot timeSlot,
            LocalTime startTime,
            LocalTime endTime,
            String label,
            UUID recordedBy,
            String notes) {
        this.academicYearId = Objects.requireNonNull(academicYearId, "L'année scolaire est obligatoire.");
        this.evaluationPeriodId = evaluationPeriodId;
        this.classRoomId = Objects.requireNonNull(classRoomId, "La classe est obligatoire.");
        this.subjectId = subjectId;
        this.teacherId = teacherId;
        this.sessionDate = Objects.requireNonNull(sessionDate, "La date de la séance d'appel est obligatoire.");
        this.timeSlot = timeSlot != null ? timeSlot : AttendanceTimeSlot.MORNING;
        validateTimes(startTime, endTime);
        this.startTime = startTime;
        this.endTime = endTime;
        this.label = normalizeOptional(label, 120, "Le libellé de la séance");
        this.recordedBy = Objects.requireNonNull(recordedBy, "L'auteur de l'appel est obligatoire.");
        this.notes = normalizeOptional(notes, 500, "Les notes");
        this.status = AttendanceSessionStatus.OPEN;
    }

    public void submit(Instant now) {
        if (this.status != AttendanceSessionStatus.OPEN) {
            throw new AttendanceConflictException(
                    "invalid-attendance-session-state",
                    "Transition de séance impossible",
                    "Seule une feuille d'appel ouverte (OPEN) peut être soumise.");
        }
        this.status = AttendanceSessionStatus.SUBMITTED;
        this.submittedAt = Objects.requireNonNull(now, "L'horodatage de soumission est obligatoire.");
    }

    public void lock(UUID actorId, Instant now) {
        if (this.status != AttendanceSessionStatus.OPEN && this.status != AttendanceSessionStatus.SUBMITTED) {
            throw new AttendanceConflictException(
                    "invalid-attendance-session-state",
                    "Transition de séance impossible",
                    "Impossible de verrouiller une feuille d'appel déjà verrouillée ou annulée.");
        }
        this.status = AttendanceSessionStatus.LOCKED;
        this.lockedBy = Objects.requireNonNull(actorId, "L'acteur de verrouillage est obligatoire.");
        this.lockedAt = Objects.requireNonNull(now, "L'horodatage de verrouillage est obligatoire.");
    }

    public void cancel(String reason) {
        if (this.status == AttendanceSessionStatus.LOCKED || this.status == AttendanceSessionStatus.CANCELLED) {
            throw new AttendanceConflictException(
                    "invalid-attendance-session-state",
                    "Transition de séance impossible",
                    "Impossible d'annuler une feuille d'appel verrouillée ou déjà annulée.");
        }
        if (reason != null && !reason.isBlank()) {
            this.notes = normalizeOptional(reason, 500, "Le motif d'annulation");
        }
        this.status = AttendanceSessionStatus.CANCELLED;
    }

    public void assertRollCallEditable() {
        if (this.status == AttendanceSessionStatus.LOCKED || this.status == AttendanceSessionStatus.CANCELLED) {
            throw new AttendanceConflictException(
                    "attendance-session-immutable",
                    "Feuille d'appel figée",
                    "Une feuille d'appel verrouillée ou annulée ne peut plus être modifiée.");
        }
    }

    public void assertJustifiable() {
        if (this.status == AttendanceSessionStatus.LOCKED || this.status == AttendanceSessionStatus.CANCELLED) {
            throw new AttendanceConflictException(
                    "attendance-session-immutable",
                    "Feuille d'appel figée",
                    "Impossible de justifier une absence sur une feuille d'appel verrouillée ou annulée.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public UUID getEvaluationPeriodId() {
        return evaluationPeriodId;
    }

    public UUID getClassRoomId() {
        return classRoomId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public AttendanceTimeSlot getTimeSlot() {
        return timeSlot;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getLabel() {
        return label;
    }

    public AttendanceSessionStatus getStatus() {
        return status;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getLockedBy() {
        return lockedBy;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public String getNotes() {
        return notes;
    }

    public long getVersion() {
        return version;
    }

    private static void validateTimes(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new AttendanceValidationException(
                    "L'heure de fin du créneau doit être strictement postérieure à l'heure de début.");
        }
    }

    private static String normalizeOptional(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new AttendanceValidationException(label + " ne doit pas dépasser " + maxLength + " caractères.");
        }
        return trimmed;
    }
}
