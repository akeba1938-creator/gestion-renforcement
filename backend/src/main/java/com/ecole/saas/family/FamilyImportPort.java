package com.ecole.saas.family;

import java.util.UUID;

/**
 * Frontière publique Spring Modulith d'écriture contrôlée du module {@code family}
 * consommée unidirectionnellement par {@code schoolimport}.
 */
public interface FamilyImportPort {

    FamilySummary createFamilyForImport(ImportFamilyCommand command);

    FamilySummary updateFamilyForImport(UUID familyId, ImportFamilyCommand command);

    GuardianSummary createGuardianForImport(ImportGuardianCommand command);

    GuardianSummary updateGuardianForImport(UUID guardianId, ImportGuardianCommand command);

    StudentGuardianSummary linkOrUpdateGuardianForImport(ImportStudentGuardianLinkCommand command);
}
