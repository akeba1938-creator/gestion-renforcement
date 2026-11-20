package com.ecole.saas.center.application;

import com.ecole.saas.center.domain.CenterConflictException;
import com.ecole.saas.center.domain.CenterNotFoundException;
import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class CenterSchoolGuard {

    private final SchoolDirectory schoolDirectory;

    CenterSchoolGuard(SchoolDirectory schoolDirectory) {
        this.schoolDirectory = schoolDirectory;
    }

    SchoolProfileSnapshot requireCurrentSchoolProfile() {
        UUID tenantId = TenantContext.requireCurrent().value();
        return schoolDirectory.findProfileById(tenantId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-school-not-found",
                        "Établissement introuvable",
                        "L'établissement courant est introuvable."));
    }

    SchoolProfileSnapshot requireActiveSchoolForWrite() {
        SchoolProfileSnapshot profile = requireCurrentSchoolProfile();
        if (!profile.active()) {
            throw new CenterConflictException(
                    "center-school-inactive",
                    "Établissement inactif",
                    "Les opérations d'écriture sont impossibles sur un établissement non actif.");
        }
        return profile;
    }
}
