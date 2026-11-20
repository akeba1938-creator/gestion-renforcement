package com.ecole.saas.finance.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.academic.GradeLevelSummary;
import com.ecole.saas.finance.domain.FeeCategory;
import com.ecole.saas.finance.domain.FeeFrequency;
import com.ecole.saas.finance.domain.FeeItem;
import com.ecole.saas.finance.domain.FinanceCodeFormatter;
import com.ecole.saas.finance.domain.FinanceConflictException;
import com.ecole.saas.finance.domain.FinanceNotFoundException;
import com.ecole.saas.finance.infrastructure.FeeItemRepository;
import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion du catalogue des frais (scolarité, inscription, annexes) par établissement et année scolaire.
 */
@Service
public class FeeItemService {

    private final FeeItemRepository feeItemRepository;
    private final AcademicStructurePort academicStructurePort;
    private final FinanceSubscriptionGuard subscriptionGuard;

    public FeeItemService(
            FeeItemRepository feeItemRepository,
            AcademicStructurePort academicStructurePort,
            FinanceSubscriptionGuard subscriptionGuard) {
        this.feeItemRepository = feeItemRepository;
        this.academicStructurePort = academicStructurePort;
        this.subscriptionGuard = subscriptionGuard;
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<FeeItem> listFeeItems(
            UUID academicYearId,
            UUID gradeLevelId,
            FeeCategory category,
            Boolean active,
            String search,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Specification<FeeItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (academicYearId != null) {
                predicates.add(cb.equal(root.get("academicYearId"), academicYearId));
            }
            if (gradeLevelId != null) {
                predicates.add(cb.equal(root.get("gradeLevelId"), gradeLevelId));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("label")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return feeItemRepository.findAll(spec, pageable);
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public FeeItem getFeeItem(UUID feeItemId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        return requireFeeItem(feeItemId);
    }

    @PreAuthorize(FinanceRoles.CATALOG_WRITE_ROLES)
    @Transactional
    public FeeItem createFeeItem(
            UUID academicYearId,
            UUID gradeLevelId,
            String code,
            String label,
            FeeCategory category,
            FeeFrequency frequency,
            long amountXof,
            boolean mandatory,
            String description) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        AcademicYearSummary academicYear = requireMutableAcademicYear(academicYearId);
        if (gradeLevelId != null) {
            requireActiveGradeLevel(gradeLevelId);
        }

        String normalizedCode = FinanceCodeFormatter.normalizeFeeCode(code);
        if (feeItemRepository.existsByAcademicYearIdAndCode(academicYear.id(), normalizedCode)) {
            throw new FinanceConflictException(
                    "fee-item-already-exists",
                    "Code de frais déjà utilisé",
                    "Un frais avec le code '" + normalizedCode + "' existe déjà pour cette année scolaire.");
        }

        FeeItem item = new FeeItem(
                academicYear.id(),
                gradeLevelId,
                normalizedCode,
                label,
                category,
                frequency,
                amountXof,
                mandatory,
                description);
        return feeItemRepository.save(item);
    }

    @PreAuthorize(FinanceRoles.CATALOG_WRITE_ROLES)
    @Transactional
    public FeeItem updateFeeItem(
            UUID feeItemId,
            UUID gradeLevelId,
            String label,
            FeeCategory category,
            FeeFrequency frequency,
            long amountXof,
            boolean mandatory,
            String description) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        FeeItem existing = requireFeeItem(feeItemId);
        requireMutableAcademicYear(existing.getAcademicYearId());
        if (gradeLevelId != null) {
            requireActiveGradeLevel(gradeLevelId);
        }

        existing.update(gradeLevelId, label, category, frequency, amountXof, mandatory, description);
        return feeItemRepository.save(existing);
    }

    @PreAuthorize(FinanceRoles.CATALOG_WRITE_ROLES)
    @Transactional
    public FeeItem activateFeeItem(UUID feeItemId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        FeeItem existing = requireFeeItem(feeItemId);
        requireMutableAcademicYear(existing.getAcademicYearId());
        existing.activate();
        return feeItemRepository.save(existing);
    }

    @PreAuthorize(FinanceRoles.CATALOG_WRITE_ROLES)
    @Transactional
    public FeeItem deactivateFeeItem(UUID feeItemId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        FeeItem existing = requireFeeItem(feeItemId);
        requireMutableAcademicYear(existing.getAcademicYearId());
        existing.deactivate();
        return feeItemRepository.save(existing);
    }

    private FeeItem requireFeeItem(UUID feeItemId) {
        return feeItemRepository.findById(feeItemId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "fee-item-not-found", "Frais introuvable", "L'élément de frais demandé est introuvable."));
    }

    private AcademicYearSummary requireMutableAcademicYear(UUID academicYearId) {
        AcademicYearSummary year = academicStructurePort.findAcademicYearById(academicYearId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "academic-year-not-found",
                        "Année scolaire introuvable",
                        "L'année scolaire référencée est introuvable."));
        if ("CLOSED".equals(year.status()) || "ARCHIVED".equals(year.status())) {
            throw new FinanceConflictException(
                    "academic-year-immutable",
                    "Année scolaire verrouillée",
                    "Impossible de modifier le catalogue de frais d'une année scolaire clôturée ou archivée.");
        }
        return year;
    }

    private void requireActiveGradeLevel(UUID gradeLevelId) {
        GradeLevelSummary level = academicStructurePort.findGradeLevelById(gradeLevelId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "grade-level-not-found",
                        "Niveau scolaire introuvable",
                        "Le niveau scolaire référencé est introuvable."));
        if (!level.active()) {
            throw new FinanceConflictException(
                    "grade-level-inactive",
                    "Niveau scolaire inactif",
                    "Le niveau scolaire référencé est désactivé.");
        }
    }
}
