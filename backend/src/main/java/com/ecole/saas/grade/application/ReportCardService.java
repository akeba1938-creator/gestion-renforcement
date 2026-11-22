package com.ecole.saas.grade.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.ClassRoomSummary;
import com.ecole.saas.academic.ClassSubjectSummary;
import com.ecole.saas.academic.EvaluationPeriodSummary;
import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.grade.domain.CouncilDecision;
import com.ecole.saas.grade.domain.Evaluation;
import com.ecole.saas.grade.domain.EvaluationPeriodClosedException;
import com.ecole.saas.grade.domain.EvaluationStatus;
import com.ecole.saas.grade.domain.GradeConflictException;
import com.ecole.saas.grade.domain.GradeNotFoundException;
import com.ecole.saas.grade.domain.GradeValidationException;
import com.ecole.saas.grade.domain.GradingCalculator;
import com.ecole.saas.grade.domain.GradingPolicy;
import com.ecole.saas.grade.domain.Mention;
import com.ecole.saas.grade.domain.MentionPolicy;
import com.ecole.saas.grade.domain.PeriodReportCard;
import com.ecole.saas.grade.domain.PeriodSubjectResult;
import com.ecole.saas.grade.domain.RankingCalculator;
import com.ecole.saas.grade.domain.ReportCardStatus;
import com.ecole.saas.grade.domain.StudentGrade;
import com.ecole.saas.grade.infrastructure.EvaluationRepository;
import com.ecole.saas.grade.infrastructure.PeriodReportCardRepository;
import com.ecole.saas.grade.infrastructure.PeriodSubjectResultRepository;
import com.ecole.saas.grade.infrastructure.StudentGradeRepository;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentEnrollmentSummary;
import com.ecole.saas.student.StudentSummary;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Service applicatif de calcul, d'appréciation, de validation et de verrouillage des bulletins
 * de période (moyennes par matière, moyennes générales pondérées par coefficient et rangs ex æquo).
 */
@Service
public class ReportCardService {

    public record ReportCardView(
            PeriodReportCard reportCard,
            String studentMatricule,
            String studentFirstName,
            String studentLastName,
            String classRoomCode,
            String classRoomName,
            String periodCode,
            String periodName,
            List<PeriodSubjectResult> subjectResults) {
    }

    private final PeriodReportCardRepository reportCardRepository;
    private final PeriodSubjectResultRepository subjectResultRepository;
    private final EvaluationRepository evaluationRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final AcademicStructurePort academicStructurePort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final GradeSchoolContext schoolContext;
    private final GradeSubscriptionGuard subscriptionGuard;
    private final GradeTeacherScopeGuard teacherScopeGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public ReportCardService(
            PeriodReportCardRepository reportCardRepository,
            PeriodSubjectResultRepository subjectResultRepository,
            EvaluationRepository evaluationRepository,
            StudentGradeRepository studentGradeRepository,
            AcademicStructurePort academicStructurePort,
            StudentDirectoryPort studentDirectoryPort,
            GradeSchoolContext schoolContext,
            GradeSubscriptionGuard subscriptionGuard,
            GradeTeacherScopeGuard teacherScopeGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.reportCardRepository = reportCardRepository;
        this.subjectResultRepository = subjectResultRepository;
        this.evaluationRepository = evaluationRepository;
        this.studentGradeRepository = studentGradeRepository;
        this.academicStructurePort = academicStructurePort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.schoolContext = schoolContext;
        this.subscriptionGuard = subscriptionGuard;
        this.teacherScopeGuard = teacherScopeGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(GradeRoles.REPORT_CARD_COMPUTE_ROLES)
    @Transactional
    public List<ReportCardView> computeClassReportCards(UUID classRoomId, UUID evaluationPeriodId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        ClassRoomSummary classRoom = academicStructurePort.findClassRoomById(classRoomId)
                .orElseThrow(() -> new GradeNotFoundException(
                        "classroom-not-found", "Classe introuvable", "La classe demandée est introuvable."));
        EvaluationPeriodSummary period = academicStructurePort.findEvaluationPeriodById(evaluationPeriodId)
                .orElseThrow(() -> new GradeNotFoundException(
                        "evaluation-period-not-found",
                        "Période d'évaluation introuvable",
                        "La période d'évaluation demandée est introuvable."));
        if (!classRoom.academicYearId().equals(period.academicYearId())) {
            throw new GradeValidationException(
                    "La classe sélectionnée n'appartient pas à l'année scolaire de la période d'évaluation.");
        }
        if ("PLANNED".equals(period.status()) || "LOCKED".equals(period.status())) {
            throw new EvaluationPeriodClosedException(
                    "Impossible de calculer les bulletins lorsque la période est au statut " + period.status() + ".");
        }

        List<PeriodReportCard> existingCards = reportCardRepository
                .findByEvaluationPeriodIdAndClassRoomIdForUpdate(evaluationPeriodId, classRoomId);
        for (PeriodReportCard existing : existingCards) {
            if (existing.getStatus() != ReportCardStatus.DRAFT) {
                throw new GradeConflictException(
                        "report-card-already-finalized",
                        "Bulletins déjà validés ou verrouillés",
                        "Impossible de recalculer la classe car au moins un bulletin est déjà " + existing.getStatus() + ".");
            }
        }

        SchoolProfileSnapshot school = schoolContext.requireSchoolProfile();
        int gradingScale = school.gradingScale();
        GradingPolicy policy = GradingPolicy.defaultSenegalPolicy();

        List<StudentEnrollmentSummary> enrollments =
                studentDirectoryPort.findActiveEnrollmentsByClassRoom(classRoomId);
        List<ClassSubjectSummary> classSubjects = academicStructurePort.findActiveSubjectsForClass(classRoomId);
        List<Evaluation> evaluations = evaluationRepository
                .findByEvaluationPeriodIdAndClassRoomIdAndStatusNotOrderByEvaluationDateAscCreatedAtAsc(
                        evaluationPeriodId, classRoomId, EvaluationStatus.CANCELLED);

        List<UUID> evaluationIds = evaluations.stream().map(Evaluation::getId).toList();
        Map<UUID, Evaluation> evaluationsById = evaluations.stream()
                .collect(Collectors.toMap(Evaluation::getId, Function.identity()));
        Map<UUID, Map<UUID, List<StudentGrade>>> gradesByStudentAndSubject = new HashMap<>();
        if (!evaluationIds.isEmpty()) {
            for (StudentGrade grade : studentGradeRepository.findByEvaluationIdIn(evaluationIds)) {
                Evaluation eval = evaluationsById.get(grade.getEvaluationId());
                if (eval == null) {
                    continue;
                }
                gradesByStudentAndSubject
                        .computeIfAbsent(grade.getStudentId(), k -> new HashMap<>())
                        .computeIfAbsent(eval.getSubjectId(), k -> new ArrayList<>())
                        .add(grade);
            }
        }

        // 1. Calcul des moyennes par élève et par matière
        Map<UUID, Map<UUID, BigDecimal>> subjectAveragesByStudent = new HashMap<>();
        Map<UUID, Map<UUID, Integer>> subjectEvalCountsByStudent = new HashMap<>();
        Map<UUID, Map<UUID, BigDecimal>> averagesBySubjectThenStudent = new HashMap<>();

        for (StudentEnrollmentSummary enrollment : enrollments) {
            UUID studentId = enrollment.studentId();
            Map<UUID, BigDecimal> perSubjectAvg = new HashMap<>();
            Map<UUID, Integer> perSubjectCount = new HashMap<>();

            for (ClassSubjectSummary cs : classSubjects) {
                List<StudentGrade> studentSubjectGrades = gradesByStudentAndSubject
                        .getOrDefault(studentId, Map.of())
                        .getOrDefault(cs.subjectId(), List.of());

                List<GradingCalculator.EvaluationScoreInput> inputs = studentSubjectGrades.stream()
                        .map(g -> {
                            Evaluation eval = evaluationsById.get(g.getEvaluationId());
                            return new GradingCalculator.EvaluationScoreInput(
                                    g.getAttendanceStatus(),
                                    g.getScore(),
                                    eval.getMaxScore(),
                                    eval.getWeight());
                        })
                        .toList();

                BigDecimal avg = GradingCalculator.computeSubjectAverage(inputs, gradingScale, policy);
                perSubjectAvg.put(cs.subjectId(), avg);
                perSubjectCount.put(cs.subjectId(), studentSubjectGrades.size());
                averagesBySubjectThenStudent
                        .computeIfAbsent(cs.subjectId(), k -> new HashMap<>())
                        .put(studentId, avg);
            }

            subjectAveragesByStudent.put(studentId, perSubjectAvg);
            subjectEvalCountsByStudent.put(studentId, perSubjectCount);
        }

        // 2. Statistiques et rangs par matière
        Map<UUID, BigDecimal> classAvgBySubject = new HashMap<>();
        Map<UUID, Map<UUID, RankingCalculator.RankEntry>> subjectRanksBySubject = new HashMap<>();
        for (ClassSubjectSummary cs : classSubjects) {
            Map<UUID, BigDecimal> scoresForSubject = averagesBySubjectThenStudent.getOrDefault(cs.subjectId(), Map.of());
            GradingCalculator.ClassAverageStats stats =
                    GradingCalculator.computeClassStats(scoresForSubject.values(), policy);
            classAvgBySubject.put(cs.subjectId(), stats.classAverage());
            subjectRanksBySubject.put(cs.subjectId(), RankingCalculator.rankDescending(scoresForSubject));
        }

        // 3. Calcul des moyennes générales pondérées par coefficient et classement de la classe
        Map<UUID, GradingCalculator.GeneralAverageResult> generalByStudent = new HashMap<>();
        Map<UUID, BigDecimal> generalAveragesMap = new HashMap<>();
        for (StudentEnrollmentSummary enrollment : enrollments) {
            UUID studentId = enrollment.studentId();
            Map<UUID, BigDecimal> perSubjectAvg = subjectAveragesByStudent.getOrDefault(studentId, Map.of());
            List<GradingCalculator.SubjectAverageInput> subjectInputs = classSubjects.stream()
                    .map(cs -> new GradingCalculator.SubjectAverageInput(
                            cs.coefficient(), perSubjectAvg.get(cs.subjectId())))
                    .toList();
            GradingCalculator.GeneralAverageResult general =
                    GradingCalculator.computeGeneralAverage(subjectInputs, policy);
            generalByStudent.put(studentId, general);
            generalAveragesMap.put(studentId, general.generalAverage());
        }

        GradingCalculator.ClassAverageStats classStats =
                GradingCalculator.computeClassStats(generalAveragesMap.values(), policy);
        Map<UUID, RankingCalculator.RankEntry> classRanks = RankingCalculator.rankDescending(generalAveragesMap);

        // 4. Persistance en lot des bulletins et résultats matière (en préservant les appréciations sur DRAFT)
        Map<UUID, PeriodReportCard> existingByStudent = new HashMap<>();
        for (PeriodReportCard existing : existingCards) {
            existingByStudent.put(existing.getStudentId(), existing);
        }
        Set<UUID> missingStudentIds = enrollments.stream()
                .map(StudentEnrollmentSummary::studentId)
                .filter(id -> !existingByStudent.containsKey(id))
                .collect(Collectors.toSet());
        if (!missingStudentIds.isEmpty()) {
            for (PeriodReportCard existing : reportCardRepository.findByEvaluationPeriodIdAndStudentIdIn(
                    evaluationPeriodId, missingStudentIds)) {
                existingByStudent.putIfAbsent(existing.getStudentId(), existing);
            }
        }

        Set<UUID> existingCardIds = enrollments.stream()
                .map(e -> existingByStudent.get(e.studentId()))
                .filter(Objects::nonNull)
                .map(PeriodReportCard::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Instant now = clock.instant();
        int classSize = enrollments.size();

        List<PeriodReportCard> cardsToSave = new ArrayList<>(enrollments.size());
        for (StudentEnrollmentSummary enrollment : enrollments) {
            UUID studentId = enrollment.studentId();
            GradingCalculator.GeneralAverageResult general = generalByStudent.get(studentId);
            RankingCalculator.RankEntry rank =
                    classRanks.getOrDefault(studentId, new RankingCalculator.RankEntry(null, false));
            Mention mention = MentionPolicy.resolve(general.generalAverage(), gradingScale);

            PeriodReportCard card = existingByStudent.get(studentId);
            if (card == null) {
                card = new PeriodReportCard(
                        period.academicYearId(),
                        evaluationPeriodId,
                        classRoomId,
                        studentId,
                        gradingScale,
                        now);
            }
            card.applyComputedResults(
                    classRoomId,
                    gradingScale,
                    general.generalAverage(),
                    general.totalWeightedScore(),
                    general.totalCoefficients(),
                    classStats.classAverage(),
                    classStats.minAverage(),
                    classStats.maxAverage(),
                    rank.rank(),
                    classSize,
                    rank.tied(),
                    mention,
                    now);
            cardsToSave.add(card);
        }
        List<PeriodReportCard> savedCards = cardsToSave.isEmpty()
                ? List.of()
                : reportCardRepository.saveAll(cardsToSave);

        Map<UUID, Map<UUID, PeriodSubjectResult>> existingSubjectsByCard = new HashMap<>();
        if (!existingCardIds.isEmpty()) {
            for (PeriodSubjectResult result : subjectResultRepository.findByReportCardIdIn(existingCardIds)) {
                existingSubjectsByCard
                        .computeIfAbsent(result.getReportCardId(), ignored -> new HashMap<>())
                        .put(result.getSubjectId(), result);
            }
        }

        Set<UUID> activeSubjectIds = classSubjects.stream()
                .map(ClassSubjectSummary::subjectId)
                .collect(Collectors.toSet());
        List<PeriodSubjectResult> obsoleteSubjects = new ArrayList<>();
        List<PeriodSubjectResult> subjectResultsToSave = new ArrayList<>(savedCards.size() * classSubjects.size());

        for (PeriodReportCard savedCard : savedCards) {
            UUID studentId = savedCard.getStudentId();
            Map<UUID, PeriodSubjectResult> existingSubjects =
                    existingSubjectsByCard.getOrDefault(savedCard.getId(), Map.of());

            for (PeriodSubjectResult obsolete : existingSubjects.values()) {
                if (!activeSubjectIds.contains(obsolete.getSubjectId())) {
                    obsoleteSubjects.add(obsolete);
                }
            }

            for (ClassSubjectSummary cs : classSubjects) {
                BigDecimal avg = subjectAveragesByStudent.getOrDefault(studentId, Map.of()).get(cs.subjectId());
                int evalCount = subjectEvalCountsByStudent.getOrDefault(studentId, Map.of())
                        .getOrDefault(cs.subjectId(), 0);
                BigDecimal classSubjectAvg = classAvgBySubject.get(cs.subjectId());
                Integer rankInSubject = subjectRanksBySubject
                        .getOrDefault(cs.subjectId(), Map.of())
                        .getOrDefault(studentId, new RankingCalculator.RankEntry(null, false))
                        .rank();

                PeriodSubjectResult existingSubject = existingSubjects.get(cs.subjectId());
                if (existingSubject != null) {
                    existingSubject.updateComputedResult(
                            cs.id(),
                            cs.subjectCode(),
                            cs.subjectName(),
                            cs.coefficient(),
                            avg,
                            classSubjectAvg,
                            rankInSubject,
                            evalCount);
                    subjectResultsToSave.add(existingSubject);
                } else {
                    subjectResultsToSave.add(new PeriodSubjectResult(
                            savedCard.getId(),
                            cs.subjectId(),
                            cs.id(),
                            cs.subjectCode(),
                            cs.subjectName(),
                            cs.coefficient(),
                            avg,
                            classSubjectAvg,
                            rankInSubject,
                            evalCount));
                }
            }
        }

        if (!obsoleteSubjects.isEmpty()) {
            subjectResultRepository.deleteAll(obsoleteSubjects);
        }
        if (!subjectResultsToSave.isEmpty()) {
            subjectResultRepository.saveAll(subjectResultsToSave);
        }

        List<PeriodReportCard> orderedCards = reportCardRepository
                .findByEvaluationPeriodIdAndClassRoomIdOrderByRankInClassAscStudentIdAsc(
                        evaluationPeriodId, classRoomId);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.GRADE,
                AuditAction.REPORT_CARD_GENERATED,
                "CLASS_ROOM",
                classRoomId,
                "Calcul et génération des bulletins de période d'une classe.",
                Map.of(
                        "evaluationPeriodId", evaluationPeriodId.toString(),
                        "reportCardCount", orderedCards.size())));
        return enrichReportCards(orderedCards);
    }

    @PreAuthorize(GradeRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<ReportCardView> listReportCards(
            UUID academicYearId,
            UUID evaluationPeriodId,
            UUID classRoomId,
            UUID studentId,
            ReportCardStatus status,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Set<UUID> allowedClasses = null;
        if (teacherScopeGuard.isRestrictedTeacher()) {
            if (classRoomId != null) {
                teacherScopeGuard.assertCanAccessClass(classRoomId);
            }
            allowedClasses = teacherScopeGuard.currentTeacherClassRoomIds();
            if (allowedClasses.isEmpty()) {
                return Page.empty(pageable);
            }
        }
        Set<UUID> scopedClasses = allowedClasses;

        Specification<PeriodReportCard> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (scopedClasses != null) {
                predicates.add(root.get("classRoomId").in(scopedClasses));
            }
            if (academicYearId != null) {
                predicates.add(cb.equal(root.get("academicYearId"), academicYearId));
            }
            if (evaluationPeriodId != null) {
                predicates.add(cb.equal(root.get("evaluationPeriodId"), evaluationPeriodId));
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
        Page<PeriodReportCard> page = reportCardRepository.findAll(spec, pageable);
        Map<UUID, ReportCardView> enriched = enrichReportCards(page.getContent()).stream()
                .collect(Collectors.toMap(v -> v.reportCard().getId(), Function.identity()));
        return page.map(c -> enriched.get(c.getId()));
    }

    @PreAuthorize(GradeRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public ReportCardView getReportCard(UUID reportCardId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        PeriodReportCard card = requireReportCard(reportCardId);
        teacherScopeGuard.assertCanAccessClass(card.getClassRoomId());
        return enrichReportCards(List.of(card)).getFirst();
    }

    @PreAuthorize(GradeRoles.APPRECIATION_WRITE_ROLES)
    @Transactional
    public ReportCardView updateSubjectAppreciation(
            UUID reportCardId,
            UUID subjectId,
            String teacherAppreciation,
            UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        PeriodReportCard card = requireReportCardForUpdate(reportCardId);
        assertPeriodNotLocked(card.getEvaluationPeriodId());
        card.assertDraft();
        teacherScopeGuard.assertCanAccessClassSubject(card.getClassRoomId(), subjectId);

        PeriodSubjectResult result = subjectResultRepository
                .findByReportCardIdAndSubjectId(reportCardId, subjectId)
                .orElseThrow(() -> new GradeNotFoundException(
                        "report-card-subject-not-found",
                        "Matière absente du bulletin",
                        "Cette matière ne figure pas sur ce bulletin de période."));
        result.updateTeacherAppreciation(teacherAppreciation, actorId, clock.instant());
        subjectResultRepository.save(result);
        return enrichReportCards(List.of(card)).getFirst();
    }

    @PreAuthorize(GradeRoles.APPRECIATION_WRITE_ROLES)
    @Transactional
    public ReportCardView updateReportCardAppreciations(
            UUID reportCardId,
            String homeroomAppreciation,
            String directorAppreciation,
            CouncilDecision councilDecision) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        PeriodReportCard card = requireReportCardForUpdate(reportCardId);
        assertPeriodNotLocked(card.getEvaluationPeriodId());
        card.assertNotLocked();

        if (homeroomAppreciation != null) {
            teacherScopeGuard.assertCanWriteHomeroomAppreciation(card.getClassRoomId());
            card.updateHomeroomAppreciation(homeroomAppreciation);
        }
        if (directorAppreciation != null || councilDecision != null) {
            teacherScopeGuard.assertCanWriteDirectorAppreciation();
            card.updateDirectorAppreciationAndDecision(directorAppreciation, councilDecision);
        }

        PeriodReportCard saved = reportCardRepository.save(card);
        return enrichReportCards(List.of(saved)).getFirst();
    }

    @PreAuthorize(GradeRoles.REPORT_CARD_VALIDATE_ROLES)
    @Transactional
    public ReportCardView validateReportCard(UUID reportCardId, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        PeriodReportCard card = requireReportCardForUpdate(reportCardId);
        assertPeriodNotLocked(card.getEvaluationPeriodId());
        card.validate(actorId, clock.instant());
        PeriodReportCard saved = reportCardRepository.save(card);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.GRADE,
                AuditAction.REPORT_CARD_VALIDATED,
                "PERIOD_REPORT_CARD",
                saved.getId(),
                "Validation d'un bulletin de période.",
                Map.of(
                        "studentId", saved.getStudentId().toString(),
                        "evaluationPeriodId", saved.getEvaluationPeriodId().toString())));
        return enrichReportCards(List.of(saved)).getFirst();
    }

    @PreAuthorize(GradeRoles.REPORT_CARD_LOCK_ROLES)
    @Transactional
    public ReportCardView lockReportCard(UUID reportCardId, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        PeriodReportCard card = requireReportCardForUpdate(reportCardId);
        card.lock(actorId, clock.instant());
        PeriodReportCard saved = reportCardRepository.save(card);
        auditLogPort.recordTenantEvent(AuditRecordCommand.critical(
                AuditCategory.GRADE,
                AuditAction.REPORT_CARD_LOCKED,
                AuditOutcome.SUCCESS,
                "PERIOD_REPORT_CARD",
                saved.getId(),
                "Verrouillage définitif d'un bulletin de période.",
                Map.of(
                        "studentId", saved.getStudentId().toString(),
                        "evaluationPeriodId", saved.getEvaluationPeriodId().toString())));
        return enrichReportCards(List.of(saved)).getFirst();
    }

    private void assertPeriodNotLocked(UUID evaluationPeriodId) {
        EvaluationPeriodSummary period = academicStructurePort.findEvaluationPeriodById(evaluationPeriodId)
                .orElseThrow(() -> new GradeNotFoundException(
                        "evaluation-period-not-found",
                        "Période d'évaluation introuvable",
                        "La période d'évaluation demandée est introuvable."));
        if ("LOCKED".equals(period.status())) {
            throw new EvaluationPeriodClosedException(
                    "La période d'évaluation est verrouillée (LOCKED) : les bulletins ne peuvent plus être modifiés.");
        }
    }

    private PeriodReportCard requireReportCard(UUID reportCardId) {
        return reportCardRepository.findById(reportCardId)
                .orElseThrow(() -> new GradeNotFoundException(
                        "report-card-not-found", "Bulletin introuvable", "Le bulletin de période demandé est introuvable."));
    }

    private PeriodReportCard requireReportCardForUpdate(UUID reportCardId) {
        return reportCardRepository.findByIdForUpdate(reportCardId)
                .orElseThrow(() -> new GradeNotFoundException(
                        "report-card-not-found", "Bulletin introuvable", "Le bulletin de période demandé est introuvable."));
    }

    private List<ReportCardView> enrichReportCards(List<PeriodReportCard> cards) {
        if (cards.isEmpty()) {
            return List.of();
        }
        Set<UUID> cardIds = cards.stream().map(PeriodReportCard::getId).collect(Collectors.toSet());
        Map<UUID, List<PeriodSubjectResult>> subjectsByCard = subjectResultRepository.findByReportCardIdIn(cardIds)
                .stream()
                .collect(Collectors.groupingBy(PeriodSubjectResult::getReportCardId));
        for (List<PeriodSubjectResult> list : subjectsByCard.values()) {
            list.sort((a, b) -> a.getSubjectName().compareToIgnoreCase(b.getSubjectName()));
        }

        Set<UUID> studentIds = cards.stream().map(PeriodReportCard::getStudentId).collect(Collectors.toSet());
        Map<UUID, StudentSummary> studentsById = studentDirectoryPort.findStudentsByIds(studentIds).stream()
                .collect(Collectors.toMap(StudentSummary::id, Function.identity()));

        Set<UUID> classRoomIds = cards.stream()
                .map(PeriodReportCard::getClassRoomId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> periodIds = cards.stream()
                .map(PeriodReportCard::getEvaluationPeriodId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, ClassRoomSummary> roomsById = academicStructurePort.findClassRoomsByIds(classRoomIds);
        Map<UUID, EvaluationPeriodSummary> periodsById = academicStructurePort.findEvaluationPeriodsByIds(periodIds);

        return cards.stream()
                .map(card -> {
                    StudentSummary student = studentsById.get(card.getStudentId());
                    ClassRoomSummary room = roomsById.get(card.getClassRoomId());
                    EvaluationPeriodSummary period = periodsById.get(card.getEvaluationPeriodId());
                    return new ReportCardView(
                            card,
                            student != null ? student.matricule() : null,
                            student != null ? student.firstName() : null,
                            student != null ? student.lastName() : null,
                            room != null ? room.code() : null,
                            room != null ? room.name() : null,
                            period != null ? period.code() : null,
                            period != null ? period.name() : null,
                            subjectsByCard.getOrDefault(card.getId(), List.of()));
                })
                .toList();
    }
}
