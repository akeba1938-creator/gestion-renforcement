package com.ecole.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecole.saas.center.CenterDirectoryPort;
import com.ecole.saas.center.CenterGroupSummary;
import com.ecole.saas.center.StudentCenterSummary;
import com.ecole.saas.center.TutorCenterWorkloadSummary;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
class CenterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final SchoolService schoolService;
    private final SubscriptionService subscriptionService;
    private final UserAccountService userAccountService;
    private final CenterDirectoryPort centerDirectoryPort;
    private final TokenIssuer tokenIssuer;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CenterIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            SchoolService schoolService,
            SubscriptionService subscriptionService,
            UserAccountService userAccountService,
            CenterDirectoryPort centerDirectoryPort,
            TokenIssuer tokenIssuer,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.schoolService = schoolService;
        this.subscriptionService = subscriptionService;
        this.userAccountService = userAccountService;
        this.centerDirectoryPort = centerDirectoryPort;
        this.tokenIssuer = tokenIssuer;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldRunEndToEndCenterFlowWithPacksSchedulesAttendanceAndPublicPort() throws Exception {
        School center = createSchool("CTR", SchoolType.TUTORING_CENTER);
        createSubscription(center, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));
        String managerToken = tenantToken(center.getId(), RoleCode.CENTER_MANAGER);

        UUID yearId = createAcademicYearViaApi(managerToken, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(managerToken, yearId);
        UUID levelId = createGradeLevelViaApi(managerToken, "TS-RENF", "Terminale S Renforcement", "TUTORING", 1);
        UUID mathId = createSubjectViaApi(managerToken, "MATH", "Mathématiques", "SCIENTIFIC", 5);

        UUID student1 = createStudentViaApi(managerToken, "Fatou", "Ndiaye", "FEMALE");
        UUID student2 = createStudentViaApi(managerToken, "Moussa", "Diop", "MALE");
        UUID student3 = createStudentViaApi(managerToken, "Awa", "Sarr", "FEMALE");

        User tutorUser = createTenantUser(
                center.getId(), "tutor-" + center.getCode().toLowerCase() + "@centre.sn", "Ibrahima", "Seck", RoleCode.TUTOR);
        UUID tutorId = createTeacherViaApi(
                managerToken, "TUT-001", tutorUser.getId(), "Ibrahima", "Seck", "TUTOR", "+221771112233", 5_000L);
        String tutorToken = tenantTokenForUser(center.getId(), tutorUser.getId(), RoleCode.TUTOR);

        // 1. Catalogue de packs en FCFA entiers
        UUID packId = createPackViaApi(managerToken, yearId, levelId, mathId, "PACK-2S", "Carnet 2 séances Maths", "SESSION_PACK", 2, 60, 20_000L);

        // 2. Cohorte de capacité 2
        UUID groupId = createGroupViaApi(managerToken, yearId, levelId, mathId, packId, "BAC-MATH-1", "Prépa BAC Maths 1", "EXAM_PREP", 2, "2026-10-01", "2027-06-30");
        mockMvc.perform(post("/api/v1/center/groups/" + groupId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 3. Affectation intervenant avec taux horaire 5 000 FCFA
        assignTutorViaApi(managerToken, groupId, tutorId, mathId, true, 5_000L);

        // 4. Attribution du pack 2 séances à student1 et inscriptions (test dépassement capacité)
        UUID studentPackId = assignStudentPackViaApi(managerToken, student1, packId, groupId, "2026-10-01");
        enrollStudentViaApi(managerToken, groupId, student1, studentPackId, "2026-10-02");
        enrollStudentViaApi(managerToken, groupId, student2, null, "2026-10-02");

        mockMvc.perform(post("/api/v1/center/groups/" + groupId + "/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("studentId", student3, "enrolledAt", "2026-10-02"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:center-group-capacity-exceeded"));

        // 5. Créneau hebdomadaire mercredi 15:00-17:00 + test conflit horaire
        createScheduleSlotViaApi(managerToken, groupId, tutorId, mathId, "WEDNESDAY", "15:00", "17:00");
        mockMvc.perform(post("/api/v1/center/groups/" + groupId + "/schedule-slots")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "teacherId", tutorId,
                                "dayOfWeek", "WEDNESDAY",
                                "startTime", "16:00",
                                "endTime", "18:00"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:center-schedule-conflict"));

        // 6. Génération de séances sur 2 mercredis (7 et 14 octobre 2026) + idempotence
        JsonNode generated = readJson(mockMvc.perform(post("/api/v1/center/groups/" + groupId + "/sessions/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fromDate", "2026-10-05", "toDate", "2026-10-18"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(2))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andReturn());
        UUID session1Id = UUID.fromString(generated.get("sessions").get(0).get("id").asString());
        UUID session2Id = UUID.fromString(generated.get("sessions").get(1).get("id").asString());

        mockMvc.perform(post("/api/v1/center/groups/" + groupId + "/sessions/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fromDate", "2026-10-05", "toDate", "2026-10-18"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.skippedCount").value(2));

        // 7. L'intervenant affecté démarre la séance 1, saisit le pointage et la clôture
        mockMvc.perform(post("/api/v1/center/sessions/" + session1Id + "/start")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(put("/api/v1/center/sessions/" + session1Id + "/attendance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("records", List.of(
                                Map.of("studentId", student1, "status", "PRESENT", "participationRating", 5, "homeworkDone", true),
                                Map.of("studentId", student2, "status", "LATE", "lateMinutes", 10, "participationRating", 4, "homeworkDone", false))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendances.length()").value(2));

        // Pack de student1 : 1/2 consommé, reste 1
        mockMvc.perform(get("/api/v1/center/student-packs/" + studentPackId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumedSessions").value(1))
                .andExpect(jsonPath("$.remainingSessions").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/center/sessions/" + session1Id + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("topic", "Limites et continuité", "notes", "Très bonne séance"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 8. Séance 2 : consommation de la 2e séance -> pack EXHAUSTED, puis refus sur une 3e séance, puis restitution à l'annulation
        mockMvc.perform(put("/api/v1/center/sessions/" + session2Id + "/attendance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("records", List.of(
                                Map.of("studentId", student1, "status", "PRESENT", "participationRating", 4, "homeworkDone", true))))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/center/student-packs/" + studentPackId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumedSessions").value(2))
                .andExpect(jsonPath("$.remainingSessions").value(0))
                .andExpect(jsonPath("$.status").value("EXHAUSTED"));

        UUID session3Id = createSessionViaApi(managerToken, groupId, tutorId, mathId, "2026-10-21", "15:00", "17:00");
        mockMvc.perform(put("/api/v1/center/sessions/" + session3Id + "/attendance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("records", List.of(
                                Map.of("studentId", student1, "status", "PRESENT"))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:center-pack-exhausted"));

        // Annulation séance 2 -> crédit restitué (pack redevient ACTIVE avec 1/2)
        mockMvc.perform(post("/api/v1/center/sessions/" + session2Id + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Jour férié"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/center/student-packs/" + studentPackId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumedSessions").value(1))
                .andExpect(jsonPath("$.remainingSessions").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 9. Synthèse élève et charge/rémunération intervenant via REST
        mockMvc.perform(get("/api/v1/center/students/" + student1 + "/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.centerSummary.activeGroupCount").value(1))
                .andExpect(jsonPath("$.centerSummary.remainingPackSessions").value(1))
                .andExpect(jsonPath("$.centerSummary.totalSessionsRecorded").value(1))
                .andExpect(jsonPath("$.centerSummary.attendanceRatePercent").value(100.00));

        // 1 séance terminée de 120 minutes à 5 000 FCFA/h => 10 000 FCFA entiers
        mockMvc.perform(get("/api/v1/center/tutors/" + tutorId + "/workload")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workload.completedSessionCount").value(1))
                .andExpect(jsonPath("$.workload.cancelledSessionCount").value(1))
                .andExpect(jsonPath("$.workload.completedMinutes").value(120))
                .andExpect(jsonPath("$.workload.estimatedRemunerationXof").value(10_000));

        // 10. Vérification du port public Spring Modulith CenterDirectoryPort
        TenantContext.runWithTenant(TenantId.of(center.getId()), () -> {
            CenterGroupSummary groupSummary = centerDirectoryPort.findGroupById(groupId).orElseThrow();
            assertThat(groupSummary.code()).isEqualTo("BAC-MATH-1");
            assertThat(groupSummary.activeEnrollmentCount()).isEqualTo(2);
            assertThat(centerDirectoryPort.isTeacherAssignedToGroup(tutorId, groupId)).isTrue();
            assertThat(centerDirectoryPort.findAssignedGroupIdsByTeacherId(tutorId)).containsExactly(groupId);

            StudentCenterSummary studentSummary = centerDirectoryPort.getStudentCenterSummary(
                    student1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
            assertThat(studentSummary.remainingPackSessions()).isEqualTo(1);
            assertThat(studentSummary.attendanceRatePercent()).isEqualByComparingTo(new BigDecimal("100.00"));

            TutorCenterWorkloadSummary workload = centerDirectoryPort.getTutorWorkloadSummary(
                    tutorId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
            assertThat(workload.completedMinutes()).isEqualTo(120);
            assertThat(workload.estimatedRemunerationXof()).isEqualTo(10_000L);
        });
    }

    @Test
    void shouldEnforceRoleMatrixAndFineGrainedTutorScope() throws Exception {
        School center = createSchool("RBAC", SchoolType.TUTORING_CENTER);
        createSubscription(center, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));

        String directorToken = tenantToken(center.getId(), RoleCode.DIRECTOR);
        String censorToken = tenantToken(center.getId(), RoleCode.CENSOR);
        String secretaryToken = tenantToken(center.getId(), RoleCode.SECRETARY);
        String promoterToken = tenantToken(center.getId(), RoleCode.PROMOTER);
        String cashierToken = tenantToken(center.getId(), RoleCode.CASHIER);
        String parentToken = tenantToken(center.getId(), RoleCode.PARENT);
        String studentToken = tenantToken(center.getId(), RoleCode.STUDENT);
        String platformToken = platformAdminToken();

        UUID yearId = createAcademicYearViaApi(directorToken, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(directorToken, yearId);
        UUID mathId = createSubjectViaApi(directorToken, "MATH", "Mathématiques", "SCIENTIFIC", 4);
        UUID student1 = createStudentViaApi(directorToken, "Oumar", "Sy", "MALE");
        UUID student2 = createStudentViaApi(directorToken, "Khadija", "Ba", "FEMALE");

        User tutorAUser = createTenantUser(center.getId(), "tutor-a@centre.sn", "Aliou", "Ndiaye", RoleCode.TUTOR);
        User tutorBUser = createTenantUser(center.getId(), "tutor-b@centre.sn", "Mariama", "Diallo", RoleCode.TUTOR);
        UUID tutorAId = createTeacherViaApi(directorToken, "TUT-A", tutorAUser.getId(), "Aliou", "Ndiaye", "TUTOR", "+221770001111", 4_000L);
        UUID tutorBId = createTeacherViaApi(directorToken, "TUT-B", tutorBUser.getId(), "Mariama", "Diallo", "TUTOR", "+221770002222", 4_500L);
        String tutorAToken = tenantTokenForUser(center.getId(), tutorAUser.getId(), RoleCode.TUTOR);

        UUID packId = createPackViaApi(directorToken, yearId, null, mathId, "PACK-MENSUEL", "Forfait mensuel", "MONTHLY_FLAT", null, 30, 25_000L);
        UUID group1 = createGroupViaApi(directorToken, yearId, null, mathId, packId, "GRP-1", "Groupe 1", "SMALL_GROUP", 10, "2026-10-01", "2027-06-30");
        UUID group2 = createGroupViaApi(directorToken, yearId, null, mathId, packId, "GRP-2", "Groupe 2", "SMALL_GROUP", 10, "2026-10-01", "2027-06-30");

        assignTutorViaApi(directorToken, group1, tutorAId, mathId, true, 4_000L);
        assignTutorViaApi(directorToken, group2, tutorBId, mathId, true, 4_500L);

        // Secrétaire inscrit les élèves et attribue un pack existant -> 201 ; mais ne peut pas créer un pack catalogue -> 403
        UUID spId = assignStudentPackViaApi(secretaryToken, student1, packId, group1, "2026-10-01");
        enrollStudentViaApi(secretaryToken, group1, student1, spId, "2026-10-02");
        enrollStudentViaApi(secretaryToken, group2, student2, null, "2026-10-02");

        mockMvc.perform(post("/api/v1/center/packs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "SEC-PACK", "name", "Interdit", "billingMode", "MONTHLY_FLAT", "priceXof", 10_000))))
                .andExpect(status().isForbidden());

        // Censeur crée des séances -> 201 ; ne peut pas créer un pack tarifaire -> 403
        UUID session1 = createSessionViaApi(censorToken, group1, tutorAId, mathId, "2026-10-14", "15:00", "17:00");
        UUID session2 = createSessionViaApi(censorToken, group2, tutorBId, mathId, "2026-10-14", "15:00", "17:00");
        mockMvc.perform(post("/api/v1/center/packs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(censorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "CEN-PACK", "name", "Interdit", "billingMode", "MONTHLY_FLAT", "priceXof", 10_000))))
                .andExpect(status().isForbidden());

        // Promoteur et Caissier : lecture seule (200), écriture refusée (403)
        mockMvc.perform(get("/api/v1/center/groups").header(HttpHeaders.AUTHORIZATION, bearer(promoterToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/center/student-packs").header(HttpHeaders.AUTHORIZATION, bearer(cashierToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/center/groups/" + group1 + "/activate").header(HttpHeaders.AUTHORIZATION, bearer(promoterToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/center/groups/" + group1 + "/activate").header(HttpHeaders.AUTHORIZATION, bearer(cashierToken)))
                .andExpect(status().isForbidden());

        // ADMIN_SAAS, PARENT, STUDENT -> 403
        mockMvc.perform(get("/api/v1/center/groups").header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/center/groups").header(HttpHeaders.AUTHORIZATION, bearer(parentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/center/groups").header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden());

        // Restriction fine TUTOR A : voit uniquement group1 et session1 ; interdit sur group2, session2, tutorB, student2 et student-packs
        mockMvc.perform(get("/api/v1/center/groups").header(HttpHeaders.AUTHORIZATION, bearer(tutorAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(group1.toString()));

        mockMvc.perform(get("/api/v1/center/groups/" + group2).header(HttpHeaders.AUTHORIZATION, bearer(tutorAToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:center-tutor-scope-forbidden"));

        mockMvc.perform(get("/api/v1/center/sessions/" + session2).header(HttpHeaders.AUTHORIZATION, bearer(tutorAToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:center-tutor-scope-forbidden"));

        mockMvc.perform(put("/api/v1/center/sessions/" + session2 + "/attendance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("records", List.of(Map.of("studentId", student2, "status", "PRESENT"))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:center-tutor-scope-forbidden"));

        mockMvc.perform(get("/api/v1/center/tutors/" + tutorBId + "/workload")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorAToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:center-tutor-scope-forbidden"));

        mockMvc.perform(get("/api/v1/center/students/" + student2 + "/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorAToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:center-tutor-scope-forbidden"));

        mockMvc.perform(get("/api/v1/center/student-packs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorAToken)))
                .andExpect(status().isForbidden());

        // Dans son périmètre (group1 / session1 / student1 / tutorA) : autorisé
        mockMvc.perform(get("/api/v1/center/sessions/" + session1).header(HttpHeaders.AUTHORIZATION, bearer(tutorAToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/center/students/" + student1 + "/overview").header(HttpHeaders.AUTHORIZATION, bearer(tutorAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packs.length()").value(0));
        mockMvc.perform(get("/api/v1/center/tutors/" + tutorAId + "/workload").header(HttpHeaders.AUTHORIZATION, bearer(tutorAToken)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldEnforceSubscriptionAccessGuardForExpiredAndSuspendedCenters() throws Exception {
        School expiredCenter = createSchool("EXP", SchoolType.TUTORING_CENTER);
        createSubscription(expiredCenter, SubscriptionStatus.ACTIVE, Instant.now().minus(1, ChronoUnit.DAYS));
        String expiredToken = tenantToken(expiredCenter.getId(), RoleCode.CENTER_MANAGER);

        // EXPIRED -> lecture autorisée (200), écriture refusée (403)
        mockMvc.perform(get("/api/v1/center/groups").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/center/packs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "PACK-EXP", "name", "Pack", "billingMode", "MONTHLY_FLAT", "priceXof", 15_000))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-access-denied"));

        School suspendedCenter = createSchool("SUS", SchoolType.TUTORING_CENTER);
        createSubscription(suspendedCenter, SubscriptionStatus.SUSPENDED, Instant.now().plus(30, ChronoUnit.DAYS));
        String suspendedToken = tenantToken(suspendedCenter.getId(), RoleCode.CENTER_MANAGER);

        // SUSPENDED -> lecture et écriture refusées (403)
        mockMvc.perform(get("/api/v1/center/groups").header(HttpHeaders.AUTHORIZATION, bearer(suspendedToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-access-denied"));
    }

    @Test
    void shouldStrictlyIsolateTenantsAcrossApiAndPostgresRlsForAllCenterTables() throws Exception {
        School centerA = createSchool("ISOA", SchoolType.TUTORING_CENTER);
        School centerB = createSchool("ISOB", SchoolType.TUTORING_CENTER);
        createSubscription(centerA, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));
        createSubscription(centerB, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));

        String tokenA = tenantToken(centerA.getId(), RoleCode.CENTER_MANAGER);
        String tokenB = tenantToken(centerB.getId(), RoleCode.CENTER_MANAGER);

        CenterFixture fixtureA = seedCenterFixture(centerA, tokenA);
        CenterFixture fixtureB = seedCenterFixture(centerB, tokenB);

        // Tentative d'accès cross-tenant de A vers les ressources de B (avec en-tête et query forgés) -> 404
        mockMvc.perform(get("/api/v1/center/packs/" + fixtureB.packId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("X-Tenant-Id", centerB.getId().toString())
                        .param("tenantId", centerB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/center/student-packs/" + fixtureB.studentPackId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("X-Tenant-Id", centerB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/center/groups/" + fixtureB.groupId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("X-Tenant-Id", centerB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/center/sessions/" + fixtureB.sessionId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("X-Tenant-Id", centerB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/center/students/" + fixtureB.studentId() + "/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/center/tutors/" + fixtureB.teacherId() + "/workload")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isNotFound());

        // Vérification directe PostgreSQL RLS (fail-closed) sous le rôle applicatif ecole_app sur les 8 tables V9
        List<String> centerTables = List.of(
                "center_session_packs",
                "center_groups",
                "center_student_packs",
                "center_group_tutors",
                "center_group_enrollments",
                "center_schedule_slots",
                "center_sessions",
                "center_session_attendances");

        // 1. Sans TenantContext -> 0 ligne visible (fail-closed)
        TenantContext.clear();
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            for (String table : centerTables) {
                Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
                assertThat(count).as("Fail-closed sans tenant sur " + table).isZero();
            }
        });

        // 2. Avec TenantContext(A) -> lignes de A visibles, 0 ligne de B visible
        TenantContext.runWithTenant(TenantId.of(centerA.getId()), () -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            for (String table : centerTables) {
                Integer visibleA = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, centerA.getId());
                Integer visibleB = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, centerB.getId());
                assertThat(visibleA).as("Lignes tenant A visibles sur " + table).isGreaterThanOrEqualTo(1);
                assertThat(visibleB).as("Lignes tenant B invisibles sur " + table).isZero();
            }
        }));

        // 3. Tentative INSERT cross-tenant sous ecole_app -> bloquée par WITH CHECK RLS
        assertThatThrownBy(() -> TenantContext.runWithTenant(
                TenantId.of(centerA.getId()),
                () -> transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update(
                            "INSERT INTO center_session_packs (tenant_id, code, name, billing_mode, price_xof) "
                                    + "VALUES (?, 'EVIL-PACK', 'Cross Tenant', 'MONTHLY_FLAT', 1000)",
                            centerB.getId());
                })))
                .isInstanceOf(DataAccessException.class);

        assertThat(fixtureA.groupId()).isNotEqualTo(fixtureB.groupId());
    }

    @Test
    void shouldPreserveTutorRemunerationAfterDeactivationAndFallbackToTeacherHourlyRate() throws Exception {
        School center = createSchool("CTR-M10", SchoolType.TUTORING_CENTER);
        createSubscription(center, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));
        String managerToken = tenantToken(center.getId(), RoleCode.CENTER_MANAGER);

        UUID yearId = createAcademicYearViaApi(managerToken, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(managerToken, yearId);
        UUID mathId = createSubjectViaApi(managerToken, "MATH", "Mathématiques", "SCIENTIFIC", 4);
        UUID physId = createSubjectViaApi(managerToken, "PHYS", "Physique", "SCIENTIFIC", 4);

        // Intervenant 1 : taux spécifique sur cohorte (6 000 FCFA/h), clôture de séance de 2h (= 12 000 FCFA), puis désactivation de l'affectation
        UUID tutor1Id = createTeacherViaApi(
                managerToken, "TUT-M10-1", null, "Mamadou", "Seck", "TUTOR", "+221770099001", 4_000L);
        UUID group1Id = createGroupViaApi(
                managerToken, yearId, null, mathId, null, "COH-M10-1", "Cohorte M10-1", "SUBJECT_SUPPORT", 15, "2026-10-01", "2027-06-30");
        UUID assignment1Id = assignTutorViaApi(managerToken, group1Id, tutor1Id, mathId, true, 6_000L);
        UUID session1Id = createSessionViaApi(managerToken, group1Id, tutor1Id, mathId, "2026-10-10", "10:00", "12:00");
        mockMvc.perform(post("/api/v1/center/sessions/" + session1Id + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("topic", "Algèbre linéaire"))))
                .andExpect(status().isOk());

        // Désactiver l'affectation de tutor1 -> la rémunération historique doit rester exactement 12 000 FCFA (snapshot + historique)
        mockMvc.perform(post("/api/v1/center/groups/" + group1Id + "/tutors/" + assignment1Id + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Fin de mission"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/center/tutors/" + tutor1Id + "/workload")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workload.activeGroupCount").value(0))
                .andExpect(jsonPath("$.workload.completedSessionCount").value(1))
                .andExpect(jsonPath("$.workload.completedMinutes").value(120))
                .andExpect(jsonPath("$.workload.estimatedRemunerationXof").value(12_000));

        // Intervenant 2 : affectation sans hourlyRateXof -> repli sur Teacher.hourlyRateXof (5 000 FCFA/h) pour une séance de 90 min (= 7 500 FCFA)
        UUID tutor2Id = createTeacherViaApi(
                managerToken, "TUT-M10-2", null, "Fatou", "Gaye", "TUTOR", "+221770099002", 5_000L);
        UUID group2Id = createGroupViaApi(
                managerToken, yearId, null, physId, null, "COH-M10-2", "Cohorte M10-2", "SUBJECT_SUPPORT", 15, "2026-10-01", "2027-06-30");
        assignTutorViaApi(managerToken, group2Id, tutor2Id, physId, true, null);
        UUID session2Id = createSessionViaApi(managerToken, group2Id, tutor2Id, physId, "2026-10-11", "14:00", "15:30");
        mockMvc.perform(post("/api/v1/center/sessions/" + session2Id + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("topic", "Mécanique"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/center/tutors/" + tutor2Id + "/workload")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workload.completedSessionCount").value(1))
                .andExpect(jsonPath("$.workload.completedMinutes").value(90))
                .andExpect(jsonPath("$.workload.estimatedRemunerationXof").value(7_500));
    }

    private record CenterFixture(
            UUID packId,
            UUID studentPackId,
            UUID groupId,
            UUID teacherId,
            UUID studentId,
            UUID slotId,
            UUID sessionId) {
    }

    private CenterFixture seedCenterFixture(School center, String token) throws Exception {
        UUID yearId = createAcademicYearViaApi(token, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(token, yearId);
        UUID mathId = createSubjectViaApi(token, "MATH", "Mathématiques", "SCIENTIFIC", 4);
        UUID studentId = createStudentViaApi(token, "Awa", "Diop", "FEMALE");
        UUID teacherId = createTeacherViaApi(token, "TUT-1", null, "Cheikh", "Fall", "TUTOR", "+221770011223", 4_000L);
        UUID packId = createPackViaApi(token, yearId, null, mathId, "PACK-1", "Pack 4 séances", "SESSION_PACK", 4, 60, 25_000L);
        UUID groupId = createGroupViaApi(token, yearId, null, mathId, packId, "COH-1", "Cohorte 1", "SUBJECT_SUPPORT", 15, "2026-10-01", "2027-06-30");
        assignTutorViaApi(token, groupId, teacherId, mathId, true, 4_000L);
        UUID studentPackId = assignStudentPackViaApi(token, studentId, packId, groupId, "2026-10-01");
        enrollStudentViaApi(token, groupId, studentId, studentPackId, "2026-10-02");
        UUID slotId = createScheduleSlotViaApi(token, groupId, teacherId, mathId, "WEDNESDAY", "15:00", "17:00");
        UUID sessionId = createSessionViaApi(token, groupId, teacherId, mathId, "2026-10-07", "15:00", "17:00");
        mockMvc.perform(put("/api/v1/center/sessions/" + sessionId + "/attendance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("records", List.of(
                                Map.of("studentId", studentId, "status", "PRESENT", "participationRating", 5))))))
                .andExpect(status().isOk());
        return new CenterFixture(packId, studentPackId, groupId, teacherId, studentId, slotId, sessionId);
    }

    @Test
    void shouldBatchGenerateSessionsUpsertAttendanceCancelSessionAndCountActiveEnrollments() throws Exception {
        School center = createSchool("CTRM7", SchoolType.TUTORING_CENTER);
        createSubscription(center, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));
        String token = tenantToken(center.getId(), RoleCode.CENTER_MANAGER);

        UUID yearId = createAcademicYearViaApi(token, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(token, yearId);
        UUID levelId = createGradeLevelViaApi(token, "TS", "Terminale S", "TUTORING", 1);
        UUID mathId = createSubjectViaApi(token, "MATH", "Mathématiques", "SCIENTIFIC", 5);

        UUID teacher1 = createTeacherViaApi(token, "TUT-B1", null, "Ibrahima", "Sy", "TUTOR", "+221778880001", 6000L);
        UUID teacher2 = createTeacherViaApi(token, "TUT-B2", null, "Ndèye", "Kane", "TUTOR", "+221778880002", 5500L);

        UUID packId = createPackViaApi(
                token, yearId, levelId, mathId, "PK-10", "Carnet 10 séances", "SESSION_PACK", 10, 60, 40_000L);
        UUID group1 = createGroupViaApi(
                token, yearId, levelId, mathId, packId, "GRP-M7-1", "Groupe Maths 1", "SMALL_GROUP", 20, "2026-10-01", "2027-06-30");
        UUID group2 = createGroupViaApi(
                token, yearId, levelId, mathId, packId, "GRP-M7-2", "Groupe Maths 2", "SMALL_GROUP", 20, "2026-10-01", "2027-06-30");
        mockMvc.perform(post("/api/v1/center/groups/" + group1 + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/center/groups/" + group2 + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        assignTutorViaApi(token, group1, teacher1, mathId, true, 6000L);
        assignTutorViaApi(token, group1, teacher2, mathId, false, 5500L);

        UUID student1 = createStudentViaApi(token, "Awa", "Diop", "FEMALE");
        UUID student2 = createStudentViaApi(token, "Modou", "Fall", "MALE");
        UUID student3 = createStudentViaApi(token, "Khadija", "Seck", "FEMALE");

        // student1 : pack explicite sur inscription ; student2 : pack fallback lié à group1 ; student3 : pack fallback global
        UUID packS1 = assignStudentPackViaApi(token, student1, packId, group1, "2026-10-01");
        UUID packS2 = assignStudentPackViaApi(token, student2, packId, group1, "2026-10-01");
        UUID packS3 = assignStudentPackViaApi(token, student3, packId, null, "2026-10-01");

        enrollStudentViaApi(token, group1, student1, packS1, "2026-10-01");
        enrollStudentViaApi(token, group1, student2, null, "2026-10-01");
        enrollStudentViaApi(token, group1, student3, null, "2026-10-01");
        enrollStudentViaApi(token, group2, student1, null, "2026-10-01");

        // M6 : comptage groupé sur CenterDirectoryPort & liste des cohortes + intervenants groupés
        List<CenterGroupSummary> activeGroups = TenantContext.executeWithTenant(
                TenantId.of(center.getId()), () -> centerDirectoryPort.findActiveGroupsByAcademicYear(yearId));
        assertThat(activeGroups).hasSize(2);
        assertThat(activeGroups.stream().filter(g -> g.id().equals(group1)).findFirst().orElseThrow().activeEnrollmentCount())
                .isEqualTo(3L);
        assertThat(activeGroups.stream().filter(g -> g.id().equals(group2)).findFirst().orElseThrow().activeEnrollmentCount())
                .isEqualTo(1L);

        mockMvc.perform(get("/api/v1/center/groups/" + group1 + "/tutors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // M7 : génération de séances en lot (2 mercredis) + idempotence skippedCount + détection conflit intervenant
        UUID slot1Id = createScheduleSlotViaApi(token, group1, teacher1, mathId, "WEDNESDAY", "15:00", "17:00");
        MvcResult gen1 = mockMvc.perform(post("/api/v1/center/groups/" + group1 + "/sessions/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fromDate", "2026-10-01", "toDate", "2026-10-15"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(2))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andReturn();
        UUID sessionId = UUID.fromString(readJson(gen1).get("sessions").get(0).get("id").asString());

        mockMvc.perform(post("/api/v1/center/groups/" + group1 + "/sessions/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fromDate", "2026-10-01", "toDate", "2026-10-15"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.skippedCount").value(2));

        // Désactiver le créneau de group1 afin de créer un créneau chevauchant sur group2 et tester le conflit de séances en lot
        mockMvc.perform(post("/api/v1/center/schedule-slots/" + slot1Id + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        createScheduleSlotViaApi(token, group2, teacher1, mathId, "WEDNESDAY", "16:00", "18:00");
        mockMvc.perform(post("/api/v1/center/groups/" + group2 + "/sessions/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fromDate", "2026-10-01", "toDate", "2026-10-15"))))
                .andExpect(status().isConflict());

        // M7 : upsertAttendance en lot sur les 3 élèves (verrouillage groupé + consommation des 3 packs)
        mockMvc.perform(put("/api/v1/center/sessions/" + sessionId + "/attendance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("records", List.of(
                                Map.of("studentId", student1, "status", "PRESENT", "participationRating", 5),
                                Map.of("studentId", student2, "status", "PRESENT", "participationRating", 4),
                                Map.of("studentId", student3, "status", "LATE", "lateMinutes", 10))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendances.length()").value(3));

        for (UUID spId : List.of(packS1, packS2, packS3)) {
            mockMvc.perform(get("/api/v1/center/student-packs/" + spId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.consumedSessions").value(1));
        }

        // Passage de student2 à EXCUSED -> restitution de son crédit en lot
        mockMvc.perform(put("/api/v1/center/sessions/" + sessionId + "/attendance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("records", List.of(
                                Map.of("studentId", student2, "status", "EXCUSED"))))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/center/student-packs/" + packS2)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumedSessions").value(0));

        // M7 : cancelSession -> restitution groupée des 2 packs encore consommés (student1 et student3)
        mockMvc.perform(post("/api/v1/center/sessions/" + sessionId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Salle indisponible"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        for (UUID spId : List.of(packS1, packS2, packS3)) {
            mockMvc.perform(get("/api/v1/center/student-packs/" + spId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.consumedSessions").value(0));
        }
    }

    // --- Helpers ---

    private School createSchool(String prefix, SchoolType type) {
        String code = prefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return schoolService.createSchool(
                code, "Centre " + code, type, "+221770000000", "contact@" + code.toLowerCase() + ".sn", "Dakar");
    }

    private void createSubscription(School school, SubscriptionStatus status, Instant endsAt) {
        SubscriptionPlan plan = subscriptionService.createPlan(
                "PLAN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                "Forfait Centre",
                20_000L,
                200_000L,
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

    private UUID createGradeLevelViaApi(String token, String code, String name, String cycle, int order) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/academic/grade-levels")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "name", name, "cycle", cycle, "displayOrder", order))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createSubjectViaApi(String token, String code, String name, String category, int defaultCoef) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/academic/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "name", name, "category", category, "defaultCoefficient", defaultCoef))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createStudentViaApi(String token, String firstName, String lastName, String gender) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", firstName,
                                "lastName", lastName,
                                "gender", gender,
                                "birthDate", "2009-05-12",
                                "admissionDate", "2026-10-01"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createTeacherViaApi(
            String token, String code, UUID userId, String firstName, String lastName, String type, String phone, Long hourlyRateXof)
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

    private UUID createPackViaApi(
            String token,
            UUID yearId,
            UUID levelId,
            UUID subjectId,
            String code,
            String name,
            String billingMode,
            Integer includedSessions,
            Integer durationDays,
            long priceXof)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (yearId != null) {
            body.put("academicYearId", yearId);
        }
        if (levelId != null) {
            body.put("gradeLevelId", levelId);
        }
        if (subjectId != null) {
            body.put("subjectId", subjectId);
        }
        body.put("code", code);
        body.put("name", name);
        body.put("billingMode", billingMode);
        if (includedSessions != null) {
            body.put("includedSessions", includedSessions);
        }
        if (durationDays != null) {
            body.put("durationDays", durationDays);
        }
        body.put("priceXof", priceXof);
        MvcResult res = mockMvc.perform(post("/api/v1/center/packs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createGroupViaApi(
            String token,
            UUID yearId,
            UUID levelId,
            UUID subjectId,
            UUID defaultPackId,
            String code,
            String name,
            String groupType,
            int capacity,
            String startDate,
            String endDate)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("academicYearId", yearId);
        if (levelId != null) {
            body.put("gradeLevelId", levelId);
        }
        if (subjectId != null) {
            body.put("subjectId", subjectId);
        }
        if (defaultPackId != null) {
            body.put("defaultPackId", defaultPackId);
        }
        body.put("code", code);
        body.put("name", name);
        body.put("groupType", groupType);
        body.put("roomName", "Salle R1");
        body.put("capacity", capacity);
        body.put("startDate", startDate);
        body.put("endDate", endDate);
        MvcResult res = mockMvc.perform(post("/api/v1/center/groups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID assignTutorViaApi(
            String token, UUID groupId, UUID teacherId, UUID subjectId, boolean leadTutor, Long hourlyRateXof)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("teacherId", teacherId);
        if (subjectId != null) {
            body.put("subjectId", subjectId);
        }
        body.put("leadTutor", leadTutor);
        if (hourlyRateXof != null) {
            body.put("hourlyRateXof", hourlyRateXof);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/center/groups/" + groupId + "/tutors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID assignStudentPackViaApi(String token, UUID studentId, UUID packId, UUID groupId, String startDate)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("studentId", studentId);
        body.put("packId", packId);
        if (groupId != null) {
            body.put("groupId", groupId);
        }
        body.put("startDate", startDate);
        MvcResult res = mockMvc.perform(post("/api/v1/center/student-packs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID enrollStudentViaApi(String token, UUID groupId, UUID studentId, UUID studentPackId, String enrolledAt)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("studentId", studentId);
        if (studentPackId != null) {
            body.put("studentPackId", studentPackId);
        }
        body.put("enrolledAt", enrolledAt);
        MvcResult res = mockMvc.perform(post("/api/v1/center/groups/" + groupId + "/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createScheduleSlotViaApi(
            String token, UUID groupId, UUID teacherId, UUID subjectId, String dayOfWeek, String startTime, String endTime)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (teacherId != null) {
            body.put("teacherId", teacherId);
        }
        if (subjectId != null) {
            body.put("subjectId", subjectId);
        }
        body.put("dayOfWeek", dayOfWeek);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        MvcResult res = mockMvc.perform(post("/api/v1/center/groups/" + groupId + "/schedule-slots")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createSessionViaApi(
            String token, UUID groupId, UUID teacherId, UUID subjectId, String date, String startTime, String endTime)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupId", groupId);
        if (teacherId != null) {
            body.put("teacherId", teacherId);
        }
        if (subjectId != null) {
            body.put("subjectId", subjectId);
        }
        body.put("sessionDate", date);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        MvcResult res = mockMvc.perform(post("/api/v1/center/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
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
