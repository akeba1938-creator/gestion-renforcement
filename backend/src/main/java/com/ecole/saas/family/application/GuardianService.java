package com.ecole.saas.family.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.family.domain.Family;
import com.ecole.saas.family.domain.FamilyArchivedException;
import com.ecole.saas.family.domain.FamilyNotFoundException;
import com.ecole.saas.family.domain.Guardian;
import com.ecole.saas.family.domain.GuardianGender;
import com.ecole.saas.family.domain.GuardianNotFoundException;
import com.ecole.saas.family.domain.GuardianStatus;
import com.ecole.saas.family.domain.StudentGuardianLink;
import com.ecole.saas.family.infrastructure.FamilyRepository;
import com.ecole.saas.family.infrastructure.GuardianRepository;
import com.ecole.saas.family.infrastructure.StudentGuardianLinkRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentSummary;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif de gestion des parents et tuteurs légaux d'un établissement.
 */
@Service
public class GuardianService {

    private final GuardianRepository guardianRepository;
    private final FamilyRepository familyRepository;
    private final StudentGuardianLinkRepository linkRepository;
    private final StudentDirectoryPort studentDirectoryPort;
    private final FamilySubscriptionGuard subscriptionGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public GuardianService(
            GuardianRepository guardianRepository,
            FamilyRepository familyRepository,
            StudentGuardianLinkRepository linkRepository,
            StudentDirectoryPort studentDirectoryPort,
            FamilySubscriptionGuard subscriptionGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.guardianRepository = guardianRepository;
        this.familyRepository = familyRepository;
        this.linkRepository = linkRepository;
        this.studentDirectoryPort = studentDirectoryPort;
        this.subscriptionGuard = subscriptionGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(FamilyService.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<GuardianView> listGuardians(
            UUID familyId, GuardianStatus status, String search, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Specification<Guardian> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (familyId != null) {
                predicates.add(cb.equal(root.get("familyId"), familyId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), pattern),
                        cb.like(cb.lower(root.get("lastName")), pattern),
                        cb.like(cb.lower(root.get("phone")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<Guardian> page = guardianRepository.findAll(spec, pageable);
        if (page.isEmpty()) {
            return page.map(g -> new GuardianView(g, null, null));
        }

        Set<UUID> familyIds = page.getContent().stream()
                .map(Guardian::getFamilyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Family> familiesById = familyIds.isEmpty()
                ? Map.of()
                : familyRepository.findAllById(familyIds).stream()
                        .collect(Collectors.toMap(Family::getId, Function.identity()));

        return page.map(guardian -> toView(guardian, familiesById.get(guardian.getFamilyId())));
    }

    @PreAuthorize(FamilyService.READ_ROLES)
    @Transactional(readOnly = true)
    public GuardianView getGuardian(UUID guardianId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        Guardian guardian = requireGuardian(guardianId);
        Family family = guardian.getFamilyId() != null
                ? familyRepository.findById(guardian.getFamilyId()).orElse(null)
                : null;
        return toView(guardian, family);
    }

    @PreAuthorize(FamilyService.WRITE_ROLES)
    @Transactional
    public GuardianView createGuardian(
            UUID familyId,
            String firstName,
            String lastName,
            GuardianGender gender,
            String phone,
            String secondaryPhone,
            String email,
            String profession,
            String address,
            String nationalIdNumber,
            String preferredLanguage) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Family family = null;
        if (familyId != null) {
            family = requireFamily(familyId);
            if (!family.isActive()) {
                throw new FamilyArchivedException("Impossible de rattacher un parent ou tuteur à une famille archivée.");
            }
        }

        Guardian saved = guardianRepository.save(new Guardian(
                familyId,
                firstName,
                lastName,
                gender,
                phone,
                secondaryPhone,
                email,
                profession,
                address,
                nationalIdNumber,
                preferredLanguage));
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.FAMILY,
                AuditAction.GUARDIAN_CREATED,
                "GUARDIAN",
                saved.getId(),
                "Création d'un dossier parent ou tuteur légal.",
                familyId != null ? Map.of("familyId", familyId.toString()) : Map.of()));
        return toView(saved, family);
    }

    @PreAuthorize(FamilyService.WRITE_ROLES)
    @Transactional
    public GuardianView updateGuardian(
            UUID guardianId,
            UUID familyId,
            String firstName,
            String lastName,
            GuardianGender gender,
            String phone,
            String secondaryPhone,
            String email,
            String profession,
            String address,
            String nationalIdNumber,
            String preferredLanguage) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Guardian guardian = requireGuardian(guardianId);
        guardian.assertActive();

        Family family = null;
        if (familyId != null) {
            family = requireFamily(familyId);
            if (!family.isActive() && !familyId.equals(guardian.getFamilyId())) {
                throw new FamilyArchivedException("Impossible de rattacher un parent ou tuteur à une famille archivée.");
            }
        }

        guardian.update(
                familyId,
                firstName,
                lastName,
                gender,
                phone,
                secondaryPhone,
                email,
                profession,
                address,
                nationalIdNumber,
                preferredLanguage);
        Guardian saved = guardianRepository.save(guardian);
        return toView(saved, family);
    }

    @PreAuthorize(FamilyService.WRITE_ROLES)
    @Transactional
    public GuardianView archiveGuardian(UUID guardianId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        Guardian guardian = requireGuardian(guardianId);
        guardian.archive(clock.instant());
        Guardian saved = guardianRepository.save(guardian);
        Family family = saved.getFamilyId() != null
                ? familyRepository.findById(saved.getFamilyId()).orElse(null)
                : null;
        return toView(saved, family);
    }

    @PreAuthorize(FamilyService.WRITE_ROLES)
    @Transactional
    public GuardianView reactivateGuardian(UUID guardianId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        Guardian guardian = requireGuardian(guardianId);
        guardian.reactivate();
        Guardian saved = guardianRepository.save(guardian);
        Family family = saved.getFamilyId() != null
                ? familyRepository.findById(saved.getFamilyId()).orElse(null)
                : null;
        return toView(saved, family);
    }

    @PreAuthorize(FamilyService.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<StudentGuardianService.StudentGuardianView> listGuardianStudents(
            UUID guardianId, Boolean activeOnly, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        Guardian guardian = requireGuardian(guardianId);

        Page<StudentGuardianLink> page = Boolean.TRUE.equals(activeOnly)
                ? linkRepository.findByGuardianIdAndActiveTrue(guardianId, pageable)
                : linkRepository.findByGuardianId(guardianId, pageable);
        if (page.isEmpty()) {
            return page.map(link -> new StudentGuardianService.StudentGuardianView(link, guardian, null));
        }

        Set<UUID> studentIds = page.getContent().stream()
                .map(StudentGuardianLink::getStudentId)
                .collect(Collectors.toSet());
        Map<UUID, StudentSummary> studentsById = studentDirectoryPort.findStudentsByIds(studentIds).stream()
                .collect(Collectors.toMap(StudentSummary::id, Function.identity()));

        return page.map(link -> new StudentGuardianService.StudentGuardianView(
                link, guardian, studentsById.get(link.getStudentId())));
    }

    private Guardian requireGuardian(UUID guardianId) {
        return guardianRepository.findById(guardianId)
                .orElseThrow(() -> new GuardianNotFoundException("Parent ou tuteur introuvable."));
    }

    private Family requireFamily(UUID familyId) {
        return familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyNotFoundException("Famille introuvable."));
    }

    private static GuardianView toView(Guardian guardian, Family family) {
        return new GuardianView(
                guardian,
                family != null ? family.getCode() : null,
                family != null ? family.getName() : null);
    }

    public record GuardianView(Guardian guardian, String familyCode, String familyName) {
    }
}
