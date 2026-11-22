package com.ecole.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecole.saas.academic.application.AcademicYearManagementService;
import com.ecole.saas.academic.domain.AcademicYear;
import com.ecole.saas.academic.domain.AcademicYearNotFoundException;
import com.ecole.saas.academic.domain.PeriodSystem;
import com.ecole.saas.identity.application.UserAccountService;
import com.ecole.saas.identity.domain.Role;
import com.ecole.saas.identity.domain.RoleCode;
import com.ecole.saas.identity.domain.User;
import com.ecole.saas.identity.infrastructure.RoleRepository;
import com.ecole.saas.school.application.SchoolService;
import com.ecole.saas.school.domain.School;
import com.ecole.saas.school.domain.SchoolType;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import com.ecole.saas.subscription.application.SubscriptionService;
import com.ecole.saas.subscription.domain.BillingCycle;
import com.ecole.saas.subscription.domain.Subscription;
import com.ecole.saas.subscription.domain.SubscriptionPlan;
import com.ecole.saas.subscription.domain.SubscriptionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class MultiTenantIsolationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    private final SchoolService schoolService;
    private final SubscriptionService subscriptionService;
    private final UserAccountService userAccountService;
    private final AcademicYearManagementService academicYearService;
    private final RoleRepository roleRepository;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    MultiTenantIsolationTest(
            SchoolService schoolService,
            SubscriptionService subscriptionService,
            UserAccountService userAccountService,
            AcademicYearManagementService academicYearService,
            RoleRepository roleRepository,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate) {
        this.schoolService = schoolService;
        this.subscriptionService = subscriptionService;
        this.userAccountService = userAccountService;
        this.academicYearService = academicYearService;
        this.roleRepository = roleRepository;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSeedAllBaseRolesIncludingTutoringCenterRoles() {
        List<RoleCode> roles = roleRepository.findAll().stream().map(Role::getCode).toList();

        assertThat(roles).containsExactlyInAnyOrder(
                RoleCode.ADMIN_SAAS,
                RoleCode.PROMOTER,
                RoleCode.DIRECTOR,
                RoleCode.SECRETARY,
                RoleCode.CASHIER,
                RoleCode.CENSOR,
                RoleCode.TEACHER,
                RoleCode.CENTER_MANAGER,
                RoleCode.TUTOR,
                RoleCode.PARENT,
                RoleCode.STUDENT);
    }

    @Test
    void shouldIsolateTwoTenantsAcrossHibernateAndPostgresqlRls() {
        School schoolA = TestSecuritySupport.runAsAdminSaas(() -> schoolService.createSchool(
                "ECOLE-A", "Cours Sainte-Marie Dakar", SchoolType.PRIVATE_SCHOOL,
                "+221770000001", "contact@ecole-a.sn", "Dakar Plateau"));
        School schoolB = TestSecuritySupport.runAsAdminSaas(() -> schoolService.createSchool(
                "CENTRE-B", "Centre Excellence Thiès", SchoolType.TUTORING_CENTER,
                "+221770000002", "contact@centre-b.sn", "Thiès"));

        TenantId tenantA = TenantId.of(schoolA.getId());
        TenantId tenantB = TenantId.of(schoolB.getId());

        SubscriptionPlan plan = TestSecuritySupport.runAsAdminSaas(() -> subscriptionService.createPlan(
                "PRO-" + UUID.randomUUID().toString().substring(0, 6),
                "Forfait Pro",
                50_000L,
                500_000L,
                600,
                30,
                10_240));

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        Subscription subA = TestSecuritySupport.runAsAdminSaas(() -> TenantContext.executeWithTenant(tenantA, () ->
                subscriptionService.createSubscription(
                        plan.getId(), SubscriptionStatus.ACTIVE, BillingCycle.MONTHLY, now, now.plus(30, ChronoUnit.DAYS))));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UUID.randomUUID().toString(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_DIRECTOR"))));

        // V16 RBAC : SchoolService (hors SchoolDirectory) et SubscriptionService réservés à ADMIN_SAAS
        assertThatThrownBy(() -> schoolService.createSchool(
                "FORBID", "Interdit", SchoolType.PRIVATE_SCHOOL, "+221770000003", "no@ecole.sn", "Dakar"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> schoolService.findById(schoolA.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> schoolService.findByCode("ECOLE-A"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> subscriptionService.findAllForCurrentTenant())
                .isInstanceOf(AccessDeniedException.class);

        User userA = TenantContext.executeWithTenant(tenantA, () ->
                userAccountService.createUserWithRole(
                        "direction@exemple.sn", "+221771111111", "Awa", "Ndiaye",
                        "hash-fictif-a", RoleCode.DIRECTOR));
        AcademicYear yearA = TenantContext.executeWithTenant(tenantA, () -> {
            AcademicYear created = academicYearService.createAcademicYear(
                    "2026-2027", "Année 2026-2027 École A",
                    LocalDate.of(2026, 10, 1), LocalDate.of(2027, 7, 31),
                    PeriodSystem.TRIMESTER);
            return academicYearService.activateAcademicYear(created.getId(), true);
        });

        // Même code d'année scolaire et même email dans l'établissement B : autorisé grâce aux unicités incluant tenant_id
        Subscription subB = TestSecuritySupport.runAsAdminSaas(() -> TenantContext.executeWithTenant(tenantB, () ->
                subscriptionService.createSubscription(
                        plan.getId(), SubscriptionStatus.ACTIVE, BillingCycle.YEARLY, now, now.plus(365, ChronoUnit.DAYS))));
        User userB = TenantContext.executeWithTenant(tenantB, () ->
                userAccountService.createUserWithRole(
                        "direction@exemple.sn", "+221772222222", "Moussa", "Diop",
                        "hash-fictif-b", RoleCode.CENTER_MANAGER));
        AcademicYear yearB = TenantContext.executeWithTenant(tenantB, () -> {
            AcademicYear created = academicYearService.createAcademicYear(
                    "2026-2027", "Année 2026-2027 Centre B",
                    LocalDate.of(2026, 10, 1), LocalDate.of(2027, 7, 31),
                    PeriodSystem.TRIMESTER);
            return academicYearService.activateAcademicYear(created.getId(), true);
        });

        // 1. Isolation couche JPA / Hibernate @TenantId : le tenant A ne voit ni ne modifie les données du tenant B
        TenantContext.runWithTenant(tenantA, () -> {
            assertThat(academicYearService.listAcademicYears(null, Pageable.unpaged()).getContent())
                    .extracting(AcademicYear::getId)
                    .containsExactly(yearA.getId());
            assertThatThrownBy(() -> academicYearService.getAcademicYear(yearB.getId()))
                    .isInstanceOf(AcademicYearNotFoundException.class);
            assertThatThrownBy(() -> academicYearService.updateAcademicYear(
                    yearB.getId(),
                    "Modification interdite",
                    LocalDate.of(2026, 10, 1),
                    LocalDate.of(2027, 7, 31),
                    PeriodSystem.TRIMESTER))
                    .isInstanceOf(AcademicYearNotFoundException.class);

            assertThat(userAccountService.findAllForCurrentTenant())
                    .extracting(User::getId)
                    .containsExactly(userA.getId());
            assertThat(userAccountService.findById(userB.getId())).isEmpty();

            TestSecuritySupport.runAsAdminSaas(() -> {
                assertThat(subscriptionService.findAllForCurrentTenant())
                        .extracting(Subscription::getId)
                        .containsExactly(subA.getId());
                assertThat(subscriptionService.findById(subB.getId())).isEmpty();
            });
        });

        // Vérifier que les données de B n'ont pas été modifiées
        TenantContext.runWithTenant(tenantB, () -> {
            AcademicYear reloadedB = academicYearService.getAcademicYear(yearB.getId());
            assertThat(reloadedB.getLabel()).isEqualTo("Année 2026-2027 Centre B");
            assertThat(subB.getAmountXof()).isEqualTo(500_000L);
        });

        // 2. Isolation couche PostgreSQL RLS directe (sous le rôle non-superuser ecole_app, hors Hibernate)
        TenantContext.runWithTenant(tenantA, () -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");

            List<UUID> visibleYears = jdbcTemplate.queryForList("SELECT id FROM academic_years", UUID.class);
            assertThat(visibleYears).containsExactly(yearA.getId());

            List<UUID> visibleUsers = jdbcTemplate.queryForList("SELECT id FROM users", UUID.class);
            assertThat(visibleUsers).containsExactly(userA.getId());

            List<UUID> visibleRoles = jdbcTemplate.queryForList("SELECT user_id FROM user_roles", UUID.class);
            assertThat(visibleRoles).containsExactly(userA.getId());

            List<UUID> visibleSubscriptions = jdbcTemplate.queryForList("SELECT id FROM subscriptions", UUID.class);
            assertThat(visibleSubscriptions).containsExactly(subA.getId());

            int updatedRows = jdbcTemplate.update(
                    "UPDATE academic_years SET label = 'Piraté' WHERE id = ?", yearB.getId());
            assertThat(updatedRows).isZero();
        }));

        // V12 : REVOKE DELETE sur academic_years pour ecole_app (archivage logique uniquement)
        TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update("DELETE FROM academic_years WHERE id = ?", yearB.getId());
                }))
                .isInstanceOf(DataAccessException.class));

        // V12 : CHECK (role_code <> 'ADMIN_SAAS') empêche l'attribution du rôle plateforme à un utilisateur tenant
        TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                        "INSERT INTO user_roles (tenant_id, user_id, role_code) VALUES (?, ?, 'ADMIN_SAAS')",
                        tenantA.value(),
                        userA.getId())))
                .isInstanceOf(DataAccessException.class));

        // Tentative d'INSERT inter-tenant sous RLS : doit être rejetée par la politique WITH CHECK
        TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update(
                            "INSERT INTO academic_years (id, tenant_id, code, label, start_date, end_date, status, is_current) "
                                    + "VALUES (?, ?, '2027-2028', 'Injection B', DATE '2027-10-01', DATE '2028-07-31', 'DRAFT', false)",
                            UUID.randomUUID(),
                            tenantB.value());
                }))
                .isInstanceOf(DataAccessException.class));

        // 3. Comportement fail-closed sans TenantContext (vérifie aussi l'absence de fuite entre connexions du pool)
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM academic_years", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_roles", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subscriptions", Integer.class)).isZero();

            // V16 : contexte plateforme (current_tenant_id() IS NULL) autorisé sur schools et subscription_plans
            List<UUID> platformVisibleSchools = jdbcTemplate.queryForList("SELECT id FROM schools", UUID.class);
            assertThat(platformVisibleSchools).contains(schoolA.getId(), schoolB.getId());
            int updatedPlansFromPlatform = jdbcTemplate.update(
                    "UPDATE subscription_plans SET name = 'Forfait Pro Plateforme' WHERE id = ?", plan.getId());
            assertThat(updatedPlansFromPlatform).isEqualTo(1);
        });

        // 4. V16 : RLS fail-closed sur schools et subscription_plans en contexte tenant
        TenantContext.runWithTenant(tenantA, () -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");

            List<UUID> tenantVisibleSchools = jdbcTemplate.queryForList("SELECT id FROM schools", UUID.class);
            assertThat(tenantVisibleSchools).containsExactly(schoolA.getId());

            int updatedOtherSchool = jdbcTemplate.update(
                    "UPDATE schools SET name = 'Piraté B' WHERE id = ?", schoolB.getId());
            assertThat(updatedOtherSchool).isZero();

            // subscription_plans : lecture autorisée à tous, écriture bloquée en contexte tenant
            List<UUID> visiblePlans = jdbcTemplate.queryForList("SELECT id FROM subscription_plans", UUID.class);
            assertThat(visiblePlans).contains(plan.getId());

            int updatedPlanFromTenant = jdbcTemplate.update(
                    "UPDATE subscription_plans SET name = 'Piraté' WHERE id = ?", plan.getId());
            assertThat(updatedPlanFromTenant).isZero();
        }));

        // V16 : INSERT sur schools et subscription_plans refusé en contexte tenant (WITH CHECK)
        TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update(
                            "INSERT INTO schools (id, code, name, school_type, status) "
                                    + "VALUES (?, 'INJ-SCH', 'Injection', 'PRIVATE_SCHOOL', 'ACTIVE')",
                            UUID.randomUUID());
                }))
                .isInstanceOf(DataAccessException.class));

        TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update(
                            "INSERT INTO subscription_plans (id, code, name, monthly_price_xof, yearly_price_xof, max_students, max_users, max_storage_mb) "
                                    + "VALUES (?, 'INJ-PLAN', 'Injection', 1000, 10000, 10, 5, 100)",
                            UUID.randomUUID());
                }))
                .isInstanceOf(DataAccessException.class));

        // 5. V16 : REVOKE DELETE ON refresh_tokens, platform_refresh_tokens FROM ecole_app
        TenantContext.runWithTenant(tenantA, () -> assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
                    jdbcTemplate.update("DELETE FROM refresh_tokens WHERE id = ?", UUID.randomUUID());
                }))
                .isInstanceOf(DataAccessException.class));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL ROLE ecole_app");
            jdbcTemplate.update("DELETE FROM platform_refresh_tokens WHERE id = ?", UUID.randomUUID());
        })).isInstanceOf(DataAccessException.class);
    }
}
