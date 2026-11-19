package com.ecole.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecole.saas.identity.application.UserAccountService;
import com.ecole.saas.identity.domain.Base32;
import com.ecole.saas.identity.domain.PasswordHasher;
import com.ecole.saas.identity.domain.PlatformAdmin;
import com.ecole.saas.identity.domain.RoleCode;
import com.ecole.saas.identity.domain.TotpGenerator;
import com.ecole.saas.identity.domain.User;
import com.ecole.saas.identity.infrastructure.PlatformAdminRepository;
import com.ecole.saas.identity.infrastructure.UserRepository;
import com.ecole.saas.school.application.SchoolService;
import com.ecole.saas.school.domain.School;
import com.ecole.saas.school.domain.SchoolType;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
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
class AuthIntegrationTest {

    private static final String REFRESH_COOKIE_NAME = "ecole_refresh_token";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String STRONG_PASSWORD = "MotDePasseTest2026!";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final SchoolService schoolService;
    private final UserAccountService userAccountService;
    private final UserRepository userRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordHasher passwordHasher;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AuthIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            SchoolService schoolService,
            UserAccountService userAccountService,
            UserRepository userRepository,
            PlatformAdminRepository platformAdminRepository,
            PasswordHasher passwordHasher,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.schoolService = schoolService;
        this.userAccountService = userAccountService;
        this.userRepository = userRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.passwordHasher = passwordHasher;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldAuthenticateStandardTenantUserAndIgnoreClientTenantSpoofing() throws Exception {
        School schoolA = createSchool("AUTH-A");
        School schoolB = createSchool("AUTH-B");
        User secretary = createUser(schoolA, "secretariat@ecole-a.sn", "Fatou", "Sall", RoleCode.SECRETARY);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "schoolCode", schoolA.getCode().toLowerCase(),
                                "email", "SECRETARIAT@ecole-a.sn",
                                "password", STRONG_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.mfaRequired").value(false))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value(secretary.getId().toString()))
                .andExpect(jsonPath("$.user.tenantId").value(schoolA.getId().toString()))
                .andExpect(jsonPath("$.user.schoolCode").value(schoolA.getCode()))
                .andExpect(jsonPath("$.user.roles[0]").value("SECRETARY"))
                .andReturn();

        String setCookie = findSetCookie(loginResult, REFRESH_COOKIE_NAME);
        assertThat(setCookie)
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");

        String accessToken = readJson(loginResult).get("accessToken").asString();

        // Tentative d'usurpation de tenant via en-tête et paramètre : ignorée côté serveur
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header("X-Tenant-Id", schoolB.getId().toString())
                        .queryParam("tenantId", schoolB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(secretary.getId().toString()))
                .andExpect(jsonPath("$.tenantId").value(schoolA.getId().toString()))
                .andExpect(jsonPath("$.schoolCode").value(schoolA.getCode()));

        // Accès sans jeton -> 401 RFC 9457 en français
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:authentication-required"))
                .andExpect(jsonPath("$.title").value("Authentification requise"));

        // Jeton invalide -> 401 RFC 9457
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer jeton.invalide.signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void shouldRequireMfaEnrollmentAndVerificationForDirectorAndRejectTotpReplay() throws Exception {
        School school = createSchool("MFA-DIR");
        User director = createUser(school, "directeur@mfa.sn", "Mamadou", "Ba", RoleCode.DIRECTOR);

        // 1. Login -> MFA obligatoire (enrôlement requis), aucun accessToken ni cookie refresh
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "schoolCode", school.getCode(),
                                "email", director.getEmail(),
                                "password", STRONG_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_ENROLLMENT_REQUIRED"))
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaEnrollmentRequired").value(true))
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.accessToken").isEmpty())
                .andExpect(jsonPath("$.mfaToken").isNotEmpty())
                .andReturn();

        assertThat(findOptionalSetCookie(loginResult, REFRESH_COOKIE_NAME)).isNull();
        String mfaToken = readJson(loginResult).get("mfaToken").asString();

        // 2. Un jeton mfa_pending ne peut JAMAIS accéder à /me (403 Forbidden)
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mfaToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:access-denied"));

        // 3. Enrôlement MFA -> secret chiffré en AES-GCM en base (jamais en clair)
        MvcResult enrollResult = mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mfaToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.otpauthUri").value(Matchers.startsWith("otpauth://totp/EcoleSaaS:")))
                .andExpect(jsonPath("$.digits").value(6))
                .andExpect(jsonPath("$.periodSeconds").value(30))
                .andReturn();

        String base32Secret = readJson(enrollResult).get("secret").asString();
        String storedCiphertext = TenantContext.executeWithTenant(
                TenantId.of(school.getId()),
                () -> userRepository.findById(director.getId()).orElseThrow().getSecurity().getMfaSecretEncrypted());
        assertThat(storedCiphertext).startsWith("v1:").doesNotContain(base32Secret);

        // 4. Code MFA invalide -> 401
        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mfaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "000000"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // 5. Code TOTP valide -> session complète émise + cookie HttpOnly
        byte[] secretBytes = Base32.decode(base32Secret);
        long currentStep = TotpGenerator.timeStep(Instant.now());
        String validCode = TotpGenerator.generate(secretBytes, currentStep);

        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mfaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", validCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.mfaEnabled").value(true))
                .andExpect(jsonPath("$.user.roles[0]").value("DIRECTOR"))
                .andReturn();

        assertThat(findSetCookie(verifyResult, REFRESH_COOKIE_NAME)).contains("HttpOnly");
        String accessToken = readJson(verifyResult).get("accessToken").asString();

        // 6. Ré-enrôlement refusé une fois le MFA actif (409 Conflict)
        mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // 7. Reconnexion : MFA_REQUIRED et anti-rejeu du même code TOTP
        MvcResult secondLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "schoolCode", school.getCode(),
                                "email", director.getEmail(),
                                "password", STRONG_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED"))
                .andExpect(jsonPath("$.mfaEnrollmentRequired").value(false))
                .andReturn();
        String secondMfaToken = readJson(secondLogin).get("mfaToken").asString();

        // Même code dans le même pas -> rejeté (anti-rejeu)
        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondMfaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", validCode))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // Pas suivant (tolérance +1) -> accepté
        String nextStepCode = TotpGenerator.generate(secretBytes, currentStep + 1);
        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondMfaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", nextStepCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));
    }

    @Test
    void shouldAuthenticatePlatformAdminWithMandatoryMfaRefreshAndLogout() throws Exception {
        String email = "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@ecole-saas.sn";
        PlatformAdmin admin = platformAdminRepository.save(new PlatformAdmin(
                email, "Aminata", "Diallo", passwordHasher.hash(STRONG_PASSWORD)));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/platform/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", STRONG_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_ENROLLMENT_REQUIRED"))
                .andExpect(jsonPath("$.mfaToken").isNotEmpty())
                .andReturn();

        String mfaToken = readJson(loginResult).get("mfaToken").asString();

        MvcResult enrollResult = mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mfaToken))
                .andExpect(status().isOk())
                .andReturn();

        byte[] secret = Base32.decode(readJson(enrollResult).get("secret").asString());
        String code = TotpGenerator.generate(secret, TotpGenerator.timeStep(Instant.now()));

        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mfaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.user.id").value(admin.getId().toString()))
                .andExpect(jsonPath("$.user.subjectType").value("PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.user.roles[0]").value("ADMIN_SAAS"))
                .andExpect(jsonPath("$.user.tenantId").isEmpty())
                .andReturn();

        String accessToken = readJson(verifyResult).get("accessToken").asString();
        String refreshCookieValue = extractCookieValue(findSetCookie(verifyResult, REFRESH_COOKIE_NAME));
        assertThat(refreshCookieValue).startsWith("p.");

        // /me en tant qu'admin plateforme ignore un X-Tenant-Id envoyé par le client
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header("X-Tenant-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectType").value("PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.tenantId").isEmpty());

        // Rotation refresh + logout avec CSRF
        CsrfBundle csrf = fetchCsrf();
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, refreshCookieValue), csrf.cookie())
                        .header(CSRF_HEADER_NAME, csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.user.subjectType").value("PLATFORM_ADMIN"))
                .andReturn();

        String rotatedValue = extractCookieValue(findSetCookie(refreshResult, REFRESH_COOKIE_NAME));
        assertThat(rotatedValue).isNotEqualTo(refreshCookieValue);

        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, rotatedValue), csrf.cookie())
                        .header(CSRF_HEADER_NAME, csrf.token()))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(findSetCookie(logoutResult, REFRESH_COOKIE_NAME)).contains("Max-Age=0");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, rotatedValue), csrf.cookie())
                        .header(CSRF_HEADER_NAME, csrf.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void shouldLockAccountAfterFiveConsecutiveFailedLoginAttempts() throws Exception {
        School school = createSchool("LOCK-5");
        User user = createUser(school, "caissier@lock.sn", "Ousmane", "Ndiaye", RoleCode.CASHIER);

        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "schoolCode", school.getCode(),
                                    "email", user.getEmail(),
                                    "password", "MauvaisMotDePasse!"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("urn:ecole:problem:authentication-failed"));
        }

        // 5e échec -> 423 Locked
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "schoolCode", school.getCode(),
                                "email", user.getEmail(),
                                "password", "MauvaisMotDePasse!"))))
                .andExpect(status().isLocked())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:account-locked"));

        // 6e tentative même avec le bon mot de passe -> toujours 423 Locked
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "schoolCode", school.getCode(),
                                "email", user.getEmail(),
                                "password", STRONG_PASSWORD))))
                .andExpect(status().isLocked())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void shouldEnforceCsrfRotateRefreshTokensAndRevokeEntireFamilyOnReuse() throws Exception {
        School schoolA = createSchool("ROT-A");
        School schoolB = createSchool("ROT-B");
        User teacher = createUser(schoolA, "prof@rot.sn", "Ibrahima", "Fall", RoleCode.TEACHER);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "schoolCode", schoolA.getCode(),
                                "email", teacher.getEmail(),
                                "password", STRONG_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        String tokenT1 = extractCookieValue(findSetCookie(loginResult, REFRESH_COOKIE_NAME));

        // 1. Refresh et Logout SANS jeton CSRF -> 403 Forbidden RFC 9457
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, tokenT1)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:csrf-invalid"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, tokenT1)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:csrf-invalid"));

        // 2. Récupérer le jeton CSRF via /api/v1/auth/csrf
        CsrfBundle csrf = fetchCsrf();

        // En-tête CSRF incohérent -> 403
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, tokenT1), csrf.cookie())
                        .header(CSRF_HEADER_NAME, "mauvais-jeton-csrf"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:csrf-invalid"));

        // 3. Falsification du tenant dans le cookie de rafraîchissement -> 401
        String forgedCrossTenantCookie = tokenT1.replace(schoolA.getId().toString(), schoolB.getId().toString());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, forgedCrossTenantCookie), csrf.cookie())
                        .header(CSRF_HEADER_NAME, csrf.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // 4. Rotation légitime T1 -> T2
        MvcResult refresh1 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, tokenT1), csrf.cookie())
                        .header(CSRF_HEADER_NAME, csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String tokenT2 = extractCookieValue(findSetCookie(refresh1, REFRESH_COOKIE_NAME));
        assertThat(tokenT2).isNotEqualTo(tokenT1);

        // 5. Réutilisation malveillante de T1 déjà roté -> 401 + révocation de toute la famille
        MvcResult reuseResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, tokenT1), csrf.cookie())
                        .header(CSRF_HEADER_NAME, csrf.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        assertThat(findSetCookie(reuseResult, REFRESH_COOKIE_NAME)).contains("Max-Age=0");

        // 6. T2 légitime est désormais également révoqué (famille entière invalidée)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, tokenT2), csrf.cookie())
                        .header(CSRF_HEADER_NAME, csrf.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void shouldReturnFrenchProblemDetailOnValidationErrorsWithoutEchoingSecrets() throws Exception {
        String sensitivePassword = "SecretNeJamaisRenvoyer999!";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "schoolCode", "",
                                "email", "pas-un-email",
                                "password", sensitivePassword))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:validation-error"))
                .andExpect(jsonPath("$.title").value("Données invalides"))
                .andExpect(jsonPath("$.errors").isArray())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(sensitivePassword);
    }

    @Test
    void shouldEnforcePostgresqlRlsOnRefreshTokensAndReadOnlyRolesForEcoleApp() throws Exception {
        School schoolA = createSchool("RLS-RT-A");
        School schoolB = createSchool("RLS-RT-B");
        User userA = createUser(schoolA, "user-a@rls.sn", "Awa", "Diop", RoleCode.SECRETARY);
        User userB = createUser(schoolB, "user-b@rls.sn", "Modou", "Ndiaye", RoleCode.SECRETARY);

        // Génère un refresh token pour chaque établissement via /login
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "schoolCode", schoolA.getCode(),
                                "email", userA.getEmail(),
                                "password", STRONG_PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "schoolCode", schoolB.getCode(),
                                "email", userB.getEmail(),
                                "password", STRONG_PASSWORD))))
                .andExpect(status().isOk());

        TenantId tenantA = TenantId.of(schoolA.getId());
        TenantId tenantB = TenantId.of(schoolB.getId());

        // Sous ecole_app + tenant A : seuls les refresh_tokens de A sont visibles
        TenantContext.runWithTenant(tenantA, () -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");

            List<UUID> visibleUsers =
                    jdbcTemplate.queryForList("SELECT user_id FROM refresh_tokens", UUID.class);
            assertThat(visibleUsers).containsExactly(userA.getId());

            int deletedB = jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userB.getId());
            assertThat(deletedB).isZero();

            // Lecture du référentiel des rôles autorisée
            Integer rolesCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);
            assertThat(rolesCount).isEqualTo(11);
        }));

        // Tentative d'INSERT inter-tenant dans refresh_tokens -> rejetée par RLS WITH CHECK
        TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update(
                            "INSERT INTO refresh_tokens (id, tenant_id, user_id, token_hash, family_id, expires_at) "
                                    + "VALUES (?, ?, ?, ?, ?, now() + interval '7 days')",
                            UUID.randomUUID(),
                            tenantB.value(),
                            userB.getId(),
                            "a".repeat(64),
                            UUID.randomUUID());
                }))
                .isInstanceOf(DataAccessException.class));

        // Fail-closed sans TenantContext sur refresh_tokens
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class)).isZero();
        });

        // Moindre privilège V3 : ecole_app ne peut pas modifier la table roles
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.update("UPDATE roles SET label = 'Piraté' WHERE code = 'DIRECTOR'");
        })).isInstanceOf(DataAccessException.class);
    }

    private School createSchool(String prefix) {
        String code = prefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return schoolService.createSchool(
                code,
                "École " + code,
                SchoolType.PRIVATE_SCHOOL,
                "+221770000000",
                "contact@" + code.toLowerCase() + ".sn",
                "Dakar");
    }

    private User createUser(School school, String email, String firstName, String lastName, RoleCode role) {
        String hashedPassword = passwordHasher.hash(STRONG_PASSWORD);
        return TenantContext.executeWithTenant(
                TenantId.of(school.getId()),
                () -> userAccountService.createUserWithRole(
                        email, "+221771112233", firstName, lastName, hashedPassword, role));
    }

    private CsrfBundle fetchCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value(CSRF_HEADER_NAME))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String token = readJson(result).get("token").asString();
        String setCookie = findSetCookie(result, CSRF_COOKIE_NAME);
        String cookieVal = extractCookieValue(setCookie);
        assertThat(cookieVal).isEqualTo(token);
        return new CsrfBundle(new Cookie(CSRF_COOKIE_NAME, cookieVal), token);
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String findSetCookie(MvcResult result, String cookieName) {
        String header = findOptionalSetCookie(result, cookieName);
        assertThat(header).as("En-tête Set-Cookie pour %s", cookieName).isNotNull();
        return header;
    }

    private static String findOptionalSetCookie(MvcResult result, String cookieName) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(h -> h.startsWith(cookieName + "="))
                .findFirst()
                .orElse(null);
    }

    private static String extractCookieValue(String setCookieHeader) {
        int eq = setCookieHeader.indexOf('=');
        int semi = setCookieHeader.indexOf(';');
        return semi > eq ? setCookieHeader.substring(eq + 1, semi) : setCookieHeader.substring(eq + 1);
    }

    private record CsrfBundle(Cookie cookie, String token) {
    }
}
