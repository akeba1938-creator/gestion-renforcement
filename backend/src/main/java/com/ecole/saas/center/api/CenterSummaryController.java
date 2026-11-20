package com.ecole.saas.center.api;

import com.ecole.saas.center.application.CenterSummaryService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Synthèses de suivi pédagogique élève et charge/rémunération estimée des intervenants en mode centre.
 */
@RestController
@RequestMapping("/api/v1/center")
public class CenterSummaryController {

    private final CenterSummaryService summaryService;

    public CenterSummaryController(CenterSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/students/{studentId}/overview")
    public ResponseEntity<StudentCenterOverviewResponse> getStudentOverview(
            @PathVariable UUID studentId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID evaluationPeriodId) {
        CenterSummaryService.StudentCenterOverview overview = summaryService.getStudentOverview(
                studentId, fromDate, toDate, academicYearId, evaluationPeriodId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StudentCenterOverviewResponse.from(overview));
    }

    @GetMapping("/tutors/{teacherId}/workload")
    public ResponseEntity<TutorWorkloadResponse> getTutorWorkload(
            @PathVariable UUID teacherId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate) {
        CenterSummaryService.TutorWorkloadOverview overview =
                summaryService.getTutorWorkload(teacherId, fromDate, toDate);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(TutorWorkloadResponse.from(overview));
    }
}
