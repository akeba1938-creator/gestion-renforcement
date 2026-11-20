package com.ecole.saas.academic.api;

import com.ecole.saas.academic.application.GradeLevelService;
import com.ecole.saas.academic.domain.EducationCycle;
import com.ecole.saas.academic.domain.GradeLevel;
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
 * Gestion des niveaux scolaires et niveaux de centre de renforcement.
 */
@RestController
@RequestMapping("/api/v1/academic/grade-levels")
public class GradeLevelController {

    private final GradeLevelService gradeLevelService;

    public GradeLevelController(GradeLevelService gradeLevelService) {
        this.gradeLevelService = gradeLevelService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<GradeLevelResponse>> listGradeLevels(
            @RequestParam(required = false) EducationCycle cycle,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "displayOrder") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = AcademicPagination.of(
                page, size, sortBy, direction, AcademicPagination.LEVEL_SORT_FIELDS, "displayOrder", Sort.Direction.ASC);
        Page<GradeLevelResponse> levels = gradeLevelService.listGradeLevels(cycle, activeOnly, pageable)
                .map(GradeLevelResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(levels));
    }

    @GetMapping("/{levelId}")
    public ResponseEntity<GradeLevelResponse> getGradeLevel(@PathVariable UUID levelId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(GradeLevelResponse.from(gradeLevelService.getGradeLevel(levelId)));
    }

    @PostMapping
    public ResponseEntity<GradeLevelResponse> createGradeLevel(@Valid @RequestBody CreateGradeLevelRequest request) {
        GradeLevel created = gradeLevelService.createGradeLevel(
                request.code(), request.name(), request.cycle(), request.displayOrder());
        return ResponseEntity.created(URI.create("/api/v1/academic/grade-levels/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(GradeLevelResponse.from(created));
    }

    @PutMapping("/{levelId}")
    public ResponseEntity<GradeLevelResponse> updateGradeLevel(
            @PathVariable UUID levelId,
            @Valid @RequestBody UpdateGradeLevelRequest request) {
        GradeLevel updated = gradeLevelService.updateGradeLevel(
                levelId, request.name(), request.cycle(), request.displayOrder());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(GradeLevelResponse.from(updated));
    }

    @PostMapping("/{levelId}/activate")
    public ResponseEntity<GradeLevelResponse> activateGradeLevel(@PathVariable UUID levelId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(GradeLevelResponse.from(gradeLevelService.activateGradeLevel(levelId)));
    }

    @PostMapping("/{levelId}/deactivate")
    public ResponseEntity<GradeLevelResponse> deactivateGradeLevel(@PathVariable UUID levelId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(GradeLevelResponse.from(gradeLevelService.deactivateGradeLevel(levelId)));
    }
}
