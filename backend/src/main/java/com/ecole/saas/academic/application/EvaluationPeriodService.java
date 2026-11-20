package com.ecole.saas.academic.application;

import com.ecole.saas.academic.domain.AcademicYear;
import com.ecole.saas.academic.domain.AcademicYearNotFoundException;
import com.ecole.saas.academic.domain.EvaluationPeriod;
import com.ecole.saas.academic.domain.EvaluationPeriodConflictException;
import com.ecole.saas.academic.domain.EvaluationPeriodNotFoundException;
import com.ecole.saas.academic.domain.EvaluationPeriodPolicy;
import com.ecole.saas.academic.domain.PeriodSystem;
import com.ecole.saas.academic.infrastructure.AcademicYearRepository;
import com.ecole.saas.academic.infrastructure.EvaluationPeriodRepository;
import com.ecole.saas.shared.domain.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif de gestion des périodes d'évaluation (trimestres, semestres, périodes personnalisées).
 */
@Service
public class EvaluationPeriodService {

    private static final String READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'SECRETARY', 'CASHIER', 'TEACHER', 'TUTOR')";
    private static final String WRITE_ROLES = "hasAnyRole('DIRECTOR', 'CENTER_MANAGER')";

    private final AcademicYearRepository academicYearRepository;
    private final EvaluationPeriodRepository evaluationPeriodRepository;
    private final Clock clock;

    public EvaluationPeriodService(
            AcademicYearRepository academicYearRepository,
            EvaluationPeriodRepository evaluationPeriodRepository,
            Clock clock) {
        this.academicYearRepository = academicYearRepository;
        this.evaluationPeriodRepository = evaluationPeriodRepository;
        this.clock = clock;
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Page<EvaluationPeriod> listPeriodsForYear(UUID academicYearId, Pageable pageable) {
        TenantContext.requireCurrent();
        requireAcademicYear(academicYearId);
        return evaluationPeriodRepository.findByAcademicYearIdOrderBySequenceOrderAsc(academicYearId, pageable);
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public EvaluationPeriod getPeriod(UUID periodId) {
        TenantContext.requireCurrent();
        return requirePeriod(periodId);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public EvaluationPeriod createPeriod(
            UUID academicYearId,
            String code,
            String name,
            PeriodSystem periodType,
            int sequenceOrder,
            LocalDate startDate,
            LocalDate endDate,
            Integer weight) {
        TenantContext.requireCurrent();
        AcademicYear year = requireAcademicYear(academicYearId);
        year.assertMutable();

        String normalizedCode = EvaluationPeriod.normalizeCode(code);
        if (evaluationPeriodRepository.existsByAcademicYearIdAndCode(academicYearId, normalizedCode)) {
            throw new EvaluationPeriodConflictException(
                    "Une période d'évaluation avec ce code existe déjà pour cette année scolaire.");
        }
        if (evaluationPeriodRepository.existsByAcademicYearIdAndSequenceOrder(academicYearId, sequenceOrder)) {
            throw new EvaluationPeriodConflictException(
                    "Une période d'évaluation avec cet ordre existe déjà pour cette année scolaire.");
        }

        PeriodSystem effectiveType = periodType != null ? periodType : year.getPeriodSystem();
        List<EvaluationPeriod> existing =
                evaluationPeriodRepository.findByAcademicYearIdOrderBySequenceOrderAsc(academicYearId);

        EvaluationPeriodPolicy.validateWithinAcademicYear(year, startDate, endDate);
        EvaluationPeriodPolicy.validateAgainstPeriodSystem(year, effectiveType, existing.size());
        EvaluationPeriodPolicy.validateNoOverlap(startDate, endDate, existing, null);

        int effectiveWeight = weight != null ? weight : EvaluationPeriod.DEFAULT_WEIGHT;
        EvaluationPeriod period = new EvaluationPeriod(
                academicYearId,
                normalizedCode,
                name,
                effectiveType,
                sequenceOrder,
                startDate,
                endDate,
                effectiveWeight);
        return evaluationPeriodRepository.save(period);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public List<EvaluationPeriod> generateStandardPeriods(UUID academicYearId) {
        TenantContext.requireCurrent();
        AcademicYear year = requireAcademicYear(academicYearId);
        year.assertMutable();

        if (evaluationPeriodRepository.countByAcademicYearId(academicYearId) > 0) {
            throw new EvaluationPeriodConflictException(
                    "Des périodes d'évaluation existent déjà pour cette année scolaire.");
        }
        List<EvaluationPeriod> generated = EvaluationPeriodPolicy.buildStandardPeriods(year);
        return evaluationPeriodRepository.saveAll(generated);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public EvaluationPeriod updatePeriod(
            UUID periodId,
            String name,
            int sequenceOrder,
            LocalDate startDate,
            LocalDate endDate,
            int weight) {
        TenantContext.requireCurrent();
        EvaluationPeriod period = requirePeriod(periodId);
        AcademicYear year = requireAcademicYear(period.getAcademicYearId());
        year.assertMutable();

        if (evaluationPeriodRepository.existsByAcademicYearIdAndSequenceOrderAndIdNot(
                period.getAcademicYearId(), sequenceOrder, periodId)) {
            throw new EvaluationPeriodConflictException(
                    "Une autre période d'évaluation utilise déjà cet ordre pour cette année scolaire.");
        }

        List<EvaluationPeriod> existing =
                evaluationPeriodRepository.findByAcademicYearIdOrderBySequenceOrderAsc(period.getAcademicYearId());
        EvaluationPeriodPolicy.validateWithinAcademicYear(year, startDate, endDate);
        EvaluationPeriodPolicy.validateNoOverlap(startDate, endDate, existing, periodId);

        period.updateDetails(name, sequenceOrder, startDate, endDate, weight);
        return evaluationPeriodRepository.save(period);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public EvaluationPeriod openPeriod(UUID periodId) {
        TenantContext.requireCurrent();
        EvaluationPeriod period = requirePeriod(periodId);
        requireAcademicYear(period.getAcademicYearId()).assertMutable();
        period.open();
        return evaluationPeriodRepository.save(period);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public EvaluationPeriod closePeriod(UUID periodId) {
        TenantContext.requireCurrent();
        EvaluationPeriod period = requirePeriod(periodId);
        requireAcademicYear(period.getAcademicYearId()).assertMutable();
        period.close();
        return evaluationPeriodRepository.save(period);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public EvaluationPeriod reopenPeriod(UUID periodId) {
        TenantContext.requireCurrent();
        EvaluationPeriod period = requirePeriod(periodId);
        requireAcademicYear(period.getAcademicYearId()).assertMutable();
        period.reopen();
        return evaluationPeriodRepository.save(period);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public EvaluationPeriod lockPeriod(UUID periodId) {
        TenantContext.requireCurrent();
        EvaluationPeriod period = requirePeriod(periodId);
        requireAcademicYear(period.getAcademicYearId()).assertMutable();
        period.lock(clock.instant());
        return evaluationPeriodRepository.save(period);
    }

    private AcademicYear requireAcademicYear(UUID academicYearId) {
        return academicYearRepository.findById(academicYearId)
                .orElseThrow(() -> new AcademicYearNotFoundException("Année scolaire introuvable."));
    }

    private EvaluationPeriod requirePeriod(UUID periodId) {
        return evaluationPeriodRepository.findById(periodId)
                .orElseThrow(() -> new EvaluationPeriodNotFoundException("Période d'évaluation introuvable."));
    }
}
