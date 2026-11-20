package com.ecole.saas.family.application;

import com.ecole.saas.family.domain.Family;
import com.ecole.saas.family.domain.FamilyCodeAlreadyExistsException;
import com.ecole.saas.family.domain.FamilyNotFoundException;
import com.ecole.saas.family.domain.FamilyStatus;
import com.ecole.saas.family.domain.Guardian;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
    private final Clock clock;

    public FamilyService(
            FamilyRepository familyRepository,
            GuardianRepository guardianRepository,
            StudentGuardianLinkRepository linkRepository,
            StudentDirectoryPort studentDirectoryPort,
            FamilySubscriptionGuard subscriptionGuard,
            Clock clock) {
        this.familyRepository = familyRepository;
        this.guardianRepository = guardianRepository;
        this.linkRepository = linkRepository;
        this.studentDirectoryPort = studentDirectoryPort;
        this.subscriptionGuard = subscriptionGuard;
        this.clock = clock;
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Page<Family> listFamilies(FamilyStatus status, String search, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Specification<Family> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("primaryPhone")), pattern),
                        cb.like(cb.lower(root.get("city")), pattern)));
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
        return requireFamily(familyId);
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
        return familyRepository.save(new Family(normalizedCode, name, primaryPhone, email, address, city, notes));
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
        return guardianRepository.findByFamilyId(familyId, pageable)
                .map(guardian -> new GuardianService.GuardianView(guardian, family.getCode(), family.getName()));
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Page<StudentSummary> listFamilyStudents(UUID familyId, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        requireFamily(familyId);

        List<UUID> guardianIds = guardianRepository.findByFamilyId(familyId).stream()
                .map(Guardian::getId)
                .toList();
        if (guardianIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        Set<UUID> studentIds = linkRepository.findByGuardianIdInAndActiveTrue(guardianIds).stream()
                .map(StudentGuardianLink::getStudentId)
                .collect(Collectors.toSet());
        if (studentIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<StudentSummary> sortedStudents = studentDirectoryPort.findStudentsByIds(studentIds).stream()
                .sorted(Comparator.comparing(StudentSummary::lastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(StudentSummary::firstName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(StudentSummary::matricule))
                .toList();

        int start = (int) pageable.getOffset();
        if (start >= sortedStudents.size()) {
            return new PageImpl<>(List.of(), pageable, sortedStudents.size());
        }
        int end = Math.min(start + pageable.getPageSize(), sortedStudents.size());
        return new PageImpl<>(sortedStudents.subList(start, end), pageable, sortedStudents.size());
    }

    private Family requireFamily(UUID familyId) {
        return familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyNotFoundException("Famille introuvable."));
    }
}
