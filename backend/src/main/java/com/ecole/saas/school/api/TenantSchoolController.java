package com.ecole.saas.school.api;

import com.ecole.saas.school.application.TenantSchoolService;
import com.ecole.saas.school.domain.School;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultation et mise à jour des informations de l'établissement courant.
 * Le tenant est déduit exclusivement du JWT côté serveur.
 */
@RestController
@RequestMapping("/api/v1/school")
public class TenantSchoolController {

    private final TenantSchoolService tenantSchoolService;

    public TenantSchoolController(TenantSchoolService tenantSchoolService) {
        this.tenantSchoolService = tenantSchoolService;
    }

    @GetMapping
    public ResponseEntity<SchoolResponse> getCurrentSchool() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SchoolResponse.from(tenantSchoolService.getCurrentSchool()));
    }

    @PutMapping
    public ResponseEntity<SchoolResponse> updateCurrentSchool(
            @Valid @RequestBody UpdateTenantSchoolRequest request) {
        School updated = tenantSchoolService.updateCurrentSchool(
                request.name(),
                request.shortName(),
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
                request.gradingPeriodSystem(),
                request.gradingScale());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SchoolResponse.from(updated));
    }
}
