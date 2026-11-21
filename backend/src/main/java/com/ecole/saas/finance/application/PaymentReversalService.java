package com.ecole.saas.finance.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.finance.domain.ApprovalStatus;
import com.ecole.saas.finance.domain.FinanceConflictException;
import com.ecole.saas.finance.domain.FinanceNotFoundException;
import com.ecole.saas.finance.domain.LedgerEntry;
import com.ecole.saas.finance.domain.LedgerEntryType;
import com.ecole.saas.finance.domain.LedgerReferenceType;
import com.ecole.saas.finance.domain.Payment;
import com.ecole.saas.finance.domain.PaymentAllocation;
import com.ecole.saas.finance.domain.PaymentReversal;
import com.ecole.saas.finance.domain.PaymentReversalRequest;
import com.ecole.saas.finance.domain.PaymentSchedule;
import com.ecole.saas.finance.domain.StudentInvoice;
import com.ecole.saas.finance.infrastructure.LedgerEntryRepository;
import com.ecole.saas.finance.infrastructure.PaymentAllocationRepository;
import com.ecole.saas.finance.infrastructure.PaymentRepository;
import com.ecole.saas.finance.infrastructure.PaymentReversalRepository;
import com.ecole.saas.finance.infrastructure.PaymentReversalRequestRepository;
import com.ecole.saas.finance.infrastructure.PaymentScheduleRepository;
import com.ecole.saas.finance.infrastructure.StudentInvoiceRepository;
import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workflow encadré de demande puis approbation de contre-passation d'un encaissement validé.
 * Ne modifie ni ne supprime jamais les tables immuables {@code payments}, {@code receipts},
 * {@code payment_allocations}, {@code payment_reversals} et {@code ledger_entries}.
 */
@Service
public class PaymentReversalService {

    public record ReversalDecisionView(PaymentReversalRequest request, PaymentReversal reversal) {
    }

    private final PaymentReversalRequestRepository requestRepository;
    private final PaymentReversalRepository reversalRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final StudentInvoiceRepository invoiceRepository;
    private final PaymentScheduleRepository scheduleRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerPostingHelper ledgerPostingHelper;
    private final FinanceSubscriptionGuard subscriptionGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public PaymentReversalService(
            PaymentReversalRequestRepository requestRepository,
            PaymentReversalRepository reversalRepository,
            PaymentRepository paymentRepository,
            PaymentAllocationRepository allocationRepository,
            StudentInvoiceRepository invoiceRepository,
            PaymentScheduleRepository scheduleRepository,
            LedgerEntryRepository ledgerEntryRepository,
            LedgerPostingHelper ledgerPostingHelper,
            FinanceSubscriptionGuard subscriptionGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.requestRepository = requestRepository;
        this.reversalRepository = reversalRepository;
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.invoiceRepository = invoiceRepository;
        this.scheduleRepository = scheduleRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerPostingHelper = ledgerPostingHelper;
        this.subscriptionGuard = subscriptionGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<PaymentReversalRequest> listReversalRequests(
            UUID paymentId,
            UUID invoiceId,
            ApprovalStatus status,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Specification<PaymentReversalRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (paymentId != null) {
                predicates.add(cb.equal(root.get("paymentId"), paymentId));
            }
            if (invoiceId != null) {
                predicates.add(cb.equal(root.get("invoiceId"), invoiceId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return requestRepository.findAll(spec, pageable);
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public ReversalDecisionView getReversalRequest(UUID requestId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        PaymentReversalRequest request = requireRequest(requestId);
        PaymentReversal reversal = reversalRepository.findByOriginalPaymentId(request.getPaymentId()).orElse(null);
        return new ReversalDecisionView(request, reversal);
    }

    @PreAuthorize(FinanceRoles.REVERSAL_REQUEST_ROLES)
    @Transactional
    public PaymentReversalRequest requestReversal(UUID paymentId, String reason, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "payment-not-found", "Encaissement introuvable", "L'encaissement demandé est introuvable."));

        if (reversalRepository.existsByOriginalPaymentId(payment.getId())) {
            throw new FinanceConflictException(
                    "payment-already-reversed",
                    "Encaissement déjà contre-passé",
                    "Cet encaissement a déjà fait l'objet d'une contre-passation validée.");
        }
        if (requestRepository.existsByPaymentIdAndStatus(payment.getId(), ApprovalStatus.PENDING_APPROVAL)) {
            throw new FinanceConflictException(
                    "reversal-request-already-pending",
                    "Demande déjà en cours",
                    "Une demande de contre-passation est déjà en attente d'approbation pour cet encaissement.");
        }

        PaymentReversalRequest request = new PaymentReversalRequest(
                payment.getId(),
                payment.getInvoiceId(),
                payment.getStudentId(),
                payment.getAmountXof(),
                reason,
                actorId,
                clock.instant());
        PaymentReversalRequest saved = requestRepository.save(request);
        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.FINANCE,
                AuditAction.PAYMENT_REVERSAL_REQUESTED,
                AuditOutcome.SUCCESS,
                "PAYMENT_REVERSAL_REQUEST",
                saved.getId(),
                "Demande de contre-passation d'un encaissement.",
                Map.of(
                        "paymentId", saved.getPaymentId().toString(),
                        "invoiceId", saved.getInvoiceId().toString(),
                        "amountXof", saved.getAmountXof())));
        return saved;
    }

    @PreAuthorize(FinanceRoles.APPROVAL_ROLES)
    @Transactional
    public ReversalDecisionView approveReversal(UUID requestId, String decisionNote, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        PaymentReversalRequest request = requireRequest(requestId);
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new FinanceNotFoundException(
                        "payment-not-found", "Encaissement introuvable", "L'encaissement associé est introuvable."));
        if (reversalRepository.existsByOriginalPaymentId(payment.getId())) {
            throw new FinanceConflictException(
                    "payment-already-reversed",
                    "Encaissement déjà contre-passé",
                    "Cet encaissement a déjà été contre-passé.");
        }

        StudentInvoice invoice = invoiceRepository.findByIdForUpdate(payment.getInvoiceId())
                .orElseThrow(() -> new FinanceNotFoundException(
                        "invoice-not-found", "Facture introuvable", "La facture associée est introuvable."));

        Instant now = clock.instant();
        request.approve(actorId, now, decisionNote);
        request = requestRepository.save(request);

        PaymentReversal reversal = reversalRepository.save(new PaymentReversal(
                payment.getId(),
                request.getId(),
                payment.getInvoiceId(),
                payment.getStudentId(),
                payment.getAmountXof(),
                payment.getPaymentMethod(),
                request.getReason(),
                actorId,
                now));

        invoice.reversePayment(payment.getAmountXof());
        invoiceRepository.save(invoice);

        List<PaymentAllocation> allocations = allocationRepository.findByPaymentId(payment.getId());
        List<PaymentSchedule> schedules = scheduleRepository.findByInvoiceIdOrderByInstallmentNoAsc(invoice.getId());
        Map<UUID, PaymentSchedule> schedulesById = new HashMap<>();
        for (PaymentSchedule schedule : schedules) {
            schedulesById.put(schedule.getId(), schedule);
        }
        for (PaymentAllocation allocation : allocations) {
            PaymentSchedule schedule = schedulesById.get(allocation.getScheduleId());
            if (schedule != null) {
                schedule.reversePayment(allocation.getAmountXof());
            }
        }
        scheduleRepository.saveAll(schedules);

        UUID originalLedgerEntryId = ledgerEntryRepository
                .findFirstByReferenceTypeAndReferenceIdAndEntryType(
                        LedgerReferenceType.PAYMENT, payment.getId(), LedgerEntryType.PAYMENT_CREDIT)
                .map(LedgerEntry::getId)
                .orElse(null);

        ledgerPostingHelper.appendEntry(
                payment.getStudentId(),
                payment.getInvoiceId(),
                LedgerEntryType.PAYMENT_REVERSAL,
                payment.getAmountXof(),
                LedgerReferenceType.PAYMENT_REVERSAL,
                reversal.getId(),
                originalLedgerEntryId,
                "Contre-passation encaissement : " + request.getReason(),
                actorId,
                now);

        auditLogPort.recordTenantEvent(AuditRecordCommand.critical(
                AuditCategory.FINANCE,
                AuditAction.PAYMENT_REVERSAL_APPROVED,
                AuditOutcome.SUCCESS,
                "PAYMENT_REVERSAL",
                reversal.getId(),
                "Approbation et exécution d'une contre-passation d'encaissement.",
                Map.of(
                        "originalPaymentId", payment.getId().toString(),
                        "invoiceId", payment.getInvoiceId().toString(),
                        "amountXof", payment.getAmountXof())));

        return new ReversalDecisionView(request, reversal);
    }

    @PreAuthorize(FinanceRoles.APPROVAL_ROLES)
    @Transactional
    public PaymentReversalRequest rejectReversal(UUID requestId, String decisionNote, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        PaymentReversalRequest request = requireRequest(requestId);
        request.reject(actorId, clock.instant(), decisionNote);
        PaymentReversalRequest saved = requestRepository.save(request);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.FINANCE,
                AuditAction.PAYMENT_REVERSAL_REJECTED,
                "PAYMENT_REVERSAL_REQUEST",
                saved.getId(),
                "Rejet d'une demande de contre-passation d'encaissement.",
                Map.of("paymentId", saved.getPaymentId().toString(), "amountXof", saved.getAmountXof())));
        return saved;
    }

    private PaymentReversalRequest requireRequest(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "reversal-request-not-found",
                        "Demande de contre-passation introuvable",
                        "La demande de contre-passation est introuvable."));
    }
}
