package com.ecole.saas.teacher.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.SubjectSummary;
import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.identity.UserDirectoryPort;
import com.ecole.saas.identity.UserSummary;
import com.ecole.saas.shared.application.SqlLikePatterns;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.teacher.domain.Teacher;
import com.ecole.saas.teacher.domain.TeacherConflictException;
import com.ecole.saas.teacher.domain.TeacherGender;
import com.ecole.saas.teacher.domain.TeacherNotFoundException;
import com.ecole.saas.teacher.domain.TeacherSpecialty;
import com.ecole.saas.teacher.domain.TeacherStatus;
import com.ecole.saas.teacher.domain.TeacherType;
import com.ecole.saas.teacher.domain.TeacherValidationException;
import com.ecole.saas.teacher.infrastructure.TeacherRepository;
import com.ecole.saas.teacher.infrastructure.TeacherSpecialtyRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif de gestion des dossiers enseignants / intervenants et de leurs spécialités.
 */
@Service
public class TeacherService {

    public record SpecialtyView(TeacherSpecialty specialty, String subjectCode, String subjectName) {
    }

    private final TeacherRepository teacherRepository;
    private final TeacherSpecialtyRepository specialtyRepository;
    private final AcademicStructurePort academicStructurePort;
    private final UserDirectoryPort userDirectoryPort;
    private final TeacherSubscriptionGuard subscriptionGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public TeacherService(
            TeacherRepository teacherRepository,
            TeacherSpecialtyRepository specialtyRepository,
            AcademicStructurePort academicStructurePort,
            UserDirectoryPort userDirectoryPort,
            TeacherSubscriptionGuard subscriptionGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.teacherRepository = teacherRepository;
        this.specialtyRepository = specialtyRepository;
        this.academicStructurePort = academicStructurePort;
        this.userDirectoryPort = userDirectoryPort;
        this.subscriptionGuard = subscriptionGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(TeacherRoles.BACKOFFICE_READ_ROLES)
    @Transactional(readOnly = true)
    public Page<Teacher> listTeachers(TeacherStatus status, TeacherType teacherType, String search, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Specification<Teacher> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (teacherType != null) {
                predicates.add(cb.equal(root.get("teacherType"), teacherType));
            }
            String pattern = SqlLikePatterns.containsLower(search);
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("employeeCode")), pattern, SqlLikePatterns.ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("firstName")), pattern, SqlLikePatterns.ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("lastName")), pattern, SqlLikePatterns.ESCAPE_CHAR)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return teacherRepository.findAll(spec, pageable);
    }

    @PreAuthorize(TeacherRoles.BACKOFFICE_READ_ROLES)
    @Transactional(readOnly = true)
    public Teacher getTeacher(UUID teacherId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        return requireTeacher(teacherId);
    }

    @PreAuthorize(TeacherRoles.SELF_ROLES)
    @Transactional(readOnly = true)
    public Teacher getOwnTeacherProfile(UUID currentUserId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        return teacherRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new TeacherNotFoundException(
                        "teacher-profile-not-linked",
                        "Profil enseignant introuvable",
                        "Aucun dossier enseignant n'est associé à ce compte utilisateur."));
    }

    @PreAuthorize(TeacherRoles.WRITE_ROLES)
    @Transactional
    public Teacher createTeacher(
            String employeeCode,
            UUID userId,
            String firstName,
            String lastName,
            TeacherGender gender,
            TeacherType teacherType,
            String phone,
            String secondaryPhone,
            String email,
            String address,
            String nationalIdNumber,
            LocalDate hireDate,
            Long hourlyRateXof,
            String notes) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        String normalizedCode = Teacher.normalizeCode(employeeCode);
        if (teacherRepository.existsByEmployeeCode(normalizedCode)) {
            throw new TeacherConflictException(
                    "teacher-code-already-exists",
                    "Code enseignant déjà utilisé",
                    "Un enseignant avec ce code existe déjà dans cet établissement.");
        }

        if (userId != null) {
            validateUserLinkForCreate(userId);
        }

        Teacher teacher = new Teacher(
                normalizedCode,
                userId,
                firstName,
                lastName,
                gender,
                teacherType,
                phone,
                secondaryPhone,
                email,
                address,
                nationalIdNumber,
                hireDate,
                hourlyRateXof,
                notes);
        Teacher saved = teacherRepository.save(teacher);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.TEACHER,
                AuditAction.TEACHER_CREATED,
                "TEACHER",
                saved.getId(),
                "Création d'un dossier enseignant ou intervenant.",
                Map.of("employeeCode", saved.getEmployeeCode(), "teacherType", saved.getTeacherType().name())));
        return saved;
    }

    @PreAuthorize(TeacherRoles.WRITE_ROLES)
    @Transactional
    public Teacher updateTeacher(
            UUID teacherId,
            String firstName,
            String lastName,
            TeacherGender gender,
            TeacherType teacherType,
            String phone,
            String secondaryPhone,
            String email,
            String address,
            String nationalIdNumber,
            LocalDate hireDate,
            Long hourlyRateXof,
            String notes) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Teacher teacher = requireTeacher(teacherId);
        teacher.updateProfile(
                firstName,
                lastName,
                gender,
                teacherType,
                phone,
                secondaryPhone,
                email,
                address,
                nationalIdNumber,
                hireDate,
                hourlyRateXof,
                notes);
        return teacherRepository.save(teacher);
    }

    @PreAuthorize(TeacherRoles.WRITE_ROLES)
    @Transactional
    public Teacher linkUserAccount(UUID teacherId, UUID userId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Teacher teacher = requireTeacher(teacherId);
        teacher.assertNotArchived();
        if (userId != null) {
            validateUserExistsInTenant(userId);
            if (teacherRepository.existsByUserIdAndIdNot(userId, teacherId)) {
                throw new TeacherConflictException(
                        "teacher-user-already-linked",
                        "Compte utilisateur déjà associé",
                        "Ce compte utilisateur est déjà associé à un autre dossier enseignant.");
            }
        }
        teacher.linkUser(userId);
        Teacher saved = teacherRepository.save(teacher);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.TEACHER,
                AuditAction.TEACHER_USER_LINKED,
                "TEACHER",
                saved.getId(),
                "Association d'un compte utilisateur à un dossier enseignant.",
                userId != null
                        ? Map.of("employeeCode", saved.getEmployeeCode(), "userId", userId.toString())
                        : Map.of("employeeCode", saved.getEmployeeCode(), "unlinked", true)));
        return saved;
    }

    @PreAuthorize(TeacherRoles.WRITE_ROLES)
    @Transactional
    public Teacher suspendTeacher(UUID teacherId, String reason) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Teacher teacher = requireTeacher(teacherId);
        teacher.suspend(reason);
        Teacher saved = teacherRepository.save(teacher);
        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.TEACHER,
                AuditAction.TEACHER_STATUS_CHANGED,
                AuditOutcome.SUCCESS,
                "TEACHER",
                saved.getId(),
                "Suspension d'un dossier enseignant.",
                Map.of("employeeCode", saved.getEmployeeCode(), "status", saved.getStatus().name())));
        return saved;
    }

    @PreAuthorize(TeacherRoles.WRITE_ROLES)
    @Transactional
    public Teacher reactivateTeacher(UUID teacherId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Teacher teacher = requireTeacher(teacherId);
        teacher.reactivate();
        Teacher saved = teacherRepository.save(teacher);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.TEACHER,
                AuditAction.TEACHER_STATUS_CHANGED,
                "TEACHER",
                saved.getId(),
                "Réactivation d'un dossier enseignant.",
                Map.of("employeeCode", saved.getEmployeeCode(), "status", saved.getStatus().name())));
        return saved;
    }

    @PreAuthorize(TeacherRoles.WRITE_ROLES)
    @Transactional
    public Teacher archiveTeacher(UUID teacherId, String reason) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Teacher teacher = requireTeacher(teacherId);
        teacher.archive(reason, clock.instant());
        Teacher saved = teacherRepository.save(teacher);
        auditLogPort.recordTenantEvent(AuditRecordCommand.critical(
                AuditCategory.TEACHER,
                AuditAction.TEACHER_STATUS_CHANGED,
                AuditOutcome.SUCCESS,
                "TEACHER",
                saved.getId(),
                "Archivage définitif d'un dossier enseignant.",
                Map.of("employeeCode", saved.getEmployeeCode(), "status", saved.getStatus().name())));
        return saved;
    }

    @PreAuthorize(TeacherRoles.BACKOFFICE_READ_ROLES)
    @Transactional(readOnly = true)
    public List<SpecialtyView> listSpecialties(UUID teacherId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        requireTeacher(teacherId);
        List<TeacherSpecialty> specialties =
                specialtyRepository.findByTeacherIdOrderByPrimarySpecialtyDescCreatedAtAsc(teacherId);
        if (specialties.isEmpty()) {
            return List.of();
        }
        Set<UUID> subjectIds = specialties.stream()
                .map(TeacherSpecialty::getSubjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, SubjectSummary> subjectsById = academicStructurePort.findSubjectsByIds(subjectIds);
        return specialties.stream()
                .map(specialty -> {
                    SubjectSummary subject = subjectsById.get(specialty.getSubjectId());
                    return new SpecialtyView(
                            specialty,
                            subject != null ? subject.code() : null,
                            subject != null ? subject.name() : null);
                })
                .toList();
    }

    @PreAuthorize(TeacherRoles.WRITE_ROLES)
    @Transactional
    public SpecialtyView addSpecialty(UUID teacherId, UUID subjectId, boolean primarySpecialty, String notes) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Teacher teacher = requireTeacher(teacherId);
        teacher.assertNotArchived();

        SubjectSummary subject = academicStructurePort.findSubjectById(subjectId)
                .orElseThrow(() -> new TeacherNotFoundException(
                        "subject-not-found", "Matière introuvable", "La matière demandée est introuvable."));
        if (!subject.active()) {
            throw new TeacherConflictException(
                    "subject-inactive",
                    "Matière inactive",
                    "Impossible d'associer une matière inactive comme spécialité.");
        }

        if (specialtyRepository.existsByTeacherIdAndSubjectId(teacherId, subjectId)) {
            throw new TeacherConflictException(
                    "teacher-specialty-already-exists",
                    "Spécialité déjà enregistrée",
                    "Cette matière figure déjà parmi les spécialités de cet enseignant.");
        }

        if (primarySpecialty) {
            specialtyRepository.findByTeacherIdAndPrimarySpecialtyTrue(teacherId).ifPresent(existingPrimary -> {
                existingPrimary.markPrimary(false);
                specialtyRepository.saveAndFlush(existingPrimary);
            });
        }

        TeacherSpecialty saved = specialtyRepository.save(
                new TeacherSpecialty(teacherId, subjectId, primarySpecialty, notes));
        return new SpecialtyView(saved, subject.code(), subject.name());
    }

    @PreAuthorize(TeacherRoles.WRITE_ROLES)
    @Transactional
    public void removeSpecialty(UUID teacherId, UUID subjectId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Teacher teacher = requireTeacher(teacherId);
        teacher.assertNotArchived();

        TeacherSpecialty existing = specialtyRepository.findByTeacherIdAndSubjectId(teacherId, subjectId)
                .orElseThrow(() -> new TeacherNotFoundException(
                        "teacher-specialty-not-found",
                        "Spécialité introuvable",
                        "Cette spécialité n'est pas associée à cet enseignant."));
        specialtyRepository.delete(existing);
    }

    private void validateUserLinkForCreate(UUID userId) {
        validateUserExistsInTenant(userId);
        if (teacherRepository.existsByUserId(userId)) {
            throw new TeacherConflictException(
                    "teacher-user-already-linked",
                    "Compte utilisateur déjà associé",
                    "Ce compte utilisateur est déjà associé à un autre dossier enseignant.");
        }
    }

    private void validateUserExistsInTenant(UUID userId) {
        UserSummary user = userDirectoryPort.findUserById(userId)
                .orElseThrow(() -> new TeacherNotFoundException(
                        "user-not-found",
                        "Compte utilisateur introuvable",
                        "Le compte utilisateur demandé est introuvable dans cet établissement."));
        if (!user.active()) {
            throw new TeacherValidationException("Le compte utilisateur associé doit être actif.");
        }
        boolean hasTeachingRole = user.roles() != null
                && (user.roles().contains("TEACHER") || user.roles().contains("TUTOR"));
        if (!hasTeachingRole) {
            throw new TeacherValidationException(
                    "Le compte utilisateur associé doit disposer du rôle TEACHER ou TUTOR.");
        }
    }

    private Teacher requireTeacher(UUID teacherId) {
        return teacherRepository.findById(teacherId)
                .orElseThrow(() -> new TeacherNotFoundException("Dossier enseignant introuvable."));
    }
}
