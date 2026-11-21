package com.ecole.saas.family.api;

import com.ecole.saas.family.application.StudentGuardianService;
import com.ecole.saas.family.application.StudentGuardianService.StudentGuardianView;
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
 * Gestion des liens élève ↔ parent/tuteur légal.
 */
@RestController
@RequestMapping("/api/v1/students/{studentId}/guardians")
public class StudentGuardianController {

    private final StudentGuardianService studentGuardianService;

    public StudentGuardianController(StudentGuardianService studentGuardianService) {
        this.studentGuardianService = studentGuardianService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<StudentGuardianResponse>> listStudentGuardians(
            @PathVariable UUID studentId,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = FamilyPagination.of(
                page, size, sortBy, direction, FamilyPagination.LINK_SORT_FIELDS, "createdAt", Sort.Direction.ASC);
        Page<StudentGuardianResponse> links = studentGuardianService
                .listStudentGuardians(studentId, activeOnly, pageable)
                .map(StudentGuardianResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(links));
    }

    @PostMapping
    public ResponseEntity<StudentGuardianResponse> linkGuardian(
            @PathVariable UUID studentId,
            @Valid @RequestBody LinkGuardianRequest request) {
        StudentGuardianView created = studentGuardianService.linkGuardianToStudent(
                studentId,
                request.guardianId(),
                request.relationshipType(),
                request.primaryContact(),
                request.financialResponsible(),
                request.legalCustody(),
                request.emergencyContact(),
                request.authorizedPickup(),
                request.notes());
        return ResponseEntity.created(URI.create(
                        "/api/v1/students/" + studentId + "/guardians/" + created.link().getId()))
                .cacheControl(CacheControl.noStore())
                .body(StudentGuardianResponse.from(created));
    }

    @PutMapping("/{linkId}")
    public ResponseEntity<StudentGuardianResponse> updateLink(
            @PathVariable UUID studentId,
            @PathVariable UUID linkId,
            @Valid @RequestBody UpdateStudentGuardianRequest request) {
        StudentGuardianView updated = studentGuardianService.updateLink(
                studentId,
                linkId,
                request.relationshipType(),
                Boolean.TRUE.equals(request.primaryContact()),
                Boolean.TRUE.equals(request.financialResponsible()),
                Boolean.TRUE.equals(request.legalCustody()),
                Boolean.TRUE.equals(request.emergencyContact()),
                Boolean.TRUE.equals(request.authorizedPickup()),
                request.notes());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StudentGuardianResponse.from(updated));
    }

    @PostMapping("/{linkId}/deactivate")
    public ResponseEntity<StudentGuardianResponse> deactivateLink(
            @PathVariable UUID studentId,
            @PathVariable UUID linkId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StudentGuardianResponse.from(studentGuardianService.deactivateLink(studentId, linkId)));
    }

    @PostMapping("/{linkId}/activate")
    public ResponseEntity<StudentGuardianResponse> activateLink(
            @PathVariable UUID studentId,
            @PathVariable UUID linkId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StudentGuardianResponse.from(studentGuardianService.activateLink(studentId, linkId)));
    }
}
