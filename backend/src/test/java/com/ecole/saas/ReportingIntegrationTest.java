package com.ecole.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecole.saas.identity.application.UserAccountService;
import com.ecole.saas.identity.domain.AccountRef;
import com.ecole.saas.identity.domain.RoleCode;
import com.ecole.saas.identity.domain.TokenIssuer;
import com.ecole.saas.identity.domain.User;
import com.ecole.saas.reporting.ReportFormat;
import com.ecole.saas.reporting.ReportSnapshotSummary;
import com.ecole.saas.reporting.ReportType;
import com.ecole.saas.reporting.ReportingDirectoryPort;
import com.ecole.saas.reporting.domain.ReportSecurityPolicy;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
class ReportingIntegrationTest {

        @Container
        @ServiceConnection
        static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

        private final MockMvc mockMvc;
        private final ObjectMapper objectMapper;
        private final SchoolService schoolService;
        private final SubscriptionService subscriptionService;
        private final UserAccountService userAccountService;
        private final ReportingDirectoryPort reportingDirectoryPort;
        private final TokenIssuer tokenIssuer;
        private final TransactionTemplate transactionTemplate;
        private final JdbcTemplate jdbcTemplate;

        @Autowired
        ReportingIntegrationTest(
                        MockMvc mockMvc,
                        ObjectMapper objectMapper,
                        SchoolService schoolService,
                        SubscriptionService subscriptionService,
                        UserAccountService userAccountService,
                        ReportingDirectoryPort reportingDirectoryPort,
                        TokenIssuer tokenIssuer,
                        TransactionTemplate transactionTemplate,
                        JdbcTemplate jdbcTemplate) {
                this.mockMvc = mockMvc;
                this.objectMapper = objectMapper;
                this.schoolService = schoolService;
                this.subscriptionService = subscriptionService;
                this.userAccountService = userAccountService;
                this.reportingDirectoryPort = reportingDirectoryPort;
                this.tokenIssuer = tokenIssuer;
                this.transactionTemplate = transactionTemplate;
                this.jdbcTemplate = jdbcTemplate;
        }

        @AfterEach
        void clearTenantContext() {
                TenantContext.clear();
        }

        @Test
        void shouldConsolidateAllModulesExportAntiCsvInjectionManageIdempotentSnapshotsAndAudit() throws Exception {
                School school = createSchool("RPTA", SchoolType.PRIVATE_SCHOOL);
                createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 200,
                                100);

                User director = createTenantUser(school.getId(), "dir@rpta.sn", "Mamadou", "Diop", RoleCode.DIRECTOR);
                User promoter = createTenantUser(school.getId(), "prom@rpta.sn", "Cheikh", "Fall", RoleCode.PROMOTER);
                User censor = createTenantUser(school.getId(), "cens@rpta.sn", "Ibrahima", "Sarr", RoleCode.CENSOR);
                User cashier = createTenantUser(school.getId(), "cash@rpta.sn", "Mariama", "Ba", RoleCode.CASHIER);
                User secretary = createTenantUser(school.getId(), "sec@rpta.sn", "Aissatou", "Sy", RoleCode.SECRETARY);
                User teacherUser = createTenantUser(school.getId(), "prof@rpta.sn", "Ousmane", "Ndiaye",
                                RoleCode.TEACHER);
                User parentUser = createTenantUser(school.getId(), "parent@rpta.sn", "Fatou", "Diallo",
                                RoleCode.PARENT);
                User studentUser = createTenantUser(school.getId(), "eleve@rpta.sn", "Aminata", "Diallo",
                                RoleCode.STUDENT);

                String directorToken = tenantTokenForUser(school.getId(), director.getId(), RoleCode.DIRECTOR);
                String promoterToken = tenantTokenForUser(school.getId(), promoter.getId(), RoleCode.PROMOTER);
                String censorToken = tenantTokenForUser(school.getId(), censor.getId(), RoleCode.CENSOR);
                String cashierToken = tenantTokenForUser(school.getId(), cashier.getId(), RoleCode.CASHIER);
                String secretaryToken = tenantTokenForUser(school.getId(), secretary.getId(), RoleCode.SECRETARY);
                String teacherToken = tenantTokenForUser(school.getId(), teacherUser.getId(), RoleCode.TEACHER);
                String parentToken = tenantTokenForUser(school.getId(), parentUser.getId(), RoleCode.PARENT);
                String studentToken = tenantTokenForUser(school.getId(), studentUser.getId(), RoleCode.STUDENT);

                // 1. Structure académique : 1 année, 1 période, 2 classes (dont une avec nom
                // contenant formule CSV-Injection)
                UUID yearId = createAcademicYearViaApi(directorToken, "2026-2027", "2026-10-01", "2027-07-31");
                activateAcademicYearViaApi(directorToken, yearId);
                UUID periodId = createAndOpenPeriodViaApi(directorToken, yearId, "T1", "Trimestre 1", 1, "2026-10-01",
                                "2026-12-20");
                UUID levelId = createGradeLevelViaApi(directorToken, "6E", "Sixième", "MIDDLE_SCHOOL", 1);
                UUID mathId = createSubjectViaApi(directorToken, "MATH", "Mathématiques", "SCIENTIFIC", 4);
                UUID classAId = createClassRoomViaApi(directorToken, yearId, levelId, "6E-A", "Sixième A", 20);
                UUID classBId = createClassRoomViaApi(directorToken, yearId, levelId, "6E-B", "=CMD|' /C calc'!A0", 20);
                assignSubjectToClassViaApi(directorToken, classAId, mathId);
                assignSubjectToClassViaApi(directorToken, classBId, mathId);

                // Enseignant affecté uniquement à 6E-A
                UUID teacherId = createTeacherViaApi(
                                directorToken, "PROF-001", teacherUser.getId(), "Ousmane", "Ndiaye", "PERMANENT",
                                "+221771000001");
                assignTeacherToClassViaApi(directorToken, teacherId, yearId, classAId, mathId);

                // 2. Élèves, inscriptions et liens famille/comptes (2 dans 6E-A, 1 dans 6E-B)
                UUID stuA1 = createStudentViaApi(secretaryToken, "Aminata", "Diallo", "FEMALE");
                UUID stuA2 = createStudentViaApi(secretaryToken, "Moussa", "Sow", "MALE");
                UUID stuB1 = createStudentViaApi(secretaryToken, "Khadija", "Faye", "FEMALE");
                enrollInClassViaApi(secretaryToken, stuA1, yearId, classAId);
                enrollInClassViaApi(secretaryToken, stuA2, yearId, classAId);
                enrollInClassViaApi(secretaryToken, stuB1, yearId, classBId);

                linkUserToStudent(school.getId(), stuA1, studentUser.getId());
                UUID familyId = createFamilyViaApi(secretaryToken, "FAM-DIALLO", "Famille Diallo");
                UUID guardianId = createGuardianViaApi(
                                secretaryToken, familyId, "Fatou", "Diallo", "+221772000001", "fatou@rpta.sn");
                linkGuardianToStudentViaApi(secretaryToken, stuA1, guardianId, "MOTHER", true, true);
                linkUserToGuardian(school.getId(), guardianId, parentUser.getId());

                // 3. Notes & bulletins : stuA1 = 16/20 (admis, LOCKED), stuA2 = 8/20 (non
                // admis, DRAFT), stuB1 = 12/20 (admis, VALIDATED)
                UUID evalA = createEvaluationViaApi(
                                teacherToken, periodId, classAId, mathId, "Compo Math A", "COMPOSITION", "2026-11-10",
                                "20.00", 1);
                upsertGradesViaApi(teacherToken, evalA, List.of(
                                Map.of("studentId", stuA1, "attendanceStatus", "PRESENT", "score", 16.0),
                                Map.of("studentId", stuA2, "attendanceStatus", "PRESENT", "score", 8.0)));
                JsonNode cardsA = computeReportCardsViaApi(censorToken, classAId, periodId);
                UUID cardA1Id = findCardIdForStudent(cardsA, stuA1);
                mockMvc.perform(post("/api/v1/grades/report-cards/" + cardA1Id + "/validate")
                                .header(HttpHeaders.AUTHORIZATION, bearer(censorToken)))
                                .andExpect(status().isOk());
                mockMvc.perform(post("/api/v1/grades/report-cards/" + cardA1Id + "/lock")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                                .andExpect(status().isOk());

                UUID evalB = createEvaluationViaApi(
                                censorToken, periodId, classBId, mathId, "Compo Math B", "COMPOSITION", "2026-11-10",
                                "20.00", 1);
                upsertGradesViaApi(censorToken, evalB, List.of(
                                Map.of("studentId", stuB1, "attendanceStatus", "PRESENT", "score", 12.0)));
                JsonNode cardsB = computeReportCardsViaApi(censorToken, classBId, periodId);
                UUID cardB1Id = findCardIdForStudent(cardsB, stuB1);
                mockMvc.perform(post("/api/v1/grades/report-cards/" + cardB1Id + "/validate")
                                .header(HttpHeaders.AUTHORIZATION, bearer(censorToken)))
                                .andExpect(status().isOk());

                // 4. Assiduité : 1 séance 6E-A (stuA1 PRESENT, stuA2 ABSENT justifié) + 1
                // séance 6E-B (stuB1 LATE 15 min)
                UUID sessA = createAttendanceSessionViaApi(
                                teacherToken, classAId, periodId, mathId, "2026-11-12", "MORNING", "08:00", "10:00",
                                false);
                JsonNode recA = readJson(mockMvc.perform(put("/api/v1/attendance/sessions/" + sessA + "/records")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("records", List.of(
                                                Map.of("studentId", stuA1, "status", "PRESENT"),
                                                Map.of("studentId", stuA2, "status", "ABSENT"))))))
                                .andExpect(status().isOk())
                                .andReturn());
                UUID absentRecordId = findRecordIdForStudent(recA.get("records"), stuA2);
                mockMvc.perform(post("/api/v1/attendance/records/" + absentRecordId + "/justify")
                                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("reason", "Certificat médical fourni"))))
                                .andExpect(status().isOk());

                UUID sessB = createAttendanceSessionViaApi(
                                censorToken, classBId, periodId, mathId, "2026-11-12", "MORNING", "08:00", "10:00",
                                false);
                mockMvc.perform(put("/api/v1/attendance/sessions/" + sessB + "/records")
                                .header(HttpHeaders.AUTHORIZATION, bearer(censorToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("records", List.of(
                                                Map.of("studentId", stuB1, "status", "LATE", "lateMinutes", 15))))))
                                .andExpect(status().isOk());

                // 5. Finance (FCFA entiers) :
                // - Facture stuA1 : brut 100 000, bourse approuvée 20 000 -> net 80 000
                // - Paiement CASH 50 000 + Paiement WAVE 30 000 -> contre-passation WAVE 30 000
                // -> net encaissé = 50 000, impayé = 30 000
                // - Remise en attente 5 000 sur stuA1
                // - Clôture de caisse aujourd'hui : attendu 50 000, compté 49 500, écart -500
                // (validée)
                UUID feeId = createFeeItemViaApi(directorToken, yearId, levelId, "SCOL-6E", "Scolarité 6ème", 100_000L);
                UUID invA1 = createAndIssueInvoiceViaApi(secretaryToken, stuA1, yearId, feeId, "2026-10-15");

                UUID scholAdjId = requestAdjustmentViaApi(cashierToken, invA1, "SCHOLARSHIP", 20_000L,
                                "Bourse excellence");
                mockMvc.perform(post("/api/v1/finance/adjustments/" + scholAdjId + "/approve")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("decisionNote", "Approuvée"))))
                                .andExpect(status().isOk());

                UUID cashPayId = recordPaymentViaApi(cashierToken, invA1, 50_000L, "CASH", null, "rpt-pay-cash-1");
                assertThat(cashPayId).isNotNull();
                UUID wavePayId = recordPaymentViaApi(
                                cashierToken, invA1, 30_000L, "MOBILE_MONEY_WAVE", "WAVE-RPT-001", "rpt-pay-wave-1");
                UUID revReqId = requestReversalViaApi(cashierToken, wavePayId, "Erreur double virement");
                mockMvc.perform(post("/api/v1/finance/payment-reversals/" + revReqId + "/approve")
                                .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("decisionNote", "Validé"))))
                                .andExpect(status().isOk());

                requestAdjustmentViaApi(secretaryToken, invA1, "DISCOUNT", 5_000L, "Remise en attente");

                LocalDate todayDakar = LocalDate.now(ZoneId.of("Africa/Dakar"));
                MvcResult closingRes = mockMvc.perform(post("/api/v1/finance/cash-closings")
                                .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                                "closingDate", todayDakar.toString(),
                                                "countedCashXof", 49_500L,
                                                "notes", "Manquant monnaie 500 FCFA"))))
                                .andExpect(status().isCreated())
                                .andReturn();
                UUID closingId = UUID.fromString(readJson(closingRes).get("id").asString());
                mockMvc.perform(post("/api/v1/finance/cash-closings/" + closingId + "/validate")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("validationNote", "Écart constaté"))))
                                .andExpect(status().isOk());

                // 6. Opérations : 1 document scolaire officiel (frais 1 000 FCFA) + 1 import
                // massif exécuté (1 élève)
                mockMvc.perform(post("/api/v1/schooldocs/documents")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                                .header("Idempotency-Key", "rpt-doc-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                                "documentType", "SCHOOLING_CERTIFICATE",
                                                "studentId", stuA1,
                                                "academicYearId", yearId,
                                                "classRoomId", classAId,
                                                "title", "Certificat scolarité",
                                                "body", "Attestation de scolarité officielle.",
                                                "feeAmountXof", 1_000L))))
                                .andExpect(status().isCreated());

                String csvImport = "first_name,last_name,gender,birth_date,admission_date\n"
                                + "Samba,Diagne,MALE,2014-03-10,2026-09-15\n";
                MockMultipartFile importFile = new MockMultipartFile(
                                "file", "students.csv", "text/csv", csvImport.getBytes(StandardCharsets.UTF_8));
                mockMvc.perform(multipart("/api/v1/schoolimport/jobs")
                                .file(importFile)
                                .param("importType", "STUDENTS")
                                .param("validationMode", "STRICT_ALL_OR_NOTHING")
                                .param("duplicateStrategy", "FAIL_ON_DUPLICATE")
                                .param("academicYearId", yearId.toString())
                                .param("executeImmediately", "true")
                                .header("Idempotency-Key", "rpt-import-001")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                                .andExpect(status().isCreated());

                // =========================================================================
                // 7. VÉRIFICATION DES 6 VUES CONSOLIDÉES TEMPS RÉEL
                // =========================================================================

                // 7.1 Rapport exécutif (DIRECTOR / PROMOTER)
                mockMvc.perform(get("/api/v1/reporting/executive")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                                .param("academicYearId", yearId.toString())
                                .param("evaluationPeriodId", periodId.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.schoolCode").value(school.getCode()))
                                .andExpect(jsonPath("$.currency").value("XOF"))
                                .andExpect(jsonPath("$.academic.classCount").value(2))
                                .andExpect(jsonPath("$.academic.totalEnrolledInScope").value(3))
                                .andExpect(jsonPath("$.academic.reportCardCount").value(3))
                                .andExpect(jsonPath("$.academic.passedStudentCount").value(2))
                                .andExpect(jsonPath("$.academic.passRatePercent").value(66.67))
                                .andExpect(jsonPath("$.academic.overallAverage").value(12.00))
                                .andExpect(jsonPath("$.academic.minAverage").value(8.00))
                                .andExpect(jsonPath("$.academic.maxAverage").value(16.00))
                                .andExpect(jsonPath("$.academic.draftReportCardCount").value(1))
                                .andExpect(jsonPath("$.academic.validatedReportCardCount").value(1))
                                .andExpect(jsonPath("$.academic.lockedReportCardCount").value(1))
                                .andExpect(jsonPath("$.attendance.sessionCount").value(2))
                                .andExpect(jsonPath("$.attendance.totalRecords").value(3))
                                .andExpect(jsonPath("$.attendance.presentCount").value(1))
                                .andExpect(jsonPath("$.attendance.lateCount").value(1))
                                .andExpect(jsonPath("$.attendance.totalLateMinutes").value(15))
                                .andExpect(jsonPath("$.attendance.absentCount").value(1))
                                .andExpect(jsonPath("$.attendance.justifiedAbsenceCount").value(1))
                                .andExpect(jsonPath("$.attendance.attendanceRatePercent").value(66.67))
                                .andExpect(jsonPath("$.financial.totalGrossXof").value(100_000))
                                .andExpect(jsonPath("$.financial.totalDiscountXof").value(20_000))
                                .andExpect(jsonPath("$.financial.totalNetXof").value(80_000))
                                .andExpect(jsonPath("$.financial.totalInvoicePaidXof").value(50_000))
                                .andExpect(jsonPath("$.financial.totalOutstandingXof").value(30_000))
                                .andExpect(jsonPath("$.financial.collectionRatePercent").value(62.50))
                                .andExpect(jsonPath("$.financial.approvedScholarshipXof").value(20_000))
                                .andExpect(jsonPath("$.financial.pendingAdjustmentCount").value(1))
                                .andExpect(jsonPath("$.financial.pendingAdjustmentXof").value(5_000))
                                .andExpect(jsonPath("$.financial.collectedPaymentsXof").value(80_000))
                                .andExpect(jsonPath("$.financial.reversalsXof").value(30_000))
                                .andExpect(jsonPath("$.financial.netCollectedXof").value(50_000))
                                .andExpect(jsonPath("$.financial.ledgerNetBalanceXof").value(30_000))
                                .andExpect(jsonPath("$.financial.ledgerConsistentWithInvoices").value(true))
                                .andExpect(jsonPath("$.financial.cashClosingTotalCount").value(1))
                                .andExpect(jsonPath("$.financial.cashClosingValidatedCount").value(1))
                                .andExpect(jsonPath("$.financial.cashClosingDiscrepancyXof").value(-500))
                                .andExpect(jsonPath("$.operations.totalDocuments").value(1))
                                .andExpect(jsonPath("$.operations.totalDocumentFeesXof").value(1_000))
                                .andExpect(jsonPath("$.operations.totalImportJobs").value(1))
                                .andExpect(jsonPath("$.operations.completedImportsCount").value(1))
                                .andExpect(jsonPath("$.operations.totalImportedCreatedRows").value(1));

                // 7.2 Export CSV académique : neutralisation anti-CSV-Injection sur le nom de
                // classe "=CMD|' /C calc'!A0"
                MvcResult csvExportRes = mockMvc.perform(get("/api/v1/reporting/exports")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                                .param("reportType", "ACADEMIC")
                                .param("format", "CSV")
                                .param("academicYearId", yearId.toString())
                                .param("evaluationPeriodId", periodId.toString()))
                                .andExpect(status().isOk())
                                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                                                containsString("attachment")))
                                .andExpect(header().exists("X-Report-Sha256"))
                                .andReturn();
                String csvContent = csvExportRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
                assertThat(csvContent).contains("classe_code,classe_nom,");
                assertThat(csvContent).contains("6E-B,'=CMD|' /C calc'!A0,");
                assertThat(csvContent).doesNotContain("6E-B,=CMD");
                assertThat(csvExportRes.getResponse().getHeader("X-Report-Sha256"))
                                .isEqualTo(ReportSecurityPolicy
                                                .sha256Hex(csvExportRes.getResponse().getContentAsByteArray()));

                // Export JSON financier (CASHIER autorisé)
                MvcResult jsonExportRes = mockMvc.perform(get("/api/v1/reporting/exports")
                                .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                                .param("reportType", "FINANCIAL")
                                .param("format", "JSON")
                                .param("academicYearId", yearId.toString()))
                                .andExpect(status().isOk())
                                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                                .andReturn();
                JsonNode exportedFinance = objectMapper.readTree(jsonExportRes.getResponse().getContentAsString());
                assertThat(exportedFinance.get("totalNetXof").asLong()).isEqualTo(80_000L);
                assertThat(exportedFinance.get("totalOutstandingXof").asLong()).isEqualTo(30_000L);

                // =========================================================================
                // 8. SNAPSHOTS PERSISTÉS : IDEMPOTENCE, SHA-256, ARCHIVAGE, QUOTA STORAGE_MB
                // =========================================================================

                Map<String, Object> snapshotPayload = Map.of(
                                "reportType", "EXECUTIVE",
                                "outputFormat", "JSON",
                                "academicYearId", yearId,
                                "evaluationPeriodId", periodId,
                                "title", "Clôture Trimestre 1");

                MvcResult snapCreateRes = mockMvc.perform(post("/api/v1/reporting/snapshots")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                                .header("Idempotency-Key", "snap-exec-t1-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(snapshotPayload)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.snapshotNumber",
                                                containsString("RPT-" + school.getCode() + "-")))
                                .andExpect(jsonPath("$.snapshotNumber", containsString("-000001")))
                                .andExpect(jsonPath("$.status").value("READY"))
                                .andExpect(jsonPath("$.scopeType").value("SCHOOL"))
                                .andExpect(jsonPath("$.fileSizeBytes").isNumber())
                                .andExpect(jsonPath("$.contentSha256").isNotEmpty())
                                .andReturn();
                JsonNode snapJson = readJson(snapCreateRes);
                UUID snapId = UUID.fromString(snapJson.get("id").asString());
                String snapNumber = snapJson.get("snapshotNumber").asString();
                String snapSha256 = snapJson.get("contentSha256").asString();

                // Rejeu idempotent identique -> 200 OK + X-Idempotent-Replay: true
                mockMvc.perform(post("/api/v1/reporting/snapshots")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                                .header("Idempotency-Key", "snap-exec-t1-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(snapshotPayload)))
                                .andExpect(status().isOk())
                                .andExpect(header().string("X-Idempotent-Replay", "true"))
                                .andExpect(jsonPath("$.id").value(snapId.toString()))
                                .andExpect(jsonPath("$.snapshotNumber").value(snapNumber));

                // Conflit d'idempotence (même clé, payload différent) -> 409 Conflict
                mockMvc.perform(post("/api/v1/reporting/snapshots")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                                .header("Idempotency-Key", "snap-exec-t1-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                                "reportType", "EXECUTIVE",
                                                "outputFormat", "CSV",
                                                "academicYearId", yearId,
                                                "evaluationPeriodId", periodId))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.type")
                                                .value("urn:ecole:problem:reporting-idempotency-conflict"));

                // Téléchargement du snapshot + vérification intégrité SHA-256
                MvcResult snapDlRes = mockMvc.perform(get("/api/v1/reporting/snapshots/" + snapId + "/download")
                                .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken)))
                                .andExpect(status().isOk())
                                .andExpect(header().string("X-Report-Sha256", snapSha256))
                                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                                .andReturn();
                assertThat(ReportSecurityPolicy.sha256Hex(snapDlRes.getResponse().getContentAsByteArray()))
                                .isEqualTo(snapSha256);

                // Archivage logique (motif valide) + journal d'événements + doublon 409
                mockMvc.perform(post("/api/v1/reporting/snapshots/" + snapId + "/archive")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("reason", "Archivage officiel fin de trimestre 1"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                                .andExpect(jsonPath("$.archiveReason").value("Archivage officiel fin de trimestre 1"));

                mockMvc.perform(post("/api/v1/reporting/snapshots/" + snapId + "/archive")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("reason", "Double archivage"))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.type")
                                                .value("urn:ecole:problem:reporting-snapshot-already-archived"));

                mockMvc.perform(get("/api/v1/reporting/snapshots/" + snapId + "/events")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(2))
                                .andExpect(jsonPath("$[0].eventType").value("GENERATED"))
                                .andExpect(jsonPath("$[1].eventType").value("ARCHIVED"));

                // Frontière publique ReportingDirectoryPort & quota STORAGE_MB
                TenantContext.runWithTenant(TenantId.of(school.getId()), () -> {
                        assertThat(reportingDirectoryPort.countSnapshotsForCurrentTenant()).isEqualTo(1L);
                        assertThat(reportingDirectoryPort.totalStorageBytesForCurrentTenant()).isGreaterThan(0L);
                        ReportSnapshotSummary summary = reportingDirectoryPort.findSnapshotByNumber(snapNumber)
                                        .orElseThrow();
                        assertThat(summary.reportType()).isEqualTo(ReportType.EXECUTIVE);
                        assertThat(summary.outputFormat()).isEqualTo(ReportFormat.JSON);
                });

                MvcResult quotasRes = mockMvc.perform(get("/api/v1/subscriptions/current/quotas")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                                .andExpect(status().isOk())
                                .andReturn();
                JsonNode storageQuota = null;
                for (JsonNode resNode : readJson(quotasRes).get("resources")) {
                        if ("STORAGE_MB".equals(resNode.get("resource").asString())) {
                                storageQuota = resNode;
                                break;
                        }
                }
                assertThat(storageQuota).isNotNull();
                assertThat(storageQuota.get("currentUsage").asLong()).isGreaterThanOrEqualTo(3L);

                // =========================================================================
                // 9. MATRICE RBAC STRICTE & PÉRIMÈTRE FIN (TEACHER / PARENT / STUDENT)
                // =========================================================================

                // ADMIN_SAAS refusé partout sur /api/v1/reporting/**
                String adminToken = platformAdminToken();
                mockMvc.perform(get("/api/v1/reporting/executive").header(HttpHeaders.AUTHORIZATION,
                                bearer(adminToken)))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/api/v1/reporting/snapshots").header(HttpHeaders.AUTHORIZATION,
                                bearer(adminToken)))
                                .andExpect(status().isForbidden());

                // CENSOR : academic/attendance/operations OK, studentOverview OK (finance
                // masqué), financial/executive/archive 403
                mockMvc.perform(get("/api/v1/reporting/academic").header(HttpHeaders.AUTHORIZATION,
                                bearer(censorToken)))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/v1/reporting/operations").header(HttpHeaders.AUTHORIZATION,
                                bearer(censorToken)))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/v1/reporting/students/" + stuA1 + "/overview")
                                .header(HttpHeaders.AUTHORIZATION, bearer(censorToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.financialSummary").value(nullValue()));
                mockMvc.perform(get("/api/v1/reporting/financial").header(HttpHeaders.AUTHORIZATION,
                                bearer(censorToken)))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/api/v1/reporting/executive").header(HttpHeaders.AUTHORIZATION,
                                bearer(censorToken)))
                                .andExpect(status().isForbidden());
                mockMvc.perform(post("/api/v1/reporting/snapshots/" + snapId + "/archive")
                                .header(HttpHeaders.AUTHORIZATION, bearer(censorToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("reason", "Tentative censeur"))))
                                .andExpect(status().isForbidden());

                // SECRETARY : academic/operations OK, financial/executive 403
                mockMvc.perform(get("/api/v1/reporting/operations").header(HttpHeaders.AUTHORIZATION,
                                bearer(secretaryToken)))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/v1/reporting/financial").header(HttpHeaders.AUTHORIZATION,
                                bearer(secretaryToken)))
                                .andExpect(status().isForbidden());

                // CASHIER : financial OK, academic/attendance/operations/executive 403
                mockMvc.perform(get("/api/v1/reporting/financial").header(HttpHeaders.AUTHORIZATION,
                                bearer(cashierToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalOutstandingXof").value(30_000));
                mockMvc.perform(get("/api/v1/reporting/academic").header(HttpHeaders.AUTHORIZATION,
                                bearer(cashierToken)))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/api/v1/reporting/operations").header(HttpHeaders.AUTHORIZATION,
                                bearer(cashierToken)))
                                .andExpect(status().isForbidden());

                // TEACHER : limité strictement à 6E-A (stuA1, stuA2) ; 6E-B / stuB1 / financial
                // / snapshot du directeur -> 403
                mockMvc.perform(get("/api/v1/reporting/academic")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                                .param("academicYearId", yearId.toString())
                                .param("evaluationPeriodId", periodId.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.classCount").value(1))
                                .andExpect(jsonPath("$.classes[0].classRoomCode").value("6E-A"))
                                .andExpect(jsonPath("$.reportCardCount").value(2));

                mockMvc.perform(get("/api/v1/reporting/academic")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                                .param("classRoomId", classBId.toString()))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.type").value("urn:ecole:problem:reporting-scope-forbidden"));

                mockMvc.perform(get("/api/v1/reporting/students/" + stuA1 + "/overview")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.financialSummary").value(nullValue()));

                mockMvc.perform(get("/api/v1/reporting/students/" + stuB1 + "/overview")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                                .andExpect(status().isForbidden());

                mockMvc.perform(get("/api/v1/reporting/financial").header(HttpHeaders.AUTHORIZATION,
                                bearer(teacherToken)))
                                .andExpect(status().isForbidden());

                mockMvc.perform(get("/api/v1/reporting/snapshots/" + snapId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                                .andExpect(status().isForbidden());

                // TEACHER peut générer et consulter son propre snapshot sur sa classe affectée
                MvcResult teacherSnapRes = mockMvc.perform(post("/api/v1/reporting/snapshots")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                                .header("Idempotency-Key", "snap-teacher-6ea-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                                "reportType", "ACADEMIC",
                                                "outputFormat", "CSV",
                                                "academicYearId", yearId,
                                                "evaluationPeriodId", periodId,
                                                "classRoomId", classAId))))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.scopeType").value("CLASSROOM"))
                                .andReturn();
                UUID teacherSnapId = UUID.fromString(readJson(teacherSnapRes).get("id").asString());
                mockMvc.perform(get("/api/v1/reporting/snapshots")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(1))
                                .andExpect(jsonPath("$.items[0].id").value(teacherSnapId.toString()));

                // Une fois l'affectation de l'enseignant désactivée, listSnapshots ne doit plus exposer son ancien snapshot
                transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + school.getId() + "', true)");
                        jdbcTemplate.update(
                                        "UPDATE teacher_assignments SET active = false WHERE class_room_id = ?",
                                        classAId);
                });
                mockMvc.perform(get("/api/v1/reporting/snapshots")
                                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(0));
                transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + school.getId() + "', true)");
                        jdbcTemplate.update(
                                        "UPDATE teacher_assignments SET active = true WHERE class_room_id = ?",
                                        classAId);
                });

                // PARENT : accès uniquement à stuA1 (avec finance), refus 403 sur stuA2, stuB1,
                // academic, financial
                mockMvc.perform(get("/api/v1/reporting/students/" + stuA1 + "/overview")
                                .header(HttpHeaders.AUTHORIZATION, bearer(parentToken))
                                .param("academicYearId", yearId.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.studentId").value(stuA1.toString()))
                                .andExpect(jsonPath("$.financialSummary.totalNetXof").value(80_000))
                                .andExpect(jsonPath("$.financialSummary.totalBalanceXof").value(30_000));

                mockMvc.perform(get("/api/v1/reporting/exports")
                                .header(HttpHeaders.AUTHORIZATION, bearer(parentToken))
                                .param("reportType", "STUDENT_OVERVIEW")
                                .param("format", "CSV")
                                .param("studentId", stuA1.toString()))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/v1/reporting/students/" + stuA2 + "/overview")
                                .header(HttpHeaders.AUTHORIZATION, bearer(parentToken)))
                                .andExpect(status().isForbidden());

                mockMvc.perform(get("/api/v1/reporting/academic").header(HttpHeaders.AUTHORIZATION,
                                bearer(parentToken)))
                                .andExpect(status().isForbidden());

                // STUDENT : accès uniquement à son propre dossier (stuA1), refus 403 sur stuA2
                // et academic
                mockMvc.perform(get("/api/v1/reporting/students/" + stuA1 + "/overview")
                                .header(HttpHeaders.AUTHORIZATION, bearer(studentToken))
                                .param("academicYearId", yearId.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.financialSummary.totalPaidXof").value(50_000));

                mockMvc.perform(get("/api/v1/reporting/students/" + stuA2 + "/overview")
                                .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                                .andExpect(status().isForbidden());

                mockMvc.perform(get("/api/v1/reporting/financial").header(HttpHeaders.AUTHORIZATION,
                                bearer(studentToken)))
                                .andExpect(status().isForbidden());

                // 10. Vérification du journal d'audit REPORTING
                mockMvc.perform(get("/api/v1/audit/logs")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                                .param("category", "REPORTING"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements")
                                                .value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));
        }

        @Test
        void shouldEnforceSubscriptionGuardAndMultiTenantIsolationWithPostgresRlsAndImmutability() throws Exception {
                // --- 1. Garde d'abonnement EXPIRED vs SUSPENDED ---
                School expiredSchool = createSchool("RPTEXP", SchoolType.PRIVATE_SCHOOL);
                createSubscription(expiredSchool, SubscriptionStatus.ACTIVE, Instant.now().minus(2, ChronoUnit.DAYS),
                                100, 50);
                String expDirToken = tenantTokenForUser(expiredSchool.getId(), UUID.randomUUID(), RoleCode.DIRECTOR);

                // EXPIRED : lecture et export autorisés (200), génération snapshot refusée
                // (403)
                mockMvc.perform(get("/api/v1/reporting/executive")
                                .header(HttpHeaders.AUTHORIZATION, bearer(expDirToken)))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/v1/reporting/exports")
                                .header(HttpHeaders.AUTHORIZATION, bearer(expDirToken))
                                .param("reportType", "OPERATIONS")
                                .param("format", "CSV"))
                                .andExpect(status().isOk());
                mockMvc.perform(post("/api/v1/reporting/snapshots")
                                .header(HttpHeaders.AUTHORIZATION, bearer(expDirToken))
                                .header("Idempotency-Key", "snap-expired-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("reportType", "OPERATIONS", "outputFormat", "JSON"))))
                                .andExpect(status().isForbidden());

                // SUSPENDED : lecture et écriture refusées (403)
                School suspendedSchool = createSchool("RPTSUSP", SchoolType.PRIVATE_SCHOOL);
                createSubscription(suspendedSchool, SubscriptionStatus.SUSPENDED,
                                Instant.now().plus(30, ChronoUnit.DAYS), 100, 50);
                String suspDirToken = tenantTokenForUser(suspendedSchool.getId(), UUID.randomUUID(), RoleCode.DIRECTOR);

                mockMvc.perform(get("/api/v1/reporting/executive")
                                .header(HttpHeaders.AUTHORIZATION, bearer(suspDirToken)))
                                .andExpect(status().isForbidden());

                // --- 2. Isolation multi-tenant API & PostgreSQL RLS direct sous ecole_app ---
                School schoolA = createSchool("RPTISOA", SchoolType.PRIVATE_SCHOOL);
                School schoolB = createSchool("RPTISOB", SchoolType.PRIVATE_SCHOOL);
                createSubscription(schoolA, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 100,
                                50);
                createSubscription(schoolB, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 100,
                                50);

                User dirA = createTenantUser(schoolA.getId(), "dir@isoa.sn", "Moussa", "Diop", RoleCode.DIRECTOR);
                User dirB = createTenantUser(schoolB.getId(), "dir@isob.sn", "Fatou", "Fall", RoleCode.DIRECTOR);
                String tokenA = tenantTokenForUser(schoolA.getId(), dirA.getId(), RoleCode.DIRECTOR);
                String tokenB = tenantTokenForUser(schoolB.getId(), dirB.getId(), RoleCode.DIRECTOR);

                MvcResult snapARes = mockMvc.perform(post("/api/v1/reporting/snapshots")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                                .header("Idempotency-Key", "iso-snap-a-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("reportType", "OPERATIONS", "outputFormat", "JSON"))))
                                .andExpect(status().isCreated())
                                .andReturn();
                UUID snapAId = UUID.fromString(readJson(snapARes).get("id").asString());

                mockMvc.perform(post("/api/v1/reporting/snapshots")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                                .header("Idempotency-Key", "iso-snap-b-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("reportType", "OPERATIONS", "outputFormat", "CSV"))))
                                .andExpect(status().isCreated());

                // Tenant B ne peut ni lire, ni télécharger, ni archiver le snapshot de A (404),
                // même avec X-Tenant-Id forgé
                mockMvc.perform(get("/api/v1/reporting/snapshots/" + snapAId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                                .header("X-Tenant-Id", schoolA.getId().toString()))
                                .andExpect(status().isNotFound());

                mockMvc.perform(get("/api/v1/reporting/snapshots/" + snapAId + "/download")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                                .andExpect(status().isNotFound());

                mockMvc.perform(post("/api/v1/reporting/snapshots/" + snapAId + "/archive")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("reason", "Tentative cross-tenant"))))
                                .andExpect(status().isNotFound());

                // Vérifications PostgreSQL directes sous le rôle applicatif ecole_app
                // (NOBYPASSRLS)
                List<String> tables = List.of(
                                "reporting_sequences",
                                "reporting_snapshots",
                                "reporting_snapshot_contents",
                                "reporting_snapshot_events");

                // Fail-closed : sans TenantContext, 0 ligne sur les 4 tables
                transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                        jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '', true)");
                        for (String table : tables) {
                                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table,
                                                Integer.class);
                                assertThat(count).as("Fail-closed sans tenant sur " + table).isZero();
                        }
                });

                // Isolation tenant A vs B sous ecole_app
                transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                        jdbcTemplate.execute(
                                        "SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
                        for (String table : tables) {
                                Integer countA = jdbcTemplate.queryForObject(
                                                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Integer.class,
                                                schoolA.getId());
                                Integer countB = jdbcTemplate.queryForObject(
                                                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Integer.class,
                                                schoolB.getId());
                                assertThat(countA).as("Tenant A visible sur " + table).isGreaterThanOrEqualTo(1);
                                assertThat(countB).as("Tenant B invisible pour A sur " + table).isZero();
                        }
                });

                // Rejet INSERT cross-tenant (RLS WITH CHECK)
                assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                        jdbcTemplate.execute(
                                        "SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
                        jdbcTemplate.update(
                                        "INSERT INTO reporting_sequences (tenant_id, sequence_year, last_value) VALUES (?, 2099, 1)",
                                        schoolB.getId());
                })).isInstanceOf(DataAccessException.class);

                // Immutabilité stricte : UPDATE et DELETE interdits sur contents & events
                // (triggers + privilèges)
                assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.execute(
                                        "SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
                        jdbcTemplate.update(
                                        "UPDATE reporting_snapshot_contents SET sha256_hash = 'tampered' WHERE snapshot_id = ?",
                                        snapAId);
                })).isInstanceOf(DataAccessException.class);

                assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.execute(
                                        "SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
                        jdbcTemplate.update(
                                        "DELETE FROM reporting_snapshot_events WHERE snapshot_id = ?",
                                        snapAId);
                })).isInstanceOf(DataAccessException.class);

                // Moindre privilège ecole_app : DELETE refusé sur reporting_snapshots
                assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                        jdbcTemplate.execute(
                                        "SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
                        jdbcTemplate.update("DELETE FROM reporting_snapshots WHERE id = ?", snapAId);
                })).isInstanceOf(DataAccessException.class);
        }

        // --- Helpers ---

        private School createSchool(String prefix, SchoolType type) {
                String code = prefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                return TestSecuritySupport.runAsAdminSaas(() -> schoolService.createSchool(
                                code, "École " + code, type, "+221770000000", "contact@" + code.toLowerCase() + ".sn",
                                "Dakar"));
        }

        private void createSubscription(
                        School school, SubscriptionStatus status, Instant endsAt, int maxStudents, int maxStorageMb) {
                TestSecuritySupport.runAsAdminSaas(() -> {
                        SubscriptionPlan plan = subscriptionService.createPlan(
                                        "PLAN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                                        "Forfait Reporting",
                                        20_000L,
                                        200_000L,
                                        maxStudents,
                                        50,
                                        maxStorageMb);
                        Instant startsAt = endsAt.minus(60, ChronoUnit.DAYS);
                        TenantContext.runWithTenant(TenantId.of(school.getId()), () -> subscriptionService.createSubscription(
                                        plan.getId(), status, BillingCycle.MONTHLY, startsAt, endsAt));
                });
        }

        private User createTenantUser(UUID schoolId, String email, String firstName, String lastName, RoleCode role) {
                return TestSecuritySupport.runAsDirector(() -> TenantContext.executeWithTenant(
                                TenantId.of(schoolId),
                                () -> userAccountService.createUserWithRole(
                                                email, "+221770000001", firstName, lastName, "argon2-test-hash", role)));
        }

        private void linkUserToGuardian(UUID schoolId, UUID guardianId, UUID userId) {
                transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolId + "', true)");
                        jdbcTemplate.update("UPDATE guardians SET user_id = ? WHERE id = ?", userId, guardianId);
                });
        }

        private void linkUserToStudent(UUID schoolId, UUID studentId, UUID userId) {
                transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolId + "', true)");
                        jdbcTemplate.update("UPDATE students SET user_id = ? WHERE id = ?", userId, studentId);
                });
        }

        private UUID createAcademicYearViaApi(String token, String code, String startDate, String endDate)
                        throws Exception {
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
                        String token, UUID yearId, String code, String name, int order, String startDate,
                        String endDate)
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

        private UUID createGradeLevelViaApi(String token, String code, String name, String cycle, int order)
                        throws Exception {
                MvcResult res = mockMvc.perform(post("/api/v1/academic/grade-levels")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("code", code, "name", name, "cycle", cycle, "displayOrder",
                                                order))))
                                .andExpect(status().isCreated())
                                .andReturn();
                return UUID.fromString(readJson(res).get("id").asString());
        }

        private UUID createSubjectViaApi(String token, String code, String name, String category, int defaultCoef)
                        throws Exception {
                MvcResult res = mockMvc.perform(post("/api/v1/academic/subjects")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("code", code, "name", name, "category", category,
                                                "defaultCoefficient", defaultCoef))))
                                .andExpect(status().isCreated())
                                .andReturn();
                return UUID.fromString(readJson(res).get("id").asString());
        }

        private UUID createClassRoomViaApi(String token, UUID yearId, UUID levelId, String code, String name,
                        int capacity)
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

        private UUID createStudentViaApi(String token, String firstName, String lastName, String gender)
                        throws Exception {
                MvcResult res = mockMvc.perform(post("/api/v1/students")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                                "firstName", firstName,
                                                "lastName", lastName,
                                                "gender", gender,
                                                "birthDate", "2014-05-12",
                                                "admissionDate", "2026-09-15"))))
                                .andExpect(status().isCreated())
                                .andReturn();
                return UUID.fromString(readJson(res).get("id").asString());
        }

        private void enrollInClassViaApi(String token, UUID studentId, UUID yearId, UUID classRoomId) throws Exception {
                mockMvc.perform(post("/api/v1/enrollments")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                                "studentId", studentId,
                                                "academicYearId", yearId,
                                                "classRoomId", classRoomId,
                                                "enrollmentType", "NEW_ADMISSION",
                                                "enrollmentDate", "2026-09-20"))))
                                .andExpect(status().isCreated());
        }

        private UUID createFamilyViaApi(String token, String code, String name) throws Exception {
                MvcResult res = mockMvc.perform(post("/api/v1/families")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("code", code, "name", name))))
                                .andExpect(status().isCreated())
                                .andReturn();
                return UUID.fromString(readJson(res).get("id").asString());
        }

        private UUID createGuardianViaApi(
                        String token, UUID familyId, String firstName, String lastName, String phone, String email)
                        throws Exception {
                MvcResult res = mockMvc.perform(post("/api/v1/guardians")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                                "familyId", familyId,
                                                "firstName", firstName,
                                                "lastName", lastName,
                                                "phone", phone,
                                                "email", email))))
                                .andExpect(status().isCreated())
                                .andReturn();
                return UUID.fromString(readJson(res).get("id").asString());
        }

        private void linkGuardianToStudentViaApi(
                        String token, UUID studentId, UUID guardianId, String rel, boolean primary, boolean financial)
                        throws Exception {
                mockMvc.perform(post("/api/v1/students/" + studentId + "/guardians")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                                "guardianId", guardianId,
                                                "relationshipType", rel,
                                                "primaryContact", primary,
                                                "financialResponsible", financial,
                                                "legalCustody", true,
                                                "emergencyContact", true,
                                                "authorizedPickup", true))))
                                .andExpect(status().isCreated());
        }

        private UUID createTeacherViaApi(
                        String token, String code, UUID userId, String firstName, String lastName, String type,
                        String phone)
                        throws Exception {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("employeeCode", code);
                body.put("userId", userId);
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
                        String token, UUID periodId, UUID classId, UUID subjectId,
                        String title, String type, String date, String maxScore, int weight) throws Exception {
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

        private void upsertGradesViaApi(String token, UUID evalId, List<Map<String, Object>> entries) throws Exception {
                mockMvc.perform(put("/api/v1/grades/evaluations/" + evalId + "/entries")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("entries", entries))))
                                .andExpect(status().isOk());
        }

        private JsonNode computeReportCardsViaApi(String token, UUID classId, UUID periodId) throws Exception {
                MvcResult res = mockMvc.perform(post("/api/v1/grades/report-cards/compute")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("classRoomId", classId, "evaluationPeriodId", periodId))))
                                .andExpect(status().isOk())
                                .andReturn();
                return readJson(res);
        }

        private UUID findCardIdForStudent(JsonNode cards, UUID studentId) {
                for (JsonNode card : cards) {
                        if (studentId.toString().equals(card.get("studentId").asString())) {
                                return UUID.fromString(card.get("id").asString());
                        }
                }
                throw new AssertionError("Bulletin introuvable pour " + studentId);
        }

        private UUID createAttendanceSessionViaApi(
                        String token, UUID classId, UUID periodId, UUID subjectId,
                        String sessionDate, String timeSlot, String startTime, String endTime, boolean initPresent)
                        throws Exception {
                MvcResult res = mockMvc.perform(post("/api/v1/attendance/sessions")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                                "classRoomId", classId,
                                                "evaluationPeriodId", periodId,
                                                "subjectId", subjectId,
                                                "sessionDate", sessionDate,
                                                "timeSlot", timeSlot,
                                                "startTime", startTime,
                                                "endTime", endTime,
                                                "initializePresent", initPresent))))
                                .andExpect(status().isCreated())
                                .andReturn();
                return UUID.fromString(readJson(res).get("id").asString());
        }

        private UUID findRecordIdForStudent(JsonNode records, UUID studentId) {
                for (JsonNode rec : records) {
                        if (studentId.toString().equals(rec.get("studentId").asString())) {
                                return UUID.fromString(rec.get("id").asString());
                        }
                }
                throw new AssertionError("Pointage introuvable pour " + studentId);
        }

        private UUID createFeeItemViaApi(
                        String token, UUID yearId, UUID levelId, String code, String label, long amountXof)
                        throws Exception {
                MvcResult res = mockMvc.perform(post("/api/v1/finance/fee-items")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                                "academicYearId", yearId,
                                                "gradeLevelId", levelId,
                                                "code", code,
                                                "label", label,
                                                "category", "TUITION",
                                                "frequency", "ANNUAL",
                                                "amountXof", amountXof,
                                                "mandatory", true))))
                                .andExpect(status().isCreated())
                                .andReturn();
                return UUID.fromString(readJson(res).get("id").asString());
        }

        private UUID createAndIssueInvoiceViaApi(
                        String token, UUID studentId, UUID yearId, UUID feeItemId, String dueDate) throws Exception {
                MvcResult res = mockMvc.perform(post("/api/v1/finance/invoices")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                                "studentId", studentId,
                                                "academicYearId", yearId,
                                                "title", "Facture annuelle",
                                                "dueDate", dueDate,
                                                "lines", List.of(Map.of("feeItemId", feeItemId, "quantity", 1))))))
                                .andExpect(status().isCreated())
                                .andReturn();
                UUID invId = UUID.fromString(readJson(res).get("invoice").get("id").asString());
                mockMvc.perform(post("/api/v1/finance/invoices/" + invId + "/issue")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                                .andExpect(status().isOk());
                return invId;
        }

        private UUID requestAdjustmentViaApi(
                        String token, UUID invoiceId, String type, long fixedAmountXof, String reason)
                        throws Exception {
                MvcResult res = mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/adjustments")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                                "adjustmentType", type,
                                                "calculationMode", "FIXED_AMOUNT",
                                                "fixedAmountXof", fixedAmountXof,
                                                "reason", reason))))
                                .andExpect(status().isCreated())
                                .andReturn();
                return UUID.fromString(readJson(res).get("id").asString());
        }

        private UUID recordPaymentViaApi(
                        String token, UUID invoiceId, long amountXof, String method, String extRef, String idemKey)
                        throws Exception {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("invoiceId", invoiceId);
                body.put("amountXof", amountXof);
                body.put("paymentMethod", method);
                if (extRef != null) {
                        body.put("externalReference", extRef);
                }
                MvcResult res = mockMvc.perform(post("/api/v1/finance/payments")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header("Idempotency-Key", idemKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(body)))
                                .andExpect(status().isCreated())
                                .andReturn();
                return UUID.fromString(readJson(res).get("id").asString());
        }

        private UUID requestReversalViaApi(String token, UUID paymentId, String reason) throws Exception {
                MvcResult res = mockMvc.perform(post("/api/v1/finance/payment-reversals")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("paymentId", paymentId, "reason", reason))))
                                .andExpect(status().isCreated())
                                .andReturn();
                return UUID.fromString(readJson(res).get("id").asString());
        }

        private String platformAdminToken() {
                return tokenIssuer
                                .issueAccessToken(AccountRef.platformAdmin(UUID.randomUUID()),
                                                Set.of(RoleCode.ADMIN_SAAS.name()))
                                .value();
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
