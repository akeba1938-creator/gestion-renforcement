package com.ecole.saas.family.application;

import com.ecole.saas.family.FamilyImportPort;
import com.ecole.saas.family.FamilySummary;
import com.ecole.saas.family.GuardianSummary;
import com.ecole.saas.family.ImportFamilyCommand;
import com.ecole.saas.family.ImportGuardianCommand;
import com.ecole.saas.family.ImportStudentGuardianLinkCommand;
import com.ecole.saas.family.StudentGuardianSummary;
import com.ecole.saas.family.domain.Family;
import com.ecole.saas.family.domain.Guardian;
import com.ecole.saas.family.domain.GuardianGender;
import com.ecole.saas.family.domain.RelationshipType;
import com.ecole.saas.family.domain.StudentGuardianLink;
import com.ecole.saas.family.infrastructure.StudentGuardianLinkRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Adaptateur applicatif de {@link FamilyImportPort} déléguant aux services métier du module
 * afin de préserver intégralement RBAC, bascules atomiques contact principal/financier et audit.
 */
@Service
public class FamilyImportPortAdapter implements FamilyImportPort {

    private final FamilyService familyService;
    private final GuardianService guardianService;
    private final StudentGuardianService studentGuardianService;
    private final StudentGuardianLinkRepository linkRepository;

    public FamilyImportPortAdapter(
            FamilyService familyService,
            GuardianService guardianService,
            StudentGuardianService studentGuardianService,
            StudentGuardianLinkRepository linkRepository) {
        this.familyService = familyService;
        this.guardianService = guardianService;
        this.studentGuardianService = studentGuardianService;
        this.linkRepository = linkRepository;
    }

    @Override
    public FamilySummary createFamilyForImport(ImportFamilyCommand command) {
        Family saved = familyService.createFamily(
                command.code(),
                command.name(),
                command.primaryPhone(),
                command.email(),
                command.address(),
                command.city(),
                command.notes());
        return toFamilySummary(saved);
    }

    @Override
    public FamilySummary updateFamilyForImport(UUID familyId, ImportFamilyCommand command) {
        Family saved = familyService.updateFamily(
                familyId,
                command.code(),
                command.name(),
                command.primaryPhone(),
                command.email(),
                command.address(),
                command.city(),
                command.notes());
        return toFamilySummary(saved);
    }

    @Override
    public GuardianSummary createGuardianForImport(ImportGuardianCommand command) {
        GuardianService.GuardianView view = guardianService.createGuardian(
                command.familyId(),
                command.firstName(),
                command.lastName(),
                parseGender(command.gender()),
                command.phone(),
                command.secondaryPhone(),
                command.email(),
                command.profession(),
                command.address(),
                command.nationalIdNumber(),
                command.preferredLanguage());
        return toGuardianSummary(view.guardian());
    }

    @Override
    public GuardianSummary updateGuardianForImport(UUID guardianId, ImportGuardianCommand command) {
        GuardianService.GuardianView view = guardianService.updateGuardian(
                guardianId,
                command.familyId(),
                command.firstName(),
                command.lastName(),
                parseGender(command.gender()),
                command.phone(),
                command.secondaryPhone(),
                command.email(),
                command.profession(),
                command.address(),
                command.nationalIdNumber(),
                command.preferredLanguage());
        return toGuardianSummary(view.guardian());
    }

    @Override
    public StudentGuardianSummary linkOrUpdateGuardianForImport(ImportStudentGuardianLinkCommand command) {
        RelationshipType relation = parseRelationship(command.relationshipType());
        Optional<StudentGuardianLink> existingOpt = linkRepository
                .findByStudentIdAndActiveTrueOrderByPrimaryContactDescCreatedAtAsc(command.studentId())
                .stream()
                .filter(l -> command.guardianId().equals(l.getGuardianId()))
                .findFirst();

        StudentGuardianService.StudentGuardianView view;
        if (existingOpt.isPresent()) {
            StudentGuardianLink existing = existingOpt.get();
            view = studentGuardianService.updateLink(
                    command.studentId(),
                    existing.getId(),
                    relation,
                    command.primaryContact() != null ? command.primaryContact() : existing.isPrimaryContact(),
                    command.financialResponsible() != null
                            ? command.financialResponsible()
                            : existing.isFinancialResponsible(),
                    command.legalCustody() != null ? command.legalCustody() : existing.isLegalCustody(),
                    command.emergencyContact() != null ? command.emergencyContact() : existing.isEmergencyContact(),
                    command.authorizedPickup() != null ? command.authorizedPickup() : existing.isAuthorizedPickup(),
                    command.notes() != null ? command.notes() : existing.getNotes());
        } else {
            view = studentGuardianService.linkGuardianToStudent(
                    command.studentId(),
                    command.guardianId(),
                    relation,
                    command.primaryContact(),
                    command.financialResponsible(),
                    command.legalCustody(),
                    command.emergencyContact(),
                    command.authorizedPickup(),
                    command.notes());
        }
        return toLinkSummary(view.link(), view.guardian());
    }

    private static GuardianGender parseGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return GuardianGender.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private static RelationshipType parseRelationship(String raw) {
        if (raw == null || raw.isBlank()) {
            return RelationshipType.LEGAL_GUARDIAN;
        }
        return RelationshipType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
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

    private static StudentGuardianSummary toLinkSummary(StudentGuardianLink link, Guardian guardian) {
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
