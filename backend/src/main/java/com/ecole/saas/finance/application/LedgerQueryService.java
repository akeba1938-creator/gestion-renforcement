package com.ecole.saas.finance.application;

import com.ecole.saas.finance.FamilyFinancialSummary;
import com.ecole.saas.finance.FinanceAccountPort;
import com.ecole.saas.finance.StudentFinancialSummary;
import com.ecole.saas.finance.domain.LedgerDirection;
import com.ecole.saas.finance.domain.LedgerEntry;
import com.ecole.saas.finance.domain.LedgerEntryType;
import com.ecole.saas.finance.infrastructure.LedgerEntryRepository;
import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultation en lecture seule du registre comptable immuable et des synthèses financières élève/famille.
 */
@Service
public class LedgerQueryService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final FinanceAccountPort financeAccountPort;
    private final FinanceSubscriptionGuard subscriptionGuard;

    public LedgerQueryService(
            LedgerEntryRepository ledgerEntryRepository,
            FinanceAccountPort financeAccountPort,
            FinanceSubscriptionGuard subscriptionGuard) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.financeAccountPort = financeAccountPort;
        this.subscriptionGuard = subscriptionGuard;
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<LedgerEntry> listLedgerEntries(
            UUID studentId,
            UUID invoiceId,
            LedgerEntryType entryType,
            LedgerDirection direction,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Specification<LedgerEntry> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            }
            if (invoiceId != null) {
                predicates.add(cb.equal(root.get("invoiceId"), invoiceId));
            }
            if (entryType != null) {
                predicates.add(cb.equal(root.get("entryType"), entryType));
            }
            if (direction != null) {
                predicates.add(cb.equal(root.get("direction"), direction));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return ledgerEntryRepository.findAll(spec, pageable);
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public StudentFinancialSummary getStudentSummary(UUID studentId, UUID academicYearId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        return financeAccountPort.getStudentFinancialSummary(studentId, academicYearId);
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public FamilyFinancialSummary getFamilySummary(UUID familyId, UUID academicYearId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        return financeAccountPort.getFamilyFinancialSummary(familyId, academicYearId);
    }
}
