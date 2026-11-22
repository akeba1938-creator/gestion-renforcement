package com.ecole.saas.student.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentContactSummary;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentEnrollmentSummary;
import com.ecole.saas.student.StudentSummary;
import com.ecole.saas.student.domain.EnrollmentStatus;
import com.ecole.saas.student.domain.MatriculeFormatter;
import com.ecole.saas.student.domain.Student;
import com.ecole.saas.student.domain.StudentEnrollment;
import com.ecole.saas.student.domain.StudentStatus;
import com.ecole.saas.student.infrastructure.StudentEnrollmentRepository;
import com.ecole.saas.student.infrastructure.StudentRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de la frontière publique {@link StudentDirectoryPort} consommée par les autres modules.
 */
@Service
public class StudentDirectoryQueryService implements StudentDirectoryPort {

    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final AcademicStructurePort academicStructurePort;

    public StudentDirectoryQueryService(
            StudentRepository studentRepository,
            StudentEnrollmentRepository enrollmentRepository,
            AcademicStructurePort academicStructurePort) {
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.academicStructurePort = academicStructurePort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentSummary> findStudentById(UUID studentId) {
        TenantContext.requireCurrent();
        if (studentId == null) {
            return Optional.empty();
        }
        return studentRepository.findById(studentId).map(StudentDirectoryQueryService::toStudentSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentSummary> findStudentByMatricule(String matricule) {
        TenantContext.requireCurrent();
        if (matricule == null || matricule.isBlank()) {
            return Optional.empty();
        }
        String normalized;
        try {
            normalized = MatriculeFormatter.normalize(matricule);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return studentRepository.findByMatricule(normalized).map(StudentDirectoryQueryService::toStudentSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentSummary> findStudentsByIds(Collection<UUID> studentIds) {
        TenantContext.requireCurrent();
        if (studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        return studentRepository.findAllById(studentIds).stream()
                .map(StudentDirectoryQueryService::toStudentSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentSummary> findStudentsByMatricules(Collection<String> matricules) {
        TenantContext.requireCurrent();
        if (matricules == null || matricules.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = matricules.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (normalized.isEmpty()) {
            return List.of();
        }
        return studentRepository.findByMatriculeIn(normalized).stream()
                .map(StudentDirectoryQueryService::toStudentSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentSummary> findStudentsByLastNamesIgnoreCase(Collection<String> lastNames) {
        TenantContext.requireCurrent();
        if (lastNames == null || lastNames.isEmpty()) {
            return List.of();
        }
        Set<String> lower = lastNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (lower.isEmpty()) {
            return List.of();
        }
        return studentRepository.findByLastNameLowerIn(lower).stream()
                .map(StudentDirectoryQueryService::toStudentSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentContactSummary> findStudentContactById(UUID studentId) {
        TenantContext.requireCurrent();
        if (studentId == null) {
            return Optional.empty();
        }
        return studentRepository.findById(studentId).map(StudentDirectoryQueryService::toStudentContactSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentContactSummary> findStudentContactByUserId(UUID userId) {
        TenantContext.requireCurrent();
        if (userId == null) {
            return Optional.empty();
        }
        return studentRepository.findByUserId(userId).map(StudentDirectoryQueryService::toStudentContactSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentContactSummary> findStudentContactsByIds(Collection<UUID> studentIds) {
        TenantContext.requireCurrent();
        if (studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        return studentRepository.findAllById(studentIds).stream()
                .map(StudentDirectoryQueryService::toStudentContactSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentEnrollmentSummary> findEnrollmentByStudentAndYear(UUID studentId, UUID academicYearId) {
        TenantContext.requireCurrent();
        if (studentId == null || academicYearId == null) {
            return Optional.empty();
        }
        return enrollmentRepository
                .findByStudentIdAndAcademicYearIdAndStatus(studentId, academicYearId, EnrollmentStatus.ACTIVE)
                .or(() -> enrollmentRepository
                        .findByStudentIdAndAcademicYearIdOrderByCreatedAtDesc(studentId, academicYearId)
                        .stream()
                        .findFirst())
                .map(StudentDirectoryQueryService::toEnrollmentSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentEnrollmentSummary> findActiveEnrollmentsByYearAndStudents(
            UUID academicYearId, Collection<UUID> studentIds) {
        TenantContext.requireCurrent();
        if (academicYearId == null || studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> validIds = studentIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return List.of();
        }
        return enrollmentRepository
                .findByAcademicYearIdAndStudentIdInAndStatus(academicYearId, validIds, EnrollmentStatus.ACTIVE)
                .stream()
                .map(StudentDirectoryQueryService::toEnrollmentSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentEnrollmentSummary> findCurrentEnrollmentForStudent(UUID studentId) {
        TenantContext.requireCurrent();
        if (studentId == null) {
            return Optional.empty();
        }
        Optional<AcademicYearSummary> currentYear = academicStructurePort.findCurrentAcademicYear();
        if (currentYear.isEmpty()) {
            return Optional.empty();
        }
        return findEnrollmentByStudentAndYear(studentId, currentYear.get().id());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentEnrollmentSummary> findEnrollmentsByStudent(UUID studentId) {
        TenantContext.requireCurrent();
        if (studentId == null) {
            return List.of();
        }
        return enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                .stream()
                .map(StudentDirectoryQueryService::toEnrollmentSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentEnrollmentSummary> findActiveEnrollmentsByClassRoom(UUID classRoomId) {
        TenantContext.requireCurrent();
        if (classRoomId == null) {
            return List.of();
        }
        return enrollmentRepository.findByClassRoomIdAndStatusOrderByCreatedAtAsc(classRoomId, EnrollmentStatus.ACTIVE)
                .stream()
                .map(StudentDirectoryQueryService::toEnrollmentSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentEnrollmentSummary> findActiveEnrollmentsByClassRooms(Collection<UUID> classRoomIds) {
        TenantContext.requireCurrent();
        if (classRoomIds == null || classRoomIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> validIds = classRoomIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return List.of();
        }
        return enrollmentRepository.findByClassRoomIdInAndStatus(validIds, EnrollmentStatus.ACTIVE)
                .stream()
                .map(StudentDirectoryQueryService::toEnrollmentSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> countActiveEnrollmentsByClassRooms(Collection<UUID> classRoomIds) {
        TenantContext.requireCurrent();
        if (classRoomIds == null || classRoomIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> validIds = classRoomIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (UUID id : validIds) {
            counts.put(id, 0L);
        }
        for (Object[] row : enrollmentRepository.countByClassRoomIdsAndStatus(validIds, EnrollmentStatus.ACTIVE)) {
            if (row != null && row.length >= 2 && row[0] instanceof UUID cid && row[1] instanceof Number n) {
                counts.put(cid, n.longValue());
            }
        }
        return Map.copyOf(counts);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findActiveStudentIdsByClassRooms(Collection<UUID> classRoomIds) {
        TenantContext.requireCurrent();
        if (classRoomIds == null || classRoomIds.isEmpty()) {
            return Set.of();
        }
        return enrollmentRepository.findByClassRoomIdInAndStatus(classRoomIds, EnrollmentStatus.ACTIVE).stream()
                .map(StudentEnrollment::getStudentId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isStudentActivelyEnrolledInAnyClass(UUID studentId, Collection<UUID> classRoomIds) {
        TenantContext.requireCurrent();
        if (studentId == null || classRoomIds == null || classRoomIds.isEmpty()) {
            return false;
        }
        return enrollmentRepository.existsByStudentIdAndClassRoomIdInAndStatus(
                studentId, classRoomIds, EnrollmentStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveStudents() {
        TenantContext.requireCurrent();
        return studentRepository.countByStatus(StudentStatus.ACTIVE);
    }

    private static StudentSummary toStudentSummary(Student student) {
        return new StudentSummary(
                student.getId(),
                student.getMatricule(),
                student.getFirstName(),
                student.getLastName(),
                student.getGender().name(),
                student.getBirthDate(),
                student.getStatus().name(),
                student.isActive());
    }

    private static StudentContactSummary toStudentContactSummary(Student student) {
        return new StudentContactSummary(
                student.getId(),
                student.getMatricule(),
                student.getUserId(),
                student.getFirstName(),
                student.getLastName(),
                student.getPhone(),
                student.getEmail(),
                student.isActive());
    }

    private static StudentEnrollmentSummary toEnrollmentSummary(StudentEnrollment enrollment) {
        return new StudentEnrollmentSummary(
                enrollment.getId(),
                enrollment.getStudentId(),
                enrollment.getAcademicYearId(),
                enrollment.getClassRoomId(),
                enrollment.getEnrollmentType().name(),
                enrollment.getStatus().name(),
                enrollment.getEnrollmentDate());
    }
}
