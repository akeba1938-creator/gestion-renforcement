package com.ecole.saas.family.application;

import com.ecole.saas.family.FamilyDirectoryPort;
import com.ecole.saas.family.FamilySummary;
import com.ecole.saas.family.GuardianContactSummary;
import com.ecole.saas.family.GuardianSummary;
import com.ecole.saas.family.StudentGuardianSummary;
import com.ecole.saas.family.domain.Family;
import com.ecole.saas.family.domain.Guardian;
import com.ecole.saas.family.domain.GuardianStatus;
import com.ecole.saas.family.domain.StudentGuardianLink;
import com.ecole.saas.family.infrastructure.FamilyRepository;
import com.ecole.saas.family.infrastructure.GuardianRepository;
import com.ecole.saas.family.infrastructure.StudentGuardianLinkRepository;
import com.ecole.saas.shared.domain.TenantContext;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de la frontière publique {@link FamilyDirectoryPort} consommée par les autres modules.
 */
@Service
public class FamilyDirectoryQueryService implements FamilyDirectoryPort {

    private final FamilyRepository familyRepository;
    private final GuardianRepository guardianRepository;
    private final StudentGuardianLinkRepository linkRepository;

    public FamilyDirectoryQueryService(
            FamilyRepository familyRepository,
            GuardianRepository guardianRepository,
            StudentGuardianLinkRepository linkRepository) {
        this.familyRepository = familyRepository;
        this.guardianRepository = guardianRepository;
        this.linkRepository = linkRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FamilySummary> findFamilyById(UUID familyId) {
        TenantContext.requireCurrent();
        if (familyId == null) {
            return Optional.empty();
        }
        return familyRepository.findById(familyId).map(FamilyDirectoryQueryService::toFamilySummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FamilySummary> findFamiliesByCodes(Collection<String> codes) {
        TenantContext.requireCurrent();
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = codes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (normalized.isEmpty()) {
            return List.of();
        }
        return familyRepository.findByCodeIn(normalized).stream()
                .map(FamilyDirectoryQueryService::toFamilySummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GuardianSummary> findGuardianById(UUID guardianId) {
        TenantContext.requireCurrent();
        if (guardianId == null) {
            return Optional.empty();
        }
        return guardianRepository.findById(guardianId).map(FamilyDirectoryQueryService::toGuardianSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuardianContactSummary> findGuardiansByPhones(Collection<String> phones) {
        TenantContext.requireCurrent();
        if (phones == null || phones.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = phones.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        if (normalized.isEmpty()) {
            return List.of();
        }
        return guardianRepository.findByPhoneIn(normalized).stream()
                .map(FamilyDirectoryQueryService::toGuardianContactSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GuardianContactSummary> findGuardianContactById(UUID guardianId) {
        TenantContext.requireCurrent();
        if (guardianId == null) {
            return Optional.empty();
        }
        return guardianRepository.findById(guardianId).map(FamilyDirectoryQueryService::toGuardianContactSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GuardianContactSummary> findGuardianContactByUserId(UUID userId) {
        TenantContext.requireCurrent();
        if (userId == null) {
            return Optional.empty();
        }
        return guardianRepository.findByUserId(userId).map(FamilyDirectoryQueryService::toGuardianContactSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuardianContactSummary> findGuardianContactsByIds(Collection<UUID> guardianIds) {
        TenantContext.requireCurrent();
        if (guardianIds == null || guardianIds.isEmpty()) {
            return List.of();
        }
        return guardianRepository.findAllById(guardianIds).stream()
                .map(FamilyDirectoryQueryService::toGuardianContactSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuardianContactSummary> findActiveGuardiansByFamilyId(UUID familyId) {
        TenantContext.requireCurrent();
        if (familyId == null) {
            return List.of();
        }
        return guardianRepository
                .findByFamilyIdAndStatusOrderByLastNameAscFirstNameAsc(familyId, GuardianStatus.ACTIVE)
                .stream()
                .map(FamilyDirectoryQueryService::toGuardianContactSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuardianContactSummary> findAllActiveGuardians() {
        TenantContext.requireCurrent();
        return guardianRepository.findByStatusOrderByLastNameAscFirstNameAsc(GuardianStatus.ACTIVE).stream()
                .map(FamilyDirectoryQueryService::toGuardianContactSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentGuardianSummary> findGuardiansByStudentId(UUID studentId) {
        TenantContext.requireCurrent();
        if (studentId == null) {
            return List.of();
        }
        List<StudentGuardianLink> links =
                linkRepository.findByStudentIdAndActiveTrueOrderByPrimaryContactDescCreatedAtAsc(studentId);
        return mapStudentGuardianLinks(links);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentGuardianSummary> findActiveGuardiansByStudentIds(Collection<UUID> studentIds) {
        TenantContext.requireCurrent();
        if (studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        List<StudentGuardianLink> links =
                linkRepository.findByStudentIdInAndActiveTrueOrderByPrimaryContactDescCreatedAtAsc(studentIds);
        return mapStudentGuardianLinks(links);
    }

    private List<StudentGuardianSummary> mapStudentGuardianLinks(List<StudentGuardianLink> links) {
        if (links.isEmpty()) {
            return List.of();
        }
        Set<UUID> guardianIds = links.stream().map(StudentGuardianLink::getGuardianId).collect(Collectors.toSet());
        Map<UUID, Guardian> guardiansById = guardianRepository.findAllById(guardianIds).stream()
                .collect(Collectors.toMap(Guardian::getId, Function.identity()));
        return links.stream()
                .map(link -> toStudentGuardianSummary(link, guardiansById.get(link.getGuardianId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentGuardianSummary> findPrimaryContactByStudentId(UUID studentId) {
        TenantContext.requireCurrent();
        if (studentId == null) {
            return Optional.empty();
        }
        return linkRepository.findByStudentIdAndActiveTrueAndPrimaryContactTrue(studentId)
                .map(link -> toStudentGuardianSummary(
                        link, guardianRepository.findById(link.getGuardianId()).orElse(null)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentGuardianSummary> findFinancialResponsibleByStudentId(UUID studentId) {
        TenantContext.requireCurrent();
        if (studentId == null) {
            return Optional.empty();
        }
        return linkRepository.findByStudentIdAndActiveTrueAndFinancialResponsibleTrue(studentId)
                .map(link -> toStudentGuardianSummary(
                        link, guardianRepository.findById(link.getGuardianId()).orElse(null)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findStudentIdsByGuardianId(UUID guardianId) {
        TenantContext.requireCurrent();
        if (guardianId == null) {
            return List.of();
        }
        return linkRepository.findByGuardianIdAndActiveTrue(guardianId).stream()
                .map(StudentGuardianLink::getStudentId)
                .distinct()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findStudentIdsByFamilyId(UUID familyId) {
        TenantContext.requireCurrent();
        if (familyId == null) {
            return List.of();
        }
        List<UUID> guardianIds = guardianRepository.findByFamilyId(familyId).stream()
                .map(Guardian::getId)
                .toList();
        if (guardianIds.isEmpty()) {
            return List.of();
        }
        return linkRepository.findByGuardianIdInAndActiveTrue(guardianIds).stream()
                .map(StudentGuardianLink::getStudentId)
                .distinct()
                .toList();
    }

    private static FamilySummary toFamilySummary(Family family) {
        return new FamilySummary(
                family.getId(),
                family.getCode(),
                family.getName(),
                family.getPrimaryPhone(),
                family.getEmail(),
                family.getCity(),
                family.getStatus().name(),
                family.isActive());
    }

    private static GuardianSummary toGuardianSummary(Guardian guardian) {
        return new GuardianSummary(
                guardian.getId(),
                guardian.getFamilyId(),
                guardian.getFirstName(),
                guardian.getLastName(),
                guardian.getPhone(),
                guardian.getSecondaryPhone(),
                guardian.getEmail(),
                guardian.getProfession(),
                guardian.getStatus().name(),
                guardian.isActive());
    }

    private static GuardianContactSummary toGuardianContactSummary(Guardian guardian) {
        return new GuardianContactSummary(
                guardian.getId(),
                guardian.getFamilyId(),
                guardian.getUserId(),
                guardian.getFirstName(),
                guardian.getLastName(),
                guardian.getPhone(),
                guardian.getSecondaryPhone(),
                guardian.getEmail(),
                guardian.getPreferredLanguage(),
                guardian.isActive());
    }

    private static StudentGuardianSummary toStudentGuardianSummary(StudentGuardianLink link, Guardian guardian) {
        return new StudentGuardianSummary(
                link.getId(),
                link.getStudentId(),
                link.getGuardianId(),
                guardian != null ? guardian.getFamilyId() : null,
                guardian != null ? guardian.getFirstName() : null,
                guardian != null ? guardian.getLastName() : null,
                guardian != null ? guardian.getPhone() : null,
                guardian != null ? guardian.getEmail() : null,
                link.getRelationshipType().name(),
                link.isPrimaryContact(),
                link.isFinancialResponsible(),
                link.isLegalCustody(),
                link.isEmergencyContact(),
                link.isAuthorizedPickup(),
                link.isActive());
    }
}
