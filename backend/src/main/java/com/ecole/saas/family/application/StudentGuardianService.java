package com.ecole.saas.family.application;

import com.ecole.saas.family.domain.Guardian;
import com.ecole.saas.family.domain.GuardianArchivedException;
import com.ecole.saas.family.domain.GuardianNotFoundException;
import com.ecole.saas.family.domain.RelationshipType;
import com.ecole.saas.family.domain.StudentArchivedForLinkException;
import com.ecole.saas.family.domain.StudentGuardianLink;
import com.ecole.saas.family.domain.StudentGuardianLinkAlreadyExistsException;
import com.ecole.saas.family.domain.StudentGuardianLinkNotFoundException;
import com.ecole.saas.family.domain.StudentReferenceNotFoundException;
import com.ecole.saas.family.infrastructure.GuardianRepository;
import com.ecole.saas.family.infrastructure.StudentGuardianLinkRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentSummary;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif de gestion des liens élève ↔ parent/tuteur légal.
 */
@Service
public class StudentGuardianService {

    private final StudentGuardianLinkRepository linkRepository;
    private final GuardianRepository guardianRepository;
    private final StudentDirectoryPort studentDirectoryPort;
    private final FamilySubscriptionGuard subscriptionGuard;

    public StudentGuardianService(
            StudentGuardianLinkRepository linkRepository,
            GuardianRepository guardianRepository,
            StudentDirectoryPort studentDirectoryPort,
            FamilySubscriptionGuard subscriptionGuard) {
        this.linkRepository = linkRepository;
        this.guardianRepository = guardianRepository;
        this.studentDirectoryPort = studentDirectoryPort;
        this.subscriptionGuard = subscriptionGuard;
    }

    @PreAuthorize(FamilyService.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<StudentGuardianView> listStudentGuardians(
            UUID studentId, Boolean activeOnly, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        StudentSummary student = requireStudent(studentId);
        Page<StudentGuardianLink> page = Boolean.TRUE.equals(activeOnly)
                ? linkRepository.findByStudentIdAndActiveTrue(studentId, pageable)
                : linkRepository.findByStudentId(studentId, pageable);
        if (page.isEmpty()) {
            return page.map(link -> new StudentGuardianView(link, null, student));
        }

        Set<UUID> guardianIds = page.getContent().stream()
                .map(StudentGuardianLink::getGuardianId)
                .collect(Collectors.toSet());
        Map<UUID, Guardian> guardiansById = guardianRepository.findAllById(guardianIds).stream()
                .collect(Collectors.toMap(Guardian::getId, Function.identity()));

        return page.map(link -> new StudentGuardianView(link, guardiansById.get(link.getGuardianId()), student));
    }

    @PreAuthorize(FamilyService.WRITE_ROLES)
    @Transactional
    public StudentGuardianView linkGuardianToStudent(
            UUID studentId,
            UUID guardianId,
            RelationshipType relationshipType,
            Boolean primaryContact,
            Boolean financialResponsible,
            Boolean legalCustody,
            Boolean emergencyContact,
            Boolean authorizedPickup,
            String notes) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        StudentSummary student = requireStudent(studentId);
        assertStudentNotArchived(student);

        Guardian guardian = requireGuardian(guardianId);
        if (!guardian.isActive()) {
            throw new GuardianArchivedException(
                    "Impossible de lier un parent ou tuteur archivé à un élève.");
        }

        if (linkRepository.existsByStudentIdAndGuardianId(studentId, guardianId)) {
            throw new StudentGuardianLinkAlreadyExistsException(
                    "Ce parent ou tuteur est déjà lié à cet élève.");
        }

        boolean isPrimary = Boolean.TRUE.equals(primaryContact);
        boolean isFinancial = Boolean.TRUE.equals(financialResponsible);
        boolean isLegalCustody = Boolean.TRUE.equals(legalCustody);
        boolean isEmergency = Boolean.TRUE.equals(emergencyContact);
        boolean isAuthorizedPickup = authorizedPickup == null || authorizedPickup;

        if (isPrimary) {
            clearExistingPrimaryContact(studentId, null);
        }
        if (isFinancial) {
            clearExistingFinancialResponsible(studentId, null);
        }

        StudentGuardianLink saved = linkRepository.save(new StudentGuardianLink(
                studentId,
                guardianId,
                relationshipType,
                isPrimary,
                isFinancial,
                isLegalCustody,
                isEmergency,
                isAuthorizedPickup,
                notes));
        return new StudentGuardianView(saved, guardian, student);
    }

    @PreAuthorize(FamilyService.WRITE_ROLES)
    @Transactional
    public StudentGuardianView updateLink(
            UUID studentId,
            UUID linkId,
            RelationshipType relationshipType,
            boolean primaryContact,
            boolean financialResponsible,
            boolean legalCustody,
            boolean emergencyContact,
            boolean authorizedPickup,
            String notes) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        StudentSummary student = requireStudent(studentId);
        assertStudentNotArchived(student);

        StudentGuardianLink link = requireLink(studentId, linkId);
        if (primaryContact) {
            clearExistingPrimaryContact(studentId, linkId);
        }
        if (financialResponsible) {
            clearExistingFinancialResponsible(studentId, linkId);
        }

        link.update(
                relationshipType,
                primaryContact,
                financialResponsible,
                legalCustody,
                emergencyContact,
                authorizedPickup,
                notes);
        StudentGuardianLink saved = linkRepository.save(link);
        Guardian guardian = guardianRepository.findById(saved.getGuardianId()).orElse(null);
        return new StudentGuardianView(saved, guardian, student);
    }

    @PreAuthorize(FamilyService.WRITE_ROLES)
    @Transactional
    public StudentGuardianView deactivateLink(UUID studentId, UUID linkId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        StudentSummary student = requireStudent(studentId);
        StudentGuardianLink link = requireLink(studentId, linkId);
        link.deactivate();
        StudentGuardianLink saved = linkRepository.save(link);
        Guardian guardian = guardianRepository.findById(saved.getGuardianId()).orElse(null);
        return new StudentGuardianView(saved, guardian, student);
    }

    @PreAuthorize(FamilyService.WRITE_ROLES)
    @Transactional
    public StudentGuardianView activateLink(UUID studentId, UUID linkId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        StudentSummary student = requireStudent(studentId);
        assertStudentNotArchived(student);

        StudentGuardianLink link = requireLink(studentId, linkId);
        Guardian guardian = requireGuardian(link.getGuardianId());
        if (!guardian.isActive()) {
            throw new GuardianArchivedException(
                    "Impossible de réactiver le lien d'un parent ou tuteur archivé.");
        }

        link.activate();
        StudentGuardianLink saved = linkRepository.save(link);
        return new StudentGuardianView(saved, guardian, student);
    }

    private void clearExistingPrimaryContact(UUID studentId, UUID excludeLinkId) {
        linkRepository.findByStudentIdAndActiveTrueAndPrimaryContactTrue(studentId).ifPresent(existing -> {
            if (!existing.getId().equals(excludeLinkId)) {
                existing.clearPrimaryContact();
                linkRepository.saveAndFlush(existing);
            }
        });
    }

    private void clearExistingFinancialResponsible(UUID studentId, UUID excludeLinkId) {
        linkRepository.findByStudentIdAndActiveTrueAndFinancialResponsibleTrue(studentId).ifPresent(existing -> {
            if (!existing.getId().equals(excludeLinkId)) {
                existing.clearFinancialResponsible();
                linkRepository.saveAndFlush(existing);
            }
        });
    }

    private StudentSummary requireStudent(UUID studentId) {
        return studentDirectoryPort.findStudentById(studentId)
                .orElseThrow(() -> new StudentReferenceNotFoundException("Élève introuvable."));
    }

    private void assertStudentNotArchived(StudentSummary student) {
        if ("ARCHIVED".equals(student.status())) {
            throw new StudentArchivedForLinkException(
                    "Impossible de modifier les liens parentaux d'un élève archivé.");
        }
    }

    private Guardian requireGuardian(UUID guardianId) {
        return guardianRepository.findById(guardianId)
                .orElseThrow(() -> new GuardianNotFoundException("Parent ou tuteur introuvable."));
    }

    private StudentGuardianLink requireLink(UUID studentId, UUID linkId) {
        return linkRepository.findByIdAndStudentId(linkId, studentId)
                .orElseThrow(() -> new StudentGuardianLinkNotFoundException("Lien élève-tuteur introuvable."));
    }

    public record StudentGuardianView(
            StudentGuardianLink link,
            Guardian guardian,
            StudentSummary student) {
    }
}
