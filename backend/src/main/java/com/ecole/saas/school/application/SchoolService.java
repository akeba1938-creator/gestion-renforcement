package com.ecole.saas.school.application;

import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.school.domain.School;
import com.ecole.saas.school.domain.SchoolStatus;
import com.ecole.saas.school.domain.SchoolType;
import com.ecole.saas.school.infrastructure.SchoolRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif interne de base du module {@code school} et implémentation de la frontière
 * publique {@link SchoolDirectory}. Les cas d'usage REST sécurisés par rôle passent par
 * {@link PlatformSchoolService} et {@link TenantSchoolService}.
 */
@Service
public class SchoolService implements SchoolDirectory {

    private final SchoolRepository schoolRepository;

    public SchoolService(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    @PreAuthorize("hasRole('ADMIN_SAAS')")
    @Transactional
    public School createSchool(String code, String name, SchoolType type, String phone, String email, String address) {
        School school = new School(code, name, type, phone, email, address);
        return schoolRepository.save(school);
    }

    @PreAuthorize("hasRole('ADMIN_SAAS')")
    @Transactional(readOnly = true)
    public Optional<School> findById(UUID id) {
        return schoolRepository.findById(id);
    }

    @PreAuthorize("hasRole('ADMIN_SAAS')")
    @Transactional(readOnly = true)
    public Optional<School> findByCode(String code) {
        return schoolRepository.findByCode(code);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SchoolSummary> findSummaryByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return schoolRepository.findByCode(normalized).map(SchoolService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SchoolSummary> findSummaryById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return schoolRepository.findById(id).map(SchoolService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SchoolProfileSnapshot> findProfileById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return schoolRepository.findById(id).map(SchoolService::toProfileSnapshot);
    }

    private static SchoolSummary toSummary(School school) {
        return new SchoolSummary(
                school.getId(),
                school.getCode(),
                school.getName(),
                school.getStatus() == SchoolStatus.ACTIVE);
    }

    private static SchoolProfileSnapshot toProfileSnapshot(School school) {
        return new SchoolProfileSnapshot(
                school.getId(),
                school.getCode(),
                school.getName(),
                school.getShortName(),
                school.getSchoolType().name(),
                school.getStatus().name(),
                school.isActive(),
                school.getTimezone(),
                school.getCurrency(),
                school.getCountryCode(),
                school.getGradingPeriodSystem().name(),
                school.getGradingScale());
    }
}
