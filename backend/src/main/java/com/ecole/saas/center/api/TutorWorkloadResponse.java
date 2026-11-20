package com.ecole.saas.center.api;

import com.ecole.saas.center.TutorCenterWorkloadSummary;
import com.ecole.saas.center.application.CenterSummaryService;
import java.util.UUID;

public record TutorWorkloadResponse(
        UUID teacherId,
        String employeeCode,
        String firstName,
        String lastName,
        TutorCenterWorkloadSummary workload) {

    static TutorWorkloadResponse from(CenterSummaryService.TutorWorkloadOverview overview) {
        return new TutorWorkloadResponse(
                overview.teacher().id(),
                overview.teacher().employeeCode(),
                overview.teacher().firstName(),
                overview.teacher().lastName(),
                overview.workload());
    }
}
