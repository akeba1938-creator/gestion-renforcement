package com.ecole.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.audit.AuditSeverity;
import com.ecole.saas.audit.PlatformAuditRecordCommand;
import com.ecole.saas.identity.application.UserAccountService;
import com.ecole.saas.identity.domain.AccountRef;
import com.ecole.saas.identity.domain.PasswordHasher;
import com.ecole.saas.identity.domain.PlatformAdmin;
import com.ecole.saas.identity.domain.RoleCode;
import com.ecole.saas.identity.domain.TokenIssuer;
import com.ecole.saas.identity.infrastructure.PlatformAdminRepository;
import com.ecole.saas.school.application.SchoolService;
import com.ecole.saas.school.domain.School;
import com.ecole.saas.school.domain.SchoolType;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
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
class AuditIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final AuditLogPort auditLogPort;
    private final SchoolService schoolService;
    private final SubscriptionService subscriptionService;
    private final UserAccountService userAccountService;
    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AuditIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            AuditLogPort auditLogPort,
            SchoolService schoolService,
            SubscriptionService subscriptionService,
            UserAccountService userAccountService,
            PlatformAdminRepository platformAdminRepository,
            PasswordHasher passwordHasher,
            TokenIssuer tokenIssuer,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.auditLogPort = auditLogPort;
        this.schoolService = schoolService;
        this.subscriptionService = subscriptionService;
        this.userAccountService = userAccountService;
        this.platformAdminRepository = platformAdminRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldTraceCriticalActionsAcrossBusinessModulesWithoutLeakingSensitiveData() throws Exception {
        String adminToken = platformAdminToken();

        // 1. Plateforme : création d'un forfait et d'un établissement + démarrage d'essai (audit plateforme + établissement)
        MvcResult planRes = mockMvc.perform(post("/api/v1/platform/subscription-plans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "AUDIT-" + shortCode(),
                                "name", "Forfait Audit Pro",
                                "monthlyPriceXof", 50_000,
                                "yearlyPriceXof", 500_000,
                                "maxStudents", 500,
                                "maxUsers", 50,
                                "maxStorageMb", 10_240,
                                "trialDays", 14))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID planId = UUID.fromString(readJson(planRes).get("id").asString());

        String schoolCode = "AUD" + shortCode();
        MvcResult schoolRes = mockMvc.perform(post("/api/v1/platform/schools")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", schoolCode,
                                "name", "École Audit Excellence",
                                "schoolType", "PRIVATE_SCHOOL",
                                "phone", "+221771234567",
                                "email", "contact@audit-ecole.sn"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID schoolId = UUID.fromString(readJson(schoolRes).get("id").asString());

        mockMvc.perform(post("/api/v1/platform/schools/" + schoolId + "/subscriptions/trial")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "planId", planId.toString(),
                                "targetBillingCycle", "MONTHLY"))))
                .andExpect(status().isCreated());

        // 2. Authentification : échec puis succès sans exposer le mot de passe
        String rawPassword = "MotDePasseTresSecret!2026";
        String userEmail = "sec-" + shortCode().toLowerCase() + "@audit-ecole.sn";
        TestSecuritySupport.runAsDirector(() -> TenantContext.executeWithTenant(
                TenantId.of(schoolId),
                () -> userAccountService.createUserWithRole(
                        userEmail, "+221772220001", "Awa", "Diallo", passwordHasher.hash(rawPassword), RoleCode.SECRETARY)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "schoolCode", schoolCode,
                                "email", userEmail,
                                "password", "MauvaisMotDePasse!999"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "schoolCode", schoolCode,
                                "email", userEmail,
                                "password", rawPassword))))
                .andExpect(status().isOk());

        // 3. Structure académique : année scolaire + période + verrouillage de période (CRITICAL)
        String directorToken = tenantToken(schoolId, RoleCode.DIRECTOR);
        String cashierToken = tenantToken(schoolId, RoleCode.CASHIER);
        String managerToken = tenantToken(schoolId, RoleCode.CENTER_MANAGER);

        MvcResult yearRes = mockMvc.perform(post("/api/v1/academic/years")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "2026-2027",
                                "label", "Année 2026-2027",
                                "startDate", "2026-10-01",
                                "endDate", "2027-07-31"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID yearId = UUID.fromString(readJson(yearRes).get("id").asString());

        mockMvc.perform(post("/api/v1/academic/years/" + yearId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("makeCurrent", true))))
                .andExpect(status().isOk());

        MvcResult periodRes = mockMvc.perform(post("/api/v1/academic/years/" + yearId + "/periods")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "T1",
                                "name", "Trimestre 1",
                                "sequenceOrder", 1,
                                "startDate", "2026-10-01",
                                "endDate", "2026-12-20"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID periodId = UUID.fromString(readJson(periodRes).get("id").asString());

        mockMvc.perform(post("/api/v1/academic/periods/" + periodId + "/open")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk());

        // 4. Élève + Tuteur (avec numéro CNI sensible jamais repris en audit)
        MvcResult studentRes = mockMvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Moussa",
                                "lastName", "Sarr",
                                "gender", "MALE",
                                "birthDate", "2012-04-10"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID studentId = UUID.fromString(readJson(studentRes).get("id").asString());

        String secretCni = "1985123456789";
        mockMvc.perform(post("/api/v1/guardians")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Ibrahima",
                                "lastName", "Sarr",
                                "phone", "+221774445566",
                                "nationalIdNumber", secretCni))))
                .andExpect(status().isCreated());

        // 5. Finance : frais, facture émise, encaissement (sans fuiter verificationToken) et contre-passation
        MvcResult feeRes = mockMvc.perform(post("/api/v1/finance/fee-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "academicYearId", yearId.toString(),
                                "code", "SCOL-T1",
                                "label", "Scolarité T1",
                                "category", "TUITION",
                                "frequency", "TRIMESTER",
                                "amountXof", 75_000,
                                "mandatory", true))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID feeId = UUID.fromString(readJson(feeRes).get("id").asString());

        MvcResult invRes = mockMvc.perform(post("/api/v1/finance/invoices")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", studentId.toString(),
                                "academicYearId", yearId.toString(),
                                "title", "Facture T1",
                                "dueDate", "2026-11-15",
                                "lines", List.of(Map.of("feeItemId", feeId.toString(), "quantity", 1))))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID invoiceId = UUID.fromString(readJson(invRes).get("invoice").get("id").asString());

        mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/issue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk());

        MvcResult payRes = mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", invoiceId.toString(),
                                "amountXof", 75_000,
                                "paymentMethod", "CASH"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode payBody = readJson(payRes);
        UUID paymentId = UUID.fromString(payBody.get("id").asString());
        String receiptToken = payBody.get("receipt").get("verificationToken").asString();

        // 6. Centre de renforcement : création d'un pack
        mockMvc.perform(post("/api/v1/center/packs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "academicYearId", yearId.toString(),
                                "code", "PACK-10S",
                                "name", "Carnet 10 séances",
                                "billingMode", "SESSION_PACK",
                                "includedSessions", 10,
                                "priceXof", 40_000))))
                .andExpect(status().isCreated());

        // 7. Verrouillage de la période (CRITICAL)
        mockMvc.perform(post("/api/v1/academic/periods/" + periodId + "/close")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/academic/periods/" + periodId + "/lock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk());

        // 8. Consultation du journal établissement : présence de toutes les catégories et zéro fuite de secret
        MvcResult auditListRes = mockMvc.perform(get("/api/v1/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();
        String auditBodyText = auditListRes.getResponse().getContentAsString();
        assertThat(auditBodyText)
                .contains("AUTH_LOGIN_FAILED")
                .contains("AUTH_LOGIN_SUCCESS")
                .contains("SUBSCRIPTION_TRIAL_STARTED")
                .contains("ACADEMIC_YEAR_CREATED")
                .contains("EVALUATION_PERIOD_LOCKED")
                .contains("STUDENT_CREATED")
                .contains("GUARDIAN_CREATED")
                .contains("INVOICE_ISSUED")
                .contains("PAYMENT_RECORDED")
                .contains("CENTER_PACK_CREATED")
                .doesNotContain(rawPassword)
                .doesNotContain("MauvaisMotDePasse!999")
                .doesNotContain(secretCni)
                .doesNotContain(receiptToken);

        // Filtrage fin par catégorie, action et sévérité
        mockMvc.perform(get("/api/v1/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .param("category", "FINANCE")
                        .param("action", "PAYMENT_RECORDED")
                        .param("resourceId", paymentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].metadata.amountXof").value("75000"));

        mockMvc.perform(get("/api/v1/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .param("severity", "CRITICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("EVALUATION_PERIOD_LOCKED"));

        // Synthèse de pilotage
        mockMvc.perform(get("/api/v1/audit/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").isNumber())
                .andExpect(jsonPath("$.countsByCategory.FINANCE").isNumber())
                .andExpect(jsonPath("$.countsByCategory.AUTH").isNumber())
                .andExpect(jsonPath("$.countsBySeverity.CRITICAL").value(1));

        // 9. Journal plateforme ADMIN_SAAS : contient les actions plateforme (forfait, école, essai)
        MvcResult platformRes = mockMvc.perform(get("/api/v1/platform/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("targetSchoolId", schoolId.toString())
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String platformText = platformRes.getResponse().getContentAsString();
        assertThat(platformText)
                .contains("SCHOOL_CREATED")
                .contains("SUBSCRIPTION_TRIAL_STARTED");
    }

    @Test
    void shouldEnforceRbacValidationReadOnlyApiAndSupervisedPlatformAccess() throws Exception {
        School school = createSchool("RBAC", SchoolType.PRIVATE_SCHOOL);
        createActiveSubscription(school);

        TenantContext.runWithTenant(TenantId.of(school.getId()), () -> auditLogPort.recordTenantEvent(
                AuditRecordCommand.success(
                        AuditCategory.FINANCE,
                        AuditAction.PAYMENT_RECORDED,
                        "PAYMENT",
                        UUID.randomUUID(),
                        "Encaissement test RBAC",
                        Map.of("amountXof", 25_000))));

        UUID platformLogId = recordPlatformTestEvent(school.getId());

        // Rôles autorisés sur /api/v1/audit/**
        for (RoleCode allowed : List.of(RoleCode.DIRECTOR, RoleCode.PROMOTER, RoleCode.CENTER_MANAGER)) {
            String token = tenantToken(school.getId(), allowed);
            mockMvc.perform(get("/api/v1/audit/logs").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/audit/summary").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
        }

        // Rôles refusés sur /api/v1/audit/**
        for (RoleCode denied : List.of(
                RoleCode.SECRETARY,
                RoleCode.CASHIER,
                RoleCode.CENSOR,
                RoleCode.TEACHER,
                RoleCode.TUTOR,
                RoleCode.PARENT,
                RoleCode.STUDENT)) {
            String token = tenantToken(school.getId(), denied);
            mockMvc.perform(get("/api/v1/audit/logs").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:ecole:problem:access-denied"));
        }

        // ADMIN_SAAS refusé sur /api/v1/audit/** et non authentifié -> 401
        String adminToken = platformAdminToken();
        mockMvc.perform(get("/api/v1/audit/logs").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit/logs"))
                .andExpect(status().isUnauthorized());

        // Rôles établissement refusés sur /api/v1/platform/audit/**
        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);
        mockMvc.perform(get("/api/v1/platform/audit/logs").header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/platform/schools/" + school.getId() + "/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .param("reason", "Tentative non autorisée"))
                .andExpect(status().isForbidden());

        // Méthodes HTTP d'écriture interdites (405 RFC 9457)
        mockMvc.perform(post("/api/v1/audit/logs").header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/audit/logs/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/v1/audit/logs/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isMethodNotAllowed());

        // Validation des paramètres de pagination et de dates
        mockMvc.perform(get("/api/v1/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .param("sortBy", "passwordHash"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .param("from", "2026-10-20T00:00:00Z")
                        .param("to", "2026-10-10T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:audit-validation-error"));

        // Consultation plateforme d'un événement unique
        mockMvc.perform(get("/api/v1/platform/audit/logs/" + platformLogId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(platformLogId.toString()));

        // Accès supervisé ADMIN_SAAS au journal établissement : motif obligatoire + 404 si école inconnue + double journalisation
        mockMvc.perform(get("/api/v1/platform/schools/" + school.getId() + "/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:audit-validation-error"));

        mockMvc.perform(get("/api/v1/platform/schools/" + school.getId() + "/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("reason", "court"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/platform/schools/" + UUID.randomUUID() + "/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("reason", "Ticket support #2026-404 vérification incident"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:audit-log-not-found"));

        mockMvc.perform(get("/api/v1/platform/schools/" + school.getId() + "/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("reason", "Ticket support #2026-99 : audit d'un encaissement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        // Vérifier que l'accès supervisé a bien été journalisé côté établissement ET côté plateforme
        mockMvc.perform(get("/api/v1/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .param("action", "AUDIT_TENANT_LOGS_ACCESSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/platform/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("action", "AUDIT_TENANT_LOGS_ACCESSED")
                        .param("targetSchoolId", school.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reason").value("Ticket support #2026-99 : audit d'un encaissement"));
    }

    @Test
    void shouldEnforceMultiTenantIsolationPostgresqlRlsAndStrictImmutability() throws Exception {
        School schoolA = createSchool("ISOA", SchoolType.PRIVATE_SCHOOL);
        School schoolB = createSchool("ISOB", SchoolType.TUTORING_CENTER);
        createActiveSubscription(schoolA);
        createActiveSubscription(schoolB);

        // Garde d'abonnement sur /api/v1/audit/** : SUSPENDED bloqué (403), EXPIRED autorisé en lecture (200)
        School suspendedAuditSchool = createSchool("AUDSUSP", SchoolType.PRIVATE_SCHOOL);
        SubscriptionPlan suspPlan = TestSecuritySupport.runAsAdminSaas(() -> subscriptionService.createPlan(
                "AUDSUSP-" + shortCode(), "Forfait Audit Susp", 20_000L, 200_000L, 100, 10, 1_024));
        Instant nowAudit = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        TestSecuritySupport.runAsAdminSaas(() -> TenantContext.runWithTenant(
                TenantId.of(suspendedAuditSchool.getId()),
                () -> subscriptionService.createSubscription(
                        suspPlan.getId(), SubscriptionStatus.SUSPENDED, BillingCycle.MONTHLY, nowAudit.minus(1, ChronoUnit.DAYS), nowAudit.plus(30, ChronoUnit.DAYS))));
        String suspAuditToken = tenantToken(suspendedAuditSchool.getId(), RoleCode.DIRECTOR);
        mockMvc.perform(get("/api/v1/audit/logs").header(HttpHeaders.AUTHORIZATION, bearer(suspAuditToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit/summary").header(HttpHeaders.AUTHORIZATION, bearer(suspAuditToken)))
                .andExpect(status().isForbidden());

        School expiredAuditSchool = createSchool("AUDEXP", SchoolType.PRIVATE_SCHOOL);
        TestSecuritySupport.runAsAdminSaas(() -> TenantContext.runWithTenant(
                TenantId.of(expiredAuditSchool.getId()),
                () -> subscriptionService.createSubscription(
                        suspPlan.getId(), SubscriptionStatus.ACTIVE, BillingCycle.MONTHLY, nowAudit.minus(40, ChronoUnit.DAYS), nowAudit.minus(2, ChronoUnit.DAYS))));
        String expAuditToken = tenantToken(expiredAuditSchool.getId(), RoleCode.DIRECTOR);
        mockMvc.perform(get("/api/v1/audit/logs").header(HttpHeaders.AUTHORIZATION, bearer(expAuditToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/audit/summary").header(HttpHeaders.AUTHORIZATION, bearer(expAuditToken)))
                .andExpect(status().isOk());

        UUID resA = UUID.randomUUID();
        UUID resB = UUID.randomUUID();

        TenantContext.runWithTenant(TenantId.of(schoolA.getId()), () -> auditLogPort.recordTenantEvent(
                AuditRecordCommand.success(
                        AuditCategory.FINANCE,
                        AuditAction.PAYMENT_RECORDED,
                        "PAYMENT",
                        resA,
                        "Événement strictement réservé à l'école A",
                        Map.of("amountXof", 10_000))));

        TenantContext.runWithTenant(TenantId.of(schoolB.getId()), () -> auditLogPort.recordTenantEvent(
                AuditRecordCommand.success(
                        AuditCategory.FINANCE,
                        AuditAction.PAYMENT_RECORDED,
                        "PAYMENT",
                        resB,
                        "Événement strictement réservé au centre B",
                        Map.of("amountXof", 20_000))));

        UUID platformLogId = recordPlatformTestEvent(schoolA.getId());

        String directorA = tenantToken(schoolA.getId(), RoleCode.DIRECTOR);
        String managerB = tenantToken(schoolB.getId(), RoleCode.CENTER_MANAGER);

        // Récupérer l'ID de l'événement B depuis le tenant B
        MvcResult bRes = mockMvc.perform(get("/api/v1/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerB))
                        .param("resourceId", resB.toString()))
                .andExpect(status().isOk())
                .andReturn();
        UUID eventBId = UUID.fromString(readJson(bRes).get("content").get(0).get("id").asString());

        MvcResult aRes = mockMvc.perform(get("/api/v1/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA))
                        .header("X-Tenant-Id", schoolB.getId().toString())
                        .param("tenantId", schoolB.getId().toString())
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        String aBody = aRes.getResponse().getContentAsString();
        assertThat(aBody).contains(resA.toString()).doesNotContain(resB.toString());
        UUID eventAId = UUID.fromString(readJson(aRes).get("content").get(0).get("id").asString());

        // Accès direct à l'événement de B par A -> 404
        mockMvc.perform(get("/api/v1/audit/logs/" + eventBId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        // 1. Isolation RLS PostgreSQL directe sous ecole_app (NOBYPASSRLS)
        TenantContext.runWithTenant(TenantId.of(schoolA.getId()), () -> transactionTemplate.executeWithoutResult(st -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");

            List<UUID> visibleAuditIds = jdbcTemplate.queryForList("SELECT id FROM audit_logs", UUID.class);
            assertThat(visibleAuditIds).contains(eventAId).doesNotContain(eventBId);

            // Séparation stricte : platform_audit_logs invisible en contexte tenant
            Integer visiblePlatformCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM platform_audit_logs", Integer.class);
            assertThat(visiblePlatformCount).isZero();
        }));

        // Tentative d'INSERT cross-tenant dans audit_logs sous ecole_app -> rejetée par RLS WITH CHECK
        TenantContext.runWithTenant(TenantId.of(schoolA.getId()), () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(st -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update(
                            "INSERT INTO audit_logs (id, tenant_id, category, action, outcome, severity, actor_type, "
                                    + "resource_type, summary, metadata_json, occurred_at) "
                                    + "VALUES (?, ?, 'FINANCE', 'PAYMENT_RECORDED', 'SUCCESS', 'INFO', 'USER', "
                                    + "'PAYMENT', 'Injection B', '{}', now())",
                            UUID.randomUUID(),
                            schoolB.getId());
                }))
                .isInstanceOf(DataAccessException.class));

        // Tentative d'INSERT dans platform_audit_logs depuis un contexte tenant -> rejetée par RLS WITH CHECK
        TenantContext.runWithTenant(TenantId.of(schoolA.getId()), () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(st -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update(
                            "INSERT INTO platform_audit_logs (id, category, action, outcome, severity, actor_type, "
                                    + "resource_type, summary, metadata_json, occurred_at) "
                                    + "VALUES (?, 'SCHOOL', 'SCHOOL_UPDATED', 'SUCCESS', 'INFO', 'USER', "
                                    + "'SCHOOL', 'Injection plateforme', '{}', now())",
                            UUID.randomUUID());
                }))
                .isInstanceOf(DataAccessException.class));

        // Fail-closed sans TenantContext : 0 ligne visible dans audit_logs sous ecole_app
        transactionTemplate.executeWithoutResult(st -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class)).isZero();
        });

        // 2. Immutabilité stricte : UPDATE et DELETE interdits sous ecole_app ET sous le propriétaire (triggers SQL)
        TenantContext.runWithTenant(TenantId.of(schoolA.getId()), () -> {
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(st -> {
                jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                jdbcTemplate.update("UPDATE audit_logs SET summary = 'Altéré' WHERE id = ?", eventAId);
            })).isInstanceOf(DataAccessException.class);

            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(st -> {
                jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                jdbcTemplate.update("DELETE FROM audit_logs WHERE id = ?", eventAId);
            })).isInstanceOf(DataAccessException.class);

            // Même en tant que propriétaire (hors ecole_app), les triggers BEFORE UPDATE OR DELETE bloquent !
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(st ->
                    jdbcTemplate.update("UPDATE audit_logs SET summary = 'Altéré owner' WHERE id = ?", eventAId)))
                    .isInstanceOf(DataAccessException.class);

            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(st ->
                    jdbcTemplate.update("DELETE FROM audit_logs WHERE id = ?", eventAId)))
                    .isInstanceOf(DataAccessException.class);
        });

        // Immutabilité de platform_audit_logs sous ecole_app et sous le propriétaire (triggers SQL)
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(st -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.update("UPDATE platform_audit_logs SET summary = 'Altéré' WHERE id = ?", platformLogId);
        })).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(st -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.update("DELETE FROM platform_audit_logs WHERE id = ?", platformLogId);
        })).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(st ->
                jdbcTemplate.update("UPDATE platform_audit_logs SET summary = 'Altéré owner' WHERE id = ?", platformLogId)))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(st ->
                jdbcTemplate.update("DELETE FROM platform_audit_logs WHERE id = ?", platformLogId)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void shouldSanitizeSensitiveMetadataBeforePersistence() throws Exception {
        School school = createSchool("SAN", SchoolType.PRIVATE_SCHOOL);
        createActiveSubscription(school);
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyIn0.signatureValide123456";
        UUID resourceId = UUID.randomUUID();

        Map<String, Object> unsafeMeta = new LinkedHashMap<>();
        unsafeMeta.put("password", "SecretPass!2026");
        unsafeMeta.put("mfaSecret", "JBSWY3DPEHPK3PXP");
        unsafeMeta.put("cni", "1990123456789");
        unsafeMeta.put("refreshToken", "raw-refresh-cookie");
        unsafeMeta.put("rawBody", "{\"secret\":\"val\"}");
        unsafeMeta.put("note", "Appel avec Bearer myBearerSecret999 et " + jwt);
        unsafeMeta.put("amountXof", 15_000L);

        TenantContext.runWithTenant(TenantId.of(school.getId()), () -> auditLogPort.recordTenantEvent(
                AuditRecordCommand.success(
                        AuditCategory.SECURITY,
                        AuditAction.USER_CREATED,
                        "USER",
                        resourceId,
                        "Création avec otpauth://totp/Ecole:u@e.sn?secret=ABCDE et CNI 1990123456789",
                        unsafeMeta)));

        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);
        MvcResult res = mockMvc.perform(get("/api/v1/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .param("resourceId", resourceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].metadata.amountXof").value("15000"))
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("SecretPass!2026")
                .doesNotContain("JBSWY3DPEHPK3PXP")
                .doesNotContain("1990123456789")
                .doesNotContain("raw-refresh-cookie")
                .doesNotContain("myBearerSecret999")
                .doesNotContain("eyJhbGci")
                .doesNotContain("otpauth://")
                .contains("[MASQUE]");
    }

    @Test
    void shouldTracePlatformAdminAuthFailuresInPlatformAuditLogs() throws Exception {
        String email = "admin-" + shortCode().toLowerCase() + "@platform.sn";
        String rawPassword = "AdminSuperSecret!2026";
        platformAdminRepository.save(new PlatformAdmin(
                email, "Super", "Admin", passwordHasher.hash(rawPassword)));

        mockMvc.perform(post("/api/v1/auth/platform/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "WrongAdminPassword!1"))))
                .andExpect(status().isUnauthorized());

        String adminToken = platformAdminToken();
        MvcResult res = mockMvc.perform(get("/api/v1/platform/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("category", "AUTH")
                        .param("action", "AUTH_LOGIN_FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andReturn();

        assertThat(res.getResponse().getContentAsString())
                .doesNotContain(rawPassword)
                .doesNotContain("WrongAdminPassword!1");
    }

    private UUID recordPlatformTestEvent(UUID targetSchoolId) {
        UUID resourceId = UUID.randomUUID();
        auditLogPort.recordPlatformEvent(PlatformAuditRecordCommand.success(
                AuditCategory.SCHOOL,
                AuditAction.SCHOOL_UPDATED,
                targetSchoolId,
                "SCHOOL",
                resourceId,
                "Opération plateforme de test",
                "Test automatisé",
                Map.of("target", targetSchoolId.toString())));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM platform_audit_logs WHERE resource_id = ?",
                UUID.class,
                resourceId);
    }

    private School createSchool(String prefix, SchoolType type) {
        String code = prefix + "-" + shortCode();
        return TestSecuritySupport.runAsAdminSaas(() -> schoolService.createSchool(
                code, "Établissement " + code, type, "+221770001122", code.toLowerCase() + "@ecole.sn", "Dakar"));
    }

    private void createActiveSubscription(School school) {
        TestSecuritySupport.runAsAdminSaas(() -> {
            SubscriptionPlan plan = subscriptionService.createPlan(
                    "PLAN-" + shortCode(), "Forfait Test", 25_000L, 250_000L, 500, 30, 5_120);
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            TenantContext.runWithTenant(TenantId.of(school.getId()), () -> subscriptionService.createSubscription(
                    plan.getId(), SubscriptionStatus.ACTIVE, BillingCycle.MONTHLY, now, now.plus(30, ChronoUnit.DAYS)));
        });
    }

    private String tenantToken(UUID tenantId, RoleCode role) {
        return tokenIssuer.issueAccessToken(AccountRef.user(UUID.randomUUID(), tenantId), Set.of(role.name())).value();
    }

    private String platformAdminToken() {
        return tokenIssuer.issueAccessToken(
                AccountRef.platformAdmin(UUID.randomUUID()), Set.of(RoleCode.ADMIN_SAAS.name())).value();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String shortCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
