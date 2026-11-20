package com.ecole.saas.family.api;

import com.ecole.saas.family.application.GuardianService;
import com.ecole.saas.family.application.GuardianService.GuardianView;
import com.ecole.saas.family.domain.GuardianStatus;
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
 * Gestion des parents et tuteurs légaux d'un établissement.
 */
@RestController
@RequestMapping("/api/v1/guardians")
public class GuardianController {

    private final GuardianService guardianService;

    public GuardianController(GuardianService guardianService) {
        this.guardianService = guardianService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<GuardianResponse>> listGuardians(
            @RequestParam(required = false) UUID familyId,
            @RequestParam(required = false) GuardianStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastName") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = FamilyPagination.of(
                page, size, sortBy, direction, FamilyPagination.GUARDIAN_SORT_FIELDS, "lastName", Sort.Direction.ASC);
        Page<GuardianResponse> guardians = guardianService
                .listGuardians(familyId, status, search, pageable)
                .map(GuardianResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(guardians));
    }

    @GetMapping("/{guardianId}")
    public ResponseEntity<GuardianResponse> getGuardian(@PathVariable UUID guardianId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(GuardianResponse.from(guardianService.getGuardian(guardianId)));
    }

    @PostMapping
    public ResponseEntity<GuardianResponse> createGuardian(@Valid @RequestBody CreateGuardianRequest request) {
        GuardianView created = guardianService.createGuardian(
                request.familyId(),
                request.firstName(),
                request.lastName(),
                request.gender(),
                request.phone(),
                request.secondaryPhone(),
                request.email(),
                request.profession(),
                request.address(),
                request.nationalIdNumber(),
                request.preferredLanguage());
        return ResponseEntity.created(URI.create("/api/v1/guardians/" + created.guardian().getId()))
                .cacheControl(CacheControl.noStore())
                .body(GuardianResponse.from(created));
    }

    @PutMapping("/{guardianId}")
    public ResponseEntity<GuardianResponse> updateGuardian(
            @PathVariable UUID guardianId,
            @Valid @RequestBody UpdateGuardianRequest request) {
        GuardianView updated = guardianService.updateGuardian(
                guardianId,
                request.familyId(),
                request.firstName(),
                request.lastName(),
                request.gender(),
                request.phone(),
                request.secondaryPhone(),
                request.email(),
                request.profession(),
                request.address(),
                request.nationalIdNumber(),
                request.preferredLanguage());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(GuardianResponse.from(updated));
    }

    @PostMapping("/{guardianId}/archive")
    public ResponseEntity<GuardianResponse> archiveGuardian(@PathVariable UUID guardianId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(GuardianResponse.from(guardianService.archiveGuardian(guardianId)));
    }

    @PostMapping("/{guardianId}/reactivate")
    public ResponseEntity<GuardianResponse> reactivateGuardian(@PathVariable UUID guardianId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(GuardianResponse.from(guardianService.reactivateGuardian(guardianId)));
    }

    @GetMapping("/{guardianId}/students")
    public ResponseEntity<PageResponse<StudentGuardianResponse>> listGuardianStudents(
            @PathVariable UUID guardianId,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = FamilyPagination.of(
                page, size, sortBy, direction, FamilyPagination.LINK_SORT_FIELDS, "createdAt", Sort.Direction.ASC);
        Page<StudentGuardianResponse> links = guardianService
                .listGuardianStudents(guardianId, activeOnly, pageable)
                .map(StudentGuardianResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(links));
    }
}
