package com.ecole.saas.grade.api;

import com.ecole.saas.grade.application.ReportCardService;
import com.ecole.saas.grade.domain.ReportCardStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calcul, consultation, appréciations, validation et verrouillage des bulletins de période.
 */
@RestController
@RequestMapping("/api/v1/grades/report-cards")
public class ReportCardController {

    private final ReportCardService reportCardService;

    public ReportCardController(ReportCardService reportCardService) {
        this.reportCardService = reportCardService;
    }

    @PostMapping("/compute")
    public ResponseEntity<List<ReportCardResponse>> computeClassReportCards(
            @Valid @RequestBody ComputeReportCardsRequest request) {
        List<ReportCardResponse> results = reportCardService
                .computeClassReportCards(request.classRoomId(), request.evaluationPeriodId())
                .stream()
                .map(ReportCardResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(results);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ReportCardResponse>> listReportCards(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID evaluationPeriodId,
            @RequestParam(required = false) UUID classRoomId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) ReportCardStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "rankInClass") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = GradePagination.of(
                page, size, sortBy, direction, GradePagination.REPORT_CARD_SORT_FIELDS, "rankInClass", Sort.Direction.ASC);
        Page<ReportCardResponse> cards = reportCardService
                .listReportCards(academicYearId, evaluationPeriodId, classRoomId, studentId, status, pageable)
                .map(ReportCardResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(cards));
    }

    @GetMapping("/{reportCardId}")
    public ResponseEntity<ReportCardResponse> getReportCard(@PathVariable UUID reportCardId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ReportCardResponse.from(reportCardService.getReportCard(reportCardId)));
    }

    @PutMapping("/{reportCardId}/subjects/{subjectId}/appreciation")
    public ResponseEntity<ReportCardResponse> updateSubjectAppreciation(
            @PathVariable UUID reportCardId,
            @PathVariable UUID subjectId,
            @Valid @RequestBody UpdateSubjectAppreciationRequest request,
            JwtAuthenticationToken authentication) {
        ReportCardService.ReportCardView updated = reportCardService.updateSubjectAppreciation(
                reportCardId,
                subjectId,
                request.teacherAppreciation(),
                GradeApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ReportCardResponse.from(updated));
    }

    @PutMapping("/{reportCardId}/appreciations")
    public ResponseEntity<ReportCardResponse> updateReportCardAppreciations(
            @PathVariable UUID reportCardId,
            @Valid @RequestBody UpdateReportCardAppreciationsRequest request) {
        ReportCardService.ReportCardView updated = reportCardService.updateReportCardAppreciations(
                reportCardId,
                request.homeroomAppreciation(),
                request.directorAppreciation(),
                request.councilDecision());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ReportCardResponse.from(updated));
    }

    @PostMapping("/{reportCardId}/validate")
    public ResponseEntity<ReportCardResponse> validateReportCard(
            @PathVariable UUID reportCardId,
            JwtAuthenticationToken authentication) {
        ReportCardService.ReportCardView validated = reportCardService.validateReportCard(
                reportCardId, GradeApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ReportCardResponse.from(validated));
    }

    @PostMapping("/{reportCardId}/lock")
    public ResponseEntity<ReportCardResponse> lockReportCard(
            @PathVariable UUID reportCardId,
            JwtAuthenticationToken authentication) {
        ReportCardService.ReportCardView locked = reportCardService.lockReportCard(
                reportCardId, GradeApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ReportCardResponse.from(locked));
    }
}
