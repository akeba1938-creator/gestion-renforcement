package com.ecole.saas.school.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.audit.PlatformAuditRecordCommand;
import com.ecole.saas.school.domain.GradingPeriodSystem;
import com.ecole.saas.school.domain.School;
import com.ecole.saas.school.domain.SchoolAlreadyExistsException;
import com.ecole.saas.school.domain.SchoolNotFoundException;
import com.ecole.saas.school.domain.SchoolStatus;
import com.ecole.saas.school.domain.SchoolType;
import com.ecole.saas.school.infrastructure.SchoolRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif d'administration plateforme des établissements ({@code ADMIN_SAAS}).
 */
@Service
@PreAuthorize("hasRole('ADMIN_SAAS')")
public class PlatformSchoolService {

    private static final Logger log = LoggerFactory.getLogger(PlatformSchoolService.class);

    private final SchoolRepository schoolRepository;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public PlatformSchoolService(
            SchoolRepository schoolRepository,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.schoolRepository = schoolRepository;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<School> listSchools(
            SchoolStatus status,
            SchoolType schoolType,
            String search,
            Pageable pageable) {
        Specification<School> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (schoolType != null) {
                predicates.add(cb.equal(root.get("schoolType"), schoolType));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return schoolRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public School getSchool(UUID schoolId) {
        return requireSchool(schoolId);
    }

    @Transactional
    public School createSchool(
            String code,
            String name,
            String shortName,
            SchoolType schoolType,
            String phone,
            String email,
            String address,
            String city,
            String region,
            String countryCode,
            String motto,
            String ministryCode,
            String website,
            String timezone,
            String currency,
            GradingPeriodSystem gradingPeriodSystem,
            Integer gradingScale) {
        String normalizedCode = School.normalizeCode(code);
        if (schoolRepository.existsByCode(normalizedCode)) {
            throw new SchoolAlreadyExistsException("Un établissement avec ce code existe déjà.");
        }
        School school = new School(
                normalizedCode,
                name,
                shortName,
                schoolType,
                phone,
                email,
                address,
                city,
                region,
                countryCode,
                motto,
                ministryCode,
                website,
                timezone,
                currency,
                gradingPeriodSystem,
                gradingScale);
        School saved = schoolRepository.save(school);
        auditLogPort.recordPlatformEvent(PlatformAuditRecordCommand.success(
                AuditCategory.SCHOOL,
                AuditAction.SCHOOL_CREATED,
                saved.getId(),
                "SCHOOL",
                saved.getId(),
                "Création d'un établissement sur la plateforme.",
                null,
                Map.of(
                        "schoolCode", saved.getCode(),
                        "schoolType", saved.getSchoolType().name(),
                        "currency", saved.getCurrency())));
        log.info("Établissement créé (schoolId={}, code={}, type={})", saved.getId(), saved.getCode(), saved.getSchoolType());
        return saved;
    }

    @Transactional
    public School updateSchool(
            UUID schoolId,
            String name,
            String shortName,
            SchoolType schoolType,
            String phone,
            String email,
            String address,
            String city,
            String region,
            String countryCode,
            String motto,
            String ministryCode,
            String website,
            String timezone,
            String currency,
            GradingPeriodSystem gradingPeriodSystem,
            Integer gradingScale) {
        School school = requireSchool(schoolId);
        school.updatePlatformDetails(
                name,
                shortName,
                schoolType,
                phone,
                email,
                address,
                city,
                region,
                countryCode,
                motto,
                ministryCode,
                website,
                timezone,
                currency,
                gradingPeriodSystem,
                gradingScale);
        School saved = schoolRepository.save(school);
        auditLogPort.recordPlatformEvent(PlatformAuditRecordCommand.success(
                AuditCategory.SCHOOL,
                AuditAction.SCHOOL_UPDATED,
                saved.getId(),
                "SCHOOL",
                saved.getId(),
                "Mise à jour plateforme d'un établissement.",
                null,
                Map.of(
                        "schoolCode", saved.getCode(),
                        "schoolType", saved.getSchoolType().name())));
        return saved;
    }

    @Transactional
    public School suspendSchool(UUID schoolId, String reason) {
        School school = requireSchool(schoolId);
        school.suspend(reason, clock.instant());
        School saved = schoolRepository.save(school);
        auditLogPort.recordPlatformEvent(PlatformAuditRecordCommand.critical(
                AuditCategory.SCHOOL,
                AuditAction.SCHOOL_SUSPENDED,
                AuditOutcome.SUCCESS,
                saved.getId(),
                "SCHOOL",
                saved.getId(),
                "Suspension plateforme d'un établissement.",
                reason,
                Map.of("schoolCode", saved.getCode(), "status", saved.getStatus().name())));
        auditLogPort.recordTenantEventForSchool(
                saved.getId(),
                AuditRecordCommand.critical(
                        AuditCategory.SCHOOL,
                        AuditAction.SCHOOL_SUSPENDED,
                        AuditOutcome.SUCCESS,
                        "SCHOOL",
                        saved.getId(),
                        "Établissement suspendu par l'administration plateforme.",
                        Map.of("schoolCode", saved.getCode(), "status", saved.getStatus().name())));
        log.info("Établissement suspendu (schoolId={}, code={})", saved.getId(), saved.getCode());
        return saved;
    }

    @Transactional
    public School activateSchool(UUID schoolId) {
        School school = requireSchool(schoolId);
        school.activate();
        School saved = schoolRepository.save(school);
        auditLogPort.recordPlatformEvent(PlatformAuditRecordCommand.success(
                AuditCategory.SCHOOL,
                AuditAction.SCHOOL_REACTIVATED,
                saved.getId(),
                "SCHOOL",
                saved.getId(),
                "Réactivation plateforme d'un établissement.",
                null,
                Map.of("schoolCode", saved.getCode(), "status", saved.getStatus().name())));
        auditLogPort.recordTenantEventForSchool(
                saved.getId(),
                AuditRecordCommand.success(
                        AuditCategory.SCHOOL,
                        AuditAction.SCHOOL_REACTIVATED,
                        "SCHOOL",
                        saved.getId(),
                        "Établissement réactivé par l'administration plateforme.",
                        Map.of("schoolCode", saved.getCode(), "status", saved.getStatus().name())));
        log.info("Établissement réactivé (schoolId={}, code={})", saved.getId(), saved.getCode());
        return saved;
    }

    @Transactional
    public School archiveSchool(UUID schoolId, String reason) {
        School school = requireSchool(schoolId);
        school.archive(reason, clock.instant());
        School saved = schoolRepository.save(school);
        auditLogPort.recordPlatformEvent(PlatformAuditRecordCommand.critical(
                AuditCategory.SCHOOL,
                AuditAction.SCHOOL_ARCHIVED,
                AuditOutcome.SUCCESS,
                saved.getId(),
                "SCHOOL",
                saved.getId(),
                "Archivage définitif d'un établissement sur la plateforme.",
                reason,
                Map.of("schoolCode", saved.getCode(), "status", saved.getStatus().name())));
        auditLogPort.recordTenantEventForSchool(
                saved.getId(),
                AuditRecordCommand.critical(
                        AuditCategory.SCHOOL,
                        AuditAction.SCHOOL_ARCHIVED,
                        AuditOutcome.SUCCESS,
                        "SCHOOL",
                        saved.getId(),
                        "Établissement archivé par l'administration plateforme.",
                        Map.of("schoolCode", saved.getCode(), "status", saved.getStatus().name())));
        log.info("Établissement archivé (schoolId={}, code={})", saved.getId(), saved.getCode());
        return saved;
    }

    private School requireSchool(UUID schoolId) {
        return schoolRepository.findById(schoolId)
                .orElseThrow(() -> new SchoolNotFoundException("Établissement introuvable."));
    }
}
