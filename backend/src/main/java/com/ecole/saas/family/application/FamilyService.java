package com.ecole.saas.family.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.family.domain.Family;
import com.ecole.saas.family.domain.FamilyCodeAlreadyExistsException;
import com.ecole.saas.family.domain.FamilyNotFoundException;
import com.ecole.saas.family.domain.FamilyStatus;
import com.ecole.saas.family.domain.Guardian;
import com.ecole.saas.family.infrastructure.FamilyRepository;
import com.ecole.saas.family.infrastructure.GuardianRepository;
import com.ecole.saas.family.infrastructure.StudentGuardianLinkRepository;
import com.ecole.saas.shared.application.SqlLikePatterns;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentSummary;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif de gestion des familles / foyers d'un établissement.
 */
@Service
public class FamilyService {

    static final String READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY', 'CASHIER', 'CENSOR', 'TEACHER', 'TUTOR')";
    static final String WRITE_ROLES = "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'SECRETARY')";

    private final FamilyRepository familyRepository;
    private final GuardianRepository guardianRepository;
    private final StudentGuardianLinkRepository linkRepository;
    private final StudentDirectoryPort studentDirectoryPort;
    private final FamilySubscriptionGuard subscriptionGuard;
    private final FamilyTeacherScopeGuard teacherScopeGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public FamilyService(
            FamilyRepository familyRepository,
            GuardianRepository guardianRepository,
            StudentGuardianLinkRepository linkRepository,
            StudentDirectoryPort studentDirectoryPort,
            FamilySubscriptionGuard subscriptionGuard,
            FamilyTeacherScopeGuard teacherScopeGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.familyRepository = familyRepository;
        this.guardianRepository = guardianRepository;
        this.linkRepository = linkRepository;
        this.studentDirectoryPort = studentDirectoryPort;
        this.subscriptionGuard = subscriptionGuard;
        this.teacherScopeGuard = teacherScopeGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Page<Family> listFamilies(FamilyStatus status, String search, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Set<UUID> visibleFamilyIds = null;
        if (teacherScopeGuard.isRestrictedTeacher()) {
            visibleFamilyIds = teacherScopeGuard.resolveVisibleFamilyIds();
            if (visibleFamilyIds.isEmpty()) {
                return Page.empty(pageable);
            }
        }
        Set<UUID> restrictedFamilyIds = visibleFamilyIds;

        Specification<Family> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (restrictedFamilyIds != null) {
                predicates.add(root.get("id").in(restrictedFamilyIds));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            String pattern = SqlLikePatterns.containsLower(search);
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, SqlLikePatterns.ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("code")), pattern, SqlLikePatterns.ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("primaryPhone")), pattern, SqlLikePatterns.ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("city")), pattern, SqlLikePatterns.ESCAPE_CHAR)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return familyRepository.findAll(spec, pageable);
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Family getFamily(UUID familyId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        Family family = requireFamily(familyId);
        teacherScopeGuard.assertCanAccessFamily(family.getId());
        return family;
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Family createFamily(
            String code,
            String name,
            String primaryPhone,
            String email,
            String address,
            String city,
            String notes) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        String normalizedCode = Family.normalizeOptionalCode(code);
        if (normalizedCode != null && familyRepository.existsByCode(normalizedCode)) {
            throw new FamilyCodeAlreadyExistsException("Une famille avec ce code existe déjà dans l'établissement.");
        }
        Family saved = familyRepository.save(new Family(normalizedCode, name, primaryPhone, email, address, city, notes));
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.FAMILY,
                AuditAction.FAMILY_CREATED,
                "FAMILY",
                saved.getId(),
                "Création d'un dossier famille.",
                saved.getCode() != null ? Map.of("familyCode", saved.getCode()) : Map.of()));
        return saved;
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Family updateFamily(
            UUID familyId,
            String code,
            String name,
            String primaryPhone,
            String email,
            String address,
            String city,
            String notes) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Family family = requireFamily(familyId);
        family.assertActive();

        String normalizedCode = Family.normalizeOptionalCode(code);
        if (normalizedCode != null && familyRepository.existsByCodeAndIdNot(normalizedCode, familyId)) {
            throw new FamilyCodeAlreadyExistsException("Une famille avec ce code existe déjà dans l'établissement.");
        }

        family.update(normalizedCode, name, primaryPhone, email, address, city, notes);
        return familyRepository.save(family);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Family archiveFamily(UUID familyId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        Family family = requireFamily(familyId);
        family.archive(clock.instant());
        return familyRepository.save(family);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Family reactivateFamily(UUID familyId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        Family family = requireFamily(familyId);
        family.reactivate();
        return familyRepository.save(family);
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Page<GuardianService.GuardianView> listFamilyGuardians(UUID familyId, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        Family family = requireFamily(familyId);
        teacherScopeGuard.assertCanAccessFamily(family.getId());
        boolean includeNationalId = teacherScopeGuard.canViewNationalIdNumber();
        Page<Guardian> guardians;
        if (teacherScopeGuard.isRestrictedTeacher()) {
            Set<UUID> visibleGuardianIds = teacherScopeGuard.resolveVisibleGuardianIds();
            if (visibleGuardianIds.isEmpty()) {
                return Page.empty(pageable);
            }
            guardians = guardianRepository.findByFamilyIdAndIdIn(familyId, visibleGuardianIds, pageable);
        } else {
            guardians = guardianRepository.findByFamilyId(familyId, pageable);
        }
        return guardians.map(guardian -> new GuardianService.GuardianView(
                guardian, family.getCode(), family.getName(), includeNationalId));
    }

    private static final int MAX_FAMILY_STUDENTS_PAGE_SIZE = 100;

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Page<StudentSummary> listFamilyStudents(UUID familyId, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        requireFamily(familyId);
        teacherScopeGuard.assertCanAccessFamily(familyId);

        int pageNumber = pageable != null && pageable.isPaged() ? pageable.getPageNumber() : 0;
        int pageSize = pageable != null && pageable.isPaged()
                ? Math.min(Math.max(pageable.getPageSize(), 1), MAX_FAMILY_STUDENTS_PAGE_SIZE)
                : 20;
        Pageable boundedPageable = PageRequest.of(pageNumber, pageSize);

        Page<UUID> studentIdPage;
        if (teacherScopeGuard.isRestrictedTeacher()) {
            Set<UUID> visibleStudentIds = teacherScopeGuard.resolveVisibleStudentIds();
            if (visibleStudentIds.isEmpty()) {
                return new PageImpl<>(List.of(), boundedPageable, 0);
            }
            studentIdPage = linkRepository.findDistinctActiveStudentIdsByFamilyIdAndStudentIdIn(
                    familyId, visibleStudentIds, boundedPageable);
        } else {
            studentIdPage = linkRepository.findDistinctActiveStudentIdsByFamilyId(familyId, boundedPageable);
        }

        if (studentIdPage.isEmpty()) {
            return new PageImpl<>(List.of(), boundedPageable, studentIdPage.getTotalElements());
        }

        List<StudentSummary> pageStudents = studentDirectoryPort.findStudentsByIds(studentIdPage.getContent()).stream()
                .sorted(Comparator.comparing(StudentSummary::lastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(StudentSummary::firstName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(StudentSummary::matricule))
                .toList();

        return new PageImpl<>(pageStudents, boundedPageable, studentIdPage.getTotalElements());
    }

    private Family requireFamily(UUID familyId) {
        return familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyNotFoundException("Famille introuvable."));
    }
}
