package com.ecole.saas.finance.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.finance.domain.AdjustmentCalculationMode;
import com.ecole.saas.finance.domain.AdjustmentType;
import com.ecole.saas.finance.domain.ApprovalStatus;
import com.ecole.saas.finance.domain.FeeAdjustment;
import com.ecole.saas.finance.domain.FinanceConflictException;
import com.ecole.saas.finance.domain.FinanceNotFoundException;
import com.ecole.saas.finance.domain.FinanceValidationException;
import com.ecole.saas.finance.domain.InvoiceStatus;
import com.ecole.saas.finance.domain.LedgerReferenceType;
import com.ecole.saas.finance.domain.PaymentSchedule;
import com.ecole.saas.finance.domain.ScheduleAllocator;
import com.ecole.saas.finance.domain.StudentInvoice;
import com.ecole.saas.finance.infrastructure.FeeAdjustmentRepository;
import com.ecole.saas.finance.infrastructure.PaymentScheduleRepository;
import com.ecole.saas.finance.infrastructure.StudentInvoiceRepository;
import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
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
 * Workflow encadré de demande et approbation de remises et bourses sur factures
 * émises.
 */
@Service
public class FeeAdjustmentService {

    private final FeeAdjustmentRepository adjustmentRepository;
    private final StudentInvoiceRepository invoiceRepository;
    private final PaymentScheduleRepository scheduleRepository;
    private final LedgerPostingHelper ledgerPostingHelper;
    private final FinanceSubscriptionGuard subscriptionGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public FeeAdjustmentService(
            FeeAdjustmentRepository adjustmentRepository,
            StudentInvoiceRepository invoiceRepository,
            PaymentScheduleRepository scheduleRepository,
            LedgerPostingHelper ledgerPostingHelper,
            FinanceSubscriptionGuard subscriptionGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.adjustmentRepository = adjustmentRepository;
        this.invoiceRepository = invoiceRepository;
        this.scheduleRepository = scheduleRepository;
        this.ledgerPostingHelper = ledgerPostingHelper;
        this.subscriptionGuard = subscriptionGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<FeeAdjustment> listAdjustments(
            UUID invoiceId,
            UUID studentId,
            AdjustmentType type,
            ApprovalStatus status,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Specification<FeeAdjustment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (invoiceId != null) {
                predicates.add(cb.equal(root.get("invoiceId"), invoiceId));
            }
            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("adjustmentType"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return adjustmentRepository.findAll(spec, pageable);
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public FeeAdjustment getAdjustment(UUID adjustmentId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        return requireAdjustment(adjustmentId);
    }

    @PreAuthorize(FinanceRoles.ADJUSTMENT_REQUEST_ROLES)
    @Transactional
    public FeeAdjustment requestAdjustment(
            UUID invoiceId,
            AdjustmentType adjustmentType,
            AdjustmentCalculationMode calculationMode,
            Long fixedAmountXof,
            Integer percentageValue,
            String reason,
            UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        StudentInvoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "invoice-not-found", "Facture introuvable", "La facture demandée est introuvable."));
        if (invoice.getStatus() != InvoiceStatus.ISSUED && invoice.getStatus() != InvoiceStatus.PARTIALLY_PAID) {
            throw new FinanceConflictException(
                    "invoice-not-adjustable",
                    "Facture non éligible",
                    "Une remise ou bourse ne peut être demandée que sur une facture émise ou partiellement payée.");
        }

        Instant now = clock.instant();
        FeeAdjustment adjustment;
        if (calculationMode == AdjustmentCalculationMode.FIXED_AMOUNT) {
            if (fixedAmountXof == null) {
                throw new FinanceValidationException(
                        "adjustment-amount-required",
                        "Montant requis",
                        "Le montant fixe en FCFA est obligatoire pour ce mode de calcul.");
            }
            adjustment = FeeAdjustment.requestFixed(
                    invoice.getId(), invoice.getStudentId(), adjustmentType, fixedAmountXof, reason, actorId, now);
        } else if (calculationMode == AdjustmentCalculationMode.PERCENTAGE) {
            if (percentageValue == null) {
                throw new FinanceValidationException(
                        "adjustment-percentage-required",
                        "Pourcentage requis",
                        "Le pourcentage entier (1..100) est obligatoire pour ce mode de calcul.");
            }
            adjustment = FeeAdjustment.requestPercentage(
                    invoice.getId(),
                    invoice.getStudentId(),
                    adjustmentType,
                    percentageValue,
                    invoice.getGrossAmountXof(),
                    reason,
                    actorId,
                    now);
        } else {
            throw new FinanceValidationException(
                    "adjustment-mode-required",
                    "Mode de calcul invalide",
                    "Le mode de calcul de la remise ou bourse est obligatoire.");
        }

        long pendingAdjustmentsXof = adjustmentRepository.sumResolvedAmountByInvoiceIdAndStatus(
                invoice.getId(), ApprovalStatus.PENDING_APPROVAL);
        long availableBalanceXof = Math.max(0L, invoice.getBalanceXof() - pendingAdjustmentsXof);
        if (adjustment.getResolvedAmountXof() > availableBalanceXof) {
            throw new FinanceConflictException(
                    "adjustment-exceeds-balance",
                    "Remise supérieure au solde",
                    "Le montant de la remise ou bourse (" + adjustment.getResolvedAmountXof()
                            + " FCFA) dépasse le solde disponible (" + availableBalanceXof
                            + " FCFA, compte tenu de " + pendingAdjustmentsXof
                            + " FCFA de demandes déjà en attente).");
        }

        FeeAdjustment saved = adjustmentRepository.save(adjustment);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.FINANCE,
                AuditAction.FEE_ADJUSTMENT_REQUESTED,
                "FEE_ADJUSTMENT",
                saved.getId(),
                "Demande de remise ou bourse sur une facture.",
                Map.of(
                        "invoiceId", saved.getInvoiceId().toString(),
                        "adjustmentType", saved.getAdjustmentType().name(),
                        "resolvedAmountXof", saved.getResolvedAmountXof())));
        return saved;
    }

    @PreAuthorize(FinanceRoles.APPROVAL_ROLES)
    @Transactional
    public FeeAdjustment approveAdjustment(UUID adjustmentId, String decisionNote, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        FeeAdjustment adjustment = requireAdjustmentForUpdate(adjustmentId);
        StudentInvoice invoice = invoiceRepository.findByIdForUpdate(adjustment.getInvoiceId())
                .orElseThrow(() -> new FinanceNotFoundException(
                        "invoice-not-found", "Facture introuvable", "La facture associée est introuvable."));

        Instant now = clock.instant();
        adjustment.approve(actorId, now, decisionNote);
        invoice.applyApprovedAdjustment(adjustment.getResolvedAmountXof());

        List<PaymentSchedule> schedules = scheduleRepository.findByInvoiceIdOrderByInstallmentNoAsc(invoice.getId());
        ScheduleAllocator.reduceFromLastInstallment(schedules, adjustment.getResolvedAmountXof());

        adjustment = adjustmentRepository.save(adjustment);
        invoiceRepository.save(invoice);
        scheduleRepository.saveAll(schedules);

        ledgerPostingHelper.appendEntry(
                invoice.getStudentId(),
                invoice.getId(),
                adjustment.ledgerEntryType(),
                adjustment.getResolvedAmountXof(),
                LedgerReferenceType.ADJUSTMENT,
                adjustment.getId(),
                null,
                adjustment.getReason(),
                actorId,
                now);

        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.FINANCE,
                AuditAction.FEE_ADJUSTMENT_APPROVED,
                AuditOutcome.SUCCESS,
                "FEE_ADJUSTMENT",
                adjustment.getId(),
                "Approbation d'une remise ou bourse financière.",
                Map.of(
                        "invoiceId", invoice.getId().toString(),
                        "adjustmentType", adjustment.getAdjustmentType().name(),
                        "resolvedAmountXof", adjustment.getResolvedAmountXof())));

        return adjustment;
    }

    @PreAuthorize(FinanceRoles.APPROVAL_ROLES)
    @Transactional
    public FeeAdjustment rejectAdjustment(UUID adjustmentId, String decisionNote, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        FeeAdjustment adjustment = requireAdjustmentForUpdate(adjustmentId);
        adjustment.reject(actorId, clock.instant(), decisionNote);
        FeeAdjustment saved = adjustmentRepository.save(adjustment);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.FINANCE,
                AuditAction.FEE_ADJUSTMENT_REJECTED,
                "FEE_ADJUSTMENT",
                saved.getId(),
                "Rejet d'une demande de remise ou bourse financière.",
                Map.of(
                        "invoiceId", saved.getInvoiceId().toString(),
                        "resolvedAmountXof", saved.getResolvedAmountXof())));
        return saved;
    }

    private FeeAdjustment requireAdjustment(UUID adjustmentId) {
        return adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "adjustment-not-found",
                        "Demande d'ajustement introuvable",
                        "La demande de remise ou bourse est introuvable."));
    }

    private FeeAdjustment requireAdjustmentForUpdate(UUID adjustmentId) {
        return adjustmentRepository.findByIdForUpdate(adjustmentId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "adjustment-not-found",
                        "Demande d'ajustement introuvable",
                        "La demande de remise ou bourse est introuvable."));
    }
}
