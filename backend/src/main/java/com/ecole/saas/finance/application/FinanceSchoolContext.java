package com.ecole.saas.finance.application;

import com.ecole.saas.finance.domain.FinanceNotFoundException;
import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
class FinanceSchoolContext {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Dakar");

    private final SchoolDirectory schoolDirectory;
    private final Clock clock;

    FinanceSchoolContext(SchoolDirectory schoolDirectory, Clock clock) {
        this.schoolDirectory = schoolDirectory;
        this.clock = clock;
    }

    SchoolProfileSnapshot requireSchoolProfile() {
        TenantId tenantId = TenantContext.requireCurrent();
        return schoolDirectory.findProfileById(tenantId.value())
                .orElseThrow(() -> new FinanceNotFoundException(
                        "school-not-found",
                        "Établissement introuvable",
                        "Impossible de charger le profil de l'établissement courant."));
    }

    ZoneId resolveSchoolZone(SchoolProfileSnapshot profile) {
        if (profile.timezone() == null || profile.timezone().isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(profile.timezone());
        } catch (DateTimeException ex) {
            return DEFAULT_ZONE;
        }
    }

    LocalDate todayInSchoolZone() {
        SchoolProfileSnapshot profile = requireSchoolProfile();
        return LocalDate.now(clock.withZone(resolveSchoolZone(profile)));
    }
}
