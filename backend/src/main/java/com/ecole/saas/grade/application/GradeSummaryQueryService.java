package com.ecole.saas.grade.application;

import com.ecole.saas.grade.ClassPeriodGradeStats;
import com.ecole.saas.grade.GradeSummaryPort;
import com.ecole.saas.grade.StudentPeriodGradeSummary;
import com.ecole.saas.grade.SubjectGradeSummary;
import com.ecole.saas.grade.domain.GradingCalculator;
import com.ecole.saas.grade.domain.GradingPolicy;
import com.ecole.saas.grade.domain.PeriodReportCard;
import com.ecole.saas.grade.domain.PeriodSubjectResult;
import com.ecole.saas.grade.infrastructure.PeriodReportCardRepository;
import com.ecole.saas.grade.infrastructure.PeriodSubjectResultRepository;
import com.ecole.saas.shared.domain.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de la frontière publique {@link GradeSummaryPort} dans le contexte tenant courant.
 */
@Service
public class GradeSummaryQueryService implements GradeSummaryPort {

    private final PeriodReportCardRepository reportCardRepository;
    private final PeriodSubjectResultRepository subjectResultRepository;

    public GradeSummaryQueryService(
            PeriodReportCardRepository reportCardRepository,
            PeriodSubjectResultRepository subjectResultRepository) {
        this.reportCardRepository = reportCardRepository;
        this.subjectResultRepository = subjectResultRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentPeriodGradeSummary> findStudentPeriodSummary(UUID studentId, UUID evaluationPeriodId) {
        TenantContext.requireCurrent();
        if (studentId == null || evaluationPeriodId == null) {
            return Optional.empty();
        }
        return reportCardRepository
                .findByEvaluationPeriodIdAndStudentId(evaluationPeriodId, studentId)
                .map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentPeriodGradeSummary> findStudentYearSummaries(UUID studentId, UUID academicYearId) {
        TenantContext.requireCurrent();
        if (studentId == null || academicYearId == null) {
            return List.of();
        }
        return reportCardRepository
                .findByStudentIdAndAcademicYearIdOrderByComputedAtAsc(studentId, academicYearId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClassPeriodGradeStats> findClassPeriodStats(UUID classRoomId, UUID evaluationPeriodId) {
        TenantContext.requireCurrent();
        if (classRoomId == null || evaluationPeriodId == null) {
            return Optional.empty();
        }
        List<PeriodReportCard> cards = reportCardRepository
                .findByEvaluationPeriodIdAndClassRoomIdOrderByRankInClassAscStudentIdAsc(
                        evaluationPeriodId, classRoomId);
        if (cards.isEmpty()) {
            return Optional.empty();
        }
        List<BigDecimal> averages = cards.stream().map(PeriodReportCard::getGeneralAverage).toList();
        GradingCalculator.ClassAverageStats stats =
                GradingCalculator.computeClassStats(averages, GradingPolicy.defaultSenegalPolicy());
        return Optional.of(new ClassPeriodGradeStats(
                classRoomId,
                evaluationPeriodId,
                cards.getFirst().getGradingScale(),
                cards.size(),
                stats.rankedCount(),
                stats.classAverage(),
                stats.minAverage(),
                stats.maxAverage()));
    }

    private StudentPeriodGradeSummary toSummary(PeriodReportCard card) {
        List<SubjectGradeSummary> subjects = subjectResultRepository
                .findByReportCardIdOrderBySubjectNameAsc(card.getId())
                .stream()
                .map(GradeSummaryQueryService::toSubjectSummary)
                .toList();
        return new StudentPeriodGradeSummary(
                card.getId(),
                card.getAcademicYearId(),
                card.getEvaluationPeriodId(),
                card.getClassRoomId(),
                card.getStudentId(),
                card.getGradingScale(),
                card.getGeneralAverage(),
                card.getClassAverage(),
                card.getMinClassAverage(),
                card.getMaxClassAverage(),
                card.getRankInClass(),
                card.getClassSize(),
                card.isTiedRank(),
                card.getTotalCoefficients(),
                card.getMention().name(),
                card.getCouncilDecision().name(),
                card.getHomeroomAppreciation(),
                card.getDirectorAppreciation(),
                card.getStatus().name(),
                subjects);
    }

    private static SubjectGradeSummary toSubjectSummary(PeriodSubjectResult result) {
        return new SubjectGradeSummary(
                result.getSubjectId(),
                result.getSubjectCode(),
                result.getSubjectName(),
                result.getCoefficient(),
                result.getAverageScore(),
                result.getClassSubjectAverage(),
                result.getRankInSubject(),
                result.getEvaluationCount(),
                result.getTeacherAppreciation());
    }
}
