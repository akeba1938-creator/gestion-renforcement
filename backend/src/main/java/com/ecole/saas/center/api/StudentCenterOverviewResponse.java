package com.ecole.saas.center.api;

import com.ecole.saas.attendance.StudentAttendanceSummary;
import com.ecole.saas.center.StudentCenterSummary;
import com.ecole.saas.center.application.CenterSummaryService;
import com.ecole.saas.grade.StudentPeriodGradeSummary;
import java.util.List;
import java.util.UUID;

public record StudentCenterOverviewResponse(
        UUID studentId,
        String matricule,
        String firstName,
        String lastName,
        StudentCenterSummary centerSummary,
        List<CenterStudentPackResponse> packs,
        StudentPeriodGradeSummary periodGradeSummary,
        StudentAttendanceSummary schoolAttendanceSummary) {

    static StudentCenterOverviewResponse from(CenterSummaryService.StudentCenterOverview overview) {
        List<CenterStudentPackResponse> packResponses = overview.packs().stream()
                .map(CenterStudentPackResponse::fromEntity)
                .toList();
        return new StudentCenterOverviewResponse(
                overview.student().id(),
                overview.student().matricule(),
                overview.student().firstName(),
                overview.student().lastName(),
                overview.centerSummary(),
                packResponses,
                overview.periodGradeSummary(),
                overview.schoolAttendanceSummary());
    }
}
