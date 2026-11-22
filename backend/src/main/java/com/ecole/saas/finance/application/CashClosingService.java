package com.ecole.saas.finance.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.finance.domain.CashClosing;
import com.ecole.saas.finance.domain.CashClosingStatus;
import com.ecole.saas.finance.domain.FinanceConflictException;
import com.ecole.saas.finance.domain.FinanceNotFoundException;
import com.ecole.saas.finance.domain.PaymentMethod;
import com.ecole.saas.finance.infrastructure.CashClosingRepository;
import com.ecole.saas.finance.infrastructure.FinanceLockRepository;
import com.ecole.saas.finance.infrastructure.PaymentRepository;
import com.ecole.saas.finance.infrastructure.PaymentReversalRepository;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des clôtures journalières de caisse avec calcul automatique de l'attendu espèces et constat d'écart.
 */
@Service
public class CashClosingService {

    private final CashClosingRepository cashClosingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentReversalRepository reversalRepository;
    private final FinanceLockRepository financeLockRepository;
    private final FinanceSchoolContext schoolContext;
    private final FinanceSubscriptionGuard subscriptionGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public CashClosingService(
            CashClosingRepository cashClosingRepository,
            PaymentRepository paymentRepository,
            PaymentReversalRepository reversalRepository,
            FinanceLockRepository financeLockRepository,
            FinanceSchoolContext schoolContext,
            FinanceSubscriptionGuard subscriptionGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.cashClosingRepository = cashClosingRepository;
        this.paymentRepository = paymentRepository;
        this.reversalRepository = reversalRepository;
        this.financeLockRepository = financeLockRepository;
        this.schoolContext = schoolContext;
        this.subscriptionGuard = subscriptionGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(FinanceRoles.CASH_CLOSING_READ_ROLES)
    @Transactional(readOnly = true)
    public Page<CashClosing> listCashClosings(
            CashClosingStatus status, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Specification<CashClosing> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("closingDate"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("closingDate"), toDate));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return cashClosingRepository.findAll(spec, pageable);
    }

    @PreAuthorize(FinanceRoles.CASH_CLOSING_READ_ROLES)
    @Transactional(readOnly = true)
    public CashClosing getCashClosing(UUID closingId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        return requireClosing(closingId);
    }

    @PreAuthorize(FinanceRoles.CASH_CLOSING_SUBMIT_ROLES)
    @Transactional
    public CashClosing submitCashClosing(
            LocalDate closingDate, long countedCashXof, String notes, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Objects.requireNonNull(closingDate, "La date de clôture est obligatoire.");
        financeLockRepository.lockCashClosingDate(closingDate);
        if (cashClosingRepository.existsByClosingDate(closingDate)) {
            throw new FinanceConflictException(
                    "cash-closing-already-exists",
                    "Clôture déjà enregistrée",
                    "Une clôture de caisse existe déjà pour la date " + closingDate + ".");
        }

        SchoolProfileSnapshot school = schoolContext.requireSchoolProfile();
        ZoneId zone = schoolContext.resolveSchoolZone(school);
        Instant startOfDay = closingDate.atStartOfDay(zone).toInstant();
        Instant nextDayStart = closingDate.plusDays(1).atStartOfDay(zone).toInstant();

        PaymentRepository.CashMethodAggregate cashInAggregate = paymentRepository
                .aggregateByPaymentMethodAndPaidAtRange(PaymentMethod.CASH, startOfDay, nextDayStart);
        PaymentReversalRepository.CashReversalAggregate cashOutAggregate = reversalRepository
                .aggregateByPaymentMethodAndReversedAtRange(PaymentMethod.CASH, startOfDay, nextDayStart);

        long cashInXof = cashInAggregate != null ? cashInAggregate.getTotalAmountXof() : 0L;
        int cashPaymentCount = cashInAggregate != null ? Math.toIntExact(cashInAggregate.getOperationCount()) : 0;
        long cashOutReversalsXof = cashOutAggregate != null ? cashOutAggregate.getTotalAmountXof() : 0L;
        int cashReversalCount = cashOutAggregate != null ? Math.toIntExact(cashOutAggregate.getOperationCount()) : 0;

        CashClosing closing = new CashClosing(
                closingDate,
                cashInXof,
                cashOutReversalsXof,
                countedCashXof,
                cashPaymentCount,
                cashReversalCount,
                notes,
                actorId,
                clock.instant());
        CashClosing saved = cashClosingRepository.save(closing);
        Map<String, Object> meta = Map.of(
                "closingDate", saved.getClosingDate().toString(),
                "expectedCashXof", saved.getExpectedCashXof(),
                "countedCashXof", saved.getCountedCashXof(),
                "discrepancyXof", saved.getDiscrepancyXof());
        auditLogPort.recordTenantEvent(saved.getDiscrepancyXof() != 0L
                ? AuditRecordCommand.warning(
                        AuditCategory.FINANCE,
                        AuditAction.CASH_CLOSING_SUBMITTED,
                        AuditOutcome.SUCCESS,
                        "CASH_CLOSING",
                        saved.getId(),
                        "Soumission d'une clôture journalière de caisse avec écart constaté.",
                        meta)
                : AuditRecordCommand.success(
                        AuditCategory.FINANCE,
                        AuditAction.CASH_CLOSING_SUBMITTED,
                        "CASH_CLOSING",
                        saved.getId(),
                        "Soumission d'une clôture journalière de caisse.",
                        meta));
        return saved;
    }

    @PreAuthorize(FinanceRoles.APPROVAL_ROLES)
    @Transactional
    public CashClosing validateCashClosing(UUID closingId, String validationNote, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        CashClosing closing = requireClosingForUpdate(closingId);
        closing.validate(actorId, clock.instant(), validationNote);
        CashClosing saved = cashClosingRepository.save(closing);
        Map<String, Object> meta = Map.of(
                "closingDate", saved.getClosingDate().toString(),
                "expectedCashXof", saved.getExpectedCashXof(),
                "countedCashXof", saved.getCountedCashXof(),
                "discrepancyXof", saved.getDiscrepancyXof());
        auditLogPort.recordTenantEvent(saved.getDiscrepancyXof() != 0L
                ? AuditRecordCommand.warning(
                        AuditCategory.FINANCE,
                        AuditAction.CASH_CLOSING_VALIDATED,
                        AuditOutcome.SUCCESS,
                        "CASH_CLOSING",
                        saved.getId(),
                        "Validation d'une clôture journalière de caisse avec écart.",
                        meta)
                : AuditRecordCommand.success(
                        AuditCategory.FINANCE,
                        AuditAction.CASH_CLOSING_VALIDATED,
                        "CASH_CLOSING",
                        saved.getId(),
                        "Validation d'une clôture journalière de caisse.",
                        meta));
        return saved;
    }

    private CashClosing requireClosing(UUID closingId) {
        return cashClosingRepository.findById(closingId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "cash-closing-not-found",
                        "Clôture de caisse introuvable",
                        "La clôture de caisse demandée est introuvable."));
    }

    private CashClosing requireClosingForUpdate(UUID closingId) {
        return cashClosingRepository.findByIdForUpdate(closingId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "cash-closing-not-found",
                        "Clôture de caisse introuvable",
                        "La clôture de caisse demandée est introuvable."));
    }
}
