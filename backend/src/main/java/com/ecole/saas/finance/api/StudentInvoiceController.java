package com.ecole.saas.finance.api;

import com.ecole.saas.finance.application.StudentInvoiceService;
import com.ecole.saas.finance.domain.InvoiceStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
 * Facturation élève et gestion des échéanciers (tenant issu exclusivement du JWT côté serveur).
 */
@RestController
@RequestMapping("/api/v1/finance/invoices")
public class StudentInvoiceController {

    private final StudentInvoiceService invoiceService;

    public StudentInvoiceController(StudentInvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<InvoiceResponse>> listInvoices(
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID familyId,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = FinancePagination.of(
                page, size, sortBy, direction, FinancePagination.INVOICE_SORT_FIELDS, "createdAt", Sort.Direction.DESC);
        Page<InvoiceResponse> invoices = invoiceService
                .listInvoices(studentId, academicYearId, familyId, status, search, pageable)
                .map(InvoiceResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(invoices));
    }

    @GetMapping("/{invoiceId}")
    public ResponseEntity<InvoiceDetailResponse> getInvoice(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(InvoiceDetailResponse.from(invoiceService.getInvoiceDetail(invoiceId)));
    }

    @PostMapping
    public ResponseEntity<InvoiceDetailResponse> createDraftInvoice(
            @Valid @RequestBody CreateInvoiceRequest request,
            JwtAuthenticationToken authentication) {
        List<StudentInvoiceService.InvoiceLineInput> lineInputs = request.lines().stream()
                .map(InvoiceLineRequest::toInput)
                .toList();
        List<StudentInvoiceService.ScheduleItemInput> scheduleInputs = request.schedules() == null
                ? null
                : request.schedules().stream().map(ScheduleItemRequest::toInput).toList();
        StudentInvoiceService.InvoiceDetailView created = invoiceService.createDraftInvoice(
                request.studentId(),
                request.academicYearId(),
                request.payerGuardianId(),
                request.familyId(),
                request.title(),
                request.dueDate(),
                request.notes(),
                lineInputs,
                scheduleInputs,
                FinanceApiSupport.extractActorId(authentication));
        return ResponseEntity.created(URI.create("/api/v1/finance/invoices/" + created.invoice().getId()))
                .cacheControl(CacheControl.noStore())
                .body(InvoiceDetailResponse.from(created));
    }

    @PutMapping("/{invoiceId}")
    public ResponseEntity<InvoiceDetailResponse> updateDraftInvoice(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody UpdateDraftInvoiceRequest request) {
        List<StudentInvoiceService.InvoiceLineInput> lineInputs = request.lines().stream()
                .map(InvoiceLineRequest::toInput)
                .toList();
        List<StudentInvoiceService.ScheduleItemInput> scheduleInputs = request.schedules() == null
                ? null
                : request.schedules().stream().map(ScheduleItemRequest::toInput).toList();
        StudentInvoiceService.InvoiceDetailView updated = invoiceService.updateDraftInvoice(
                invoiceId,
                request.payerGuardianId(),
                request.familyId(),
                request.title(),
                request.dueDate(),
                request.notes(),
                lineInputs,
                scheduleInputs);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(InvoiceDetailResponse.from(updated));
    }

    @PostMapping("/{invoiceId}/schedules/generate")
    public ResponseEntity<InvoiceDetailResponse> generateSchedules(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody GenerateSchedulesRequest request) {
        StudentInvoiceService.InvoiceDetailView updated = invoiceService.generateEqualSchedules(
                invoiceId,
                request.installmentCount(),
                request.firstDueDate(),
                request.intervalMonths(),
                request.labelPrefix());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(InvoiceDetailResponse.from(updated));
    }

    @PostMapping("/{invoiceId}/issue")
    public ResponseEntity<InvoiceDetailResponse> issueInvoice(
            @PathVariable UUID invoiceId,
            JwtAuthenticationToken authentication) {
        StudentInvoiceService.InvoiceDetailView issued =
                invoiceService.issueInvoice(invoiceId, FinanceApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(InvoiceDetailResponse.from(issued));
    }

    @PostMapping("/{invoiceId}/cancel")
    public ResponseEntity<InvoiceDetailResponse> cancelInvoice(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody CancelInvoiceRequest request,
            JwtAuthenticationToken authentication) {
        StudentInvoiceService.InvoiceDetailView cancelled = invoiceService.cancelInvoice(
                invoiceId, request.reason(), FinanceApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(InvoiceDetailResponse.from(cancelled));
    }
}
