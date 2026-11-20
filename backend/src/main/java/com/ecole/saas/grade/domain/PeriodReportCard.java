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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "period_report_cards")
public class PeriodReportCard extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "evaluation_period_id", nullable = false, updatable = false)
    private UUID evaluationPeriodId;

    @Column(name = "class_room_id", nullable = false)
    private UUID classRoomId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "grading_scale", nullable = false)
    private int gradingScale;

    @Column(name = "general_average", precision = 6, scale = 2)
    private BigDecimal generalAverage;

    @Column(name = "class_average", precision = 6, scale = 2)
    private BigDecimal classAverage;

    @Column(name = "min_class_average", precision = 6, scale = 2)
    private BigDecimal minClassAverage;

    @Column(name = "max_class_average", precision = 6, scale = 2)
    private BigDecimal maxClassAverage;

    @Column(name = "rank_in_class")
    private Integer rankInClass;

    @Column(name = "class_size", nullable = false)
    private int classSize;

    @Column(name = "tied_rank", nullable = false)
    private boolean tiedRank;

    @Column(name = "total_weighted_score", precision = 10, scale = 2)
    private BigDecimal totalWeightedScore;

    @Column(name = "total_coefficients", nullable = false)
    private int totalCoefficients;

    @Enumerated(EnumType.STRING)
    @Column(name = "mention", nullable = false, length = 30)
    private Mention mention;

    @Enumerated(EnumType.STRING)
    @Column(name = "council_decision", nullable = false, length = 30)
    private CouncilDecision councilDecision;

    @Column(name = "homeroom_appreciation", length = 1000)
    private String homeroomAppreciation;

    @Column(name = "director_appreciation", length = 1000)
    private String directorAppreciation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportCardStatus status;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "validated_by")
    private UUID validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "locked_by")
    private UUID lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    protected PeriodReportCard() {
    }

    public PeriodReportCard(
            UUID academicYearId,
            UUID evaluationPeriodId,
            UUID classRoomId,
            UUID studentId,
            int gradingScale,
            Instant computedAt) {
        this.academicYearId = Objects.requireNonNull(academicYearId, "L'année scolaire est obligatoire.");
        this.evaluationPeriodId = Objects.requireNonNull(evaluationPeriodId, "La période est obligatoire.");
        this.classRoomId = Objects.requireNonNull(classRoomId, "La classe est obligatoire.");
        this.studentId = Objects.requireNonNull(studentId, "L'élève est obligatoire.");
        GradingCalculator.validateGradingScale(gradingScale);
        this.gradingScale = gradingScale;
        this.mention = Mention.NONE;
        this.councilDecision = CouncilDecision.NONE;
        this.status = ReportCardStatus.DRAFT;
        this.computedAt = Objects.requireNonNull(computedAt, "La date de calcul est obligatoire.");
    }

    public void applyComputedResults(
            UUID classRoomId,
            int gradingScale,
            BigDecimal generalAverage,
            BigDecimal totalWeightedScore,
            int totalCoefficients,
            BigDecimal classAverage,
            BigDecimal minClassAverage,
            BigDecimal maxClassAverage,
            Integer rankInClass,
            int classSize,
            boolean tiedRank,
            Mention mention,
            Instant computedAt) {
        assertDraft();
        GradingCalculator.validateGradingScale(gradingScale);
        this.classRoomId = Objects.requireNonNull(classRoomId, "La classe est obligatoire.");
        this.gradingScale = gradingScale;
        this.generalAverage = generalAverage;
        this.totalWeightedScore = totalWeightedScore;
        this.totalCoefficients = totalCoefficients;
        this.classAverage = classAverage;
        this.minClassAverage = minClassAverage;
        this.maxClassAverage = maxClassAverage;
        this.rankInClass = rankInClass;
        this.classSize = classSize;
        this.tiedRank = tiedRank;
        this.mention = Objects.requireNonNull(mention, "La mention est obligatoire.");
        this.computedAt = Objects.requireNonNull(computedAt, "La date de calcul est obligatoire.");
    }

    public void updateHomeroomAppreciation(String homeroomAppreciation) {
        assertDraft();
        this.homeroomAppreciation = normalizeAppreciation(homeroomAppreciation);
    }

    public void updateDirectorAppreciationAndDecision(String directorAppreciation, CouncilDecision councilDecision) {
        assertNotLocked();
        if (directorAppreciation != null) {
            this.directorAppreciation = normalizeAppreciation(directorAppreciation);
        }
        if (councilDecision != null) {
            this.councilDecision = councilDecision;
        }
    }

    public void validate(UUID actorId, Instant now) {
        if (this.status != ReportCardStatus.DRAFT) {
            throw new GradeConflictException(
                    "invalid-report-card-state",
                    "Transition de bulletin impossible",
                    "Seul un bulletin en brouillon (DRAFT) peut être validé.");
        }
        this.status = ReportCardStatus.VALIDATED;
        this.validatedBy = Objects.requireNonNull(actorId, "L'acteur de validation est obligatoire.");
        this.validatedAt = Objects.requireNonNull(now, "L'horodatage de validation est obligatoire.");
    }

    public void lock(UUID actorId, Instant now) {
        if (this.status != ReportCardStatus.VALIDATED) {
            throw new GradeConflictException(
                    "invalid-report-card-state",
                    "Transition de bulletin impossible",
                    "Seul un bulletin validé (VALIDATED) peut être verrouillé.");
        }
        this.status = ReportCardStatus.LOCKED;
        this.lockedBy = Objects.requireNonNull(actorId, "L'acteur de verrouillage est obligatoire.");
        this.lockedAt = Objects.requireNonNull(now, "L'horodatage de verrouillage est obligatoire.");
    }

    public void assertDraft() {
        if (this.status != ReportCardStatus.DRAFT) {
            throw new GradeConflictException(
                    "report-card-immutable",
                    "Bulletin non modifiable",
                    "Ce bulletin n'est plus en brouillon (" + this.status + ") et ne peut pas être recalculé ni modifié.");
        }
    }

    public void assertNotLocked() {
        if (this.status == ReportCardStatus.LOCKED) {
            throw new GradeConflictException(
                    "report-card-locked",
                    "Bulletin verrouillé",
                    "Un bulletin verrouillé est strictement immuable.");
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

    public UUID getStudentId() {
        return studentId;
    }

    public int getGradingScale() {
        return gradingScale;
    }

    public BigDecimal getGeneralAverage() {
        return generalAverage;
    }

    public BigDecimal getClassAverage() {
        return classAverage;
    }

    public BigDecimal getMinClassAverage() {
        return minClassAverage;
    }

    public BigDecimal getMaxClassAverage() {
        return maxClassAverage;
    }

    public Integer getRankInClass() {
        return rankInClass;
    }

    public int getClassSize() {
        return classSize;
    }

    public boolean isTiedRank() {
        return tiedRank;
    }

    public BigDecimal getTotalWeightedScore() {
        return totalWeightedScore;
    }

    public int getTotalCoefficients() {
        return totalCoefficients;
    }

    public Mention getMention() {
        return mention;
    }

    public CouncilDecision getCouncilDecision() {
        return councilDecision;
    }

    public String getHomeroomAppreciation() {
        return homeroomAppreciation;
    }

    public String getDirectorAppreciation() {
        return directorAppreciation;
    }

    public ReportCardStatus getStatus() {
        return status;
    }

    public Instant getComputedAt() {
        return computedAt;
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

    private static String normalizeAppreciation(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() > 1000) {
            throw new GradeValidationException("L'appréciation ne doit pas dépasser 1000 caractères.");
        }
        return trimmed;
    }
}
