package com.ecole.saas.center.api;

import com.ecole.saas.center.application.CenterPackService;
import com.ecole.saas.center.domain.CenterPackBillingMode;
import com.ecole.saas.center.domain.CenterSessionPack;
import com.ecole.saas.center.domain.CenterStudentPackStatus;
import jakarta.validation.Valid;
import java.net.URI;
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
 * Gestion du catalogue de forfaits/packs en FCFA entiers et des souscriptions de packs élèves.
 */
@RestController
@RequestMapping("/api/v1/center")
public class CenterPackController {

    private final CenterPackService packService;

    public CenterPackController(CenterPackService packService) {
        this.packService = packService;
    }

    @GetMapping("/packs")
    public ResponseEntity<PageResponse<CenterPackResponse>> listPacks(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID gradeLevelId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) CenterPackBillingMode billingMode,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = CenterPagination.of(
                page, size, sortBy, direction, CenterPagination.PACK_SORT_FIELDS, "code", Sort.Direction.ASC);
        Page<CenterPackResponse> result = packService
                .listPacks(academicYearId, gradeLevelId, subjectId, billingMode, active, pageable)
                .map(CenterPackResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(result));
    }

    @GetMapping("/packs/{packId}")
    public ResponseEntity<CenterPackResponse> getPack(@PathVariable UUID packId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterPackResponse.from(packService.getPack(packId)));
    }

    @PostMapping("/packs")
    public ResponseEntity<CenterPackResponse> createPack(@Valid @RequestBody CreateCenterPackRequest request) {
        CenterSessionPack created = packService.createPack(
                request.academicYearId(),
                request.gradeLevelId(),
                request.subjectId(),
                request.code(),
                request.name(),
                request.description(),
                request.billingMode(),
                request.includedSessions(),
                request.durationDays(),
                request.priceXof());
        return ResponseEntity.created(URI.create("/api/v1/center/packs/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(CenterPackResponse.from(created));
    }

    @PutMapping("/packs/{packId}")
    public ResponseEntity<CenterPackResponse> updatePack(
            @PathVariable UUID packId,
            @Valid @RequestBody UpdateCenterPackRequest request) {
        CenterSessionPack updated = packService.updatePack(
                packId,
                request.academicYearId(),
                request.gradeLevelId(),
                request.subjectId(),
                request.name(),
                request.description(),
                request.billingMode(),
                request.includedSessions(),
                request.durationDays(),
                request.priceXof());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterPackResponse.from(updated));
    }

    @PostMapping("/packs/{packId}/activate")
    public ResponseEntity<CenterPackResponse> activatePack(@PathVariable UUID packId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterPackResponse.from(packService.activatePack(packId)));
    }

    @PostMapping("/packs/{packId}/deactivate")
    public ResponseEntity<CenterPackResponse> deactivatePack(@PathVariable UUID packId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterPackResponse.from(packService.deactivatePack(packId)));
    }

    @GetMapping("/student-packs")
    public ResponseEntity<PageResponse<CenterStudentPackResponse>> listStudentPacks(
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) CenterStudentPackStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = CenterPagination.of(
                page,
                size,
                sortBy,
                direction,
                CenterPagination.STUDENT_PACK_SORT_FIELDS,
                "startDate",
                Sort.Direction.DESC);
        Page<CenterStudentPackResponse> result = packService
                .listStudentPacks(studentId, groupId, status, pageable)
                .map(CenterStudentPackResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(result));
    }

    @GetMapping("/student-packs/{studentPackId}")
    public ResponseEntity<CenterStudentPackResponse> getStudentPack(@PathVariable UUID studentPackId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterStudentPackResponse.from(packService.getStudentPack(studentPackId)));
    }

    @PostMapping("/student-packs")
    public ResponseEntity<CenterStudentPackResponse> assignStudentPack(
            @Valid @RequestBody AssignStudentPackRequest request,
            JwtAuthenticationToken authentication) {
        CenterPackService.StudentPackView assigned = packService.assignPackToStudent(
                request.studentId(),
                request.packId(),
                request.groupId(),
                request.customPriceXof(),
                request.customTotalSessions(),
                request.startDate(),
                request.endDate(),
                request.notes(),
                CenterApiSupport.extractActorId(authentication));
        return ResponseEntity.created(URI.create("/api/v1/center/student-packs/" + assigned.studentPack().getId()))
                .cacheControl(CacheControl.noStore())
                .body(CenterStudentPackResponse.from(assigned));
    }

    @PostMapping("/student-packs/{studentPackId}/cancel")
    public ResponseEntity<CenterStudentPackResponse> cancelStudentPack(
            @PathVariable UUID studentPackId,
            @Valid @RequestBody CenterReasonRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterStudentPackResponse.from(
                        packService.cancelStudentPack(studentPackId, request.reason())));
    }
}
