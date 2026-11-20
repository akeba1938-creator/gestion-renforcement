package com.ecole.saas.grade.api;

import com.ecole.saas.grade.application.ReportCardService;
import com.ecole.saas.grade.domain.CouncilDecision;
import com.ecole.saas.grade.domain.Mention;
import com.ecole.saas.grade.domain.ReportCardStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportCardResponse(
        UUID id,
        UUID academicYearId,
        UUID evaluationPeriodId,
        String periodCode,
        String periodName,
        UUID classRoomId,
        String classRoomCode,
        String classRoomName,
        UUID studentId,
        String studentMatricule,
        String studentFirstName,
        String studentLastName,
        int gradingScale,
        BigDecimal generalAverage,
        BigDecimal classAverage,
        BigDecimal minClassAverage,
        BigDecimal maxClassAverage,
        Integer rankInClass,
        int classSize,
        boolean tiedRank,
        BigDecimal totalWeightedScore,
        int totalCoefficients,
        Mention mention,
        CouncilDecision councilDecision,
        String homeroomAppreciation,
        String directorAppreciation,
        ReportCardStatus status,
        Instant computedAt,
        UUID validatedBy,
        Instant validatedAt,
        UUID lockedBy,
        Instant lockedAt,
        List<PeriodSubjectResultResponse> subjectResults) {

    public static ReportCardResponse from(ReportCardService.ReportCardView view) {
        return new ReportCardResponse(
                view.reportCard().getId(),
                view.reportCard().getAcademicYearId(),
                view.reportCard().getEvaluationPeriodId(),
                view.periodCode(),
                view.periodName(),
                view.reportCard().getClassRoomId(),
                view.classRoomCode(),
                view.classRoomName(),
                view.reportCard().getStudentId(),
                view.studentMatricule(),
                view.studentFirstName(),
                view.studentLastName(),
                view.reportCard().getGradingScale(),
                view.reportCard().getGeneralAverage(),
                view.reportCard().getClassAverage(),
                view.reportCard().getMinClassAverage(),
                view.reportCard().getMaxClassAverage(),
                view.reportCard().getRankInClass(),
                view.reportCard().getClassSize(),
                view.reportCard().isTiedRank(),
                view.reportCard().getTotalWeightedScore(),
                view.reportCard().getTotalCoefficients(),
                view.reportCard().getMention(),
                view.reportCard().getCouncilDecision(),
                view.reportCard().getHomeroomAppreciation(),
                view.reportCard().getDirectorAppreciation(),
                view.reportCard().getStatus(),
                view.reportCard().getComputedAt(),
                view.reportCard().getValidatedBy(),
                view.reportCard().getValidatedAt(),
                view.reportCard().getLockedBy(),
                view.reportCard().getLockedAt(),
                view.subjectResults().stream().map(PeriodSubjectResultResponse::from).toList());
    }
}
