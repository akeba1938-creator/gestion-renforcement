package com.ecole.saas.finance.api;

import com.ecole.saas.finance.application.CashClosingService;
import com.ecole.saas.finance.domain.CashClosing;
import com.ecole.saas.finance.domain.CashClosingStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
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
 * Clôtures journalières de caisse et validation des écarts constatés.
 */
@RestController
@RequestMapping("/api/v1/finance/cash-closings")
public class CashClosingController {

    private final CashClosingService cashClosingService;

    public CashClosingController(CashClosingService cashClosingService) {
        this.cashClosingService = cashClosingService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<CashClosingResponse>> listCashClosings(
            @RequestParam(required = false) CashClosingStatus status,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "closingDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = FinancePagination.of(
                page, size, sortBy, direction, FinancePagination.CASH_CLOSING_SORT_FIELDS, "closingDate", Sort.Direction.DESC);
        Page<CashClosingResponse> closings = cashClosingService
                .listCashClosings(status, fromDate, toDate, pageable)
                .map(CashClosingResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(closings));
    }

    @GetMapping("/{closingId}")
    public ResponseEntity<CashClosingResponse> getCashClosing(@PathVariable UUID closingId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CashClosingResponse.from(cashClosingService.getCashClosing(closingId)));
    }

    @PostMapping
    public ResponseEntity<CashClosingResponse> submitCashClosing(
            @Valid @RequestBody SubmitCashClosingRequest request,
            JwtAuthenticationToken authentication) {
        CashClosing created = cashClosingService.submitCashClosing(
                request.closingDate(),
                request.countedCashXof(),
                request.notes(),
                FinanceApiSupport.extractActorId(authentication));
        return ResponseEntity.created(URI.create("/api/v1/finance/cash-closings/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(CashClosingResponse.from(created));
    }

    @PostMapping("/{closingId}/validate")
    public ResponseEntity<CashClosingResponse> validateCashClosing(
            @PathVariable UUID closingId,
            @Valid @RequestBody(required = false) ValidateCashClosingRequest request,
            JwtAuthenticationToken authentication) {
        String note = request != null ? request.validationNote() : null;
        CashClosing validated = cashClosingService.validateCashClosing(
                closingId, note, FinanceApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CashClosingResponse.from(validated));
    }
}
