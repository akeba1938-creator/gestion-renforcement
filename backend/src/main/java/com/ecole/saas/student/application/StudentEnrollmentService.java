package com.ecole.saas.student.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.academic.ClassRoomSummary;
import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import com.ecole.saas.student.domain.AcademicReferenceNotFoundException;
import com.ecole.saas.student.domain.ClassRoomCapacityExceededException;
import com.ecole.saas.student.domain.EnrollmentNotFoundException;
import com.ecole.saas.student.domain.EnrollmentStatus;
import com.ecole.saas.student.domain.EnrollmentType;
import com.ecole.saas.student.domain.InvalidEnrollmentStateException;
import com.ecole.saas.student.domain.Student;
import com.ecole.saas.student.domain.StudentAlreadyEnrolledException;
import com.ecole.saas.student.domain.StudentEnrollment;
import com.ecole.saas.student.domain.StudentNotFoundException;
import com.ecole.saas.student.infrastructure.StudentEnrollmentRepository;
import com.ecole.saas.student.infrastructure.StudentRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Service applicatif de gestion des inscriptions annuelles et affectations aux classes via
 * {@link AcademicStructurePort}.
 */
@Service
public class StudentEnrollmentService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Dakar");

    private final StudentEnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final AcademicStructurePort academicStructurePort;
    private final SchoolDirectory schoolDirectory;
    private final StudentSubscriptionGuard subscriptionGuard;
    private final Clock clock;

    public StudentEnrollmentService(
            StudentEnrollmentRepository enrollmentRepository,
            StudentRepository studentRepository,
            AcademicStructurePort academicStructurePort,
            SchoolDirectory schoolDirectory,
            StudentSubscriptionGuard subscriptionGuard,
            Clock clock) {
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.academicStructurePort = academicStructurePort;
        this.schoolDirectory = schoolDirectory;
        this.subscriptionGuard = subscriptionGuard;
        this.clock = clock;
    }

    @PreAuthorize(StudentService.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<EnrollmentView> listEnrollments(
            UUID academicYearId,
            UUID classRoomId,
            UUID studentId,
            EnrollmentStatus status,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Specification<StudentEnrollment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (academicYearId != null) {
                predicates.add(cb.equal(root.get("academicYearId"), academicYearId));
            }
            if (classRoomId != null) {
                predicates.add(cb.equal(root.get("classRoomId"), classRoomId));
            }
            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return enrichPage(enrollmentRepository.findAll(spec, pageable));
    }

    @PreAuthorize(StudentService.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<EnrollmentView> listStudentEnrollments(UUID studentId, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        requireStudent(studentId);
        return enrichPage(enrollmentRepository.findByStudentId(studentId, pageable));
    }

    @PreAuthorize(StudentService.READ_ROLES)
    @Transactional(readOnly = true)
    public EnrollmentView getEnrollment(UUID enrollmentId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        StudentEnrollment enrollment = requireEnrollment(enrollmentId);
        return toView(
                enrollment,
                studentRepository.findById(enrollment.getStudentId()).orElse(null),
                academicStructurePort.findAcademicYearById(enrollment.getAcademicYearId()).orElse(null),
                academicStructurePort.findClassRoomById(enrollment.getClassRoomId()).orElse(null));
    }

    @PreAuthorize(StudentService.WRITE_ROLES)
    @Transactional
    public EnrollmentView enrollStudent(
            UUID studentId,
            UUID academicYearId,
            UUID classRoomId,
            EnrollmentType enrollmentType,
            LocalDate enrollmentDate,
            String previousSchool,
            Boolean repeating,
            String notes) {
        TenantId tenantId = TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Student student = requireStudent(studentId);
        student.assertActive();

        AcademicYearSummary year = requireAcademicYear(academicYearId);
        assertYearMutable(year);

        ClassRoomSummary classRoom = requireClassRoom(classRoomId);
        assertClassRoomUsableForYear(classRoom, academicYearId);

        if (enrollmentRepository.existsByStudentIdAndAcademicYearIdAndStatus(
                studentId, academicYearId, EnrollmentStatus.ACTIVE)) {
            throw new StudentAlreadyEnrolledException(
                    "Cet élève possède déjà une inscription active pour cette année scolaire.");
        }

        assertClassCapacityAvailable(classRoom);

        LocalDate effectiveDate = enrollmentDate != null ? enrollmentDate : todayForCurrentSchool(tenantId);
        EnrollmentType effectiveType = enrollmentType != null ? enrollmentType : EnrollmentType.NEW_ADMISSION;
        boolean isRepeating = Boolean.TRUE.equals(repeating);

        StudentEnrollment saved = enrollmentRepository.save(new StudentEnrollment(
                studentId,
                academicYearId,
                classRoomId,
                effectiveType,
                effectiveDate,
                previousSchool,
                isRepeating,
                notes));
        return toView(saved, student, year, classRoom);
    }

    @PreAuthorize(StudentService.WRITE_ROLES)
    @Transactional
    public EnrollmentView transferClass(UUID enrollmentId, UUID targetClassRoomId, String reason) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        StudentEnrollment enrollment = requireEnrollment(enrollmentId);
        enrollment.assertActive();

        AcademicYearSummary year = requireAcademicYear(enrollment.getAcademicYearId());
        assertYearMutable(year);

        ClassRoomSummary targetClass = requireClassRoom(targetClassRoomId);
        assertClassRoomUsableForYear(targetClass, enrollment.getAcademicYearId());
        assertClassCapacityAvailable(targetClass);

        enrollment.transferClass(targetClassRoomId, reason);
        StudentEnrollment saved = enrollmentRepository.save(enrollment);
        Student student = studentRepository.findById(saved.getStudentId()).orElse(null);
        return toView(saved, student, year, targetClass);
    }

    @PreAuthorize(StudentService.WRITE_ROLES)
    @Transactional
    public EnrollmentView completeEnrollment(UUID enrollmentId, LocalDate endDate, String reason) {
        TenantId tenantId = TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        StudentEnrollment enrollment = requireEnrollment(enrollmentId);
        AcademicYearSummary year = requireAcademicYear(enrollment.getAcademicYearId());
        assertYearMutable(year);

        LocalDate effectiveEndDate = endDate != null ? endDate : todayForCurrentSchool(tenantId);
        enrollment.complete(effectiveEndDate, reason);
        StudentEnrollment saved = enrollmentRepository.save(enrollment);
        return buildView(saved, year);
    }

    @PreAuthorize(StudentService.WRITE_ROLES)
    @Transactional
    public EnrollmentView withdrawEnrollment(UUID enrollmentId, LocalDate endDate, String reason) {
        TenantId tenantId = TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        StudentEnrollment enrollment = requireEnrollment(enrollmentId);
        AcademicYearSummary year = requireAcademicYear(enrollment.getAcademicYearId());
        assertYearMutable(year);

        LocalDate effectiveEndDate = endDate != null ? endDate : todayForCurrentSchool(tenantId);
        enrollment.withdraw(effectiveEndDate, reason);
        StudentEnrollment saved = enrollmentRepository.save(enrollment);
        return buildView(saved, year);
    }

    @PreAuthorize(StudentService.WRITE_ROLES)
    @Transactional
    public EnrollmentView transferOutEnrollment(UUID enrollmentId, LocalDate endDate, String reason) {
        TenantId tenantId = TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        StudentEnrollment enrollment = requireEnrollment(enrollmentId);
        AcademicYearSummary year = requireAcademicYear(enrollment.getAcademicYearId());
        assertYearMutable(year);

        LocalDate effectiveEndDate = endDate != null ? endDate : todayForCurrentSchool(tenantId);
        enrollment.transferOut(effectiveEndDate, reason);
        StudentEnrollment saved = enrollmentRepository.save(enrollment);
        return buildView(saved, year);
    }

    @PreAuthorize(StudentService.WRITE_ROLES)
    @Transactional
    public EnrollmentView cancelEnrollment(UUID enrollmentId, String reason) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        StudentEnrollment enrollment = requireEnrollment(enrollmentId);
        AcademicYearSummary year = requireAcademicYear(enrollment.getAcademicYearId());
        assertYearMutable(year);

        enrollment.cancel(reason);
        StudentEnrollment saved = enrollmentRepository.save(enrollment);
        return buildView(saved, year);
    }

    private Page<EnrollmentView> enrichPage(Page<StudentEnrollment> page) {
        if (page.isEmpty()) {
            return page.map(e -> toView(e, null, null, null));
        }

        Set<UUID> studentIds = page.getContent().stream()
                .map(StudentEnrollment::getStudentId)
                .collect(Collectors.toSet());
        Map<UUID, Student> studentsById = studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));

        Map<UUID, AcademicYearSummary> yearsById = new HashMap<>();
        Map<UUID, ClassRoomSummary> classesById = new HashMap<>();

        for (StudentEnrollment enrollment : page.getContent()) {
            yearsById.computeIfAbsent(
                    enrollment.getAcademicYearId(),
                    id -> academicStructurePort.findAcademicYearById(id).orElse(null));
            classesById.computeIfAbsent(
                    enrollment.getClassRoomId(),
                    id -> academicStructurePort.findClassRoomById(id).orElse(null));
        }

        return page.map(enrollment -> toView(
                enrollment,
                studentsById.get(enrollment.getStudentId()),
                yearsById.get(enrollment.getAcademicYearId()),
                classesById.get(enrollment.getClassRoomId())));
    }

    private EnrollmentView buildView(StudentEnrollment enrollment, AcademicYearSummary year) {
        Student student = studentRepository.findById(enrollment.getStudentId()).orElse(null);
        ClassRoomSummary classRoom = academicStructurePort
                .findClassRoomById(enrollment.getClassRoomId())
                .orElse(null);
        return toView(enrollment, student, year, classRoom);
    }

    private Student requireStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException("Élève introuvable."));
    }

    private StudentEnrollment requireEnrollment(UUID enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException("Inscription introuvable."));
    }

    private AcademicYearSummary requireAcademicYear(UUID academicYearId) {
        return academicStructurePort.findAcademicYearById(academicYearId)
                .orElseThrow(() -> new AcademicReferenceNotFoundException("Année scolaire introuvable."));
    }

    private ClassRoomSummary requireClassRoom(UUID classRoomId) {
        return academicStructurePort.findClassRoomById(classRoomId)
                .orElseThrow(() -> new AcademicReferenceNotFoundException("Classe introuvable."));
    }

    private void assertYearMutable(AcademicYearSummary year) {
        if (!"DRAFT".equals(year.status()) && !"ACTIVE".equals(year.status())) {
            throw new InvalidEnrollmentStateException(
                    "Impossible d'inscrire ou de modifier une inscription sur une année scolaire clôturée ou archivée.");
        }
    }

    private void assertClassRoomUsableForYear(ClassRoomSummary classRoom, UUID academicYearId) {
        if (!"ACTIVE".equals(classRoom.status())) {
            throw new InvalidEnrollmentStateException(
                    "Impossible d'affecter un élève à une classe archivée.");
        }
        if (!academicYearId.equals(classRoom.academicYearId())) {
            throw new IllegalArgumentException(
                    "La classe sélectionnée n'appartient pas à l'année scolaire demandée.");
        }
    }

    private void assertClassCapacityAvailable(ClassRoomSummary classRoom) {
        long activeInClass = enrollmentRepository.countByClassRoomIdAndStatus(
                classRoom.id(), EnrollmentStatus.ACTIVE);
        if (activeInClass >= classRoom.capacity()) {
            throw new ClassRoomCapacityExceededException(
                    "La classe " + classRoom.code() + " a atteint sa capacité maximale de "
                            + classRoom.capacity() + " élèves.");
        }
    }

    private LocalDate todayForCurrentSchool(TenantId tenantId) {
        ZoneId zone = schoolDirectory.findProfileById(tenantId.value())
                .map(profile -> {
                    try {
                        return profile.timezone() != null && !profile.timezone().isBlank()
                                ? ZoneId.of(profile.timezone())
                                : DEFAULT_ZONE;
                    } catch (DateTimeException ex) {
                        return DEFAULT_ZONE;
                    }
                })
                .orElse(DEFAULT_ZONE);
        return LocalDate.now(clock.withZone(zone));
    }

    private static EnrollmentView toView(
            StudentEnrollment enrollment,
            Student student,
            AcademicYearSummary year,
            ClassRoomSummary classRoom) {
        return new EnrollmentView(
                enrollment,
                student != null ? student.getMatricule() : null,
                student != null ? student.getFirstName() : null,
                student != null ? student.getLastName() : null,
                year != null ? year.code() : null,
                classRoom != null ? classRoom.code() : null,
                classRoom != null ? classRoom.name() : null);
    }

    public record EnrollmentView(
            StudentEnrollment enrollment,
            String studentMatricule,
            String studentFirstName,
            String studentLastName,
            String academicYearCode,
            String classRoomCode,
            String classRoomName) {
    }
}
