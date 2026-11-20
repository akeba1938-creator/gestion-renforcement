package com.ecole.saas.academic.application;

import com.ecole.saas.academic.domain.AcademicYear;
import com.ecole.saas.academic.domain.AcademicYearNotFoundException;
import com.ecole.saas.academic.domain.ClassRoom;
import com.ecole.saas.academic.domain.ClassRoomAlreadyExistsException;
import com.ecole.saas.academic.domain.ClassRoomNotFoundException;
import com.ecole.saas.academic.domain.ClassRoomStatus;
import com.ecole.saas.academic.domain.ClassSubject;
import com.ecole.saas.academic.domain.ClassSubjectAlreadyExistsException;
import com.ecole.saas.academic.domain.ClassSubjectNotFoundException;
import com.ecole.saas.academic.domain.GradeLevel;
import com.ecole.saas.academic.domain.GradeLevelInactiveException;
import com.ecole.saas.academic.domain.GradeLevelNotFoundException;
import com.ecole.saas.academic.domain.Subject;
import com.ecole.saas.academic.domain.SubjectInactiveException;
import com.ecole.saas.academic.domain.SubjectNotFoundException;
import com.ecole.saas.academic.infrastructure.AcademicYearRepository;
import com.ecole.saas.academic.infrastructure.ClassRoomRepository;
import com.ecole.saas.academic.infrastructure.ClassSubjectRepository;
import com.ecole.saas.academic.infrastructure.GradeLevelRepository;
import com.ecole.saas.academic.infrastructure.SubjectRepository;
import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * Service applicatif de gestion des classes/groupes et des matières qui leur sont rattachées.
 */
@Service
public class ClassRoomService {

    private static final String READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'SECRETARY', 'CASHIER', 'TEACHER', 'TUTOR')";
    private static final String WRITE_ROLES = "hasAnyRole('DIRECTOR', 'CENTER_MANAGER')";

    private final ClassRoomRepository classRoomRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final AcademicYearRepository academicYearRepository;
    private final GradeLevelRepository gradeLevelRepository;
    private final SubjectRepository subjectRepository;

    public ClassRoomService(
            ClassRoomRepository classRoomRepository,
            ClassSubjectRepository classSubjectRepository,
            AcademicYearRepository academicYearRepository,
            GradeLevelRepository gradeLevelRepository,
            SubjectRepository subjectRepository) {
        this.classRoomRepository = classRoomRepository;
        this.classSubjectRepository = classSubjectRepository;
        this.academicYearRepository = academicYearRepository;
        this.gradeLevelRepository = gradeLevelRepository;
        this.subjectRepository = subjectRepository;
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Page<ClassRoomView> listClassRooms(
            UUID academicYearId,
            UUID gradeLevelId,
            ClassRoomStatus status,
            Pageable pageable) {
        TenantContext.requireCurrent();
        Specification<ClassRoom> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (academicYearId != null) {
                predicates.add(cb.equal(root.get("academicYearId"), academicYearId));
            }
            if (gradeLevelId != null) {
                predicates.add(cb.equal(root.get("gradeLevelId"), gradeLevelId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<ClassRoom> page = classRoomRepository.findAll(spec, pageable);
        if (page.isEmpty()) {
            return page.map(c -> new ClassRoomView(c, null, null, null));
        }

        Set<UUID> yearIds = page.getContent().stream().map(ClassRoom::getAcademicYearId).collect(Collectors.toSet());
        Set<UUID> levelIds = page.getContent().stream().map(ClassRoom::getGradeLevelId).collect(Collectors.toSet());

        Map<UUID, AcademicYear> yearsById = academicYearRepository.findAllById(yearIds).stream()
                .collect(Collectors.toMap(AcademicYear::getId, Function.identity()));
        Map<UUID, GradeLevel> levelsById = gradeLevelRepository.findAllById(levelIds).stream()
                .collect(Collectors.toMap(GradeLevel::getId, Function.identity()));

        return page.map(room -> toClassRoomView(
                room,
                yearsById.get(room.getAcademicYearId()),
                levelsById.get(room.getGradeLevelId())));
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public ClassRoomView getClassRoom(UUID classRoomId) {
        TenantContext.requireCurrent();
        ClassRoom room = requireClassRoom(classRoomId);
        AcademicYear year = academicYearRepository.findById(room.getAcademicYearId()).orElse(null);
        GradeLevel level = gradeLevelRepository.findById(room.getGradeLevelId()).orElse(null);
        return toClassRoomView(room, year, level);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public ClassRoomView createClassRoom(
            UUID academicYearId,
            UUID gradeLevelId,
            String code,
            String name,
            String series,
            String roomName,
            Integer capacity) {
        TenantContext.requireCurrent();
        AcademicYear year = requireAcademicYear(academicYearId);
        year.assertMutable();

        GradeLevel level = requireGradeLevel(gradeLevelId);
        if (!level.isActive()) {
            throw new GradeLevelInactiveException("Impossible de créer une classe sur un niveau inactif.");
        }

        String normalizedCode = ClassRoom.normalizeCode(code);
        if (classRoomRepository.existsByAcademicYearIdAndCode(academicYearId, normalizedCode)) {
            throw new ClassRoomAlreadyExistsException(
                    "Une classe avec ce code existe déjà pour cette année scolaire.");
        }

        int effectiveCapacity = capacity != null ? capacity : ClassRoom.DEFAULT_CAPACITY;
        ClassRoom saved = classRoomRepository.save(new ClassRoom(
                academicYearId, gradeLevelId, normalizedCode, name, series, roomName, effectiveCapacity));
        return toClassRoomView(saved, year, level);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public ClassRoomView updateClassRoom(
            UUID classRoomId,
            UUID gradeLevelId,
            String name,
            String series,
            String roomName,
            int capacity) {
        TenantContext.requireCurrent();
        ClassRoom room = requireClassRoom(classRoomId);
        AcademicYear year = requireAcademicYear(room.getAcademicYearId());
        year.assertMutable();

        GradeLevel level = requireGradeLevel(gradeLevelId);
        if (!level.isActive() && !level.getId().equals(room.getGradeLevelId())) {
            throw new GradeLevelInactiveException("Impossible de rattacher une classe à un niveau inactif.");
        }

        room.update(gradeLevelId, name, series, roomName, capacity);
        ClassRoom saved = classRoomRepository.save(room);
        return toClassRoomView(saved, year, level);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public ClassRoomView archiveClassRoom(UUID classRoomId) {
        TenantContext.requireCurrent();
        ClassRoom room = requireClassRoom(classRoomId);
        AcademicYear year = requireAcademicYear(room.getAcademicYearId());
        year.assertMutable();

        room.archive();
        ClassRoom saved = classRoomRepository.save(room);
        GradeLevel level = gradeLevelRepository.findById(saved.getGradeLevelId()).orElse(null);
        return toClassRoomView(saved, year, level);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public ClassRoomView reactivateClassRoom(UUID classRoomId) {
        TenantContext.requireCurrent();
        ClassRoom room = requireClassRoom(classRoomId);
        AcademicYear year = requireAcademicYear(room.getAcademicYearId());
        year.assertMutable();

        room.reactivate();
        ClassRoom saved = classRoomRepository.save(room);
        GradeLevel level = gradeLevelRepository.findById(saved.getGradeLevelId()).orElse(null);
        return toClassRoomView(saved, year, level);
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Page<ClassSubjectView> listClassSubjects(UUID classRoomId, Boolean activeOnly, Pageable pageable) {
        TenantContext.requireCurrent();
        requireClassRoom(classRoomId);
        Page<ClassSubject> page = Boolean.TRUE.equals(activeOnly)
                ? classSubjectRepository.findByClassRoomIdAndActiveTrue(classRoomId, pageable)
                : classSubjectRepository.findByClassRoomId(classRoomId, pageable);
        if (page.isEmpty()) {
            return page.map(cs -> new ClassSubjectView(cs, null));
        }

        Set<UUID> subjectIds = page.getContent().stream().map(ClassSubject::getSubjectId).collect(Collectors.toSet());
        Map<UUID, Subject> subjectsById = subjectRepository.findAllById(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity()));
        return page.map(cs -> new ClassSubjectView(cs, subjectsById.get(cs.getSubjectId())));
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public ClassSubjectView assignSubjectToClass(
            UUID classRoomId,
            UUID subjectId,
            Integer coefficient,
            Integer weeklyHours,
            Boolean mandatory) {
        TenantContext.requireCurrent();
        ClassRoom room = requireClassRoom(classRoomId);
        room.assertActive();
        requireAcademicYear(room.getAcademicYearId()).assertMutable();

        Subject subject = requireSubject(subjectId);
        if (!subject.isActive()) {
            throw new SubjectInactiveException("Impossible d'affecter une matière inactive à une classe.");
        }
        if (classSubjectRepository.existsByClassRoomIdAndSubjectId(classRoomId, subjectId)) {
            throw new ClassSubjectAlreadyExistsException("Cette matière est déjà affectée à cette classe.");
        }

        int effectiveCoef = coefficient != null ? coefficient : subject.getDefaultCoefficient();
        boolean isMandatory = mandatory == null || mandatory;
        ClassSubject saved = classSubjectRepository.save(
                new ClassSubject(classRoomId, subjectId, effectiveCoef, weeklyHours, isMandatory));
        return new ClassSubjectView(saved, subject);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public ClassSubjectView updateClassSubject(
            UUID classRoomId,
            UUID classSubjectId,
            int coefficient,
            Integer weeklyHours,
            boolean mandatory) {
        TenantContext.requireCurrent();
        ClassRoom room = requireClassRoom(classRoomId);
        room.assertActive();
        requireAcademicYear(room.getAcademicYearId()).assertMutable();

        ClassSubject classSubject = requireClassSubject(classRoomId, classSubjectId);
        classSubject.update(coefficient, weeklyHours, mandatory);
        ClassSubject saved = classSubjectRepository.save(classSubject);
        Subject subject = subjectRepository.findById(saved.getSubjectId()).orElse(null);
        return new ClassSubjectView(saved, subject);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public ClassSubjectView activateClassSubject(UUID classRoomId, UUID classSubjectId) {
        TenantContext.requireCurrent();
        ClassRoom room = requireClassRoom(classRoomId);
        room.assertActive();
        requireAcademicYear(room.getAcademicYearId()).assertMutable();

        ClassSubject classSubject = requireClassSubject(classRoomId, classSubjectId);
        Subject subject = requireSubject(classSubject.getSubjectId());
        if (!subject.isActive()) {
            throw new SubjectInactiveException(
                    "Impossible de réactiver l'affectation d'une matière qui est désactivée.");
        }
        classSubject.activate();
        return new ClassSubjectView(classSubjectRepository.save(classSubject), subject);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public ClassSubjectView deactivateClassSubject(UUID classRoomId, UUID classSubjectId) {
        TenantContext.requireCurrent();
        ClassRoom room = requireClassRoom(classRoomId);
        room.assertActive();
        requireAcademicYear(room.getAcademicYearId()).assertMutable();

        ClassSubject classSubject = requireClassSubject(classRoomId, classSubjectId);
        classSubject.deactivate();
        ClassSubject saved = classSubjectRepository.save(classSubject);
        Subject subject = subjectRepository.findById(saved.getSubjectId()).orElse(null);
        return new ClassSubjectView(saved, subject);
    }

    private ClassRoom requireClassRoom(UUID classRoomId) {
        return classRoomRepository.findById(classRoomId)
                .orElseThrow(() -> new ClassRoomNotFoundException("Classe introuvable."));
    }

    private AcademicYear requireAcademicYear(UUID academicYearId) {
        return academicYearRepository.findById(academicYearId)
                .orElseThrow(() -> new AcademicYearNotFoundException("Année scolaire introuvable."));
    }

    private GradeLevel requireGradeLevel(UUID gradeLevelId) {
        return gradeLevelRepository.findById(gradeLevelId)
                .orElseThrow(() -> new GradeLevelNotFoundException("Niveau introuvable."));
    }

    private Subject requireSubject(UUID subjectId) {
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> new SubjectNotFoundException("Matière introuvable."));
    }

    private ClassSubject requireClassSubject(UUID classRoomId, UUID classSubjectId) {
        return classSubjectRepository.findByIdAndClassRoomId(classSubjectId, classRoomId)
                .orElseThrow(() -> new ClassSubjectNotFoundException("Matière de classe introuvable."));
    }

    private static ClassRoomView toClassRoomView(ClassRoom room, AcademicYear year, GradeLevel level) {
        return new ClassRoomView(
                room,
                year != null ? year.getCode() : null,
                level != null ? level.getCode() : null,
                level != null ? level.getName() : null);
    }

    public record ClassRoomView(
            ClassRoom classRoom,
            String academicYearCode,
            String gradeLevelCode,
            String gradeLevelName) {
    }

    public record ClassSubjectView(ClassSubject classSubject, Subject subject) {
    }
}
