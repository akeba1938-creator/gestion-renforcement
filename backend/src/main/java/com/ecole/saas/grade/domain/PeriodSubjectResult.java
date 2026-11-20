package com.ecole.saas.grade.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "period_subject_results")
public class PeriodSubjectResult extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "report_card_id", nullable = false, updatable = false)
    private UUID reportCardId;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "class_subject_id", nullable = false)
    private UUID classSubjectId;

    @Column(name = "subject_code", nullable = false, length = 20)
    private String subjectCode;

    @Column(name = "subject_name", nullable = false, length = 120)
    private String subjectName;

    @Column(name = "coefficient", nullable = false)
    private int coefficient;

    @Column(name = "average_score", precision = 6, scale = 2)
    private BigDecimal averageScore;

    @Column(name = "class_subject_average", precision = 6, scale = 2)
    private BigDecimal classSubjectAverage;

    @Column(name = "rank_in_subject")
    private Integer rankInSubject;

    @Column(name = "evaluation_count", nullable = false)
    private int evaluationCount;

    @Column(name = "teacher_appreciation", length = 500)
    private String teacherAppreciation;

    @Column(name = "appreciated_by")
    private UUID appreciatedBy;

    @Column(name = "appreciated_at")
    private Instant appreciatedAt;

    protected PeriodSubjectResult() {
    }

    public PeriodSubjectResult(
            UUID reportCardId,
            UUID subjectId,
            UUID classSubjectId,
            String subjectCode,
            String subjectName,
            int coefficient,
            BigDecimal averageScore,
            BigDecimal classSubjectAverage,
            Integer rankInSubject,
            int evaluationCount) {
        this.reportCardId = Objects.requireNonNull(reportCardId, "Le bulletin est obligatoire.");
        this.subjectId = Objects.requireNonNull(subjectId, "La matière est obligatoire.");
        this.classSubjectId = Objects.requireNonNull(classSubjectId, "La matière de classe est obligatoire.");
        this.subjectCode = Objects.requireNonNull(subjectCode, "Le code matière est obligatoire.");
        this.subjectName = Objects.requireNonNull(subjectName, "Le nom matière est obligatoire.");
        this.coefficient = coefficient;
        this.averageScore = averageScore;
        this.classSubjectAverage = classSubjectAverage;
        this.rankInSubject = rankInSubject;
        this.evaluationCount = evaluationCount;
    }

    public void updateComputedResult(
            UUID classSubjectId,
            String subjectCode,
            String subjectName,
            int coefficient,
            BigDecimal averageScore,
            BigDecimal classSubjectAverage,
            Integer rankInSubject,
            int evaluationCount) {
        this.classSubjectId = Objects.requireNonNull(classSubjectId, "La matière de classe est obligatoire.");
        this.subjectCode = Objects.requireNonNull(subjectCode, "Le code matière est obligatoire.");
        this.subjectName = Objects.requireNonNull(subjectName, "Le nom matière est obligatoire.");
        this.coefficient = coefficient;
        this.averageScore = averageScore;
        this.classSubjectAverage = classSubjectAverage;
        this.rankInSubject = rankInSubject;
        this.evaluationCount = evaluationCount;
    }

    public void updateTeacherAppreciation(String appreciation, UUID actorId, Instant now) {
        if (appreciation == null || appreciation.isBlank()) {
            this.teacherAppreciation = null;
        } else {
            String trimmed = appreciation.trim();
            if (trimmed.length() > 500) {
                throw new GradeValidationException("L'appréciation matière ne doit pas dépasser 500 caractères.");
            }
            this.teacherAppreciation = trimmed;
        }
        this.appreciatedBy = Objects.requireNonNull(actorId, "L'auteur de l'appréciation est obligatoire.");
        this.appreciatedAt = Objects.requireNonNull(now, "L'horodatage de l'appréciation est obligatoire.");
    }

    public UUID getId() {
        return id;
    }

    public UUID getReportCardId() {
        return reportCardId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public UUID getClassSubjectId() {
        return classSubjectId;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public int getCoefficient() {
        return coefficient;
    }

    public BigDecimal getAverageScore() {
        return averageScore;
    }

    public BigDecimal getClassSubjectAverage() {
        return classSubjectAverage;
    }

    public Integer getRankInSubject() {
        return rankInSubject;
    }

    public int getEvaluationCount() {
        return evaluationCount;
    }

    public String getTeacherAppreciation() {
        return teacherAppreciation;
    }

    public UUID getAppreciatedBy() {
        return appreciatedBy;
    }

    public Instant getAppreciatedAt() {
        return appreciatedAt;
    }
}
