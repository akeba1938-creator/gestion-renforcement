package com.ecole.saas.school.api;

import com.ecole.saas.school.application.PlatformSchoolService;
import com.ecole.saas.school.domain.School;
import com.ecole.saas.school.domain.SchoolStatus;
import com.ecole.saas.school.domain.SchoolType;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Administration plateforme des établissements (écoles privées et centres de renforcement)
 * réservée aux administrateurs SaaS ({@code ADMIN_SAAS}).
 */
@RestController
@RequestMapping("/api/v1/platform/schools")
public class PlatformSchoolController {

    private final PlatformSchoolService platformSchoolService;

    public PlatformSchoolController(PlatformSchoolService platformSchoolService) {
        this.platformSchoolService = platformSchoolService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<SchoolResponse>> listSchools(
            @RequestParam(required = false) SchoolStatus status,
            @RequestParam(required = false) SchoolType schoolType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = SchoolPagination.of(page, size, sortBy, direction);
        Page<SchoolResponse> schools = platformSchoolService
                .listSchools(status, schoolType, search, pageable)
                .map(SchoolResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(schools));
    }

    @GetMapping("/{schoolId}")
    public ResponseEntity<SchoolResponse> getSchool(@PathVariable UUID schoolId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SchoolResponse.from(platformSchoolService.getSchool(schoolId)));
    }

    @PostMapping
    public ResponseEntity<SchoolResponse> createSchool(@Valid @RequestBody CreateSchoolRequest request) {
        School created = platformSchoolService.createSchool(
                request.code(),
                request.name(),
                request.shortName(),
                request.schoolType(),
                request.phone(),
                request.email(),
                request.address(),
                request.city(),
                request.region(),
                request.countryCode(),
                request.motto(),
                request.ministryCode(),
                request.website(),
                request.timezone(),
                request.currency(),
                request.gradingPeriodSystem(),
                request.gradingScale());
        return ResponseEntity.created(URI.create("/api/v1/platform/schools/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(SchoolResponse.from(created));
    }

    @PutMapping("/{schoolId}")
    public ResponseEntity<SchoolResponse> updateSchool(
            @PathVariable UUID schoolId,
            @Valid @RequestBody UpdatePlatformSchoolRequest request) {
        School updated = platformSchoolService.updateSchool(
                schoolId,
                request.name(),
                request.shortName(),
                request.schoolType(),
                request.phone(),
                request.email(),
                request.address(),
                request.city(),
                request.region(),
                request.countryCode(),
                request.motto(),
                request.ministryCode(),
                request.website(),
                request.timezone(),
                request.currency(),
                request.gradingPeriodSystem(),
                request.gradingScale());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SchoolResponse.from(updated));
    }

    @PostMapping("/{schoolId}/suspend")
    public ResponseEntity<SchoolResponse> suspendSchool(
            @PathVariable UUID schoolId,
            @Valid @RequestBody SchoolStatusReasonRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SchoolResponse.from(platformSchoolService.suspendSchool(schoolId, request.reason())));
    }

    @PostMapping("/{schoolId}/activate")
    public ResponseEntity<SchoolResponse> activateSchool(@PathVariable UUID schoolId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SchoolResponse.from(platformSchoolService.activateSchool(schoolId)));
    }

    @PostMapping("/{schoolId}/archive")
    public ResponseEntity<SchoolResponse> archiveSchool(
            @PathVariable UUID schoolId,
            @Valid @RequestBody SchoolStatusReasonRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SchoolResponse.from(platformSchoolService.archiveSchool(schoolId, request.reason())));
    }
}
