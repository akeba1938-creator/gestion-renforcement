package com.ecole.saas.finance.application;

import com.ecole.saas.finance.domain.CashClosing;
import com.ecole.saas.finance.domain.CashClosingStatus;
import com.ecole.saas.finance.domain.FinanceConflictException;
import com.ecole.saas.finance.domain.FinanceNotFoundException;
import com.ecole.saas.finance.domain.MoneyMath;
import com.ecole.saas.finance.domain.Payment;
import com.ecole.saas.finance.domain.PaymentMethod;
import com.ecole.saas.finance.domain.PaymentReversal;
import com.ecole.saas.finance.infrastructure.CashClosingRepository;
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
    private final FinanceSchoolContext schoolContext;
    private final FinanceSubscriptionGuard subscriptionGuard;
    private final Clock clock;

    public CashClosingService(
            CashClosingRepository cashClosingRepository,
            PaymentRepository paymentRepository,
            PaymentReversalRepository reversalRepository,
            FinanceSchoolContext schoolContext,
            FinanceSubscriptionGuard subscriptionGuard,
            Clock clock) {
        this.cashClosingRepository = cashClosingRepository;
        this.paymentRepository = paymentRepository;
        this.reversalRepository = reversalRepository;
        this.schoolContext = schoolContext;
        this.subscriptionGuard = subscriptionGuard;
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

        List<Payment> cashPayments = paymentRepository
                .findByPaymentMethodAndPaidAtGreaterThanEqualAndPaidAtLessThan(
                        PaymentMethod.CASH, startOfDay, nextDayStart);
        List<PaymentReversal> cashReversals = reversalRepository
                .findByPaymentMethodAndReversedAtGreaterThanEqualAndReversedAtLessThan(
                        PaymentMethod.CASH, startOfDay, nextDayStart);

        long cashInXof = cashPayments.stream()
                .map(Payment::getAmountXof)
                .reduce(0L, MoneyMath::addExact);
        long cashOutReversalsXof = cashReversals.stream()
                .map(PaymentReversal::getAmountXof)
                .reduce(0L, MoneyMath::addExact);

        CashClosing closing = new CashClosing(
                closingDate,
                cashInXof,
                cashOutReversalsXof,
                countedCashXof,
                cashPayments.size(),
                cashReversals.size(),
                notes,
                actorId,
                clock.instant());
        return cashClosingRepository.save(closing);
    }

    @PreAuthorize(FinanceRoles.APPROVAL_ROLES)
    @Transactional
    public CashClosing validateCashClosing(UUID closingId, String validationNote, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        CashClosing closing = requireClosing(closingId);
        closing.validate(actorId, clock.instant(), validationNote);
        return cashClosingRepository.save(closing);
    }

    private CashClosing requireClosing(UUID closingId) {
        return cashClosingRepository.findById(closingId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "cash-closing-not-found",
                        "Clôture de caisse introuvable",
                        "La clôture de caisse demandée est introuvable."));
    }
}
