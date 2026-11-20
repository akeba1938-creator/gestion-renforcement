package com.ecole.saas.grade.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "student_grades")
public class StudentGrade extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private UUID evaluationId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 25)
    private GradeAttendanceStatus attendanceStatus;

    @Column(name = "score", precision = 6, scale = 2)
    private BigDecimal score;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "entered_by", nullable = false, updatable = false)
    private UUID enteredBy;

    @Column(name = "entered_at", nullable = false, updatable = false)
    private Instant enteredAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected StudentGrade() {
    }

    public StudentGrade(
            UUID evaluationId,
            UUID studentId,
            GradeAttendanceStatus attendanceStatus,
            BigDecimal score,
            BigDecimal evaluationMaxScore,
            String comment,
            UUID enteredBy,
            Instant enteredAt) {
        this.evaluationId = Objects.requireNonNull(evaluationId, "L'évaluation est obligatoire.");
        this.studentId = Objects.requireNonNull(studentId, "L'élève est obligatoire.");
        this.enteredBy = Objects.requireNonNull(enteredBy, "L'auteur de la saisie est obligatoire.");
        this.enteredAt = Objects.requireNonNull(enteredAt, "L'horodatage de saisie est obligatoire.");
        applyGrade(attendanceStatus, score, evaluationMaxScore, comment);
    }

    public void updateGrade(
            GradeAttendanceStatus attendanceStatus,
            BigDecimal score,
            BigDecimal evaluationMaxScore,
            String comment,
            UUID updatedBy) {
        applyGrade(attendanceStatus, score, evaluationMaxScore, comment);
        this.updatedBy = Objects.requireNonNull(updatedBy, "L'auteur de la mise à jour est obligatoire.");
    }

    private void applyGrade(
            GradeAttendanceStatus attendanceStatus,
            BigDecimal score,
            BigDecimal evaluationMaxScore,
            String comment) {
        Objects.requireNonNull(attendanceStatus, "Le statut de présence à l'évaluation est obligatoire.");
        Objects.requireNonNull(evaluationMaxScore, "Le barème de l'évaluation est obligatoire.");
        if (attendanceStatus == GradeAttendanceStatus.PRESENT) {
            if (score == null) {
                throw new GradeValidationException("La note est obligatoire lorsque l'élève est présent.");
            }
            if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(evaluationMaxScore) > 0) {
                throw new GradeValidationException(
                        "La note doit être comprise entre 0 et " + evaluationMaxScore + ".");
            }
            this.score = score.setScale(2, RoundingMode.HALF_UP);
        } else {
            if (score != null) {
                throw new GradeValidationException(
                        "Aucune note ne doit être renseignée pour un élève absent ou dispensé.");
            }
            this.score = null;
        }
        this.attendanceStatus = attendanceStatus;
        this.comment = normalizeComment(comment);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEvaluationId() {
        return evaluationId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public GradeAttendanceStatus getAttendanceStatus() {
        return attendanceStatus;
    }

    public BigDecimal getScore() {
        return score;
    }

    public String getComment() {
        return comment;
    }

    public UUID getEnteredBy() {
        return enteredBy;
    }

    public Instant getEnteredAt() {
        return enteredAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    private static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String trimmed = comment.trim();
        if (trimmed.length() > 500) {
            throw new GradeValidationException("Le commentaire ne doit pas dépasser 500 caractères.");
        }
        return trimmed;
    }
}
