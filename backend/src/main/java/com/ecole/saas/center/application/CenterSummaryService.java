package com.ecole.saas.center.application;

import com.ecole.saas.attendance.AttendanceSummaryPort;
import com.ecole.saas.attendance.StudentAttendanceSummary;
import com.ecole.saas.center.StudentCenterSummary;
import com.ecole.saas.center.TutorCenterWorkloadSummary;
import com.ecole.saas.center.domain.CenterGroupEnrollment;
import com.ecole.saas.center.domain.CenterGroupEnrollmentStatus;
import com.ecole.saas.center.domain.CenterNotFoundException;
import com.ecole.saas.center.domain.CenterStudentPack;
import com.ecole.saas.center.domain.CenterValidationException;
import com.ecole.saas.center.infrastructure.CenterGroupEnrollmentRepository;
import com.ecole.saas.center.infrastructure.CenterStudentPackRepository;
import com.ecole.saas.grade.GradeSummaryPort;
import com.ecole.saas.grade.StudentPeriodGradeSummary;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentSummary;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vues de synthèse du module centre (suivi élève enrichi de notes/assiduité scolaire si pertinent,
 * et charge/rémunération estimée des intervenants en FCFA entiers).
 */
@Service
public class CenterSummaryService {

    private final CenterDirectoryQueryService directoryQueryService;
    private final CenterGroupEnrollmentRepository enrollmentRepository;
    private final CenterStudentPackRepository studentPackRepository;
    private final StudentDirectoryPort studentDirectoryPort;
    private final TeacherDirectoryPort teacherDirectoryPort;
    private final GradeSummaryPort gradeSummaryPort;
    private final AttendanceSummaryPort attendanceSummaryPort;
    private final CenterSubscriptionGuard subscriptionGuard;
    private final CenterTutorScopeGuard tutorScopeGuard;

    public CenterSummaryService(
            CenterDirectoryQueryService directoryQueryService,
            CenterGroupEnrollmentRepository enrollmentRepository,
            CenterStudentPackRepository studentPackRepository,
            StudentDirectoryPort studentDirectoryPort,
            TeacherDirectoryPort teacherDirectoryPort,
            GradeSummaryPort gradeSummaryPort,
            AttendanceSummaryPort attendanceSummaryPort,
            CenterSubscriptionGuard subscriptionGuard,
            CenterTutorScopeGuard tutorScopeGuard) {
        this.directoryQueryService = directoryQueryService;
        this.enrollmentRepository = enrollmentRepository;
        this.studentPackRepository = studentPackRepository;
        this.studentDirectoryPort = studentDirectoryPort;
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.gradeSummaryPort = gradeSummaryPort;
        this.attendanceSummaryPort = attendanceSummaryPort;
        this.subscriptionGuard = subscriptionGuard;
        this.tutorScopeGuard = tutorScopeGuard;
    }

    @PreAuthorize(CenterRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public StudentCenterOverview getStudentOverview(
            UUID studentId,
            LocalDate fromDate,
            LocalDate toDate,
            UUID academicYearId,
            UUID evaluationPeriodId) {
        subscriptionGuard.requireReadAccess();
        validateDateRange(fromDate, toDate);
        StudentSummary student = studentDirectoryPort.findStudentById(studentId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-student-not-found", "Élève introuvable", "L'élève demandé est introuvable."));

        List<CenterGroupEnrollment> activeEnrollments =
                enrollmentRepository.findByStudentIdAndStatus(studentId, CenterGroupEnrollmentStatus.ACTIVE);
        Set<UUID> studentActiveGroupIds = activeEnrollments.stream()
                .map(CenterGroupEnrollment::getGroupId)
                .collect(Collectors.toSet());
        tutorScopeGuard.assertCanAccessStudentInGroups(studentActiveGroupIds);

        StudentCenterSummary centerSummary =
                directoryQueryService.getStudentCenterSummary(studentId, fromDate, toDate);
        List<CenterStudentPack> packs = tutorScopeGuard.isRestrictedTutor()
                ? List.of()
                : studentPackRepository.findByStudentIdOrderByStartDateDesc(studentId);

        StudentPeriodGradeSummary gradeSummary = null;
        StudentAttendanceSummary schoolAttendanceSummary = null;
        if (evaluationPeriodId != null) {
            gradeSummary = gradeSummaryPort
                    .findStudentPeriodSummary(studentId, evaluationPeriodId)
                    .orElse(null);
            schoolAttendanceSummary = attendanceSummaryPort.getStudentPeriodSummary(studentId, evaluationPeriodId);
        } else if (academicYearId != null) {
            schoolAttendanceSummary = attendanceSummaryPort.getStudentSummary(
                    studentId, academicYearId, null, fromDate, toDate);
        }

        return new StudentCenterOverview(
                student,
                centerSummary,
                packs,
                gradeSummary,
                schoolAttendanceSummary);
    }

    @PreAuthorize(CenterRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public TutorWorkloadOverview getTutorWorkload(UUID teacherId, LocalDate fromDate, LocalDate toDate) {
        subscriptionGuard.requireReadAccess();
        validateDateRange(fromDate, toDate);
        TeacherSummary teacher = teacherDirectoryPort.findTeacherById(teacherId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-teacher-not-found", "Intervenant introuvable", "L'intervenant demandé est introuvable."));
        tutorScopeGuard.assertCanAccessTeacher(teacher.id());

        TutorCenterWorkloadSummary workload =
                directoryQueryService.getTutorWorkloadSummary(teacherId, fromDate, toDate);
        return new TutorWorkloadOverview(teacher, workload);
    }

    private static void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new CenterValidationException(
                    "center-date-range-invalid",
                    "Plage de dates invalide",
                    "La date de fin doit être postérieure ou égale à la date de début.");
        }
    }

    public record StudentCenterOverview(
            StudentSummary student,
            StudentCenterSummary centerSummary,
            List<CenterStudentPack> packs,
            StudentPeriodGradeSummary periodGradeSummary,
            StudentAttendanceSummary schoolAttendanceSummary) {
    }

    public record TutorWorkloadOverview(
            TeacherSummary teacher,
            TutorCenterWorkloadSummary workload) {
    }
}
