package com.ecole.saas.academic.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.academic.ClassRoomSummary;
import com.ecole.saas.academic.ClassSubjectSummary;
import com.ecole.saas.academic.EvaluationPeriodSummary;
import com.ecole.saas.academic.GradeLevelSummary;
import com.ecole.saas.academic.SubjectSummary;
import com.ecole.saas.academic.domain.AcademicYear;
import com.ecole.saas.academic.domain.ClassRoom;
import com.ecole.saas.academic.domain.ClassSubject;
import com.ecole.saas.academic.domain.EvaluationPeriod;
import com.ecole.saas.academic.domain.GradeLevel;
import com.ecole.saas.academic.domain.Subject;
import com.ecole.saas.academic.infrastructure.AcademicYearRepository;
import com.ecole.saas.academic.infrastructure.ClassRoomRepository;
import com.ecole.saas.academic.infrastructure.ClassSubjectRepository;
import com.ecole.saas.academic.infrastructure.EvaluationPeriodRepository;
import com.ecole.saas.academic.infrastructure.GradeLevelRepository;
import com.ecole.saas.academic.infrastructure.SubjectRepository;
import com.ecole.saas.shared.domain.TenantContext;
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
 * Implémentation de la frontière publique {@link AcademicStructurePort} consommée par les autres
 * modules applicatifs dans le contexte tenant courant.
 */
@Service
public class AcademicStructureQueryService implements AcademicStructurePort {

    private final AcademicYearRepository academicYearRepository;
    private final EvaluationPeriodRepository evaluationPeriodRepository;
    private final GradeLevelRepository gradeLevelRepository;
    private final SubjectRepository subjectRepository;
    private final ClassRoomRepository classRoomRepository;
    private final ClassSubjectRepository classSubjectRepository;

    public AcademicStructureQueryService(
            AcademicYearRepository academicYearRepository,
            EvaluationPeriodRepository evaluationPeriodRepository,
            GradeLevelRepository gradeLevelRepository,
            SubjectRepository subjectRepository,
            ClassRoomRepository classRoomRepository,
            ClassSubjectRepository classSubjectRepository) {
        this.academicYearRepository = academicYearRepository;
        this.evaluationPeriodRepository = evaluationPeriodRepository;
        this.gradeLevelRepository = gradeLevelRepository;
        this.subjectRepository = subjectRepository;
        this.classRoomRepository = classRoomRepository;
        this.classSubjectRepository = classSubjectRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AcademicYearSummary> findCurrentAcademicYear() {
        TenantContext.requireCurrent();
        return academicYearRepository.findByCurrentTrue().map(AcademicStructureQueryService::toYearSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AcademicYearSummary> findAcademicYearById(UUID academicYearId) {
        TenantContext.requireCurrent();
        if (academicYearId == null) {
            return Optional.empty();
        }
        return academicYearRepository.findById(academicYearId).map(AcademicStructureQueryService::toYearSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AcademicYearSummary> findAcademicYearsByIds(Collection<UUID> academicYearIds) {
        TenantContext.requireCurrent();
        Set<UUID> validIds = sanitizeIds(academicYearIds);
        if (validIds.isEmpty()) {
            return Map.of();
        }
        return academicYearRepository.findAllById(validIds).stream()
                .collect(Collectors.toMap(AcademicYear::getId, AcademicStructureQueryService::toYearSummary, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvaluationPeriodSummary> findEvaluationPeriodsByYear(UUID academicYearId) {
        TenantContext.requireCurrent();
        if (academicYearId == null || academicYearRepository.findById(academicYearId).isEmpty()) {
            return List.of();
        }
        return evaluationPeriodRepository.findByAcademicYearIdOrderBySequenceOrderAsc(academicYearId).stream()
                .map(AcademicStructureQueryService::toPeriodSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvaluationPeriodSummary> findEvaluationPeriodById(UUID periodId) {
        TenantContext.requireCurrent();
        if (periodId == null) {
            return Optional.empty();
        }
        return evaluationPeriodRepository.findById(periodId).map(AcademicStructureQueryService::toPeriodSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, EvaluationPeriodSummary> findEvaluationPeriodsByIds(Collection<UUID> periodIds) {
        TenantContext.requireCurrent();
        Set<UUID> validIds = sanitizeIds(periodIds);
        if (validIds.isEmpty()) {
            return Map.of();
        }
        return evaluationPeriodRepository.findAllById(validIds).stream()
                .collect(Collectors.toMap(
                        EvaluationPeriod::getId, AcademicStructureQueryService::toPeriodSummary, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GradeLevelSummary> findGradeLevelById(UUID gradeLevelId) {
        TenantContext.requireCurrent();
        if (gradeLevelId == null) {
            return Optional.empty();
        }
        return gradeLevelRepository.findById(gradeLevelId).map(AcademicStructureQueryService::toLevelSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, GradeLevelSummary> findGradeLevelsByIds(Collection<UUID> gradeLevelIds) {
        TenantContext.requireCurrent();
        Set<UUID> validIds = sanitizeIds(gradeLevelIds);
        if (validIds.isEmpty()) {
            return Map.of();
        }
        return gradeLevelRepository.findAllById(validIds).stream()
                .collect(Collectors.toMap(
                        GradeLevel::getId, AcademicStructureQueryService::toLevelSummary, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SubjectSummary> findSubjectById(UUID subjectId) {
        TenantContext.requireCurrent();
        if (subjectId == null) {
            return Optional.empty();
        }
        return subjectRepository.findById(subjectId).map(AcademicStructureQueryService::toSubjectSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SubjectSummary> findSubjectsByIds(Collection<UUID> subjectIds) {
        TenantContext.requireCurrent();
        Set<UUID> validIds = sanitizeIds(subjectIds);
        if (validIds.isEmpty()) {
            return Map.of();
        }
        return subjectRepository.findAllById(validIds).stream()
                .collect(Collectors.toMap(
                        Subject::getId, AcademicStructureQueryService::toSubjectSummary, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClassRoomSummary> findClassRoomById(UUID classRoomId) {
        TenantContext.requireCurrent();
        if (classRoomId == null) {
            return Optional.empty();
        }
        return classRoomRepository.findById(classRoomId).map(AcademicStructureQueryService::toClassRoomSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ClassRoomSummary> findClassRoomsByIds(Collection<UUID> classRoomIds) {
        TenantContext.requireCurrent();
        Set<UUID> validIds = sanitizeIds(classRoomIds);
        if (validIds.isEmpty()) {
            return Map.of();
        }
        return classRoomRepository.findAllById(validIds).stream()
                .collect(Collectors.toMap(
                        ClassRoom::getId, AcademicStructureQueryService::toClassRoomSummary, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassSubjectSummary> findActiveSubjectsForClass(UUID classRoomId) {
        TenantContext.requireCurrent();
        if (classRoomId == null || classRoomRepository.findById(classRoomId).isEmpty()) {
            return List.of();
        }
        List<ClassSubject> assignments =
                classSubjectRepository.findByClassRoomIdAndActiveTrueOrderByCreatedAtAsc(classRoomId);
        if (assignments.isEmpty()) {
            return List.of();
        }
        Set<UUID> subjectIds = assignments.stream().map(ClassSubject::getSubjectId).collect(Collectors.toSet());
        Map<UUID, Subject> subjectsById = subjectRepository.findAllById(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity()));

        return assignments.stream()
                .map(cs -> {
                    Subject subject = subjectsById.get(cs.getSubjectId());
                    return new ClassSubjectSummary(
                            cs.getId(),
                            cs.getClassRoomId(),
                            cs.getSubjectId(),
                            subject != null ? subject.getCode() : null,
                            subject != null ? subject.getName() : null,
                            cs.getCoefficient(),
                            cs.getWeeklyHours(),
                            cs.isMandatory(),
                            cs.isActive());
                })
                .toList();
    }

    private static Set<UUID> sanitizeIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static AcademicYearSummary toYearSummary(AcademicYear year) {
        return new AcademicYearSummary(
                year.getId(),
                year.getCode(),
                year.getLabel(),
                year.getStartDate(),
                year.getEndDate(),
                year.getPeriodSystem().name(),
                year.getStatus().name(),
                year.isCurrent());
    }

    private static EvaluationPeriodSummary toPeriodSummary(EvaluationPeriod period) {
        return new EvaluationPeriodSummary(
                period.getId(),
                period.getAcademicYearId(),
                period.getCode(),
                period.getName(),
                period.getPeriodType().name(),
                period.getSequenceOrder(),
                period.getStartDate(),
                period.getEndDate(),
                period.getWeight(),
                period.getStatus().name());
    }

    private static GradeLevelSummary toLevelSummary(GradeLevel level) {
        return new GradeLevelSummary(
                level.getId(),
                level.getCode(),
                level.getName(),
                level.getCycle().name(),
                level.getDisplayOrder(),
                level.isActive());
    }

    private static SubjectSummary toSubjectSummary(Subject subject) {
        return new SubjectSummary(
                subject.getId(),
                subject.getCode(),
                subject.getName(),
                subject.getShortName(),
                subject.getCategory().name(),
                subject.getDefaultCoefficient(),
                subject.isActive());
    }

    private static ClassRoomSummary toClassRoomSummary(ClassRoom room) {
        return new ClassRoomSummary(
                room.getId(),
                room.getAcademicYearId(),
                room.getGradeLevelId(),
                room.getCode(),
                room.getName(),
                room.getSeries(),
                room.getRoomName(),
                room.getCapacity(),
                room.getStatus().name());
    }
}
