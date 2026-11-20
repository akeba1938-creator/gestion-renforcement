package com.ecole.saas.student.application;

import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import com.ecole.saas.student.domain.MatriculeFormatter;
import com.ecole.saas.student.domain.Student;
import com.ecole.saas.student.domain.StudentGender;
import com.ecole.saas.student.domain.StudentNotFoundException;
import com.ecole.saas.student.domain.StudentStatus;
import com.ecole.saas.student.infrastructure.MatriculeSequenceRepository;
import com.ecole.saas.student.infrastructure.StudentRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Service applicatif de gestion des dossiers élèves, avec génération séquentielle des matricules
 * isolée par établissement et contrôle des quotas d'abonnement.
 */
@Service
public class StudentService {

    static final String READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY', 'CASHIER', 'CENSOR', 'TEACHER', 'TUTOR')";
    static final String WRITE_ROLES = "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'SECRETARY')";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Dakar");

    private final StudentRepository studentRepository;
    private final MatriculeSequenceRepository matriculeSequenceRepository;
    private final SchoolDirectory schoolDirectory;
    private final StudentSubscriptionGuard subscriptionGuard;
    private final Clock clock;

    public StudentService(
            StudentRepository studentRepository,
            MatriculeSequenceRepository matriculeSequenceRepository,
            SchoolDirectory schoolDirectory,
            StudentSubscriptionGuard subscriptionGuard,
            Clock clock) {
        this.studentRepository = studentRepository;
        this.matriculeSequenceRepository = matriculeSequenceRepository;
        this.schoolDirectory = schoolDirectory;
        this.subscriptionGuard = subscriptionGuard;
        this.clock = clock;
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Page<Student> listStudents(StudentStatus status, StudentGender gender, String search, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Specification<Student> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (gender != null) {
                predicates.add(cb.equal(root.get("gender"), gender));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("matricule")), pattern),
                        cb.like(cb.lower(root.get("firstName")), pattern),
                        cb.like(cb.lower(root.get("lastName")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return studentRepository.findAll(spec, pageable);
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Student getStudent(UUID studentId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        return requireStudent(studentId);
    }

    @PreAuthorize(READ_ROLES)
    @Transactional(readOnly = true)
    public Student getStudentByMatricule(String rawMatricule) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        String normalized = MatriculeFormatter.normalize(rawMatricule);
        return studentRepository.findByMatricule(normalized)
                .orElseThrow(() -> new StudentNotFoundException("Élève introuvable."));
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Student createStudent(
            String firstName,
            String lastName,
            StudentGender gender,
            LocalDate birthDate,
            String birthPlace,
            String nationality,
            String address,
            String phone,
            String email,
            String medicalNotes,
            LocalDate admissionDate) {
        TenantId tenantId = TenantContext.requireCurrent();
        long activeCount = studentRepository.countByStatus(StudentStatus.ACTIVE);
        subscriptionGuard.assertStudentQuotaAvailable(activeCount);

        SchoolProfileSnapshot school = requireActiveSchool(tenantId);
        LocalDate today = todayForSchool(school);
        LocalDate effectiveAdmissionDate = admissionDate != null ? admissionDate : today;
        int sequenceYear = effectiveAdmissionDate.getYear();
        if (sequenceYear < MatriculeFormatter.MIN_YEAR || sequenceYear > MatriculeFormatter.MAX_YEAR) {
            throw new IllegalArgumentException(
                    "L'année d'admission doit être comprise entre "
                            + MatriculeFormatter.MIN_YEAR + " et " + MatriculeFormatter.MAX_YEAR + ".");
        }

        long sequenceNumber = matriculeSequenceRepository.nextSequenceValue(sequenceYear);
        String matricule = MatriculeFormatter.format(school.code(), sequenceYear, sequenceNumber);

        Student student = new Student(
                matricule,
                firstName,
                lastName,
                gender,
                birthDate,
                birthPlace,
                nationality,
                address,
                phone,
                email,
                medicalNotes,
                effectiveAdmissionDate,
                today);
        return studentRepository.save(student);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Student updateStudent(
            UUID studentId,
            String firstName,
            String lastName,
            StudentGender gender,
            LocalDate birthDate,
            String birthPlace,
            String nationality,
            String address,
            String phone,
            String email,
            String medicalNotes,
            LocalDate admissionDate) {
        TenantId tenantId = TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Student student = requireStudent(studentId);
        LocalDate today = todayForCurrentSchool(tenantId);
        student.updateProfile(
                firstName,
                lastName,
                gender,
                birthDate,
                birthPlace,
                nationality,
                address,
                phone,
                email,
                medicalNotes,
                admissionDate,
                today);
        return studentRepository.save(student);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Student transferStudent(UUID studentId, String reason) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        Student student = requireStudent(studentId);
        student.markTransferred(reason);
        return studentRepository.save(student);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Student graduateStudent(UUID studentId, String reason) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        Student student = requireStudent(studentId);
        student.markGraduated(reason);
        return studentRepository.save(student);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Student withdrawStudent(UUID studentId, String reason) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        Student student = requireStudent(studentId);
        student.markWithdrawn(reason);
        return studentRepository.save(student);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Student archiveStudent(UUID studentId, String reason) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();
        Student student = requireStudent(studentId);
        student.archive(reason, clock.instant());
        return studentRepository.save(student);
    }

    @PreAuthorize(WRITE_ROLES)
    @Transactional
    public Student reactivateStudent(UUID studentId) {
        TenantContext.requireCurrent();
        long activeCount = studentRepository.countByStatus(StudentStatus.ACTIVE);
        subscriptionGuard.assertStudentQuotaAvailable(activeCount);

        Student student = requireStudent(studentId);
        student.reactivate();
        return studentRepository.save(student);
    }

    private Student requireStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException("Élève introuvable."));
    }

    private SchoolProfileSnapshot requireActiveSchool(TenantId tenantId) {
        SchoolProfileSnapshot profile = schoolDirectory.findProfileById(tenantId.value())
                .orElseThrow(() -> new IllegalStateException("Établissement courant introuvable."));
        if (!profile.active()) {
            throw new IllegalStateException(
                    "Impossible d'enregistrer un élève sur un établissement qui n'est pas actif.");
        }
        return profile;
    }

    private LocalDate todayForCurrentSchool(TenantId tenantId) {
        ZoneId zone = schoolDirectory.findProfileById(tenantId.value())
                .map(StudentService::resolveZone)
                .orElse(DEFAULT_ZONE);
        return LocalDate.now(clock.withZone(zone));
    }

    private LocalDate todayForSchool(SchoolProfileSnapshot school) {
        return LocalDate.now(clock.withZone(resolveZone(school)));
    }

    private static ZoneId resolveZone(SchoolProfileSnapshot school) {
        if (school == null || school.timezone() == null || school.timezone().isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(school.timezone());
        } catch (DateTimeException ex) {
            return DEFAULT_ZONE;
        }
    }
}
