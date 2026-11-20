package com.ecole.saas.finance.application;

import com.ecole.saas.family.FamilyDirectoryPort;
import com.ecole.saas.family.GuardianSummary;
import com.ecole.saas.finance.domain.FinanceCodeFormatter;
import com.ecole.saas.finance.domain.FinanceConflictException;
import com.ecole.saas.finance.domain.FinanceNotFoundException;
import com.ecole.saas.finance.domain.FinanceSequenceType;
import com.ecole.saas.finance.domain.LedgerEntryType;
import com.ecole.saas.finance.domain.LedgerReferenceType;
import com.ecole.saas.finance.domain.OpaqueTokenGenerator;
import com.ecole.saas.finance.domain.Payment;
import com.ecole.saas.finance.domain.PaymentAllocation;
import com.ecole.saas.finance.domain.PaymentConfirmationSource;
import com.ecole.saas.finance.domain.PaymentMethod;
import com.ecole.saas.finance.domain.PaymentReversal;
import com.ecole.saas.finance.domain.PaymentSchedule;
import com.ecole.saas.finance.domain.Receipt;
import com.ecole.saas.finance.domain.ScheduleAllocator;
import com.ecole.saas.finance.domain.StudentInvoice;
import com.ecole.saas.finance.infrastructure.FinanceSequenceRepository;
import com.ecole.saas.finance.infrastructure.PaymentAllocationRepository;
import com.ecole.saas.finance.infrastructure.PaymentRepository;
import com.ecole.saas.finance.infrastructure.PaymentReversalRepository;
import com.ecole.saas.finance.infrastructure.PaymentScheduleRepository;
import com.ecole.saas.finance.infrastructure.ReceiptRepository;
import com.ecole.saas.finance.infrastructure.StudentInvoiceRepository;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentSummary;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif des encaissements immuables, reçus séquentiels sans trou et vérification QR opaque.
 * L'encaissement est strictement réservé aux rôles {@code CASHIER} et {@code CENTER_MANAGER}
 * afin de préserver la séparation des tâches avec {@code DIRECTOR} et {@code PROMOTER}.
 */
@Service
public class PaymentService {

    public record PaymentView(
            Payment payment,
            Receipt receipt,
            List<PaymentAllocation> allocations,
            PaymentReversal reversal,
            boolean idempotentReplay) {

        public boolean reversed() {
            return reversal != null;
        }
    }

    public record ReceiptVerificationView(
            UUID receiptId,
            String receiptNumber,
            long amountXof,
            String currency,
            PaymentMethod paymentMethod,
            Instant issuedAt,
            String invoiceNumber,
            String studentMatricule,
            boolean reversed,
            Instant reversedAt) {
    }

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final ReceiptRepository receiptRepository;
    private final PaymentReversalRepository reversalRepository;
    private final StudentInvoiceRepository invoiceRepository;
    private final PaymentScheduleRepository scheduleRepository;
    private final FinanceSequenceRepository sequenceRepository;
    private final LedgerPostingHelper ledgerPostingHelper;
    private final StudentDirectoryPort studentDirectoryPort;
    private final FamilyDirectoryPort familyDirectoryPort;
    private final FinanceSchoolContext schoolContext;
    private final FinanceSubscriptionGuard subscriptionGuard;
    private final Clock clock;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentAllocationRepository allocationRepository,
            ReceiptRepository receiptRepository,
            PaymentReversalRepository reversalRepository,
            StudentInvoiceRepository invoiceRepository,
            PaymentScheduleRepository scheduleRepository,
            FinanceSequenceRepository sequenceRepository,
            LedgerPostingHelper ledgerPostingHelper,
            StudentDirectoryPort studentDirectoryPort,
            FamilyDirectoryPort familyDirectoryPort,
            FinanceSchoolContext schoolContext,
            FinanceSubscriptionGuard subscriptionGuard,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.receiptRepository = receiptRepository;
        this.reversalRepository = reversalRepository;
        this.invoiceRepository = invoiceRepository;
        this.scheduleRepository = scheduleRepository;
        this.sequenceRepository = sequenceRepository;
        this.ledgerPostingHelper = ledgerPostingHelper;
        this.studentDirectoryPort = studentDirectoryPort;
        this.familyDirectoryPort = familyDirectoryPort;
        this.schoolContext = schoolContext;
        this.subscriptionGuard = subscriptionGuard;
        this.clock = clock;
    }

    @PreAuthorize(FinanceRoles.PAYMENT_RECORD_ROLES)
    @Transactional
    public PaymentView recordPayment(
            UUID invoiceId,
            UUID paymentScheduleId,
            UUID payerGuardianId,
            long amountXof,
            PaymentMethod paymentMethod,
            String externalReference,
            String idempotencyKey,
            String notes,
            UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        String normalizedIdempotencyKey = Payment.normalizeIdempotencyKey(idempotencyKey);
        Optional<Payment> existingOpt = paymentRepository.findByIdempotencyKey(normalizedIdempotencyKey);
        if (existingOpt.isPresent()) {
            Payment existing = existingOpt.get();
            if (!existing.matchesIdempotentRequest(
                    invoiceId, amountXof, paymentMethod, externalReference, paymentScheduleId)) {
                throw new FinanceConflictException(
                        "idempotency-key-conflict",
                        "Conflit d'idempotence",
                        "Cette clé d'idempotence a déjà été utilisée pour un encaissement différent.");
            }
            return buildView(existing, true);
        }

        StudentInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "invoice-not-found", "Facture introuvable", "La facture demandée est introuvable."));

        UUID resolvedPayerGuardianId = resolvePayerGuardian(payerGuardianId, invoice.getPayerGuardianId());
        String normalizedRef = Payment.normalizeOptional(externalReference, 120);
        if (normalizedRef != null
                && paymentRepository.existsByPaymentMethodAndExternalReference(paymentMethod, normalizedRef)) {
            throw new FinanceConflictException(
                    "payment-reference-duplicate",
                    "Référence déjà enregistrée",
                    "Un encaissement avec cette référence externe existe déjà pour ce mode de paiement.");
        }

        Instant now = clock.instant();
        Payment payment = new Payment(
                invoice.getId(),
                invoice.getStudentId(),
                resolvedPayerGuardianId,
                paymentScheduleId,
                amountXof,
                paymentMethod,
                normalizedRef,
                PaymentConfirmationSource.MANUAL_DESK,
                normalizedIdempotencyKey,
                now,
                actorId,
                notes);

        invoice.applyPayment(amountXof);
        List<PaymentSchedule> schedules = scheduleRepository.findByInvoiceIdOrderByInstallmentNoAsc(invoice.getId());
        List<ScheduleAllocator.AllocationPlan> plans =
                ScheduleAllocator.allocatePayment(schedules, amountXof, paymentScheduleId);

        payment = paymentRepository.save(payment);
        invoiceRepository.save(invoice);
        scheduleRepository.saveAll(schedules);

        List<PaymentAllocation> allocations = new ArrayList<>(plans.size());
        for (ScheduleAllocator.AllocationPlan plan : plans) {
            allocations.add(new PaymentAllocation(payment.getId(), plan.schedule().getId(), plan.amountXof()));
        }
        allocations = allocationRepository.saveAll(allocations);

        SchoolProfileSnapshot school = schoolContext.requireSchoolProfile();
        LocalDate today = LocalDate.now(clock.withZone(schoolContext.resolveSchoolZone(school)));
        long receiptSeq = sequenceRepository.nextSequenceValue(FinanceSequenceType.RECEIPT, today.getYear());
        String receiptNumber = FinanceCodeFormatter.formatReceiptNumber(school.code(), today.getYear(), receiptSeq);
        String verificationToken = OpaqueTokenGenerator.generateVerificationToken();

        Receipt receipt = receiptRepository.save(new Receipt(
                payment.getId(),
                invoice.getId(),
                invoice.getStudentId(),
                receiptNumber,
                verificationToken,
                payment.getAmountXof(),
                payment.getPaymentMethod(),
                now,
                actorId));

        ledgerPostingHelper.appendEntry(
                invoice.getStudentId(),
                invoice.getId(),
                LedgerEntryType.PAYMENT_CREDIT,
                payment.getAmountXof(),
                LedgerReferenceType.PAYMENT,
                payment.getId(),
                null,
                "Encaissement " + receiptNumber + " (" + payment.getPaymentMethod() + ")",
                actorId,
                now);

        return new PaymentView(payment, receipt, allocations, null, false);
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<PaymentView> listPayments(
            UUID invoiceId,
            UUID studentId,
            PaymentMethod paymentMethod,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Specification<Payment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (invoiceId != null) {
                predicates.add(cb.equal(root.get("invoiceId"), invoiceId));
            }
            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            }
            if (paymentMethod != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), paymentMethod));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<Payment> page = paymentRepository.findAll(spec, pageable);
        List<UUID> paymentIds = page.getContent().stream().map(Payment::getId).toList();
        Map<UUID, Receipt> receiptsByPaymentId = new HashMap<>();
        Map<UUID, PaymentReversal> reversalsByPaymentId = new HashMap<>();
        if (!paymentIds.isEmpty()) {
            for (Receipt receipt : receiptRepository.findByPaymentIdIn(paymentIds)) {
                receiptsByPaymentId.put(receipt.getPaymentId(), receipt);
            }
            for (PaymentReversal reversal : reversalRepository.findByOriginalPaymentIdIn(paymentIds)) {
                reversalsByPaymentId.put(reversal.getOriginalPaymentId(), reversal);
            }
        }

        return page.map(payment -> new PaymentView(
                payment,
                receiptsByPaymentId.get(payment.getId()),
                List.of(),
                reversalsByPaymentId.get(payment.getId()),
                false));
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public PaymentView getPayment(UUID paymentId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "payment-not-found", "Encaissement introuvable", "L'encaissement demandé est introuvable."));
        return buildView(payment, false);
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<Receipt> listReceipts(UUID studentId, UUID invoiceId, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Specification<Receipt> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            }
            if (invoiceId != null) {
                predicates.add(cb.equal(root.get("invoiceId"), invoiceId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return receiptRepository.findAll(spec, pageable);
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public ReceiptVerificationView verifyReceiptByToken(String verificationToken) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        if (verificationToken == null || verificationToken.isBlank()) {
            throw new FinanceNotFoundException(
                    "receipt-not-found", "Reçu introuvable", "Aucun reçu ne correspond à ce jeton.");
        }
        Receipt receipt = receiptRepository.findByVerificationToken(verificationToken.trim())
                .orElseThrow(() -> new FinanceNotFoundException(
                        "receipt-not-found", "Reçu introuvable", "Aucun reçu ne correspond à ce jeton."));

        Optional<PaymentReversal> reversal = reversalRepository.findByOriginalPaymentId(receipt.getPaymentId());
        String invoiceNumber = invoiceRepository.findById(receipt.getInvoiceId())
                .map(StudentInvoice::getInvoiceNumber)
                .orElse("");
        String studentMatricule = studentDirectoryPort.findStudentById(receipt.getStudentId())
                .map(StudentSummary::matricule)
                .orElse("");

        return new ReceiptVerificationView(
                receipt.getId(),
                receipt.getReceiptNumber(),
                receipt.getAmountXof(),
                receipt.getCurrency(),
                receipt.getPaymentMethod(),
                receipt.getIssuedAt(),
                invoiceNumber,
                studentMatricule,
                reversal.isPresent(),
                reversal.map(PaymentReversal::getReversedAt).orElse(null));
    }

    private PaymentView buildView(Payment payment, boolean idempotentReplay) {
        Receipt receipt = receiptRepository.findByPaymentId(payment.getId()).orElse(null);
        List<PaymentAllocation> allocations = allocationRepository.findByPaymentId(payment.getId());
        PaymentReversal reversal = reversalRepository.findByOriginalPaymentId(payment.getId()).orElse(null);
        return new PaymentView(payment, receipt, allocations, reversal, idempotentReplay);
    }

    private UUID resolvePayerGuardian(UUID requestedGuardianId, UUID invoiceGuardianId) {
        if (requestedGuardianId == null) {
            return invoiceGuardianId;
        }
        GuardianSummary guardian = familyDirectoryPort.findGuardianById(requestedGuardianId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "guardian-not-found",
                        "Parent/tuteur introuvable",
                        "Le payeur référencé est introuvable."));
        if (!guardian.active()) {
            throw new FinanceConflictException(
                    "guardian-inactive",
                    "Parent/tuteur archivé",
                    "Le payeur référencé est archivé.");
        }
        return guardian.id();
    }
}
