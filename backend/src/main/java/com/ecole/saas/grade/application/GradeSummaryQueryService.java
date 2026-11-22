package com.ecole.saas.grade.application;

import com.ecole.saas.grade.ClassGradeReportingBreakdown;
import com.ecole.saas.grade.ClassPeriodGradeStats;
import com.ecole.saas.grade.GradeReportingOverview;
import com.ecole.saas.grade.GradeSummaryPort;
import com.ecole.saas.grade.StudentPeriodGradeSummary;
import com.ecole.saas.grade.SubjectGradeSummary;
import com.ecole.saas.grade.domain.GradingCalculator;
import com.ecole.saas.grade.domain.GradingPolicy;
import com.ecole.saas.grade.domain.PeriodReportCard;
import com.ecole.saas.grade.domain.PeriodSubjectResult;
import com.ecole.saas.grade.domain.ReportCardStatus;
import com.ecole.saas.grade.infrastructure.PeriodReportCardRepository;
import com.ecole.saas.grade.infrastructure.PeriodSubjectResultRepository;
import com.ecole.saas.shared.domain.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
        List<PeriodReportCard> cards = reportCardRepository
                .findByStudentIdAndAcademicYearIdOrderByComputedAtAsc(studentId, academicYearId);
        if (cards.isEmpty()) {
            return List.of();
        }
        List<UUID> cardIds = cards.stream().map(PeriodReportCard::getId).toList();
        Map<UUID, List<PeriodSubjectResult>> subjectsByCard = subjectResultRepository
                .findByReportCardIdIn(cardIds)
                .stream()
                .collect(Collectors.groupingBy(PeriodSubjectResult::getReportCardId));
        for (List<PeriodSubjectResult> results : subjectsByCard.values()) {
            results.sort(Comparator.comparing(PeriodSubjectResult::getSubjectName, String.CASE_INSENSITIVE_ORDER));
        }
        return cards.stream()
                .map(card -> toSummary(card, subjectsByCard.getOrDefault(card.getId(), List.of())))
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

    @Override
    @Transactional(readOnly = true)
    public GradeReportingOverview getPeriodReportingOverview(
            UUID academicYearId, UUID evaluationPeriodId, Collection<UUID> classRoomIds) {
        TenantContext.requireCurrent();
        Set<UUID> scopedClasses = classRoomIds == null
                ? null
                : classRoomIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (scopedClasses != null && scopedClasses.isEmpty()) {
            return emptyGradeOverview(academicYearId, evaluationPeriodId);
        }

        List<PeriodReportCard> cards;
        if (evaluationPeriodId != null) {
            cards = scopedClasses != null
                    ? reportCardRepository.findByEvaluationPeriodIdAndClassRoomIdIn(evaluationPeriodId, scopedClasses)
                    : reportCardRepository.findByEvaluationPeriodId(evaluationPeriodId);
            if (academicYearId != null) {
                cards = cards.stream()
                        .filter(card -> academicYearId.equals(card.getAcademicYearId()))
                        .toList();
            }
        } else if (academicYearId != null) {
            cards = scopedClasses != null
                    ? reportCardRepository.findByAcademicYearIdAndClassRoomIdIn(academicYearId, scopedClasses)
                    : reportCardRepository.findByAcademicYearId(academicYearId);
        } else {
            return emptyGradeOverview(null, null);
        }

        if (cards.isEmpty()) {
            return emptyGradeOverview(academicYearId, evaluationPeriodId);
        }

        int gradingScale = cards.getFirst().getGradingScale();
        GradingPolicy policy = GradingPolicy.defaultSenegalPolicy();
        GradeBucket overall = computeGradeBucket(cards, gradingScale, policy);

        Map<UUID, List<PeriodReportCard>> byClass = new LinkedHashMap<>();
        for (PeriodReportCard card : cards) {
            byClass.computeIfAbsent(card.getClassRoomId(), ignored -> new ArrayList<>()).add(card);
        }

        List<ClassGradeReportingBreakdown> breakdowns = new ArrayList<>(byClass.size());
        for (Map.Entry<UUID, List<PeriodReportCard>> entry : byClass.entrySet()) {
            List<PeriodReportCard> classCards = entry.getValue();
            int classScale = classCards.getFirst().getGradingScale();
            GradeBucket bucket = computeGradeBucket(classCards, classScale, policy);
            breakdowns.add(new ClassGradeReportingBreakdown(
                    entry.getKey(),
                    classScale,
                    bucket.reportCardCount(),
                    bucket.rankedCount(),
                    bucket.passedCount(),
                    bucket.passRatePercent(),
                    bucket.average(),
                    bucket.minAverage(),
                    bucket.maxAverage(),
                    bucket.draftCount(),
                    bucket.validatedCount(),
                    bucket.lockedCount(),
                    bucket.mentionCounts()));
        }

        return new GradeReportingOverview(
                academicYearId,
                evaluationPeriodId,
                gradingScale,
                overall.reportCardCount(),
                overall.rankedCount(),
                overall.passedCount(),
                overall.passRatePercent(),
                overall.average(),
                overall.minAverage(),
                overall.maxAverage(),
                overall.draftCount(),
                overall.validatedCount(),
                overall.lockedCount(),
                overall.mentionCounts(),
                List.copyOf(breakdowns));
    }

    private static GradeReportingOverview emptyGradeOverview(UUID academicYearId, UUID evaluationPeriodId) {
        return new GradeReportingOverview(
                academicYearId,
                evaluationPeriodId,
                20,
                0,
                0,
                0,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                Map.of(),
                List.of());
    }

    private static GradeBucket computeGradeBucket(
            List<PeriodReportCard> cards, int gradingScale, GradingPolicy policy) {
        List<BigDecimal> averages = new ArrayList<>(cards.size());
        BigDecimal passingThreshold = BigDecimal.valueOf(gradingScale)
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        int passed = 0;
        int draft = 0;
        int validated = 0;
        int locked = 0;
        Map<String, Long> mentions = new LinkedHashMap<>();

        for (PeriodReportCard card : cards) {
            BigDecimal avg = card.getGeneralAverage();
            if (avg != null) {
                averages.add(avg);
                if (avg.compareTo(passingThreshold) >= 0) {
                    passed++;
                }
            }
            if (card.getStatus() == ReportCardStatus.DRAFT) {
                draft++;
            } else if (card.getStatus() == ReportCardStatus.VALIDATED) {
                validated++;
            } else if (card.getStatus() == ReportCardStatus.LOCKED) {
                locked++;
            }
            String mentionName = card.getMention().name();
            mentions.merge(mentionName, 1L, Long::sum);
        }

        GradingCalculator.ClassAverageStats stats = GradingCalculator.computeClassStats(averages, policy);
        BigDecimal passRate = stats.rankedCount() == 0
                ? null
                : BigDecimal.valueOf(passed)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(stats.rankedCount()), 2, RoundingMode.HALF_UP);

        return new GradeBucket(
                cards.size(),
                stats.rankedCount(),
                passed,
                passRate,
                stats.classAverage(),
                stats.minAverage(),
                stats.maxAverage(),
                draft,
                validated,
                locked,
                Collections.unmodifiableMap(mentions));
    }

    private record GradeBucket(
            int reportCardCount,
            int rankedCount,
            int passedCount,
            BigDecimal passRatePercent,
            BigDecimal average,
            BigDecimal minAverage,
            BigDecimal maxAverage,
            int draftCount,
            int validatedCount,
            int lockedCount,
            Map<String, Long> mentionCounts) {
    }

    private StudentPeriodGradeSummary toSummary(PeriodReportCard card) {
        return toSummary(card, subjectResultRepository.findByReportCardIdOrderBySubjectNameAsc(card.getId()));
    }

    private static StudentPeriodGradeSummary toSummary(
            PeriodReportCard card, List<PeriodSubjectResult> subjectResults) {
        List<SubjectGradeSummary> subjects = subjectResults.stream()
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
