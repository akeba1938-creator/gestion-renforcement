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
import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.shared.domain.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public EvaluationPeriodService(
            AcademicYearRepository academicYearRepository,
            EvaluationPeriodRepository evaluationPeriodRepository,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.academicYearRepository = academicYearRepository;
        this.evaluationPeriodRepository = evaluationPeriodRepository;
        this.auditLogPort = auditLogPort;
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
        EvaluationPeriod saved = evaluationPeriodRepository.save(period);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.ACADEMIC,
                AuditAction.EVALUATION_PERIOD_CREATED,
                "EVALUATION_PERIOD",
                saved.getId(),
                "Création d'une période d'évaluation.",
                Map.of("academicYearId", academicYearId.toString(), "code", saved.getCode())));
        return saved;
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
        List<EvaluationPeriod> saved = evaluationPeriodRepository.saveAll(generated);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.ACADEMIC,
                AuditAction.EVALUATION_PERIOD_CREATED,
                "ACADEMIC_YEAR",
                academicYearId,
                "Génération automatique des périodes d'évaluation standard.",
                Map.of("periodCount", saved.size(), "periodSystem", year.getPeriodSystem().name())));
        return saved;
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
        EvaluationPeriod saved = evaluationPeriodRepository.save(period);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.ACADEMIC,
                AuditAction.EVALUATION_PERIOD_OPENED,
                "EVALUATION_PERIOD",
                saved.getId(),
                "Ouverture d'une période d'évaluation.",
                Map.of("code", saved.getCode(), "academicYearId", saved.getAcademicYearId().toString())));
        return saved;
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public EvaluationPeriod closePeriod(UUID periodId) {
        TenantContext.requireCurrent();
        EvaluationPeriod period = requirePeriod(periodId);
        requireAcademicYear(period.getAcademicYearId()).assertMutable();
        period.close();
        EvaluationPeriod saved = evaluationPeriodRepository.save(period);
        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.ACADEMIC,
                AuditAction.EVALUATION_PERIOD_CLOSED,
                AuditOutcome.SUCCESS,
                "EVALUATION_PERIOD",
                saved.getId(),
                "Clôture d'une période d'évaluation.",
                Map.of("code", saved.getCode(), "academicYearId", saved.getAcademicYearId().toString())));
        return saved;
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public EvaluationPeriod reopenPeriod(UUID periodId) {
        TenantContext.requireCurrent();
        EvaluationPeriod period = requirePeriod(periodId);
        requireAcademicYear(period.getAcademicYearId()).assertMutable();
        period.reopen();
        EvaluationPeriod saved = evaluationPeriodRepository.save(period);
        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.ACADEMIC,
                AuditAction.EVALUATION_PERIOD_REOPENED,
                AuditOutcome.SUCCESS,
                "EVALUATION_PERIOD",
                saved.getId(),
                "Réouverture d'une période d'évaluation clôturée.",
                Map.of("code", saved.getCode(), "academicYearId", saved.getAcademicYearId().toString())));
        return saved;
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public EvaluationPeriod lockPeriod(UUID periodId) {
        TenantContext.requireCurrent();
        EvaluationPeriod period = requirePeriod(periodId);
        requireAcademicYear(period.getAcademicYearId()).assertMutable();
        period.lock(clock.instant());
        EvaluationPeriod saved = evaluationPeriodRepository.save(period);
        auditLogPort.recordTenantEvent(AuditRecordCommand.critical(
                AuditCategory.ACADEMIC,
                AuditAction.EVALUATION_PERIOD_LOCKED,
                AuditOutcome.SUCCESS,
                "EVALUATION_PERIOD",
                saved.getId(),
                "Verrouillage définitif d'une période d'évaluation.",
                Map.of("code", saved.getCode(), "academicYearId", saved.getAcademicYearId().toString())));
        return saved;
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
