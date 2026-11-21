package com.ecole.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.ecole.saas.school.application.SchoolService;
import com.ecole.saas.school.domain.School;
import com.ecole.saas.school.domain.SchoolType;
import com.ecole.saas.schooldocs.DocumentStatus;
import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.IssuedDocumentSummary;
import com.ecole.saas.schooldocs.SchoolDocsDirectoryPort;
import com.ecole.saas.schooldocs.domain.DocumentSecurityPolicy;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import com.ecole.saas.subscription.application.SubscriptionService;
import com.ecole.saas.subscription.domain.BillingCycle;
import com.ecole.saas.subscription.domain.SubscriptionPlan;
import com.ecole.saas.subscription.domain.SubscriptionStatus;
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
class SchoolDocsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final SchoolService schoolService;
    private final SubscriptionService subscriptionService;
    private final UserAccountService userAccountService;
    private final SchoolDocsDirectoryPort schoolDocsDirectoryPort;
    private final TokenIssuer tokenIssuer;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SchoolDocsIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            SchoolService schoolService,
            SubscriptionService subscriptionService,
            UserAccountService userAccountService,
            SchoolDocsDirectoryPort schoolDocsDirectoryPort,
            TokenIssuer tokenIssuer,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.schoolService = schoolService;
        this.subscriptionService = subscriptionService;
        this.userAccountService = userAccountService;
        this.schoolDocsDirectoryPort = schoolDocsDirectoryPort;
        this.tokenIssuer = tokenIssuer;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldManageTemplatesIssueOfficialDocumentsReissueRevokeVerifyDownloadAuditAndSummarize() throws Exception {
        School school = createSchool("DOCA", SchoolType.PRIVATE_SCHOOL);
        createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 500);

        User director = createTenantUser(school.getId(), "dir@doca.sn", "Mamadou", "Diop", RoleCode.DIRECTOR);
        String directorToken = tenantTokenForUser(school.getId(), director.getId(), RoleCode.DIRECTOR);

        UUID yearId = createAcademicYearViaApi(directorToken, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(directorToken, yearId);
        UUID levelId = createGradeLevelViaApi(directorToken, "6E", "Sixième", "MIDDLE_SCHOOL", 1);
        UUID classRoomId = createClassRoomViaApi(directorToken, yearId, levelId, "6E-A", "Sixième A", 40);

        UUID studentId = createStudentViaApi(directorToken, "Awa", "Ndiaye", "FEMALE", "+221771110001", "awa@doca.sn");
        enrollInClassViaApi(directorToken, studentId, yearId, classRoomId);

        UUID familyId = createFamilyViaApi(directorToken, "FAM-NDIAYE", "Famille Ndiaye");
        UUID guardianId = createGuardianViaApi(
                directorToken, familyId, "Fatou", "Ndiaye", "+221772220001", "fatou@doca.sn");
        linkGuardianToStudentViaApi(directorToken, studentId, guardianId, "MOTHER", true, true);

        // 1. CRUD modèle + aperçu + désactivation (blocage émission) + réactivation
        MvcResult tplRes = mockMvc.perform(post("/api/v1/schooldocs/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "cert_scolarite_std",
                                "name", "Certificat de scolarité standard",
                                "description", "Modèle officiel pour démarches administratives",
                                "documentType", "SCHOOLING_CERTIFICATE",
                                "language", "fr",
                                "titleTemplate", "Certificat de scolarité {{academicYear.code}}",
                                "bodyTemplate", "Le Directeur de {{school.name}} atteste que l'élève {{student.fullName}} "
                                        + "(matricule {{student.matricule}}) est inscrit(e) en classe de {{classRoom.name}} "
                                        + "pour l'année {{academicYear.label}}. Motif : {{purpose}}.",
                                "footerTemplate", "Fait à Dakar le {{document.issueDate}} — Réf {{document.number}}",
                                "defaultValidityDays", 90,
                                "feeAmountXof", 1000))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CERT_SCOLARITE_STD"))
                .andExpect(jsonPath("$.feeAmountXof").value(1000))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        UUID templateId = UUID.fromString(readJson(tplRes).get("id").asString());

        // Mise à jour modèle
        mockMvc.perform(put("/api/v1/schooldocs/templates/" + templateId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Certificat de scolarité officiel",
                                "description", "Modèle officiel mis à jour",
                                "documentType", "SCHOOLING_CERTIFICATE",
                                "language", "fr",
                                "titleTemplate", "Certificat de scolarité {{academicYear.code}}",
                                "bodyTemplate", "Le Directeur de {{school.name}} certifie que {{student.fullName}} "
                                        + "(matricule {{student.matricule}}) est régulièrement inscrit(e) en {{classRoom.name}} "
                                        + "({{academicYear.label}}). Motif : {{purpose}}.",
                                "footerTemplate", "Fait à Dakar le {{document.issueDate}} — N° {{document.number}}",
                                "defaultValidityDays", 90,
                                "feeAmountXof", 1000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Certificat de scolarité officiel"));

        // Prévisualisation
        mockMvc.perform(post("/api/v1/schooldocs/templates/" + templateId + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("variables", Map.of("purpose", "Bourse scolaire")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renderedBody", containsString("Bourse scolaire")));

        // Désactivation -> émission refusée en 409
        mockMvc.perform(post("/api/v1/schooldocs/templates/" + templateId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "issue-inactive-tpl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "templateId", templateId,
                                "studentId", studentId,
                                "variables", Map.of("purpose", "Dossier administratif")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:schooldocs-template-inactive"));

        // Réactivation
        mockMvc.perform(post("/api/v1/schooldocs/templates/" + templateId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        // 2. Émission certificat de scolarité + contrôle idempotence (rejeu 200 et conflit 409)
        Map<String, Object> issuePayload = Map.of(
                "templateId", templateId,
                "studentId", studentId,
                "variables", Map.of("purpose", "Dossier de bourse nationale"));
        MvcResult doc1Res = mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "issue-cert-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(issuePayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber", containsString("DOC-" + school.getCode() + "-")))
                .andExpect(jsonPath("$.documentNumber", containsString("-000001")))
                .andExpect(jsonPath("$.documentType").value("SCHOOLING_CERTIFICATE"))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.validityStatus").value("VALID"))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.editionNumber").value(1))
                .andExpect(jsonPath("$.feeAmountXof").value(1000))
                .andExpect(jsonPath("$.renderedBody", containsString("Awa Ndiaye")))
                .andExpect(jsonPath("$.renderedBody", containsString("Sixième A")))
                .andReturn();
        JsonNode doc1Json = readJson(doc1Res);
        UUID doc1Id = UUID.fromString(doc1Json.get("id").asString());
        String doc1Number = doc1Json.get("documentNumber").asString();
        String doc1Token = doc1Json.get("verificationToken").asString();
        String doc1Sha256 = doc1Json.get("contentSha256").asString();

        // Rejeu idempotent identique -> 200 + X-Idempotent-Replay: true
        mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "issue-cert-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(issuePayload)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(doc1Id.toString()))
                .andExpect(jsonPath("$.documentNumber").value(doc1Number));

        // Même clé d'idempotence avec payload différent -> 409 Conflict
        mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "issue-cert-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "templateId", templateId,
                                "studentId", studentId,
                                "variables", Map.of("purpose", "Autre motif")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:schooldocs-idempotency-conflict"));

        // 3. Émission des autres types officiels (inscription, assiduité, réussite, convocation avec auto-tuteur)
        mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "issue-enroll-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "documentType", "ENROLLMENT_ATTESTATION",
                                "studentId", studentId,
                                "title", "Attestation d'inscription {{academicYear.code}}",
                                "body", "Atteste l'inscription de {{student.fullName}} en {{classRoom.name}}.",
                                "feeAmountXof", 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber", containsString("-000002")));

        mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "issue-att-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "documentType", "ATTENDANCE_ATTESTATION",
                                "studentId", studentId,
                                "title", "Attestation d'assiduité",
                                "body", "{{student.fullName}} présente un taux de présence de {{rate}}.",
                                "variables", Map.of("rate", "98,5 %"),
                                "feeAmountXof", 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber", containsString("-000003")));

        mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "issue-success-004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "documentType", "SUCCESS_ATTESTATION",
                                "studentId", studentId,
                                "title", "Attestation de réussite",
                                "body", "{{student.fullName}} est admise en classe supérieure avec mention Bien.",
                                "feeAmountXof", 500))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber", containsString("-000004")));

        mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "issue-summons-005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "documentType", "SUMMONS",
                                "studentId", studentId,
                                "title", "Convocation parent d'élève",
                                "body", "Mme/M. {{guardian.fullName}} est prié(e) de se présenter à la direction.",
                                "feeAmountXof", 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber", containsString("-000005")))
                .andExpect(jsonPath("$.guardianId").value(guardianId.toString()))
                .andExpect(jsonPath("$.renderedBody", containsString("Fatou Ndiaye")));

        // 4. Téléchargement binaire PDF sécurisé + vérification octets %PDF-1.4 & SHA-256
        MvcResult downloadRes = mockMvc.perform(get("/api/v1/schooldocs/documents/" + doc1Id + "/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string("X-Document-Sha256", doc1Sha256))
                .andReturn();
        byte[] pdfBytes = downloadRes.getResponse().getContentAsByteArray();
        DocumentSecurityPolicy.assertIntegrity(pdfBytes, doc1Sha256);
        String pdfText = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        assertThat(pdfText).startsWith("%PDF-1.4").contains(doc1Number).contains(doc1Token).endsWith("%%EOF\n");

        // 5. Duplicata / réédition tracée (édition 2 depuis original, puis édition 3 depuis édition 2)
        MvcResult dup2Res = mockMvc.perform(post("/api/v1/schooldocs/documents/" + doc1Id + "/reissue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "reissue-doc1-ed2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "reason", "Original égaré lors du dépôt de dossier",
                                "feeAmountXof", 500))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber", containsString("-000006")))
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.editionNumber").value(2))
                .andExpect(jsonPath("$.originalDocumentId").value(doc1Id.toString()))
                .andExpect(jsonPath("$.reissueReason").value("Original égaré lors du dépôt de dossier"))
                .andReturn();
        UUID dup2Id = UUID.fromString(readJson(dup2Res).get("id").asString());

        // Rejeu idempotent de la réédition -> 200 OK
        mockMvc.perform(post("/api/v1/schooldocs/documents/" + doc1Id + "/reissue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "reissue-doc1-ed2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "reason", "Original égaré lors du dépôt de dossier",
                                "feeAmountXof", 500))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(dup2Id.toString()));

        // Édition 3 demandée depuis le duplicata 2 -> pointe vers l'original racine doc1Id
        mockMvc.perform(post("/api/v1/schooldocs/documents/" + dup2Id + "/reissue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "reissue-doc1-ed3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Exemplaire supplémentaire pour l'ambassade"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber", containsString("-000007")))
                .andExpect(jsonPath("$.editionNumber").value(3))
                .andExpect(jsonPath("$.originalDocumentId").value(doc1Id.toString()));

        // Chaîne complète des duplicatas (éditions 1, 2, 3) et événements immuables sur l'original
        mockMvc.perform(get("/api/v1/schooldocs/documents/" + doc1Id + "/duplicates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].editionNumber").value(1))
                .andExpect(jsonPath("$[1].editionNumber").value(2))
                .andExpect(jsonPath("$[2].editionNumber").value(3));

        mockMvc.perform(get("/api/v1/schooldocs/documents/" + doc1Id + "/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].eventType").value("ISSUED"))
                .andExpect(jsonPath("$[1].eventType").value("DUPLICATE_ISSUED"))
                .andExpect(jsonPath("$[2].eventType").value("DUPLICATE_ISSUED"));

        // 6. Vérification d'authenticité VALID puis révocation -> REVOKED + blocage réédition
        mockMvc.perform(get("/api/v1/schooldocs/verify/" + doc1Token)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentNumber").value(doc1Number))
                .andExpect(jsonPath("$.validityStatus").value("VALID"))
                .andExpect(jsonPath("$.authentic").value(true))
                .andExpect(jsonPath("$.integrityVerified").value(true));

        mockMvc.perform(post("/api/v1/schooldocs/documents/" + doc1Id + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Erreur administrative détectée après émission"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.validityStatus").value("REVOKED"));

        mockMvc.perform(get("/api/v1/schooldocs/verify/" + doc1Token)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validityStatus").value("REVOKED"))
                .andExpect(jsonPath("$.authentic").value(false))
                .andExpect(jsonPath("$.revocationReason").value("Erreur administrative détectée après émission"));

        // Double révocation -> 409 ; réédition d'un révoqué -> 409
        mockMvc.perform(post("/api/v1/schooldocs/documents/" + doc1Id + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Double tentative de révocation"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:document-already-revoked"));

        mockMvc.perform(post("/api/v1/schooldocs/documents/" + doc1Id + "/reissue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "reissue-revoked-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Tentative de duplicata sur révoqué"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:document-revoked"));

        // 7. Synthèse SQL /summary + quota STORAGE_MB + frontière publique SchoolDocsDirectoryPort
        mockMvc.perform(get("/api/v1/schooldocs/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTemplates").value(1))
                .andExpect(jsonPath("$.totalDocuments").value(7))
                .andExpect(jsonPath("$.revokedDocuments").value(1))
                .andExpect(jsonPath("$.validDocuments").value(6))
                .andExpect(jsonPath("$.duplicateDocuments").value(2))
                .andExpect(jsonPath("$.documentsByType.SCHOOLING_CERTIFICATE").value(3))
                .andExpect(jsonPath("$.totalStorageMiB").value(1))
                .andExpect(jsonPath("$.totalFeeAmountXof").value(3000));

        mockMvc.perform(get("/api/v1/subscriptions/current/quotas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources[?(@.resource == 'STORAGE_MB')].currentUsage")
                        .value(org.hamcrest.Matchers.contains(1)));

        TenantContext.runWithTenant(TenantId.of(school.getId()), () -> {
            assertThat(schoolDocsDirectoryPort.countDocumentsForCurrentTenant()).isEqualTo(7L);
            assertThat(schoolDocsDirectoryPort.totalStorageBytesForCurrentTenant()).isPositive();
            IssuedDocumentSummary summary = schoolDocsDirectoryPort.findDocumentByNumber(doc1Number).orElseThrow();
            assertThat(summary.status()).isEqualTo(DocumentStatus.REVOKED);
            assertThat(summary.documentType()).isEqualTo(DocumentType.SCHOOLING_CERTIFICATE);
            assertThat(schoolDocsDirectoryPort.findDocumentsByStudentId(studentId)).hasSize(7);
        });

        // 8. Audit immuable SCHOOLDOCS sans aucune fuite du jeton opaque de vérification
        MvcResult auditRes = mockMvc.perform(get("/api/v1/audit/logs?category=SCHOOLDOCS&size=50")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(10)))
                .andReturn();
        String auditBody = auditRes.getResponse().getContentAsString();
        assertThat(auditBody)
                .contains("SCHOOLDOCS_TEMPLATE_CREATED")
                .contains("SCHOOLDOCS_DOCUMENT_ISSUED")
                .contains("SCHOOLDOCS_DOCUMENT_REISSUED")
                .contains("SCHOOLDOCS_DOCUMENT_REVOKED")
                .contains("SCHOOLDOCS_DOCUMENT_DOWNLOADED")
                .doesNotContain(doc1Token);
    }

    @Test
    void shouldEnforceRbacInactiveTemplateVisibilityAndStudentParentTeacherScopes() throws Exception {
        School school = createSchool("DOCRBAC", SchoolType.PRIVATE_SCHOOL);
        createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 500);

        User director = createTenantUser(school.getId(), "dir@docrbac.sn", "Aliou", "Sall", RoleCode.DIRECTOR);
        User secretary = createTenantUser(school.getId(), "sec@docrbac.sn", "Mariama", "Ba", RoleCode.SECRETARY);
        User censor = createTenantUser(school.getId(), "cen@docrbac.sn", "Cheikh", "Gueye", RoleCode.CENSOR);
        User promoter = createTenantUser(school.getId(), "pro@docrbac.sn", "Oumar", "Sy", RoleCode.PROMOTER);
        User cashier = createTenantUser(school.getId(), "cai@docrbac.sn", "Ndeye", "Faye", RoleCode.CASHIER);
        User teacherUser = createTenantUser(school.getId(), "prof@docrbac.sn", "Modou", "Sow", RoleCode.TEACHER);
        User parentUser = createTenantUser(school.getId(), "parent@docrbac.sn", "Binta", "Diallo", RoleCode.PARENT);
        User studentUser1 = createTenantUser(school.getId(), "eleve1@docrbac.sn", "Khadija", "Diallo", RoleCode.STUDENT);

        String directorToken = tenantTokenForUser(school.getId(), director.getId(), RoleCode.DIRECTOR);
        String secretaryToken = tenantTokenForUser(school.getId(), secretary.getId(), RoleCode.SECRETARY);
        String censorToken = tenantTokenForUser(school.getId(), censor.getId(), RoleCode.CENSOR);
        String promoterToken = tenantTokenForUser(school.getId(), promoter.getId(), RoleCode.PROMOTER);
        String cashierToken = tenantTokenForUser(school.getId(), cashier.getId(), RoleCode.CASHIER);
        String teacherToken = tenantTokenForUser(school.getId(), teacherUser.getId(), RoleCode.TEACHER);
        String parentToken = tenantTokenForUser(school.getId(), parentUser.getId(), RoleCode.PARENT);
        String student1Token = tenantTokenForUser(school.getId(), studentUser1.getId(), RoleCode.STUDENT);
        String adminSaasToken = platformAdminToken();

        // Deux classes 6E-A et 5E-B, deux élèves
        UUID yearId = createAcademicYearViaApi(directorToken, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(directorToken, yearId);
        UUID levelId = createGradeLevelViaApi(directorToken, "COL", "Collège", "MIDDLE_SCHOOL", 1);
        UUID mathId = createSubjectViaApi(directorToken, "MATH", "Mathématiques", "SCIENTIFIC", 4);
        UUID class6A = createClassRoomViaApi(directorToken, yearId, levelId, "6E-A", "Sixième A", 40);
        UUID class5B = createClassRoomViaApi(directorToken, yearId, levelId, "5E-B", "Cinquième B", 40);
        assignSubjectToClassViaApi(directorToken, class6A, mathId);

        UUID student1 = createStudentViaApi(directorToken, "Khadija", "Diallo", "FEMALE", "+221771110011", "k@docrbac.sn");
        UUID student2 = createStudentViaApi(directorToken, "Ibrahima", "Kane", "MALE", "+221771110012", "i@docrbac.sn");
        enrollInClassViaApi(directorToken, student1, yearId, class6A);
        enrollInClassViaApi(directorToken, student2, yearId, class5B);
        linkUserToStudent(school.getId(), student1, studentUser1.getId());

        UUID familyId = createFamilyViaApi(directorToken, "FAM-DIALLO", "Famille Diallo");
        UUID guardian1 = createGuardianViaApi(
                directorToken, familyId, "Binta", "Diallo", "+221772220011", "parent@docrbac.sn");
        linkUserToGuardian(school.getId(), guardian1, parentUser.getId());
        linkGuardianToStudentViaApi(directorToken, student1, guardian1, "MOTHER", true, true);

        // Enseignant affecté uniquement à 6E-A
        UUID teacherId = createTeacherViaApi(
                directorToken, "ENS-10", teacherUser.getId(), "Modou", "Sow", "PERMANENT", "+221773330010");
        assignTeacherToClassViaApi(directorToken, teacherId, yearId, class6A, mathId);

        // Un modèle actif et un modèle désactivé
        MvcResult activeTplRes = mockMvc.perform(post("/api/v1/schooldocs/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "TPL_ACTIF",
                                "name", "Modèle actif",
                                "documentType", "SCHOOLING_CERTIFICATE",
                                "titleTemplate", "Certificat",
                                "bodyTemplate", "Élève {{student.fullName}}",
                                "feeAmountXof", 0))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID activeTplId = UUID.fromString(readJson(activeTplRes).get("id").asString());

        MvcResult inactiveTplRes = mockMvc.perform(post("/api/v1/schooldocs/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "TPL_INACTIF",
                                "name", "Modèle archivé",
                                "documentType", "SUMMONS",
                                "titleTemplate", "Convocation archivée",
                                "bodyTemplate", "Archivé {{student.fullName}}",
                                "feeAmountXof", 0))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID inactiveTplId = UUID.fromString(readJson(inactiveTplRes).get("id").asString());
        mockMvc.perform(post("/api/v1/schooldocs/templates/" + inactiveTplId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk());

        // Forçage active = true hors PROMOTER / DIRECTOR / CENTER_MANAGER / SECRETARY
        mockMvc.perform(get("/api/v1/schooldocs/templates?active=false")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("TPL_INACTIF"));

        for (String nonManagerToken : List.of(censorToken, cashierToken, teacherToken)) {
            mockMvc.perform(get("/api/v1/schooldocs/templates?active=false")
                            .header(HttpHeaders.AUTHORIZATION, bearer(nonManagerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].code").value("TPL_ACTIF"));

            mockMvc.perform(get("/api/v1/schooldocs/templates/" + inactiveTplId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(nonManagerToken)))
                    .andExpect(status().isNotFound());

            mockMvc.perform(post("/api/v1/schooldocs/templates/" + inactiveTplId + "/preview")
                            .header(HttpHeaders.AUTHORIZATION, bearer(nonManagerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("variables", Map.of()))))
                    .andExpect(status().isNotFound());
        }

        // Émission d'un document pour student1 (6E-A) et un pour student2 (5E-B)
        MvcResult docS1Res = mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .header("Idempotency-Key", "rbac-s1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("templateId", activeTplId, "studentId", student1))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID docS1Id = UUID.fromString(readJson(docS1Res).get("id").asString());
        String docS1Token = readJson(docS1Res).get("verificationToken").asString();

        MvcResult docS2Res = mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .header("Idempotency-Key", "rbac-s2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("templateId", activeTplId, "studentId", student2))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID docS2Id = UUID.fromString(readJson(docS2Res).get("id").asString());
        String docS2Token = readJson(docS2Res).get("verificationToken").asString();

        // CENSOR : autorisé sur SUMMONS, refusé sur SCHOOLING_CERTIFICATE, refusé sur revoke et création modèle
        mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(censorToken))
                        .header("Idempotency-Key", "censor-summons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "documentType", "SUMMONS",
                                "studentId", student1,
                                "title", "Convocation discipline",
                                "body", "Entretien disciplinaire pour {{student.fullName}}."))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(censorToken))
                        .header("Idempotency-Key", "censor-cert-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("templateId", activeTplId, "studentId", student1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:schooldocs-censor-type-forbidden"));

        mockMvc.perform(post("/api/v1/schooldocs/documents/" + docS1Id + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, bearer(censorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Révocation non autorisée pour censeur"))))
                .andExpect(status().isForbidden());

        // SECRETARY : refusé sur revoke ; PROMOTER : refusé sur issue mais autorisé sur revoke ; CASHIER : refusé sur summary & issue
        mockMvc.perform(post("/api/v1/schooldocs/documents/" + docS1Id + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Révocation refusée au secrétariat"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken))
                        .header("Idempotency-Key", "promoter-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("templateId", activeTplId, "studentId", student1))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/schooldocs/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/schooldocs/documents/" + docS1Id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken)))
                .andExpect(status().isOk());

        // Périmètre TEACHER (6E-A uniquement : voit student1, refusé 403 sur student2)
        mockMvc.perform(get("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/schooldocs/documents/" + docS1Id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/schooldocs/documents/" + docS1Id + "/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/schooldocs/documents/" + docS2Id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/schooldocs/documents?classRoomId=" + class5B)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/schooldocs/verify/" + docS2Token)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isForbidden());

        // Périmètre PARENT (student1 uniquement : voit/télécharge/vérifie student1, refusé 403 sur student2 et modèles)
        mockMvc.perform(get("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/schooldocs/documents/" + docS1Id + "/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/schooldocs/verify/" + docS1Token)
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/schooldocs/documents/" + docS2Id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/schooldocs/documents/" + docS2Id + "/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/schooldocs/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken)))
                .andExpect(status().isForbidden());

        // Périmètre STUDENT (student1 uniquement : voit/télécharge/vérifie ses documents, refusé 403 sur student2)
        mockMvc.perform(get("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/schooldocs/documents/" + docS1Id + "/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student1Token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/schooldocs/documents/" + docS2Id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student1Token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/schooldocs/documents?studentId=" + student2)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student1Token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/schooldocs/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student1Token)))
                .andExpect(status().isForbidden());

        // ADMIN_SAAS refusé 403 sur toutes les routes tenant schooldocs
        mockMvc.perform(get("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminSaasToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/schooldocs/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminSaasToken)))
                .andExpect(status().isForbidden());

        // PROMOTER peut révoquer docS1
        mockMvc.perform(post("/api/v1/schooldocs/documents/" + docS1Id + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Révocation approuvée par le promoteur"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    void shouldEnforceSubscriptionGuardStorageQuotaAndBoxedDtoValidation() throws Exception {
        // 1. Abonnement EXPIRED : lecture + téléchargement autorisés (200), écriture refusée (403)
        School expiredSchool = createSchool("DOCEXP", SchoolType.PRIVATE_SCHOOL);
        createSubscription(expiredSchool, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 50);
        User dirExp = createTenantUser(expiredSchool.getId(), "dir@docexp.sn", "Dir", "Exp", RoleCode.DIRECTOR);
        String dirExpToken = tenantTokenForUser(expiredSchool.getId(), dirExp.getId(), RoleCode.DIRECTOR);

        UUID studentExp = createStudentViaApi(dirExpToken, "Moussa", "Ba", "MALE", null, null);
        MvcResult docRes = mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirExpToken))
                        .header("Idempotency-Key", "exp-doc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "documentType", "SCHOOLING_CERTIFICATE",
                                "studentId", studentExp,
                                "title", "Certificat",
                                "body", "Atteste la scolarité de {{student.fullName}}."))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID docId = UUID.fromString(readJson(docRes).get("id").asString());

        // Validation DTO boxés (@NotNull sur feeAmountXof absent -> 400)
        mockMvc.perform(post("/api/v1/schooldocs/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirExpToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "NO_FEE",
                                "name", "Sans frais explicite",
                                "documentType", "SCHOOLING_CERTIFICATE",
                                "titleTemplate", "Titre",
                                "bodyTemplate", "Corps"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:validation-error"));

        // Bascule l'abonnement en expiré dans le passé
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + expiredSchool.getId() + "', true)");
            jdbcTemplate.update(
                    "UPDATE subscriptions SET ends_at = now() - interval '2 days' WHERE tenant_id = ?",
                    expiredSchool.getId());
        });

        mockMvc.perform(get("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirExpToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/schooldocs/documents/" + docId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirExpToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirExpToken))
                        .header("Idempotency-Key", "exp-write-blocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "documentType", "SCHOOLING_CERTIFICATE",
                                "studentId", studentExp,
                                "title", "Bloqué",
                                "body", "Bloqué"))))
                .andExpect(status().isForbidden());

        // 2. Abonnement SUSPENDED : lecture et écriture refusées (403)
        School suspendedSchool = createSchool("DOCSUS", SchoolType.TUTORING_CENTER);
        createSubscription(suspendedSchool, SubscriptionStatus.SUSPENDED, Instant.now().plus(30, ChronoUnit.DAYS), 50);
        String dirSusToken = tenantToken(suspendedSchool.getId(), RoleCode.CENTER_MANAGER);

        mockMvc.perform(get("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirSusToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/schooldocs/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirSusToken)))
                .andExpect(status().isForbidden());

        // 3. Validité EXPIRED d'un document dépassant valid_until + dépassement quota STORAGE_MB (409)
        School quotaSchool = createSchool("DOCQTA", SchoolType.PRIVATE_SCHOOL);
        createSubscription(quotaSchool, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 1);
        User dirQuota = createTenantUser(quotaSchool.getId(), "dir@docqta.sn", "Dir", "Quota", RoleCode.DIRECTOR);
        String dirQuotaToken = tenantTokenForUser(quotaSchool.getId(), dirQuota.getId(), RoleCode.DIRECTOR);

        UUID studentQuota = createStudentViaApi(dirQuotaToken, "Samba", "Dieng", "MALE", null, null);
        MvcResult expiringRes = mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirQuotaToken))
                        .header("Idempotency-Key", "quota-doc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "documentType", "SCHOOLING_CERTIFICATE",
                                "studentId", studentQuota,
                                "title", "Certificat temporaire",
                                "body", "Atteste la scolarité de {{student.fullName}}.",
                                "validityDays", 1))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID expiringDocId = UUID.fromString(readJson(expiringRes).get("id").asString());
        String expiringToken = readJson(expiringRes).get("verificationToken").asString();

        // Force issued_at / valid_until dans le passé et file_size_bytes à 1 MiB exact pour tester EXPIRED + quota STORAGE_MB
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + quotaSchool.getId() + "', true)");
            jdbcTemplate.update(
                    "UPDATE schooldocs_documents "
                            + "SET issued_at = now() - interval '10 days', "
                            + "    valid_until = now() - interval '1 day', "
                            + "    file_size_bytes = 1048576 "
                            + "WHERE id = ?",
                    expiringDocId);
        });

        mockMvc.perform(get("/api/v1/schooldocs/verify/" + expiringToken)
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirQuotaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validityStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.authentic").value(false));

        // Émission suivante dépasse le plafond de 1 Mo -> 409 subscription-quota-exceeded
        mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirQuotaToken))
                        .header("Idempotency-Key", "quota-doc-exceeded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "documentType", "ENROLLMENT_ATTESTATION",
                                "studentId", studentQuota,
                                "title", "Dépassement stockage",
                                "body", "Ne doit pas passer le quota de 1 Mo."))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-quota-exceeded"));
    }

    @Test
    void shouldStrictlyIsolateTenantsAtRestAndPostgresqlRlsAndEnforceSqlImmutability() throws Exception {
        School schoolA = createSchool("DOCISOA", SchoolType.PRIVATE_SCHOOL);
        School schoolB = createSchool("DOCISOB", SchoolType.PRIVATE_SCHOOL);
        createSubscription(schoolA, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 500);
        createSubscription(schoolB, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS), 500);

        User dirA = createTenantUser(schoolA.getId(), "dir@docisoa.sn", "Dir", "A", RoleCode.DIRECTOR);
        User dirB = createTenantUser(schoolB.getId(), "dir@docisob.sn", "Dir", "B", RoleCode.DIRECTOR);
        String tokenA = tenantTokenForUser(schoolA.getId(), dirA.getId(), RoleCode.DIRECTOR);
        String tokenB = tenantTokenForUser(schoolB.getId(), dirB.getId(), RoleCode.DIRECTOR);

        UUID studentA = createStudentViaApi(tokenA, "Amina", "A", "FEMALE", null, null);
        UUID studentB = createStudentViaApi(tokenB, "Bacar", "B", "MALE", null, null);

        // Mêmes codes de modèles dans A et B (indépendants)
        MvcResult tplARes = mockMvc.perform(post("/api/v1/schooldocs/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "CERT_STD",
                                "name", "Modèle A",
                                "documentType", "SCHOOLING_CERTIFICATE",
                                "titleTemplate", "Certificat A",
                                "bodyTemplate", "Élève {{student.fullName}}",
                                "feeAmountXof", 0))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID tplAId = UUID.fromString(readJson(tplARes).get("id").asString());

        MvcResult tplBRes = mockMvc.perform(post("/api/v1/schooldocs/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "CERT_STD",
                                "name", "Modèle B",
                                "documentType", "SCHOOLING_CERTIFICATE",
                                "titleTemplate", "Certificat B",
                                "bodyTemplate", "Élève {{student.fullName}}",
                                "feeAmountXof", 0))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID tplBId = UUID.fromString(readJson(tplBRes).get("id").asString());

        // Séquences indépendantes (-000001 dans A et -000001 dans B)
        MvcResult docARes = mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("Idempotency-Key", "iso-same-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("templateId", tplAId, "studentId", studentA))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber", containsString("-000001")))
                .andReturn();
        UUID docAId = UUID.fromString(readJson(docARes).get("id").asString());

        MvcResult docBRes = mockMvc.perform(post("/api/v1/schooldocs/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .header("Idempotency-Key", "iso-same-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("templateId", tplBId, "studentId", studentB))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber", containsString("-000001")))
                .andReturn();
        UUID docBId = UUID.fromString(readJson(docBRes).get("id").asString());
        String tokenVerifyB = readJson(docBRes).get("verificationToken").asString();

        // A forge X-Tenant-Id et ?tenantId vers B : ne voit que A, reçoit 404 sur toutes les ressources de B
        mockMvc.perform(get("/api/v1/schooldocs/documents?tenantId=" + schoolB.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(docAId.toString()));

        mockMvc.perform(get("/api/v1/schooldocs/templates/" + tplBId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/schooldocs/documents/" + docBId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/schooldocs/documents/" + docBId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/schooldocs/verify/" + tokenVerifyB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/schooldocs/documents/" + docBId + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Tentative cross-tenant"))))
                .andExpect(status().isNotFound());

        // Vérifications PostgreSQL directes sous le rôle applicatif ecole_app (NOBYPASSRLS)
        List<String> tables = List.of(
                "schooldocs_sequences",
                "schooldocs_templates",
                "schooldocs_documents",
                "schooldocs_document_contents",
                "schooldocs_document_events");

        // 1. Fail-closed : sans TenantContext, 0 ligne sur les 5 tables
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
                    "INSERT INTO schooldocs_sequences (tenant_id, sequence_year, last_value) VALUES (?, 2099, 1)",
                    schoolB.getId());
        })).isInstanceOf(DataAccessException.class);

        // 4. Immutabilité stricte : UPDATE et DELETE interdits sur contents & events (triggers + privilèges)
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
            jdbcTemplate.update(
                    "UPDATE schooldocs_document_contents SET sha256_hash = 'tampered' WHERE document_id = ?",
                    docAId);
        })).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
            jdbcTemplate.update(
                    "DELETE FROM schooldocs_document_events WHERE document_id = ?",
                    docAId);
        })).isInstanceOf(DataAccessException.class);

        // 5. Moindre privilège ecole_app : DELETE refusé sur schooldocs_documents et schooldocs_templates
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
            jdbcTemplate.update("DELETE FROM schooldocs_documents WHERE id = ?", docAId);
        })).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
            jdbcTemplate.update("DELETE FROM schooldocs_templates WHERE id = ?", tplAId);
        })).isInstanceOf(DataAccessException.class);
    }

    // --- Helpers ---

    private School createSchool(String prefix, SchoolType type) {
        String code = prefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return schoolService.createSchool(
                code, "École " + code, type, "+221770000000", "contact@" + code.toLowerCase() + ".sn", "Dakar");
    }

    private void createSubscription(School school, SubscriptionStatus status, Instant endsAt, int maxStorageMb) {
        SubscriptionPlan plan = subscriptionService.createPlan(
                "PLAN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                "Forfait Docs",
                20_000L,
                200_000L,
                200,
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

    private UUID createStudentViaApi(
            String token, String firstName, String lastName, String gender, String phone, String email) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("gender", gender);
        body.put("birthDate", "2010-03-10");
        body.put("admissionDate", "2026-10-01");
        if (phone != null) {
            body.put("phone", phone);
        }
        if (email != null) {
            body.put("email", email);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
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
                                "enrollmentDate", "2026-10-01"))))
                .andExpect(status().isCreated());
    }

    private UUID createFamilyViaApi(String token, String code, String name) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/families")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "name", name, "primaryPhone", "+221770001000"))))
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

    private void linkGuardianToStudentViaApi(
            String token,
            UUID studentId,
            UUID guardianId,
            String relationshipType,
            boolean primaryContact,
            boolean financialResponsible) throws Exception {
        mockMvc.perform(post("/api/v1/students/" + studentId + "/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "guardianId", guardianId,
                                "relationshipType", relationshipType,
                                "primaryContact", primaryContact,
                                "financialResponsible", financialResponsible,
                                "legalCustody", true,
                                "emergencyContact", true,
                                "authorizedPickup", true))))
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
        body.put("email", code.toLowerCase() + "@ecole.sn");
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
