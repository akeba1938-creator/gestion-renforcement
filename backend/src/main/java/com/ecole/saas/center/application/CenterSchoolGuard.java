package com.ecole.saas.center.application;

import com.ecole.saas.center.domain.CenterConflictException;
import com.ecole.saas.center.domain.CenterNotFoundException;
import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class CenterSchoolGuard {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Dakar");

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

    ZoneId resolveSchoolZone(SchoolProfileSnapshot profile) {
        if (profile == null || profile.timezone() == null || profile.timezone().isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(profile.timezone());
        } catch (DateTimeException ex) {
            return DEFAULT_ZONE;
        }
    }

    LocalDate todayInSchoolZone(Clock clock, SchoolProfileSnapshot profile) {
        return LocalDate.now(clock.withZone(resolveSchoolZone(profile)));
    }
}
