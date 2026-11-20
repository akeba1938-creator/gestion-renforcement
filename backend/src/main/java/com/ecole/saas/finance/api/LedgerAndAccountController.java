package com.ecole.saas.finance.api;

import com.ecole.saas.finance.FamilyFinancialSummary;
import com.ecole.saas.finance.StudentFinancialSummary;
import com.ecole.saas.finance.application.LedgerQueryService;
import com.ecole.saas.finance.domain.LedgerDirection;
import com.ecole.saas.finance.domain.LedgerEntryType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultation en lecture seule du registre comptable immuable et des relevés de comptes élève/famille.
 */
@RestController
@RequestMapping("/api/v1/finance")
public class LedgerAndAccountController {

    private final LedgerQueryService ledgerQueryService;

    public LedgerAndAccountController(LedgerQueryService ledgerQueryService) {
        this.ledgerQueryService = ledgerQueryService;
    }

    @GetMapping("/ledger")
    public ResponseEntity<PageResponse<LedgerEntryResponse>> listLedgerEntries(
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID invoiceId,
            @RequestParam(required = false) LedgerEntryType entryType,
            @RequestParam(required = false) LedgerDirection direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "sequenceNo") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        Pageable pageable = FinancePagination.of(
                page, size, sortBy, sortDirection, FinancePagination.LEDGER_SORT_FIELDS, "sequenceNo", Sort.Direction.DESC);
        Page<LedgerEntryResponse> entries = ledgerQueryService
                .listLedgerEntries(studentId, invoiceId, entryType, direction, pageable)
                .map(LedgerEntryResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(entries));
    }

    @GetMapping("/students/{studentId}/summary")
    public ResponseEntity<StudentFinancialSummary> getStudentSummary(
            @PathVariable UUID studentId,
            @RequestParam(required = false) UUID academicYearId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ledgerQueryService.getStudentSummary(studentId, academicYearId));
    }

    @GetMapping("/families/{familyId}/summary")
    public ResponseEntity<FamilyFinancialSummary> getFamilySummary(
            @PathVariable UUID familyId,
            @RequestParam(required = false) UUID academicYearId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ledgerQueryService.getFamilySummary(familyId, academicYearId));
    }
}
