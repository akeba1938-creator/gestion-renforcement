package com.ecole.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.academic.ClassRoomSummary;
import com.ecole.saas.academic.ClassSubjectSummary;
import com.ecole.saas.academic.EvaluationPeriodSummary;
import com.ecole.saas.identity.domain.AccountRef;
import com.ecole.saas.identity.domain.RoleCode;
import com.ecole.saas.identity.domain.TokenIssuer;
import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.school.application.SchoolService;
import com.ecole.saas.school.domain.School;
import com.ecole.saas.school.domain.SchoolType;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
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
class SchoolAcademicIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final SchoolService schoolService;
    private final SchoolDirectory schoolDirectory;
    private final AcademicStructurePort academicStructurePort;
    private final TokenIssuer tokenIssuer;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SchoolAcademicIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            SchoolService schoolService,
            SchoolDirectory schoolDirectory,
            AcademicStructurePort academicStructurePort,
            TokenIssuer tokenIssuer,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.schoolService = schoolService;
        this.schoolDirectory = schoolDirectory;
        this.academicStructurePort = academicStructurePort;
        this.tokenIssuer = tokenIssuer;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldManageSchoolLifecycleOnPlatformAndTenantProfile() throws Exception {
        String adminToken = platformAdminToken();
        String schoolCode = uniqueCode("ECOLE");
        String centerCode = uniqueCode("CENTRE");

        // 1. Création d'une école privée et d'un centre de renforcement par ADMIN_SAAS
        MvcResult createSchoolRes = mockMvc.perform(post("/api/v1/platform/schools")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.ofEntries(
                                Map.entry("code", schoolCode.toLowerCase()),
                                Map.entry("name", "Institution Notre-Dame Dakar"),
                                Map.entry("shortName", "IND"),
                                Map.entry("schoolType", "PRIVATE_SCHOOL"),
                                Map.entry("phone", "+221771001001"),
                                Map.entry("email", "Contact@IND.sn"),
                                Map.entry("address", "Plateau, Rue Carnot"),
                                Map.entry("city", "Dakar"),
                                Map.entry("region", "Dakar"),
                                Map.entry("countryCode", "SN"),
                                Map.entry("motto", "Excellence et Rigueur"),
                                Map.entry("ministryCode", "IA-DK-2026-01"),
                                Map.entry("gradingPeriodSystem", "TRIMESTER"),
                                Map.entry("gradingScale", 20)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(schoolCode))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currency").value("XOF"))
                .andExpect(jsonPath("$.timezone").value("Africa/Dakar"))
                .andExpect(jsonPath("$.email").value("contact@ind.sn"))
                .andReturn();
        UUID schoolId = UUID.fromString(readJson(createSchoolRes).get("id").asString());

        MvcResult createCenterRes = mockMvc.perform(post("/api/v1/platform/schools")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", centerCode,
                                "name", "Centre Réussite Thiès",
                                "schoolType", "TUTORING_CENTER",
                                "phone", "+221772002002",
                                "city", "Thiès",
                                "gradingPeriodSystem", "SEMESTER",
                                "gradingScale", 20))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schoolType").value("TUTORING_CENTER"))
                .andExpect(jsonPath("$.gradingPeriodSystem").value("SEMESTER"))
                .andReturn();
        UUID centerId = UUID.fromString(readJson(createCenterRes).get("id").asString());

        // Doublon de code -> 409 RFC 9457
        mockMvc.perform(post("/api/v1/platform/schools")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", schoolCode,
                                "name", "Doublon",
                                "schoolType", "PRIVATE_SCHOOL"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:school-already-exists"));

        // Liste paginée filtrée par type et recherche
        mockMvc.perform(get("/api/v1/platform/schools")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("schoolType", "TUTORING_CENTER")
                        .param("search", centerCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(centerId.toString()));

        // Consultation et mise à jour du profil côté établissement par DIRECTOR / CENTER_MANAGER
        String directorToken = tenantToken(schoolId, RoleCode.DIRECTOR);
        String centerManagerToken = tenantToken(centerId, RoleCode.CENTER_MANAGER);

        mockMvc.perform(get("/api/v1/school")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(schoolId.toString()))
                .andExpect(jsonPath("$.code").value(schoolCode));

        mockMvc.perform(put("/api/v1/school")
                        .header(HttpHeaders.AUTHORIZATION, bearer(centerManagerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Centre Réussite Thiès Plus",
                                "shortName", "CRT+",
                                "phone", "+221772002099",
                                "email", "contact@crt.sn",
                                "city", "Thiès",
                                "region", "Thiès",
                                "gradingPeriodSystem", "CUSTOM",
                                "gradingScale", 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(centerCode))
                .andExpect(jsonPath("$.schoolType").value("TUTORING_CENTER"))
                .andExpect(jsonPath("$.name").value("Centre Réussite Thiès Plus"))
                .andExpect(jsonPath("$.gradingPeriodSystem").value("CUSTOM"));

        // Vérification de la frontière publique SchoolDirectory
        SchoolProfileSnapshot profile = schoolDirectory.findProfileById(centerId).orElseThrow();
        assertThat(profile.code()).isEqualTo(centerCode);
        assertThat(profile.schoolType()).isEqualTo("TUTORING_CENTER");
        assertThat(profile.gradingPeriodSystem()).isEqualTo("CUSTOM");
        assertThat(profile.active()).isTrue();

        // Suspension plateforme -> écriture tenant bloquée (409) -> réactivation -> archivage terminal
        mockMvc.perform(post("/api/v1/platform/schools/" + schoolId + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Suspension administrative temporaire"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.statusReason").value("Suspension administrative temporaire"));

        mockMvc.perform(put("/api/v1/school")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Modif pendant suspension"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:school-not-active"));

        mockMvc.perform(post("/api/v1/platform/schools/" + schoolId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/platform/schools/" + schoolId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Fermeture de l'établissement"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(post("/api/v1/platform/schools/" + schoolId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:invalid-school-transition"));
    }

    @Test
    void shouldManageCompleteAcademicStructureAndExposePublicPort() throws Exception {
        School school = createSchool("ACAD-FLOW", SchoolType.PRIVATE_SCHOOL);
        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);

        // 1. Création de deux années scolaires (2026-2027 hérite TRIMESTER de l'école, 2027-2028 en SEMESTER)
        UUID year1Id = createAcademicYearViaApi(
                directorToken, "2026-2027", "Année scolaire 2026-2027", "2026-10-01", "2027-07-31", null);
        UUID year2Id = createAcademicYearViaApi(
                directorToken, "2027-2028", "Année scolaire 2027-2028", "2027-10-01", "2028-07-31", "SEMESTER");

        // Activation de year1 comme année courante
        mockMvc.perform(post("/api/v1/academic/years/" + year1Id + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("makeCurrent", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.periodSystem").value("TRIMESTER"));

        mockMvc.perform(get("/api/v1/academic/years/current")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(year1Id.toString()));

        // Activation de year2 sans bascule, puis bascule explicite set-current -> year1 perd current
        mockMvc.perform(post("/api/v1/academic/years/" + year2Id + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("makeCurrent", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(false));

        mockMvc.perform(post("/api/v1/academic/years/" + year2Id + "/set-current")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(true));

        mockMvc.perform(get("/api/v1/academic/years/" + year1Id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(false));

        // 2. Génération automatique des 3 trimestres sur year1 + refus de doublon
        mockMvc.perform(post("/api/v1/academic/years/" + year1Id + "/periods/generate-standard")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].code").value("T1"))
                .andExpect(jsonPath("$[1].code").value("T2"))
                .andExpect(jsonPath("$[2].code").value("T3"));

        mockMvc.perform(post("/api/v1/academic/years/" + year1Id + "/periods/generate-standard")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:evaluation-period-conflict"));

        // 3. Création manuelle de semestres sur year2 + contrôles bornes / chevauchement / plafond semestriel
        UUID s1Id = createPeriodViaApi(
                directorToken, year2Id, "S1", "Semestre 1", "SEMESTER", 1, "2027-10-01", "2028-02-15", 1);

        // Hors bornes -> 400
        mockMvc.perform(post("/api/v1/academic/years/" + year2Id + "/periods")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "S2",
                                "name", "Semestre 2 hors bornes",
                                "periodType", "SEMESTER",
                                "sequenceOrder", 2,
                                "startDate", "2028-02-16",
                                "endDate", "2028-08-15",
                                "weight", 1))))
                .andExpect(status().isBadRequest());

        // Chevauchement -> 409
        mockMvc.perform(post("/api/v1/academic/years/" + year2Id + "/periods")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "S2",
                                "name", "Semestre 2 chevauchant",
                                "periodType", "SEMESTER",
                                "sequenceOrder", 2,
                                "startDate", "2028-02-15",
                                "endDate", "2028-07-31",
                                "weight", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:evaluation-period-conflict"));

        UUID s2Id = createPeriodViaApi(
                directorToken, year2Id, "S2", "Semestre 2", "SEMESTER", 2, "2028-02-16", "2028-07-31", 1);

        // 3e semestre interdit -> 409
        mockMvc.perform(post("/api/v1/academic/years/" + year2Id + "/periods")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "S3",
                                "name", "Semestre 3 interdit",
                                "periodType", "SEMESTER",
                                "sequenceOrder", 3,
                                "startDate", "2028-07-01",
                                "endDate", "2028-07-30",
                                "weight", 1))))
                .andExpect(status().isConflict());

        // Cycle de vie d'une période : open -> close -> reopen -> close -> lock -> modification interdite
        mockMvc.perform(post("/api/v1/academic/periods/" + s1Id + "/open")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(post("/api/v1/academic/periods/" + s1Id + "/close")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(post("/api/v1/academic/periods/" + s1Id + "/reopen")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(post("/api/v1/academic/periods/" + s1Id + "/close")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/academic/periods/" + s1Id + "/lock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        mockMvc.perform(put("/api/v1/academic/periods/" + s1Id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Tentative après verrouillage",
                                "sequenceOrder", 1,
                                "startDate", "2027-10-01",
                                "endDate", "2028-02-15",
                                "weight", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:invalid-evaluation-period-transition"));

        // 4. Niveaux, matières, classes et matières de classe
        UUID levelTleId = createGradeLevelViaApi(directorToken, "TLE", "Terminale", "HIGH_SCHOOL", 13);
        UUID inactiveLevelId = createGradeLevelViaApi(directorToken, "CI", "Cours d'Initiation", "PRIMARY", 1);

        mockMvc.perform(post("/api/v1/academic/grade-levels/" + inactiveLevelId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Création d'une classe sur un niveau inactif -> 409
        mockMvc.perform(post("/api/v1/academic/classes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "academicYearId", year2Id,
                                "gradeLevelId", inactiveLevelId,
                                "code", "CI-A",
                                "name", "CI A",
                                "capacity", 30))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:grade-level-inactive"));

        UUID mathId = createSubjectViaApi(directorToken, "MATH", "Mathématiques", "Maths", "SCIENTIFIC", 5);
        UUID svtId = createSubjectViaApi(directorToken, "SVT", "Sciences de la Vie et de la Terre", "SVT", "SCIENTIFIC", 6);

        UUID classId = createClassViaApi(directorToken, year2Id, levelTleId, "TLE-S2-A", "Terminale S2 A", "S2", "Salle 10", 38);

        UUID classMathId = assignSubjectViaApi(directorToken, classId, mathId, 5, 6, true);
        UUID classSvtId = assignSubjectViaApi(directorToken, classId, svtId, null, 5, true);

        // Doublon d'affectation matière -> 409
        mockMvc.perform(post("/api/v1/academic/classes/" + classId + "/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("subjectId", mathId, "coefficient", 5))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:class-subject-already-exists"));

        // Désactivation de SVT dans la classe -> seule MATH reste active
        mockMvc.perform(post("/api/v1/academic/classes/" + classId + "/subjects/" + classSvtId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/academic/classes/" + classId + "/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(classMathId.toString()))
                .andExpect(jsonPath("$.content[0].subjectCode").value("MATH"));

        // 5. Vérification de la frontière publique Spring Modulith AcademicStructurePort
        TenantContext.runWithTenant(TenantId.of(school.getId()), () -> {
            AcademicYearSummary currentYear = academicStructurePort.findCurrentAcademicYear().orElseThrow();
            assertThat(currentYear.id()).isEqualTo(year2Id);
            assertThat(currentYear.code()).isEqualTo("2027-2028");

            List<EvaluationPeriodSummary> periods = academicStructurePort.findEvaluationPeriodsByYear(year2Id);
            assertThat(periods).extracting(EvaluationPeriodSummary::id).containsExactly(s1Id, s2Id);

            ClassRoomSummary roomSummary = academicStructurePort.findClassRoomById(classId).orElseThrow();
            assertThat(roomSummary.code()).isEqualTo("TLE-S2-A");

            List<ClassSubjectSummary> activeSubjects = academicStructurePort.findActiveSubjectsForClass(classId);
            assertThat(activeSubjects).hasSize(1);
            assertThat(activeSubjects.get(0).subjectCode()).isEqualTo("MATH");
            assertThat(activeSubjects.get(0).coefficient()).isEqualTo(5);
        });

        // 6. Clôture et archivage de year1 -> immuable
        mockMvc.perform(post("/api/v1/academic/years/" + year1Id + "/close")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.current").value(false));

        mockMvc.perform(put("/api/v1/academic/years/" + year1Id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "label", "Modif interdite",
                                "startDate", "2026-10-01",
                                "endDate", "2027-07-31"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ecole:problem:academic-year-immutable"));

        mockMvc.perform(post("/api/v1/academic/years/" + year1Id + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void shouldEnforceRoleBasedAccessControlMatrixAcrossSchoolAndAcademicEndpoints() throws Exception {
        School school = createSchool("RBAC-ACAD", SchoolType.PRIVATE_SCHOOL);
        String adminToken = platformAdminToken();
        String directorToken = tenantToken(school.getId(), RoleCode.DIRECTOR);
        String centerManagerToken = tenantToken(school.getId(), RoleCode.CENTER_MANAGER);
        String promoterToken = tenantToken(school.getId(), RoleCode.PROMOTER);

        // Requêtes anonymes -> 401 RFC 9457
        mockMvc.perform(get("/api/v1/school"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/v1/academic/years"))
                .andExpect(status().isUnauthorized());

        // DIRECTOR et CENTER_MANAGER refusés sur la gestion plateforme
        mockMvc.perform(get("/api/v1/platform/schools")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/v1/platform/schools")
                        .header(HttpHeaders.AUTHORIZATION, bearer(centerManagerToken)))
                .andExpect(status().isForbidden());

        // ADMIN_SAAS refusé sur /api/v1/school et /api/v1/academic/**
        mockMvc.perform(get("/api/v1/school")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/academic/years")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());

        // PROMOTER : lecture autorisée sur /api/v1/school et /api/v1/academic/**, écriture refusée (403)
        mockMvc.perform(get("/api/v1/school")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/academic/years")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/school")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Tentative promoteur"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/academic/grade-levels")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "6E", "name", "Sixième", "cycle", "MIDDLE_SCHOOL", "displayOrder", 1))))
                .andExpect(status().isForbidden());

        // CENSOR, SECRETARY, CASHIER, TEACHER, TUTOR : 403 sur /api/v1/school, 200 lecture /api/v1/academic, 403 écriture
        for (RoleCode staffRole : List.of(
                RoleCode.CENSOR, RoleCode.SECRETARY, RoleCode.CASHIER, RoleCode.TEACHER, RoleCode.TUTOR)) {
            String token = tenantToken(school.getId(), staffRole);
            mockMvc.perform(get("/api/v1/school")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/academic/years")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/academic/grade-levels")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/academic/subjects")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/academic/classes")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/academic/subjects")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("code", "FR", "name", "Français", "defaultCoefficient", 3))))
                    .andExpect(status().isForbidden());
        }

        // PARENT et STUDENT : 403 partout
        for (RoleCode portalRole : List.of(RoleCode.PARENT, RoleCode.STUDENT)) {
            String token = tenantToken(school.getId(), portalRole);
            mockMvc.perform(get("/api/v1/school")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/academic/years")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden());
        }

        // CENTER_MANAGER autorisé en écriture académique
        mockMvc.perform(post("/api/v1/academic/grade-levels")
                        .header(HttpHeaders.AUTHORIZATION, bearer(centerManagerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "PREP-BAC", "name", "Préparation BAC", "cycle", "TUTORING", "displayOrder", 1))))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldStrictlyIsolateTenantsAcrossHttpHibernateAndPostgresqlRls() throws Exception {
        School schoolA = createSchool("ISO-ACAD-A", SchoolType.PRIVATE_SCHOOL);
        School schoolB = createSchool("ISO-ACAD-B", SchoolType.TUTORING_CENTER);

        String directorA = tenantToken(schoolA.getId(), RoleCode.DIRECTOR);
        String managerB = tenantToken(schoolB.getId(), RoleCode.CENTER_MANAGER);

        // Mêmes codes dans A et B : autorisés grâce aux unicités préfixées par tenant_id
        UUID yearA = createAcademicYearViaApi(directorA, "2026-2027", "Année A", "2026-10-01", "2027-07-31", "TRIMESTER");
        UUID yearB = createAcademicYearViaApi(managerB, "2026-2027", "Année B", "2026-10-01", "2027-07-31", "TRIMESTER");

        UUID periodA = createPeriodViaApi(directorA, yearA, "T1", "Trimestre 1 A", "TRIMESTER", 1, "2026-10-01", "2026-12-20", 1);
        UUID periodB = createPeriodViaApi(managerB, yearB, "T1", "Trimestre 1 B", "TRIMESTER", 1, "2026-10-01", "2026-12-20", 1);

        UUID levelA = createGradeLevelViaApi(directorA, "6E", "Sixième A", "MIDDLE_SCHOOL", 1);
        UUID levelB = createGradeLevelViaApi(managerB, "6E", "Sixième B", "TUTORING", 1);

        UUID subjectA = createSubjectViaApi(directorA, "MATH", "Mathématiques A", "Maths", "SCIENTIFIC", 4);
        UUID subjectB = createSubjectViaApi(managerB, "MATH", "Mathématiques B", "Maths", "SCIENTIFIC", 4);

        UUID classA = createClassViaApi(directorA, yearA, levelA, "6E-A", "6ème A", null, "Salle 1", 35);
        UUID classB = createClassViaApi(managerB, yearB, levelB, "6E-A", "Groupe 6ème A", null, "Salle B", 20);

        UUID classSubjectA = assignSubjectViaApi(directorA, classA, subjectA, 4, 5, true);
        UUID classSubjectB = assignSubjectViaApi(managerB, classB, subjectB, 4, 4, true);

        // 1. Usurpation d'en-tête X-Tenant-Id et paramètre tenantId ignorés
        mockMvc.perform(get("/api/v1/school")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA))
                        .header("X-Tenant-Id", schoolB.getId().toString())
                        .param("tenantId", schoolB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(schoolA.getId().toString()));

        mockMvc.perform(get("/api/v1/academic/years")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA))
                        .header("X-Tenant-Id", schoolB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(yearA.toString()));

        // 2. Lecture / modification directe des ressources de B par A -> 404
        mockMvc.perform(get("/api/v1/academic/years/" + yearB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/academic/periods/" + periodB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/academic/grade-levels/" + levelB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/academic/subjects/" + subjectB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/academic/classes/" + classB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/academic/classes/" + classB + "/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA)))
                .andExpect(status().isNotFound());

        // 3. Tentative de liaison cross-tenant (classe dans A sur année/niveau de B, ou matière de B dans classe A) -> 404
        mockMvc.perform(post("/api/v1/academic/classes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "academicYearId", yearB,
                                "gradeLevelId", levelA,
                                "code", "CROSS-1",
                                "name", "Cross Year",
                                "capacity", 30))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/academic/classes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "academicYearId", yearA,
                                "gradeLevelId", levelB,
                                "code", "CROSS-2",
                                "name", "Cross Level",
                                "capacity", 30))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/academic/classes/" + classA + "/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("subjectId", subjectB, "coefficient", 3))))
                .andExpect(status().isNotFound());

        // 4. Vérification directe PostgreSQL RLS sous le rôle non-superuser ecole_app (hors Hibernate)
        TenantId tenantA = TenantId.of(schoolA.getId());
        TenantId tenantB = TenantId.of(schoolB.getId());

        TenantContext.runWithTenant(tenantA, () -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");

            assertThat(jdbcTemplate.queryForList("SELECT id FROM evaluation_periods", UUID.class))
                    .containsExactly(periodA);
            assertThat(jdbcTemplate.queryForList("SELECT id FROM grade_levels", UUID.class))
                    .containsExactly(levelA);
            assertThat(jdbcTemplate.queryForList("SELECT id FROM subjects", UUID.class))
                    .containsExactly(subjectA);
            assertThat(jdbcTemplate.queryForList("SELECT id FROM class_rooms", UUID.class))
                    .containsExactly(classA);
            assertThat(jdbcTemplate.queryForList("SELECT id FROM class_subjects", UUID.class))
                    .containsExactly(classSubjectA);

            assertThat(jdbcTemplate.update("UPDATE evaluation_periods SET name = 'Piraté' WHERE id = ?", periodB)).isZero();
            assertThat(jdbcTemplate.update("UPDATE grade_levels SET name = 'Piraté' WHERE id = ?", levelB)).isZero();
            assertThat(jdbcTemplate.update("UPDATE subjects SET name = 'Piraté' WHERE id = ?", subjectB)).isZero();
            assertThat(jdbcTemplate.update("UPDATE class_rooms SET name = 'Piraté' WHERE id = ?", classB)).isZero();
            assertThat(jdbcTemplate.update("UPDATE class_subjects SET coefficient = 9 WHERE id = ?", classSubjectB)).isZero();

            assertThat(jdbcTemplate.update("DELETE FROM class_subjects WHERE id = ?", classSubjectB)).isZero();
            assertThat(jdbcTemplate.update("DELETE FROM class_rooms WHERE id = ?", classB)).isZero();
            assertThat(jdbcTemplate.update("DELETE FROM subjects WHERE id = ?", subjectB)).isZero();
            assertThat(jdbcTemplate.update("DELETE FROM grade_levels WHERE id = ?", levelB)).isZero();
            assertThat(jdbcTemplate.update("DELETE FROM evaluation_periods WHERE id = ?", periodB)).isZero();
        }));

        // Tentative d'INSERT cross-tenant sous RLS -> bloquée par WITH CHECK sur chaque nouvelle table
        TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update(
                            "INSERT INTO grade_levels (id, tenant_id, code, name, cycle, display_order, active) "
                                    + "VALUES (?, ?, 'INJ', 'Injection', 'PRIMARY', 99, true)",
                            UUID.randomUUID(),
                            tenantB.value());
                }))
                .isInstanceOf(DataAccessException.class));

        TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update(
                            "INSERT INTO subjects (id, tenant_id, code, name, category, default_coefficient, active) "
                                    + "VALUES (?, ?, 'INJ', 'Injection', 'GENERAL', 1, true)",
                            UUID.randomUUID(),
                            tenantB.value());
                }))
                .isInstanceOf(DataAccessException.class));

        // Comportement fail-closed sans TenantContext : 0 ligne visible sur toutes les tables académiques
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM academic_years", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evaluation_periods", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM grade_levels", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subjects", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM class_rooms", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM class_subjects", Integer.class)).isZero();
        });
    }

    private UUID createAcademicYearViaApi(
            String token,
            String code,
            String label,
            String startDate,
            String endDate,
            String periodSystem) throws Exception {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("code", code);
        body.put("label", label);
        body.put("startDate", startDate);
        body.put("endDate", endDate);
        if (periodSystem != null) {
            body.put("periodSystem", periodSystem);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/academic/years")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createPeriodViaApi(
            String token,
            UUID yearId,
            String code,
            String name,
            String periodType,
            int sequenceOrder,
            String startDate,
            String endDate,
            int weight) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/academic/years/" + yearId + "/periods")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", code,
                                "name", name,
                                "periodType", periodType,
                                "sequenceOrder", sequenceOrder,
                                "startDate", startDate,
                                "endDate", endDate,
                                "weight", weight))))
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

    private UUID createSubjectViaApi(
            String token, String code, String name, String shortName, String category, int defaultCoefficient)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/academic/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", code,
                                "name", name,
                                "shortName", shortName,
                                "category", category,
                                "defaultCoefficient", defaultCoefficient))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID createClassViaApi(
            String token,
            UUID academicYearId,
            UUID gradeLevelId,
            String code,
            String name,
            String series,
            String roomName,
            int capacity) throws Exception {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("academicYearId", academicYearId);
        body.put("gradeLevelId", gradeLevelId);
        body.put("code", code);
        body.put("name", name);
        if (series != null) {
            body.put("series", series);
        }
        if (roomName != null) {
            body.put("roomName", roomName);
        }
        body.put("capacity", capacity);
        MvcResult res = mockMvc.perform(post("/api/v1/academic/classes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(res).get("id").asString());
    }

    private UUID assignSubjectViaApi(
            String token, UUID classId, UUID subjectId, Integer coefficient, Integer weeklyHours, Boolean mandatory)
            throws Exception {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("subjectId", subjectId);
        if (coefficient != null) {
            body.put("coefficient", coefficient);
        }
        if (weeklyHours != null) {
            body.put("weeklyHours", weeklyHours);
        }
        if (mandatory != null) {
            body.put("mandatory", mandatory);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/academic/classes/" + classId + "/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
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
}
