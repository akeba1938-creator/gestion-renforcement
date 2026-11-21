package com.ecole.saas.academic.application;

import com.ecole.saas.academic.domain.EducationCycle;
import com.ecole.saas.academic.domain.GradeLevel;
import com.ecole.saas.academic.domain.GradeLevelAlreadyExistsException;
import com.ecole.saas.academic.domain.GradeLevelNotFoundException;
import com.ecole.saas.academic.infrastructure.GradeLevelRepository;
import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif de gestion des niveaux scolaires et niveaux de centre de renforcement.
 */
@Service
public class GradeLevelService {

    private static final String READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'SECRETARY', 'CASHIER', 'TEACHER', 'TUTOR')";
    private static final String WRITE_ROLES = "hasAnyRole('DIRECTOR', 'CENTER_MANAGER')";

    private final GradeLevelRepository gradeLevelRepository;
    private final AcademicSubscriptionGuard subscriptionGuard;

    public GradeLevelService(
            GradeLevelRepository gradeLevelRepository,
            AcademicSubscriptionGuard subscriptionGuard) {
        this.gradeLevelRepository = gradeLevelRepository;
        this.subscriptionGuard = subscriptionGuard;
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Page<GradeLevel> listGradeLevels(EducationCycle cycle, Boolean activeOnly, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        Specification<GradeLevel> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (cycle != null) {
                predicates.add(cb.equal(root.get("cycle"), cycle));
            }
            if (Boolean.TRUE.equals(activeOnly)) {
                predicates.add(cb.isTrue(root.get("active")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return gradeLevelRepository.findAll(spec, pageable);
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public GradeLevel getGradeLevel(UUID levelId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        return requireGradeLevel(levelId);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public GradeLevel createGradeLevel(String code, String name, EducationCycle cycle, int displayOrder) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        String normalizedCode = GradeLevel.normalizeCode(code);
        if (gradeLevelRepository.existsByCode(normalizedCode)) {
            throw new GradeLevelAlreadyExistsException("Un niveau avec ce code existe déjà dans votre établissement.");
        }
        return gradeLevelRepository.save(new GradeLevel(normalizedCode, name, cycle, displayOrder));
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public GradeLevel updateGradeLevel(UUID levelId, String name, EducationCycle cycle, int displayOrder) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        GradeLevel level = requireGradeLevel(levelId);
        level.update(name, cycle, displayOrder);
        return gradeLevelRepository.save(level);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public GradeLevel activateGradeLevel(UUID levelId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        GradeLevel level = requireGradeLevel(levelId);
        level.activate();
        return gradeLevelRepository.save(level);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public GradeLevel deactivateGradeLevel(UUID levelId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        GradeLevel level = requireGradeLevel(levelId);
        level.deactivate();
        return gradeLevelRepository.save(level);
    }

    private GradeLevel requireGradeLevel(UUID levelId) {
        return gradeLevelRepository.findById(levelId)
                .orElseThrow(() -> new GradeLevelNotFoundException("Niveau introuvable."));
    }
}
