package com.ecole.saas.grade.application;

import com.ecole.saas.grade.domain.GradeNotFoundException;
import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import org.springframework.stereotype.Component;

@Component
class GradeSchoolContext {

    private final SchoolDirectory schoolDirectory;

    GradeSchoolContext(SchoolDirectory schoolDirectory) {
        this.schoolDirectory = schoolDirectory;
    }

    SchoolProfileSnapshot requireSchoolProfile() {
        TenantId tenantId = TenantContext.requireCurrent();
        return schoolDirectory.findProfileById(tenantId.value())
                .orElseThrow(() -> new GradeNotFoundException(
                        "school-not-found",
                        "Établissement introuvable",
                        "Impossible de charger le profil de l'établissement courant."));
    }
}
