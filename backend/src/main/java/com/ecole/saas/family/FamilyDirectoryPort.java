package com.ecole.saas.family;

import java.util.Collection;
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

    List<FamilySummary> findFamiliesByCodes(Collection<String> codes);

    Optional<GuardianSummary> findGuardianById(UUID guardianId);

    List<GuardianContactSummary> findGuardiansByPhones(Collection<String> phones);

    Optional<GuardianContactSummary> findGuardianContactById(UUID guardianId);

    Optional<GuardianContactSummary> findGuardianContactByUserId(UUID userId);

    List<GuardianContactSummary> findGuardianContactsByIds(Collection<UUID> guardianIds);

    List<GuardianContactSummary> findActiveGuardiansByFamilyId(UUID familyId);

    List<GuardianContactSummary> findAllActiveGuardians();

    List<StudentGuardianSummary> findGuardiansByStudentId(UUID studentId);

    List<StudentGuardianSummary> findActiveGuardiansByStudentIds(Collection<UUID> studentIds);

    Optional<StudentGuardianSummary> findPrimaryContactByStudentId(UUID studentId);

    Optional<StudentGuardianSummary> findFinancialResponsibleByStudentId(UUID studentId);

    List<UUID> findStudentIdsByGuardianId(UUID guardianId);

    List<UUID> findStudentIdsByFamilyId(UUID familyId);
}
