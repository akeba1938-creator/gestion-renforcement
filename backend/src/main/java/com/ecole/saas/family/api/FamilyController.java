package com.ecole.saas.family.api;

import com.ecole.saas.family.application.FamilyService;
import com.ecole.saas.family.domain.Family;
import com.ecole.saas.family.domain.FamilyStatus;
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
 * Gestion des familles / foyers d'un établissement.
 */
@RestController
@RequestMapping("/api/v1/families")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<FamilyResponse>> listFamilies(
            @RequestParam(required = false) FamilyStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = FamilyPagination.of(
                page, size, sortBy, direction, FamilyPagination.FAMILY_SORT_FIELDS, "name", Sort.Direction.ASC);
        Page<FamilyResponse> families = familyService.listFamilies(status, search, pageable)
                .map(FamilyResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(families));
    }

    @GetMapping("/{familyId}")
    public ResponseEntity<FamilyResponse> getFamily(@PathVariable UUID familyId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(FamilyResponse.from(familyService.getFamily(familyId)));
    }

    @PostMapping
    public ResponseEntity<FamilyResponse> createFamily(@Valid @RequestBody CreateFamilyRequest request) {
        Family created = familyService.createFamily(
                request.code(),
                request.name(),
                request.primaryPhone(),
                request.email(),
                request.address(),
                request.city(),
                request.notes());
        return ResponseEntity.created(URI.create("/api/v1/families/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(FamilyResponse.from(created));
    }

    @PutMapping("/{familyId}")
    public ResponseEntity<FamilyResponse> updateFamily(
            @PathVariable UUID familyId,
            @Valid @RequestBody UpdateFamilyRequest request) {
        Family updated = familyService.updateFamily(
                familyId,
                request.code(),
                request.name(),
                request.primaryPhone(),
                request.email(),
                request.address(),
                request.city(),
                request.notes());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(FamilyResponse.from(updated));
    }

    @PostMapping("/{familyId}/archive")
    public ResponseEntity<FamilyResponse> archiveFamily(@PathVariable UUID familyId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(FamilyResponse.from(familyService.archiveFamily(familyId)));
    }

    @PostMapping("/{familyId}/reactivate")
    public ResponseEntity<FamilyResponse> reactivateFamily(@PathVariable UUID familyId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(FamilyResponse.from(familyService.reactivateFamily(familyId)));
    }

    @GetMapping("/{familyId}/guardians")
    public ResponseEntity<PageResponse<GuardianResponse>> listFamilyGuardians(
            @PathVariable UUID familyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastName") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = FamilyPagination.of(
                page, size, sortBy, direction, FamilyPagination.GUARDIAN_SORT_FIELDS, "lastName", Sort.Direction.ASC);
        Page<GuardianResponse> guardians = familyService.listFamilyGuardians(familyId, pageable)
                .map(GuardianResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(guardians));
    }

    @GetMapping("/{familyId}/students")
    public ResponseEntity<PageResponse<FamilyStudentResponse>> listFamilyStudents(
            @PathVariable UUID familyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = FamilyPagination.ofFixedOrder(page, size);
        Page<FamilyStudentResponse> students = familyService.listFamilyStudents(familyId, pageable)
                .map(FamilyStudentResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(students));
    }
}
