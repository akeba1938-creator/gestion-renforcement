package com.ecole.saas.academic.application;

import com.ecole.saas.academic.domain.Subject;
import com.ecole.saas.academic.domain.SubjectAlreadyExistsException;
import com.ecole.saas.academic.domain.SubjectCategory;
import com.ecole.saas.academic.domain.SubjectNotFoundException;
import com.ecole.saas.academic.infrastructure.SubjectRepository;
import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif de gestion du référentiel des matières par établissement.
 */
@Service
public class SubjectService {

    private static final String READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'SECRETARY', 'CASHIER', 'TEACHER', 'TUTOR')";
    private static final String WRITE_ROLES = "hasAnyRole('DIRECTOR', 'CENTER_MANAGER')";

    private final SubjectRepository subjectRepository;
    private final AcademicSubscriptionGuard subscriptionGuard;

    public SubjectService(
            SubjectRepository subjectRepository,
            AcademicSubscriptionGuard subscriptionGuard) {
        this.subjectRepository = subjectRepository;
        this.subscriptionGuard = subscriptionGuard;
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Page<Subject> listSubjects(
            SubjectCategory category,
            Boolean activeOnly,
            String search,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        Specification<Subject> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (Boolean.TRUE.equals(activeOnly)) {
                predicates.add(cb.isTrue(root.get("active")));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return subjectRepository.findAll(spec, pageable);
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Subject getSubject(UUID subjectId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        return requireSubject(subjectId);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Subject createSubject(
            String code,
            String name,
            String shortName,
            SubjectCategory category,
            Integer defaultCoefficient) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        String normalizedCode = Subject.normalizeCode(code);
        if (subjectRepository.existsByCode(normalizedCode)) {
            throw new SubjectAlreadyExistsException(
                    "Une matière avec ce code existe déjà dans votre établissement.");
        }
        int effectiveCoef = defaultCoefficient != null ? defaultCoefficient : Subject.DEFAULT_COEFFICIENT;
        return subjectRepository.save(new Subject(normalizedCode, name, shortName, category, effectiveCoef));
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Subject updateSubject(
            UUID subjectId,
            String name,
            String shortName,
            SubjectCategory category,
            int defaultCoefficient) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        Subject subject = requireSubject(subjectId);
        subject.update(name, shortName, category, defaultCoefficient);
        return subjectRepository.save(subject);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Subject activateSubject(UUID subjectId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        Subject subject = requireSubject(subjectId);
        subject.activate();
        return subjectRepository.save(subject);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Subject deactivateSubject(UUID subjectId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        Subject subject = requireSubject(subjectId);
        subject.deactivate();
        return subjectRepository.save(subject);
    }

    private Subject requireSubject(UUID subjectId) {
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> new SubjectNotFoundException("Matière introuvable."));
    }
}
