package com.ecole.saas.teacher.application;

import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.teacher.TeacherAssignmentSummary;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import com.ecole.saas.teacher.domain.Teacher;
import com.ecole.saas.teacher.domain.TeacherAssignment;
import com.ecole.saas.teacher.domain.TeacherStatus;
import com.ecole.saas.teacher.infrastructure.TeacherAssignmentRepository;
import com.ecole.saas.teacher.infrastructure.TeacherRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de la frontière publique {@link TeacherDirectoryPort} exposée aux modules
 * {@code student}, {@code grade} et {@code attendance} dans le contexte tenant courant.
 */
@Service
public class TeacherDirectoryQueryService implements TeacherDirectoryPort {

    private final TeacherRepository teacherRepository;
    private final TeacherAssignmentRepository assignmentRepository;

    public TeacherDirectoryQueryService(
            TeacherRepository teacherRepository,
            TeacherAssignmentRepository assignmentRepository) {
        this.teacherRepository = teacherRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TeacherSummary> findTeacherById(UUID teacherId) {
        TenantContext.requireCurrent();
        if (teacherId == null) {
            return Optional.empty();
        }
        return teacherRepository.findById(teacherId).map(TeacherDirectoryQueryService::toTeacherSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, TeacherSummary> findTeachersByIds(Collection<UUID> teacherIds) {
        TenantContext.requireCurrent();
        if (teacherIds == null || teacherIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> validIds = teacherIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return Map.of();
        }
        return teacherRepository.findAllById(validIds).stream()
                .collect(Collectors.toMap(Teacher::getId, TeacherDirectoryQueryService::toTeacherSummary, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TeacherSummary> findTeacherByUserId(UUID userId) {
        TenantContext.requireCurrent();
        if (userId == null) {
            return Optional.empty();
        }
        return teacherRepository.findByUserId(userId).map(TeacherDirectoryQueryService::toTeacherSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherSummary> findAllActiveTeachers() {
        TenantContext.requireCurrent();
        return teacherRepository.findByStatusOrderByLastNameAscFirstNameAsc(TeacherStatus.ACTIVE).stream()
                .map(TeacherDirectoryQueryService::toTeacherSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherAssignmentSummary> findActiveAssignmentsByTeacherId(UUID teacherId) {
        TenantContext.requireCurrent();
        if (teacherId == null) {
            return List.of();
        }
        Teacher teacher = teacherRepository.findById(teacherId).orElse(null);
        if (teacher == null || !teacher.isActive()) {
            return List.of();
        }
        return assignmentRepository.findByTeacherIdAndActiveTrueOrderByCreatedAtAsc(teacherId).stream()
                .map(a -> toAssignmentSummary(a, teacher))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherAssignmentSummary> findActiveAssignmentsByUserId(UUID userId) {
        TenantContext.requireCurrent();
        if (userId == null) {
            return List.of();
        }
        Teacher teacher = teacherRepository.findByUserId(userId).orElse(null);
        if (teacher == null || !teacher.isActive()) {
            return List.of();
        }
        return assignmentRepository.findByTeacherIdAndActiveTrueOrderByCreatedAtAsc(teacher.getId()).stream()
                .map(a -> toAssignmentSummary(a, teacher))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherAssignmentSummary> findActiveAssignmentsByClassRoom(UUID classRoomId) {
        TenantContext.requireCurrent();
        if (classRoomId == null) {
            return List.of();
        }
        List<TeacherAssignment> assignments =
                assignmentRepository.findByClassRoomIdAndActiveTrueOrderByCreatedAtAsc(classRoomId);
        if (assignments.isEmpty()) {
            return List.of();
        }
        Set<UUID> teacherIds = assignments.stream().map(TeacherAssignment::getTeacherId).collect(Collectors.toSet());
        Map<UUID, Teacher> teachersById = teacherRepository.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity()));
        return assignments.stream()
                .filter(a -> {
                    Teacher t = teachersById.get(a.getTeacherId());
                    return t != null && t.isActive();
                })
                .map(a -> toAssignmentSummary(a, teachersById.get(a.getTeacherId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findAssignedClassRoomIdsByUserId(UUID userId) {
        TenantContext.requireCurrent();
        if (userId == null) {
            return Set.of();
        }
        Teacher teacher = teacherRepository.findByUserId(userId).orElse(null);
        if (teacher == null || !teacher.isActive()) {
            return Set.of();
        }
        return assignmentRepository.findByTeacherIdAndActiveTrueOrderByCreatedAtAsc(teacher.getId()).stream()
                .map(TeacherAssignment::getClassRoomId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUserAssignedToClass(UUID userId, UUID classRoomId) {
        TenantContext.requireCurrent();
        if (userId == null || classRoomId == null) {
            return false;
        }
        Teacher teacher = teacherRepository.findByUserId(userId).orElse(null);
        if (teacher == null || !teacher.isActive()) {
            return false;
        }
        return assignmentRepository.existsByTeacherIdAndClassRoomIdAndActiveTrue(teacher.getId(), classRoomId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUserAssignedToClassSubject(UUID userId, UUID classRoomId, UUID subjectId) {
        TenantContext.requireCurrent();
        if (userId == null || classRoomId == null || subjectId == null) {
            return false;
        }
        Teacher teacher = teacherRepository.findByUserId(userId).orElse(null);
        if (teacher == null || !teacher.isActive()) {
            return false;
        }
        return assignmentRepository.existsByTeacherIdAndClassRoomIdAndSubjectIdAndActiveTrue(
                teacher.getId(), classRoomId, subjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUserHomeroomTeacherOfClass(UUID userId, UUID classRoomId) {
        TenantContext.requireCurrent();
        if (userId == null || classRoomId == null) {
            return false;
        }
        Teacher teacher = teacherRepository.findByUserId(userId).orElse(null);
        if (teacher == null || !teacher.isActive()) {
            return false;
        }
        return assignmentRepository.existsByTeacherIdAndClassRoomIdAndHomeroomTeacherTrueAndActiveTrue(
                teacher.getId(), classRoomId);
    }

    private static TeacherSummary toTeacherSummary(Teacher teacher) {
        return new TeacherSummary(
                teacher.getId(),
                teacher.getEmployeeCode(),
                teacher.getUserId(),
                teacher.getFirstName(),
                teacher.getLastName(),
                teacher.getTeacherType().name(),
                teacher.getPhone(),
                teacher.getEmail(),
                teacher.getHourlyRateXof(),
                teacher.getStatus().name(),
                teacher.isActive());
    }

    private static TeacherAssignmentSummary toAssignmentSummary(TeacherAssignment assignment, Teacher teacher) {
        return new TeacherAssignmentSummary(
                assignment.getId(),
                assignment.getTeacherId(),
                teacher != null ? teacher.getUserId() : null,
                teacher != null ? teacher.getFullName() : null,
                assignment.getAcademicYearId(),
                assignment.getClassRoomId(),
                assignment.getSubjectId(),
                assignment.getClassSubjectId(),
                assignment.isHomeroomTeacher(),
                assignment.getWeeklyHours(),
                assignment.isActive());
    }
}
