package com.ecole.saas.school.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SchoolTest {

    @Test
    void shouldCreateSchoolWithNormalizedFieldsAndDefaults() {
        School school = new School(
                "  ecole-dakar_01 ",
                "  Cours Sainte-Marie de Hann  ",
                SchoolType.PRIVATE_SCHOOL,
                "+221771234567",
                "Contact@CSMH.SN",
                "Dakar Hann");

        assertThat(school.getCode()).isEqualTo("ECOLE-DAKAR_01");
        assertThat(school.getName()).isEqualTo("Cours Sainte-Marie de Hann");
        assertThat(school.getEmail()).isEqualTo("contact@csmh.sn");
        assertThat(school.getStatus()).isEqualTo(SchoolStatus.ACTIVE);
        assertThat(school.isActive()).isTrue();
        assertThat(school.getTimezone()).isEqualTo("Africa/Dakar");
        assertThat(school.getCurrency()).isEqualTo("XOF");
        assertThat(school.getCountryCode()).isEqualTo("SN");
        assertThat(school.getGradingPeriodSystem()).isEqualTo(GradingPeriodSystem.TRIMESTER);
        assertThat(school.getGradingScale()).isEqualTo(20);
    }

    @Test
    void shouldRejectInvalidPhoneTimezoneAndGradingScale() {
        assertThatThrownBy(() -> new School(
                "ECOLE-1", "École Test", SchoolType.PRIVATE_SCHOOL, "771234567", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("E.164");

        assertThatThrownBy(() -> new School(
                "ECOLE-1", "École Test", null, SchoolType.PRIVATE_SCHOOL, null, null, null,
                null, null, "SN", null, null, null, "Mars/Olympus", "XOF",
                GradingPeriodSystem.TRIMESTER, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fuseau horaire");

        assertThatThrownBy(() -> new School(
                "ECOLE-1", "École Test", null, SchoolType.PRIVATE_SCHOOL, null, null, null,
                null, null, "SN", null, null, null, "Africa/Dakar", "XOF",
                GradingPeriodSystem.TRIMESTER, 15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("barème");
    }

    @Test
    void shouldEnforceStrictStatusLifecycle() {
        School school = new School(
                "CENTRE-THIES", "Centre Excellence", SchoolType.TUTORING_CENTER, "+221770000010", null, null);
        Instant now = Instant.parse("2026-10-01T10:00:00Z");

        assertThatThrownBy(school::activate)
                .isInstanceOf(InvalidSchoolTransitionException.class);

        school.suspend("Impayé prolongé", now);
        assertThat(school.getStatus()).isEqualTo(SchoolStatus.SUSPENDED);
        assertThat(school.getStatusReason()).isEqualTo("Impayé prolongé");
        assertThat(school.getSuspendedAt()).isEqualTo(now);

        assertThatThrownBy(() -> school.updateTenantProfile(
                "Nouveau nom", null, null, null, null, null, null, "SN", null, null, null,
                "Africa/Dakar", GradingPeriodSystem.SEMESTER, 20))
                .isInstanceOf(SchoolNotActiveException.class);

        school.activate();
        assertThat(school.getStatus()).isEqualTo(SchoolStatus.ACTIVE);
        assertThat(school.getSuspendedAt()).isNull();
        assertThat(school.getStatusReason()).isNull();

        school.archive("Fermeture définitive", now.plusSeconds(3600));
        assertThat(school.getStatus()).isEqualTo(SchoolStatus.ARCHIVED);
        assertThat(school.getArchivedAt()).isEqualTo(now.plusSeconds(3600));

        assertThatThrownBy(() -> school.suspend("Impossible", now))
                .isInstanceOf(InvalidSchoolTransitionException.class);
        assertThatThrownBy(school::activate)
                .isInstanceOf(InvalidSchoolTransitionException.class);
        assertThatThrownBy(() -> school.archive("Doublon", now))
                .isInstanceOf(InvalidSchoolTransitionException.class);
    }

    @Test
    void shouldUpdateTenantProfileWithoutAlteringImmutableFields() {
        School school = new School(
                "ECOLE-SL", "Institution Saint-Louis", SchoolType.PRIVATE_SCHOOL, "+221770000020", null, null);

        school.updateTenantProfile(
                "Institution Saint-Louis Privée",
                "ISL",
                "+221770000021",
                "direction@isl.sn",
                "Île de Saint-Louis",
                "Saint-Louis",
                "Saint-Louis",
                "sn",
                "Travail et Discipline",
                "IA-SL-2026-04",
                "https://isl.sn",
                "Africa/Dakar",
                GradingPeriodSystem.SEMESTER,
                20);

        assertThat(school.getCode()).isEqualTo("ECOLE-SL");
        assertThat(school.getSchoolType()).isEqualTo(SchoolType.PRIVATE_SCHOOL);
        assertThat(school.getCurrency()).isEqualTo("XOF");
        assertThat(school.getShortName()).isEqualTo("ISL");
        assertThat(school.getCity()).isEqualTo("Saint-Louis");
        assertThat(school.getCountryCode()).isEqualTo("SN");
        assertThat(school.getGradingPeriodSystem()).isEqualTo(GradingPeriodSystem.SEMESTER);
    }
}
