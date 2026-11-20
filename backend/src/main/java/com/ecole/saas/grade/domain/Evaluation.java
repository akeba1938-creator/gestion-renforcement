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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "evaluations")
public class Evaluation extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "evaluation_period_id", nullable = false, updatable = false)
    private UUID evaluationPeriodId;

    @Column(name = "class_room_id", nullable = false, updatable = false)
    private UUID classRoomId;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "class_subject_id", nullable = false, updatable = false)
    private UUID classSubjectId;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_type", nullable = false, length = 25)
    private EvaluationType evaluationType;

    @Column(name = "evaluation_date", nullable = false)
    private LocalDate evaluationDate;

    @Column(name = "max_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "weight", nullable = false)
    private int weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EvaluationStatus status;

    @Column(name = "validated_by")
    private UUID validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "locked_by")
    private UUID lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "notes", length = 500)
    private String notes;

    protected Evaluation() {
    }

    public Evaluation(
            UUID academicYearId,
            UUID evaluationPeriodId,
            UUID classRoomId,
            UUID subjectId,
            UUID classSubjectId,
            UUID teacherId,
            String title,
            EvaluationType evaluationType,
            LocalDate evaluationDate,
            BigDecimal maxScore,
            int schoolGradingScale,
            int weight,
            UUID createdBy,
            String notes) {
        this.academicYearId = Objects.requireNonNull(academicYearId, "L'année scolaire est obligatoire.");
        this.evaluationPeriodId = Objects.requireNonNull(evaluationPeriodId, "La période d'évaluation est obligatoire.");
        this.classRoomId = Objects.requireNonNull(classRoomId, "La classe est obligatoire.");
        this.subjectId = Objects.requireNonNull(subjectId, "La matière est obligatoire.");
        this.classSubjectId = Objects.requireNonNull(classSubjectId, "La matière de classe est obligatoire.");
        this.teacherId = teacherId;
        this.title = requireTitle(title);
        this.evaluationType = Objects.requireNonNull(evaluationType, "Le type d'évaluation est obligatoire.");
        this.evaluationDate = Objects.requireNonNull(evaluationDate, "La date d'évaluation est obligatoire.");
        this.maxScore = validateMaxScore(maxScore, schoolGradingScale);
        this.weight = validateWeight(weight);
        this.createdBy = Objects.requireNonNull(createdBy, "L'auteur de l'évaluation est obligatoire.");
        this.notes = normalizeNotes(notes);
        this.status = EvaluationStatus.DRAFT;
    }

    public void updateDetails(
            String title,
            EvaluationType evaluationType,
            LocalDate evaluationDate,
            BigDecimal maxScore,
            int schoolGradingScale,
            int weight,
            String notes) {
        assertEditable();
        this.title = requireTitle(title);
        this.evaluationType = Objects.requireNonNull(evaluationType, "Le type d'évaluation est obligatoire.");
        this.evaluationDate = Objects.requireNonNull(evaluationDate, "La date d'évaluation est obligatoire.");
        this.maxScore = validateMaxScore(maxScore, schoolGradingScale);
        this.weight = validateWeight(weight);
        this.notes = normalizeNotes(notes);
    }

    public void validate(UUID actorId, Instant now) {
        if (this.status != EvaluationStatus.DRAFT) {
            throw new GradeConflictException(
                    "invalid-evaluation-state",
                    "Transition d'état impossible",
                    "Seule une évaluation en brouillon (DRAFT) peut être validée.");
        }
        this.status = EvaluationStatus.VALIDATED;
        this.validatedBy = Objects.requireNonNull(actorId, "L'acteur de validation est obligatoire.");
        this.validatedAt = Objects.requireNonNull(now, "L'horodatage de validation est obligatoire.");
    }

    public void lock(UUID actorId, Instant now) {
        if (this.status != EvaluationStatus.DRAFT && this.status != EvaluationStatus.VALIDATED) {
            throw new GradeConflictException(
                    "invalid-evaluation-state",
                    "Transition d'état impossible",
                    "Impossible de verrouiller une évaluation annulée ou déjà verrouillée.");
        }
        this.status = EvaluationStatus.LOCKED;
        this.lockedBy = Objects.requireNonNull(actorId, "L'acteur de verrouillage est obligatoire.");
        this.lockedAt = Objects.requireNonNull(now, "L'horodatage de verrouillage est obligatoire.");
    }

    public void cancel(String reason) {
        if (this.status == EvaluationStatus.LOCKED || this.status == EvaluationStatus.CANCELLED) {
            throw new GradeConflictException(
                    "invalid-evaluation-state",
                    "Transition d'état impossible",
                    "Impossible d'annuler une évaluation verrouillée ou déjà annulée.");
        }
        if (reason != null && !reason.isBlank()) {
            this.notes = normalizeNotes(reason);
        }
        this.status = EvaluationStatus.CANCELLED;
    }

    public void assertEditable() {
        if (this.status == EvaluationStatus.LOCKED || this.status == EvaluationStatus.CANCELLED) {
            throw new GradeConflictException(
                    "evaluation-immutable",
                    "Évaluation figée",
                    "Une évaluation verrouillée ou annulée ne peut plus être modifiée.");
        }
    }

    public boolean isIncludedInAverages() {
        return this.status != EvaluationStatus.CANCELLED;
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

    public UUID getClassSubjectId() {
        return classSubjectId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public String getTitle() {
        return title;
    }

    public EvaluationType getEvaluationType() {
        return evaluationType;
    }

    public LocalDate getEvaluationDate() {
        return evaluationDate;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public int getWeight() {
        return weight;
    }

    public EvaluationStatus getStatus() {
        return status;
    }

    public UUID getValidatedBy() {
        return validatedBy;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public UUID getLockedBy() {
        return lockedBy;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public String getNotes() {
        return notes;
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new GradeValidationException("L'intitulé de l'évaluation est obligatoire.");
        }
        String trimmed = title.trim();
        if (trimmed.length() > 150) {
            throw new GradeValidationException("L'intitulé de l'évaluation ne doit pas dépasser 150 caractères.");
        }
        return trimmed;
    }

    private static BigDecimal validateMaxScore(BigDecimal maxScore, int schoolGradingScale) {
        GradingCalculator.validateGradingScale(schoolGradingScale);
        BigDecimal effective = maxScore != null ? maxScore : BigDecimal.valueOf(schoolGradingScale);
        BigDecimal schoolMax = BigDecimal.valueOf(schoolGradingScale);
        if (effective.compareTo(BigDecimal.ZERO) <= 0 || effective.compareTo(schoolMax) > 0) {
            throw new GradeValidationException(
                    "Le barème de l'évaluation doit être strictement positif et inférieur ou égal au barème de l'établissement ("
                            + schoolGradingScale + ").");
        }
        return effective.setScale(2, RoundingMode.HALF_UP);
    }

    private static int validateWeight(int weight) {
        if (weight <= 0 || weight > 20) {
            throw new GradeValidationException("Le poids de l'évaluation doit être compris entre 1 et 20.");
        }
        return weight;
    }

    private static String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String trimmed = notes.trim();
        if (trimmed.length() > 500) {
            throw new GradeValidationException("Les remarques ne doivent pas dépasser 500 caractères.");
        }
        return trimmed;
    }
}
