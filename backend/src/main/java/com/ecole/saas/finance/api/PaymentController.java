package com.ecole.saas.finance.api;

import com.ecole.saas.finance.application.PaymentService;
import com.ecole.saas.finance.domain.FinanceValidationException;
import com.ecole.saas.finance.domain.PaymentMethod;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Encaissements immuables (strictement réservés à {@code CASHIER} et {@code CENTER_MANAGER})
 * et reçus séquentiels sans trou.
 */
@RestController
@RequestMapping("/api/v1/finance")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> recordPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody RecordPaymentRequest request,
            JwtAuthenticationToken authentication) {
        String resolvedKey = resolveIdempotencyKey(idempotencyKeyHeader, request.idempotencyKey());
        PaymentService.PaymentView result = paymentService.recordPayment(
                request.invoiceId(),
                request.paymentScheduleId(),
                request.payerGuardianId(),
                request.amountXof(),
                request.paymentMethod(),
                request.externalReference(),
                resolvedKey,
                request.notes(),
                FinanceApiSupport.extractActorId(authentication));
        PaymentResponse body = PaymentResponse.from(result);
        if (result.idempotentReplay()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Idempotent-Replay", "true")
                    .body(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/finance/payments/" + result.payment().getId()))
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping("/payments")
    public ResponseEntity<PageResponse<PaymentResponse>> listPayments(
            @RequestParam(required = false) UUID invoiceId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "paidAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = FinancePagination.of(
                page, size, sortBy, direction, FinancePagination.PAYMENT_SORT_FIELDS, "paidAt", Sort.Direction.DESC);
        Page<PaymentResponse> payments = paymentService
                .listPayments(invoiceId, studentId, paymentMethod, pageable)
                .map(PaymentResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(payments));
    }

    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PaymentResponse.from(paymentService.getPayment(paymentId)));
    }

    @GetMapping("/receipts")
    public ResponseEntity<PageResponse<ReceiptResponse>> listReceipts(
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID invoiceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "issuedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = FinancePagination.of(
                page, size, sortBy, direction, FinancePagination.RECEIPT_SORT_FIELDS, "issuedAt", Sort.Direction.DESC);
        Page<ReceiptResponse> receipts = paymentService
                .listReceipts(studentId, invoiceId, pageable)
                .map(ReceiptResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(receipts));
    }

    @GetMapping("/receipts/verify/{verificationToken}")
    public ResponseEntity<ReceiptVerificationResponse> verifyReceipt(@PathVariable String verificationToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ReceiptVerificationResponse.from(paymentService.verifyReceiptByToken(verificationToken)));
    }

    private static String resolveIdempotencyKey(String headerKey, String bodyKey) {
        boolean hasHeader = headerKey != null && !headerKey.isBlank();
        boolean hasBody = bodyKey != null && !bodyKey.isBlank();
        if (hasHeader && hasBody && !headerKey.trim().equals(bodyKey.trim())) {
            throw new FinanceValidationException(
                    "idempotency-key-mismatch",
                    "Clé d'idempotence incohérente",
                    "La clé d'idempotence de l'en-tête et celle du corps doivent être identiques.");
        }
        return hasHeader ? headerKey.trim() : (hasBody ? bodyKey.trim() : null);
    }
}
