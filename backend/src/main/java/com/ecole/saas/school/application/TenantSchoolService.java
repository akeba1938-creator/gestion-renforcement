package com.ecole.saas.school.application;

import com.ecole.saas.school.domain.GradingPeriodSystem;
import com.ecole.saas.school.domain.School;
import com.ecole.saas.school.domain.SchoolNotFoundException;
import com.ecole.saas.school.infrastructure.SchoolRepository;
import com.ecole.saas.shared.domain.TenantContext;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de consultation et mise à jour des informations de l'établissement courant.
 * L'établissement est exclusivement résolu depuis {@link TenantContext} (alimenté par le JWT côté serveur).
 */
@Service
public class TenantSchoolService {

    private final SchoolRepository schoolRepository;

    public TenantSchoolService(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    @PreAuthorize("hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER')")
    @Transactional(readOnly = true)
    public School getCurrentSchool() {
        UUID tenantId = TenantContext.requireCurrent().value();
        return schoolRepository.findById(tenantId)
                .orElseThrow(() -> new SchoolNotFoundException("Établissement introuvable."));
    }

    @PreAuthorize("hasAnyRole('DIRECTOR', 'CENTER_MANAGER')")
    @Transactional
    public School updateCurrentSchool(
            String name,
            String shortName,
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
            GradingPeriodSystem gradingPeriodSystem,
            Integer gradingScale) {
        UUID tenantId = TenantContext.requireCurrent().value();
        School school = schoolRepository.findById(tenantId)
                .orElseThrow(() -> new SchoolNotFoundException("Établissement introuvable."));
        school.updateTenantProfile(
                name,
                shortName,
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
                gradingPeriodSystem,
                gradingScale);
        return schoolRepository.save(school);
    }
}
