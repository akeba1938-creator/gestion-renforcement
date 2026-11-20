package com.ecole.saas.family;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique du module {@code family} exposée aux autres modules applicatifs
 * ({@code finance}, {@code communication}, {@code grade}, {@code attendance}, {@code reporting})
 * pour consulter les familles, tuteurs légaux et liens élève-tuteur du tenant courant.
 */
public interface FamilyDirectoryPort {

    Optional<FamilySummary> findFamilyById(UUID familyId);

    Optional<GuardianSummary> findGuardianById(UUID guardianId);

    List<StudentGuardianSummary> findGuardiansByStudentId(UUID studentId);

    Optional<StudentGuardianSummary> findPrimaryContactByStudentId(UUID studentId);

    Optional<StudentGuardianSummary> findFinancialResponsibleByStudentId(UUID studentId);

    List<UUID> findStudentIdsByGuardianId(UUID guardianId);

    List<UUID> findStudentIdsByFamilyId(UUID familyId);
}
