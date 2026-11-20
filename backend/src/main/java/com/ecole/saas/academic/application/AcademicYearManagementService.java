package com.ecole.saas.academic.application;

import com.ecole.saas.academic.domain.AcademicYear;
import com.ecole.saas.academic.domain.AcademicYearAlreadyExistsException;
import com.ecole.saas.academic.domain.AcademicYearNotFoundException;
import com.ecole.saas.academic.domain.AcademicYearStatus;
import com.ecole.saas.academic.domain.EvaluationPeriod;
import com.ecole.saas.academic.domain.EvaluationPeriodConflictException;
import com.ecole.saas.academic.domain.EvaluationPeriodPolicy;
import com.ecole.saas.academic.domain.PeriodSystem;
import com.ecole.saas.academic.infrastructure.AcademicYearRepository;
import com.ecole.saas.academic.infrastructure.EvaluationPeriodRepository;
import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.shared.domain.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif sécurisé par rôle pour la gestion du cycle de vie des années scolaires
 * du tenant courant.
 */
@Service
public class AcademicYearManagementService {

    private static final Logger log = LoggerFactory.getLogger(AcademicYearManagementService.class);

    private static final String READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'SECRETARY', 'CASHIER', 'TEACHER', 'TUTOR')";
    private static final String WRITE_ROLES = "hasAnyRole('DIRECTOR', 'CENTER_MANAGER')";

    private final AcademicYearRepository academicYearRepository;
    private final EvaluationPeriodRepository evaluationPeriodRepository;
    private final SchoolDirectory schoolDirectory;
    private final Clock clock;

    public AcademicYearManagementService(
            AcademicYearRepository academicYearRepository,
            EvaluationPeriodRepository evaluationPeriodRepository,
            SchoolDirectory schoolDirectory,
            Clock clock) {
        this.academicYearRepository = academicYearRepository;
        this.evaluationPeriodRepository = evaluationPeriodRepository;
        this.schoolDirectory = schoolDirectory;
        this.clock = clock;
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Page<AcademicYear> listAcademicYears(AcademicYearStatus status, Pageable pageable) {
        TenantContext.requireCurrent();
        if (status != null) {
            return academicYearRepository.findByStatus(status, pageable);
        }
        return academicYearRepository.findAll(pageable);
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public AcademicYear getCurrentAcademicYear() {
        TenantContext.requireCurrent();
        return academicYearRepository.findByCurrentTrue()
                .orElseThrow(() -> new AcademicYearNotFoundException(
                        "Aucune année scolaire courante n'est définie pour cet établissement."));
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public AcademicYear getAcademicYear(UUID yearId) {
        TenantContext.requireCurrent();
        return requireAcademicYear(yearId);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public AcademicYear createAcademicYear(
            String code,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            PeriodSystem periodSystem) {
        UUID tenantId = TenantContext.requireCurrent().value();
        String normalizedCode = AcademicYear.normalizeCode(code);
        if (academicYearRepository.existsByCode(normalizedCode)) {
            throw new AcademicYearAlreadyExistsException("Une année scolaire avec ce code existe déjà.");
        }
        PeriodSystem resolvedSystem = periodSystem != null ? periodSystem : resolveDefaultPeriodSystem(tenantId);
        AcademicYear year = new AcademicYear(
                normalizedCode,
                label,
                startDate,
                endDate,
                resolvedSystem,
                AcademicYearStatus.DRAFT,
                false);
        AcademicYear saved = academicYearRepository.save(year);
        log.info("Année scolaire créée (yearId={}, code={}, system={})", saved.getId(), saved.getCode(), resolvedSystem);
        return saved;
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public AcademicYear updateAcademicYear(
            UUID yearId,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            PeriodSystem periodSystem) {
        TenantContext.requireCurrent();
        AcademicYear year = requireAcademicYear(yearId);
        year.assertMutable();

        List<EvaluationPeriod> periods = evaluationPeriodRepository.findByAcademicYearIdOrderBySequenceOrderAsc(yearId);
        if (!periods.isEmpty()) {
            EvaluationPeriodPolicy.validatePeriodsWithinNewYearBounds(startDate, endDate, periods);
            if (periodSystem != null && periodSystem != year.getPeriodSystem()) {
                throw new EvaluationPeriodConflictException(
                        "Impossible de changer le système de périodes tant que des périodes d'évaluation existent déjà.");
            }
        }

        year.updateDetails(label, startDate, endDate, periodSystem);
        return academicYearRepository.save(year);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public AcademicYear activateAcademicYear(UUID yearId, boolean makeCurrent) {
        TenantContext.requireCurrent();
        AcademicYear year = requireAcademicYear(yearId);
        if (makeCurrent) {
            clearCurrentYearIfOther(yearId);
        }
        year.activate(makeCurrent);
        AcademicYear saved = academicYearRepository.save(year);
        log.info("Année scolaire activée (yearId={}, current={})", saved.getId(), saved.isCurrent());
        return saved;
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public AcademicYear setCurrentAcademicYear(UUID yearId) {
        TenantContext.requireCurrent();
        AcademicYear year = requireAcademicYear(yearId);
        if (year.isCurrent()) {
            return year;
        }
        // Vérifier la transition avant de retirer le drapeau sur l'année courante précédente
        if (year.getStatus() != AcademicYearStatus.ACTIVE) {
            year.markAsCurrent();
        }
        clearCurrentYearIfOther(yearId);
        year.markAsCurrent();
        AcademicYear saved = academicYearRepository.save(year);
        log.info("Année scolaire définie comme courante (yearId={})", saved.getId());
        return saved;
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public AcademicYear closeAcademicYear(UUID yearId) {
        TenantContext.requireCurrent();
        AcademicYear year = requireAcademicYear(yearId);
        year.close(clock.instant());
        AcademicYear saved = academicYearRepository.save(year);
        log.info("Année scolaire clôturée (yearId={})", saved.getId());
        return saved;
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public AcademicYear archiveAcademicYear(UUID yearId) {
        TenantContext.requireCurrent();
        AcademicYear year = requireAcademicYear(yearId);
        year.archive(clock.instant());
        AcademicYear saved = academicYearRepository.save(year);
        log.info("Année scolaire archivée (yearId={})", saved.getId());
        return saved;
    }

    private AcademicYear requireAcademicYear(UUID yearId) {
        return academicYearRepository.findById(yearId)
                .orElseThrow(() -> new AcademicYearNotFoundException("Année scolaire introuvable."));
    }

    private void clearCurrentYearIfOther(UUID targetYearId) {
        academicYearRepository.findByCurrentTrue().ifPresent(currentYear -> {
            if (!currentYear.getId().equals(targetYearId)) {
                currentYear.unmarkAsCurrent();
                academicYearRepository.saveAndFlush(currentYear);
            }
        });
    }

    private PeriodSystem resolveDefaultPeriodSystem(UUID tenantId) {
        return schoolDirectory.findProfileById(tenantId)
                .map(profile -> {
                    try {
                        return PeriodSystem.valueOf(profile.gradingPeriodSystem());
                    } catch (IllegalArgumentException ex) {
                        return PeriodSystem.TRIMESTER;
                    }
                })
                .orElse(PeriodSystem.TRIMESTER);
    }
}
