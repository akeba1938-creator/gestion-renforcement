package com.ecole.saas.academic.api;

import com.ecole.saas.academic.application.EvaluationPeriodService;
import com.ecole.saas.academic.domain.EvaluationPeriod;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion des périodes d'évaluation (trimestres, semestres ou périodes personnalisées)
 * de l'établissement courant.
 */
@RestController
@RequestMapping("/api/v1/academic")
public class EvaluationPeriodController {

    private final EvaluationPeriodService periodService;

    public EvaluationPeriodController(EvaluationPeriodService periodService) {
        this.periodService = periodService;
    }

    @GetMapping("/years/{yearId}/periods")
    public ResponseEntity<PageResponse<EvaluationPeriodResponse>> listPeriodsForYear(
            @PathVariable UUID yearId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = AcademicPagination.ofFixedOrder(page, size);
        Page<EvaluationPeriodResponse> periods = periodService.listPeriodsForYear(yearId, pageable)
                .map(EvaluationPeriodResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(periods));
    }

    @PostMapping("/years/{yearId}/periods")
    public ResponseEntity<EvaluationPeriodResponse> createPeriod(
            @PathVariable UUID yearId,
            @Valid @RequestBody CreateEvaluationPeriodRequest request) {
        EvaluationPeriod created = periodService.createPeriod(
                yearId,
                request.code(),
                request.name(),
                request.periodType(),
                request.sequenceOrder(),
                request.startDate(),
                request.endDate(),
                request.weight());
        return ResponseEntity.created(URI.create("/api/v1/academic/periods/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(EvaluationPeriodResponse.from(created));
    }

    @PostMapping("/years/{yearId}/periods/generate-standard")
    public ResponseEntity<List<EvaluationPeriodResponse>> generateStandardPeriods(@PathVariable UUID yearId) {
        List<EvaluationPeriodResponse> periods = periodService.generateStandardPeriods(yearId).stream()
                .map(EvaluationPeriodResponse::from)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(periods);
    }

    @GetMapping("/periods/{periodId}")
    public ResponseEntity<EvaluationPeriodResponse> getPeriod(@PathVariable UUID periodId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EvaluationPeriodResponse.from(periodService.getPeriod(periodId)));
    }

    @PutMapping("/periods/{periodId}")
    public ResponseEntity<EvaluationPeriodResponse> updatePeriod(
            @PathVariable UUID periodId,
            @Valid @RequestBody UpdateEvaluationPeriodRequest request) {
        EvaluationPeriod updated = periodService.updatePeriod(
                periodId,
                request.name(),
                request.sequenceOrder(),
                request.startDate(),
                request.endDate(),
                request.weight());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EvaluationPeriodResponse.from(updated));
    }

    @PostMapping("/periods/{periodId}/open")
    public ResponseEntity<EvaluationPeriodResponse> openPeriod(@PathVariable UUID periodId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EvaluationPeriodResponse.from(periodService.openPeriod(periodId)));
    }

    @PostMapping("/periods/{periodId}/close")
    public ResponseEntity<EvaluationPeriodResponse> closePeriod(@PathVariable UUID periodId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EvaluationPeriodResponse.from(periodService.closePeriod(periodId)));
    }

    @PostMapping("/periods/{periodId}/reopen")
    public ResponseEntity<EvaluationPeriodResponse> reopenPeriod(@PathVariable UUID periodId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EvaluationPeriodResponse.from(periodService.reopenPeriod(periodId)));
    }

    @PostMapping("/periods/{periodId}/lock")
    public ResponseEntity<EvaluationPeriodResponse> lockPeriod(@PathVariable UUID periodId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EvaluationPeriodResponse.from(periodService.lockPeriod(periodId)));
    }
}
