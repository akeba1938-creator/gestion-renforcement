package com.ecole.saas.center.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Séance concrète datée d'une cohorte de renforcement (créée manuellement ou générée depuis un créneau).
 */
@Entity
@Table(name = "center_sessions")
public class CenterSession extends TenantScopedEntity {

    public static final int MAX_DURATION_MINUTES = 720;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "schedule_slot_id")
    private UUID scheduleSlotId;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "room_name", length = 60)
    private String roomName;

    @Column(name = "topic", length = 200)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CenterSessionStatus status = CenterSessionStatus.SCHEDULED;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_reason", length = 255)
    private String cancelledReason;

    @Column(name = "notes", length = 500)
    private String notes;

    protected CenterSession() {
    }

    public CenterSession(
            UUID groupId,
            UUID scheduleSlotId,
            UUID teacherId,
            UUID subjectId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String roomName,
            String topic,
            String notes,
            UUID recordedBy) {
        this.groupId = Objects.requireNonNull(groupId, "La cohorte est obligatoire.");
        this.scheduleSlotId = scheduleSlotId;
        this.teacherId = teacherId;
        this.subjectId = subjectId;
        if (sessionDate == null) {
            throw new CenterValidationException(
                    "center-session-invalid", "Date obligatoire", "La date de la séance est obligatoire.");
        }
        int computedDuration = computeDurationMinutes(startTime, endTime);
        this.sessionDate = sessionDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = computedDuration;
        this.roomName = normalizeOptional(roomName, 60);
        this.topic = normalizeOptional(topic, 200);
        this.notes = normalizeOptional(notes, 500);
        this.recordedBy = Objects.requireNonNull(recordedBy, "L'acteur de création est obligatoire.");
        this.status = CenterSessionStatus.SCHEDULED;
    }

    public void start() {
        if (this.status != CenterSessionStatus.SCHEDULED) {
            throw new CenterConflictException(
                    "center-session-invalid-transition",
                    "Transition de séance invalide",
                    "Seule une séance planifiée (SCHEDULED) peut être démarrée (statut actuel : " + this.status + ").");
        }
        this.status = CenterSessionStatus.IN_PROGRESS;
    }

    public void complete(UUID actorId, Instant now, String topicOverride, String notesOverride) {
        if (this.status != CenterSessionStatus.SCHEDULED && this.status != CenterSessionStatus.IN_PROGRESS) {
            throw new CenterConflictException(
                    "center-session-invalid-transition",
                    "Transition de séance invalide",
                    "Seule une séance SCHEDULED ou IN_PROGRESS peut être clôturée (statut actuel : " + this.status + ").");
        }
        if (topicOverride != null && !topicOverride.isBlank()) {
            this.topic = normalizeOptional(topicOverride, 200);
        }
        if (notesOverride != null && !notesOverride.isBlank()) {
            this.notes = normalizeOptional(notesOverride, 500);
        }
        this.status = CenterSessionStatus.COMPLETED;
        this.completedBy = Objects.requireNonNull(actorId, "L'acteur de clôture est obligatoire.");
        this.completedAt = Objects.requireNonNull(now, "L'horodatage de clôture est obligatoire.");
    }

    public void cancel(String reason) {
        if (this.status != CenterSessionStatus.SCHEDULED && this.status != CenterSessionStatus.IN_PROGRESS) {
            throw new CenterConflictException(
                    "center-session-invalid-transition",
                    "Annulation impossible",
                    "Seule une séance SCHEDULED ou IN_PROGRESS peut être annulée (statut actuel : " + this.status + ").");
        }
        if (reason == null || reason.isBlank()) {
            throw new CenterValidationException(
                    "center-session-invalid",
                    "Motif obligatoire",
                    "Un motif est obligatoire pour annuler une séance.");
        }
        this.status = CenterSessionStatus.CANCELLED;
        this.cancelledReason = normalizeOptional(reason, 255);
    }

    public boolean isAttendanceMutable() {
        return this.status == CenterSessionStatus.SCHEDULED || this.status == CenterSessionStatus.IN_PROGRESS;
    }

    public static int computeDurationMinutes(LocalTime start, LocalTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new CenterValidationException(
                    "center-session-invalid",
                    "Horaires de séance invalides",
                    "L'heure de fin de la séance doit être strictement postérieure à l'heure de début.");
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < 1 || minutes > MAX_DURATION_MINUTES) {
            throw new CenterValidationException(
                    "center-session-invalid",
                    "Durée de séance invalide",
                    "La durée d'une séance doit être comprise entre 1 et " + MAX_DURATION_MINUTES + " minutes.");
        }
        return (int) minutes;
    }

    private static String normalizeOptional(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLen) {
            throw new CenterValidationException(
                    "center-session-invalid",
                    "Séance invalide",
                    "La longueur maximale autorisée (" + maxLen + " caractères) est dépassée.");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getScheduleSlotId() {
        return scheduleSlotId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getTopic() {
        return topic;
    }

    public CenterSessionStatus getStatus() {
        return status;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public UUID getCompletedBy() {
        return completedBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getCancelledReason() {
        return cancelledReason;
    }

    public String getNotes() {
        return notes;
    }
}
