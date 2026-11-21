package com.ecole.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecole.saas.identity.application.UserAccountService;
import com.ecole.saas.identity.domain.AccountRef;
import com.ecole.saas.identity.domain.RoleCode;
import com.ecole.saas.identity.domain.TokenIssuer;
import com.ecole.saas.identity.domain.User;
import com.ecole.saas.school.application.SchoolService;
import com.ecole.saas.school.domain.School;
import com.ecole.saas.school.domain.SchoolType;
import com.ecole.saas.schoolimport.ImportJobStatus;
import com.ecole.saas.schoolimport.ImportJobSummary;
import com.ecole.saas.schoolimport.SchoolImportDirectoryPort;
import com.ecole.saas.schoolimport.infrastructure.SecureTabularFileParserTest;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import com.ecole.saas.subscription.application.SubscriptionService;
import com.ecole.saas.subscription.domain.BillingCycle;
import com.ecole.saas.subscription.domain.SubscriptionPlan;
import com.ecole.saas.subscription.domain.SubscriptionStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
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
class SchoolImportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final SchoolService schoolService;
    private final SubscriptionService subscriptionService;
    private final UserAccountService userAccountService;
    private final SchoolImportDirectoryPort importDirectoryPort;
    private final TokenIssuer tokenIssuer;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SchoolImportIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            SchoolService schoolService,
            SubscriptionService subscriptionService,
            UserAccountService userAccountService,
            SchoolImportDirectoryPort importDirectoryPort,
            TokenIssuer tokenIssuer,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.schoolService = schoolService;
        this.subscriptionService = subscriptionService;
        this.userAccountService = userAccountService;
        this.importDirectoryPort = importDirectoryPort;
        this.tokenIssuer = tokenIssuer;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldRunDryRunPreviewAndExecuteAcrossAllSixImportTypesCsvAndXlsxWithAuditAndSummary() throws Exception {
        School school = createSchool("IMPA", SchoolType.PRIVATE_SCHOOL);
        createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 200, 100);

        User director = createTenantUser(school.getId(), "dir@impa.sn", "Mamadou", "Diop", RoleCode.DIRECTOR);
        String directorToken = tenantTokenForUser(school.getId(), director.getId(), RoleCode.DIRECTOR);

        UUID yearId = createAcademicYearViaApi(directorToken, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(directorToken, yearId);
        UUID periodId = createAndOpenPeriodViaApi(directorToken, yearId, "T1", "Trimestre 1", 1, "2026-10-01", "2026-12-20");
        UUID levelId = createGradeLevelViaApi(directorToken, "6E", "Sixième", "MIDDLE_SCHOOL", 1);
        UUID mathId = createSubjectViaApi(directorToken, "MATH", "Mathématiques", "SCIENTIFIC", 4);
        UUID classAId = createClassRoomViaApi(directorToken, yearId, levelId, "6E-A", "Sixième A", 30);
        UUID classBId = createClassRoomViaApi(directorToken, yearId, levelId, "6E-B", "Sixième B", 30);
        assignSubjectToClassViaApi(directorToken, classAId, mathId);

        // 0. Liste et téléchargement de modèle CSV
        mockMvc.perform(get("/api/v1/schoolimport/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));

        MvcResult tplDownload = mockMvc.perform(get("/api/v1/schoolimport/templates/STUDENTS/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(new String(tplDownload.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("first_name;last_name;gender");

        // 1. STUDENTS (CSV) : analyse à sec -> aucune création en base -> puis exécution
        String studentsCsv = "\uFEFFPrénom;Nom;Sexe;Date de naissance;Téléphone;Email;Notes médicales;Date d'admission;Code classe\n"
                + "Awa;Ndiaye;F;2014-05-12;+221771110001;awa@impa.sn;Asthme confidentiel;2026-10-01;6E-A\n"
                + "Moussa;Diop;M;14/06/2014;+221771110002;moussa@impa.sn;;2026-10-01;6E-A\n";
        JsonNode stuPreview = uploadJob(
                directorToken, "eleves.csv", studentsCsv.getBytes(StandardCharsets.UTF_8),
                "STUDENTS", "STRICT_ALL_OR_NOTHING", "FAIL_ON_DUPLICATE",
                yearId, null, null, false, "idem-stu-1");
        UUID stuJobId = UUID.fromString(stuPreview.get("id").asString());
        assertThat(stuPreview.get("status").asString()).isEqualTo("PREVIEW_READY");
        assertThat(stuPreview.get("totalRows").asInt()).isEqualTo(2);
        assertThat(stuPreview.get("validRows").asInt()).isEqualTo(2);
        assertThat(stuPreview.get("errorRows").asInt()).isZero();
        assertThat(stuPreview.get("quotaResource").asString()).isEqualTo("STUDENTS");
        assertThat(stuPreview.get("quotaProjectedAfter").asLong()).isEqualTo(2L);

        // Vérifie que l'analyse à sec n'a rien créé dans /api/v1/students et masque les notes médicales
        mockMvc.perform(get("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        MvcResult previewRowsRes = mockMvc.perform(get("/api/v1/schoolimport/jobs/" + stuJobId + "/rows")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(previewRowsRes.getResponse().getContentAsString())
                .contains("[MASQUE]")
                .doesNotContain("Asthme confidentiel");

        // Exécution du lot STUDENTS
        JsonNode stuExecuted = executeJob(directorToken, stuJobId, "exec-stu-1");
        assertThat(stuExecuted.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(stuExecuted.get("createdCount").asInt()).isEqualTo(2);

        // Récupère les matricules générés
        JsonNode studentsPage = readJson(mockMvc.perform(get("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn());
        String matAwa = findMatriculeByFirstName(studentsPage.get("content"), "Awa");
        String matMoussa = findMatriculeByFirstName(studentsPage.get("content"), "Moussa");

        // 2. FAMILIES_GUARDIANS (CSV) avec CNI masquée et lien élève-tuteur
        String famCsv = "family_code;family_name;family_city;guardian_first_name;guardian_last_name;"
                + "guardian_phone;national_id_number;student_matricule;relationship_type;primary_contact;financial_responsible\n"
                + "FAM-NDIAYE;Famille Ndiaye;Dakar;Fatou;Ndiaye;+221772220001;1754198000999;"
                + matAwa + ";MOTHER;true;true\n";
        JsonNode famJob = uploadJob(
                directorToken, "familles.csv", famCsv.getBytes(StandardCharsets.UTF_8),
                "FAMILIES_GUARDIANS", "STRICT_ALL_OR_NOTHING", "UPSERT_UPDATE",
                null, null, null, true, "idem-fam-1");
        assertThat(famJob.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(famJob.get("createdCount").asInt()).isEqualTo(1);
        UUID famJobId = UUID.fromString(famJob.get("id").asString());
        MvcResult famRowsRes = mockMvc.perform(get("/api/v1/schoolimport/jobs/" + famJobId + "/rows")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(famRowsRes.getResponse().getContentAsString())
                .contains("[MASQUE]")
                .doesNotContain("1754198000999");

        // 3. TEACHERS (XLSX réel OOXML) avec taux horaire entier FCFA + spécialité MATH
        byte[] teachersXlsx = SecureTabularFileParserTest.buildMinimalXlsx(
                List.of("employee_code", "first_name", "last_name", "teacher_type", "phone", "hourly_rate_xof", "subject_code"),
                List.of(
                        List.of("ENS-IMP-01", "Cheikh", "Fall", "PERMANENT", "+221773330001", "5000", "MATH"),
                        List.of("ENS-IMP-02", "Aminata", "Sarr", "TUTOR", "+221773330002", "6500", "MATH")));
        JsonNode teacherJob = uploadJob(
                directorToken, "enseignants.xlsx", teachersXlsx,
                "TEACHERS", "STRICT_ALL_OR_NOTHING", "FAIL_ON_DUPLICATE",
                null, null, null, true, "idem-teach-xlsx-1");
        assertThat(teacherJob.get("sourceFormat").asString()).isEqualTo("XLSX");
        assertThat(teacherJob.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(teacherJob.get("createdCount").asInt()).isEqualTo(2);

        // 4. ENROLLMENTS (UPSERT_UPDATE : transfert contrôlé de Moussa de 6E-A vers 6E-B)
        String enrollCsv = "student_matricule;class_code;enrollment_type;repeating\n"
                + matMoussa + ";6E-B;NEW_ADMISSION;false\n";
        JsonNode enrollJob = uploadJob(
                directorToken, "inscriptions.csv", enrollCsv.getBytes(StandardCharsets.UTF_8),
                "ENROLLMENTS", "STRICT_ALL_OR_NOTHING", "UPSERT_UPDATE",
                yearId, null, null, true, "idem-enr-1");
        assertThat(enrollJob.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(enrollJob.get("updatedCount").asInt()).isEqualTo(1);

        // 5. GRADES (sur évaluation ouverte de 6E-A pour Awa)
        UUID evalId = createEvaluationViaApi(
                directorToken, periodId, classAId, mathId, "Devoir Math", "TEST", "2026-10-20", "20.00", 1);
        String gradesCsv = "student_matricule;attendance_status;score;comment\n"
                + matAwa + ";PRESENT;17.50;Excellent travail\n";
        JsonNode gradeJob = uploadJob(
                directorToken, "notes.csv", gradesCsv.getBytes(StandardCharsets.UTF_8),
                "GRADES", "STRICT_ALL_OR_NOTHING", "UPSERT_UPDATE",
                null, evalId, null, true, "idem-grade-1");
        assertThat(gradeJob.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(gradeJob.get("createdCount").asInt()).isEqualTo(1);

        // Vérifie la note enregistrée dans le module grade
        mockMvc.perform(get("/api/v1/grades/evaluations/" + evalId + "/entries")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].score").value(17.5));

        // 6. ATTENDANCE (sur séance d'appel ouverte de 6E-A pour Awa)
        UUID sessionId = createAttendanceSessionViaApi(
                directorToken, classAId, periodId, mathId, "2026-10-21", "MORNING", "08:00", "10:00", false);
        String attCsv = "student_matricule;status;late_minutes;notes\n"
                + matAwa + ";LATE;10;Retard transport\n";
        JsonNode attJob = uploadJob(
                directorToken, "presences.csv", attCsv.getBytes(StandardCharsets.UTF_8),
                "ATTENDANCE", "STRICT_ALL_OR_NOTHING", "UPSERT_UPDATE",
                null, null, sessionId, true, "idem-att-1");
        assertThat(attJob.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(attJob.get("createdCount").asInt()).isEqualTo(1);

        // Vérifie les événements immuables, la synthèse SQL, l'audit SCHOOLIMPORT et le port public
        mockMvc.perform(get("/api/v1/schoolimport/jobs/" + stuJobId + "/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].eventType").value("UPLOADED"))
                .andExpect(jsonPath("$[3].eventType").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/schoolimport/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").value(6))
                .andExpect(jsonPath("$.completedCount").value(6))
                .andExpect(jsonPath("$.totalCreatedRows").value(7))
                .andExpect(jsonPath("$.totalUpdatedRows").value(1));

        // Vérifie que l'audit contient la catégorie SCHOOLIMPORT sans fuite de CNI/notes médicales
        MvcResult auditRes = mockMvc.perform(get("/api/v1/audit/logs?category=SCHOOLIMPORT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(12))
                .andReturn();
        assertThat(auditRes.getResponse().getContentAsString())
                .doesNotContain("1754198000999")
                .doesNotContain("Asthme");

        // Vérifie le port public SchoolImportDirectoryPort
        TenantContext.runWithTenant(TenantId.of(school.getId()), () -> {
            ImportJobSummary summary = importDirectoryPort.findJobById(stuJobId).orElseThrow();
            assertThat(summary.status()).isEqualTo(ImportJobStatus.COMPLETED);
            assertThat(importDirectoryPort.findJobByNumber(summary.jobNumber())).isPresent();
            assertThat(importDirectoryPort.countCompletedJobs()).isEqualTo(6L);
            assertThat(importDirectoryPort.findRecentJobs(3)).hasSize(3);
        });
    }

    @Test
    void shouldEnforceValidationModesDuplicateStrategiesIdempotencyCancellationAndErrorCsvExport() throws Exception {
        School school = createSchool("IMPB", SchoolType.PRIVATE_SCHOOL);
        createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 200, 100);
        User director = createTenantUser(school.getId(), "dir@impb.sn", "Ibrahima", "Sow", RoleCode.DIRECTOR);
        String token = tenantTokenForUser(school.getId(), director.getId(), RoleCode.DIRECTOR);

        // 1. STRICT_ALL_OR_NOTHING avec 1 ligne invalide (téléphone non E.164 + formule dangereuse) -> VALIDATION_FAILED
        String badCsv = "first_name;last_name;gender;birth_date;phone\n"
                + "Awa;Ndiaye;FEMALE;2014-05-12;+221771110001\n"
                + "=CMD();BadPhone;FEMALE;2014-06-10;771234567\n";
        JsonNode strictFailed = uploadJob(
                token, "strict_bad.csv", badCsv.getBytes(StandardCharsets.UTF_8),
                "STUDENTS", "STRICT_ALL_OR_NOTHING", "FAIL_ON_DUPLICATE",
                null, null, null, false, "idem-strict-bad");
        UUID badJobId = UUID.fromString(strictFailed.get("id").asString());
        assertThat(strictFailed.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(strictFailed.get("validRows").asInt()).isEqualTo(1);
        assertThat(strictFailed.get("errorRows").asInt()).isEqualTo(1);

        // Tentative d'exécution sur VALIDATION_FAILED -> 409
        mockMvc.perform(post("/api/v1/schoolimport/jobs/" + badJobId + "/execute")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", "exec-bad-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("confirmExecution", true))))
                .andExpect(status().isConflict());

        // Téléchargement CSV des erreurs avec neutralisation anti-CSV-Injection
        MvcResult errCsvRes = mockMvc.perform(get("/api/v1/schoolimport/jobs/" + badJobId + "/errors/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        String errCsvText = new String(errCsvRes.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(errCsvText).contains("INVALID_PHONE_E164");

        // Annulation du lot VALIDATION_FAILED
        mockMvc.perform(post("/api/v1/schoolimport/jobs/" + badJobId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Fichier corrigé à réimporter"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 2. PARTIAL_SKIP_ERRORS : 1 ligne valide créée en REQUIRES_NEW + 1 ligne invalide ignorée -> COMPLETED_WITH_ERRORS
        JsonNode partialJob = uploadJob(
                token, "partial.csv", badCsv.getBytes(StandardCharsets.UTF_8),
                "STUDENTS", "PARTIAL_SKIP_ERRORS", "FAIL_ON_DUPLICATE",
                null, null, null, false, "idem-partial-1");
        UUID partialJobId = UUID.fromString(partialJob.get("id").asString());
        assertThat(partialJob.get("status").asString()).isEqualTo("PREVIEW_READY");

        JsonNode partialDone = executeJob(token, partialJobId, "exec-partial-1");
        assertThat(partialDone.get("status").asString()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(partialDone.get("createdCount").asInt()).isEqualTo(1);
        assertThat(partialDone.get("errorRows").asInt()).isEqualTo(1);

        // Idempotence d'exécution : même clé -> 200 OK + X-Idempotent-Replay: true
        mockMvc.perform(post("/api/v1/schoolimport/jobs/" + partialJobId + "/execute")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", "exec-partial-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("confirmExecution", true))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.createdCount").value(1));

        // Clé d'exécution différente sur lot déjà terminé -> 409 Conflict
        mockMvc.perform(post("/api/v1/schoolimport/jobs/" + partialJobId + "/execute")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", "exec-partial-different")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("confirmExecution", true))))
                .andExpect(status().isConflict());

        // Idempotence d'upload : même clé + même fichier -> 200 OK + X-Idempotent-Replay: true
        MockMultipartFile sameFile = new MockMultipartFile(
                "file", "partial.csv", "text/csv", badCsv.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/schoolimport/jobs")
                        .file(sameFile)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", "idem-partial-1")
                        .param("importType", "STUDENTS")
                        .param("validationMode", "PARTIAL_SKIP_ERRORS")
                        .param("duplicateStrategy", "FAIL_ON_DUPLICATE"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(partialJobId.toString()));

        // Même clé d'upload + fichier différent -> 409 Conflict
        MockMultipartFile diffFile = new MockMultipartFile(
                "file", "other.csv", "text/csv",
                "first_name;last_name;gender;birth_date\nAutre;Eleve;MALE;2014-01-10\n".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/schoolimport/jobs")
                        .file(diffFile)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", "idem-partial-1")
                        .param("importType", "STUDENTS")
                        .param("validationMode", "PARTIAL_SKIP_ERRORS")
                        .param("duplicateStrategy", "FAIL_ON_DUPLICATE"))
                .andExpect(status().isConflict());

        // 3. Stratégies de doublons sur TEACHERS : FAIL_ON_DUPLICATE / SKIP_DUPLICATES / UPSERT_UPDATE
        String t1Csv = "employee_code;first_name;last_name;teacher_type;phone;hourly_rate_xof\n"
                + "ENS-DUP;Aliou;Ba;PERMANENT;+221774440001;4000\n";
        JsonNode createdT = uploadJob(
                token, "t1.csv", t1Csv.getBytes(StandardCharsets.UTF_8),
                "TEACHERS", "STRICT_ALL_OR_NOTHING", "FAIL_ON_DUPLICATE",
                null, null, null, true, "idem-t1");
        assertThat(createdT.get("createdCount").asInt()).isEqualTo(1);

        // Doublon avec FAIL_ON_DUPLICATE -> VALIDATION_FAILED
        JsonNode dupFail = uploadJob(
                token, "t1_dup.csv", t1Csv.getBytes(StandardCharsets.UTF_8),
                "TEACHERS", "STRICT_ALL_OR_NOTHING", "FAIL_ON_DUPLICATE",
                null, null, null, false, "idem-t1-fail");
        assertThat(dupFail.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(dupFail.get("errorRows").asInt()).isEqualTo(1);

        // Doublon avec SKIP_DUPLICATES -> COMPLETED, skippedCount = 1
        JsonNode dupSkip = uploadJob(
                token, "t1_skip.csv", t1Csv.getBytes(StandardCharsets.UTF_8),
                "TEACHERS", "STRICT_ALL_OR_NOTHING", "SKIP_DUPLICATES",
                null, null, null, true, "idem-t1-skip");
        assertThat(dupSkip.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(dupSkip.get("skippedCount").asInt()).isEqualTo(1);
        assertThat(dupSkip.get("createdCount").asInt()).isZero();

        // Doublon avec UPSERT_UPDATE -> COMPLETED, updatedCount = 1 (taux horaire mis à jour à 5500 FCFA)
        String t1UpdCsv = "employee_code;first_name;last_name;teacher_type;phone;hourly_rate_xof\n"
                + "ENS-DUP;Aliou;Ba;PERMANENT;+221774440001;5500\n";
        JsonNode dupUpsert = uploadJob(
                token, "t1_upd.csv", t1UpdCsv.getBytes(StandardCharsets.UTF_8),
                "TEACHERS", "STRICT_ALL_OR_NOTHING", "UPSERT_UPDATE",
                null, null, null, true, "idem-t1-upd");
        assertThat(dupUpsert.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(dupUpsert.get("updatedCount").asInt()).isEqualTo(1);
    }

    @Test
    void shouldEnforceStudentAndStorageQuotasSubscriptionStatesCapacityAndClosedPeriod() throws Exception {
        School school = createSchool("IMPQ", SchoolType.PRIVATE_SCHOOL);
        // Forfait limité à 2 élèves et 5 Mo de stockage
        createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 2, 5);
        User director = createTenantUser(school.getId(), "dir@impq.sn", "Oumar", "Sy", RoleCode.DIRECTOR);
        String token = tenantTokenForUser(school.getId(), director.getId(), RoleCode.DIRECTOR);

        // Lot de 3 élèves sur quota = 2 en STRICT_ALL_OR_NOTHING -> VALIDATION_FAILED (ligne 4 en SUBSCRIPTION_QUOTA_EXCEEDED)
        String threeStudents = "first_name;last_name;gender;birth_date\n"
                + "Eleve1;Test;MALE;2014-01-01\n"
                + "Eleve2;Test;FEMALE;2014-02-02\n"
                + "Eleve3;Test;MALE;2014-03-03\n";
        JsonNode strictQuota = uploadJob(
                token, "quota3.csv", threeStudents.getBytes(StandardCharsets.UTF_8),
                "STUDENTS", "STRICT_ALL_OR_NOTHING", "FAIL_ON_DUPLICATE",
                null, null, null, false, "idem-quota-strict");
        assertThat(strictQuota.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(strictQuota.get("validRows").asInt()).isEqualTo(2);
        assertThat(strictQuota.get("errorRows").asInt()).isEqualTo(1);

        // Même lot en PARTIAL_SKIP_ERRORS -> 2 créés, 1 en erreur quota (aucun dépassement)
        JsonNode partialQuota = uploadJob(
                token, "quota3_partial.csv", threeStudents.getBytes(StandardCharsets.UTF_8),
                "STUDENTS", "PARTIAL_SKIP_ERRORS", "FAIL_ON_DUPLICATE",
                null, null, null, true, "idem-quota-partial");
        assertThat(partialQuota.get("status").asString()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(partialQuota.get("createdCount").asInt()).isEqualTo(2);
        assertThat(partialQuota.get("errorRows").asInt()).isEqualTo(1);

        // Vérifie la remontée STORAGE_MB + STUDENTS dans GET /api/v1/subscriptions/current/quotas
        MvcResult quotasRes = mockMvc.perform(get("/api/v1/subscriptions/current/quotas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode quotasJson = readJson(quotasRes).get("resources");
        long studentsUsed = -1;
        long storageUsed = -1;
        for (JsonNode q : quotasJson) {
            if ("STUDENTS".equals(q.get("resource").asString())) {
                studentsUsed = q.get("currentUsage").asLong();
            } else if ("STORAGE_MB".equals(q.get("resource").asString())) {
                storageUsed = q.get("currentUsage").asLong();
            }
        }
        assertThat(studentsUsed).isEqualTo(2L);
        assertThat(storageUsed).isGreaterThanOrEqualTo(1L);

        // Période d'évaluation fermée (CLOSED) -> import GRADES bloqué en 409
        UUID yearId = createAcademicYearViaApi(token, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(token, yearId);
        UUID periodId = createAndOpenPeriodViaApi(token, yearId, "T1", "Trimestre 1", 1, "2026-10-01", "2026-12-20");
        UUID levelId = createGradeLevelViaApi(token, "6E", "Sixième", "MIDDLE_SCHOOL", 1);
        UUID mathId = createSubjectViaApi(token, "MATH", "Mathématiques", "SCIENTIFIC", 4);
        UUID classId = createClassRoomViaApi(token, yearId, levelId, "6E-A", "Sixième A", 1);
        assignSubjectToClassViaApi(token, classId, mathId);
        UUID evalId = createEvaluationViaApi(
                token, periodId, classId, mathId, "Test Math", "TEST", "2026-10-15", "20.00", 1);
        // Ferme la période T1
        mockMvc.perform(post("/api/v1/academic/periods/" + periodId + "/close")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        MockMultipartFile gradeFile = new MockMultipartFile(
                "file", "grades.csv", "text/csv", "student_matricule;score\nMAT-1;15\n".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/schoolimport/jobs")
                        .file(gradeFile)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", "idem-closed-period")
                        .param("importType", "GRADES")
                        .param("evaluationId", evalId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", containsString("evaluation-period-closed")));

        // Abonnement EXPIRED : lecture seule 200 OK, écriture 403
        School expiredSchool = createSchool("IMPX", SchoolType.PRIVATE_SCHOOL);
        createSubscription(expiredSchool, SubscriptionStatus.ACTIVE, Instant.now().minus(2, ChronoUnit.DAYS), 100, 100);
        String expiredToken = tenantToken(expiredSchool.getId(), RoleCode.DIRECTOR);
        mockMvc.perform(get("/api/v1/schoolimport/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)))
                .andExpect(status().isOk());
        MockMultipartFile csvFile = new MockMultipartFile(
                "file", "e.csv", "text/csv",
                "first_name;last_name;gender;birth_date\nA;B;MALE;2014-01-01\n".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/schoolimport/jobs")
                        .file(csvFile)
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
                        .header("Idempotency-Key", "idem-exp-1")
                        .param("importType", "STUDENTS"))
                .andExpect(status().isForbidden());

        // Abonnement SUSPENDED : lecture et écriture 403
        School suspendedSchool = createSchool("IMPS", SchoolType.PRIVATE_SCHOOL);
        createSubscription(suspendedSchool, SubscriptionStatus.SUSPENDED, Instant.now().plus(30, ChronoUnit.DAYS), 100, 100);
        String suspendedToken = tenantToken(suspendedSchool.getId(), RoleCode.DIRECTOR);
        mockMvc.perform(get("/api/v1/schoolimport/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspendedToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldEnforceRbacTeacherScopeAndRejectSpoofedFiles() throws Exception {
        School school = createSchool("IMPR", SchoolType.PRIVATE_SCHOOL);
        createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 200, 100);

        User director = createTenantUser(school.getId(), "dir@impr.sn", "Dir", "One", RoleCode.DIRECTOR);
        User teacherUser = createTenantUser(school.getId(), "prof@impr.sn", "Prof", "Math", RoleCode.TEACHER);
        String dirToken = tenantTokenForUser(school.getId(), director.getId(), RoleCode.DIRECTOR);
        String secretaryToken = tenantToken(school.getId(), RoleCode.SECRETARY);
        String censorToken = tenantToken(school.getId(), RoleCode.CENSOR);
        String promoterToken = tenantToken(school.getId(), RoleCode.PROMOTER);
        String teacherToken = tenantTokenForUser(school.getId(), teacherUser.getId(), RoleCode.TEACHER);
        String cashierToken = tenantToken(school.getId(), RoleCode.CASHIER);
        String parentToken = tenantToken(school.getId(), RoleCode.PARENT);
        String studentToken = tenantToken(school.getId(), RoleCode.STUDENT);
        String saasToken = platformAdminToken();

        // Structure & enseignant affecté uniquement à 6E-A / MATH
        UUID yearId = createAcademicYearViaApi(dirToken, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(dirToken, yearId);
        UUID periodId = createAndOpenPeriodViaApi(dirToken, yearId, "T1", "Trimestre 1", 1, "2026-10-01", "2026-12-20");
        UUID levelId = createGradeLevelViaApi(dirToken, "6E", "Sixième", "MIDDLE_SCHOOL", 1);
        UUID mathId = createSubjectViaApi(dirToken, "MATH", "Maths", "SCIENTIFIC", 4);
        UUID frId = createSubjectViaApi(dirToken, "FR", "Français", "LANGUAGE", 3);
        UUID classAId = createClassRoomViaApi(dirToken, yearId, levelId, "6E-A", "Sixième A", 30);
        assignSubjectToClassViaApi(dirToken, classAId, mathId);
        assignSubjectToClassViaApi(dirToken, classAId, frId);

        UUID teacherId = createTeacherViaApi(dirToken, "T-01", teacherUser.getId(), "Prof", "Math", "PERMANENT", "+221779990001");
        assignTeacherToClassViaApi(dirToken, teacherId, yearId, classAId, mathId);
        UUID mathEvalId = createEvaluationViaApi(dirToken, periodId, classAId, mathId, "Devoir Math", "TEST", "2026-10-20", "20.00", 1);
        UUID frEvalId = createEvaluationViaApi(dirToken, periodId, classAId, frId, "Devoir Fr", "TEST", "2026-10-20", "20.00", 1);

        // Fichier PDF déguisé en .csv -> rejeté par détection réelle (400)
        MockMultipartFile spoofedPdf = new MockMultipartFile(
                "file", "eleves.csv", "text/csv", "%PDF-1.4\nfake pdf content".getBytes(StandardCharsets.US_ASCII));
        mockMvc.perform(multipart("/api/v1/schoolimport/jobs")
                        .file(spoofedPdf)
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirToken))
                        .header("Idempotency-Key", "idem-spoof-pdf")
                        .param("importType", "STUDENTS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", containsString("unsupported-file-type")));

        byte[] studentCsv = "first_name;last_name;gender;birth_date\nAli;Diallo;MALE;2014-04-04\n"
                .getBytes(StandardCharsets.UTF_8);
        byte[] teacherCsv = "employee_code;first_name;last_name;teacher_type;phone\nE-99;M;N;PERMANENT;+221778880001\n"
                .getBytes(StandardCharsets.UTF_8);

        // SECRETARY autorisé sur STUDENTS, refusé 403 sur TEACHERS
        JsonNode secJob = uploadJob(
                secretaryToken, "s.csv", studentCsv, "STUDENTS",
                "STRICT_ALL_OR_NOTHING", "FAIL_ON_DUPLICATE", yearId, null, null, true, "idem-sec-stu");
        assertThat(secJob.get("status").asString()).isEqualTo("COMPLETED");
        UUID secJobId = UUID.fromString(secJob.get("id").asString());

        mockMvc.perform(multipart("/api/v1/schoolimport/jobs")
                        .file(new MockMultipartFile("file", "t.csv", "text/csv", teacherCsv))
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .header("Idempotency-Key", "idem-sec-teach")
                        .param("importType", "TEACHERS"))
                .andExpect(status().isForbidden());

        // CENSOR autorisé sur TEACHERS, refusé 403 sur STUDENTS
        JsonNode cenJob = uploadJob(
                censorToken, "t.csv", teacherCsv, "TEACHERS",
                "STRICT_ALL_OR_NOTHING", "FAIL_ON_DUPLICATE", null, null, null, true, "idem-cen-teach");
        assertThat(cenJob.get("status").asString()).isEqualTo("COMPLETED");

        mockMvc.perform(multipart("/api/v1/schoolimport/jobs")
                        .file(new MockMultipartFile("file", "s2.csv", "text/csv", studentCsv))
                        .header(HttpHeaders.AUTHORIZATION, bearer(censorToken))
                        .header("Idempotency-Key", "idem-cen-stu")
                        .param("importType", "STUDENTS"))
                .andExpect(status().isForbidden());

        // PROMOTER lecture seule 200, écriture 403
        mockMvc.perform(get("/api/v1/schoolimport/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken)))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/schoolimport/jobs")
                        .file(new MockMultipartFile("file", "s3.csv", "text/csv", studentCsv))
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken))
                        .header("Idempotency-Key", "idem-prom-stu")
                        .param("importType", "STUDENTS"))
                .andExpect(status().isForbidden());

        // TEACHER : refusé sur STUDENTS (403), refusé sur évaluation FR hors périmètre (403),
        // refusé sur consultation du lot du secrétariat (403), autorisé sur évaluation MATH affectée (201)
        mockMvc.perform(multipart("/api/v1/schoolimport/jobs")
                        .file(new MockMultipartFile("file", "s4.csv", "text/csv", studentCsv))
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .header("Idempotency-Key", "idem-tch-stu")
                        .param("importType", "STUDENTS"))
                .andExpect(status().isForbidden());

        byte[] gradeCsv = "student_matricule;score\nNO-SUCH-MAT;15\n".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/v1/schoolimport/jobs")
                        .file(new MockMultipartFile("file", "fr.csv", "text/csv", gradeCsv))
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .header("Idempotency-Key", "idem-tch-fr")
                        .param("importType", "GRADES")
                        .param("evaluationId", frEvalId.toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/schoolimport/jobs/" + secJobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/v1/schoolimport/jobs")
                        .file(new MockMultipartFile("file", "math.csv", "text/csv", gradeCsv))
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .header("Idempotency-Key", "idem-tch-math")
                        .param("importType", "GRADES")
                        .param("evaluationId", mathEvalId.toString()))
                .andExpect(status().isCreated());

        // CASHIER, PARENT, STUDENT, ADMIN_SAAS -> 403 partout
        for (String deniedToken : List.of(cashierToken, parentToken, studentToken, saasToken)) {
            mockMvc.perform(get("/api/v1/schoolimport/jobs")
                            .header(HttpHeaders.AUTHORIZATION, bearer(deniedToken)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void shouldIsolateTenantsAtRestAndEnforcePostgresRlsAndImmutabilityUnderEcoleApp() throws Exception {
        School schoolA = createSchool("IMTA", SchoolType.PRIVATE_SCHOOL);
        School schoolB = createSchool("IMTB", SchoolType.PRIVATE_SCHOOL);
        createSubscription(schoolA, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 200, 100);
        createSubscription(schoolB, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 200, 100);

        String tokenA = tenantToken(schoolA.getId(), RoleCode.DIRECTOR);
        String tokenB = tenantToken(schoolB.getId(), RoleCode.DIRECTOR);

        byte[] csv = "first_name;last_name;gender;birth_date\nFatou;Dieng;FEMALE;2014-05-05\n"
                .getBytes(StandardCharsets.UTF_8);
        JsonNode jobA = uploadJob(
                tokenA, "a.csv", csv, "STUDENTS",
                "STRICT_ALL_OR_NOTHING", "FAIL_ON_DUPLICATE", null, null, null, true, "idem-iso-a");
        JsonNode jobB = uploadJob(
                tokenB, "b.csv", csv, "STUDENTS",
                "STRICT_ALL_OR_NOTHING", "FAIL_ON_DUPLICATE", null, null, null, true, "idem-iso-b");

        UUID jobAId = UUID.fromString(jobA.get("id").asString());
        UUID jobBId = UUID.fromString(jobB.get("id").asString());

        // Isolation REST : A ne peut ni lire, ni exécuter, ni annuler, ni exporter les erreurs de B (même avec X-Tenant-Id forgé)
        mockMvc.perform(get("/api/v1/schoolimport/jobs/" + jobBId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("X-Tenant-Id", schoolB.getId().toString())
                        .param("tenantId", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/schoolimport/jobs/" + jobBId + "/rows")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/schoolimport/jobs/" + jobBId + "/errors/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/schoolimport/jobs/" + jobBId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Tentative inter-tenant"))))
                .andExpect(status().isNotFound());

        // Vérification directe PostgreSQL RLS sous ecole_app (NOBYPASSRLS) sur les 5 tables V14
        List<String> tables = List.of(
                "schoolimport_sequences",
                "schoolimport_jobs",
                "schoolimport_job_files",
                "schoolimport_job_rows",
                "schoolimport_job_events");

        // 1. Fail-closed : 0 ligne sans TenantContext
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '', true)");
            for (String table : tables) {
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
                assertThat(count).as("Fail-closed sans tenant sur " + table).isZero();
            }
        });

        // 2. Isolation tenant A vs B sous ecole_app
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
            for (String table : tables) {
                Integer countA = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, schoolA.getId());
                Integer countB = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, schoolB.getId());
                assertThat(countA).as("Tenant A visible sur " + table).isGreaterThanOrEqualTo(1);
                assertThat(countB).as("Tenant B invisible pour A sur " + table).isZero();
            }
        });

        // 3. Rejet INSERT cross-tenant (RLS WITH CHECK)
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
            jdbcTemplate.update(
                    "INSERT INTO schoolimport_sequences (tenant_id, sequence_year, last_value) VALUES (?, 2099, 1)",
                    schoolB.getId());
        })).isInstanceOf(DataAccessException.class);

        // 4. Immutabilité stricte (triggers BEFORE UPDATE OR DELETE sur files et events)
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
            jdbcTemplate.update(
                    "UPDATE schoolimport_job_files SET sha256_hash = 'tampered' WHERE job_id = ?", jobAId);
        })).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
            jdbcTemplate.update("DELETE FROM schoolimport_job_events WHERE job_id = ?", jobAId);
        })).isInstanceOf(DataAccessException.class);

        // 5. Moindre privilège ecole_app : DELETE refusé sur schoolimport_jobs et schoolimport_job_rows
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
            jdbcTemplate.update("DELETE FROM schoolimport_job_rows WHERE job_id = ?", jobAId);
        })).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
            jdbcTemplate.update("DELETE FROM schoolimport_jobs WHERE id = ?", jobAId);
        })).isInstanceOf(DataAccessException.class);
    }

    // --- Helpers ---

    private JsonNode uploadJob(
            String token,
            String fileName,
            byte[] bytes,
            String importType,
            String validationMode,
            String duplicateStrategy,
            UUID academicYearId,
            UUID evaluationId,
            UUID attendanceSessionId,
            boolean executeImmediately,
            String idempotencyKey) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "application/octet-stream", bytes);
        var req = multipart("/api/v1/schoolimport/jobs")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("Idempotency-Key", idempotencyKey)
                .param("importType", importType)
                .param("validationMode", validationMode)
                .param("duplicateStrategy", duplicateStrategy)
                .param("executeImmediately", Boolean.toString(executeImmediately));
        if (academicYearId != null) {
            req.param("academicYearId", academicYearId.toString());
        }
        if (evaluationId != null) {
            req.param("evaluationId", evaluationId.toString());
        }
        if (attendanceSessionId != null) {
            req.param("attendanceSessionId", attendanceSessionId.toString());
        }
        MvcResult res = mockMvc.perform(req)
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(res);
    }

    private JsonNode executeJob(String token, UUID jobId, String idempotencyKey) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/schoolimport/jobs/" + jobId + "/execute")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("confirmExecution", true))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(res);
    }

    private static String findMatriculeByFirstName(JsonNode items, String firstName) {
        for (JsonNode item : items) {
            if (firstName.equals(item.get("firstName").asString())) {
                return item.get("matricule").asString();
            }
        }
        throw new AssertionError("Élève introuvable : " + firstName);
    }

    private School createSchool(String prefix, SchoolType type) {
        String code = prefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return schoolService.createSchool(
                code, "École " + code, type, "+221770000000", "contact@" + code.toLowerCase() + ".sn", "Dakar");
    }

    private void createSubscription(
            School school, SubscriptionStatus status, Instant endsAt, int maxStudents, int maxStorageMb) {
        SubscriptionPlan plan = subscriptionService.createPlan(
                "PLAN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                "Forfait Import",
                20_000L,
                200_000L,
                maxStudents,
                50,
                maxStorageMb);
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

    private UUID createAndOpenPeriodViaApi(
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
        UUID periodId = UUID.fromString(readJson(res).get("id").asString());
        mockMvc.perform(post("/api/v1/academic/periods/" + periodId + "/open")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        return periodId;
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

    private UUID createSubjectViaApi(String token, String code, String name, String category, int defaultCoef)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/academic/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "name", name, "category", category, "defaultCoefficient", defaultCoef))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createClassRoomViaApi(String token, UUID yearId, UUID levelId, String code, String name, int capacity)
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

    private void assignSubjectToClassViaApi(String token, UUID classRoomId, UUID subjectId) throws Exception {
        mockMvc.perform(post("/api/v1/academic/classes/" + classRoomId + "/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("subjectId", subjectId, "coefficient", 4, "mandatory", true))))
                .andExpect(status().isCreated());
    }

    private UUID createTeacherViaApi(
            String token, String code, UUID userId, String firstName, String lastName, String type, String phone)
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
        MvcResult res = mockMvc.perform(post("/api/v1/teachers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private void assignTeacherToClassViaApi(
            String token, UUID teacherId, UUID yearId, UUID classRoomId, UUID subjectId) throws Exception {
        mockMvc.perform(post("/api/v1/teacher-assignments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "teacherId", teacherId,
                                "academicYearId", yearId,
                                "classRoomId", classRoomId,
                                "subjectId", subjectId,
                                "homeroomTeacher", true,
                                "weeklyHours", 4))))
                .andExpect(status().isCreated());
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
            int weight) throws Exception {
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

    private UUID createAttendanceSessionViaApi(
            String token,
            UUID classId,
            UUID periodId,
            UUID subjectId,
            String sessionDate,
            String timeSlot,
            String startTime,
            String endTime,
            boolean initializePresent) throws Exception {
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
