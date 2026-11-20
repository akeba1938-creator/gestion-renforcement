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
import java.util.Objects;
import java.util.UUID;

/**
 * Suivi individuel de présence, participation, devoirs et consommation de pack sur une séance de renforcement.
 */
@Entity
@Table(name = "center_session_attendances")
public class CenterSessionAttendance extends TenantScopedEntity {

    public static final int MAX_LATE_MINUTES = 300;
    public static final int MIN_PARTICIPATION_RATING = 1;
    public static final int MAX_PARTICIPATION_RATING = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(name = "consumed_student_pack_id")
    private UUID consumedStudentPackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CenterAttendanceStatus status;

    @Column(name = "late_minutes")
    private Integer lateMinutes;

    @Column(name = "participation_rating")
    private Integer participationRating;

    @Column(name = "homework_done")
    private Boolean homeworkDone;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected CenterSessionAttendance() {
    }

    public CenterSessionAttendance(
            UUID sessionId,
            UUID studentId,
            UUID enrollmentId,
            CenterAttendanceStatus status,
            Integer lateMinutes,
            Integer participationRating,
            Boolean homeworkDone,
            String remarks,
            UUID recordedBy) {
        this.sessionId = Objects.requireNonNull(sessionId, "La séance est obligatoire.");
        this.studentId = Objects.requireNonNull(studentId, "L'élève est obligatoire.");
        this.enrollmentId = Objects.requireNonNull(enrollmentId, "L'inscription cohorte est obligatoire.");
        this.recordedBy = Objects.requireNonNull(recordedBy, "L'acteur d'enregistrement est obligatoire.");
        applyAttendance(status, lateMinutes, participationRating, homeworkDone, remarks, recordedBy);
    }

    public void updateAttendance(
            CenterAttendanceStatus status,
            Integer lateMinutes,
            Integer participationRating,
            Boolean homeworkDone,
            String remarks,
            UUID actorId) {
        applyAttendance(status, lateMinutes, participationRating, homeworkDone, remarks, actorId);
    }

    public void markPackConsumed(UUID studentPackId) {
        this.consumedStudentPackId = Objects.requireNonNull(studentPackId, "L'identifiant du pack consommé est obligatoire.");
    }

    public void clearPackConsumed() {
        this.consumedStudentPackId = null;
    }

    private void applyAttendance(
            CenterAttendanceStatus newStatus,
            Integer newLateMinutes,
            Integer newParticipationRating,
            Boolean newHomeworkDone,
            String newRemarks,
            UUID actorId) {
        if (newStatus == null) {
            throw new CenterValidationException(
                    "center-attendance-invalid", "Statut de présence obligatoire", "Le statut de présence est obligatoire.");
        }
        if (newStatus == CenterAttendanceStatus.LATE) {
            if (newLateMinutes == null || newLateMinutes < 1 || newLateMinutes > MAX_LATE_MINUTES) {
                throw new CenterValidationException(
                        "center-attendance-invalid",
                        "Durée de retard invalide",
                        "Pour un retard (LATE), le nombre de minutes doit être compris entre 1 et "
                                + MAX_LATE_MINUTES + ".");
            }
            this.lateMinutes = newLateMinutes;
        } else {
            if (newLateMinutes != null) {
                throw new CenterValidationException(
                        "center-attendance-invalid",
                        "Durée de retard incohérente",
                        "Les minutes de retard ne peuvent être renseignées que pour le statut LATE.");
            }
            this.lateMinutes = null;
        }
        if (newParticipationRating != null
                && (newParticipationRating < MIN_PARTICIPATION_RATING
                        || newParticipationRating > MAX_PARTICIPATION_RATING)) {
            throw new CenterValidationException(
                    "center-attendance-invalid",
                    "Note de participation invalide",
                    "La note de participation doit être comprise entre "
                            + MIN_PARTICIPATION_RATING + " et " + MAX_PARTICIPATION_RATING + ".");
        }
        if (!newStatus.countsAsAttended() && newParticipationRating != null) {
            throw new CenterValidationException(
                    "center-attendance-invalid",
                    "Participation incohérente",
                    "Une note de participation ne peut être saisie que pour un élève PRESENT ou LATE.");
        }
        this.status = newStatus;
        this.participationRating = newParticipationRating;
        this.homeworkDone = newHomeworkDone;
        this.remarks = normalizeOptional(newRemarks, 500);
        this.updatedBy = Objects.requireNonNull(actorId, "L'acteur de mise à jour est obligatoire.");
    }

    private static String normalizeOptional(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLen) {
            throw new CenterValidationException(
                    "center-attendance-invalid",
                    "Pointage invalide",
                    "La longueur maximale autorisée (" + maxLen + " caractères) est dépassée.");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getEnrollmentId() {
        return enrollmentId;
    }

    public UUID getConsumedStudentPackId() {
        return consumedStudentPackId;
    }

    public CenterAttendanceStatus getStatus() {
        return status;
    }

    public Integer getLateMinutes() {
        return lateMinutes;
    }

    public Integer getParticipationRating() {
        return participationRating;
    }

    public Boolean getHomeworkDone() {
        return homeworkDone;
    }

    public String getRemarks() {
        return remarks;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
