package com.ecole.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecole.saas.identity.domain.AccountRef;
import com.ecole.saas.identity.domain.RoleCode;
import com.ecole.saas.identity.domain.TokenIssuer;
import com.ecole.saas.school.application.SchoolService;
import com.ecole.saas.school.domain.School;
import com.ecole.saas.school.domain.SchoolType;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import com.ecole.saas.subscription.EffectiveAccessState;
import com.ecole.saas.subscription.QuotaCheckResult;
import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.subscription.SubscriptionAccessPort;
import com.ecole.saas.subscription.SubscriptionSnapshot;
import com.ecole.saas.subscription.application.SubscriptionService;
import com.ecole.saas.subscription.domain.BillingCycle;
import com.ecole.saas.subscription.domain.SubscriptionAccessDeniedException;
import com.ecole.saas.subscription.domain.SubscriptionQuotaExceededException;
import com.ecole.saas.subscription.domain.SubscriptionStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class SubscriptionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final SchoolService schoolService;
    private final SubscriptionService subscriptionService;
    private final SubscriptionAccessPort subscriptionAccessPort;
    private final TokenIssuer tokenIssuer;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SubscriptionIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            SchoolService schoolService,
            SubscriptionService subscriptionService,
            SubscriptionAccessPort subscriptionAccessPort,
            TokenIssuer tokenIssuer,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.schoolService = schoolService;
        this.subscriptionService = subscriptionService;
        this.subscriptionAccessPort = subscriptionAccessPort;
        this.tokenIssuer = tokenIssuer;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldManagePlanCatalogAndCompleteSubscriptionLifecycleWithImmutableEvents() throws Exception {
        String adminToken = platformAdminToken();
        School school = createSchool("SUB-LIFE", SchoolType.PRIVATE_SCHOOL);
        String directorToken = tenantToken(school, RoleCode.DIRECTOR);

        // 1. Création de deux forfaits par ADMIN_SAAS
        String starterCode = uniqueCode("STARTER");
        String proCode = uniqueCode("PRO");

        UUID starterPlanId = createPlanViaApi(adminToken, starterCode, "Forfait Starter", 25_000L, 250_000L, 200, 10, 2_048, 14);
        UUID proPlanId = createPlanViaApi(adminToken, proCode, "Forfait Pro", 60_000L, 600_000L, 800, 40, 10_240, 21);

        // Doublon de code -> 409 RFC 9457
        mockMvc.perform(post("/api/v1/platform/subscription-plans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", starterCode.toLowerCase(),
                                "name", "Doublon",
                                "monthlyPriceXof", 10_000L,
                                "yearlyPriceXof", 100_000L,
                                "maxStudents", 100,
                                "maxUsers", 5,
                                "maxStorageMb", 1_024,
                                "trialDays", 14))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-plan-already-exists"));

        // Mise à jour, désactivation puis réactivation d'un forfait
        mockMvc.perform(put("/api/v1/platform/subscription-plans/" + starterPlanId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Forfait Starter Révisé",
                                "description", "Idéal petites écoles",
                                "monthlyPriceXof", 30_000L,
                                "yearlyPriceXof", 300_000L,
                                "maxStudents", 250,
                                "maxUsers", 12,
                                "maxStorageMb", 3_072,
                                "trialDays", 15))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyPriceXof").value(30_000L))
                .andExpect(jsonPath("$.maxStudents").value(250))
                .andExpect(jsonPath("$.trialDays").value(15));

        mockMvc.perform(post("/api/v1/platform/subscription-plans/" + starterPlanId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Un forfait désactivé est invisible sur l'endpoint public /api/v1/subscription-plans/{id} -> 404
        mockMvc.perform(get("/api/v1/subscription-plans/" + starterPlanId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isNotFound());

        // Impossible de démarrer un essai sur un forfait désactivé -> 409
        mockMvc.perform(post("/api/v1/platform/schools/" + school.getId() + "/subscriptions/trial")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("planId", starterPlanId.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-plan-inactive"));

        mockMvc.perform(post("/api/v1/platform/subscription-plans/" + starterPlanId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        // 2. Démarrage de la période d'essai (montant 0 FCFA)
        MvcResult trialResult = mockMvc.perform(post("/api/v1/platform/schools/" + school.getId() + "/subscriptions/trial")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "planId", starterPlanId.toString(),
                                "targetBillingCycle", "MONTHLY"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TRIAL"))
                .andExpect(jsonPath("$.accessState").value("TRIAL_ACTIVE"))
                .andExpect(jsonPath("$.readAllowed").value(true))
                .andExpect(jsonPath("$.writeAllowed").value(true))
                .andExpect(jsonPath("$.amountXof").value(0))
                .andExpect(jsonPath("$.quotas.maxStudents").value(250))
                .andExpect(jsonPath("$.trialEndsAt").isNotEmpty())
                .andReturn();

        UUID subscriptionId = UUID.fromString(readJson(trialResult).get("id").asString());

        // Un second abonnement ouvert pour le même établissement est refusé -> 409
        mockMvc.perform(post("/api/v1/platform/schools/" + school.getId() + "/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "planId", proPlanId.toString(),
                                "billingCycle", "MONTHLY"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:active-subscription-already-exists"));

        // 3. Activation (conversion d'essai -> ACTIVE)
        mockMvc.perform(post("/api/v1/platform/schools/" + school.getId() + "/subscriptions/" + subscriptionId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("billingCycle", "MONTHLY", "reason", "Règlement premier mois"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.accessState").value("ACTIVE"))
                .andExpect(jsonPath("$.amountXof").value(30_000L));

        // 4. Ajustement des quotas effectifs (extensions)
        mockMvc.perform(put("/api/v1/platform/schools/" + school.getId() + "/subscriptions/" + subscriptionId + "/quotas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "extraStudents", 50,
                                "extraUsers", 3,
                                "extraStorageMb", 1_024,
                                "reason", "Extension rentrée"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotas.baseStudents").value(250))
                .andExpect(jsonPath("$.quotas.extraStudents").value(50))
                .andExpect(jsonPath("$.quotas.maxStudents").value(300))
                .andExpect(jsonPath("$.quotas.maxUsers").value(15))
                .andExpect(jsonPath("$.quotas.maxStorageMb").value(4_096));

        // 5. Changement de forfait vers PRO annuel (conservation des extensions)
        mockMvc.perform(put("/api/v1/platform/schools/" + school.getId() + "/subscriptions/" + subscriptionId + "/plan")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "planId", proPlanId.toString(),
                                "billingCycle", "YEARLY",
                                "resetExtraQuotas", false,
                                "reason", "Passage au forfait Pro"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value(proCode))
                .andExpect(jsonPath("$.amountXof").value(600_000L))
                .andExpect(jsonPath("$.quotas.baseStudents").value(800))
                .andExpect(jsonPath("$.quotas.extraStudents").value(50))
                .andExpect(jsonPath("$.quotas.maxStudents").value(850));

        // 6. Renouvellement
        mockMvc.perform(post("/api/v1/platform/schools/" + school.getId() + "/subscriptions/" + subscriptionId + "/renew")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("billingCycle", "YEARLY", "reason", "Renouvellement annuel"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.amountXof").value(600_000L));

        // 7. Passage en impayé (PAST_DUE) avec période de grâce -> écriture encore autorisée
        Instant graceEnd = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        mockMvc.perform(post("/api/v1/platform/schools/" + school.getId() + "/subscriptions/" + subscriptionId + "/past-due")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "gracePeriodEndsAt", graceEnd.toString(),
                                "reason", "Retard de règlement"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAST_DUE"))
                .andExpect(jsonPath("$.accessState").value("PAST_DUE_GRACE"))
                .andExpect(jsonPath("$.writeAllowed").value(true));

        // 8. Suspension -> lecture et écriture opérationnelles bloquées
        mockMvc.perform(post("/api/v1/platform/schools/" + school.getId() + "/subscriptions/" + subscriptionId + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Impayé après délai de grâce"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.accessState").value("SUSPENDED"))
                .andExpect(jsonPath("$.readAllowed").value(false))
                .andExpect(jsonPath("$.writeAllowed").value(false));

        // 9. Réactivation puis annulation terminale
        mockMvc.perform(post("/api/v1/platform/schools/" + school.getId() + "/subscriptions/" + subscriptionId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Paiement régularisé"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.accessState").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/platform/schools/" + school.getId() + "/subscriptions/" + subscriptionId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Fermeture de l'établissement"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.accessState").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

        // Transition interdite après annulation -> 409
        mockMvc.perform(post("/api/v1/platform/schools/" + school.getId() + "/subscriptions/" + subscriptionId + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Tentative invalide"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:invalid-subscription-transition"));

        // 10. Vérification du journal complet des 9 événements immuables ordonnés par sequenceNo DESC
        mockMvc.perform(get("/api/v1/platform/schools/" + school.getId() + "/subscriptions/" + subscriptionId + "/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .queryParam("page", "0")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(9))
                .andExpect(jsonPath("$.content[0].sequenceNo").value(9))
                .andExpect(jsonPath("$.content[0].eventType").value("CANCELLED"))
                .andExpect(jsonPath("$.content[1].eventType").value("REACTIVATED"))
                .andExpect(jsonPath("$.content[8].sequenceNo").value(1))
                .andExpect(jsonPath("$.content[8].eventType").value("TRIAL_STARTED"));

        // Validation pagination hors bornes -> 400 RFC 9457
        mockMvc.perform(get("/api/v1/platform/subscription-plans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .queryParam("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void shouldExposePublicSubscriptionAccessPortForOtherModules() throws Exception {
        String adminToken = platformAdminToken();
        School schoolWithoutSub = createSchool("PORT-NONE", SchoolType.PRIVATE_SCHOOL);
        School schoolActive = createSchool("PORT-ACT", SchoolType.PRIVATE_SCHOOL);
        School schoolExpired = createSchool("PORT-EXP", SchoolType.TUTORING_CENTER);

        UUID planId = createPlanViaApi(
                adminToken, uniqueCode("PORT"), "Forfait Port", 40_000L, 400_000L, 100, 10, 2_048, 14);

        // 1. Aucun abonnement -> MISSING
        TenantContext.runWithTenant(TenantId.of(schoolWithoutSub.getId()), () -> {
            assertThat(subscriptionAccessPort.findCurrentForCurrentTenant()).isEmpty();
            assertThat(subscriptionAccessPort.isOperationalForCurrentTenant()).isFalse();
            assertThatThrownBy(subscriptionAccessPort::requireOperationalSubscription)
                    .isInstanceOf(SubscriptionAccessDeniedException.class);
            assertThatThrownBy(() -> subscriptionAccessPort.assertQuotaAvailable(QuotaResource.STUDENTS, 0, 1))
                    .isInstanceOf(SubscriptionAccessDeniedException.class);
        });

        // 2. Abonnement actif + extension de 20 élèves => plafond effectif 120
        MvcResult created = mockMvc.perform(post("/api/v1/platform/schools/" + schoolActive.getId() + "/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("planId", planId.toString(), "billingCycle", "MONTHLY"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID subActiveId = UUID.fromString(readJson(created).get("id").asString());

        mockMvc.perform(put("/api/v1/platform/schools/" + schoolActive.getId() + "/subscriptions/" + subActiveId + "/quotas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "extraStudents", 20,
                                "extraUsers", 2,
                                "extraStorageMb", 512,
                                "reason", "Extension"))))
                .andExpect(status().isOk());

        TenantContext.runWithTenant(TenantId.of(schoolActive.getId()), () -> {
            assertThat(subscriptionAccessPort.isOperationalForCurrentTenant()).isTrue();
            SubscriptionSnapshot snapshot = subscriptionAccessPort.requireOperationalSubscription();
            assertThat(snapshot.accessState()).isEqualTo(EffectiveAccessState.ACTIVE);
            assertThat(snapshot.quotas().maxStudents()).isEqualTo(120);
            assertThat(snapshot.quotas().maxUsers()).isEqualTo(12);
            assertThat(snapshot.quotas().maxStorageMb()).isEqualTo(2_560);

            assertThatCode(() -> subscriptionAccessPort.assertQuotaAvailable(QuotaResource.STUDENTS, 119, 1))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> subscriptionAccessPort.assertQuotaAvailable(QuotaResource.STUDENTS, 120, 1))
                    .isInstanceOf(SubscriptionQuotaExceededException.class);

            QuotaCheckResult storageCheck = subscriptionAccessPort.checkQuota(QuotaResource.STORAGE_MB, 2_500, 100);
            assertThat(storageCheck.allowed()).isFalse();
            assertThat(storageCheck.remaining()).isEqualTo(60L);
        });

        // 3. Abonnement expiré -> lecture seule autorisée, écriture refusée
        Instant pastStart = Instant.now().minus(60, ChronoUnit.DAYS);
        Instant pastEnd = Instant.now().minus(5, ChronoUnit.DAYS);
        TenantContext.runWithTenant(TenantId.of(schoolExpired.getId()), () ->
                subscriptionService.createSubscription(
                        planId, SubscriptionStatus.ACTIVE, BillingCycle.MONTHLY, pastStart, pastEnd));

        TenantContext.runWithTenant(TenantId.of(schoolExpired.getId()), () -> {
            SubscriptionSnapshot expiredSnapshot = subscriptionAccessPort.findCurrentForCurrentTenant().orElseThrow();
            assertThat(expiredSnapshot.accessState()).isEqualTo(EffectiveAccessState.EXPIRED);
            assertThat(expiredSnapshot.readAllowed()).isTrue();
            assertThat(expiredSnapshot.writeAllowed()).isFalse();
            assertThat(subscriptionAccessPort.isOperationalForCurrentTenant()).isFalse();
            assertThatThrownBy(() -> subscriptionAccessPort.assertQuotaAvailable(QuotaResource.USERS, 1, 1))
                    .isInstanceOf(SubscriptionAccessDeniedException.class);
        });
    }

    @Test
    void shouldEnforceRoleMatrixAndRejectUnauthorizedOrMfaPendingAccess() throws Exception {
        String adminToken = platformAdminToken();
        School school = createSchool("SUB-RBAC", SchoolType.TUTORING_CENTER);
        UUID planId = createPlanViaApi(
                adminToken, uniqueCode("RBAC"), "Forfait RBAC", 20_000L, 200_000L, 150, 8, 1_024, 14);

        MvcResult created = mockMvc.perform(post("/api/v1/platform/schools/" + school.getId() + "/subscriptions/trial")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("planId", planId.toString()))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID subId = UUID.fromString(readJson(created).get("id").asString());

        // Non authentifié -> 401 RFC 9457
        mockMvc.perform(get("/api/v1/subscriptions/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // Jeton MFA en attente -> 403
        String mfaPendingToken = tokenIssuer
                .issueMfaPendingToken(AccountRef.user(UUID.randomUUID(), school.getId()))
                .value();
        mockMvc.perform(get("/api/v1/subscriptions/current")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mfaPendingToken)))
                .andExpect(status().isForbidden());

        // PROMOTER, DIRECTOR, CENTER_MANAGER autorisés sur /api/v1/subscriptions/** mais refusés sur /api/v1/platform/**
        for (RoleCode allowedRole : List.of(RoleCode.PROMOTER, RoleCode.DIRECTOR, RoleCode.CENTER_MANAGER)) {
            String token = tenantToken(school, allowedRole);
            mockMvc.perform(get("/api/v1/subscriptions/current")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(subId.toString()));

            mockMvc.perform(get("/api/v1/subscriptions/current/quotas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resources.length()").value(3));

            mockMvc.perform(get("/api/v1/subscriptions")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));

            mockMvc.perform(get("/api/v1/subscriptions/" + subId + "/events")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));

            // Refus sur endpoints plateforme -> 403
            mockMvc.perform(get("/api/v1/platform/subscription-plans")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

            mockMvc.perform(get("/api/v1/platform/schools/" + school.getId() + "/subscriptions/current")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden());
        }

        // SECRETARY, TEACHER, CASHIER : accès aux forfaits actifs OK, mais 403 sur /api/v1/subscriptions/** et /api/v1/platform/**
        for (RoleCode deniedRole : List.of(RoleCode.SECRETARY, RoleCode.TEACHER, RoleCode.CASHIER)) {
            String token = tenantToken(school, deniedRole);
            mockMvc.perform(get("/api/v1/subscription-plans")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/subscriptions/current")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

            mockMvc.perform(get("/api/v1/subscriptions/current/quotas")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/v1/platform/subscription-plans")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "code", uniqueCode("NOPE"),
                                    "name", "Interdit",
                                    "monthlyPriceXof", 10_000L,
                                    "yearlyPriceXof", 100_000L,
                                    "maxStudents", 100,
                                    "maxUsers", 5,
                                    "maxStorageMb", 1_024))))
                    .andExpect(status().isForbidden());
        }

        // ADMIN_SAAS : refusé sur /api/v1/subscriptions/current (endpoint réservé aux rôles établissement)
        mockMvc.perform(get("/api/v1/subscriptions/current")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldEnforceStrictMultiTenantIsolationAcrossHttpAndPostgresqlRls() throws Exception {
        String adminToken = platformAdminToken();
        School schoolA = createSchool("ISO-SUB-A", SchoolType.PRIVATE_SCHOOL);
        School schoolB = createSchool("ISO-SUB-B", SchoolType.TUTORING_CENTER);

        UUID planId = createPlanViaApi(
                adminToken, uniqueCode("ISO"), "Forfait Isolation", 35_000L, 350_000L, 300, 15, 4_096, 14);

        UUID subAId = UUID.fromString(readJson(mockMvc.perform(
                        post("/api/v1/platform/schools/" + schoolA.getId() + "/subscriptions")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("planId", planId.toString(), "billingCycle", "MONTHLY"))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString());

        UUID subBId = UUID.fromString(readJson(mockMvc.perform(
                        post("/api/v1/platform/schools/" + schoolB.getId() + "/subscriptions")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("planId", planId.toString(), "billingCycle", "YEARLY"))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString());

        String directorAToken = tenantToken(schoolA, RoleCode.DIRECTOR);

        // 1. Spoofing via X-Tenant-Id et queryParam ignoré côté serveur -> retourne uniquement l'abonnement de A
        mockMvc.perform(get("/api/v1/subscriptions/current")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorAToken))
                        .header("X-Tenant-Id", schoolB.getId().toString())
                        .queryParam("tenantId", schoolB.getId().toString())
                        .queryParam("schoolId", schoolB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(subAId.toString()))
                .andExpect(jsonPath("$.billingCycle").value("MONTHLY"));

        // Liste historique : seul subA est visible
        mockMvc.perform(get("/api/v1/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(subAId.toString()));

        // Tentative d'accès aux événements de l'abonnement de B -> 404 Not Found
        mockMvc.perform(get("/api/v1/subscriptions/" + subBId + "/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorAToken)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // 2. RLS PostgreSQL directe sous le rôle non-superuser ecole_app (subscriptions + subscription_events)
        TenantId tenantA = TenantId.of(schoolA.getId());
        TenantId tenantB = TenantId.of(schoolB.getId());

        TenantContext.runWithTenant(tenantA, () -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");

            List<UUID> visibleSubs = jdbcTemplate.queryForList("SELECT id FROM subscriptions", UUID.class);
            assertThat(visibleSubs).containsExactly(subAId);

            List<UUID> visibleEvents = jdbcTemplate.queryForList(
                    "SELECT subscription_id FROM subscription_events", UUID.class);
            assertThat(visibleEvents).containsExactly(subAId);

            int updatedB = jdbcTemplate.update(
                    "UPDATE subscriptions SET status_reason = 'Piraté' WHERE id = ?", subBId);
            assertThat(updatedB).isZero();
        }));

        // Tentative d'INSERT inter-tenant dans subscription_events -> rejetée par RLS WITH CHECK
        TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update(
                            "INSERT INTO subscription_events (id, tenant_id, subscription_id, sequence_no, event_type, "
                                    + "new_status, plan_id, amount_xof, effective_max_students, effective_max_users, "
                                    + "effective_max_storage_mb, occurred_at) "
                                    + "VALUES (?, ?, ?, 99, 'ACTIVATED', 'ACTIVE', ?, 0, 100, 10, 1024, now())",
                            UUID.randomUUID(),
                            tenantB.value(),
                            subBId,
                            planId);
                }))
                .isInstanceOf(DataAccessException.class));

        // Moindre privilège : ecole_app ne peut ni UPDATE ni DELETE sur subscription_events (journal en ajout seul)
        TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update(
                            "UPDATE subscription_events SET reason = 'Modifié' WHERE subscription_id = ?", subAId);
                }))
                .isInstanceOf(DataAccessException.class));

        TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update("DELETE FROM subscription_events WHERE subscription_id = ?", subAId);
                }))
                .isInstanceOf(DataAccessException.class));

        // M1 (V12) : le trigger BEFORE UPDATE OR DELETE protège subscription_events même sous le propriétaire de table
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                "UPDATE subscription_events SET reason = 'Altération propriétaire' WHERE subscription_id = ?",
                subAId)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                "DELETE FROM subscription_events WHERE subscription_id = ?",
                subAId)))
                .isInstanceOf(DataAccessException.class);

        // Fail-closed sans TenantContext
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subscriptions", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subscription_events", Integer.class)).isZero();
        });
    }

    private UUID createPlanViaApi(
            String adminToken,
            String code,
            String name,
            long monthlyPriceXof,
            long yearlyPriceXof,
            int maxStudents,
            int maxUsers,
            int maxStorageMb,
            int trialDays) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/subscription-plans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", code,
                                "name", name,
                                "monthlyPriceXof", monthlyPriceXof,
                                "yearlyPriceXof", yearlyPriceXof,
                                "maxStudents", maxStudents,
                                "maxUsers", maxUsers,
                                "maxStorageMb", maxStorageMb,
                                "trialDays", trialDays))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).get("id").asString());
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

    private String tenantToken(School school, RoleCode role) {
        return tokenIssuer
                .issueAccessToken(AccountRef.user(UUID.randomUUID(), school.getId()), Set.of(role.name()))
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
}
