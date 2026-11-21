package com.ecole.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecole.saas.family.FamilyDirectoryPort;
import com.ecole.saas.family.FamilySummary;
import com.ecole.saas.family.GuardianSummary;
import com.ecole.saas.family.StudentGuardianSummary;
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
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentEnrollmentSummary;
import com.ecole.saas.student.StudentSummary;
import com.ecole.saas.subscription.application.SubscriptionService;
import com.ecole.saas.subscription.domain.BillingCycle;
import com.ecole.saas.subscription.domain.SubscriptionPlan;
import com.ecole.saas.subscription.domain.SubscriptionStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class StudentFamilyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final SchoolService schoolService;
    private final SubscriptionService subscriptionService;
    private final UserAccountService userAccountService;
    private final StudentDirectoryPort studentDirectoryPort;
    private final FamilyDirectoryPort familyDirectoryPort;
    private final TokenIssuer tokenIssuer;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    StudentFamilyIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            SchoolService schoolService,
            SubscriptionService subscriptionService,
            UserAccountService userAccountService,
            StudentDirectoryPort studentDirectoryPort,
            FamilyDirectoryPort familyDirectoryPort,
            TokenIssuer tokenIssuer,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.schoolService = schoolService;
        this.subscriptionService = subscriptionService;
        this.userAccountService = userAccountService;
        this.studentDirectoryPort = studentDirectoryPort;
        this.familyDirectoryPort = familyDirectoryPort;
        this.tokenIssuer = tokenIssuer;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldManageStudentsSequentialMatriculesEnrollmentsAndExposePublicPort() throws Exception {
        School school = createSchool("STU-FLOW", SchoolType.PRIVATE_SCHOOL);
        createActiveSubscription(school, 50);
        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);
        String secretaryToken = tenantToken(school.getId(), RoleCode.SECRETARY);

        // 1. Structure académique : 2 années, 1 niveau, 3 classes (dont une de capacité 1 et une archivée)
        UUID year2026Id = createAcademicYearViaApi(
                directorToken, "2026-2027", "Année 2026-2027", "2026-10-01", "2027-07-31");
        mockMvc.perform(post("/api/v1/academic/years/" + year2026Id + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("makeCurrent", true))))
                .andExpect(status().isOk());

        UUID year2027Id = createAcademicYearViaApi(
                directorToken, "2027-2028", "Année 2027-2028", "2027-10-01", "2028-07-31");
        UUID level6eId = createGradeLevelViaApi(directorToken, "6E", "Sixième", "MIDDLE_SCHOOL", 1);

        UUID class6eAId = createClassViaApi(directorToken, year2026Id, level6eId, "6E-A", "6ème A", 1);
        UUID class6eBId = createClassViaApi(directorToken, year2026Id, level6eId, "6E-B", "6ème B", 2);
        UUID classArchivedId = createClassViaApi(directorToken, year2026Id, level6eId, "6E-ARC", "6ème Archivée", 10);
        mockMvc.perform(post("/api/v1/academic/classes/" + classArchivedId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk());
        UUID classNextYearId = createClassViaApi(directorToken, year2027Id, level6eId, "6E-A", "6ème A 2027", 30);

        // 2. Création de 3 élèves : matricules séquentiels par année d'admission (2026 -> 0001, 0002 ; 2027 -> 0001)
        JsonNode s1 = createStudentViaApi(
                secretaryToken, "Awa", "Diop", "FEMALE", "2013-05-10", "+221771001001", "2026-10-01");
        UUID student1Id = UUID.fromString(s1.get("id").asString());
        assertThat(s1.get("matricule").asString()).isEqualTo(school.getCode() + "-2026-0001");

        JsonNode s2 = createStudentViaApi(
                secretaryToken, "Moussa", "Fall", "MALE", "2013-08-21", null, "2026-10-02");
        UUID student2Id = UUID.fromString(s2.get("id").asString());
        assertThat(s2.get("matricule").asString()).isEqualTo(school.getCode() + "-2026-0002");

        JsonNode s3 = createStudentViaApi(
                secretaryToken, "Khadija", "Ndiaye", "FEMALE", "2014-01-12", null, "2027-01-15");
        assertThat(s3.get("matricule").asString()).isEqualTo(school.getCode() + "-2027-0001");

        // Recherche par matricule et filtrage paginé
        mockMvc.perform(get("/api/v1/students/by-matricule/" + school.getCode().toLowerCase() + "-2026-0001")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(student1Id.toString()))
                .andExpect(jsonPath("$.firstName").value("Awa"));

        mockMvc.perform(get("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .param("search", "Diop")
                        .param("gender", "FEMALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(student1Id.toString()));

        // Mise à jour du dossier (matricule reste immuable)
        mockMvc.perform(put("/api/v1/students/" + student1Id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Awa Marie",
                                "lastName", "Diop",
                                "gender", "FEMALE",
                                "birthDate", "2013-05-10",
                                "birthPlace", "Dakar",
                                "nationality", "Sénégalaise",
                                "address", "Sacré-Cœur",
                                "admissionDate", "2026-10-01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Awa Marie"))
                .andExpect(jsonPath("$.matricule").value(school.getCode() + "-2026-0001"));

        // 3. Inscription de student1 dans 6E-A (capacité = 1)
        UUID enrollment1Id = enrollStudentViaApi(
                secretaryToken, student1Id, year2026Id, class6eAId, "NEW_ADMISSION", "2026-10-05");

        // Doublon d'inscription active pour la même année -> 409
        mockMvc.perform(post("/api/v1/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", student1Id,
                                "academicYearId", year2026Id,
                                "classRoomId", class6eBId,
                                "enrollmentType", "RE_ENROLLMENT"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:student-already-enrolled"));

        // Capacité atteinte sur 6E-A (1/1) pour student2 -> 409
        mockMvc.perform(post("/api/v1/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", student2Id,
                                "academicYearId", year2026Id,
                                "classRoomId", class6eAId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:classroom-capacity-exceeded"));

        // Classe archivée -> 409
        mockMvc.perform(post("/api/v1/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", student2Id,
                                "academicYearId", year2026Id,
                                "classRoomId", classArchivedId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:invalid-enrollment-state"));

        // Incohérence classe (2027) vs année demandée (2026) -> 400
        mockMvc.perform(post("/api/v1/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", student2Id,
                                "academicYearId", year2026Id,
                                "classRoomId", classNextYearId))))
                .andExpect(status().isBadRequest());

        // Changement de classe de student1 : 6E-A -> 6E-B libère la place en 6E-A
        mockMvc.perform(post("/api/v1/enrollments/" + enrollment1Id + "/transfer-class")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "targetClassRoomId", class6eBId,
                                "reason", "Rééquilibrage des effectifs"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classRoomId").value(class6eBId.toString()))
                .andExpect(jsonPath("$.classRoomCode").value("6E-B"));

        // student2 peut maintenant s'inscrire dans 6E-A
        UUID enrollment2Id = enrollStudentViaApi(
                secretaryToken, student2Id, year2026Id, class6eAId, "NEW_ADMISSION", "2026-10-06");

        // Vérification du port public StudentDirectoryPort
        TenantContext.runWithTenant(TenantId.of(school.getId()), () -> {
            StudentSummary summary = studentDirectoryPort.findStudentById(student1Id).orElseThrow();
            assertThat(summary.firstName()).isEqualTo("Awa Marie");
            assertThat(summary.active()).isTrue();
            assertThat(studentDirectoryPort.countActiveStudents()).isEqualTo(3L);

            StudentEnrollmentSummary current =
                    studentDirectoryPort.findCurrentEnrollmentForStudent(student1Id).orElseThrow();
            assertThat(current.id()).isEqualTo(enrollment1Id);
            assertThat(current.classRoomId()).isEqualTo(class6eBId);

            List<StudentEnrollmentSummary> classAStudents =
                    studentDirectoryPort.findActiveEnrollmentsByClassRoom(class6eAId);
            assertThat(classAStudents).extracting(StudentEnrollmentSummary::studentId).containsExactly(student2Id);
        });

        // Historique des inscriptions d'un élève + retrait + clôture d'année bloquant toute nouvelle mutation
        mockMvc.perform(get("/api/v1/students/" + student1Id + "/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(post("/api/v1/enrollments/" + enrollment2Id + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("endDate", "2026-11-10", "reason", "Déménagement"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        mockMvc.perform(post("/api/v1/academic/years/" + year2026Id + "/close")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/enrollments/" + enrollment1Id + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Fin d'année"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:invalid-enrollment-state"));
    }

    @Test
    void shouldEnforceSubscriptionStateQuotasAndReportLiveStudentUsage() throws Exception {
        // 1. Établissement sans abonnement -> lecture et écriture bloquées (403)
        School schoolNoSub = createSchool("STU-NOSUB", SchoolType.TUTORING_CENTER);
        String noSubDirector = tenantToken(schoolNoSub.getId(), RoleCode.DIRECTOR);

        mockMvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(noSubDirector)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-access-denied"));

        mockMvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(noSubDirector))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Test",
                                "lastName", "SansSub",
                                "gender", "MALE",
                                "birthDate", "2012-01-01"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-access-denied"));

        // 2. Établissement avec quota maxStudents = 2
        School schoolQuota = createSchool("STU-QUOTA", SchoolType.PRIVATE_SCHOOL);
        createActiveSubscription(schoolQuota, 2);
        String directorToken = tenantToken(schoolQuota.getId(), RoleCode.DIRECTOR);

        JsonNode s1 = createStudentViaApi(directorToken, "Oumar", "Sy", "MALE", "2012-03-01", null, "2026-10-01");
        UUID student1Id = UUID.fromString(s1.get("id").asString());
        createStudentViaApi(directorToken, "Fatou", "Ba", "FEMALE", "2012-04-02", null, "2026-10-01");

        // Vérification que GET /api/v1/subscriptions/current/quotas reflète l'usage réel (2 / 2)
        JsonNode quotasFull = readJson(mockMvc.perform(get("/api/v1/subscriptions/current/quotas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode studentsQuotaFull = findQuotaItem(quotasFull, "STUDENTS");
        assertThat(studentsQuotaFull.get("effectiveLimit").asInt()).isEqualTo(2);
        assertThat(studentsQuotaFull.get("currentUsage").asLong()).isEqualTo(2L);
        assertThat(studentsQuotaFull.get("remaining").asLong()).isEqualTo(0L);
        assertThat(studentsQuotaFull.get("withinLimit").asBoolean()).isTrue();

        // 3e élève au-delà du quota -> 409 subscription-quota-exceeded
        mockMvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Aminata",
                                "lastName", "Sarr",
                                "gender", "FEMALE",
                                "birthDate", "2012-06-15"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-quota-exceeded"));

        // Transfert du 1er élève -> libère 1 place dans le quota actif (usage = 1, remaining = 1)
        mockMvc.perform(post("/api/v1/students/" + student1Id + "/transfer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Changement de ville"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRANSFERRED"));

        JsonNode quotasAfterTransfer = readJson(mockMvc.perform(get("/api/v1/subscriptions/current/quotas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode studentsQuotaAfterTransfer = findQuotaItem(quotasAfterTransfer, "STUDENTS");
        assertThat(studentsQuotaAfterTransfer.get("currentUsage").asLong()).isEqualTo(1L);
        assertThat(studentsQuotaAfterTransfer.get("remaining").asLong()).isEqualTo(1L);

        // Un nouvel élève peut maintenant être créé (usage = 2)
        createStudentViaApi(directorToken, "Aminata", "Sarr", "FEMALE", "2012-06-15", null, "2026-10-03");

        // Réactivation du 1er élève bloquée par le quota (2 / 2) -> 409
        mockMvc.perform(post("/api/v1/students/" + student1Id + "/reactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-quota-exceeded"));

        // 3. Abonnement EXPIRED : lecture seule autorisée (200), écriture refusée (403)
        School schoolExpired = createSchool("STU-EXP", SchoolType.PRIVATE_SCHOOL);
        createSubscriptionWithStatus(
                schoolExpired,
                SubscriptionStatus.ACTIVE,
                Instant.now().minus(60, ChronoUnit.DAYS),
                Instant.now().minus(2, ChronoUnit.DAYS),
                50);
        String expiredDirector = tenantToken(schoolExpired.getId(), RoleCode.DIRECTOR);

        mockMvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(expiredDirector)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/families").header(HttpHeaders.AUTHORIZATION, bearer(expiredDirector)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredDirector))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Bloqué",
                                "lastName", "Expiré",
                                "gender", "MALE",
                                "birthDate", "2013-01-01"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-access-denied"));

        mockMvc.perform(post("/api/v1/families")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredDirector))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Famille Bloquée"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-access-denied"));

        // 4. Abonnement SUSPENDED : lecture et écriture bloquées (403)
        School schoolSuspended = createSchool("STU-SUSP", SchoolType.TUTORING_CENTER);
        createSubscriptionWithStatus(
                schoolSuspended,
                SubscriptionStatus.SUSPENDED,
                Instant.now().minus(10, ChronoUnit.DAYS),
                Instant.now().plus(20, ChronoUnit.DAYS),
                50);
        String suspendedDirector = tenantToken(schoolSuspended.getId(), RoleCode.DIRECTOR);

        mockMvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(suspendedDirector)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-access-denied"));
        mockMvc.perform(get("/api/v1/guardians").header(HttpHeaders.AUTHORIZATION, bearer(suspendedDirector)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-access-denied"));
    }

    @Test
    void shouldManageFamiliesGuardiansStudentLinksAndExposePublicPort() throws Exception {
        School school = createSchool("FAM-FLOW", SchoolType.PRIVATE_SCHOOL);
        createActiveSubscription(school, 50);
        String secretaryToken = tenantToken(school.getId(), RoleCode.SECRETARY);

        // 1. Création de 2 élèves (fratrie) et 1 famille
        UUID student1Id = UUID.fromString(createStudentViaApi(
                secretaryToken, "Cheikh", "Diallo", "MALE", "2012-02-10", null, "2026-10-01").get("id").asString());
        UUID student2Id = UUID.fromString(createStudentViaApi(
                secretaryToken, "Mariama", "Diallo", "FEMALE", "2014-07-18", null, "2026-10-01").get("id").asString());

        UUID familyId = createFamilyViaApi(secretaryToken, "FAM-DIALLO", "Famille Diallo", "+221771234000", "Dakar");

        // Doublon de code famille -> 409
        mockMvc.perform(post("/api/v1/families")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "fam-diallo", "name", "Doublon"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:family-code-already-exists"));

        // 2. Création des deux parents dans la famille
        UUID fatherId = createGuardianViaApi(
                secretaryToken, familyId, "Mamadou", "Diallo", "+221771234001", "mamadou@diallo.sn");
        UUID motherId = createGuardianViaApi(
                secretaryToken, familyId, "Aïssatou", "Diallo", "+221771234002", "aissatou@diallo.sn");

        // Téléphone invalide (hors E.164) -> 400
        mockMvc.perform(post("/api/v1/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Invalide",
                                "lastName", "Phone",
                                "phone", "771234009"))))
                .andExpect(status().isBadRequest());

        // 3. Rattachement père (primary + financial) puis mère (devient primary + financial -> bascule atomique!)
        UUID fatherLinkId = linkGuardianViaApi(
                secretaryToken, student1Id, fatherId, "FATHER", true, true);
        UUID motherLinkId = linkGuardianViaApi(
                secretaryToken, student1Id, motherId, "MOTHER", true, true);
        linkGuardianViaApi(secretaryToken, student2Id, fatherId, "FATHER", true, true);

        // Doublon de lien élève-tuteur -> 409
        mockMvc.perform(post("/api/v1/students/" + student1Id + "/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("guardianId", fatherId, "relationshipType", "FATHER"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:student-guardian-link-already-exists"));

        // Vérification : mother est désormais l'unique primaryContact et financialResponsible pour student1
        JsonNode linksPage = readJson(mockMvc.perform(get("/api/v1/students/" + student1Id + "/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn());
        JsonNode fatherLinkNode = findContentById(linksPage, fatherLinkId);
        JsonNode motherLinkNode = findContentById(linksPage, motherLinkId);
        assertThat(fatherLinkNode.get("primaryContact").asBoolean()).isFalse();
        assertThat(fatherLinkNode.get("financialResponsible").asBoolean()).isFalse();
        assertThat(motherLinkNode.get("primaryContact").asBoolean()).isTrue();
        assertThat(motherLinkNode.get("financialResponsible").asBoolean()).isTrue();

        // Vues consolidées famille -> tuteurs et famille -> élèves (2 enfants dédupliqués)
        mockMvc.perform(get("/api/v1/families/" + familyId + "/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/families/" + familyId + "/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/guardians/" + fatherId + "/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Vérification du port public FamilyDirectoryPort
        TenantContext.runWithTenant(TenantId.of(school.getId()), () -> {
            FamilySummary fam = familyDirectoryPort.findFamilyById(familyId).orElseThrow();
            assertThat(fam.code()).isEqualTo("FAM-DIALLO");
            GuardianSummary mom = familyDirectoryPort.findGuardianById(motherId).orElseThrow();
            assertThat(mom.phone()).isEqualTo("+221771234002");

            StudentGuardianSummary primary =
                    familyDirectoryPort.findPrimaryContactByStudentId(student1Id).orElseThrow();
            assertThat(primary.guardianId()).isEqualTo(motherId);

            StudentGuardianSummary financial =
                    familyDirectoryPort.findFinancialResponsibleByStudentId(student1Id).orElseThrow();
            assertThat(financial.guardianId()).isEqualTo(motherId);

            assertThat(familyDirectoryPort.findStudentIdsByFamilyId(familyId))
                    .containsExactlyInAnyOrder(student1Id, student2Id);
            assertThat(familyDirectoryPort.findStudentIdsByGuardianId(fatherId))
                    .containsExactlyInAnyOrder(student1Id, student2Id);
        });

        // Désactivation et réactivation de lien, archivage tuteur bloquant nouveau lien
        mockMvc.perform(post("/api/v1/students/" + student1Id + "/guardians/" + motherLinkId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.primaryContact").value(false));

        mockMvc.perform(post("/api/v1/guardians/" + motherId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(post("/api/v1/students/" + student1Id + "/guardians/" + motherLinkId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:guardian-archived"));
    }

    @Test
    void shouldEnforceRoleMatrixAndRejectUnauthorizedOrMfaPendingAccess() throws Exception {
        School school = createSchool("SF-RBAC", SchoolType.PRIVATE_SCHOOL);
        createActiveSubscription(school, 50);

        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);
        UUID studentId = UUID.fromString(createStudentViaApi(
                directorToken, "Samba", "Gueye", "MALE", "2013-01-01", null, "2026-10-01").get("id").asString());
        UUID familyId = createFamilyViaApi(directorToken, "FAM-RBAC", "Famille Gueye", "+221770001001", "Dakar");
        UUID guardianId = createGuardianViaApi(
                directorToken, familyId, "Ousmane", "Gueye", "+221770001002", null);

        // Non authentifié -> 401 RFC 9457
        mockMvc.perform(get("/api/v1/students"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // Jeton MFA_PENDING -> 403
        String mfaPending = tokenIssuer
                .issueMfaPendingToken(AccountRef.user(UUID.randomUUID(), school.getId()))
                .value();
        mockMvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(mfaPending)))
                .andExpect(status().isForbidden());

        // Rôles écriture (DIRECTOR, CENTER_MANAGER, SECRETARY) -> OK
        for (RoleCode writer : List.of(RoleCode.DIRECTOR, RoleCode.CENTER_MANAGER, RoleCode.SECRETARY)) {
            String token = tenantToken(school.getId(), writer);
            mockMvc.perform(get("/api/v1/students/" + studentId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/families")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "Famille " + writer.name()))))
                    .andExpect(status().isCreated());
        }

        // Rôles lecture seule (PROMOTER, CASHIER, CENSOR, TEACHER, TUTOR) -> GET 200, POST 403
        for (RoleCode reader : List.of(
                RoleCode.PROMOTER, RoleCode.CASHIER, RoleCode.CENSOR, RoleCode.TEACHER, RoleCode.TUTOR)) {
            String token = tenantToken(school.getId(), reader);
            mockMvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/enrollments").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/families").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/guardians").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/students")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "firstName", "Refusé",
                                    "lastName", reader.name(),
                                    "gender", "MALE",
                                    "birthDate", "2013-01-01"))))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

            mockMvc.perform(post("/api/v1/students/" + studentId + "/guardians")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("guardianId", guardianId, "relationshipType", "FATHER"))))
                    .andExpect(status().isForbidden());
        }

        // ADMIN_SAAS, PARENT, STUDENT -> 403 sur endpoints back-office
        String adminToken = platformAdminToken();
        mockMvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/families").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());

        for (RoleCode portalRole : List.of(RoleCode.PARENT, RoleCode.STUDENT)) {
            String token = tenantToken(school.getId(), portalRole);
            mockMvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/families").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/guardians").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void shouldEnforceStrictMultiTenantIsolationAcrossHttpAndPostgresqlRls() throws Exception {
        School schoolA = createSchool("ISO-SF-A", SchoolType.PRIVATE_SCHOOL);
        School schoolB = createSchool("ISO-SF-B", SchoolType.TUTORING_CENTER);
        createActiveSubscription(schoolA, 50);
        createActiveSubscription(schoolB, 50);

        String directorA = tenantToken(schoolA.getId(), RoleCode.DIRECTOR);
        String directorB = tenantToken(schoolB.getId(), RoleCode.DIRECTOR);

        // 1. Données symétriques dans A et B (matricules indépendants commençant à 0001 et même code famille)
        UUID yearA = createAcademicYearViaApi(directorA, "2026-2027", "Année A", "2026-10-01", "2027-07-31");
        UUID levelA = createGradeLevelViaApi(directorA, "6E", "Sixième", "MIDDLE_SCHOOL", 1);
        UUID classA = createClassViaApi(directorA, yearA, levelA, "6E-A", "6ème A", 30);
        JsonNode stuANode = createStudentViaApi(
                directorA, "Amadou", "Sow", "MALE", "2013-02-01", null, "2026-10-01");
        UUID studentA = UUID.fromString(stuANode.get("id").asString());
        assertThat(stuANode.get("matricule").asString()).isEqualTo(schoolA.getCode() + "-2026-0001");
        UUID enrollmentA = enrollStudentViaApi(directorA, studentA, yearA, classA, "NEW_ADMISSION", "2026-10-02");
        UUID familyA = createFamilyViaApi(directorA, "FAM-01", "Famille Sow A", "+221771110001", "Dakar");
        UUID guardianA = createGuardianViaApi(directorA, familyA, "Ibrahima", "Sow", "+221771110002", null);
        UUID linkA = linkGuardianViaApi(directorA, studentA, guardianA, "FATHER", true, true);

        UUID yearB = createAcademicYearViaApi(directorB, "2026-2027", "Année B", "2026-10-01", "2027-07-31");
        UUID levelB = createGradeLevelViaApi(directorB, "6E", "Sixième", "MIDDLE_SCHOOL", 1);
        UUID classB = createClassViaApi(directorB, yearB, levelB, "6E-A", "6ème A", 30);
        JsonNode stuBNode = createStudentViaApi(
                directorB, "Binta", "Kane", "FEMALE", "2013-04-01", null, "2026-10-01");
        UUID studentB = UUID.fromString(stuBNode.get("id").asString());
        assertThat(stuBNode.get("matricule").asString()).isEqualTo(schoolB.getCode() + "-2026-0001");
        UUID enrollmentB = enrollStudentViaApi(directorB, studentB, yearB, classB, "NEW_ADMISSION", "2026-10-02");
        UUID familyB = createFamilyViaApi(directorB, "FAM-01", "Famille Kane B", "+221772220001", "Thiès");
        UUID guardianB = createGuardianViaApi(directorB, familyB, "Moussa", "Kane", "+221772220002", null);
        UUID linkB = linkGuardianViaApi(directorB, studentB, guardianB, "FATHER", true, true);

        // 2. En-tête X-Tenant-Id / paramètre ?tenantId forgés par A ignorés ; ressources de B -> 404
        mockMvc.perform(get("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA))
                        .header("X-Tenant-Id", schoolB.getId().toString())
                        .param("tenantId", schoolB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(studentA.toString()));

        mockMvc.perform(get("/api/v1/students/" + studentB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/enrollments/" + enrollmentB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/families/" + familyB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/guardians/" + guardianB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/students/" + studentB + "/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA)))
                .andExpect(status().isNotFound());

        // 3. Tentatives de liaison cross-tenant -> 404
        mockMvc.perform(post("/api/v1/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", studentA,
                                "academicYearId", yearB,
                                "classRoomId", classB))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "familyId", familyB,
                                "firstName", "Cross",
                                "lastName", "Tenant",
                                "phone", "+221779999999"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/students/" + studentA + "/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("guardianId", guardianB, "relationshipType", "LEGAL_GUARDIAN"))))
                .andExpect(status().isNotFound());

        // 4. Vérification directe PostgreSQL RLS sous le rôle non-superuser ecole_app sur les 6 tables V6
        TenantId tenantA = TenantId.of(schoolA.getId());
        TenantId tenantB = TenantId.of(schoolB.getId());

        TenantContext.runWithTenant(tenantA, () -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM student_matricule_sequences", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForList("SELECT id FROM students", UUID.class))
                    .containsExactly(studentA);
            assertThat(jdbcTemplate.queryForList("SELECT id FROM student_enrollments", UUID.class))
                    .containsExactly(enrollmentA);
            assertThat(jdbcTemplate.queryForList("SELECT id FROM families", UUID.class))
                    .containsExactly(familyA);
            assertThat(jdbcTemplate.queryForList("SELECT id FROM guardians", UUID.class))
                    .containsExactly(guardianA);
            assertThat(jdbcTemplate.queryForList("SELECT id FROM student_guardians", UUID.class))
                    .containsExactly(linkA);

            assertThat(jdbcTemplate.update("UPDATE students SET first_name = 'Piraté' WHERE id = ?", studentB)).isZero();
            assertThat(jdbcTemplate.update("UPDATE student_enrollments SET notes = 'Piraté' WHERE id = ?", enrollmentB)).isZero();
            assertThat(jdbcTemplate.update("UPDATE families SET name = 'Piraté' WHERE id = ?", familyB)).isZero();
            assertThat(jdbcTemplate.update("UPDATE guardians SET first_name = 'Piraté' WHERE id = ?", guardianB)).isZero();
            assertThat(jdbcTemplate.update("UPDATE student_guardians SET notes = 'Piraté' WHERE id = ?", linkB)).isZero();
        }));

        // V12 : REVOKE DELETE pour ecole_app sur les tables à archivage logique du module student/family
        for (Map.Entry<String, UUID> target : List.of(
                Map.entry("student_guardians", linkB),
                Map.entry("guardians", guardianB),
                Map.entry("families", familyB),
                Map.entry("student_enrollments", enrollmentB),
                Map.entry("students", studentB))) {
            TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                        jdbcTemplate.update("DELETE FROM " + target.getKey() + " WHERE id = ?", target.getValue());
                    }))
                    .isInstanceOf(DataAccessException.class));
        }

        // Tentative d'INSERT cross-tenant bloquée par WITH CHECK
        TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update(
                            "INSERT INTO students (id, tenant_id, matricule, first_name, last_name, gender, birth_date, admission_date, status) "
                                    + "VALUES (?, ?, 'INJ-2026-0001', 'Inj', 'Test', 'MALE', DATE '2013-01-01', DATE '2026-10-01', 'ACTIVE')",
                            UUID.randomUUID(),
                            tenantB.value());
                }))
                .isInstanceOf(DataAccessException.class));

        TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update(
                            "INSERT INTO families (id, tenant_id, name, status) VALUES (?, ?, 'Inj Family', 'ACTIVE')",
                            UUID.randomUUID(),
                            tenantB.value());
                }))
                .isInstanceOf(DataAccessException.class));

        // Fail-closed sans TenantContext : 0 ligne visible sur les 6 tables V6
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM student_matricule_sequences", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM students", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM student_enrollments", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM families", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM guardians", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM student_guardians", Integer.class)).isZero();
        });
    }

    @Test
    void shouldRestrictTeacherScopeInFamilyModuleAndMaskNationalIdNumberByRole() throws Exception {
        School school = createSchool("FAM-SCOPE", SchoolType.PRIVATE_SCHOOL);
        createActiveSubscription(school, 50);
        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);
        String secretaryToken = tenantToken(school.getId(), RoleCode.SECRETARY);
        String cashierToken = tenantToken(school.getId(), RoleCode.CASHIER);
        String promoterToken = tenantToken(school.getId(), RoleCode.PROMOTER);

        UUID yearId = createAcademicYearViaApi(
                directorToken, "2026-2027", "Année 2026-2027", "2026-10-01", "2027-07-31");
        mockMvc.perform(post("/api/v1/academic/years/" + yearId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("makeCurrent", true))))
                .andExpect(status().isOk());
        UUID levelId = createGradeLevelViaApi(directorToken, "6E", "Sixième", "MIDDLE_SCHOOL", 1);
        UUID class1Id = createClassViaApi(directorToken, yearId, levelId, "6E-A", "Sixième A", 30);
        UUID class2Id = createClassViaApi(directorToken, yearId, levelId, "6E-B", "Sixième B", 30);

        UUID student1Id = UUID.fromString(createStudentViaApi(
                directorToken, "Aminata", "Sow", "FEMALE", "2014-03-10", null, "2026-10-01").get("id").asString());
        UUID student2Id = UUID.fromString(createStudentViaApi(
                directorToken, "Ibrahima", "Fall", "MALE", "2014-05-12", null, "2026-10-01").get("id").asString());
        enrollStudentViaApi(directorToken, student1Id, yearId, class1Id, "NEW_ADMISSION", "2026-10-01");
        enrollStudentViaApi(directorToken, student2Id, yearId, class2Id, "NEW_ADMISSION", "2026-10-01");

        UUID family1Id = createFamilyViaApi(directorToken, "FAM-SOW", "Famille Sow", "+221771110001", "Dakar");
        UUID family2Id = createFamilyViaApi(directorToken, "FAM-FALL", "Famille Fall", "+221771110002", "Thiès");

        // Créer les tuteurs avec CNI (nationalIdNumber)
        MvcResult g1Res = mockMvc.perform(post("/api/v1/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "familyId", family1Id,
                                "firstName", "Mamadou",
                                "lastName", "Sow",
                                "phone", "+221771110011",
                                "nationalIdNumber", "CNI-SN-1985-0001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nationalIdNumber").value("CNI-SN-1985-0001"))
                .andReturn();
        UUID guardian1Id = UUID.fromString(readJson(g1Res).get("id").asString());

        MvcResult g2Res = mockMvc.perform(post("/api/v1/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "familyId", family2Id,
                                "firstName", "Fatou",
                                "lastName", "Fall",
                                "phone", "+221771110022",
                                "nationalIdNumber", "CNI-SN-1987-0002"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID guardian2Id = UUID.fromString(readJson(g2Res).get("id").asString());

        linkGuardianViaApi(directorToken, student1Id, guardian1Id, "FATHER", true, true);
        linkGuardianViaApi(directorToken, student2Id, guardian2Id, "MOTHER", true, true);

        // Créer une matière sur class1Id, un compte utilisateur et un enseignant affecté uniquement à class1Id
        MvcResult subjectRes = mockMvc.perform(post("/api/v1/academic/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "MATH",
                                "name", "Mathématiques",
                                "category", "SCIENTIFIC",
                                "defaultCoefficient", 4))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID subjectId = UUID.fromString(readJson(subjectRes).get("id").asString());

        mockMvc.perform(post("/api/v1/academic/classes/" + class1Id + "/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("subjectId", subjectId, "coefficient", 4, "mandatory", true))))
                .andExpect(status().isCreated());

        User teacherUser = TenantContext.executeWithTenant(
                TenantId.of(school.getId()),
                () -> userAccountService.createUserWithRole(
                        "prof.diallo@ecole.sn",
                        "+221772223344",
                        "Cheikh",
                        "Diallo",
                        "hash-fictif-teacher",
                        RoleCode.TEACHER));
        UUID teacherUserId = teacherUser.getId();
        MvcResult teacherRes = mockMvc.perform(post("/api/v1/teachers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "employeeCode", "ENS-SCOPE-01",
                                "firstName", "Cheikh",
                                "lastName", "Diallo",
                                "teacherType", "PERMANENT",
                                "phone", "+221772223344",
                                "userId", teacherUserId))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID teacherId = UUID.fromString(readJson(teacherRes).get("id").asString());

        mockMvc.perform(post("/api/v1/teacher-assignments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "teacherId", teacherId,
                                "academicYearId", yearId,
                                "classRoomId", class1Id,
                                "subjectId", subjectId,
                                "homeroomTeacher", true,
                                "weeklyHours", 4))))
                .andExpect(status().isCreated());

        String teacherToken = tokenIssuer
                .issueAccessToken(AccountRef.user(teacherUserId, school.getId()), Set.of(RoleCode.TEACHER.name()))
                .value();

        // C1 — TEACHER ne voit que family1 et guardian1 dans les listes, et reçoit 403 sur family2 / guardian2 / student2
        mockMvc.perform(get("/api/v1/families").header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(family1Id.toString()));

        mockMvc.perform(get("/api/v1/families/" + family1Id).header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/families/" + family2Id).header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:family-scope-forbidden"));
        mockMvc.perform(get("/api/v1/families/" + family2Id + "/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/families/" + family2Id + "/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/guardians").header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(guardian1Id.toString()))
                .andExpect(jsonPath("$.content[0].nationalIdNumber").isEmpty());

        mockMvc.perform(get("/api/v1/guardians/" + guardian1Id).header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nationalIdNumber").isEmpty());
        mockMvc.perform(get("/api/v1/guardians/" + guardian2Id).header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:family-scope-forbidden"));
        mockMvc.perform(get("/api/v1/guardians/" + guardian2Id + "/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/students/" + student1Id + "/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/students/" + student2Id + "/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:family-scope-forbidden"));

        // C1 — nationalIdNumber visible uniquement pour DIRECTOR / CENTER_MANAGER / SECRETARY, masqué (null) pour PROMOTER / CASHIER / TEACHER
        mockMvc.perform(get("/api/v1/guardians/" + guardian1Id).header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nationalIdNumber").value("CNI-SN-1985-0001"));
        mockMvc.perform(get("/api/v1/guardians/" + guardian1Id).header(HttpHeaders.AUTHORIZATION, bearer(cashierToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nationalIdNumber").isEmpty());
        mockMvc.perform(get("/api/v1/guardians/" + guardian1Id).header(HttpHeaders.AUTHORIZATION, bearer(promoterToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nationalIdNumber").isEmpty());
        mockMvc.perform(get("/api/v1/families/" + family1Id + "/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nationalIdNumber").isEmpty());
    }

    @Test
    void shouldSerializeConcurrentEnrollmentsAgainstClassRoomCapacity() throws Exception {
        School school = createSchool("CAP-CONC", SchoolType.PRIVATE_SCHOOL);
        createActiveSubscription(school, 50);
        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);

        UUID yearId = createAcademicYearViaApi(
                directorToken, "2026-2027", "Année 2026-2027", "2026-10-01", "2027-07-31");
        UUID levelId = createGradeLevelViaApi(directorToken, "CP", "Cours Préparatoire", "PRIMARY", 1);
        UUID smallClassId = createClassViaApi(directorToken, yearId, levelId, "CP-1", "CP Unique", 1);

        UUID s1 = UUID.fromString(createStudentViaApi(
                directorToken, "Awa", "Ba", "FEMALE", "2019-01-10", null, "2026-10-01").get("id").asString());
        UUID s2 = UUID.fromString(createStudentViaApi(
                directorToken, "Modou", "Ba", "MALE", "2019-02-11", null, "2026-10-01").get("id").asString());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> f1 = pool.submit(() -> enrollConcurrently(directorToken, s1, yearId, smallClassId, ready, start));
            Future<Integer> f2 = pool.submit(() -> enrollConcurrently(directorToken, s2, yearId, smallClassId, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = List.of(f1.get(15, TimeUnit.SECONDS), f2.get(15, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);

            TenantContext.runWithTenant(TenantId.of(school.getId()), () ->
                    assertThat(studentDirectoryPort.findActiveEnrollmentsByClassRoom(smallClassId)).hasSize(1));
        } finally {
            pool.shutdownNow();
        }
    }

    private int enrollConcurrently(
            String token,
            UUID studentId,
            UUID yearId,
            UUID classId,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/v1/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", studentId,
                                "academicYearId", yearId,
                                "classRoomId", classId,
                                "enrollmentType", "NEW_ADMISSION",
                                "enrollmentDate", "2026-10-01"))))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private void createActiveSubscription(School school, int maxStudents) {
        createSubscriptionWithStatus(
                school,
                SubscriptionStatus.ACTIVE,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS),
                maxStudents);
    }

    private void createSubscriptionWithStatus(
            School school, SubscriptionStatus status, Instant startsAt, Instant endsAt, int maxStudents) {
        SubscriptionPlan plan = subscriptionService.createPlan(
                uniqueCode("PLAN"),
                "Forfait Test",
                25_000L,
                250_000L,
                maxStudents,
                20,
                2_048);
        TenantContext.runWithTenant(TenantId.of(school.getId()), () ->
                subscriptionService.createSubscription(plan.getId(), status, BillingCycle.MONTHLY, startsAt, endsAt));
    }

    private JsonNode createStudentViaApi(
            String token,
            String firstName,
            String lastName,
            String gender,
            String birthDate,
            String phone,
            String admissionDate) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("gender", gender);
        body.put("birthDate", birthDate);
        if (phone != null) {
            body.put("phone", phone);
        }
        if (admissionDate != null) {
            body.put("admissionDate", admissionDate);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(res);
    }

    private UUID enrollStudentViaApi(
            String token,
            UUID studentId,
            UUID academicYearId,
            UUID classRoomId,
            String enrollmentType,
            String enrollmentDate) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", studentId,
                                "academicYearId", academicYearId,
                                "classRoomId", classRoomId,
                                "enrollmentType", enrollmentType,
                                "enrollmentDate", enrollmentDate))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createFamilyViaApi(String token, String code, String name, String phone, String city) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (code != null) {
            body.put("code", code);
        }
        body.put("name", name);
        if (phone != null) {
            body.put("primaryPhone", phone);
        }
        if (city != null) {
            body.put("city", city);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/families")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createGuardianViaApi(
            String token, UUID familyId, String firstName, String lastName, String phone, String email) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (familyId != null) {
            body.put("familyId", familyId);
        }
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("phone", phone);
        if (email != null) {
            body.put("email", email);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID linkGuardianViaApi(
            String token,
            UUID studentId,
            UUID guardianId,
            String relationshipType,
            boolean primaryContact,
            boolean financialResponsible) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/students/" + studentId + "/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "guardianId", guardianId,
                                "relationshipType", relationshipType,
                                "primaryContact", primaryContact,
                                "financialResponsible", financialResponsible))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createAcademicYearViaApi(
            String token, String code, String label, String startDate, String endDate) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/academic/years")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", code,
                                "label", label,
                                "startDate", startDate,
                                "endDate", endDate))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createGradeLevelViaApi(
            String token, String code, String name, String cycle, int displayOrder) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/academic/grade-levels")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", code,
                                "name", name,
                                "cycle", cycle,
                                "displayOrder", displayOrder))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createClassViaApi(
            String token, UUID academicYearId, UUID gradeLevelId, String code, String name, int capacity)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/academic/classes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "academicYearId", academicYearId,
                                "gradeLevelId", gradeLevelId,
                                "code", code,
                                "name", name,
                                "capacity", capacity))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private School createSchool(String prefix, SchoolType type) {
        String code = uniqueCode(prefix);
        return schoolService.createSchool(
                code,
                "Établissement " + code,
                type,
                "+221770000000",
                "contact@" + code.toLowerCase() + ".sn",
                "Dakar");
    }

    private String platformAdminToken() {
        return tokenIssuer
                .issueAccessToken(AccountRef.platformAdmin(UUID.randomUUID()), Set.of(RoleCode.ADMIN_SAAS.name()))
                .value();
    }

    private String tenantToken(UUID schoolId, RoleCode role) {
        return tokenIssuer
                .issueAccessToken(AccountRef.user(UUID.randomUUID(), schoolId), Set.of(role.name()))
                .value();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static JsonNode findQuotaItem(JsonNode quotasOverview, String resource) {
        for (JsonNode item : quotasOverview.get("resources")) {
            if (resource.equals(item.get("resource").asString())) {
                return item;
            }
        }
        throw new AssertionError("Ressource de quota introuvable : " + resource);
    }

    private static JsonNode findContentById(JsonNode pageNode, UUID id) {
        String expected = id.toString();
        for (JsonNode item : pageNode.get("content")) {
            if (expected.equals(item.get("id").asString())) {
                return item;
            }
        }
        throw new AssertionError("Élément introuvable dans la page : " + expected);
    }
}
