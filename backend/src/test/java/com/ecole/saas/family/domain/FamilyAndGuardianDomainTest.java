package com.ecole.saas.family.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FamilyAndGuardianDomainTest {

    @Test
    void shouldValidateAndManageFamilyLifecycle() {
        Family family = new Family(
                " fam-001 ",
                " Famille Diallo ",
                "+221771001001",
                "Famille.Diallo@Example.SN",
                "Almadies",
                "Dakar",
                "Paiement trimestriel");

        assertThat(family.getCode()).isEqualTo("FAM-001");
        assertThat(family.getName()).isEqualTo("Famille Diallo");
        assertThat(family.getEmail()).isEqualTo("famille.diallo@example.sn");
        assertThat(family.isActive()).isTrue();

        Instant now = Instant.parse("2026-10-15T10:00:00Z");
        family.archive(now);
        assertThat(family.isActive()).isFalse();
        assertThat(family.getArchivedAt()).isEqualTo(now);

        assertThatThrownBy(() -> family.update("FAM-001", "Modif", null, null, null, null, null))
                .isInstanceOf(FamilyArchivedException.class);

        family.reactivate();
        assertThat(family.isActive()).isTrue();
        assertThat(family.getArchivedAt()).isNull();

        assertThatThrownBy(() -> new Family("bad code", "Famille", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Family(null, "Famille", "771001001", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldValidateGuardianPhoneEmailLanguageAndArchive() {
        UUID familyId = UUID.randomUUID();
        Guardian guardian = new Guardian(
                familyId,
                " Mamadou ",
                " Diallo ",
                GuardianGender.MALE,
                "+221771112233",
                "+221761112233",
                "M.Diallo@Example.SN",
                "Ingénieur",
                "Dakar",
                "1750198500123",
                null);

        assertThat(guardian.getFirstName()).isEqualTo("Mamadou");
        assertThat(guardian.getLastName()).isEqualTo("Diallo");
        assertThat(guardian.getPreferredLanguage()).isEqualTo("fr");
        assertThat(guardian.getEmail()).isEqualTo("m.diallo@example.sn");

        Instant now = Instant.parse("2026-10-15T10:00:00Z");
        guardian.archive(now);
        assertThat(guardian.isActive()).isFalse();
        assertThatThrownBy(() -> guardian.update(
                        familyId, "Mamadou", "Diallo", GuardianGender.MALE, "+221771112233",
                        null, null, null, null, null, "fr"))
                .isInstanceOf(GuardianArchivedException.class);

        guardian.reactivate();
        assertThat(guardian.isActive()).isTrue();

        assertThatThrownBy(() -> new Guardian(
                        null, "Fatou", "Ndiaye", GuardianGender.FEMALE, "770001122",
                        null, null, null, null, null, "fr"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Guardian(
                        null, "Fatou", "Ndiaye", GuardianGender.FEMALE, "+221770001122",
                        null, null, null, null, null, "INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldManageStudentGuardianLinkFlagsAndDeactivation() {
        StudentGuardianLink link = new StudentGuardianLink(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RelationshipType.MOTHER,
                true,
                true,
                true,
                true,
                true,
                "Contact prioritaire");

        assertThat(link.isActive()).isTrue();
        assertThat(link.isPrimaryContact()).isTrue();
        assertThat(link.isFinancialResponsible()).isTrue();

        link.clearPrimaryContact();
        assertThat(link.isPrimaryContact()).isFalse();
        assertThat(link.isFinancialResponsible()).isTrue();

        link.deactivate();
        assertThat(link.isActive()).isFalse();
        assertThat(link.isPrimaryContact()).isFalse();
        assertThat(link.isFinancialResponsible()).isFalse();

        assertThatThrownBy(() -> link.update(RelationshipType.MOTHER, true, false, true, true, true, null))
                .isInstanceOf(IllegalStateException.class);

        link.activate();
        assertThat(link.isActive()).isTrue();
        link.update(RelationshipType.LEGAL_GUARDIAN, true, false, true, true, false, "Mise à jour");
        assertThat(link.getRelationshipType()).isEqualTo(RelationshipType.LEGAL_GUARDIAN);
        assertThat(link.isPrimaryContact()).isTrue();
        assertThat(link.isAuthorizedPickup()).isFalse();
    }
}
