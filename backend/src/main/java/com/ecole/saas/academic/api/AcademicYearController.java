package com.ecole.saas.academic.api;

import com.ecole.saas.academic.application.AcademicYearManagementService;
import com.ecole.saas.academic.domain.AcademicYear;
import com.ecole.saas.academic.domain.AcademicYearStatus;
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
 * Gestion des années scolaires de l'établissement courant.
 * Le tenant est déduit exclusivement du JWT côté serveur.
 */
@RestController
@RequestMapping("/api/v1/academic/years")
public class AcademicYearController {

    private final AcademicYearManagementService academicYearService;

    public AcademicYearController(AcademicYearManagementService academicYearService) {
        this.academicYearService = academicYearService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AcademicYearResponse>> listYears(
            @RequestParam(required = false) AcademicYearStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = AcademicPagination.of(
                page, size, sortBy, direction, AcademicPagination.YEAR_SORT_FIELDS, "startDate", Sort.Direction.DESC);
        Page<AcademicYearResponse> years = academicYearService.listAcademicYears(status, pageable)
                .map(AcademicYearResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(years));
    }

    @GetMapping("/current")
    public ResponseEntity<AcademicYearResponse> getCurrentYear() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AcademicYearResponse.from(academicYearService.getCurrentAcademicYear()));
    }

    @GetMapping("/{yearId}")
    public ResponseEntity<AcademicYearResponse> getYear(@PathVariable UUID yearId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AcademicYearResponse.from(academicYearService.getAcademicYear(yearId)));
    }

    @PostMapping
    public ResponseEntity<AcademicYearResponse> createYear(@Valid @RequestBody CreateAcademicYearRequest request) {
        AcademicYear created = academicYearService.createAcademicYear(
                request.code(),
                request.label(),
                request.startDate(),
                request.endDate(),
                request.periodSystem());
        return ResponseEntity.created(URI.create("/api/v1/academic/years/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(AcademicYearResponse.from(created));
    }

    @PutMapping("/{yearId}")
    public ResponseEntity<AcademicYearResponse> updateYear(
            @PathVariable UUID yearId,
            @Valid @RequestBody UpdateAcademicYearRequest request) {
        AcademicYear updated = academicYearService.updateAcademicYear(
                yearId,
                request.label(),
                request.startDate(),
                request.endDate(),
                request.periodSystem());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AcademicYearResponse.from(updated));
    }

    @PostMapping("/{yearId}/activate")
    public ResponseEntity<AcademicYearResponse> activateYear(
            @PathVariable UUID yearId,
            @RequestBody(required = false) ActivateAcademicYearRequest request) {
        boolean makeCurrent = request == null || request.resolvedMakeCurrent();
        AcademicYear activated = academicYearService.activateAcademicYear(yearId, makeCurrent);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AcademicYearResponse.from(activated));
    }

    @PostMapping("/{yearId}/set-current")
    public ResponseEntity<AcademicYearResponse> setCurrentYear(@PathVariable UUID yearId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AcademicYearResponse.from(academicYearService.setCurrentAcademicYear(yearId)));
    }

    @PostMapping("/{yearId}/close")
    public ResponseEntity<AcademicYearResponse> closeYear(@PathVariable UUID yearId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AcademicYearResponse.from(academicYearService.closeAcademicYear(yearId)));
    }

    @PostMapping("/{yearId}/archive")
    public ResponseEntity<AcademicYearResponse> archiveYear(@PathVariable UUID yearId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AcademicYearResponse.from(academicYearService.archiveAcademicYear(yearId)));
    }
}
