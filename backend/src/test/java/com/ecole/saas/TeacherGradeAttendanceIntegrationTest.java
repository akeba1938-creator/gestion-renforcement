package com.ecole.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.attendance.AttendanceSummaryPort;
import com.ecole.saas.attendance.StudentAttendanceSummary;
import com.ecole.saas.grade.ClassPeriodGradeStats;
import com.ecole.saas.grade.GradeSummaryPort;
import com.ecole.saas.grade.StudentPeriodGradeSummary;
import com.ecole.saas.identity.UserDirectoryPort;
import com.ecole.saas.identity.application.UserAccountService;
import com.ecole.saas.identity.domain.AccountRef;
import com.ecole.saas.identity.domain.RoleCode;
import com.ecole.saas.identity.domain.TokenIssuer;
import com.ecole.saas.identity.domain.User;
import com.ecole.saas.school.application.SchoolService;
import com.ecole.saas.school.domain.School;
import com.ecole.saas.school.domain.SchoolType;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import com.ecole.saas.subscription.application.SubscriptionService;
import com.ecole.saas.subscription.domain.BillingCycle;
import com.ecole.saas.subscription.domain.SubscriptionPlan;
import com.ecole.saas.subscription.domain.SubscriptionStatus;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class TeacherGradeAttendanceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final SchoolService schoolService;
    private final SubscriptionService subscriptionService;
    private final UserAccountService userAccountService;
    private final UserDirectoryPort userDirectoryPort;
    private final AcademicStructurePort academicStructurePort;
    private final TeacherDirectoryPort teacherDirectoryPort;
    private final GradeSummaryPort gradeSummaryPort;
    private final AttendanceSummaryPort attendanceSummaryPort;
    private final TokenIssuer tokenIssuer;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    TeacherGradeAttendanceIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            SchoolService schoolService,
            SubscriptionService subscriptionService,
            UserAccountService userAccountService,
            UserDirectoryPort userDirectoryPort,
            AcademicStructurePort academicStructurePort,
            TeacherDirectoryPort teacherDirectoryPort,
            GradeSummaryPort gradeSummaryPort,
            AttendanceSummaryPort attendanceSummaryPort,
            TokenIssuer tokenIssuer,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.schoolService = schoolService;
        this.subscriptionService = subscriptionService;
        this.userAccountService = userAccountService;
        this.userDirectoryPort = userDirectoryPort;
        this.academicStructurePort = academicStructurePort;
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.gradeSummaryPort = gradeSummaryPort;
        this.attendanceSummaryPort = attendanceSummaryPort;
        this.tokenIssuer = tokenIssuer;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldRunEndToEndTeacherGradeAttendanceFlowAndExposePublicPorts() throws Exception {
        School school = createSchool("TGA-FLOW", SchoolType.PRIVATE_SCHOOL);
        createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));
        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);
        String censorToken = tenantToken(school.getId(), RoleCode.CENSOR);
        String secretaryToken = tenantToken(school.getId(), RoleCode.SECRETARY);

        // 1. Structure académique : année, période T1 ouverte, niveau, 2 matières, 1 classe
        UUID yearId = createAcademicYearViaApi(directorToken, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(directorToken, yearId);
        UUID periodId = createAndOpenPeriodViaApi(directorToken, yearId, "T1", "Trimestre 1", 1, "2026-10-01", "2026-12-20");
        UUID levelId = createGradeLevelViaApi(directorToken, "3E", "Troisième", "MIDDLE_SCHOOL", 1);
        UUID mathId = createSubjectViaApi(directorToken, "MATH", "Mathématiques", "SCIENTIFIC", 4);
        UUID frenchId = createSubjectViaApi(directorToken, "FR", "Français", "LITERARY", 2);
        UUID class3eAId = createClassViaApi(directorToken, yearId, levelId, "3E-A", "3ème A", 40);
        assignSubjectToClassViaApi(directorToken, class3eAId, mathId, 4, 6);
        assignSubjectToClassViaApi(directorToken, class3eAId, frenchId, 2, 4);

        // 2. Compte utilisateur + dossier enseignant + spécialité + affectation
        User teacherUser = createTenantUser(school.getId(), "prof.math@ecole.sn", "Mamadou", "Ba", RoleCode.TEACHER);
        String teacherToken = tenantTokenForUser(school.getId(), teacherUser.getId(), RoleCode.TEACHER);

        UUID teacherId = createTeacherViaApi(
                directorToken, "ENS-001", teacherUser.getId(), "Mamadou", "Ba", "PERMANENT", "+221771112233", 8000L);
        mockMvc.perform(post("/api/v1/teachers/" + teacherId + "/specialties")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("subjectId", mathId, "primarySpecialty", true, "notes", "Certifié"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primarySpecialty").value(true));
        mockMvc.perform(post("/api/v1/teachers/" + teacherId + "/specialties")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("subjectId", frenchId, "primarySpecialty", false))))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/teachers/" + teacherId + "/specialties/" + frenchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isNoContent());

        UUID assignmentId = createAssignmentViaApi(directorToken, teacherId, yearId, class3eAId, mathId, true, 6);
        assertThat(assignmentId).isNotNull();

        // L'enseignant consulte son profil /me et ses affectations
        mockMvc.perform(get("/api/v1/teachers/me").header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value("ENS-001"));
        mockMvc.perform(get("/api/v1/teachers/me/assignments").header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].homeroomTeacher").value(true));

        // 3. Inscription de 4 élèves dans 3E-A (pour tester moyennes pondérées et ex æquo 1, 2, 2, 4)
        UUID stu1 = createAndEnrollStudent(secretaryToken, yearId, class3eAId, "Awa", "Diop", "FEMALE");
        UUID stu2 = createAndEnrollStudent(secretaryToken, yearId, class3eAId, "Moussa", "Fall", "MALE");
        UUID stu3 = createAndEnrollStudent(secretaryToken, yearId, class3eAId, "Fatou", "Sarr", "FEMALE");
        UUID stu4 = createAndEnrollStudent(secretaryToken, yearId, class3eAId, "Oumar", "Ndiaye", "MALE");

        // 4. Évaluations :
        //    - Math Interro sur 10 (poids 1, normalisée sur 20) créée par l'enseignant affecté
        //    - Math Compo sur 20 (poids 2) créée par l'enseignant affecté
        //    - Français Devoir sur 20 (poids 1) créé par le censeur
        UUID mathQuizId = createEvaluationViaApi(
                teacherToken, periodId, class3eAId, mathId, "Interro Algèbre", "QUIZ", "2026-10-15", "10.00", 1);
        UUID mathCompId = createEvaluationViaApi(
                teacherToken, periodId, class3eAId, mathId, "Composition T1", "COMPOSITION", "2026-11-20", "20.00", 2);
        UUID frenchTestId = createEvaluationViaApi(
                censorToken, periodId, class3eAId, frenchId, "Devoir Français", "TEST", "2026-11-10", "20.00", 1);

        // Saisie des notes :
        // stu1: Math quiz 9/10 (->18/20, p1), Math compo 16/20 (p2) => Math=(18+32)/3=16.67 ; Fr=15.00
        //       Générale = (16.67*4 + 15.00*2)/6 = (66.68+30)/6 = 16.11 (Rang 1, TRES_BIEN)
        // stu2: Math quiz 7/10 (->14/20, p1), Math compo 14/20 (p2) => Math=14.00 ; Fr=14.00
        //       Générale = 14.00 (Rang 2 ex æquo, BIEN)
        // stu3: Math quiz ABSENT_JUSTIFIED (neutre), Math compo 14/20 (p2) => Math=14.00 ; Fr=14.00
        //       Générale = 14.00 (Rang 2 ex æquo, BIEN)
        // stu4: Math quiz ABSENT_UNJUSTIFIED (=0, p1), Math compo 12/20 (p2) => Math=(0+24)/3=8.00 ; Fr=11.00
        //       Générale = (8.00*4 + 11.00*2)/6 = 54/6 = 9.00 (Rang 4, INSUFFISANT)
        upsertGradesViaApi(teacherToken, mathQuizId, List.of(
                Map.of("studentId", stu1, "attendanceStatus", "PRESENT", "score", 9.0),
                Map.of("studentId", stu2, "attendanceStatus", "PRESENT", "score", 7.0),
                Map.of("studentId", stu3, "attendanceStatus", "ABSENT_JUSTIFIED"),
                Map.of("studentId", stu4, "attendanceStatus", "ABSENT_UNJUSTIFIED")));

        upsertGradesViaApi(teacherToken, mathCompId, List.of(
                Map.of("studentId", stu1, "attendanceStatus", "PRESENT", "score", 16.0),
                Map.of("studentId", stu2, "attendanceStatus", "PRESENT", "score", 14.0),
                Map.of("studentId", stu3, "attendanceStatus", "PRESENT", "score", 14.0),
                Map.of("studentId", stu4, "attendanceStatus", "PRESENT", "score", 12.0)));

        upsertGradesViaApi(censorToken, frenchTestId, List.of(
                Map.of("studentId", stu1, "attendanceStatus", "PRESENT", "score", 15.0),
                Map.of("studentId", stu2, "attendanceStatus", "PRESENT", "score", 14.0),
                Map.of("studentId", stu3, "attendanceStatus", "PRESENT", "score", 14.0),
                Map.of("studentId", stu4, "attendanceStatus", "PRESENT", "score", 11.0)));

        // Note dépassant le barème de l'évaluation -> 400
        mockMvc.perform(put("/api/v1/grades/evaluations/" + mathQuizId + "/entries")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("entries", List.of(
                                Map.of("studentId", stu1, "attendanceStatus", "PRESENT", "score", 12.0))))))
                .andExpect(status().isBadRequest());

        // Validation de l'évaluation par le censeur
        mockMvc.perform(post("/api/v1/grades/evaluations/" + mathCompId + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(censorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"));

        // Calcul des bulletins de la classe 3E-A pour T1
        JsonNode computedCards = readJson(mockMvc.perform(post("/api/v1/grades/report-cards/compute")
                        .header(HttpHeaders.AUTHORIZATION, bearer(censorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("classRoomId", class3eAId, "evaluationPeriodId", periodId))))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(computedCards.size()).isEqualTo(4);
        JsonNode cardStu1 = findByStudentId(computedCards, stu1);
        JsonNode cardStu2 = findByStudentId(computedCards, stu2);
        JsonNode cardStu3 = findByStudentId(computedCards, stu3);
        JsonNode cardStu4 = findByStudentId(computedCards, stu4);

        assertThat(cardStu1.get("generalAverage").decimalValue()).isEqualByComparingTo("16.11");
        assertThat(cardStu1.get("rankInClass").asInt()).isEqualTo(1);
        assertThat(cardStu1.get("tiedRank").asBoolean()).isFalse();
        assertThat(cardStu1.get("mention").asString()).isEqualTo("TRES_BIEN");

        assertThat(cardStu2.get("generalAverage").decimalValue()).isEqualByComparingTo("14.00");
        assertThat(cardStu2.get("rankInClass").asInt()).isEqualTo(2);
        assertThat(cardStu2.get("tiedRank").asBoolean()).isTrue();
        assertThat(cardStu3.get("rankInClass").asInt()).isEqualTo(2);
        assertThat(cardStu3.get("tiedRank").asBoolean()).isTrue();

        assertThat(cardStu4.get("generalAverage").decimalValue()).isEqualByComparingTo("9.00");
        assertThat(cardStu4.get("rankInClass").asInt()).isEqualTo(4);
        assertThat(cardStu4.get("tiedRank").asBoolean()).isFalse();
        assertThat(cardStu4.get("mention").asString()).isEqualTo("INSUFFISANT");

        // Appréciations : matière par prof Math, prof principal par prof Math, direction + décision par directeur
        UUID cardStu1Id = UUID.fromString(cardStu1.get("id").asString());
        mockMvc.perform(put("/api/v1/grades/report-cards/" + cardStu1Id + "/subjects/" + mathId + "/appreciation")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("teacherAppreciation", "Excellent trimestre en mathématiques."))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/grades/report-cards/" + cardStu1Id + "/appreciations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("homeroomAppreciation", "Élève sérieuse et impliquée."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeroomAppreciation").value("Élève sérieuse et impliquée."));

        mockMvc.perform(put("/api/v1/grades/report-cards/" + cardStu1Id + "/appreciations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "directorAppreciation", "Félicitations du conseil de classe.",
                                "councilDecision", "FELICITATIONS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.councilDecision").value("FELICITATIONS"));

        // Validation puis verrouillage du bulletin de stu1
        mockMvc.perform(post("/api/v1/grades/report-cards/" + cardStu1Id + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(censorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"));
        mockMvc.perform(post("/api/v1/grades/report-cards/" + cardStu1Id + "/lock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        // Modification d'un bulletin verrouillé -> 409
        mockMvc.perform(put("/api/v1/grades/report-cards/" + cardStu1Id + "/appreciations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("directorAppreciation", "Tentative après verrou"))))
                .andExpect(status().isConflict());

        // 5. Module Attendance : 2 séances d'appel + justification d'une absence + synthèses
        UUID session1Id = createAttendanceSessionViaApi(
                teacherToken, class3eAId, periodId, mathId, "2026-10-16", "MORNING", "08:00", "10:00", true);
        JsonNode s1Updated = readJson(mockMvc.perform(put("/api/v1/attendance/sessions/" + session1Id + "/records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("records", List.of(
                                Map.of("studentId", stu1, "status", "PRESENT"),
                                Map.of("studentId", stu2, "status", "LATE", "lateMinutes", 15, "notes", "Retard bus"),
                                Map.of("studentId", stu3, "status", "ABSENT"),
                                Map.of("studentId", stu4, "status", "ABSENT"))))))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(post("/api/v1/attendance/sessions/" + session1Id + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // Justification de l'absence de stu3 par le secrétariat (avec pièce justificative privée)
        UUID stu3RecordId = findRecordIdByStudentId(s1Updated.get("records"), stu3);
        mockMvc.perform(post("/api/v1/attendance/records/" + stu3RecordId + "/justify")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "reason", "Certificat médical fourni par la famille",
                                "documentRef", "justifs/2026/cert-stu3.pdf"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.justified").value(true))
                .andExpect(jsonPath("$.justificationDocumentRef").value("justifs/2026/cert-stu3.pdf"));

        UUID session2Id = createAttendanceSessionViaApi(
                censorToken, class3eAId, periodId, null, "2026-10-17", "MORNING", "08:00", "12:00", false);
        mockMvc.perform(put("/api/v1/attendance/sessions/" + session2Id + "/records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(censorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("records", List.of(
                                Map.of("studentId", stu1, "status", "PRESENT"),
                                Map.of("studentId", stu2, "status", "PRESENT"),
                                Map.of("studentId", stu3, "status", "PRESENT"),
                                Map.of("studentId", stu4, "status", "ABSENT"))))))
                .andExpect(status().isOk());

        // Synthèse d'assiduité via API REST
        mockMvc.perform(get("/api/v1/attendance/summaries/students/" + stu3)
                        .header(HttpHeaders.AUTHORIZATION, bearer(censorToken))
                        .param("evaluationPeriodId", periodId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").value(2))
                .andExpect(jsonPath("$.presentCount").value(1))
                .andExpect(jsonPath("$.absentCount").value(1))
                .andExpect(jsonPath("$.justifiedAbsenceCount").value(1))
                .andExpect(jsonPath("$.unjustifiedAbsenceCount").value(0))
                .andExpect(jsonPath("$.attendanceRatePercent").value(50.00));

        // 6. Vérification des 4 ports publics Spring Modulith
        TenantContext.runWithTenant(TenantId.of(school.getId()), () -> {
            assertThat(userDirectoryPort.findUserById(teacherUser.getId())).isPresent();

            TeacherSummary teacherSummary = teacherDirectoryPort.findTeacherByUserId(teacherUser.getId()).orElseThrow();
            assertThat(teacherSummary.id()).isEqualTo(teacherId);
            assertThat(teacherDirectoryPort.isUserAssignedToClassSubject(teacherUser.getId(), class3eAId, mathId)).isTrue();
            assertThat(teacherDirectoryPort.isUserAssignedToClassSubject(teacherUser.getId(), class3eAId, frenchId)).isFalse();
            assertThat(teacherDirectoryPort.isUserHomeroomTeacherOfClass(teacherUser.getId(), class3eAId)).isTrue();

            StudentPeriodGradeSummary gradeSummary =
                    gradeSummaryPort.findStudentPeriodSummary(stu1, periodId).orElseThrow();
            assertThat(gradeSummary.generalAverage()).isEqualByComparingTo("16.11");
            assertThat(gradeSummary.rankInClass()).isEqualTo(1);
            assertThat(gradeSummary.subjects()).hasSize(2);

            ClassPeriodGradeStats stats = gradeSummaryPort.findClassPeriodStats(class3eAId, periodId).orElseThrow();
            assertThat(stats.studentCount()).isEqualTo(4);
            assertThat(stats.maxAverage()).isEqualByComparingTo("16.11");
            assertThat(stats.minAverage()).isEqualByComparingTo("9.00");

            StudentAttendanceSummary attSummary = attendanceSummaryPort.getStudentPeriodSummary(stu2, periodId);
            assertThat(attSummary.totalSessions()).isEqualTo(2);
            assertThat(attSummary.lateCount()).isEqualTo(1);
            assertThat(attSummary.totalLateMinutes()).isEqualTo(15);
            assertThat(attSummary.attendanceRatePercent()).isEqualByComparingTo("100.00");
        });
    }

    @Test
    void shouldEnforceFineGrainedTeacherAndTutorRestrictionAcrossTeacherStudentGradeAndAttendance() throws Exception {
        School school = createSchool("TGA-SCOPE", SchoolType.TUTORING_CENTER);
        createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));
        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);

        UUID yearId = createAcademicYearViaApi(directorToken, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(directorToken, yearId);
        UUID periodId = createAndOpenPeriodViaApi(directorToken, yearId, "T1", "Trimestre 1", 1, "2026-10-01", "2026-12-20");
        UUID levelId = createGradeLevelViaApi(directorToken, "TERM", "Terminale", "TUTORING", 1);
        UUID mathId = createSubjectViaApi(directorToken, "MATH", "Mathématiques", "SCIENTIFIC", 5);
        UUID physicsId = createSubjectViaApi(directorToken, "PC", "Physique-Chimie", "SCIENTIFIC", 5);

        UUID classAId = createClassViaApi(directorToken, yearId, levelId, "TS-A", "Terminale S A", 30);
        UUID classBId = createClassViaApi(directorToken, yearId, levelId, "TS-B", "Terminale S B", 30);
        assignSubjectToClassViaApi(directorToken, classAId, mathId, 5, 6);
        assignSubjectToClassViaApi(directorToken, classAId, physicsId, 5, 6);
        assignSubjectToClassViaApi(directorToken, classBId, mathId, 5, 6);

        UUID stuClassA = createAndEnrollStudent(directorToken, yearId, classAId, "Ibrahima", "Sy", "MALE");
        UUID stuClassB = createAndEnrollStudent(directorToken, yearId, classBId, "Mariama", "Kane", "FEMALE");

        User tutorUser = createTenantUser(school.getId(), "tutor.math@centre.sn", "Aliou", "Diallo", RoleCode.TUTOR);
        String tutorToken = tenantTokenForUser(school.getId(), tutorUser.getId(), RoleCode.TUTOR);
        UUID tutorId = createTeacherViaApi(
                directorToken, "TUT-01", tutorUser.getId(), "Aliou", "Diallo", "TUTOR", "+221772223344", 5000L);
        UUID otherTeacherId = createTeacherViaApi(
                directorToken, "TUT-02", null, "Binta", "Sow", "TUTOR", "+221772223355", 5000L);

        // Affectation uniquement sur (ClassA, Math), PAS sur (ClassA, Physics) ni sur (ClassB, Math)
        createAssignmentViaApi(directorToken, tutorId, yearId, classAId, mathId, false, 4);

        // 1. Module teacher : accès à /me OK, liste globale et autre enseignant -> 403
        mockMvc.perform(get("/api/v1/teachers/me").header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/teachers").header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/teachers/" + otherTeacherId).header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isForbidden());

        // 2. Module student : le TUTOR ne voit QUE l'élève de ClassA, et reçoit 403 sur l'élève de ClassB
        mockMvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(stuClassA.toString()));
        mockMvc.perform(get("/api/v1/students/" + stuClassA).header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/students/" + stuClassB).header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:teacher-assignment-forbidden"));
        mockMvc.perform(get("/api/v1/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                        .param("classRoomId", classBId.toString()))
                .andExpect(status().isForbidden());

        // 3. Module grade : autorisé sur (ClassA, Math), refusé (403) sur (ClassA, Physics) et (ClassB, Math)
        UUID allowedEvalId = createEvaluationViaApi(
                tutorToken, periodId, classAId, mathId, "Devoir Math A", "HOMEWORK", "2026-10-20", "20.00", 1);
        assertThat(allowedEvalId).isNotNull();

        mockMvc.perform(post("/api/v1/grades/evaluations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "evaluationPeriodId", periodId,
                                "classRoomId", classAId,
                                "subjectId", physicsId,
                                "title", "Interdit Physique",
                                "evaluationType", "HOMEWORK",
                                "evaluationDate", "2026-10-20"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:teacher-assignment-forbidden"));

        mockMvc.perform(post("/api/v1/grades/evaluations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "evaluationPeriodId", periodId,
                                "classRoomId", classBId,
                                "subjectId", mathId,
                                "title", "Interdit Classe B",
                                "evaluationType", "HOMEWORK",
                                "evaluationDate", "2026-10-20"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:teacher-assignment-forbidden"));

        // Une évaluation créée par le directeur sur (ClassA, Physics) est invisible / 403 au tuteur Math
        UUID physicsEvalId = createEvaluationViaApi(
                directorToken, periodId, classAId, physicsId, "Devoir PC A", "TEST", "2026-10-21", "20.00", 1);
        mockMvc.perform(get("/api/v1/grades/evaluations/" + physicsEvalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/grades/evaluations/" + physicsEvalId + "/entries")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("entries", List.of(
                                Map.of("studentId", stuClassA, "attendanceStatus", "PRESENT", "score", 15.0))))))
                .andExpect(status().isForbidden());

        // 4. Module attendance : autorisé sur (ClassA, Math), refusé (403) sur (ClassA, Physics) et ClassB
        UUID allowedSessionId = createAttendanceSessionViaApi(
                tutorToken, classAId, periodId, mathId, "2026-10-22", "MORNING", "08:00", "10:00", true);
        assertThat(allowedSessionId).isNotNull();

        mockMvc.perform(post("/api/v1/attendance/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "classRoomId", classAId,
                                "subjectId", physicsId,
                                "sessionDate", "2026-10-22"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:teacher-assignment-forbidden"));

        mockMvc.perform(post("/api/v1/attendance/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "classRoomId", classBId,
                                "sessionDate", "2026-10-22"))))
                .andExpect(status().isForbidden());

        // Un tuteur ne peut pas verrouiller une feuille d'appel ni consulter l'assiduité d'un élève de ClassB
        mockMvc.perform(post("/api/v1/attendance/sessions/" + allowedSessionId + "/lock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/attendance/summaries/students/" + stuClassB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldStrictlyBlockGradeEntryAndModificationWhenEvaluationPeriodIsClosedOrLocked() throws Exception {
        School school = createSchool("TGA-LOCK", SchoolType.PRIVATE_SCHOOL);
        createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));
        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);

        UUID yearId = createAcademicYearViaApi(directorToken, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(directorToken, yearId);
        UUID periodId = createPeriodViaApi(directorToken, yearId, "T1", "Trimestre 1", 1, "2026-10-01", "2026-12-20");
        UUID levelId = createGradeLevelViaApi(directorToken, "6E", "Sixième", "MIDDLE_SCHOOL", 1);
        UUID mathId = createSubjectViaApi(directorToken, "MATH", "Mathématiques", "SCIENTIFIC", 4);
        UUID classId = createClassViaApi(directorToken, yearId, levelId, "6E-A", "6ème A", 30);
        assignSubjectToClassViaApi(directorToken, classId, mathId, 4, 5);
        UUID studentId = createAndEnrollStudent(directorToken, yearId, classId, "Samba", "Gueye", "MALE");

        // 1. Période PLANNED -> création évaluation bloquée (409 evaluation-period-closed)
        mockMvc.perform(post("/api/v1/grades/evaluations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "evaluationPeriodId", periodId,
                                "classRoomId", classId,
                                "subjectId", mathId,
                                "title", "Devoir trop tôt",
                                "evaluationType", "HOMEWORK",
                                "evaluationDate", "2026-10-15"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:evaluation-period-closed"));

        // 2. Ouverture période -> création + saisie autorisées
        mockMvc.perform(post("/api/v1/academic/periods/" + periodId + "/open")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk());
        UUID evalId = createEvaluationViaApi(
                directorToken, periodId, classId, mathId, "Devoir 1", "HOMEWORK", "2026-10-15", "20.00", 1);
        upsertGradesViaApi(directorToken, evalId, List.of(
                Map.of("studentId", studentId, "attendanceStatus", "PRESENT", "score", 15.0)));

        // 3. Clôture période (CLOSED) -> TOUTE création/modification d'évaluation ou saisie de note bloquée (409)
        mockMvc.perform(post("/api/v1/academic/periods/" + periodId + "/close")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(post("/api/v1/grades/evaluations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "evaluationPeriodId", periodId,
                                "classRoomId", classId,
                                "subjectId", mathId,
                                "title", "Devoir sur période fermée",
                                "evaluationType", "HOMEWORK",
                                "evaluationDate", "2026-10-20"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:evaluation-period-closed"));

        mockMvc.perform(put("/api/v1/grades/evaluations/" + evalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Modif interdite",
                                "evaluationType", "HOMEWORK",
                                "evaluationDate", "2026-10-15",
                                "maxScore", 20.0,
                                "weight", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:evaluation-period-closed"));

        mockMvc.perform(put("/api/v1/grades/evaluations/" + evalId + "/entries")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("entries", List.of(
                                Map.of("studentId", studentId, "attendanceStatus", "PRESENT", "score", 18.0))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:evaluation-period-closed"));

        // En revanche le calcul de bulletin reste possible sur période CLOSED
        JsonNode cards = readJson(mockMvc.perform(post("/api/v1/grades/report-cards/compute")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("classRoomId", classId, "evaluationPeriodId", periodId))))
                .andExpect(status().isOk())
                .andReturn());
        UUID cardId = UUID.fromString(cards.get(0).get("id").asString());

        // 4. Verrouillage période (LOCKED) -> même les bulletins et recalculs sont strictement bloqués (409)
        mockMvc.perform(post("/api/v1/academic/periods/" + periodId + "/lock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        mockMvc.perform(put("/api/v1/grades/evaluations/" + evalId + "/entries")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("entries", List.of(
                                Map.of("studentId", studentId, "attendanceStatus", "PRESENT", "score", 19.0))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:evaluation-period-closed"));

        mockMvc.perform(post("/api/v1/grades/report-cards/compute")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("classRoomId", classId, "evaluationPeriodId", periodId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:evaluation-period-closed"));

        mockMvc.perform(put("/api/v1/grades/report-cards/" + cardId + "/appreciations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("directorAppreciation", "Modif après LOCKED"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:evaluation-period-closed"));
    }

    @Test
    void shouldEnforceRbacMatrixAndSubscriptionGuardsAcrossTeacherGradeAndAttendance() throws Exception {
        School school = createSchool("TGA-RBAC", SchoolType.PRIVATE_SCHOOL);
        createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));

        // Non authentifié -> 401
        mockMvc.perform(get("/api/v1/teachers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/grades/evaluations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/attendance/sessions")).andExpect(status().isUnauthorized());

        // MFA_PENDING -> 403
        String mfaPending = tokenIssuer
                .issueMfaPendingToken(AccountRef.user(UUID.randomUUID(), school.getId()))
                .value();
        mockMvc.perform(get("/api/v1/teachers").header(HttpHeaders.AUTHORIZATION, bearer(mfaPending)))
                .andExpect(status().isForbidden());

        // Rôles interdits (ADMIN_SAAS, CASHIER, PARENT, STUDENT) -> 403 partout
        String adminToken = platformAdminToken();
        for (String forbiddenToken : List.of(
                adminToken,
                tenantToken(school.getId(), RoleCode.CASHIER),
                tenantToken(school.getId(), RoleCode.PARENT),
                tenantToken(school.getId(), RoleCode.STUDENT))) {
            mockMvc.perform(get("/api/v1/teachers").header(HttpHeaders.AUTHORIZATION, bearer(forbiddenToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            mockMvc.perform(get("/api/v1/grades/evaluations").header(HttpHeaders.AUTHORIZATION, bearer(forbiddenToken)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/attendance/sessions").header(HttpHeaders.AUTHORIZATION, bearer(forbiddenToken)))
                    .andExpect(status().isForbidden());
        }

        // PROMOTER et SECRETARY : lecture seule sur teachers / grades / attendance (sauf justification pour SECRETARY)
        for (RoleCode reader : List.of(RoleCode.PROMOTER, RoleCode.SECRETARY)) {
            String token = tenantToken(school.getId(), reader);
            mockMvc.perform(get("/api/v1/teachers").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/grades/evaluations").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/attendance/sessions").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/teachers")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "employeeCode", "ENS-NO",
                                    "firstName", "Refusé",
                                    "lastName", reader.name(),
                                    "teacherType", "PERMANENT",
                                    "phone", "+221770001122"))))
                    .andExpect(status().isForbidden());
        }

        // Abonnement EXPIRED : lecture 200, écriture 403
        School expiredSchool = createSchool("TGA-EXP", SchoolType.PRIVATE_SCHOOL);
        createSubscription(expiredSchool, SubscriptionStatus.ACTIVE, Instant.now().minus(1, ChronoUnit.DAYS));
        String expDirector = tenantToken(expiredSchool.getId(), RoleCode.DIRECTOR);
        mockMvc.perform(get("/api/v1/teachers").header(HttpHeaders.AUTHORIZATION, bearer(expDirector)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/grades/evaluations").header(HttpHeaders.AUTHORIZATION, bearer(expDirector)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/attendance/sessions").header(HttpHeaders.AUTHORIZATION, bearer(expDirector)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/teachers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expDirector))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "employeeCode", "ENS-EXP",
                                "firstName", "Bloqué",
                                "lastName", "Expiré",
                                "teacherType", "PERMANENT",
                                "phone", "+221770009999"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-access-denied"));

        // Sans abonnement -> lecture et écriture 403
        School noSubSchool = createSchool("TGA-NOSUB", SchoolType.TUTORING_CENTER);
        String noSubDirector = tenantToken(noSubSchool.getId(), RoleCode.DIRECTOR);
        mockMvc.perform(get("/api/v1/teachers").header(HttpHeaders.AUTHORIZATION, bearer(noSubDirector)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-access-denied"));
        mockMvc.perform(get("/api/v1/grades/evaluations").header(HttpHeaders.AUTHORIZATION, bearer(noSubDirector)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/attendance/sessions").header(HttpHeaders.AUTHORIZATION, bearer(noSubDirector)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldEnforceStrictMultiTenantIsolationAcrossHttpAndPostgresqlRlsForAllV8Tables() throws Exception {
        School schoolA = createSchool("ISO-TGA-A", SchoolType.PRIVATE_SCHOOL);
        School schoolB = createSchool("ISO-TGA-B", SchoolType.TUTORING_CENTER);
        createSubscription(schoolA, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));
        createSubscription(schoolB, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));

        String dirA = tenantToken(schoolA.getId(), RoleCode.DIRECTOR);
        String dirB = tenantToken(schoolB.getId(), RoleCode.DIRECTOR);

        // Données symétriques complètes dans A et B (mêmes codes ENS-001, 2026-2027, T1, MATH, 6E-A)
        TenantFixture a = seedCompleteFixture(schoolA, dirA);
        TenantFixture b = seedCompleteFixture(schoolB, dirB);

        // 1. En-tête X-Tenant-Id et paramètre ?tenantId forgés ignorés : A ne voit que A
        mockMvc.perform(get("/api/v1/teachers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirA))
                        .header("X-Tenant-Id", schoolB.getId().toString())
                        .param("tenantId", schoolB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a.teacherId().toString()));

        // 2. Accès direct cross-tenant aux ressources de B -> 404
        mockMvc.perform(get("/api/v1/teachers/" + b.teacherId()).header(HttpHeaders.AUTHORIZATION, bearer(dirA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/teacher-assignments/" + b.assignmentId()).header(HttpHeaders.AUTHORIZATION, bearer(dirA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/grades/evaluations/" + b.evaluationId()).header(HttpHeaders.AUTHORIZATION, bearer(dirA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/grades/report-cards/" + b.reportCardId()).header(HttpHeaders.AUTHORIZATION, bearer(dirA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/attendance/sessions/" + b.attendanceSessionId()).header(HttpHeaders.AUTHORIZATION, bearer(dirA)))
                .andExpect(status().isNotFound());

        // 3. Liaisons cross-tenant -> 404 (ex: lier userB à teacherA, justifier recordB depuis A)
        mockMvc.perform(put("/api/v1/teachers/" + a.teacherId() + "/user-link")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", b.userId()))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/attendance/records/" + b.attendanceRecordId() + "/justify")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Cross tenant"))))
                .andExpect(status().isNotFound());

        // 4. Vérification PostgreSQL RLS directe sous ecole_app sur les 9 nouvelles tables de V8
        List<String> v8Tables = List.of(
                "teachers",
                "teacher_specialties",
                "teacher_assignments",
                "evaluations",
                "student_grades",
                "period_report_cards",
                "period_subject_results",
                "attendance_sessions",
                "attendance_records");

        // Sans TenantContext -> 0 ligne (fail-closed)
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            for (String table : v8Tables) {
                Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
                assertThat(count).as("RLS fail-closed sans tenant sur " + table).isZero();
            }
        });

        // Avec Tenant A -> uniquement les lignes de A, aucune ligne de B
        TenantContext.runWithTenant(TenantId.of(schoolA.getId()), () -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            for (String table : v8Tables) {
                Integer visibleA = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE tenant_id = ?",
                        Integer.class,
                        schoolA.getId());
                Integer visibleB = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE tenant_id = ?",
                        Integer.class,
                        schoolB.getId());
                assertThat(visibleA).as("Lignes tenant A visibles sur " + table).isGreaterThan(0);
                assertThat(visibleB).as("Lignes tenant B invisibles sur " + table).isZero();
            }
        }));

        // Tentative INSERT cross-tenant sous ecole_app -> bloquée par WITH CHECK RLS
        assertThatThrownBy(() -> TenantContext.runWithTenant(
                TenantId.of(schoolA.getId()),
                () -> transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update(
                            "INSERT INTO teachers (tenant_id, employee_code, first_name, last_name, teacher_type, phone, status) "
                                    + "VALUES (?, 'CROSS-1', 'Evil', 'Cross', 'PERMANENT', '+221779999999', 'ACTIVE')",
                            schoolB.getId());
                })))
                .isInstanceOf(DataAccessException.class);
    }

    private record TenantFixture(
            UUID userId,
            UUID teacherId,
            UUID assignmentId,
            UUID evaluationId,
            UUID reportCardId,
            UUID attendanceSessionId,
            UUID attendanceRecordId) {
    }

    private TenantFixture seedCompleteFixture(School school, String directorToken) throws Exception {
        UUID yearId = createAcademicYearViaApi(directorToken, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(directorToken, yearId);
        UUID periodId = createAndOpenPeriodViaApi(directorToken, yearId, "T1", "Trimestre 1", 1, "2026-10-01", "2026-12-20");
        UUID levelId = createGradeLevelViaApi(directorToken, "6E", "Sixième", "MIDDLE_SCHOOL", 1);
        UUID mathId = createSubjectViaApi(directorToken, "MATH", "Mathématiques", "SCIENTIFIC", 4);
        UUID classId = createClassViaApi(directorToken, yearId, levelId, "6E-A", "6ème A", 30);
        assignSubjectToClassViaApi(directorToken, classId, mathId, 4, 5);
        UUID studentId = createAndEnrollStudent(directorToken, yearId, classId, "Amina", "Diallo", "FEMALE");

        User teacherUser = createTenantUser(
                school.getId(), "ens-" + school.getCode().toLowerCase() + "@ecole.sn", "Modou", "Fall", RoleCode.TEACHER);
        UUID teacherId = createTeacherViaApi(
                directorToken, "ENS-001", teacherUser.getId(), "Modou", "Fall", "PERMANENT", "+221770011223", null);
        mockMvc.perform(post("/api/v1/teachers/" + teacherId + "/specialties")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("subjectId", mathId, "primarySpecialty", true))))
                .andExpect(status().isCreated());
        UUID assignmentId = createAssignmentViaApi(directorToken, teacherId, yearId, classId, mathId, true, 5);

        UUID evalId = createEvaluationViaApi(
                directorToken, periodId, classId, mathId, "Devoir 1", "HOMEWORK", "2026-10-15", "20.00", 1);
        upsertGradesViaApi(directorToken, evalId, List.of(
                Map.of("studentId", studentId, "attendanceStatus", "PRESENT", "score", 15.0)));

        JsonNode cards = readJson(mockMvc.perform(post("/api/v1/grades/report-cards/compute")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("classRoomId", classId, "evaluationPeriodId", periodId))))
                .andExpect(status().isOk())
                .andReturn());
        UUID reportCardId = UUID.fromString(cards.get(0).get("id").asString());

        UUID sessionId = createAttendanceSessionViaApi(
                directorToken, classId, periodId, mathId, "2026-10-16", "MORNING", "08:00", "10:00", false);
        JsonNode updatedSession = readJson(mockMvc.perform(put("/api/v1/attendance/sessions/" + sessionId + "/records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("records", List.of(
                                Map.of("studentId", studentId, "status", "ABSENT"))))))
                .andExpect(status().isOk())
                .andReturn());
        UUID recordId = UUID.fromString(updatedSession.get("records").get(0).get("id").asString());

        return new TenantFixture(
                teacherUser.getId(), teacherId, assignmentId, evalId, reportCardId, sessionId, recordId);
    }

    @Test
    void shouldBatchLoadReferencesAcrossPortsAndRecomputeReportCardsInBatch() throws Exception {
        School schoolA = createSchool("M67A", SchoolType.PRIVATE_SCHOOL);
        School schoolB = createSchool("M67B", SchoolType.PRIVATE_SCHOOL);
        createSubscription(schoolA, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));
        createSubscription(schoolB, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));

        String dirTokenA = tenantToken(schoolA.getId(), RoleCode.DIRECTOR);
        String dirTokenB = tenantToken(schoolB.getId(), RoleCode.DIRECTOR);

        UUID yearA = createAcademicYearViaApi(dirTokenA, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(dirTokenA, yearA);
        UUID periodA = createAndOpenPeriodViaApi(dirTokenA, yearA, "T1", "Trimestre 1", 1, "2026-10-01", "2026-12-20");
        UUID levelA = createGradeLevelViaApi(dirTokenA, "3E", "Troisième", "MIDDLE_SCHOOL", 4);
        UUID mathA = createSubjectViaApi(dirTokenA, "MATH", "Mathématiques", "SCIENTIFIC", 4);
        UUID frA = createSubjectViaApi(dirTokenA, "FR", "Français", "LITERARY", 3);
        UUID histA = createSubjectViaApi(dirTokenA, "HIST", "Histoire-Géo", "HUMANITIES", 2);
        UUID classA = createClassViaApi(dirTokenA, yearA, levelA, "3E-A", "Troisième A", 40);

        assignSubjectToClassViaApi(dirTokenA, classA, mathA, 4, 5);
        assignSubjectToClassViaApi(dirTokenA, classA, frA, 3, 4);
        MvcResult histClassSubjRes = mockMvc.perform(post("/api/v1/academic/classes/" + classA + "/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirTokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "subjectId", histA,
                                "coefficient", 2,
                                "weeklyHours", 2,
                                "mandatory", true))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID histClassSubjectId = UUID.fromString(readJson(histClassSubjRes).get("id").asString());

        UUID yearB = createAcademicYearViaApi(dirTokenB, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(dirTokenB, yearB);
        UUID levelB = createGradeLevelViaApi(dirTokenB, "3E", "Troisième", "MIDDLE_SCHOOL", 4);
        UUID mathB = createSubjectViaApi(dirTokenB, "MATH", "Mathématiques", "SCIENTIFIC", 4);
        UUID classB = createClassViaApi(dirTokenB, yearB, levelB, "3E-B", "Troisième B", 40);
        UUID teacherB = createTeacherViaApi(dirTokenB, "ENS-B1", null, "Bamba", "Diallo", "PERMANENT", "+221779990001", null);

        User teacherUserA = createTenantUser(schoolA.getId(), "prof.m67@ecole.sn", "Mamadou", "Sarr", RoleCode.TEACHER);
        UUID teacherA1 = createTeacherViaApi(
                dirTokenA, "ENS-A1", teacherUserA.getId(), "Mamadou", "Sarr", "PERMANENT", "+221779990002", null);
        UUID teacherA2 = createTeacherViaApi(
                dirTokenA, "ENS-A2", null, "Aissatou", "Ba", "PART_TIME", "+221779990003", 5000L);
        createAssignmentViaApi(dirTokenA, teacherA1, yearA, classA, mathA, true, 5);
        createAssignmentViaApi(dirTokenA, teacherA2, yearA, classA, frA, false, 4);

        // M6 : lectures groupées sur AcademicStructurePort & TeacherDirectoryPort + isolation inter-tenant
        TenantContext.runWithTenant(TenantId.of(schoolA.getId()), () -> {
            assertThat(academicStructurePort.findAcademicYearsByIds(List.of())).isEmpty();
            assertThat(academicStructurePort.findAcademicYearsByIds(null)).isEmpty();
            assertThat(academicStructurePort.findAcademicYearsByIds(List.of(yearA, yearB)))
                    .containsOnlyKeys(yearA);
            assertThat(academicStructurePort.findEvaluationPeriodsByIds(List.of(periodA)))
                    .containsOnlyKeys(periodA);
            assertThat(academicStructurePort.findGradeLevelsByIds(List.of(levelA, levelB)))
                    .containsOnlyKeys(levelA);
            assertThat(academicStructurePort.findSubjectsByIds(List.of(mathA, frA, mathB)))
                    .containsOnlyKeys(mathA, frA);
            assertThat(academicStructurePort.findClassRoomsByIds(List.of(classA, classB)))
                    .containsOnlyKeys(classA);
            assertThat(teacherDirectoryPort.findTeachersByIds(List.of())).isEmpty();
            assertThat(teacherDirectoryPort.findTeachersByIds(List.of(teacherA1, teacherA2, teacherB)))
                    .containsOnlyKeys(teacherA1, teacherA2);
        });

        UUID student1 = createAndEnrollStudent(dirTokenA, yearA, classA, "Fatou", "Gueye", "FEMALE");
        UUID student2 = createAndEnrollStudent(dirTokenA, yearA, classA, "Cheikh", "Mbaye", "MALE");

        UUID evalMath = createEvaluationViaApi(
                dirTokenA, periodA, classA, mathA, "Devoir Maths", "TEST", "2026-10-20", "20", 1);
        UUID evalFr = createEvaluationViaApi(
                dirTokenA, periodA, classA, frA, "Devoir Français", "TEST", "2026-10-22", "20", 1);
        upsertGradesViaApi(dirTokenA, evalMath, List.of(
                Map.of("studentId", student1, "attendanceStatus", "PRESENT", "score", new BigDecimal("16.00")),
                Map.of("studentId", student2, "attendanceStatus", "PRESENT", "score", new BigDecimal("12.00"))));
        upsertGradesViaApi(dirTokenA, evalFr, List.of(
                Map.of("studentId", student1, "attendanceStatus", "PRESENT", "score", new BigDecimal("14.00")),
                Map.of("studentId", student2, "attendanceStatus", "PRESENT", "score", new BigDecimal("15.00"))));

        createAttendanceSessionViaApi(
                dirTokenA, classA, periodA, mathA, "2026-10-20", "MORNING", "08:00", "10:00", true);
        createAttendanceSessionViaApi(
                dirTokenA, classA, periodA, frA, "2026-10-21", "AFTERNOON", "15:00", "17:00", true);

        // M6 : vérification des listes paginées enrichies par lots
        mockMvc.perform(get("/api/v1/attendance/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirTokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].classRoomCode").value("3E-A"))
                .andExpect(jsonPath("$.content[0].periodCode").value("T1"));

        mockMvc.perform(get("/api/v1/grades/evaluations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirTokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].classRoomCode").value("3E-A"))
                .andExpect(jsonPath("$.content[0].periodCode").value("T1"));

        mockMvc.perform(get("/api/v1/teacher-assignments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirTokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].classRoomCode").value("3E-A"))
                .andExpect(jsonPath("$.content[0].academicYearCode").value("2026-2027"));

        String teacherTokenA = tenantTokenForUser(schoolA.getId(), teacherUserA.getId(), RoleCode.TEACHER);
        mockMvc.perform(get("/api/v1/teachers/me/assignments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherTokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subjectCode").value("MATH"));

        mockMvc.perform(get("/api/v1/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirTokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].classRoomCode").value("3E-A"))
                .andExpect(jsonPath("$.content[0].academicYearCode").value("2026-2027"));

        // M7 : calcul en lot des bulletins (3 matières actives), appréciation DRAFT, désactivation matière obsolète, recalcul
        MvcResult firstCompute = mockMvc.perform(post("/api/v1/grades/report-cards/compute")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirTokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("classRoomId", classA, "evaluationPeriodId", periodA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].subjectResults.length()").value(3))
                .andReturn();
        UUID firstCardId = UUID.fromString(readJson(firstCompute).get(0).get("id").asString());

        mockMvc.perform(put("/api/v1/grades/report-cards/" + firstCardId + "/subjects/" + mathA + "/appreciation")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirTokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("teacherAppreciation", "Très bon travail"))))
                .andExpect(status().isOk());

        // Désactiver Histoire-Géo sur la classe puis recalculer -> la matière obsolète disparaît, l'appréciation reste
        mockMvc.perform(post("/api/v1/academic/classes/" + classA + "/subjects/" + histClassSubjectId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirTokenA)))
                .andExpect(status().isOk());

        MvcResult recompute = mockMvc.perform(post("/api/v1/grades/report-cards/compute")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirTokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("classRoomId", classA, "evaluationPeriodId", periodA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].subjectResults.length()").value(2))
                .andReturn();

        JsonNode firstRecomputedSubjects = readJson(recompute).get(0).get("subjectResults");
        boolean foundPreservedAppreciation = false;
        for (JsonNode subj : firstRecomputedSubjects) {
            assertThat(subj.get("subjectCode").asString()).isNotEqualTo("HIST");
            if ("MATH".equals(subj.get("subjectCode").asString())) {
                assertThat(subj.get("teacherAppreciation").asString()).isEqualTo("Très bon travail");
                foundPreservedAppreciation = true;
            }
        }
        assertThat(foundPreservedAppreciation).isTrue();
    }

    // --- Helpers ---

    private School createSchool(String prefix, SchoolType type) {
        String code = prefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return schoolService.createSchool(
                code, "Établissement " + code, type, "+221770000000", "contact@" + code.toLowerCase() + ".sn", "Dakar");
    }

    private void createSubscription(School school, SubscriptionStatus status, Instant endsAt) {
        SubscriptionPlan plan = subscriptionService.createPlan(
                "PLAN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                "Forfait Test",
                25_000L,
                250_000L,
                200,
                50,
                5_000);
        Instant startsAt = endsAt.minus(60, ChronoUnit.DAYS);
        TenantContext.runWithTenant(TenantId.of(school.getId()), () -> subscriptionService.createSubscription(
                plan.getId(), status, BillingCycle.MONTHLY, startsAt, endsAt));
    }

    private User createTenantUser(UUID schoolId, String email, String firstName, String lastName, RoleCode role) {
        return TenantContext.executeWithTenant(
                TenantId.of(schoolId),
                () -> userAccountService.createUserWithRole(
                        email, "+221770000001", firstName, lastName, "argon2-test-hash", role));
    }

    private UUID createAcademicYearViaApi(String token, String code, String startDate, String endDate) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/academic/years")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", code,
                                "label", "Année " + code,
                                "startDate", startDate,
                                "endDate", endDate,
                                "periodSystem", "TRIMESTER"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private void activateAcademicYearViaApi(String token, UUID yearId) throws Exception {
        mockMvc.perform(post("/api/v1/academic/years/" + yearId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("makeCurrent", true))))
                .andExpect(status().isOk());
    }

    private UUID createPeriodViaApi(
            String token, UUID yearId, String code, String name, int order, String startDate, String endDate)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/academic/years/" + yearId + "/periods")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", code,
                                "name", name,
                                "periodType", "TRIMESTER",
                                "sequenceOrder", order,
                                "startDate", startDate,
                                "endDate", endDate,
                                "weight", 1))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createAndOpenPeriodViaApi(
            String token, UUID yearId, String code, String name, int order, String startDate, String endDate)
            throws Exception {
        UUID periodId = createPeriodViaApi(token, yearId, code, name, order, startDate, endDate);
        mockMvc.perform(post("/api/v1/academic/periods/" + periodId + "/open")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        return periodId;
    }

    private UUID createGradeLevelViaApi(String token, String code, String name, String cycle, int order)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/academic/grade-levels")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "name", name, "cycle", cycle, "displayOrder", order))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createSubjectViaApi(String token, String code, String name, String category, int defaultCoef)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/academic/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", code,
                                "name", name,
                                "category", category,
                                "defaultCoefficient", defaultCoef))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createClassViaApi(String token, UUID yearId, UUID levelId, String code, String name, int capacity)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/academic/classes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "academicYearId", yearId,
                                "gradeLevelId", levelId,
                                "code", code,
                                "name", name,
                                "capacity", capacity))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private void assignSubjectToClassViaApi(String token, UUID classId, UUID subjectId, int coef, int hours)
            throws Exception {
        mockMvc.perform(post("/api/v1/academic/classes/" + classId + "/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "subjectId", subjectId,
                                "coefficient", coef,
                                "weeklyHours", hours,
                                "mandatory", true))))
                .andExpect(status().isCreated());
    }

    private UUID createAndEnrollStudent(
            String token, UUID yearId, UUID classId, String firstName, String lastName, String gender)
            throws Exception {
        MvcResult stuRes = mockMvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", firstName,
                                "lastName", lastName,
                                "gender", gender,
                                "birthDate", "2012-04-10",
                                "admissionDate", "2026-10-01"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID studentId = UUID.fromString(readJson(stuRes).get("id").asString());

        mockMvc.perform(post("/api/v1/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", studentId,
                                "academicYearId", yearId,
                                "classRoomId", classId,
                                "enrollmentType", "NEW_ADMISSION",
                                "enrollmentDate", "2026-10-02"))))
                .andExpect(status().isCreated());
        return studentId;
    }

    private UUID createTeacherViaApi(
            String token,
            String code,
            UUID userId,
            String firstName,
            String lastName,
            String type,
            String phone,
            Long hourlyRateXof)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("employeeCode", code);
        if (userId != null) {
            body.put("userId", userId);
        }
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("teacherType", type);
        body.put("phone", phone);
        if (hourlyRateXof != null) {
            body.put("hourlyRateXof", hourlyRateXof);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/teachers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createAssignmentViaApi(
            String token, UUID teacherId, UUID yearId, UUID classId, UUID subjectId, boolean homeroom, int weeklyHours)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/teacher-assignments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "teacherId", teacherId,
                                "academicYearId", yearId,
                                "classRoomId", classId,
                                "subjectId", subjectId,
                                "homeroomTeacher", homeroom,
                                "weeklyHours", weeklyHours))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createEvaluationViaApi(
            String token,
            UUID periodId,
            UUID classId,
            UUID subjectId,
            String title,
            String type,
            String date,
            String maxScore,
            int weight)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/grades/evaluations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "evaluationPeriodId", periodId,
                                "classRoomId", classId,
                                "subjectId", subjectId,
                                "title", title,
                                "evaluationType", type,
                                "evaluationDate", date,
                                "maxScore", new BigDecimal(maxScore),
                                "weight", weight))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private void upsertGradesViaApi(String token, UUID evaluationId, List<Map<String, Object>> entries)
            throws Exception {
        mockMvc.perform(put("/api/v1/grades/evaluations/" + evaluationId + "/entries")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("entries", entries))))
                .andExpect(status().isOk());
    }

    private UUID createAttendanceSessionViaApi(
            String token,
            UUID classId,
            UUID periodId,
            UUID subjectId,
            String sessionDate,
            String timeSlot,
            String startTime,
            String endTime,
            boolean initializePresent)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("classRoomId", classId);
        if (periodId != null) {
            body.put("evaluationPeriodId", periodId);
        }
        if (subjectId != null) {
            body.put("subjectId", subjectId);
        }
        body.put("sessionDate", sessionDate);
        body.put("timeSlot", timeSlot);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        body.put("initializePresent", initializePresent);
        MvcResult res = mockMvc.perform(post("/api/v1/attendance/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private static JsonNode findByStudentId(JsonNode arrayNode, UUID studentId) {
        String expected = studentId.toString();
        for (JsonNode item : arrayNode) {
            if (expected.equals(item.get("studentId").asString())) {
                return item;
            }
        }
        throw new AssertionError("Bulletin introuvable pour l'élève " + expected);
    }

    private static UUID findRecordIdByStudentId(JsonNode recordsArray, UUID studentId) {
        String expected = studentId.toString();
        for (JsonNode item : recordsArray) {
            if (expected.equals(item.get("studentId").asString())) {
                return UUID.fromString(item.get("id").asString());
            }
        }
        throw new AssertionError("Pointage introuvable pour l'élève " + expected);
    }

    private String platformAdminToken() {
        return tokenIssuer
                .issueAccessToken(AccountRef.platformAdmin(UUID.randomUUID()), Set.of(RoleCode.ADMIN_SAAS.name()))
                .value();
    }

    private String tenantToken(UUID schoolId, RoleCode role) {
        return tenantTokenForUser(schoolId, UUID.randomUUID(), role);
    }

    private String tenantTokenForUser(UUID schoolId, UUID userId, RoleCode role) {
        return tokenIssuer
                .issueAccessToken(AccountRef.user(userId, schoolId), Set.of(role.name()))
                .value();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
