package com.ecole.saas.academic.api;

import com.ecole.saas.academic.application.SubjectService;
import com.ecole.saas.academic.domain.Subject;
import com.ecole.saas.academic.domain.SubjectCategory;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
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
 * Gestion du référentiel des matières de l'établissement courant.
 */
@RestController
@RequestMapping("/api/v1/academic/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<SubjectResponse>> listSubjects(
            @RequestParam(required = false) SubjectCategory category,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = AcademicPagination.of(
                page, size, sortBy, direction, AcademicPagination.SUBJECT_SORT_FIELDS, "name", Sort.Direction.ASC);
        Page<SubjectResponse> subjects = subjectService.listSubjects(category, activeOnly, search, pageable)
                .map(SubjectResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(subjects));
    }

    @GetMapping("/{subjectId}")
    public ResponseEntity<SubjectResponse> getSubject(@PathVariable UUID subjectId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SubjectResponse.from(subjectService.getSubject(subjectId)));
    }

    @PostMapping
    public ResponseEntity<SubjectResponse> createSubject(@Valid @RequestBody CreateSubjectRequest request) {
        Subject created = subjectService.createSubject(
                request.code(),
                request.name(),
                request.shortName(),
                request.category(),
                request.defaultCoefficient());
        return ResponseEntity.created(URI.create("/api/v1/academic/subjects/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(SubjectResponse.from(created));
    }

    @PutMapping("/{subjectId}")
    public ResponseEntity<SubjectResponse> updateSubject(
            @PathVariable UUID subjectId,
            @Valid @RequestBody UpdateSubjectRequest request) {
        Subject updated = subjectService.updateSubject(
                subjectId,
                request.name(),
                request.shortName(),
                request.category(),
                request.defaultCoefficient());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SubjectResponse.from(updated));
    }

    @PostMapping("/{subjectId}/activate")
    public ResponseEntity<SubjectResponse> activateSubject(@PathVariable UUID subjectId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SubjectResponse.from(subjectService.activateSubject(subjectId)));
    }

    @PostMapping("/{subjectId}/deactivate")
    public ResponseEntity<SubjectResponse> deactivateSubject(@PathVariable UUID subjectId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SubjectResponse.from(subjectService.deactivateSubject(subjectId)));
    }
}
