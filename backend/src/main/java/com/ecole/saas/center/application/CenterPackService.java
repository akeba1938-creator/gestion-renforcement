package com.ecole.saas.center.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.academic.GradeLevelSummary;
import com.ecole.saas.academic.SubjectSummary;
import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.center.domain.CenterConflictException;
import com.ecole.saas.center.domain.CenterGroup;
import com.ecole.saas.center.domain.CenterNotFoundException;
import com.ecole.saas.center.domain.CenterPackBillingMode;
import com.ecole.saas.center.domain.CenterSessionPack;
import com.ecole.saas.center.domain.CenterStudentPack;
import com.ecole.saas.center.domain.CenterStudentPackStatus;
import com.ecole.saas.center.infrastructure.CenterGroupRepository;
import com.ecole.saas.center.infrastructure.CenterSessionPackRepository;
import com.ecole.saas.center.infrastructure.CenterStudentPackRepository;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentSummary;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion du catalogue de forfaits/packs FCFA du centre et des packs attribués aux élèves.
 */
@Service
public class CenterPackService {

    private final CenterSessionPackRepository packRepository;
    private final CenterStudentPackRepository studentPackRepository;
    private final CenterGroupRepository groupRepository;
    private final AcademicStructurePort academicStructurePort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final CenterSubscriptionGuard subscriptionGuard;
    private final CenterSchoolGuard schoolGuard;
    private final AuditLogPort auditLogPort;

    public CenterPackService(
            CenterSessionPackRepository packRepository,
            CenterStudentPackRepository studentPackRepository,
            CenterGroupRepository groupRepository,
            AcademicStructurePort academicStructurePort,
            StudentDirectoryPort studentDirectoryPort,
            CenterSubscriptionGuard subscriptionGuard,
            CenterSchoolGuard schoolGuard,
            AuditLogPort auditLogPort) {
        this.packRepository = packRepository;
        this.studentPackRepository = studentPackRepository;
        this.groupRepository = groupRepository;
        this.academicStructurePort = academicStructurePort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.subscriptionGuard = subscriptionGuard;
        this.schoolGuard = schoolGuard;
        this.auditLogPort = auditLogPort;
    }

    @PreAuthorize(CenterRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<CenterSessionPack> listPacks(
            UUID academicYearId,
            UUID gradeLevelId,
            UUID subjectId,
            CenterPackBillingMode billingMode,
            Boolean active,
            Pageable pageable) {
        subscriptionGuard.requireReadAccess();
        Specification<CenterSessionPack> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (academicYearId != null) {
                predicates.add(cb.equal(root.get("academicYearId"), academicYearId));
            }
            if (gradeLevelId != null) {
                predicates.add(cb.equal(root.get("gradeLevelId"), gradeLevelId));
            }
            if (subjectId != null) {
                predicates.add(cb.equal(root.get("subjectId"), subjectId));
            }
            if (billingMode != null) {
                predicates.add(cb.equal(root.get("billingMode"), billingMode));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return packRepository.findAll(spec, pageable);
    }

    @PreAuthorize(CenterRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public CenterSessionPack getPack(UUID packId) {
        subscriptionGuard.requireReadAccess();
        return requirePack(packId);
    }

    @PreAuthorize(CenterRoles.PACK_CATALOG_WRITE_ROLES)
    @Transactional
    public CenterSessionPack createPack(
            UUID academicYearId,
            UUID gradeLevelId,
            UUID subjectId,
            String code,
            String name,
            String description,
            CenterPackBillingMode billingMode,
            Integer includedSessions,
            Integer durationDays,
            long priceXof) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        validateOptionalAcademicReferences(academicYearId, gradeLevelId, subjectId, true);
        String normalizedCode = CenterSessionPack.normalizeCode(code);
        if (packRepository.existsByCode(normalizedCode)) {
            throw new CenterConflictException(
                    "center-pack-already-exists",
                    "Code forfait déjà utilisé",
                    "Un forfait avec le code " + normalizedCode + " existe déjà dans cet établissement.");
        }
        CenterSessionPack pack = new CenterSessionPack(
                academicYearId,
                gradeLevelId,
                subjectId,
                normalizedCode,
                name,
                description,
                billingMode,
                includedSessions,
                durationDays,
                priceXof);
        CenterSessionPack saved = packRepository.save(pack);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.CENTER,
                AuditAction.CENTER_PACK_CREATED,
                "CENTER_SESSION_PACK",
                saved.getId(),
                "Création d'un forfait de séances du centre de renforcement.",
                Map.of(
                        "code", saved.getCode(),
                        "billingMode", saved.getBillingMode().name(),
                        "priceXof", saved.getPriceXof())));
        return saved;
    }

    @PreAuthorize(CenterRoles.PACK_CATALOG_WRITE_ROLES)
    @Transactional
    public CenterSessionPack updatePack(
            UUID packId,
            UUID academicYearId,
            UUID gradeLevelId,
            UUID subjectId,
            String name,
            String description,
            CenterPackBillingMode billingMode,
            Integer includedSessions,
            Integer durationDays,
            long priceXof) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterSessionPack pack = requirePack(packId);
        validateOptionalAcademicReferences(academicYearId, gradeLevelId, subjectId, true);
        pack.updateDetails(
                academicYearId,
                gradeLevelId,
                subjectId,
                name,
                description,
                billingMode,
                includedSessions,
                durationDays,
                priceXof);
        return packRepository.save(pack);
    }

    @PreAuthorize(CenterRoles.PACK_CATALOG_WRITE_ROLES)
    @Transactional
    public CenterSessionPack activatePack(UUID packId) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterSessionPack pack = requirePack(packId);
        pack.activate();
        return packRepository.save(pack);
    }

    @PreAuthorize(CenterRoles.PACK_CATALOG_WRITE_ROLES)
    @Transactional
    public CenterSessionPack deactivatePack(UUID packId) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterSessionPack pack = requirePack(packId);
        pack.deactivate();
        CenterSessionPack saved = packRepository.save(pack);
        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.CENTER,
                AuditAction.CENTER_PACK_DEACTIVATED,
                AuditOutcome.SUCCESS,
                "CENTER_SESSION_PACK",
                saved.getId(),
                "Désactivation d'un forfait de séances du centre.",
                Map.of("code", saved.getCode())));
        return saved;
    }

    @PreAuthorize(CenterRoles.STAFF_READ_ROLES)
    @Transactional(readOnly = true)
    public Page<StudentPackView> listStudentPacks(
            UUID studentId, UUID groupId, CenterStudentPackStatus status, Pageable pageable) {
        subscriptionGuard.requireReadAccess();
        Specification<CenterStudentPack> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            }
            if (groupId != null) {
                predicates.add(cb.equal(root.get("groupId"), groupId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return studentPackRepository.findAll(spec, pageable).map(this::toStudentPackView);
    }

    @PreAuthorize(CenterRoles.STAFF_READ_ROLES)
    @Transactional(readOnly = true)
    public StudentPackView getStudentPack(UUID studentPackId) {
        subscriptionGuard.requireReadAccess();
        CenterStudentPack pack = requireStudentPack(studentPackId);
        return toStudentPackView(pack);
    }

    @PreAuthorize(CenterRoles.STUDENT_PACK_WRITE_ROLES)
    @Transactional
    public StudentPackView assignPackToStudent(
            UUID studentId,
            UUID packId,
            UUID groupId,
            Long customPriceXof,
            Integer customTotalSessions,
            LocalDate startDate,
            LocalDate endDate,
            String notes,
            UUID actorId) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        StudentSummary student = studentDirectoryPort.findStudentById(studentId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-student-not-found", "Élève introuvable", "L'élève demandé est introuvable."));
        if (!student.active()) {
            throw new CenterConflictException(
                    "center-student-inactive",
                    "Élève inactif",
                    "Impossible d'attribuer un pack à un élève dont le dossier n'est pas ACTIVE.");
        }
        CenterSessionPack pack = requirePack(packId);
        if (groupId != null) {
            CenterGroup group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new CenterNotFoundException(
                            "center-group-not-found", "Cohorte introuvable", "La cohorte demandée est introuvable."));
            if (!group.isOperationalForEnrollmentsAndPlanning()) {
                throw new CenterConflictException(
                        "center-group-immutable",
                        "Cohorte non opérationnelle",
                        "Impossible d'associer un pack à une cohorte clôturée ou archivée.");
            }
        }
        CenterStudentPack assigned = new CenterStudentPack(
                student.id(),
                pack,
                groupId,
                customPriceXof,
                customTotalSessions,
                startDate,
                endDate,
                actorId,
                notes);
        CenterStudentPack saved = studentPackRepository.save(assigned);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.CENTER,
                AuditAction.CENTER_STUDENT_PACK_ASSIGNED,
                "CENTER_STUDENT_PACK",
                saved.getId(),
                "Attribution d'un pack de séances à un élève du centre.",
                Map.of(
                        "studentId", student.id().toString(),
                        "packCode", saved.getPackCodeSnapshot(),
                        "priceXof", saved.getPriceXof())));
        return new StudentPackView(saved, student);
    }

    @PreAuthorize(CenterRoles.PACK_CATALOG_WRITE_ROLES)
    @Transactional
    public StudentPackView cancelStudentPack(UUID studentPackId, String reason) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterStudentPack studentPack = requireStudentPack(studentPackId);
        studentPack.cancel(reason);
        CenterStudentPack saved = studentPackRepository.save(studentPack);
        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.CENTER,
                AuditAction.CENTER_STUDENT_PACK_CANCELLED,
                AuditOutcome.SUCCESS,
                "CENTER_STUDENT_PACK",
                saved.getId(),
                "Annulation d'un pack de séances élève.",
                Map.of(
                        "studentId", saved.getStudentId().toString(),
                        "packCode", saved.getPackCodeSnapshot())));
        return toStudentPackView(saved);
    }

    private void validateOptionalAcademicReferences(
            UUID academicYearId, UUID gradeLevelId, UUID subjectId, boolean requireMutableYear) {
        if (academicYearId != null) {
            AcademicYearSummary year = academicStructurePort.findAcademicYearById(academicYearId)
                    .orElseThrow(() -> new CenterNotFoundException(
                            "center-academic-year-not-found",
                            "Année scolaire introuvable",
                            "L'année scolaire référencée est introuvable."));
            if (requireMutableYear && ("CLOSED".equals(year.status()) || "ARCHIVED".equals(year.status()))) {
                throw new CenterConflictException(
                        "center-academic-year-closed",
                        "Année scolaire clôturée",
                        "Impossible de créer ou modifier un forfait sur une année scolaire clôturée ou archivée.");
            }
        }
        if (gradeLevelId != null) {
            GradeLevelSummary level = academicStructurePort.findGradeLevelById(gradeLevelId)
                    .orElseThrow(() -> new CenterNotFoundException(
                            "center-grade-level-not-found",
                            "Niveau introuvable",
                            "Le niveau référencé est introuvable."));
            if (!level.active()) {
                throw new CenterConflictException(
                        "center-grade-level-inactive",
                        "Niveau inactif",
                        "Le niveau référencé est inactif.");
            }
        }
        if (subjectId != null) {
            SubjectSummary subject = academicStructurePort.findSubjectById(subjectId)
                    .orElseThrow(() -> new CenterNotFoundException(
                            "center-subject-not-found",
                            "Matière introuvable",
                            "La matière référencée est introuvable."));
            if (!subject.active()) {
                throw new CenterConflictException(
                        "center-subject-inactive",
                        "Matière inactive",
                        "La matière référencée est inactive.");
            }
        }
    }

    private CenterSessionPack requirePack(UUID packId) {
        return packRepository.findById(packId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-pack-not-found", "Forfait introuvable", "Le forfait demandé est introuvable."));
    }

    private CenterStudentPack requireStudentPack(UUID studentPackId) {
        return studentPackRepository.findById(studentPackId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-student-pack-not-found",
                        "Forfait élève introuvable",
                        "Le forfait élève demandé est introuvable."));
    }

    private StudentPackView toStudentPackView(CenterStudentPack studentPack) {
        StudentSummary student = studentDirectoryPort.findStudentById(studentPack.getStudentId()).orElse(null);
        return new StudentPackView(studentPack, student);
    }

    public record StudentPackView(CenterStudentPack studentPack, StudentSummary student) {
    }
}
