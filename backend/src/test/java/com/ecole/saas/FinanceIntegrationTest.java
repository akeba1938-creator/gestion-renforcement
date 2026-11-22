package com.ecole.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecole.saas.finance.FamilyFinancialSummary;
import com.ecole.saas.finance.FinanceAccountPort;
import com.ecole.saas.finance.StudentFinancialSummary;
import com.ecole.saas.identity.domain.AccountRef;
import com.ecole.saas.identity.domain.RoleCode;
import com.ecole.saas.identity.domain.TokenIssuer;
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
import java.time.ZoneId;
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
class FinanceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final SchoolService schoolService;
    private final SubscriptionService subscriptionService;
    private final FinanceAccountPort financeAccountPort;
    private final TokenIssuer tokenIssuer;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    FinanceIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            SchoolService schoolService,
            SubscriptionService subscriptionService,
            FinanceAccountPort financeAccountPort,
            TokenIssuer tokenIssuer,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.schoolService = schoolService;
        this.subscriptionService = subscriptionService;
        this.financeAccountPort = financeAccountPort;
        this.tokenIssuer = tokenIssuer;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldRunFullFinanceLifecycleWithLedgerReconciliationAndPublicPort() throws Exception {
        School school = createSchool("FINFLOW", SchoolType.PRIVATE_SCHOOL);
        createActiveSubscription(school, 100);

        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);
        String promoterToken = tenantToken(school.getId(), RoleCode.PROMOTER);
        String cashierToken = tenantToken(school.getId(), RoleCode.CASHIER);
        String secretaryToken = tenantToken(school.getId(), RoleCode.SECRETARY);

        // 1. Structure académique + élève + famille + responsable financier
        UUID yearId = createAcademicYearViaApi(
                directorToken, "2026-2027", "Année 2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYear(directorToken, yearId);
        UUID grade6Id = createGradeLevelViaApi(directorToken, "6E", "Sixième", "MIDDLE_SCHOOL", 1);
        UUID class6AId = createClassViaApi(directorToken, yearId, grade6Id, "6E-A", "6ème A", 40);

        JsonNode student1 = createStudentViaApi(
                directorToken, "Aminata", "Diallo", "FEMALE", "2014-05-12", null, "2026-09-15");
        UUID student1Id = UUID.fromString(student1.get("id").asString());
        String student1Matricule = student1.get("matricule").asString();
        UUID enrollment1Id = enrollStudentViaApi(
                directorToken, student1Id, yearId, class6AId, "NEW_ADMISSION", "2026-09-20");

        JsonNode student2 = createStudentViaApi(
                directorToken, "Ousmane", "Diallo", "MALE", "2015-08-20", null, "2026-09-15");
        UUID student2Id = UUID.fromString(student2.get("id").asString());

        UUID familyId = createFamilyViaApi(directorToken, "FAM-DIALLO", "Famille Diallo", "+221771112233", "Dakar");
        UUID guardianId = createGuardianViaApi(
                directorToken, familyId, "Mamadou", "Diallo", "+221771112233", "mamadou.diallo@example.sn");
        linkGuardianViaApi(directorToken, student1Id, guardianId, "FATHER", true, true);
        linkGuardianViaApi(directorToken, student2Id, guardianId, "FATHER", true, true);

        // 2. Catalogue de frais en FCFA entiers
        UUID inscriptionFeeId = createFeeItemViaApi(
                directorToken, yearId, grade6Id, "INSC-6E", "Frais d'inscription 6ème", "ENROLLMENT", "ONE_TIME", 25_000L);
        UUID tuitionFeeId = createFeeItemViaApi(
                directorToken, yearId, grade6Id, "SCOL-6E", "Scolarité 6ème", "TUITION", "ANNUAL", 75_000L);

        // Doublon de code sur la même année -> 409
        mockMvc.perform(post("/api/v1/finance/fee-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "academicYearId", yearId,
                                "code", "insc-6e",
                                "label", "Doublon",
                                "category", "ENROLLMENT",
                                "frequency", "ONE_TIME",
                                "amountXof", 10_000L,
                                "mandatory", true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:fee-item-already-exists"));

        // 3. Création facture brouillon (résolution auto de enrollmentId, payerGuardianId, familyId) + génération échéancier 3 parts (100 000 -> 33 334 + 33 333 + 33 333)
        MvcResult createInvRes = mockMvc.perform(post("/api/v1/finance/invoices")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", student1Id,
                                "academicYearId", yearId,
                                "title", "Facture annuelle 2026-2027",
                                "dueDate", "2026-10-15",
                                "lines", List.of(
                                        Map.of("feeItemId", inscriptionFeeId, "quantity", 1),
                                        Map.of("feeItemId", tuitionFeeId, "quantity", 1))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoice.status").value("DRAFT"))
                .andExpect(jsonPath("$.invoice.grossAmountXof").value(100_000))
                .andExpect(jsonPath("$.invoice.enrollmentId").value(enrollment1Id.toString()))
                .andExpect(jsonPath("$.invoice.payerGuardianId").value(guardianId.toString()))
                .andExpect(jsonPath("$.invoice.familyId").value(familyId.toString()))
                .andReturn();
        JsonNode invoiceDetail = readJson(createInvRes);
        UUID invoiceId = UUID.fromString(invoiceDetail.get("invoice").get("id").asString());
        String invoiceNumber = invoiceDetail.get("invoice").get("invoiceNumber").asString();
        assertThat(invoiceNumber).startsWith("FAC-" + school.getCode() + "-");

        MvcResult genSchedRes = mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/schedules/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "installmentCount", 3,
                                "firstDueDate", "2026-10-10",
                                "intervalMonths", 1,
                                "labelPrefix", "Tranche"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedules.length()").value(3))
                .andExpect(jsonPath("$.schedules[0].amountXof").value(33_334))
                .andExpect(jsonPath("$.schedules[1].amountXof").value(33_333))
                .andExpect(jsonPath("$.schedules[2].amountXof").value(33_333))
                .andReturn();
        assertThat(readJson(genSchedRes).get("schedules").size()).isEqualTo(3);

        // Émission facture -> écriture CHARGE (DEBIT 100 000)
        mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/issue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("ISSUED"))
                .andExpect(jsonPath("$.invoice.balanceXof").value(100_000));

        // 4. Workflow remise/bourse : demande 20% par CASHIER -> approbation par DIRECTOR -> CREDIT 20 000, net=80 000, tranche 3 réduite à 13 333
        MvcResult scholarshipReqRes = mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "adjustmentType", "SCHOLARSHIP",
                                "calculationMode", "PERCENTAGE",
                                "percentageValue", 20,
                                "reason", "Bourse au mérite 20%"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.resolvedAmountXof").value(20_000))
                .andReturn();
        UUID scholarshipId = UUID.fromString(readJson(scholarshipReqRes).get("id").asString());

        mockMvc.perform(post("/api/v1/finance/adjustments/" + scholarshipId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decisionNote", "Approuvé par la direction"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/v1/finance/invoices/" + invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.discountAmountXof").value(20_000))
                .andExpect(jsonPath("$.invoice.netAmountXof").value(80_000))
                .andExpect(jsonPath("$.invoice.balanceXof").value(80_000))
                .andExpect(jsonPath("$.schedules[2].amountXof").value(13_333));

        // Demande de remise rejetée par PROMOTER
        MvcResult discountReqRes = mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "adjustmentType", "DISCOUNT",
                                "calculationMode", "FIXED_AMOUNT",
                                "fixedAmountXof", 10_000,
                                "reason", "Remise exceptionnelle"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID rejectedAdjId = UUID.fromString(readJson(discountReqRes).get("id").asString());
        mockMvc.perform(post("/api/v1/finance/adjustments/" + rejectedAdjId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decisionNote", "Déjà bénéficiaire d'une bourse"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // V16 : cumul des demandes PENDING_APPROVAL soustrait du solde disponible dès requestAdjustment
        MvcResult pendingAdjRes = mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "adjustmentType", "DISCOUNT",
                                "calculationMode", "FIXED_AMOUNT",
                                "fixedAmountXof", 50_000L,
                                "reason", "Première remise en attente"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID pendingAdjId = UUID.fromString(readJson(pendingAdjRes).get("id").asString());

        // Solde = 80 000, en attente = 50 000 -> disponible = 30 000 ; une demande de 35 000 doit être refusée (409)
        mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "adjustmentType", "DISCOUNT",
                                "calculationMode", "FIXED_AMOUNT",
                                "fixedAmountXof", 35_000L,
                                "reason", "Seconde remise dépassant le solde disponible"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:adjustment-exceeds-balance"));

        // Rejet de la demande temporaire pour laisser le solde intact pour la suite du scénario
        mockMvc.perform(post("/api/v1/finance/adjustments/" + pendingAdjId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decisionNote", "Nettoyage test cumul pending"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // 5. Encaissement espèces partiel (50 000) par CASHIER + test d'idempotence + conflit idempotence
        MvcResult cashPayRes = mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .header("Idempotency-Key", "pay-cash-flow-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", invoiceId,
                                "amountXof", 50_000L,
                                "paymentMethod", "CASH",
                                "notes", "Paiement guichet espèces"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountXof").value(50_000))
                .andExpect(jsonPath("$.confirmationSource").value("MANUAL_DESK"))
                .andExpect(jsonPath("$.reversed").value(false))
                .andExpect(jsonPath("$.receipt.receiptNumber").value(org.hamcrest.Matchers.endsWith("-000001")))
                .andExpect(jsonPath("$.allocations.length()").value(2))
                .andReturn();
        JsonNode cashPayJson = readJson(cashPayRes);
        UUID cashPaymentId = UUID.fromString(cashPayJson.get("id").asString());
        String cashReceiptNo = cashPayJson.get("receipt").get("receiptNumber").asString();

        // Rejeu idempotent strict -> 200 OK + X-Idempotent-Replay: true, même paiement & même reçu (pas de trou)
        mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .header("Idempotency-Key", "pay-cash-flow-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", invoiceId,
                                "amountXof", 50_000L,
                                "paymentMethod", "CASH",
                                "notes", "Paiement guichet espèces"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(cashPaymentId.toString()))
                .andExpect(jsonPath("$.receipt.receiptNumber").value(cashReceiptNo));

        // Conflit d'idempotence (même clé, notes différentes) -> 409
        mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .header("Idempotency-Key", "pay-cash-flow-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", invoiceId,
                                "amountXof", 50_000L,
                                "paymentMethod", "CASH",
                                "notes", "Note altérée"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:idempotency-key-conflict"));

        // Conflit d'idempotence (même clé, montant différent) -> 409
        mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .header("Idempotency-Key", "pay-cash-flow-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", invoiceId,
                                "amountXof", 45_000L,
                                "paymentMethod", "CASH"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:idempotency-key-conflict"));

        // 6. Paiement Mobile Money Wave : référence obligatoire, solde facture (30 000), reçu -000002, doublon référence -> 409
        mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .header("Idempotency-Key", "pay-wave-no-ref-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", invoiceId,
                                "amountXof", 30_000L,
                                "paymentMethod", "MOBILE_MONEY_WAVE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:payment-reference-required"));

        MvcResult wavePayRes = mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .header("Idempotency-Key", "pay-wave-flow-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", invoiceId,
                                "amountXof", 30_000L,
                                "paymentMethod", "MOBILE_MONEY_WAVE",
                                "externalReference", "WAVE-SN-2026-9901"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receipt.receiptNumber").value(org.hamcrest.Matchers.endsWith("-000002")))
                .andReturn();
        JsonNode wavePayJson = readJson(wavePayRes);
        UUID wavePaymentId = UUID.fromString(wavePayJson.get("id").asString());
        String waveQrToken = wavePayJson.get("receipt").get("verificationToken").asString();
        assertThat(waveQrToken).doesNotContain("Diallo").doesNotContain(student1Matricule);

        // Facture soldée (PAID, balance = 0)
        mockMvc.perform(get("/api/v1/finance/invoices/" + invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("PAID"))
                .andExpect(jsonPath("$.invoice.paidAmountXof").value(80_000))
                .andExpect(jsonPath("$.invoice.balanceXof").value(0));

        // Vérification du reçu par jeton QR opaque avant contre-passation
        mockMvc.perform(get("/api/v1/finance/receipts/verify/" + waveQrToken)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentMatricule").value(student1Matricule))
                .andExpect(jsonPath("$.amountXof").value(30_000))
                .andExpect(jsonPath("$.reversed").value(false));

        // 7. Contre-passation tracée du paiement Wave : demande par CASHIER -> approbation par PROMOTER
        MvcResult revReqRes = mockMvc.perform(post("/api/v1/finance/payment-reversals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "paymentId", wavePaymentId,
                                "reason", "Double transfert Wave remboursé"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();
        UUID reversalRequestId = UUID.fromString(readJson(revReqRes).get("id").asString());

        mockMvc.perform(post("/api/v1/finance/payment-reversals/" + reversalRequestId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decisionNote", "Contre-passation validée après contrôle relevé"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("APPROVED"))
                .andExpect(jsonPath("$.reversal.originalPaymentId").value(wavePaymentId.toString()))
                .andExpect(jsonPath("$.reversal.amountXof").value(30_000));

        // Facture revient à PARTIALLY_PAID (balance = 30 000) et le reçu QR affiche reversed=true sans modification de receipts
        mockMvc.perform(get("/api/v1/finance/invoices/" + invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.invoice.paidAmountXof").value(50_000))
                .andExpect(jsonPath("$.invoice.balanceXof").value(30_000));

        mockMvc.perform(get("/api/v1/finance/receipts/verify/" + waveQrToken)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reversed").value(true))
                .andExpect(jsonPath("$.reversedAt").isNotEmpty());

        // Double contre-passation interdite -> 409
        mockMvc.perform(post("/api/v1/finance/payment-reversals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentId", wavePaymentId, "reason", "Doublon"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:payment-already-reversed"));

        // 8. Annulation d'une facture émise non payée pour élève 2 -> écriture CHARGE_REVERSAL
        MvcResult inv2Res = mockMvc.perform(post("/api/v1/finance/invoices")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", student2Id,
                                "academicYearId", yearId,
                                "title", "Facture élève 2",
                                "dueDate", "2026-10-20",
                                "lines", List.of(Map.of("feeItemId", inscriptionFeeId, "quantity", 1))))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID invoice2Id = UUID.fromString(readJson(inv2Res).get("invoice").get("id").asString());
        mockMvc.perform(post("/api/v1/finance/invoices/" + invoice2Id + "/issue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/finance/invoices/" + invoice2Id + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Erreur de facturation doublon"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("CANCELLED"));

        // 9. Clôture journalière de caisse (attendu espèces = 50 000, compté = 49 500 -> écart -500)
        LocalDate todayDakar = LocalDate.now(ZoneId.of("Africa/Dakar"));
        mockMvc.perform(post("/api/v1/finance/cash-closings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "closingDate", todayDakar.toString(),
                                "countedCashXof", 49_500L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:cash-closing-discrepancy-note-required"));

        MvcResult closingRes = mockMvc.perform(post("/api/v1/finance/cash-closings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "closingDate", todayDakar.toString(),
                                "countedCashXof", 49_500L,
                                "notes", "Pièce de 500 FCFA manquante"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expectedCashXof").value(50_000))
                .andExpect(jsonPath("$.discrepancyXof").value(-500))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn();
        UUID closingId = UUID.fromString(readJson(closingRes).get("id").asString());

        mockMvc.perform(post("/api/v1/finance/cash-closings/" + closingId + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("validationNote", "Écart acté"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"));

        // 10. Vérification du registre comptable, synthèses REST et frontière publique FinanceAccountPort
        mockMvc.perform(get("/api/v1/finance/ledger")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken))
                        .param("studentId", student1Id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5)); // CHARGE, SCHOLARSHIP_CREDIT, 2x PAYMENT_CREDIT, PAYMENT_REVERSAL

        mockMvc.perform(get("/api/v1/finance/students/" + student1Id + "/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken))
                        .param("academicYearId", yearId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGrossXof").value(100_000))
                .andExpect(jsonPath("$.totalDiscountXof").value(20_000))
                .andExpect(jsonPath("$.totalNetXof").value(80_000))
                .andExpect(jsonPath("$.totalPaidXof").value(50_000))
                .andExpect(jsonPath("$.totalBalanceXof").value(30_000))
                .andExpect(jsonPath("$.ledgerBalanceXof").value(30_000));

        mockMvc.perform(get("/api/v1/finance/families/" + familyId + "/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken))
                        .param("academicYearId", yearId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentCount").value(2))
                .andExpect(jsonPath("$.totalBalanceXof").value(30_000))
                .andExpect(jsonPath("$.ledgerBalanceXof").value(30_000));

        TenantContext.runWithTenant(TenantId.of(school.getId()), () -> {
            StudentFinancialSummary s1 = financeAccountPort.getStudentFinancialSummary(student1Id, yearId);
            assertThat(s1.totalBalanceXof()).isEqualTo(30_000L);
            assertThat(s1.ledgerBalanceXof()).isEqualTo(30_000L);

            FamilyFinancialSummary fam = financeAccountPort.getFamilyFinancialSummary(familyId, yearId);
            assertThat(fam.totalBalanceXof()).isEqualTo(30_000L);
            assertThat(fam.ledgerBalanceXof()).isEqualTo(30_000L);

            assertThat(financeAccountPort.findStudentLedgerBalanceXof(student2Id)).contains(0L);
        });
    }

    @Test
    void shouldEnforceDatabaseImmutabilityOnAppendOnlyFinanceTables() throws Exception {
        School school = createSchool("FINIMM", SchoolType.PRIVATE_SCHOOL);
        createActiveSubscription(school, 50);

        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);
        String cashierToken = tenantToken(school.getId(), RoleCode.CASHIER);
        String promoterToken = tenantToken(school.getId(), RoleCode.PROMOTER);

        Fixture fixture = createIssuedInvoiceFixture(school, directorToken, 40_000L);

        // Encaissement + contre-passation afin d'avoir au moins 1 ligne dans chacune des 5 tables immuables
        MvcResult payRes = mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .header("Idempotency-Key", "imm-test-pay-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", fixture.invoiceId(),
                                "amountXof", 40_000L,
                                "paymentMethod", "CASH"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID paymentId = UUID.fromString(readJson(payRes).get("id").asString());

        MvcResult revReqRes = mockMvc.perform(post("/api/v1/finance/payment-reversals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentId", paymentId, "reason", "Test immutabilité"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID revReqId = UUID.fromString(readJson(revReqRes).get("id").asString());
        mockMvc.perform(post("/api/v1/finance/payment-reversals/" + revReqId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken)))
                .andExpect(status().isOk());

        List<String> immutableTables = List.of(
                "payments", "payment_allocations", "receipts", "payment_reversals", "ledger_entries");

        // 1. Sous ecole_app (privilèges + triggers) : UPDATE et DELETE systématiquement rejetés
        for (String table : immutableTables) {
            assertThatThrownBy(() -> runAsEcoleApp(school.getId(), () ->
                    jdbcTemplate.update("UPDATE " + table + " SET updated_at = now()")))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> runAsEcoleApp(school.getId(), () ->
                    jdbcTemplate.update("DELETE FROM " + table)))
                    .isInstanceOf(DataAccessException.class);
        }

        // 2. Même en tant que propriétaire de table (trigger PostgreSQL BEFORE UPDATE OR DELETE)
        for (String table : immutableTables) {
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.execute(
                        "SELECT set_config('app.current_tenant_id', '" + school.getId() + "', true)");
                jdbcTemplate.update("UPDATE " + table + " SET updated_at = now()");
            })).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.execute(
                        "SELECT set_config('app.current_tenant_id', '" + school.getId() + "', true)");
                jdbcTemplate.update("DELETE FROM " + table);
            })).isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    void shouldEnforceRoleBasedAuthorizationAndSeparationOfDuties() throws Exception {
        School school = createSchool("FINRBAC", SchoolType.TUTORING_CENTER);
        createActiveSubscription(school, 50);

        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);
        String promoterToken = tenantToken(school.getId(), RoleCode.PROMOTER);
        String centerManagerToken = tenantToken(school.getId(), RoleCode.CENTER_MANAGER);
        String cashierToken = tenantToken(school.getId(), RoleCode.CASHIER);
        String secretaryToken = tenantToken(school.getId(), RoleCode.SECRETARY);

        Fixture fixture = createIssuedInvoiceFixture(school, directorToken, 60_000L);

        // Séparation des tâches stricte : DIRECTOR, PROMOTER et SECRETARY ne peuvent PAS encaisser (403)
        for (String forbiddenPayer : List.of(directorToken, promoterToken, secretaryToken)) {
            mockMvc.perform(post("/api/v1/finance/payments")
                            .header(HttpHeaders.AUTHORIZATION, bearer(forbiddenPayer))
                            .header("Idempotency-Key", "rbac-forbid-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "invoiceId", fixture.invoiceId(),
                                    "amountXof", 10_000L,
                                    "paymentMethod", "CASH"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:ecole:problem:access-denied"));
        }

        // CENTER_MANAGER est autorisé à encaisser (201)
        MvcResult cmPayRes = mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(centerManagerToken))
                        .header("Idempotency-Key", "rbac-cm-pay-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", fixture.invoiceId(),
                                "amountXof", 10_000L,
                                "paymentMethod", "CASH"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID paymentId = UUID.fromString(readJson(cmPayRes).get("id").asString());

        // CASHIER et SECRETARY ne peuvent pas créer de frais au catalogue (403)
        for (String token : List.of(cashierToken, secretaryToken, promoterToken)) {
            mockMvc.perform(post("/api/v1/finance/fee-items")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "academicYearId", fixture.yearId(),
                                    "code", "FORB-01",
                                    "label", "Interdit",
                                    "category", "OTHER",
                                    "frequency", "ONE_TIME",
                                    "amountXof", 5_000L,
                                    "mandatory", false))))
                    .andExpect(status().isForbidden());
        }

        // CASHIER et SECRETARY peuvent demander remise/contre-passation mais PAS approuver (403)
        MvcResult adjRes = mockMvc.perform(post("/api/v1/finance/invoices/" + fixture.invoiceId() + "/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "adjustmentType", "DISCOUNT",
                                "calculationMode", "FIXED_AMOUNT",
                                "fixedAmountXof", 5_000L,
                                "reason", "Demande caissier"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID adjId = UUID.fromString(readJson(adjRes).get("id").asString());

        MvcResult revRes = mockMvc.perform(post("/api/v1/finance/payment-reversals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentId", paymentId, "reason", "Demande secrétariat"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID revId = UUID.fromString(readJson(revRes).get("id").asString());

        for (String nonApprover : List.of(cashierToken, secretaryToken)) {
            mockMvc.perform(post("/api/v1/finance/adjustments/" + adjId + "/approve")
                            .header(HttpHeaders.AUTHORIZATION, bearer(nonApprover)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/finance/payment-reversals/" + revId + "/approve")
                            .header(HttpHeaders.AUTHORIZATION, bearer(nonApprover)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/finance/invoices/" + fixture.invoiceId() + "/cancel")
                            .header(HttpHeaders.AUTHORIZATION, bearer(nonApprover))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("reason", "Tentative annulation"))))
                    .andExpect(status().isForbidden());
        }

        // SECRETARY n'a pas accès aux clôtures de caisse ; DIRECTOR ne peut pas soumettre une clôture
        mockMvc.perform(get("/api/v1/finance/cash-closings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/finance/cash-closings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "closingDate", LocalDate.now(ZoneId.of("Africa/Dakar")).toString(),
                                "countedCashXof", 10_000L))))
                .andExpect(status().isForbidden());

        // Rôles hors finance (ADMIN_SAAS, CENSOR, TEACHER, TUTOR, PARENT, STUDENT) -> 403
        List<String> excludedTokens = List.of(
                platformAdminToken(),
                tenantToken(school.getId(), RoleCode.CENSOR),
                tenantToken(school.getId(), RoleCode.TEACHER),
                tenantToken(school.getId(), RoleCode.TUTOR),
                tenantToken(school.getId(), RoleCode.PARENT),
                tenantToken(school.getId(), RoleCode.STUDENT));
        for (String token : excludedTokens) {
            mockMvc.perform(get("/api/v1/finance/fee-items")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/finance/invoices")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/finance/ledger")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void shouldEnforceSubscriptionAccessStatesOnFinanceEndpoints() throws Exception {
        // 1. Abonnement EXPIRED -> lecture 200, écriture 403
        School expiredSchool = createSchool("FINEXP", SchoolType.PRIVATE_SCHOOL);
        createSubscriptionWithStatus(
                expiredSchool,
                SubscriptionStatus.ACTIVE,
                Instant.now().minus(40, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS),
                50);
        String expiredDirector = tenantToken(expiredSchool.getId(), RoleCode.DIRECTOR);

        mockMvc.perform(get("/api/v1/finance/fee-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredDirector)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/finance/ledger")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredDirector)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/finance/fee-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredDirector))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "academicYearId", UUID.randomUUID(),
                                "code", "EXP-01",
                                "label", "Bloqué",
                                "category", "TUITION",
                                "frequency", "ANNUAL",
                                "amountXof", 10_000L,
                                "mandatory", true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-access-denied"));

        // 2. Abonnement SUSPENDED -> lecture et écriture 403
        School suspendedSchool = createSchool("FINSUSP", SchoolType.PRIVATE_SCHOOL);
        createSubscriptionWithStatus(
                suspendedSchool,
                SubscriptionStatus.SUSPENDED,
                Instant.now().minus(10, ChronoUnit.DAYS),
                Instant.now().plus(20, ChronoUnit.DAYS),
                50);
        String suspendedDirector = tenantToken(suspendedSchool.getId(), RoleCode.DIRECTOR);
        mockMvc.perform(get("/api/v1/finance/invoices")
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspendedDirector)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-access-denied"));

        // 3. Sans abonnement -> lecture 403
        School noSubSchool = createSchool("FINNOSUB", SchoolType.PRIVATE_SCHOOL);
        String noSubDirector = tenantToken(noSubSchool.getId(), RoleCode.DIRECTOR);
        mockMvc.perform(get("/api/v1/finance/fee-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(noSubDirector)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:subscription-access-denied"));
    }

    @Test
    void shouldIsolateTenantsAcrossHttpAndPostgresqlRlsOnAllV7Tables() throws Exception {
        School schoolA = createSchool("FINA", SchoolType.PRIVATE_SCHOOL);
        School schoolB = createSchool("FINB", SchoolType.PRIVATE_SCHOOL);
        createActiveSubscription(schoolA, 50);
        createActiveSubscription(schoolB, 50);

        String dirA = tenantToken(schoolA.getId(), RoleCode.DIRECTOR);
        String cashA = tenantToken(schoolA.getId(), RoleCode.CASHIER);
        String promA = tenantToken(schoolA.getId(), RoleCode.PROMOTER);

        String dirB = tenantToken(schoolB.getId(), RoleCode.DIRECTOR);
        String cashB = tenantToken(schoolB.getId(), RoleCode.CASHIER);
        String promB = tenantToken(schoolB.getId(), RoleCode.PROMOTER);

        Fixture fixA = createIssuedInvoiceFixture(schoolA, dirA, 50_000L);
        Fixture fixB = createIssuedInvoiceFixture(schoolB, dirB, 70_000L);

        // Même clé d'idempotence dans A et B autorisée car isolée par tenant_id !
        MvcResult payARes = mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashA))
                        .header("Idempotency-Key", "shared-idem-key-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", fixA.invoiceId(),
                                "amountXof", 20_000L,
                                "paymentMethod", "CASH"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receipt.receiptNumber").value(org.hamcrest.Matchers.endsWith("-000001")))
                .andReturn();
        JsonNode payAJson = readJson(payARes);
        UUID paymentAId = UUID.fromString(payAJson.get("id").asString());

        MvcResult payBRes = mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashB))
                        .header("Idempotency-Key", "shared-idem-key-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", fixB.invoiceId(),
                                "amountXof", 30_000L,
                                "paymentMethod", "CASH"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receipt.receiptNumber").value(org.hamcrest.Matchers.endsWith("-000001")))
                .andReturn();
        JsonNode payBJson = readJson(payBRes);
        UUID paymentBId = UUID.fromString(payBJson.get("id").asString());
        String qrTokenB = payBJson.get("receipt").get("verificationToken").asString();

        // Peupler aussi fee_adjustments, payment_reversal_requests, payment_reversals et cash_closings dans A et B
        UUID adjBId = requestAndApproveAdjustment(cashB, promB, fixB.invoiceId(), 5_000L);
        requestAndApproveAdjustment(cashA, promA, fixA.invoiceId(), 5_000L);
        UUID revReqBId = requestAndApproveReversal(cashB, promB, paymentBId);
        requestAndApproveReversal(cashA, promA, paymentAId);
        UUID closingBId = submitCashClosing(cashB, 0L);
        submitCashClosing(cashA, 0L);

        // Isolation HTTP : A tente d'accéder aux ressources de B, même avec X-Tenant-Id et ?tenantId forgés -> 404
        mockMvc.perform(get("/api/v1/finance/fee-items/" + fixB.feeItemId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirA))
                        .header("X-Tenant-Id", schoolB.getId().toString())
                        .param("tenantId", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/finance/invoices/" + fixB.invoiceId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/finance/adjustments/" + adjBId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/finance/payments/" + paymentBId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/finance/receipts/verify/" + qrTokenB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/finance/payment-reversals/" + revReqBId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/finance/cash-closings/" + closingBId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(dirA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isNotFound());

        // Tentative de paiement par A sur une facture de B -> 404
        mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashA))
                        .header("X-Tenant-Id", schoolB.getId().toString())
                        .header("Idempotency-Key", "cross-tenant-pay-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", fixB.invoiceId(),
                                "amountXof", 10_000L,
                                "paymentMethod", "CASH"))))
                .andExpect(status().isNotFound());

        // Vérification PostgreSQL RLS sous ecole_app (NOBYPASSRLS) sur les 13 tables de V7
        List<String> v7Tables = List.of(
                "finance_sequences",
                "fee_items",
                "student_invoices",
                "student_invoice_lines",
                "payment_schedules",
                "fee_adjustments",
                "payments",
                "payment_allocations",
                "receipts",
                "payment_reversal_requests",
                "payment_reversals",
                "ledger_entries",
                "cash_closings");

        for (String table : v7Tables) {
            // 1. Fail-closed sans TenantContext -> 0 ligne
            Integer countWithoutTenant = runAsEcoleApp(null, () ->
                    jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
            assertThat(countWithoutTenant)
                    .as("La table %s doit être fail-closed (0 ligne) sans TenantContext", table)
                    .isZero();

            // 2. Sous tenant A -> uniquement des lignes appartenant à A (et >= 1)
            Integer countA = runAsEcoleApp(schoolA.getId(), () ->
                    jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
            Integer crossCountInA = runAsEcoleApp(schoolA.getId(), () ->
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM " + table + " WHERE tenant_id <> ?",
                            Integer.class,
                            schoolA.getId()));
            assertThat(countA).as("La table %s doit contenir des données pour A", table).isPositive();
            assertThat(crossCountInA).as("Aucune fuite cross-tenant sur %s", table).isZero();
        }

        // 3. INSERT cross-tenant bloqué par RLS WITH CHECK sous ecole_app
        assertThatThrownBy(() -> runAsEcoleApp(schoolA.getId(), () ->
                jdbcTemplate.update(
                        "INSERT INTO finance_sequences (tenant_id, sequence_type, sequence_year, last_value) VALUES (?, 'LEDGER', 2099, 1)",
                        schoolB.getId())))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void shouldEnforceConcurrentPaymentIntegrityMakerCheckerValidationAndRequestId() throws Exception {
        School school = createSchool("FIN-HARD", SchoolType.PRIVATE_SCHOOL);
        createActiveSubscription(school, 50);
        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);
        String cashierToken = tenantToken(school.getId(), RoleCode.CASHIER);
        String multiRoleToken = tokenIssuer
                .issueAccessToken(
                        AccountRef.user(UUID.randomUUID(), school.getId()),
                        Set.of(RoleCode.CENTER_MANAGER.name(), RoleCode.DIRECTOR.name()))
                .value();

        Fixture fix = createIssuedInvoiceFixture(school, directorToken, 100_000L);

        // m1 + m3 : omettre amountXof sur POST /api/v1/finance/fee-items -> 400 Bad Request avec requestId MDC
        mockMvc.perform(post("/api/v1/finance/fee-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "academicYearId", fix.yearId(),
                                "code", "MISSING-AMT",
                                "label", "Frais sans montant",
                                "category", "TUITION",
                                "frequency", "ANNUAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        // C2 (a) : deux encaissements concurrents de 70 000 FCFA sur solde 100 000 FCFA -> 1 seul passe (201), l'autre échoue (409)
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch readyDistinct = new CountDownLatch(2);
            CountDownLatch startDistinct = new CountDownLatch(1);
            Future<Integer> p1 = pool.submit(() -> recordPaymentConcurrently(
                    cashierToken, fix.invoiceId(), 70_000L, "conc-distinct-key-01", readyDistinct, startDistinct));
            Future<Integer> p2 = pool.submit(() -> recordPaymentConcurrently(
                    cashierToken, fix.invoiceId(), 70_000L, "conc-distinct-key-02", readyDistinct, startDistinct));
            assertThat(readyDistinct.await(5, TimeUnit.SECONDS)).isTrue();
            startDistinct.countDown();

            List<Integer> distinctStatuses = List.of(p1.get(15, TimeUnit.SECONDS), p2.get(15, TimeUnit.SECONDS));
            assertThat(distinctStatuses).containsExactlyInAnyOrder(201, 409);

            // C2 (b) : deux encaissements concurrents de 20 000 FCFA avec la MÊME Idempotency-Key -> {201, 200}, aucun 500, 1 seul paiement
            CountDownLatch readySame = new CountDownLatch(2);
            CountDownLatch startSame = new CountDownLatch(1);
            Future<Integer> s1 = pool.submit(() -> recordPaymentConcurrently(
                    cashierToken, fix.invoiceId(), 20_000L, "conc-same-idem-key-01", readySame, startSame));
            Future<Integer> s2 = pool.submit(() -> recordPaymentConcurrently(
                    cashierToken, fix.invoiceId(), 20_000L, "conc-same-idem-key-01", readySame, startSame));
            assertThat(readySame.await(5, TimeUnit.SECONDS)).isTrue();
            startSame.countDown();

            List<Integer> sameStatuses = List.of(s1.get(15, TimeUnit.SECONDS), s2.get(15, TimeUnit.SECONDS));
            assertThat(sameStatuses).containsExactlyInAnyOrder(201, 200);
        } finally {
            pool.shutdownNow();
        }

        // Vérifier l'invariant facture / registre : 70 000 + 20 000 = 90 000 payés, solde = 10 000, exactement 2 paiements
        mockMvc.perform(get("/api/v1/finance/invoices/" + fix.invoiceId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.paidAmountXof").value(90_000))
                .andExpect(jsonPath("$.invoice.balanceXof").value(10_000));

        MvcResult paymentsList = mockMvc.perform(get("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .param("invoiceId", fix.invoiceId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        UUID firstPaymentId = UUID.fromString(readJson(paymentsList).get("content").get(0).get("id").asString());

        TenantContext.runWithTenant(TenantId.of(school.getId()), () -> {
            StudentFinancialSummary summary =
                    financeAccountPort.getStudentFinancialSummary(fix.studentId(), fix.yearId());
            assertThat(summary.totalPaidXof()).isEqualTo(90_000L);
            assertThat(summary.totalBalanceXof()).isEqualTo(10_000L);
            assertThat(summary.ledgerBalanceXof()).isEqualTo(10_000L);
        });

        // M5 : interdiction d'auto-approbation (Maker-Checker) sur remise, contre-passation et clôture de caisse
        MvcResult adjRes = mockMvc.perform(post("/api/v1/finance/invoices/" + fix.invoiceId() + "/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(multiRoleToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "adjustmentType", "DISCOUNT",
                                "calculationMode", "FIXED_AMOUNT",
                                "fixedAmountXof", 5_000L,
                                "reason", "Remise demandée par acteur multi-rôle"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID selfAdjId = UUID.fromString(readJson(adjRes).get("id").asString());

        mockMvc.perform(post("/api/v1/finance/adjustments/" + selfAdjId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(multiRoleToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:finance-self-approval-forbidden"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        MvcResult revRes = mockMvc.perform(post("/api/v1/finance/payment-reversals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(multiRoleToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "paymentId", firstPaymentId,
                                "reason", "Contre-passation demandée par acteur multi-rôle"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID selfRevId = UUID.fromString(readJson(revRes).get("id").asString());

        mockMvc.perform(post("/api/v1/finance/payment-reversals/" + selfRevId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(multiRoleToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:finance-self-approval-forbidden"));

        MvcResult closingRes = mockMvc.perform(post("/api/v1/finance/cash-closings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(multiRoleToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "closingDate", LocalDate.now(ZoneId.of("Africa/Dakar")).toString(),
                                "countedCashXof", 90_000L))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID selfClosingId = UUID.fromString(readJson(closingRes).get("id").asString());

        mockMvc.perform(post("/api/v1/finance/cash-closings/" + selfClosingId + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(multiRoleToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:finance-self-approval-forbidden"));
    }

    @Test
    void shouldAggregateCashClosingViaSqlForEmptyAndMixedPaymentDaysWithTenantIsolation() throws Exception {
        School schoolA = createSchool("FINM8A", SchoolType.PRIVATE_SCHOOL);
        School schoolB = createSchool("FINM8B", SchoolType.PRIVATE_SCHOOL);
        createActiveSubscription(schoolA, 100);
        createActiveSubscription(schoolB, 100);

        String dirTokenA = tenantToken(schoolA.getId(), RoleCode.DIRECTOR);
        String cashierTokenA = tenantToken(schoolA.getId(), RoleCode.CASHIER);
        String dirTokenB = tenantToken(schoolB.getId(), RoleCode.DIRECTOR);
        String cashierTokenB = tenantToken(schoolB.getId(), RoleCode.CASHIER);

        Fixture fixA = createIssuedInvoiceFixture(schoolA, dirTokenA, 200_000L);
        Fixture fixB = createIssuedInvoiceFixture(schoolB, dirTokenB, 100_000L);

        // 1. Clôture sur une journée sans encaissement espèces -> 0 FCFA, 0 paiement, 0 contre-passation
        LocalDate yesterday = LocalDate.now(ZoneId.of("Africa/Dakar")).minusDays(1);
        mockMvc.perform(post("/api/v1/finance/cash-closings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierTokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "closingDate", yesterday.toString(),
                                "countedCashXof", 0L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cashInXof").value(0))
                .andExpect(jsonPath("$.cashOutReversalsXof").value(0))
                .andExpect(jsonPath("$.expectedCashXof").value(0))
                .andExpect(jsonPath("$.paymentCount").value(0))
                .andExpect(jsonPath("$.reversalCount").value(0))
                .andExpect(jsonPath("$.discrepancyXof").value(0));

        // 2. Journée courante : 2 paiements CASH (60 000 + 40 000), 1 paiement WAVE (30 000 exclu), 1 contre-passation CASH (40 000)
        // + 1 paiement CASH sur l'école B (50 000 isolé par RLS)
        mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierTokenA))
                        .header("Idempotency-Key", "fin-m8-cash-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", fixA.invoiceId(),
                                "amountXof", 60_000L,
                                "paymentMethod", "CASH"))))
                .andExpect(status().isCreated());

        MvcResult cash2Res = mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierTokenA))
                        .header("Idempotency-Key", "fin-m8-cash-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", fixA.invoiceId(),
                                "amountXof", 40_000L,
                                "paymentMethod", "CASH"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID cash2Id = UUID.fromString(readJson(cash2Res).get("id").asString());

        mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierTokenA))
                        .header("Idempotency-Key", "fin-m8-bank-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", fixA.invoiceId(),
                                "amountXof", 30_000L,
                                "paymentMethod", "BANK_TRANSFER",
                                "externalReference", "VIR-M8-001"))))
                .andExpect(status().isCreated());

        requestAndApproveReversal(cashierTokenA, dirTokenA, cash2Id);

        mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierTokenB))
                        .header("Idempotency-Key", "fin-m8-cash-b1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", fixB.invoiceId(),
                                "amountXof", 50_000L,
                                "paymentMethod", "CASH"))))
                .andExpect(status().isCreated());

        LocalDate today = LocalDate.now(ZoneId.of("Africa/Dakar"));
        mockMvc.perform(post("/api/v1/finance/cash-closings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierTokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "closingDate", today.toString(),
                                "countedCashXof", 58_000L,
                                "notes", "Écart de 2 000 FCFA en cours de vérification"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cashInXof").value(100_000))
                .andExpect(jsonPath("$.cashOutReversalsXof").value(40_000))
                .andExpect(jsonPath("$.expectedCashXof").value(60_000))
                .andExpect(jsonPath("$.paymentCount").value(2))
                .andExpect(jsonPath("$.reversalCount").value(1))
                .andExpect(jsonPath("$.discrepancyXof").value(-2_000));
    }

    @Test
    void shouldResetBalanceAndRejectPendingAdjustmentsWhenCancellingInvoice() throws Exception {
        School school = createSchool("FIN-CANCEL", SchoolType.PRIVATE_SCHOOL);
        createActiveSubscription(school, 50);
        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);
        String cashierToken = tenantToken(school.getId(), RoleCode.CASHIER);

        Fixture fixture = createIssuedInvoiceFixture(school, directorToken, 80_000L);

        // Demander une remise en attente d'approbation sur la facture émise
        MvcResult adjRes = mockMvc.perform(post("/api/v1/finance/invoices/" + fixture.invoiceId() + "/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "adjustmentType", "DISCOUNT",
                                "calculationMode", "FIXED_AMOUNT",
                                "fixedAmountXof", 15_000L,
                                "reason", "Demande de remise avant annulation"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();
        UUID adjustmentId = UUID.fromString(readJson(adjRes).get("id").asString());

        // Annuler la facture -> balanceXof passe à 0 et la demande PENDING_APPROVAL est automatiquement REJECTED
        mockMvc.perform(post("/api/v1/finance/invoices/" + fixture.invoiceId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Désinscription confirmée avant paiement"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("CANCELLED"))
                .andExpect(jsonPath("$.invoice.balanceXof").value(0));

        mockMvc.perform(get("/api/v1/finance/adjustments/" + adjustmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionNote").value(org.hamcrest.Matchers.containsString("Rejet automatique")));
    }

    private int recordPaymentConcurrently(
            String token,
            UUID invoiceId,
            long amountXof,
            String idempotencyKey,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/v1/finance/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceId", invoiceId,
                                "amountXof", amountXof,
                                "paymentMethod", "CASH"))))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private record Fixture(UUID yearId, UUID gradeLevelId, UUID studentId, UUID feeItemId, UUID invoiceId) {
    }

    private Fixture createIssuedInvoiceFixture(School school, String directorToken, long amountXof) throws Exception {
        UUID yearId = createAcademicYearViaApi(
                directorToken, "2026-2027", "Année 2026-2027", "2026-10-01", "2027-07-31");
        activateAcademicYear(directorToken, yearId);
        UUID gradeId = createGradeLevelViaApi(directorToken, "CM2", "CM2", "PRIMARY", 1);
        JsonNode student = createStudentViaApi(
                directorToken, "Moussa", "Ndiaye", "MALE", "2015-03-10", null, "2026-09-10");
        UUID studentId = UUID.fromString(student.get("id").asString());
        UUID feeItemId = createFeeItemViaApi(
                directorToken, yearId, gradeId, "SCOL-2026", "Scolarité annuelle", "TUITION", "ANNUAL", amountXof);

        MvcResult invRes = mockMvc.perform(post("/api/v1/finance/invoices")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "studentId", studentId,
                                "academicYearId", yearId,
                                "title", "Scolarité " + school.getCode(),
                                "dueDate", "2026-11-05",
                                "lines", List.of(Map.of("feeItemId", feeItemId, "quantity", 1))))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID invoiceId = UUID.fromString(readJson(invRes).get("invoice").get("id").asString());

        mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/issue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk());

        return new Fixture(yearId, gradeId, studentId, feeItemId, invoiceId);
    }

    private UUID requestAndApproveAdjustment(String requesterToken, String approverToken, UUID invoiceId, long amountXof)
            throws Exception {
        MvcResult reqRes = mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(requesterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "adjustmentType", "DISCOUNT",
                                "calculationMode", "FIXED_AMOUNT",
                                "fixedAmountXof", amountXof,
                                "reason", "Remise test"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID adjId = UUID.fromString(readJson(reqRes).get("id").asString());
        mockMvc.perform(post("/api/v1/finance/adjustments/" + adjId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(approverToken)))
                .andExpect(status().isOk());
        return adjId;
    }

    private UUID requestAndApproveReversal(String requesterToken, String approverToken, UUID paymentId) throws Exception {
        MvcResult reqRes = mockMvc.perform(post("/api/v1/finance/payment-reversals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(requesterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentId", paymentId, "reason", "Annulation test"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID reqId = UUID.fromString(readJson(reqRes).get("id").asString());
        mockMvc.perform(post("/api/v1/finance/payment-reversals/" + reqId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(approverToken)))
                .andExpect(status().isOk());
        return reqId;
    }

    private UUID submitCashClosing(String cashierToken, long countedCashXof) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/finance/cash-closings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cashierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "closingDate", LocalDate.now(ZoneId.of("Africa/Dakar")).toString(),
                                "countedCashXof", countedCashXof,
                                "notes", "Clôture test"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private <T> T runAsEcoleApp(UUID tenantId, java.util.function.Supplier<T> action) {
        return transactionTemplate.execute(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            String tenantParam = tenantId != null ? tenantId.toString() : "";
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + tenantParam + "', true)");
            return action.get();
        });
    }

    private UUID createFeeItemViaApi(
            String token,
            UUID yearId,
            UUID gradeLevelId,
            String code,
            String label,
            String category,
            String frequency,
            long amountXof) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("academicYearId", yearId);
        if (gradeLevelId != null) {
            body.put("gradeLevelId", gradeLevelId);
        }
        body.put("code", code);
        body.put("label", label);
        body.put("category", category);
        body.put("frequency", frequency);
        body.put("amountXof", amountXof);
        body.put("mandatory", true);
        MvcResult res = mockMvc.perform(post("/api/v1/finance/fee-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
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
        TestSecuritySupport.runAsAdminSaas(() -> {
            SubscriptionPlan plan = subscriptionService.createPlan(
                    uniqueCode("PLAN"), "Forfait Finance", 25_000L, 250_000L, maxStudents, 20, 2_048);
            TenantContext.runWithTenant(TenantId.of(school.getId()), () ->
                    subscriptionService.createSubscription(plan.getId(), status, BillingCycle.MONTHLY, startsAt, endsAt));
        });
    }

    private void activateAcademicYear(String token, UUID yearId) throws Exception {
        mockMvc.perform(post("/api/v1/academic/years/" + yearId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("makeCurrent", true))))
                .andExpect(status().isOk());
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

    private UUID createFamilyViaApi(String token, String code, String name, String phone, String city)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/families")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", code,
                                "name", name,
                                "primaryPhone", phone,
                                "city", city))))
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

    private void linkGuardianViaApi(
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
                                "financialResponsible", financialResponsible))))
                .andExpect(status().isCreated());
    }

    private School createSchool(String prefix, SchoolType type) {
        String code = uniqueCode(prefix);
        return TestSecuritySupport.runAsAdminSaas(() -> schoolService.createSchool(
                code, "Établissement " + code, type, "+221770000000", "contact@" + code.toLowerCase() + ".sn", "Dakar"));
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
}
