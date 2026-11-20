package com.ecole.saas.finance.api;

import com.ecole.saas.finance.application.PaymentReversalService;
import com.ecole.saas.finance.domain.ApprovalStatus;
import com.ecole.saas.finance.domain.PaymentReversalRequest;
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
 * Workflow demande -> approbation de contre-passation (annulation tracée) des encaissements validés.
 */
@RestController
@RequestMapping("/api/v1/finance/payment-reversals")
public class PaymentReversalController {

    private final PaymentReversalService reversalService;

    public PaymentReversalController(PaymentReversalService reversalService) {
        this.reversalService = reversalService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<PaymentReversalRequestResponse>> listReversalRequests(
            @RequestParam(required = false) UUID paymentId,
            @RequestParam(required = false) UUID invoiceId,
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "requestedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = FinancePagination.of(
                page, size, sortBy, direction, FinancePagination.REVERSAL_REQUEST_SORT_FIELDS, "requestedAt", Sort.Direction.DESC);
        Page<PaymentReversalRequestResponse> requests = reversalService
                .listReversalRequests(paymentId, invoiceId, status, pageable)
                .map(PaymentReversalRequestResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(requests));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<PaymentReversalDecisionResponse> getReversalRequest(@PathVariable UUID requestId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PaymentReversalDecisionResponse.from(reversalService.getReversalRequest(requestId)));
    }

    @PostMapping
    public ResponseEntity<PaymentReversalRequestResponse> requestReversal(
            @Valid @RequestBody RequestPaymentReversalRequest request,
            JwtAuthenticationToken authentication) {
        PaymentReversalRequest created = reversalService.requestReversal(
                request.paymentId(), request.reason(), FinanceApiSupport.extractActorId(authentication));
        return ResponseEntity.created(URI.create("/api/v1/finance/payment-reversals/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(PaymentReversalRequestResponse.from(created));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<PaymentReversalDecisionResponse> approveReversal(
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) ReversalDecisionRequest request,
            JwtAuthenticationToken authentication) {
        String note = request != null ? request.decisionNote() : null;
        PaymentReversalService.ReversalDecisionView decision = reversalService.approveReversal(
                requestId, note, FinanceApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PaymentReversalDecisionResponse.from(decision));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<PaymentReversalRequestResponse> rejectReversal(
            @PathVariable UUID requestId,
            @Valid @RequestBody ReversalDecisionRequest request,
            JwtAuthenticationToken authentication) {
        PaymentReversalRequest rejected = reversalService.rejectReversal(
                requestId, request.decisionNote(), FinanceApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PaymentReversalRequestResponse.from(rejected));
    }
}
