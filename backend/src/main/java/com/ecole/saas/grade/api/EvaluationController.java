package com.ecole.saas.grade.api;

import com.ecole.saas.grade.application.EvaluationService;
import com.ecole.saas.grade.domain.EvaluationStatus;
import jakarta.validation.Valid;
import java.net.URI;
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
 * Gestion des évaluations et saisie/validation des notes (tenant issu exclusivement du JWT).
 */
@RestController
@RequestMapping("/api/v1/grades/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<EvaluationResponse>> listEvaluations(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID evaluationPeriodId,
            @RequestParam(required = false) UUID classRoomId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) EvaluationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "evaluationDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = GradePagination.of(
                page, size, sortBy, direction, GradePagination.EVALUATION_SORT_FIELDS, "evaluationDate", Sort.Direction.DESC);
        Page<EvaluationResponse> evaluations = evaluationService
                .listEvaluations(academicYearId, evaluationPeriodId, classRoomId, subjectId, status, pageable)
                .map(EvaluationResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(evaluations));
    }

    @GetMapping("/{evaluationId}")
    public ResponseEntity<EvaluationResponse> getEvaluation(@PathVariable UUID evaluationId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EvaluationResponse.from(evaluationService.getEvaluation(evaluationId)));
    }

    @PostMapping
    public ResponseEntity<EvaluationResponse> createEvaluation(
            @Valid @RequestBody CreateEvaluationRequest request,
            JwtAuthenticationToken authentication) {
        EvaluationService.EvaluationView created = evaluationService.createEvaluation(
                request.evaluationPeriodId(),
                request.classRoomId(),
                request.subjectId(),
                request.teacherId(),
                request.title(),
                request.evaluationType(),
                request.evaluationDate(),
                request.maxScore(),
                request.weight(),
                request.notes(),
                GradeApiSupport.extractActorId(authentication));
        return ResponseEntity.created(URI.create("/api/v1/grades/evaluations/" + created.evaluation().getId()))
                .cacheControl(CacheControl.noStore())
                .body(EvaluationResponse.from(created));
    }

    @PutMapping("/{evaluationId}")
    public ResponseEntity<EvaluationResponse> updateEvaluation(
            @PathVariable UUID evaluationId,
            @Valid @RequestBody UpdateEvaluationRequest request) {
        EvaluationService.EvaluationView updated = evaluationService.updateEvaluation(
                evaluationId,
                request.title(),
                request.evaluationType(),
                request.evaluationDate(),
                request.maxScore(),
                request.weight(),
                request.notes());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EvaluationResponse.from(updated));
    }

    @PostMapping("/{evaluationId}/validate")
    public ResponseEntity<EvaluationResponse> validateEvaluation(
            @PathVariable UUID evaluationId,
            JwtAuthenticationToken authentication) {
        EvaluationService.EvaluationView validated = evaluationService.validateEvaluation(
                evaluationId, GradeApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EvaluationResponse.from(validated));
    }

    @PostMapping("/{evaluationId}/lock")
    public ResponseEntity<EvaluationResponse> lockEvaluation(
            @PathVariable UUID evaluationId,
            JwtAuthenticationToken authentication) {
        EvaluationService.EvaluationView locked = evaluationService.lockEvaluation(
                evaluationId, GradeApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EvaluationResponse.from(locked));
    }

    @PostMapping("/{evaluationId}/cancel")
    public ResponseEntity<EvaluationResponse> cancelEvaluation(
            @PathVariable UUID evaluationId,
            @Valid @RequestBody(required = false) CancelEvaluationRequest request) {
        EvaluationService.EvaluationView cancelled = evaluationService.cancelEvaluation(
                evaluationId, request != null ? request.reason() : null);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EvaluationResponse.from(cancelled));
    }

    @GetMapping("/{evaluationId}/entries")
    public ResponseEntity<List<StudentGradeResponse>> listGrades(@PathVariable UUID evaluationId) {
        List<StudentGradeResponse> entries = evaluationService.listGrades(evaluationId).stream()
                .map(StudentGradeResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(entries);
    }

    @PutMapping("/{evaluationId}/entries")
    public ResponseEntity<List<StudentGradeResponse>> upsertGrades(
            @PathVariable UUID evaluationId,
            @Valid @RequestBody UpsertGradesRequest request,
            JwtAuthenticationToken authentication) {
        List<EvaluationService.StudentGradeInput> inputs = request.entries().stream()
                .map(item -> new EvaluationService.StudentGradeInput(
                        item.studentId(), item.attendanceStatus(), item.score(), item.comment()))
                .toList();
        List<StudentGradeResponse> saved = evaluationService
                .upsertGrades(evaluationId, inputs, GradeApiSupport.extractActorId(authentication))
                .stream()
                .map(StudentGradeResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(saved);
    }
}
