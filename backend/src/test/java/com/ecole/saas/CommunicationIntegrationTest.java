package com.ecole.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecole.saas.communication.AbsenceNotificationCommand;
import com.ecole.saas.communication.CampaignStatus;
import com.ecole.saas.communication.CampaignSummary;
import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.CommunicationPort;
import com.ecole.saas.communication.NotificationDispatchResult;
import com.ecole.saas.communication.PaymentDueReminderNotificationCommand;
import com.ecole.saas.communication.PaymentReceiptNotificationCommand;
import com.ecole.saas.communication.ReportCardPublishedNotificationCommand;
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
class CommunicationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final SchoolService schoolService;
    private final SubscriptionService subscriptionService;
    private final UserAccountService userAccountService;
    private final CommunicationPort communicationPort;
    private final TokenIssuer tokenIssuer;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CommunicationIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            SchoolService schoolService,
            SubscriptionService subscriptionService,
            UserAccountService userAccountService,
            CommunicationPort communicationPort,
            TokenIssuer tokenIssuer,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.schoolService = schoolService;
        this.subscriptionService = subscriptionService;
        this.userAccountService = userAccountService;
        this.communicationPort = communicationPort;
        this.tokenIssuer = tokenIssuer;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldManageTemplatesConsentCampaignsDeduplicationIdempotencyDeliveryLifecycleAndInbox() throws Exception {
        School school = createSchool("COMA", SchoolType.PRIVATE_SCHOOL);
        createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));

        User directorUser = createTenantUser(school.getId(), "dir@coma.sn", "Mamadou", "Diop", RoleCode.DIRECTOR);
        User teacherUser = createTenantUser(school.getId(), "prof@coma.sn", "Ousmane", "Ndiaye", RoleCode.TEACHER);
        User parentUser1 = createTenantUser(school.getId(), "parent1@coma.sn", "Aminata", "Fall", RoleCode.PARENT);
        String directorToken = tenantTokenForUser(school.getId(), directorUser.getId(), RoleCode.DIRECTOR);
        String teacherToken = tenantTokenForUser(school.getId(), teacherUser.getId(), RoleCode.TEACHER);
        String parentToken1 = tenantTokenForUser(school.getId(), parentUser1.getId(), RoleCode.PARENT);

        // Préparation académique, élèves, familles, cohorte centre et enseignant
        UUID yearId = createAcademicYearViaApi(directorToken, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(directorToken, yearId);
        UUID levelId = createGradeLevelViaApi(directorToken, "6E", "Sixième", "MIDDLE_SCHOOL", 1);
        UUID mathId = createSubjectViaApi(directorToken, "MATH", "Mathématiques", "SCIENTIFIC", 4);
        UUID classRoomId = createClassRoomViaApi(directorToken, yearId, levelId, "6E-A", "Sixième A", 40);

        UUID student1 = createStudentViaApi(directorToken, "Awa", "Fall", "FEMALE", "+221771110001", "awa@coma.sn");
        UUID student2 = createStudentViaApi(directorToken, "Moussa", "Fall", "MALE", "+221771110002", "moussa@coma.sn");
        enrollInClassViaApi(directorToken, student1, yearId, classRoomId);
        enrollInClassViaApi(directorToken, student2, yearId, classRoomId);

        UUID familyId = createFamilyViaApi(directorToken, "FAM-FALL", "Famille Fall");
        UUID guardian1 = createGuardianViaApi(
                directorToken, familyId, "Aminata", "Fall", "+221772220001", "parent1@coma.sn");
        UUID guardian2 = createGuardianViaApi(
                directorToken, familyId, "Ibrahima", "Fall", "+221772220002", "parent2@coma.sn");
        linkUserToGuardian(school.getId(), guardian1, parentUser1.getId());

        // guardian1 est lié aux DEUX élèves de 6E-A (test de déduplication) ; guardian2 est lié à student2
        linkGuardianToStudentViaApi(directorToken, student1, guardian1, "MOTHER", true, true);
        linkGuardianToStudentViaApi(directorToken, student2, guardian1, "MOTHER", true, true);
        linkGuardianToStudentViaApi(directorToken, student2, guardian2, "FATHER", false, false);

        UUID teacherId = createTeacherViaApi(
                directorToken, "ENS-01", teacherUser.getId(), "Ousmane", "Ndiaye", "PERMANENT", "+221773330001");
        UUID groupId = createCenterGroupViaApi(directorToken, yearId, levelId, mathId, "COH-6E", "Soutien 6e");
        activateCenterGroupViaApi(directorToken, groupId);
        enrollInCenterGroupViaApi(directorToken, groupId, student1);

        // 1. CRUD Modèles multicanaux + prévisualisation
        MvcResult tplRes = mockMvc.perform(post("/api/v1/communication/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "reunion_classe",
                                "name", "Convocation réunion de classe",
                                "category", "SUMMONS",
                                "channel", "SMS",
                                "language", "fr",
                                "bodyTemplate", "Bonjour {{recipientName}}, réunion le {{date}} pour la classe {{className}}. - {{schoolName}}",
                                "estimatedUnitCostXof", 25))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("REUNION_CLASSE"))
                .andExpect(jsonPath("$.estimatedUnitCostXof").value(25))
                .andReturn();
        UUID templateId = UUID.fromString(readJson(tplRes).get("id").asString());

        mockMvc.perform(post("/api/v1/communication/templates/" + templateId + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "variables", Map.of(
                                        "recipientName", "Aminata Fall",
                                        "date", "15/11/2026",
                                        "className", "6E-A",
                                        "schoolName", school.getName()),
                                "strict", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsSegments").value(1))
                .andExpect(jsonPath("$.estimatedCostXof").value(25))
                .andExpect(jsonPath("$.renderedBody").value(org.hamcrest.Matchers.containsString("Aminata Fall")));

        // Désactivation puis réactivation
        mockMvc.perform(post("/api/v1/communication/templates/" + templateId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(post("/api/v1/communication/templates/" + templateId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        // 2. Préférences & consentement : guardian2 refuse les SMS ; parentUser1 met à jour ses propres préférences
        mockMvc.perform(put("/api/v1/communication/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "contactType", "GUARDIAN",
                                "contactId", guardian2,
                                "inAppEnabled", true,
                                "smsEnabled", false,
                                "whatsappEnabled", true,
                                "emailEnabled", true,
                                "marketingOptIn", false,
                                "preferredChannel", "WHATSAPP",
                                "preferredLanguage", "fr",
                                "consentSource", "OPT_OUT_REQUEST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsEnabled").value(false))
                .andExpect(jsonPath("$.marketingOptIn").value(false));

        mockMvc.perform(put("/api/v1/communication/preferences/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "inAppEnabled", true,
                                "smsEnabled", true,
                                "whatsappEnabled", true,
                                "emailEnabled", true,
                                "marketingOptIn", true,
                                "preferredChannel", "IN_APP",
                                "preferredLanguage", "fr"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactType").value("GUARDIAN"))
                .andExpect(jsonPath("$.contactId").value(guardian1.toString()))
                .andExpect(jsonPath("$.consentSource").value("SELF_SERVICE"));

        // 3. Campagne SMS sur la classe 6E-A : déduplication de guardian1 + opt-out de guardian2 + idempotence
        Map<String, Object> classCampaignPayload = new LinkedHashMap<>();
        classCampaignPayload.put("title", "Réunion 6E-A");
        classCampaignPayload.put("category", "SUMMONS");
        classCampaignPayload.put("channel", "SMS");
        classCampaignPayload.put("templateId", templateId);
        classCampaignPayload.put("audienceType", "CLASS_ROOM_GUARDIANS");
        classCampaignPayload.put("audienceTargetId", classRoomId);
        classCampaignPayload.put("variables", Map.of("date", "15/11/2026", "className", "6E-A"));
        classCampaignPayload.put("dispatchImmediately", true);

        MvcResult campRes = mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "camp-6ea-reunion-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(classCampaignPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARTIALLY_FAILED"))
                .andExpect(jsonPath("$.totalRecipients").value(2)) // guardian1 dédupliqué + guardian2
                .andExpect(jsonPath("$.sentCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.actualCostXof").value(25))
                .andReturn();
        UUID campaignId = UUID.fromString(readJson(campRes).get("id").asString());

        // Rejeu idempotent identique -> 200 OK + X-Idempotent-Replay: true, aucun doublon
        mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "camp-6ea-reunion-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(classCampaignPayload)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(campaignId.toString()));

        // Réutilisation de la même clé d'idempotence avec payload différent -> 409 Conflict
        Map<String, Object> alteredPayload = new LinkedHashMap<>(classCampaignPayload);
        alteredPayload.put("title", "Autre titre");
        mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "camp-6ea-reunion-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(alteredPayload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:idempotency-key-conflict"));

        // Vérification des 2 remises (guardian1 SENT personnalisé, guardian2 FAILED CHANNEL_OPTED_OUT)
        MvcResult delivRes = mockMvc.perform(get("/api/v1/communication/campaigns/" + campaignId + "/deliveries")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        JsonNode deliveries = readJson(delivRes).get("content");
        UUID guardian1DeliveryId = null;
        for (JsonNode d : deliveries) {
            if (d.get("recipientId").asString().equals(guardian1.toString())) {
                guardian1DeliveryId = UUID.fromString(d.get("id").asString());
                assertThat(d.get("status").asString()).isEqualTo("SENT");
                assertThat(d.get("renderedBody").asString()).contains("Aminata Fall");
                assertThat(d.get("chargedCostXof").asLong()).isEqualTo(25L);
            } else {
                assertThat(d.get("status").asString()).isEqualTo("FAILED");
                assertThat(d.get("failureCode").asString()).isEqualTo("CHANNEL_OPTED_OUT");
                assertThat(d.get("chargedCostXof").asLong()).isEqualTo(0L);
            }
        }
        assertThat(guardian1DeliveryId).isNotNull();

        // 4. Cycle de vie des statuts de remise + relance technique (retry-failed) + immutabilité SQL
        mockMvc.perform(post("/api/v1/communication/deliveries/" + guardian1DeliveryId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "status", "FAILED",
                                "providerStatus", "TEMP_ERR",
                                "failureCode", "GATEWAY_ERROR",
                                "failureReason", "Timeout passerelle simulé"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        // Relance : seule la remise en GATEWAY_ERROR est relancée, pas CHANNEL_OPTED_OUT
        mockMvc.perform(post("/api/v1/communication/campaigns/" + campaignId + "/retry-failed")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retriedCount").value(1))
                .andExpect(jsonPath("$.succeededCount").value(1));

        // Accusé DELIVERED puis READ + consultation du journal immuable d'événements
        mockMvc.perform(post("/api/v1/communication/deliveries/" + guardian1DeliveryId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "DELIVERED", "providerStatus", "DELIVRD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        mockMvc.perform(post("/api/v1/communication/deliveries/" + guardian1DeliveryId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "READ", "providerStatus", "SEEN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READ"));

        MvcResult eventsRes = mockMvc.perform(get("/api/v1/communication/deliveries/" + guardian1DeliveryId + "/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readJson(eventsRes).size()).isGreaterThanOrEqualTo(7);

        // Vérification de l'immutabilité SQL sur communication_delivery_events (trigger BEFORE UPDATE OR DELETE)
        UUID finalDeliveryId = guardian1DeliveryId;
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + school.getId() + "', true)");
            jdbcTemplate.update(
                    "UPDATE communication_delivery_events SET detail = 'falsifie' WHERE delivery_id = ?",
                    finalDeliveryId);
        })).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + school.getId() + "', true)");
            jdbcTemplate.update(
                    "DELETE FROM communication_delivery_events WHERE delivery_id = ?",
                    finalDeliveryId);
        })).isInstanceOf(DataAccessException.class);

        // 5. Envoi WHATSAPP sur cohorte centre + IN_APP vers parents + EMAIL vers personnel + annulation brouillon
        mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "camp-cohorte-wa-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Séance de renforcement",
                                "category", "CENTER_SESSION",
                                "channel", "WHATSAPP",
                                "audienceType", "CENTER_GROUP_GUARDIANS",
                                "audienceTargetId", groupId,
                                "body", "Séance samedi à 10h pour {{recipientName}}.",
                                "dispatchImmediately", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.sentCount").value(1))
                .andExpect(jsonPath("$.actualCostXof").value(15));

        MvcResult inAppCampRes = mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "camp-inapp-parent-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Info rentrée",
                                "category", "GENERAL",
                                "channel", "IN_APP",
                                "audienceType", "GUARDIAN",
                                "audienceTargetId", guardian1,
                                "subject", "Bienvenue à la rentrée",
                                "body", "Bonjour {{recipientName}}, bienvenue !",
                                "dispatchImmediately", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.deliveredCount").value(1))
                .andExpect(jsonPath("$.actualCostXof").value(0))
                .andReturn();
        UUID inAppCampaignId = UUID.fromString(readJson(inAppCampRes).get("id").asString());

        // Envoi EMAIL à tous les enseignants
        mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "camp-email-teachers-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Note de service",
                                "category", "GENERAL",
                                "channel", "EMAIL",
                                "audienceType", "ALL_TEACHERS",
                                "subject", "Conseil pédagogique",
                                "body", "Bonjour {{recipientName}}, conseil vendredi.",
                                "dispatchImmediately", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRecipients").value(1));

        // Campagne brouillon puis annulation
        MvcResult draftRes = mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .header("Idempotency-Key", "camp-draft-cancel-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Brouillon annulable",
                                "category", "GENERAL",
                                "channel", "IN_APP",
                                "audienceType", "ALL_STAFF",
                                "subject", "Test",
                                "body", "Contenu brouillon",
                                "dispatchImmediately", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        UUID draftId = UUID.fromString(readJson(draftRes).get("id").asString());
        mockMvc.perform(post("/api/v1/communication/campaigns/" + draftId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Reporté au mois prochain"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 6. Boîte de réception IN_APP de parentUser1 + cloisonnement strict entre utilisateurs du même tenant
        mockMvc.perform(get("/api/v1/communication/inbox/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        MvcResult inboxRes = mockMvc.perform(get("/api/v1/communication/inbox?unreadOnly=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn();
        UUID parentInboxDeliveryId = UUID.fromString(readJson(inboxRes).get("content").get(0).get("id").asString());

        // teacherUser (même établissement) ne peut ni lire ni marquer lu le message de parentUser1 -> 404
        mockMvc.perform(get("/api/v1/communication/inbox/" + parentInboxDeliveryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/communication/inbox/" + parentInboxDeliveryId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isNotFound());

        // parentUser1 marque son message comme lu
        mockMvc.perform(post("/api/v1/communication/inbox/" + parentInboxDeliveryId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READ"))
                .andExpect(jsonPath("$.read").value(true));

        mockMvc.perform(get("/api/v1/communication/inbox/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));

        // La campagne in-app reflète désormais readCount == 1
        mockMvc.perform(get("/api/v1/communication/campaigns/" + inAppCampaignId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readCount").value(1));

        // 7. Synthèse globale & audit immuable (catégorie COMMUNICATION, sans PII)
        mockMvc.perform(get("/api/v1/communication/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCampaigns").value(5))
                .andExpect(jsonPath("$.totalActualCostXof").value(40));

        MvcResult auditRes = mockMvc.perform(get("/api/v1/audit/logs?category=COMMUNICATION")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readJson(auditRes).get("totalElements").asLong()).isGreaterThanOrEqualTo(5L);
        assertThat(auditRes.getResponse().getContentAsString()).doesNotContain("+221772220001");
    }

    @Test
    void shouldTriggerBusinessNotificationsViaPublicCommunicationPort() throws Exception {
        School school = createSchool("PORT", SchoolType.PRIVATE_SCHOOL);
        createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));

        User directorUser = createTenantUser(school.getId(), "dir@port.sn", "Cheikh", "Sarr", RoleCode.DIRECTOR);
        User parentUser = createTenantUser(school.getId(), "parent@port.sn", "Mariama", "Ba", RoleCode.PARENT);
        String directorToken = tenantTokenForUser(school.getId(), directorUser.getId(), RoleCode.DIRECTOR);

        UUID studentId = createStudentViaApi(directorToken, "Lamine", "Ba", "MALE", null, null);
        UUID guardianId = createGuardianViaApi(
                directorToken, null, "Mariama", "Ba", "+221774440001", "parent@port.sn");
        linkUserToGuardian(school.getId(), guardianId, parentUser.getId());
        linkGuardianToStudentViaApi(directorToken, studentId, guardianId, "MOTHER", true, true);

        TenantContext.runWithTenant(TenantId.of(school.getId()), () -> {
            // 1. Alerte absence (SMS)
            NotificationDispatchResult absRes = communicationPort.notifyStudentAbsence(
                    new AbsenceNotificationCommand(
                            "port-abs-001",
                            studentId,
                            UUID.randomUUID(),
                            CommunicationChannel.SMS,
                            LocalDate.of(2026, 11, 20),
                            "ABSENT",
                            null,
                            "3E-A",
                            null));
            assertThat(absRes.status()).isEqualTo(CampaignStatus.COMPLETED);
            assertThat(absRes.sentOrDeliveredCount()).isEqualTo(1);
            assertThat(absRes.actualCostXof()).isGreaterThan(0L);
            assertThat(absRes.idempotentReplay()).isFalse();

            // 2. Reçu de paiement (WHATSAPP, montant entier FCFA)
            NotificationDispatchResult receiptRes = communicationPort.notifyPaymentReceipt(
                    new PaymentReceiptNotificationCommand(
                            "port-rec-001",
                            studentId,
                            UUID.randomUUID(),
                            CommunicationChannel.WHATSAPP,
                            "REC-PORT-2026-000001",
                            75_000L,
                            25_000L,
                            "MOBILE_MONEY_WAVE",
                            null));
            assertThat(receiptRes.status()).isEqualTo(CampaignStatus.COMPLETED);
            assertThat(receiptRes.actualCostXof()).isEqualTo(15L);

            // 3. Publication de bulletin (IN_APP -> incrémente la boîte de réception du parent)
            NotificationDispatchResult bulletinRes = communicationPort.notifyReportCardPublished(
                    new ReportCardPublishedNotificationCommand(
                            "port-bul-001",
                            studentId,
                            UUID.randomUUID(),
                            CommunicationChannel.IN_APP,
                            "Trimestre 1",
                            "3E-A",
                            "15.75 / 20",
                            2,
                            "Très Bien",
                            null));
            assertThat(bulletinRes.status()).isEqualTo(CampaignStatus.COMPLETED);
            assertThat(communicationPort.countUnreadInAppNotifications(parentUser.getId())).isEqualTo(1L);

            // 4. Rappel d'échéance (EMAIL) + contrôle d'idempotence sur rejeu
            PaymentDueReminderNotificationCommand dueCmd = new PaymentDueReminderNotificationCommand(
                    "port-due-001",
                    studentId,
                    UUID.randomUUID(),
                    CommunicationChannel.EMAIL,
                    "FAC-PORT-2026-000001",
                    25_000L,
                    LocalDate.of(2026, 12, 5),
                    null);
            NotificationDispatchResult dueFirst = communicationPort.notifyPaymentDueReminder(dueCmd);
            NotificationDispatchResult dueReplay = communicationPort.notifyPaymentDueReminder(dueCmd);
            assertThat(dueFirst.idempotentReplay()).isFalse();
            assertThat(dueReplay.idempotentReplay()).isTrue();
            assertThat(dueReplay.campaignId()).isEqualTo(dueFirst.campaignId());

            CampaignSummary summary = communicationPort.findCampaignById(dueFirst.campaignId()).orElseThrow();
            assertThat(summary.totalRecipients()).isEqualTo(1);
        });
    }

    @Test
    void shouldEnforceRolePermissionsFineTeacherTutorScopeAndSubscriptionGuard() throws Exception {
        School school = createSchool("RBAC", SchoolType.PRIVATE_SCHOOL);
        createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));

        User directorUser = createTenantUser(school.getId(), "dir@rbac.sn", "Dir", "One", RoleCode.DIRECTOR);
        User teacherUser = createTenantUser(school.getId(), "prof@rbac.sn", "Prof", "One", RoleCode.TEACHER);
        User cashierUser = createTenantUser(school.getId(), "cash@rbac.sn", "Cash", "One", RoleCode.CASHIER);
        User censorUser = createTenantUser(school.getId(), "cens@rbac.sn", "Cens", "One", RoleCode.CENSOR);
        User parentUser = createTenantUser(school.getId(), "par@rbac.sn", "Par", "One", RoleCode.PARENT);

        String directorToken = tenantTokenForUser(school.getId(), directorUser.getId(), RoleCode.DIRECTOR);
        String teacherToken = tenantTokenForUser(school.getId(), teacherUser.getId(), RoleCode.TEACHER);
        String cashierToken = tenantTokenForUser(school.getId(), cashierUser.getId(), RoleCode.CASHIER);
        String censorToken = tenantTokenForUser(school.getId(), censorUser.getId(), RoleCode.CENSOR);
        String parentToken = tenantTokenForUser(school.getId(), parentUser.getId(), RoleCode.PARENT);
        String adminToken = platformAdminToken();

        UUID yearId = createAcademicYearViaApi(directorToken, "2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYearViaApi(directorToken, yearId);
        UUID levelId = createGradeLevelViaApi(directorToken, "5E", "Cinquième", "MIDDLE_SCHOOL", 2);
        UUID mathId = createSubjectViaApi(directorToken, "MATH", "Mathématiques", "SCIENTIFIC", 4);
        UUID assignedClassId = createClassRoomViaApi(directorToken, yearId, levelId, "5E-A", "Cinquième A", 35);
        UUID otherClassId = createClassRoomViaApi(directorToken, yearId, levelId, "5E-B", "Cinquième B", 35);
        assignSubjectToClassViaApi(directorToken, assignedClassId, mathId);

        UUID teacherId = createTeacherViaApi(
                directorToken, "ENS-R1", teacherUser.getId(), "Prof", "One", "PERMANENT", "+221775550001");
        assignTeacherToClassViaApi(directorToken, teacherId, yearId, assignedClassId, mathId);

        UUID studentId = createStudentViaApi(directorToken, "Khadija", "Sow", "FEMALE", null, null);
        enrollInClassViaApi(directorToken, studentId, yearId, assignedClassId);
        UUID guardianId = createGuardianViaApi(directorToken, null, "Modou", "Sow", "+221775550002", "sow@rbac.sn");
        linkGuardianToStudentViaApi(directorToken, studentId, guardianId, "FATHER", true, true);

        // CASHIER : autorisé sur PAYMENT_DUE_REMINDER, refusé sur GENERAL et création modèle
        mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .header("Idempotency-Key", "rbac-cash-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "PAYMENT_DUE_REMINDER",
                                "channel", "SMS",
                                "audienceType", "GUARDIAN",
                                "audienceTargetId", guardianId,
                                "body", "Rappel échéance."))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .header("Idempotency-Key", "rbac-cash-ko")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "GENERAL",
                                "channel", "SMS",
                                "audienceType", "GUARDIAN",
                                "audienceTargetId", guardianId,
                                "body", "Message général."))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/communication/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "CASH_TPL",
                                "name", "Interdit",
                                "category", "PAYMENT_RECEIPT",
                                "channel", "SMS",
                                "bodyTemplate", "Test",
                                "estimatedUnitCostXof", 0))))
                .andExpect(status().isForbidden());

        // CENSOR : autorisé sur SUMMONS, refusé sur PAYMENT_DUE_REMINDER
        mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(censorToken))
                        .header("Idempotency-Key", "rbac-cens-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "SUMMONS",
                                "channel", "SMS",
                                "audienceType", "GUARDIAN",
                                "audienceTargetId", guardianId,
                                "body", "Convocation discipline."))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(censorToken))
                        .header("Idempotency-Key", "rbac-cens-ko")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "PAYMENT_DUE_REMINDER",
                                "channel", "SMS",
                                "audienceType", "GUARDIAN",
                                "audienceTargetId", guardianId,
                                "body", "Relance interdite."))))
                .andExpect(status().isForbidden());

        // TEACHER : autorisé sur sa classe affectée, refusé sur l'autre classe et ALL_GUARDIANS
        mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .header("Idempotency-Key", "rbac-prof-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "GENERAL",
                                "channel", "SMS",
                                "audienceType", "CLASS_ROOM_GUARDIANS",
                                "audienceTargetId", assignedClassId,
                                "body", "Devoir de maths pour demain."))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .header("Idempotency-Key", "rbac-prof-other-class")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "GENERAL",
                                "channel", "SMS",
                                "audienceType", "CLASS_ROOM_GUARDIANS",
                                "audienceTargetId", otherClassId,
                                "body", "Hors périmètre."))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .header("Idempotency-Key", "rbac-prof-global")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "GENERAL",
                                "channel", "SMS",
                                "audienceType", "ALL_GUARDIANS",
                                "body", "Interdit."))))
                .andExpect(status().isForbidden());

        // PARENT : inbox + /preferences/me autorisés, back-office refusé (403)
        mockMvc.perform(get("/api/v1/communication/inbox")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/communication/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(parentToken)))
                .andExpect(status().isForbidden());

        // ADMIN_SAAS : refusé sur routes tenant (403)
        mockMvc.perform(get("/api/v1/communication/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/communication/inbox")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());

        // Garde d'abonnement : EXPIRED -> lecture seule 200, écriture 403 ; SUSPENDED -> lecture 403
        School expiredSchool = createSchool("EXPC", SchoolType.PRIVATE_SCHOOL);
        createSubscription(expiredSchool, SubscriptionStatus.ACTIVE, Instant.now().minus(2, ChronoUnit.DAYS));
        String expiredDirToken = tenantToken(expiredSchool.getId(), RoleCode.DIRECTOR);

        mockMvc.perform(get("/api/v1/communication/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredDirToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/communication/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredDirToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "EXP_1",
                                "name", "Bloqué",
                                "category", "GENERAL",
                                "channel", "SMS",
                                "bodyTemplate", "Test",
                                "estimatedUnitCostXof", 0))))
                .andExpect(status().isForbidden());

        School suspendedSchool = createSchool("SUSC", SchoolType.PRIVATE_SCHOOL);
        createSubscription(suspendedSchool, SubscriptionStatus.SUSPENDED, Instant.now().plus(10, ChronoUnit.DAYS));
        String suspendedDirToken = tenantToken(suspendedSchool.getId(), RoleCode.DIRECTOR);

        mockMvc.perform(get("/api/v1/communication/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspendedDirToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldStrictlyIsolateCommunicationAcrossTenantsInApiAndPostgresRls() throws Exception {
        School schoolA = createSchool("ISOA", SchoolType.PRIVATE_SCHOOL);
        School schoolB = createSchool("ISOB", SchoolType.PRIVATE_SCHOOL);
        createSubscription(schoolA, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));
        createSubscription(schoolB, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));

        User dirA = createTenantUser(schoolA.getId(), "dir@isoa.sn", "Dir", "A", RoleCode.DIRECTOR);
        User dirB = createTenantUser(schoolB.getId(), "dir@isob.sn", "Dir", "B", RoleCode.DIRECTOR);
        String tokenA = tenantTokenForUser(schoolA.getId(), dirA.getId(), RoleCode.DIRECTOR);
        String tokenB = tenantTokenForUser(schoolB.getId(), dirB.getId(), RoleCode.DIRECTOR);

        UUID guardianA = createGuardianViaApi(tokenA, null, "Parent", "A", "+221776660001", "pa@isoa.sn");
        UUID guardianB = createGuardianViaApi(tokenB, null, "Parent", "B", "+221776660002", "pb@isob.sn");

        // Même code modèle et même clé d'idempotence dans A et B (unicités composites par tenant_id)
        MvcResult tplA = mockMvc.perform(post("/api/v1/communication/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "SHARED_CODE",
                                "name", "Modèle A",
                                "category", "GENERAL",
                                "channel", "SMS",
                                "bodyTemplate", "Message A",
                                "estimatedUnitCostXof", 25))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID templateIdA = UUID.fromString(readJson(tplA).get("id").asString());

        MvcResult tplB = mockMvc.perform(post("/api/v1/communication/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "SHARED_CODE",
                                "name", "Modèle B",
                                "category", "GENERAL",
                                "channel", "SMS",
                                "bodyTemplate", "Message B",
                                "estimatedUnitCostXof", 25))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID templateIdB = UUID.fromString(readJson(tplB).get("id").asString());

        // Préférences dans A et B
        mockMvc.perform(put("/api/v1/communication/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "contactType", "GUARDIAN",
                                "contactId", guardianA,
                                "inAppEnabled", true,
                                "smsEnabled", true,
                                "whatsappEnabled", true,
                                "emailEnabled", true,
                                "marketingOptIn", true))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/communication/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "contactType", "GUARDIAN",
                                "contactId", guardianB,
                                "inAppEnabled", true,
                                "smsEnabled", true,
                                "whatsappEnabled", true,
                                "emailEnabled", true,
                                "marketingOptIn", true))))
                .andExpect(status().isOk());

        // Même Idempotency-Key dans A et B
        MvcResult campA = mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("Idempotency-Key", "shared-idem-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "GENERAL",
                                "channel", "SMS",
                                "templateId", templateIdA,
                                "audienceType", "GUARDIAN",
                                "audienceTargetId", guardianA))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID campaignIdA = UUID.fromString(readJson(campA).get("id").asString());

        MvcResult campB = mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .header("Idempotency-Key", "shared-idem-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "GENERAL",
                                "channel", "SMS",
                                "templateId", templateIdB,
                                "audienceType", "GUARDIAN",
                                "audienceTargetId", guardianB))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID campaignIdB = UUID.fromString(readJson(campB).get("id").asString());

        // Isolation API REST : A ne peut ni lire les ressources de B ni forger X-Tenant-Id / ?tenantId
        mockMvc.perform(get("/api/v1/communication/templates/" + templateIdB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("X-Tenant-Id", schoolB.getId().toString())
                        .param("tenantId", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/communication/campaigns/" + campaignIdB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .header("X-Tenant-Id", schoolB.getId().toString())
                        .header("Idempotency-Key", "cross-tenant-target-attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "GENERAL",
                                "channel", "SMS",
                                "audienceType", "GUARDIAN",
                                "audienceTargetId", guardianB,
                                "body", "Tentative cross-tenant"))))
                .andExpect(status().isNotFound());

        // Isolation PostgreSQL RLS sous le rôle non-superuser ecole_app (NOBYPASSRLS) sur les 6 tables
        List<String> tables = List.of(
                "communication_sequences",
                "communication_templates",
                "communication_contact_preferences",
                "communication_campaigns",
                "communication_deliveries",
                "communication_delivery_events");

        // 1. Fail-closed : sans contexte tenant -> 0 ligne sur les 6 tables
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '', true)");
            for (String table : tables) {
                Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
                assertThat(count).as("Fail-closed sans tenant sur " + table).isZero();
            }
        });

        // 2. Avec contexte tenant A : aucune ligne de B n'est visible
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
            for (String table : tables) {
                Integer countA = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, schoolA.getId());
                Integer countB = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, schoolB.getId());
                assertThat(countA).as("Lignes tenant A visibles sur " + table).isPositive();
                assertThat(countB).as("Lignes tenant B invisibles sur " + table).isZero();
            }
        });

        // 3. Blocage INSERT cross-tenant sous ecole_app
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
            jdbcTemplate.update(
                    """
                    INSERT INTO communication_templates
                    (tenant_id, code, name, category, channel, language, body_template, estimated_unit_cost_xof, active)
                    VALUES (?, 'CROSS_TPL', 'Cross', 'GENERAL', 'SMS', 'fr', 'Test', 0, true)
                    """,
                    schoolB.getId());
        })).isInstanceOf(DataAccessException.class);

        // 4. Blocage DELETE sur communication_campaigns sous ecole_app (droit non accordé)
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolA.getId() + "', true)");
            jdbcTemplate.update("DELETE FROM communication_campaigns WHERE id = ?", campaignIdA);
        })).isInstanceOf(DataAccessException.class);
    }

    @Test
    void shouldHideInactiveTemplatesFromNonAdminsAndSupportCashierPlusCensorMultiRole() throws Exception {
        School school = createSchool("COMM-M4", SchoolType.PRIVATE_SCHOOL);
        createSubscription(school, SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS));

        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);
        String cashierToken = tenantToken(school.getId(), RoleCode.CASHIER);
        String censorToken = tenantToken(school.getId(), RoleCode.CENSOR);
        String teacherToken = tenantToken(school.getId(), RoleCode.TEACHER);
        String multiRoleToken = tokenIssuer
                .issueAccessToken(
                        AccountRef.user(UUID.randomUUID(), school.getId()),
                        Set.of(RoleCode.CASHIER.name(), RoleCode.CENSOR.name()))
                .value();

        // Créer 1 modèle actif et 1 modèle désactivé
        MvcResult activeTplRes = mockMvc.perform(post("/api/v1/communication/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "ACTIF_M4",
                                "name", "Modèle actif",
                                "category", "GENERAL",
                                "channel", "SMS",
                                "bodyTemplate", "Bonjour {{recipientName}}",
                                "estimatedUnitCostXof", 15L))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID activeTemplateId = UUID.fromString(readJson(activeTplRes).get("id").asString());

        MvcResult inactiveTplRes = mockMvc.perform(post("/api/v1/communication/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "INACTIF_M4",
                                "name", "Modèle archivé",
                                "category", "GENERAL",
                                "channel", "SMS",
                                "bodyTemplate", "Brouillon confidentiel",
                                "estimatedUnitCostXof", 15L))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID inactiveTemplateId = UUID.fromString(readJson(inactiveTplRes).get("id").asString());

        mockMvc.perform(post("/api/v1/communication/templates/" + inactiveTemplateId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // M4 (a) : hors rôles admin, ?active=false ne révèle aucun modèle inactif, et GET/preview sur un modèle inactif -> 403
        for (String nonAdminToken : List.of(cashierToken, censorToken, teacherToken, multiRoleToken)) {
            mockMvc.perform(get("/api/v1/communication/templates")
                            .header(HttpHeaders.AUTHORIZATION, bearer(nonAdminToken))
                            .param("active", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(activeTemplateId.toString()));

            mockMvc.perform(get("/api/v1/communication/templates/" + inactiveTemplateId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(nonAdminToken)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/v1/communication/templates/" + inactiveTemplateId + "/preview")
                            .header(HttpHeaders.AUTHORIZATION, bearer(nonAdminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("variables", Map.of(), "strict", false))))
                    .andExpect(status().isForbidden());
        }

        // L'administrateur (DIRECTOR) continue de voir et prévisualiser le modèle inactif
        mockMvc.perform(get("/api/v1/communication/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(inactiveTemplateId.toString()));
        mockMvc.perform(get("/api/v1/communication/templates/" + inactiveTemplateId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk());

        // M4 (b) : cumul de rôles CASHIER + CENSOR autorise lecture et envoi des campagnes financières ET non financières
        UUID familyId = createFamilyViaApi(directorToken, "FAM-M4", "Famille M4");
        UUID guardianId = createGuardianViaApi(
                directorToken, familyId, "Moussa", "Ndiaye", "+221774445566", "moussa.m4@ecole.sn");

        MvcResult finCampaignRes = mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(multiRoleToken))
                        .header("Idempotency-Key", "comm-m4-fin-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Relance scolarité M4",
                                "category", "PAYMENT_DUE_REMINDER",
                                "channel", "SMS",
                                "audienceType", "GUARDIAN",
                                "audienceTargetId", guardianId,
                                "body", "Rappel échéance scolarité.",
                                "dispatchImmediately", true))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID finCampaignId = UUID.fromString(readJson(finCampaignRes).get("id").asString());

        MvcResult pedCampaignRes = mockMvc.perform(post("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(multiRoleToken))
                        .header("Idempotency-Key", "comm-m4-ped-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Information pédagogique M4",
                                "category", "GENERAL",
                                "channel", "SMS",
                                "audienceType", "GUARDIAN",
                                "audienceTargetId", guardianId,
                                "body", "Réunion parents-professeurs vendredi.",
                                "dispatchImmediately", true))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID pedCampaignId = UUID.fromString(readJson(pedCampaignRes).get("id").asString());

        mockMvc.perform(get("/api/v1/communication/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(multiRoleToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/communication/campaigns/" + finCampaignId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(multiRoleToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/communication/campaigns/" + pedCampaignId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(multiRoleToken)))
                .andExpect(status().isOk());
    }

    // --- Helpers ---

    private School createSchool(String prefix, SchoolType type) {
        String code = prefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return schoolService.createSchool(
                code, "École " + code, type, "+221770000000", "contact@" + code.toLowerCase() + ".sn", "Dakar");
    }

    private void createSubscription(School school, SubscriptionStatus status, Instant endsAt) {
        SubscriptionPlan plan = subscriptionService.createPlan(
                "PLAN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                "Forfait Communication",
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

    private void linkUserToGuardian(UUID schoolId, UUID guardianId, UUID userId) {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + schoolId + "', true)");
            jdbcTemplate.update("UPDATE guardians SET user_id = ? WHERE id = ?", userId, guardianId);
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

    private UUID createCenterGroupViaApi(
            String token, UUID yearId, UUID levelId, UUID subjectId, String code, String name) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/center/groups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "academicYearId", yearId,
                                "gradeLevelId", levelId,
                                "subjectId", subjectId,
                                "code", code,
                                "name", name,
                                "groupType", "SUBJECT_SUPPORT",
                                "capacity", 20,
                                "startDate", "2026-10-01",
                                "endDate", "2027-06-30"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private void activateCenterGroupViaApi(String token, UUID groupId) throws Exception {
        mockMvc.perform(post("/api/v1/center/groups/" + groupId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private void enrollInCenterGroupViaApi(String token, UUID groupId, UUID studentId) throws Exception {
        mockMvc.perform(post("/api/v1/center/groups/" + groupId + "/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("studentId", studentId, "enrolledAt", "2026-10-02"))))
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
