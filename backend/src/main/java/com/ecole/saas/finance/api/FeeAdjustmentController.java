package com.ecole.saas.finance.api;

import com.ecole.saas.finance.application.FeeAdjustmentService;
import com.ecole.saas.finance.domain.AdjustmentType;
import com.ecole.saas.finance.domain.ApprovalStatus;
import com.ecole.saas.finance.domain.FeeAdjustment;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workflow encadré de demande et d'approbation des remises et bourses.
 */
@RestController
@RequestMapping("/api/v1/finance")
public class FeeAdjustmentController {

    private final FeeAdjustmentService adjustmentService;

    public FeeAdjustmentController(FeeAdjustmentService adjustmentService) {
        this.adjustmentService = adjustmentService;
    }

    @GetMapping("/adjustments")
    public ResponseEntity<PageResponse<FeeAdjustmentResponse>> listAdjustments(
            @RequestParam(required = false) UUID invoiceId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) AdjustmentType adjustmentType,
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "requestedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = FinancePagination.of(
                page, size, sortBy, direction, FinancePagination.ADJUSTMENT_SORT_FIELDS, "requestedAt", Sort.Direction.DESC);
        Page<FeeAdjustmentResponse> adjustments = adjustmentService
                .listAdjustments(invoiceId, studentId, adjustmentType, status, pageable)
                .map(FeeAdjustmentResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(adjustments));
    }

    @GetMapping("/adjustments/{adjustmentId}")
    public ResponseEntity<FeeAdjustmentResponse> getAdjustment(@PathVariable UUID adjustmentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(FeeAdjustmentResponse.from(adjustmentService.getAdjustment(adjustmentId)));
    }

    @PostMapping("/invoices/{invoiceId}/adjustments")
    public ResponseEntity<FeeAdjustmentResponse> requestAdjustment(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody RequestFeeAdjustmentRequest request,
            JwtAuthenticationToken authentication) {
        FeeAdjustment created = adjustmentService.requestAdjustment(
                invoiceId,
                request.adjustmentType(),
                request.calculationMode(),
                request.fixedAmountXof(),
                request.percentageValue(),
                request.reason(),
                FinanceApiSupport.extractActorId(authentication));
        return ResponseEntity.created(URI.create("/api/v1/finance/adjustments/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(FeeAdjustmentResponse.from(created));
    }

    @PostMapping("/adjustments/{adjustmentId}/approve")
    public ResponseEntity<FeeAdjustmentResponse> approveAdjustment(
            @PathVariable UUID adjustmentId,
            @Valid @RequestBody(required = false) AdjustmentDecisionRequest request,
            JwtAuthenticationToken authentication) {
        String note = request != null ? request.decisionNote() : null;
        FeeAdjustment approved = adjustmentService.approveAdjustment(
                adjustmentId, note, FinanceApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(FeeAdjustmentResponse.from(approved));
    }

    @PostMapping("/adjustments/{adjustmentId}/reject")
    public ResponseEntity<FeeAdjustmentResponse> rejectAdjustment(
            @PathVariable UUID adjustmentId,
            @Valid @RequestBody AdjustmentDecisionRequest request,
            JwtAuthenticationToken authentication) {
        FeeAdjustment rejected = adjustmentService.rejectAdjustment(
                adjustmentId, request.decisionNote(), FinanceApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(FeeAdjustmentResponse.from(rejected));
    }
}
