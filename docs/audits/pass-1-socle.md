# Audit Backend — Passe 1 : Socle, Multi-Tenant, Disponibilité & Observabilité

**Date** : 2026-11-21  
**Mode** : Analyse statique en lecture seule — sans exécution Maven (aucune modification du code source, des migrations SQL ou de la configuration applicative ; aucun `git commit`)  
**Référentiel** : `.agents/workflows/audit-pass-1-socle.md`, `.agents/rules/quality-gate.md`, `.agents/rules/security.md`, `.agents/rules/database.md`, `.agents/rules/backend.md`, `.agents/rules/testing.md`

---

## 1. En-tête & Synthèse exécutive

### 1.1 Périmètre exact audité

| Catégorie | Éléments contrôlés | Volumétrie |
|---|---|---|
| **Modules Java (`src/main/java/com/ecole/saas/`)** | `shared` (21 fichiers), `identity` (58 fichiers), `subscription` (58 fichiers), `school` (28 fichiers), `audit` (37 fichiers), classe racine `EcoleSaasApplication.java` (1 fichier) | **203 fichiers Java** |
| **Build & Configuration applicative** | `backend/pom.xml`, `backend/src/main/resources/application.yml`, `backend/src/main/resources/application-dev.yml`, contrôle d'arborescence de `backend/src/main/resources/` (absence de secrets en clair, de clés privées et de `application-test.yml`), `backend/src/test/resources/application-test.yml` | **4 fichiers + contrôle répertoire** |
| **Migrations Flyway (`src/main/resources/db/migration/`)** | `V1__init_schema.sql` à `V15__reporting_module.sql` couvrant l'intégralité du schéma multi-tenant | **15 migrations / 74 tables** |
| **Tests associés (`src/test/java/com/ecole/saas/`)** | **7 tests racine** (`ArchitectureTest`, `MultiTenantIsolationTest`, `EcoleSaasApplicationTest`, `AuthIntegrationTest`, `SubscriptionIntegrationTest`, `SchoolAcademicIntegrationTest`, `AuditIntegrationTest`) + **11 tests unitaires/domaine/infra des 5 modules** (`shared`: 3, `identity`: 2, `subscription`: 3, `school`: 1, `audit`: 2) | **18 fichiers de tests** |

### 1.2 Tableau de comptage des constats

| Gravité | Nombre | Identifiants |
|---|---:|---|
| **`[BLOQUANT]`** | **4** | `P1-001` à `P1-004` |
| **`[MAJEUR]`** | **13** | `P1-005` à `P1-017` |
| **`[MINEUR]`** | **10** | `P1-018` à `P1-027` |
| **`[CONFORME]`** | **19** | Points de conformité vérifiés avec preuves (`C-01` à `C-19`) |

### 1.3 Résumé exécutif

Le socle multi-tenant et cryptographique repose sur des fondations techniques robustes : résolution exclusive du `TenantContext` depuis le JWT serveur (zéro lecture d'en-tête ou paramètre client), isolation Hibernate `@TenantId` couplée à `set_config('app.current_tenant_id', ..., true)` et à la Row-Level Security PostgreSQL (`ENABLE` + `FORCE ROW LEVEL SECURITY` fail-closed sous le rôle `ecole_app` `NOSUPERUSER NOBYPASSRLS`), hachage `Argon2id` conforme OWASP, chiffrement `AES-256-GCM` des secrets TOTP avec anti-rejeu, rotation atomique des refresh tokens sous `PESSIMISTIC_WRITE`, immutabilité stricte des journaux `audit_logs`, `platform_audit_logs` et `subscription_events` par triggers SQL et privilèges `SELECT, INSERT`, et domaine Java pur sans dépendance Spring.

En revanche, **4 constats `[BLOQUANT]`** empêchent la validation du Quality Gate en l'état :
1. `UserAccountService` (`identity`) ne porte ni `@PreAuthorize` ni contrôle `SubscriptionAccessPort` / quota `max_users` (`P1-001`).
2. `TenantSchoolService` (`school`) n'injecte pas `SubscriptionAccessPort`, permettant la lecture et la modification du profil d'établissement sans abonnement actif (`P1-002`).
3. `Subscription` et `SubscriptionPlan` (`subscription`) sont dépourvus de `@Version` (et `SubscriptionRepository` de `@Lock(PESSIMISTIC_WRITE)`), exposant les mutations d'abonnement, quotas et le calcul `MAX(sequenceNo)+1` de `subscription_events` aux conditions de course (`P1-003`).
4. `SubscriptionPlanService` (`findActivePlans`/`getActivePlan`), `SubscriptionService` et `SchoolService` exposent des méthodes applicatives publiques sans `@PreAuthorize` explicite (`P1-004`).

Sur le plan opérationnel et d'observabilité (**13 constats `[MAJEUR]`**), l'exploitation en production requiert le paramétrage explicite d'HikariCP (`P1-005`), l'activation des probes Actuator `liveness`/`readiness` et métriques protégées (`P1-006`), le `graceful shutdown` et les timeouts serveur/JDBC/statement (`P1-007`), une politique CORS explicite + en-tête CSP (`P1-008`), un rate limiting HTTP sur `/api/v1/auth/**` (`P1-009`), le repositionnement du `requestId` en tête de chaîne de sécurité (`P1-010`), le durcissement de `GlobalProblemHandler`/`ProblemDetailSecurityHandlers` et des logs d'alerte `WARN`/`ERROR` jusque dans `AuditLogService` (`P1-011`, `P1-012`), la configuration de logs structurés affichant `tenantId`/`requestId` (`P1-013`), l'extension de `AuditMetadataSanitizer` aux e-mails/téléphones/adresses (`P1-014`), la suppression des transactions imbriquées `REQUIRES_NEW` dans `PlatformSchoolService` (`P1-015`), l'indexation préfixée `tenant_id` sur `subscriptions` + preuves `REVOKE DELETE` (`P1-016`), et la couverture de tests manquante sur `GlobalProblemHandler`, JWT `exp`/`iss`/`alg` et verrouillage `PlatformAdmin` (`P1-017`).

---

## 2. Constats par gravité

### 2.1 Section `[BLOQUANT]` (4 constats)

#### `P1-001` — Service applicatif `UserAccountService` sans `@PreAuthorize`, sans garde `SubscriptionAccessPort` / quota `max_users`, ni validation de politique de mot de passe
- **Identifiant** : `P1-001`
- **Gravité** : `[BLOQUANT]`
- **Axe & Module** : Axe A (RBAC, Quality Gate §1 & §2) — `identity`
- **Localisation** : `backend/src/main/java/com/ecole/saas/identity/application/UserAccountService.java:19-69` (`createUserWithRole` l. 35-53, `findAllForCurrentTenant` l. 55-58, `findById` l. 60-63, `findRolesByUserId` l. 65-68)
- **Constat factuel** :
  - `UserAccountService` (bean `@Service` public) n'est annoté par aucun `@PreAuthorize` ni au niveau de la classe ni sur ses méthodes (aucune occurrence de `@PreAuthorize` dans tout le module `identity`).
  - `createUserWithRole(...)` n'invoque ni `SubscriptionAccessPort.requireOperationalSubscription()` / `assertReadAllowed()` ni le contrôle du quota `max_users` (`QuotaResource.USERS`).
  - Le service accepte directement `passwordHash` sans contrôle de `PasswordPolicy` ni garde RBAC côté service sur les rôles attribuables (seul le constructeur de `UserRole` rejette `ADMIN_SAAS`).
- **Risque** : Violation directe de `.agents/rules/quality-gate.md` §1 (`@PreAuthorize` explicite sur chaque service applicatif) et §2 (`SubscriptionAccessPort` + quotas). Tout contrôleur ou service futur injectant `UserAccountService` permettrait une élévation de privilèges (création de comptes `DIRECTOR`/`PROMOTER` ou lecture de l'annuaire utilisateurs par un rôle non habilité) ainsi que le contournement du quota `max_users` d'abonnement.
- **Correction précise** :
  1. Annoter `UserAccountService` avec `@PreAuthorize("hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER')")` (ou sécuriser finement chaque méthode d'administration/lecture ; si un amorçage hors session est requis dans les tests d'intégration, isoler un composant d'amorçage réservé aux tests ou authentifier le contexte de test).
  2. Injecter `SubscriptionAccessPort` dans `UserAccountService` : appeler `assertReadAllowed()` dans les méthodes de lecture et vérifier l'accès écriture + le quota `max_users` (avec verrouillage ou fournisseur d'usage `QuotaUsageProvider`) dans `createUserWithRole`.
  3. **Test à ajouter** : test d'intégration vérifiant le refus `403` pour un rôle non habilité (`TEACHER`, `SECRETARY`, `STUDENT`, `PARENT`) et le blocage lors du dépassement du quota `max_users`.

#### `P1-002` — Absence de garde `SubscriptionAccessPort` dans `TenantSchoolService`
- **Identifiant** : `P1-002`
- **Gravité** : `[BLOQUANT]`
- **Axe & Module** : Axe A (Quality Gate §2.4 — Garde d'abonnement) — `school`
- **Localisation** : `backend/src/main/java/com/ecole/saas/school/application/TenantSchoolService.java:23-89` (constructeur l. 28-31, `getCurrentSchool` l. 33-39, `updateCurrentSchool` l. 41-89) ; `backend/src/test/java/com/ecole/saas/SchoolAcademicIntegrationTest.java:105-203`
- **Constat factuel** : `TenantSchoolService` n'injecte pas `SubscriptionAccessPort`. Ni `getCurrentSchool()` (l. 35-39) ni `updateCurrentSchool(...)` (l. 43-89) ne vérifient l'état de l'abonnement (`assertReadAllowed()` / `requireOperationalSubscription()` / `assertWriteAllowed()`). Dans `SchoolAcademicIntegrationTest.java:105-203`, l'établissement est consulté et modifié via `/api/v1/school` sans qu'aucun abonnement n'ait été provisionné pour ce tenant.
- **Risque** : Violation directe de `.agents/rules/quality-gate.md` §2 (*"`SubscriptionAccessPort` vérifié en lecture (`assertReadAllowed()`) et écriture sur tous les services métier"*). Un établissement sans abonnement (`MISSING`), expiré (`EXPIRED`), suspendu (`SUSPENDED`) ou annulé (`CANCELLED`) peut continuer à consulter et modifier ses paramètres structurants (`name`, `gradingScale`, `gradingPeriodSystem`, `timezone`).
- **Correction précise** : Injecter `SubscriptionAccessPort` dans `TenantSchoolService`, appeler `subscriptionAccessPort.assertReadAllowed()` dans `getCurrentSchool()` et `subscriptionAccessPort.requireOperationalSubscription()` (ou `assertWriteAllowed()`) au début de `updateCurrentSchool(...)`.
- **Test à ajouter** : mettre à jour `SchoolAcademicIntegrationTest.shouldManageSchoolLifecycleOnPlatformAndTenantProfile` pour provisionner un abonnement actif et ajouter des tests vérifiant le retour `403` (`urn:ecole:problem:subscription-access-denied`) sur `GET /api/v1/school` et `PUT /api/v1/school` lorsque l'abonnement est absent, suspendu, annulé ou expiré.

#### `P1-003` — Absence de verrouillage optimiste (`@Version`) et pessimiste (`@Lock(PESSIMISTIC_WRITE)`) sur `Subscription`, `SubscriptionPlan` et la séquence d'événements `sequence_no`
- **Identifiant** : `P1-003`
- **Gravité** : `[BLOQUANT]`
- **Axe & Module** : Axe A (Quality Gate §2.1 — Intégrité & Concurrence) — `subscription` (+ migrations `V2`, `V4`, `V12`)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/subscription/domain/Subscription.java:18-80` (absence de `@Version`)
  - `backend/src/main/java/com/ecole/saas/subscription/domain/SubscriptionPlan.java:17-64` (absence de `@Version`)
  - `backend/src/main/java/com/ecole/saas/subscription/infrastructure/SubscriptionRepository.java:10-17` (aucune requête `@Lock(LockModeType.PESSIMISTIC_WRITE)`)
  - `backend/src/main/java/com/ecole/saas/subscription/application/SubscriptionSupport.java:91-113` (l. 99 : `eventRepository.findMaxSequenceNoBySubscriptionId(subscription.getId()) + 1`)
  - `backend/src/main/java/com/ecole/saas/subscription/application/PlatformSubscriptionService.java:218-576` (`activateSubscription`, `renewSubscription`, `changePlan`, `adjustExtraQuotas`, `markPastDue`, `suspendSubscription`, `cancelSubscription`)
  - `backend/src/main/resources/db/migration/V2__core_tenant_schema.sql:23-38, 80-99`, `V4__subscription_lifecycle_and_quotas.sql:4-22, 58`, `V12__security_and_integrity_hardening.sql:160-175`
- **Constat factuel** : Contrairement aux entités verrouillées par `V12:162-175`, les tables `subscriptions` et `subscription_plans` ne possèdent pas de colonne `version` et leurs entités JPA n'ont pas `@Version`. `PlatformSubscriptionService` charge l'abonnement via `subscriptionRepository.findById(subscriptionId)` sans verrou pessimiste, modifie les statuts/forfaits/extensions de quotas et calcule le numéro d'événement suivant via `MAX(sequenceNo) + 1`.
- **Risque** : Violation directe de `.agents/rules/quality-gate.md` §2 (*"`@Lock(PESSIMISTIC_WRITE)` et/ou `@Version` (ou `pg_advisory_xact_lock`) sur toute mutation de solde, capacité, quota, pack ou séquence"*). Deux mutations concurrentes sur le même abonnement (ex. ajustement des quotas et renouvellement/changement de forfait) entraînent un écrasement silencieux (*lost update*) ou une collision SQL sur la contrainte `uq_subscription_events_tenant_sub_seq` (`V4:58`).
- **Correction précise** :
  1. Créer une nouvelle migration `V16__optimistic_locking_socle.sql` (sans modifier `V2`/`V4`/`V12`) ajoutant `ALTER TABLE subscriptions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;` et `ALTER TABLE subscription_plans ADD COLUMN version BIGINT NOT NULL DEFAULT 0;`.
  2. Ajouter `@Version private Long version;` sur `Subscription` et `SubscriptionPlan`.
  3. Déclarer `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT s FROM Subscription s WHERE s.id = :id") Optional<Subscription> findByIdForUpdate(@Param("id") UUID id);` dans `SubscriptionRepository` et l'utiliser dans toutes les méthodes de mutation de `PlatformSubscriptionService` (sérialisant ainsi la mutation d'abonnement et le calcul de `sequence_no`).
- **Test à ajouter** : test de concurrence dans `SubscriptionIntegrationTest` vérifiant la sérialisation sans collision `uq_subscription_events_tenant_sub_seq` et la détection de conflit optimiste sur `Subscription` / `SubscriptionPlan`.

#### `P1-004` — Méthodes et services applicatifs publics sans `@PreAuthorize` explicite (`SubscriptionPlanService`, `SubscriptionService`, `SchoolService`)
- **Identifiant** : `P1-004`
- **Gravité** : `[BLOQUANT]`
- **Axe & Module** : Axe A (RBAC — Quality Gate §1 & Passe 1 Axe A.5) — `subscription`, `school`
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/subscription/application/SubscriptionPlanService.java:34-43` (`findActivePlans` l. 34-37, `getActivePlan` l. 39-43) et `backend/src/main/java/com/ecole/saas/subscription/api/SubscriptionPlanController.java:27-43`
  - `backend/src/main/java/com/ecole/saas/subscription/application/SubscriptionService.java:26-105` (classe `@Service` publique avec `createPlan`, `createSubscription`, `findAllForCurrentTenant`, `findById` sans `@PreAuthorize`)
  - `backend/src/main/java/com/ecole/saas/school/application/SchoolService.java:20-72` (classe `@Service` publique avec `createSchool`, `findById`, `findByCode` et méthodes `SchoolDirectory` sans `@PreAuthorize`)
- **Constat factuel** :
  - Dans `SubscriptionPlanService`, seules les méthodes d'administration (`l. 45, 54, 61, 106, 146, 165`) portent `@PreAuthorize("hasRole('ADMIN_SAAS')")` ; `findActivePlans` et `getActivePlan` (exposées via `GET /api/v1/subscription-plans` dans `SubscriptionPlanController`) n'ont aucune annotation `@PreAuthorize`.
  - `SubscriptionService` et `SchoolService` sont des `@Service` publics sans aucun `@PreAuthorize`, exposant respectivement `createPlan`/`createSubscription` (sans garde `ensureNoOpenSubscription` ni audit) et `createSchool` (sans normalisation ni audit).
- **Risque** : Violation directe de `.agents/rules/quality-gate.md` §1 (*"`@PreAuthorize` explicite sur chaque service applicatif"*) et de `audit-pass-1-socle.md` §3 Axe A.5. Sur `/api/v1/subscription-plans`, tout utilisateur authentifié (y compris `PARENT` ou `STUDENT`) accède au catalogue tarifaire SaaS sans garde de rôle explicite ; pour `SubscriptionService` et `SchoolService`, tout appel ou injection future contourne le RBAC `ADMIN_SAAS` et la piste d'audit.
- **Correction précise** :
  1. Annoter `SubscriptionPlanService.findActivePlans` et `getActivePlan` avec `@PreAuthorize("hasAnyRole('ADMIN_SAAS', 'PROMOTER', 'DIRECTOR', 'CENTER_MANAGER')")` (ou `isAuthenticated()` si l'accès catalogue est volontairement ouvert à tout authentifié, de façon explicite).
  2. Annoter les méthodes d'écriture/lecture de `SubscriptionService` et `SchoolService` avec `@PreAuthorize("hasRole('ADMIN_SAAS')")` (ou déplacer l'implémentation `SchoolDirectory` vers un adaptateur lecture dédié documenté et supprimer les méthodes d'écriture internes redondantes au profit de `PlatformSubscriptionService` / `PlatformSchoolService`).
- **Test à ajouter** : tests d'intégration RBAC sur `GET /api/v1/subscription-plans` (vérifiant les rôles autorisés et `403` pour rôles non habilités) et tests `@PreAuthorize` vérifiant le rejet sans rôle `ADMIN_SAAS` sur `SubscriptionService` et `SchoolService`.

---

### 2.2 Section `[MAJEUR]` (13 constats)

#### `P1-005` — Absence totale de configuration HikariCP dans `application.yml` et `application-dev.yml`
- **Identifiant** : `P1-005`
- **Gravité** : `[MAJEUR]`
- **Axe & Module** : Axe B (Disponibilité & Exploitation — HikariCP) — Configuration (`application*.yml`)
- **Localisation** : `backend/src/main/resources/application.yml:4-7`, `backend/src/main/resources/application-dev.yml:1-6`
- **Constat factuel** : Aucune propriété `spring.datasource.hikari.*` (`maximum-pool-size`, `minimum-idle`, `connection-timeout`, `idle-timeout`, `max-lifetime`, `leak-detection-threshold`) n'est définie dans `application.yml` ou `application-dev.yml`. Les valeurs par défaut d'HikariCP s'appliquent (`maximum-pool-size=10`, `connection-timeout=30000ms`, `leak-detection-threshold=0` désactivé).
- **Risque** : Sous charge réelle multi-tenant (imports, génération de bulletins/rapports, pics de connexions), un pool limité à 10 connexions avec attente de 30 s et sans détection de fuite (`leak-detection-threshold=0`) provoque une saturation rapide des threads Tomcat, des timeouts en cascade et une indisponibilité sans diagnostic de fuite de connexion.
- **Correction précise** : Configurer `spring.datasource.hikari` dans `application.yml` (surchargeable par variables d'environnement / profils) :
  - `maximum-pool-size: ${DB_POOL_MAX_SIZE:20}`
  - `minimum-idle: ${DB_POOL_MIN_IDLE:5}`
  - `connection-timeout: ${DB_POOL_CONNECTION_TIMEOUT_MS:5000}`
  - `idle-timeout: ${DB_POOL_IDLE_TIMEOUT_MS:300000}`
  - `max-lifetime: ${DB_POOL_MAX_LIFETIME_MS:1200000}`
  - `leak-detection-threshold: ${DB_POOL_LEAK_DETECTION_MS:15000}`
- **Test à ajouter** : test `@SpringBootTest` vérifiant le binding effectif des propriétés sur `HikariDataSource` (`getMaximumPoolSize`, `getMinimumIdle`, `getConnectionTimeout`, `getLeakDetectionThreshold`).

#### `P1-006` — Absence de probes Actuator (`liveness` / `readiness`), de restriction explicite `show-components` et de métriques
- **Identifiant** : `P1-006`
- **Gravité** : `[MAJEUR]`
- **Axe & Module** : Axe B (Disponibilité & Exploitation — Spring Boot Actuator) — Configuration (`application.yml`, `SecurityConfig`)
- **Localisation** : `backend/src/main/resources/application.yml:54-61`, `backend/src/main/java/com/ecole/saas/shared/infrastructure/security/SecurityConfig.java:73`
- **Constat factuel** : `application.yml:54-61` expose uniquement `health` (`management.endpoints.web.exposure.include: health`) avec `show-details: never` (ce qui exclut bien `env`, `beans`, `heapdump`, `mappings`, `configprops`). Toutefois :
  - `management.endpoint.health.probes.enabled: true` (`liveness` et `readiness`) est absent ;
  - `management.endpoint.health.show-components: never` n'est pas explicité ;
  - aucun endpoint `info`, `metrics` ou `prometheus` (protégé) n'est activé pour la supervision JVM/HikariCP.
- **Risque** : Les orchestrateurs (Kubernetes/Docker) ne peuvent pas distinguer une indisponibilité temporaire de PostgreSQL (`readiness`) d'un blocage irréversible de la JVM (`liveness`), entraînant des redémarrages en boucle lors d'un micro-incident DB. L'absence de métriques limite l'alerting de production (saturation pool HikariCP, latence HTTP, erreurs 5xx).
- **Correction précise** : Ajouter dans `application.yml` :
  - `management.endpoint.health.probes.enabled: true` et `management.endpoint.health.show-components: never` ;
  - Optionnellement `include: health,info,metrics,prometheus` en restreignant `/actuator/info`, `/actuator/metrics/**` et `/actuator/prometheus` dans `SecurityConfig` (rôle `ADMIN_SAAS` ou port d'administration dédié `management.server.port` non exposé publiquement).
- **Test à ajouter** : étendre `EcoleSaasApplicationTest` pour vérifier `GET /actuator/health/liveness` et `GET /actuator/health/readiness` (`200 UP` sans détails ni composants exposés) et `401`/`403` sur `/actuator/metrics` si activé.

#### `P1-007` — Absence de Graceful Shutdown (`server.shutdown=graceful`) et de timeouts serveur / JDBC / statement
- **Identifiant** : `P1-007`
- **Gravité** : `[MAJEUR]`
- **Axe & Module** : Axe B (Disponibilité & Exploitation — Shutdown & Timeouts) — Configuration (`application.yml`)
- **Localisation** : `backend/src/main/resources/application.yml:1-61`
- **Constat factuel** :
  - `server.shutdown` et `spring.lifecycle.timeout-per-shutdown-phase` sont absents (`server.shutdown` reste `immediate` par défaut).
  - Aucun timeout HTTP Tomcat (`server.tomcat.connection-timeout`, `server.tomcat.keep-alive-timeout`, `spring.mvc.async.request-timeout`) n'est défini.
  - Aucun timeout JDBC PostgreSQL (`connectTimeout`, `socketTimeout`, `options=-c statement_timeout=... -c lock_timeout=...`) ni timeout JPA (`spring.jpa.properties.jakarta.persistence.query.timeout`) n'est configuré.
- **Risque** : Lors d'un arrêt conteneur (`SIGTERM`) ou redéploiement, les requêtes HTTP et transactions SQL en cours sont interrompues brutalement. Sans `socketTimeout` / `statement_timeout`, une requête SQL coûteuse ou une coupure réseau PostgreSQL bloque durablement les threads Tomcat et les connexions HikariCP.
- **Correction précise** : Ajouter dans `application.yml` :
  - `server.shutdown: graceful` et `spring.lifecycle.timeout-per-shutdown-phase: 20s`
  - `server.tomcat.connection-timeout: 10s` et `server.tomcat.keep-alive-timeout: 15s`
  - `spring.datasource.hikari.data-source-properties.connectTimeout: 5`, `socketTimeout: 30`, `options: -c statement_timeout=25000 -c lock_timeout=5000`
  - `spring.jpa.properties.jakarta.persistence.query.timeout: 25000`
- **Test à ajouter** : test d'intégration vérifiant la configuration du `statement_timeout` en session PostgreSQL et le chargement des propriétés serveur au démarrage.

#### `P1-008` — Absence de politique CORS explicite et d'en-tête `Content-Security-Policy` (CSP) dans `SecurityConfig`
- **Identifiant** : `P1-008`
- **Gravité** : `[MAJEUR]`
- **Axe & Module** : Axe A (Politique CORS & Surface HTTP) — `shared`
- **Localisation** : `backend/src/main/java/com/ecole/saas/shared/infrastructure/security/SecurityConfig.java:52-90` (absence confirmée dans tout `backend/src/main/java`)
- **Constat factuel** :
  - Aucun bean `CorsConfigurationSource` ni appel `.cors(...)` n'existe dans `SecurityConfig` ni dans aucun autre fichier Java du backend.
  - Le bloc `.headers(...)` (`SecurityConfig.java:58-61`) configure uniquement `frameOptions(deny)` et `referrerPolicy(NO_REFERRER)`, sans `contentSecurityPolicy(...)`.
- **Risque** : En déploiement multi-origines (frontend Angular servi sur un domaine distinct de l'API avec cookie HttpOnly `ecole_refresh_token` et en-tête `X-XSRF-TOKEN`), les requêtes préflight `OPTIONS` échoueront ou inciteront à configurer un CORS permissif au niveau du reverse proxy (ex. `*` combiné avec `allowCredentials=true`). L'absence de CSP réduit la défense en profondeur des réponses HTTP.
- **Correction précise** :
  1. Ajouter `allowedOrigins` dans `SecurityProperties` (paramétrable par profil, interdiction explicite de `*` au démarrage si `allowCredentials=true`), déclarer un bean `CorsConfigurationSource` avec `allowCredentials=true`, méthodes explicites (`GET, POST, PUT, PATCH, DELETE, OPTIONS`) et en-têtes explicites (`Authorization, Content-Type, X-XSRF-TOKEN`), et activer `.cors(Customizer.withDefaults())` dans `SecurityConfig`.
  2. Ajouter `.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))` dans `.headers(...)`.
- **Test à ajouter** : tests `MockMvc` vérifiant la présence de l'en-tête `Content-Security-Policy`, le rejet d'une origine CORS non autorisée et l'acceptation d'une origine explicitement configurée.

#### `P1-009` — Absence de limitation de débit (Rate Limiting HTTP) anti-brute-force / anti-DoS sur `/api/v1/auth/**`
- **Identifiant** : `P1-009`
- **Gravité** : `[MAJEUR]`
- **Axe & Module** : Axe A (Authentification & anti-brute-force) — `identity` / `shared`
- **Localisation** : `backend/src/main/java/com/ecole/saas/identity/api/AuthController.java:39-87`, `backend/src/main/java/com/ecole/saas/identity/application/AuthService.java:120-294, 345-513`, `backend/src/main/java/com/ecole/saas/identity/infrastructure/Argon2PasswordHasher.java:16-27`
- **Constat factuel** : Le verrouillage est uniquement appliqué par compte (`LoginLockPolicy` : 5 échecs consécutifs -> blocage 15 min). Aucune limitation de débit HTTP par IP / route n'existe sur `/api/v1/auth/login`, `/api/v1/auth/platform/login`, `/api/v1/auth/mfa/verify` ni `/api/v1/auth/refresh`. De plus, `AuthService.loginTenantUser` (`l. 123`) et `loginPlatformAdmin` (`l. 222`, `unwrapLoginOutcome`) exécutent Argon2id (`19 MiB` RAM, `t=2`) même lorsque le `schoolCode` ou l'`email` est inconnu.
- **Risque** : Exposition au *password spraying* distribué sur de multiples comptes/établissements, au verrouillage malveillant ciblé des comptes `DIRECTOR` / `ADMIN_SAAS` (5 requêtes par compte), et au déni de service CPU/RAM par saturation Argon2id depuis des requêtes anonymes.
- **Correction précise** : Ajouter un filtre HTTP (ex. Bucket4j par IP + route sur `/api/v1/auth/**`, couplé le cas échéant aux limites du reverse proxy) retournant HTTP `429 Too Many Requests` en `ProblemDetail` RFC 9457 français avec en-tête `Retry-After` et journalisation `WARN`.
- **Test à ajouter** : test d'intégration vérifiant le retour HTTP `429` `ProblemDetail` après dépassement du seuil de requêtes par IP sur `/api/v1/auth/login`.

#### `P1-010` — Perte de `requestId` dans les réponses RFC 9457 de `ProblemDetailSecurityHandlers` sur erreurs 401/403 CSRF/JWT
- **Identifiant** : `P1-010`
- **Gravité** : `[MAJEUR]`
- **Axe & Module** : Axe C (RFC 9457 & Corrélation) — `shared`
- **Localisation** : `backend/src/main/java/com/ecole/saas/shared/infrastructure/security/SecurityConfig.java:71`, `TenantContextFilter.java:34, 50-54`, `ProblemDetailSecurityHandlers.java:82-85`
- **Constat factuel** :
  - `TenantContextFilter` — qui génère `requestId` et l'insère dans le `MDC` (`TenantContextFilter.java:34`) — est enregistré **après** `BearerTokenAuthenticationFilter` (`SecurityConfig.java:71`).
  - Or `CsrfFilter` s'exécute **avant** `BearerTokenAuthenticationFilter` (et invoque `ProblemDetailSecurityHandlers.handle` sur échec CSRF), et `BearerTokenAuthenticationFilter` invoque directement `ProblemDetailSecurityHandlers.commence` en cas d'absence de jeton ou de jeton JWT invalide/expiré sans poursuivre la chaîne de filtres.
  - En conséquence, lors de ces réponses `401` et `403`, `MDC.get("requestId")` dans `ProblemDetailSecurityHandlers.java:82` vaut `null` et la propriété `requestId` est absente du `ProblemDetail` retourné au client. Par ailleurs, aucun en-tête de réponse `X-Request-Id` n'est émis.
- **Risque** : Rupture du contrat RFC 9457 (`requestId` systématique) sur les erreurs de sécurité (`401`/`403` CSRF et JWT invalide), rendant impossible la corrélation entre les remontées client et les journaux serveur.
- **Correction précise** :
  1. Extraire la génération/propagation de `requestId` (`MDC` + en-tête HTTP `X-Request-Id`) dans un `RequestCorrelationFilter` dédié enregistré **tout en tête** de la `SecurityFilterChain` (avant `CsrfFilter` et `BearerTokenAuthenticationFilter`, avec nettoyage dans `finally`), tout en conservant `TenantContextFilter` après `BearerTokenAuthenticationFilter` pour `tenantId`.
  2. Dans `ProblemDetailSecurityHandlers.writeProblem`, générer un UUID de secours si `MDC.get("requestId")` est absent.
- **Test à ajouter** : assertions `jsonPath("$.requestId").isNotEmpty()` et `header().exists("X-Request-Id")` dans `AuthIntegrationTest` sur les cas `401` (sans jeton / jeton invalide) et `403` (CSRF manquant / `mfa_pending` sur `/me`).

#### `P1-011` — `GlobalProblemHandler` & `ProblemDetailSecurityHandlers` : fuite potentielle de `ex.getMessage()`, omissions d'handlers MVC/upload et absence de logs `WARN` sur `401`/`403`
- **Identifiant** : `P1-011`
- **Gravité** : `[MAJEUR]`
- **Axe & Module** : Axe C (RFC 9457, Sécurité des erreurs & Alerting) — `shared`
- **Localisation** : `backend/src/main/java/com/ecole/saas/shared/api/GlobalProblemHandler.java:134-176`, `backend/src/main/java/com/ecole/saas/shared/infrastructure/security/ProblemDetailSecurityHandlers.java:29-67`
- **Constat factuel** :
  1. `GlobalProblemHandler.handleIllegalArgument` (`l. 154-164`) et `handleIllegalState` (`l. 166-176`) renvoient `ex.getMessage()` directement au client dans `ProblemDetail.detail`. Toute `IllegalArgumentException` ou `IllegalStateException` levée par une bibliothèque tierce (Spring, Hibernate, JDK) peut exposer des messages techniques en anglais ou des détails internes.
  2. Ni `GlobalProblemHandler.handleAuthentication` / `handleAccessDenied` (`l. 134-152`, qui intercepte les refus `@PreAuthorize`), ni `ProblemDetailSecurityHandlers.commence` / `handle` (`l. 29-67`, qui intercepte les refus 401 JWT et 403 CSRF) n'émettent de log `WARN`.
  3. `GlobalProblemHandler` ne gère pas `MaxUploadSizeExceededException` (un fichier trop volumineux tombe dans `handleUnexpected` `500` au lieu de `413 Payload Too Large`) ni `ServletRequestBindingException` / `MissingPathVariableException` (`400`).
- **Risque** : Fuite possible de détails techniques ou messages non francisés vers le client ; invisibilité des refus d'autorisation RBAC/CSRF (`403`) et d'authentification (`401`) dans la supervision/alerting ; erreur `500` inappropriée sur dépassement de taille d'upload.
- **Correction précise** :
  1. Dans `GlobalProblemHandler.handleIllegalArgument` et `handleIllegalState`, retourner un message générique en français (en migrant les rejets métier explicites vers des `BusinessException`) et journaliser le type d'exception côté serveur sans PII.
  2. Ajouter `log.warn("Authentification requise sur {} {} (requestId={})", ...)` et `log.warn("Accès refusé sur {} {} (type={}, requestId={})", ...)` (sans jeton ni en-tête sensible) dans `GlobalProblemHandler` (`l. 134-152`) et `ProblemDetailSecurityHandlers` (`l. 29-67`).
  3. Ajouter des `@ExceptionHandler` dédiés pour `MaxUploadSizeExceededException` (`413`) et `ServletRequestBindingException` (`400`).
- **Test à ajouter** : test `@WebMvcTest` / intégration vérifiant qu'une `IllegalStateException` technique ou `RuntimeException` n'expose pas `ex.getMessage()`, qu'un dépassement de taille d'upload renvoie `413` en français, et que les refus `401`/`403` émettent un log `WARN` (via `OutputCaptureExtension`).

#### `P1-012` — Absence de `Logger` et de journalisation `ERROR` sur échec de persistance d'audit dans `AuditLogService`
- **Identifiant** : `P1-012`
- **Gravité** : `[MAJEUR]`
- **Axe & Module** : Axe C (Alerting & Résilience observabilité) — `audit`
- **Localisation** : `backend/src/main/java/com/ecole/saas/audit/application/AuditLogService.java:30-121` (`recordTenantEvent` l. 51-55, `recordTenantEventForSchool` l. 58-64, `recordPlatformEvent` l. 68-93, `persistTenantEvent` l. 103-121)
- **Constat factuel** : `AuditLogService` ne déclare aucun `Logger` SLF4J et n'émet aucun log `ERROR` lorsqu'une écriture dans `audit_logs` (`auditLogRepository.save`) ou `platform_audit_logs` (`platformAuditLogRepository.save`) échoue (ex. contrainte SQL, perte de connexion DB, ou exception sur flux non HTTP / asynchrone).
- **Risque** : En cas d'échec d'enregistrement dans la piste d'audit sur un flux applicatif ou lors d'une écriture `REQUIRES_NEW` interceptée, aucune alerte `ERROR` spécifique à la perte de traçabilité d'audit n'est émise pour l'équipe d'exploitation.
- **Correction précise** : Déclarer un `Logger` SLF4J dans `AuditLogService`, encadrer les appels `auditLogRepository.save(...)` et `platformAuditLogRepository.save(...)` d'un bloc `try/catch (RuntimeException ex)` qui journalise en `ERROR` (`category`, `action`, `tenantId`/`targetSchoolId`, `correlationId`, type d'exception — sans PII ni métadonnées brutes) avant de relancer l'exception.
- **Test à ajouter** : test unitaire (avec mock repository levant une `DataAccessException` + `OutputCaptureExtension`) vérifiant l'émission d'un log `ERROR` lors d'un échec de persistance dans `AuditLogService` et la propagation de l'exception.

#### `P1-013` — Absence de configuration de journalisation structurée incluant `tenantId` et `requestId`
- **Identifiant** : `P1-013`
- **Gravité** : `[MAJEUR]`
- **Axe & Module** : Axe C (Observabilité & Journaux structurés) — Configuration (`application.yml`, absence de `logback-spring.xml`)
- **Localisation** : `backend/src/main/resources/application.yml:1-61`, répertoire `backend/src/main/resources/` (0 fichier Logback), `backend/src/main/java/com/ecole/saas/shared/infrastructure/security/TenantContextFilter.java:24-25, 34, 46`
- **Constat factuel** : `TenantContextFilter` alimente les clés MDC `requestId` (`l. 34`) et `tenantId` (`l. 46`), mais ni `application.yml` (aucune clé `logging.*`) ni aucun fichier `logback-spring.xml` ne configure le format de sortie des journaux. Le pattern console par défaut de Spring Boot n'inclut pas `%X{requestId}` ni `%X{tenantId}`.
- **Risque** : En production, les journaux émis sur la sortie standard n'affichent ni `tenantId` ni `requestId`, rendant la corrélation multi-tenant et l'analyse post-incident inopérantes malgré la présence du MDC en code.
- **Correction précise** : Ajouter `logback-spring.xml` (ou `logging.pattern.console` + format structuré JSON en profil `prod` via `logging.structured.format.console: ecs` / `logstash`) incluant systématiquement `requestId` (`%X{requestId:-}`) et `tenantId` (`%X{tenantId:-}`), sans journalisation de PII.
- **Test à ajouter** : test d'intégration avec `OutputCaptureExtension` vérifiant que les journaux émis pendant une requête authentifiée contiennent `requestId` et `tenantId` (et aucun secret, mot de passe ou e-mail).

#### `P1-014` — Filtrage PII incomplet dans `AuditMetadataSanitizer` (e-mails, numéros de téléphone E.164, adresses)
- **Identifiant** : `P1-014`
- **Gravité** : `[MAJEUR]`
- **Axe & Module** : Axe C (`QG-1.3` & `audit-pass-1-socle.md` §3 Axe C.2) — `audit`
- **Localisation** : `backend/src/main/java/com/ecole/saas/audit/domain/AuditMetadataSanitizer.java:31-101, 123-166` (`FORBIDDEN_TOKENS` l. 48-73, `FORBIDDEN_COMPOUND_SUBSTRINGS` l. 75-100, `sanitizeText` l. 123-140)
- **Constat factuel** : `AuditMetadataSanitizer` masque efficacement mots de passe, JWT, secrets TOTP, cookies, CNI/NIN, passeports, IBAN et payloads bruts, mais `FORBIDDEN_TOKENS`, `FORBIDDEN_COMPOUND_SUBSTRINGS` et `sanitizeText` n'excluent ni ne masquent les clés `email`/`mail`, `phone`/`telephone`/`mobile`, `address`/`adresse` ni les adresses e-mail ou numéros de téléphone E.164 dans les résumés et valeurs libres.
- **Risque** : Toute métadonnée ou résumé contenant un e-mail, un téléphone ou une adresse postale est persisté de manière irréversible dans les tables immuables `audit_logs` / `platform_audit_logs`, en violation directe de l'exigence zéro PII dans `audit_logs`.
- **Correction précise** : Étendre `FORBIDDEN_TOKENS` / `FORBIDDEN_COMPOUND_SUBSTRINGS` (`email`, `mail`, `phone`, `telephone`, `mobile`, `address`, `adresse`, `birthdate`) et ajouter des expressions régulières de masquage pour les adresses e-mail et numéros E.164 dans `AuditMetadataSanitizer.sanitizeText`.
- **Test à ajouter** : enrichir `AuditMetadataSanitizerTest` avec des cas vérifiant l'exclusion des clés `email`/`phone`/`address` et le masquage `[MASQUE]` des e-mails et numéros E.164 dans `summary` et les valeurs de métadonnées.

#### `P1-015` — Transactions imbriquées `REQUIRES_NEW` (`PlatformSchoolService`) et split non atomique post-commit (`PlatformSubscriptionService`) avec `AuditLogPort`
- **Identifiant** : `P1-015`
- **Gravité** : `[MAJEUR]`
- **Axe & Module** : Axe A / B (Quality Gate §3.2 — Transactions courtes & résilience) — `school`, `subscription`, `audit`
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/school/application/PlatformSchoolService.java:194-277` (`suspendSchool` l. 194-221, `activateSchool` l. 223-248, `archiveSchool` l. 250-277 appelant `auditLogPort.recordTenantEventForSchool`, qui exécute `PROPAGATION_REQUIRES_NEW` dans `AuditLogService.java:45, 58-64`)
  - `backend/src/main/java/com/ecole/saas/subscription/application/PlatformSubscriptionService.java:124-133, 178-187, 276-285, 331-340, 383-392, 433-442, 478-487, 522-531, 566-575`
- **Constat factuel** :
  - Dans `PlatformSchoolService`, une transaction `@Transactional` déjà ouverte détient une connexion HikariCP tandis que `recordTenantEventForSchool` ouvre une seconde transaction `PROPAGATION_REQUIRES_NEW` (acquérant une 2e connexion simultanée) et commite `audit_logs` indépendamment du commit ou du rollback de la transaction parente sur `schools`.
  - Dans `PlatformSubscriptionService`, `auditPlatformSubscription` s'exécute après la fermeture du bloc `executeInSchoolTenant`, dans une transaction séparée : un échec lors de l'audit plateforme laisse la mutation d'abonnement déjà commitée sans trace dans `platform_audit_logs`.
- **Risque** : (1) Sous charge ou pool HikariCP réduit, l'acquisition imbriquée de deux connexions par requête expose à un épuisement/deadlock du pool HikariCP, ainsi qu'à une désynchronisation entre `schools` (rollback) et `audit_logs` (déjà commité). (2) Sur `PlatformSubscriptionService`, risque de perte silencieuse ou non corrélée de l'audit plateforme après mutation d'abonnement.
- **Correction précise** :
  1. Dans `PlatformSchoolService`, séparer la transaction sur `schools` et l'appel à `recordTenantEventForSchool` (via `TransactionTemplate` séquentiel, comme dans `PlatformAuditQueryService`, ou via un événement applicatif `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`).
  2. Dans `PlatformSubscriptionService`, encadrer `auditPlatformSubscription` d'une journalisation `ERROR` critique explicite (sans faire échouer rétroactivement le retour ou avec mécanisme de reprise/outbox si exigé).
- **Test à ajouter** : test d'intégration vérifiant qu'aucun enregistrement `audit_logs` orphelin n'est commité si la transaction `PlatformSchoolService` échoue (rollback) et qu'une seule connexion active est requise par opération.

#### `P1-016` — Index `idx_subscriptions_status_ends_at` sans préfixe `tenant_id` (`V12`) et lacunes de tests `REVOKE DELETE` sur `schools`, `subscription_plans` et `subscriptions`
- **Identifiant** : `P1-016`
- **Gravité** : `[MAJEUR]`
- **Axe & Module** : Axe A / D (Base de données §2, Quality Gate §1 & §5) — `subscription`, `school`, `V12`
- **Localisation** :
  - `backend/src/main/resources/db/migration/V12__security_and_integrity_hardening.sql:188-189` (`CREATE INDEX idx_subscriptions_status_ends_at ON subscriptions (status, ends_at);`)
  - `backend/src/test/java/com/ecole/saas/SubscriptionIntegrationTest.java:581-605`
  - `backend/src/test/java/com/ecole/saas/SchoolAcademicIntegrationTest.java:695-709`
- **Constat factuel** :
  - L'index `idx_subscriptions_status_ends_at` (`V12:188-189`) créé sur la table métier `subscriptions` ne commence pas par `tenant_id`.
  - Par ailleurs, `SubscriptionIntegrationTest:581-605` teste `REVOKE UPDATE/DELETE` uniquement sur `subscription_events` (pas sur `subscriptions` ni `subscription_plans`), et `SchoolAcademicIntegrationTest:695-709` teste `REVOKE DELETE` uniquement sur les tables académiques (`class_subjects`, `class_rooms`, `subjects`, `grade_levels`, `evaluation_periods`) sans inclure `schools` (`V12:84-88`).
- **Risque** : Écart vis-à-vis de la convention stricte d'indexation multi-tenant (`tenant_id` en tête d'index sur toute table métier) et absence de preuve automatisée protégeant contre une régression des droits `DELETE` sur `schools`, `subscription_plans` et `subscriptions`.
- **Correction précise** : Dans une nouvelle migration `V16+` (sans modifier `V12`), créer `CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_subscriptions_tenant_status_ends_at ON subscriptions (tenant_id, status, ends_at);` (et supprimer `idx_subscriptions_status_ends_at` s'il n'est pas justifié par un job plateforme hors contexte tenant).
- **Test à ajouter** : ajouter dans `SubscriptionIntegrationTest` et `SchoolAcademicIntegrationTest` des assertions `SET LOCAL ROLE ecole_app` vérifiant le rejet `DataAccessException` sur `DELETE FROM subscriptions`, `DELETE FROM subscription_plans` et `DELETE FROM schools`.

#### `P1-017` — Couverture de tests critiques manquante sur `GlobalProblemHandler` (500/JSON/`requestId`), validation JWT (`exp`/`iss`/`alg`) et verrouillage `PlatformAdmin`
- **Identifiant** : `P1-017`
- **Gravité** : `[MAJEUR]`
- **Axe & Module** : Axe D (Preuves de tests — Quality Gate §5) — Tests `AuthIntegrationTest`, `AuthCryptoInfrastructureTest`
- **Localisation** : `backend/src/test/java/com/ecole/saas/AuthIntegrationTest.java:147-159, 465-481`, `backend/src/test/java/com/ecole/saas/identity/infrastructure/AuthCryptoInfrastructureTest.java:154-209`
- **Constat factuel** :
  - `AuthIntegrationTest` ne vérifie jamais la présence du champ `$.requestId` dans les réponses `ProblemDetail` (`400`, `401`, `403`, `423`), ce qui a masqué l'anomalie `P1-010`.
  - Aucun test dans le périmètre Passe 1 ne couvre le traitement par `GlobalProblemHandler` d'un JSON malformé (`HttpMessageNotReadableException`), d'un conflit optimiste (`OptimisticLockingFailureException`), d'un conflit d'intégrité (`DataIntegrityViolationException`), ni d'une exception interne `500` (garantie d'absence de fuite de stacktrace, SQL ou nom de classe).
  - `AuthCryptoInfrastructureTest` ne teste pas le rejet par `JwtCryptoConfig.jwtDecoder` d'un JWT expiré (`exp`), d'un mauvais émetteur (`iss`) ni d'un algorithme inattendu (`alg=none` ou asymétrique).
  - `AuthIntegrationTest` ne teste pas le verrouillage après 5 échecs pour `PlatformAdmin` (testé uniquement pour `User` tenant).
- **Risque** : Régression silencieuse sur la non-fuite de stacktraces/SQL en `500`, la perte de `requestId`, ou la validation cryptographique/temporelle des JWT.
- **Correction précise** : Créer une suite dédiée `GlobalProblemHandlerTest` couvrant `400` JSON malformé, `409` intégrité/concurrence, `500` sans fuite et présence systématique de `$.requestId` ; enrichir `AuthCryptoInfrastructureTest` (cas `exp`, `iss`, `alg`) et `AuthIntegrationTest` (verrouillage `PlatformAdmin` + assertions `$.requestId` sur `401`/`403`).
- **Test à ajouter** : tests décrits ci-dessus dans `GlobalProblemHandlerTest`, `AuthCryptoInfrastructureTest` et `AuthIntegrationTest`.

---

### 2.3 Section `[MINEUR]` (10 constats)

#### `P1-018` — Validation du claim JWT `aud` (audience) absente dans `JwtTokenIssuer` et `JwtCryptoConfig`
- **Identifiant** : `P1-018`
- **Gravité** : `[MINEUR]`
- **Axe & Module** : Axe A (Authentification & JWT) — `identity` / `shared`
- **Localisation** : `backend/src/main/java/com/ecole/saas/identity/infrastructure/JwtTokenIssuer.java:60-74`, `backend/src/main/java/com/ecole/saas/shared/infrastructure/security/JwtCryptoConfig.java:40-45`
- **Constat factuel** : `JwtTokenIssuer` émet les claims `iss`, `jti`, `sub`, `iat`, `exp`, `token_type`, `subject_type`, `tenant_id` et `roles`, mais n'émet pas le claim `aud`. `JwtCryptoConfig.jwtDecoder` utilise `JwtValidators.createDefaultWithIssuer(...)` sans validateur d'audience.
- **Risque** : Risque faible en l'état (clé HMAC dédiée et `iss` validé), mais limite la défense en profondeur si plusieurs services ou environnements venaient à partager un émetteur ou une infrastructure de clés.
- **Correction précise** : Ajouter `app.security.jwt.audience` (défaut `ecole-saas-api`) dans `SecurityProperties`, émettre `.audience(List.of(audience))` dans `JwtTokenIssuer`, et combiner `JwtValidators.createDefaultWithIssuer(...)` avec un `JwtClaimValidator<List<String>>("aud", ...)` dans `JwtCryptoConfig`.
- **Test à ajouter** : test dans `AuthCryptoInfrastructureTest` rejetant un JWT dont le claim `aud` est absent ou invalide.

#### `P1-019` — Fonction `current_tenant_id()` (`V1`) sensible à une chaîne non-UUID
- **Identifiant** : `P1-019`
- **Gravité** : `[MINEUR]`
- **Axe & Module** : Axe A (RLS PostgreSQL) — Migrations SQL (`V1`)
- **Localisation** : `backend/src/main/resources/db/migration/V1__init_schema.sql:4-10`
- **Constat factuel** : La fonction `current_tenant_id()` exécute `SELECT NULLIF(current_setting('app.current_tenant_id', true), '')::uuid;`. Si `app.current_tenant_id` contenait une chaîne non vide qui n'est pas un UUID valide, PostgreSQL lèverait une exception `22P02` (`invalid input syntax for type uuid`) au lieu de retourner `NULL`.
- **Risque** : Le comportement fail-closed reste assuré (aucune fuite de ligne), mais génère une erreur SQL `500` brutale si une valeur malformée était injectée en session.
- **Correction précise** : Dans une nouvelle migration `V16__harden_current_tenant_id_function.sql` (sans jamais modifier `V1`), redéfinir `current_tenant_id()` via `CREATE OR REPLACE FUNCTION` en validant le format UUID par expression régulière (`CASE WHEN current_setting('app.current_tenant_id', true) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN ...::uuid ELSE NULL END`).
- **Test à ajouter** : test SQL Testcontainers vérifiant que `SET LOCAL app.current_tenant_id = 'invalide'` retourne 0 ligne sous RLS sans lever d'erreur `22P02`.

#### `P1-020` — Absence de garde RLS sur les écritures `schools` et `subscription_plans` pour `ecole_app` et `DELETE` conservé sur quelques tables auxiliaires
- **Identifiant** : `P1-020`
- **Gravité** : `[MINEUR]`
- **Axe & Module** : Axe A (RLS & Privilèges `ecole_app`) — Migrations SQL (`V2`, `V3`, `V7`, `V8`, `V12`)
- **Localisation** : `V2__core_tenant_schema.sql:4-38, 201-208`, `V3__auth_tokens_and_mfa.sql:49`, `V7__finance_module.sql:617-620`, `V8__teacher_grade_attendance.sql:488-491`, `V12__security_and_integrity_hardening.sql:22-32, 84-101`
- **Constat factuel** :
  - Contrairement à `platform_admins`, `platform_refresh_tokens` et `platform_audit_logs` qui portent une politique RLS `current_tenant_id() IS NULL`, les tables `schools` et `subscription_plans` (sur lesquelles `ecole_app` a `SELECT, INSERT, UPDATE`) n'ont pas de RLS.
  - `DELETE` reste accordé à `ecole_app` sur `user_roles` (`V2:208`), `refresh_tokens`/`platform_refresh_tokens` (`V3:49`), `student_invoice_lines`/`payment_schedules` (`V7:618-619`), et `teacher_specialties`/`period_subject_results` (`V8:489-490`).
- **Risque** : Si un futur service exécuté sous contexte tenant écrivait par erreur dans `subscription_plans` ou dans une autre ligne de `schools`, PostgreSQL ne bloquerait pas l'écriture (la barrière reste purement applicative). Sur `refresh_tokens`/`platform_refresh_tokens`, la rotation utilise `revoked_at` et n'a pas besoin de `DELETE`.
- **Correction précise** : Dans une migration `V16+`, activer/forcer la RLS sur `subscription_plans` (`SELECT` autorisé ; écritures restreintes à `current_tenant_id() IS NULL`) et sur `schools` (`SELECT` autorisé ; `INSERT` restreint à `current_tenant_id() IS NULL` ; `UPDATE` restreint à `current_tenant_id() IS NULL OR id = current_tenant_id()`), et révoquer `DELETE` sur `refresh_tokens` / `platform_refresh_tokens` si aucune purge SQL sous `ecole_app` n'est requise.
- **Test à ajouter** : test PostgreSQL sous `ecole_app` confirmant qu'un contexte tenant ne peut ni modifier `subscription_plans`, ni modifier une autre ligne de `schools`.

#### `P1-021` — `TenantAwareJpaTransactionManager` n'applique `set_config` qu'à l'ouverture de transaction (`doBegin`)
- **Identifiant** : `P1-021`
- **Gravité** : `[MINEUR]`
- **Axe & Module** : Axe A (Multi-Tenant Persistence) — `shared`
- **Localisation** : `backend/src/main/java/com/ecole/saas/shared/infrastructure/tenant/TenantAwareJpaTransactionManager.java:20-33`
- **Constat factuel** : `SELECT set_config('app.current_tenant_id', :tenantId, true)` n'est exécuté que dans `doBegin(...)`. Si `TenantContext.executeWithTenant(...)` ou `runWithTenant(...)` était invoqué à l'intérieur d'une transaction JPA déjà ouverte (`PROPAGATION_REQUIRED`), `app.current_tenant_id` côté PostgreSQL conserverait le tenant de la transaction englobante.
- **Risque** : Faible en l'état (`AuthService`, `PlatformSubscriptionService`, `AuditLogService` ouvrent leurs transactions à l'intérieur de `executeWithTenant` ou via `REQUIRES_NEW`), mais piégeux en cas d'évolution future.
- **Correction précise** : Ajouter un garde-fou dans `TenantContext.executeWithTenant` / `runWithTenant` vérifiant `TransactionSynchronizationManager.isActualTransactionActive()` (ou documenter et refuser tout changement de tenant sans `REQUIRES_NEW`).
- **Test à ajouter** : test d'intégration confirmant qu'un changement de `TenantContext` dans une transaction active sans `REQUIRES_NEW` est explicitement refusé.

#### `P1-022` — Utilisation directe d'`Instant.now()` dans les callbacks JPA et les handlers RFC 9457
- **Identifiant** : `P1-022`
- **Gravité** : `[MINEUR]`
- **Axe & Module** : Axe A & C (Quality Gate §2 `Clock`) — `shared`, `identity`, `subscription`, `school`, `audit`
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/shared/domain/TenantScopedEntity.java:43, 52`
  - `backend/src/main/java/com/ecole/saas/identity/domain/PlatformAdmin.java:63, 72`
  - `backend/src/main/java/com/ecole/saas/identity/domain/PlatformRefreshToken.java:56`
  - `backend/src/main/java/com/ecole/saas/subscription/domain/SubscriptionPlan.java:194, 203`
  - `backend/src/main/java/com/ecole/saas/school/domain/School.java:291, 300`
  - `backend/src/main/java/com/ecole/saas/audit/domain/PlatformAuditLog.java:132`
  - `backend/src/main/java/com/ecole/saas/shared/api/GlobalProblemHandler.java:237`
  - `backend/src/main/java/com/ecole/saas/shared/infrastructure/security/ProblemDetailSecurityHandlers.java:81`
- **Constat factuel** : Toute la logique métier (`AuthService`, `JwtTokenIssuer`, `PlatformSubscriptionService`, `PlatformSchoolService`, `AuditLogService`) injecte bien `Clock`. En revanche, les horodatages techniques `createdAt`/`updatedAt` des hooks `@PrePersist`/`@PreUpdate` et le champ `timestamp` des `ProblemDetail` appellent `Instant.now()` directement.
- **Risque** : Impact purement technique sur le déterminisme temporel des tests (aucun impact sur les calculs d'expiration ou de cycle métier).
- **Correction précise** : Injecter `Clock` dans `GlobalProblemHandler` et `ProblemDetailSecurityHandlers` ; pour les entités JPA, utiliser `@EnableJpaAuditing` avec un `DateTimeProvider` adossé au bean `Clock` (hors couche `domain` pure, ou conserver la tolérance explicite pour `createdAt`/`updatedAt`).
- **Test à ajouter** : test unitaire vérifiant que la propriété `timestamp` des `ProblemDetail` utilise le `Clock` injecté.

#### `P1-023` — Journalisation des échecs auth/MFA unitaires au niveau `INFO` et absence de log sur `schoolCode` inconnu
- **Identifiant** : `P1-023`
- **Gravité** : `[MINEUR]`
- **Axe & Module** : Axe C (Observabilité & Alerting) — `identity`
- **Localisation** : `backend/src/main/java/com/ecole/saas/identity/application/AuthService.java:122-125, 166, 252, 386, 448`
- **Constat factuel** : Les verrouillages de compte et réutilisations de refresh token sont correctement journalisés en `WARN` et audités en `CRITICAL`. En revanche, les échecs unitaires de mot de passe (`l. 166, 252`) et de code MFA (`l. 386, 448`) sont émis au niveau `INFO`, et une tentative avec un `schoolCode` inexistant/inactif (`l. 122-125`) n'émet aucun log.
- **Risque** : Sous-détection dans les tableaux de bord d'alerte lors d'attaques à faible volume par compte (sous le seuil de 5 tentatives).
- **Correction précise** : Passer ces événements en `log.warn(...)` (en conservant strictement l'absence de PII : `accountId` / `schoolCode` normalisé uniquement, jamais d'email ni de mot de passe).
- **Test à ajouter** : test avec `OutputCaptureExtension` sur échec login/MFA confirmant le niveau `WARN` et l'absence de PII.

#### `P1-024` — Incohérence fonctionnelle sur `trialDays = 0` entre `SubscriptionPlan`/`V4` et `SubscriptionPolicy.computeTrialEnd`
- **Identifiant** : `P1-024`
- **Gravité** : `[MINEUR]`
- **Axe & Module** : Axe A (Cohérence domaine / DTO / SQL) — `subscription`
- **Localisation** : `backend/src/main/resources/db/migration/V4__subscription_lifecycle_and_quotas.sql:10`, `CreateSubscriptionPlanRequest.java:33`, `SubscriptionPlan.java:178`, `SubscriptionPolicy.java:148`, `PlatformSubscriptionService.java:96-97`
- **Constat factuel** : Un forfait accepte `trialDays = 0` (`V4:10`, `CreateSubscriptionPlanRequest:33`, `SubscriptionPlan:178`), mais `PlatformSubscriptionService.startTrial` appelle `SubscriptionPolicy.computeTrialEnd(now, effectiveTrialDays)` qui lève `IllegalArgumentException` si `trialDays <= 0`.
- **Risque** : Rejet HTTP `400` générique (`invalid-argument`) au lieu d'une `BusinessException` métier explicite lorsqu'un forfait est configuré sans période d'essai (`trialDays = 0`).
- **Correction précise** : Lever une `BusinessException` dédiée (ex. `InvalidSubscriptionTransitionException`) dans `PlatformSubscriptionService.startTrial` lorsque `effectiveTrialDays == 0` sans `trialDaysOverride`.
- **Test à ajouter** : test dans `SubscriptionIntegrationTest` couvrant le démarrage d'un essai sur un forfait à `trialDays = 0`.

#### `P1-025` — Paramètre `search` non borné dans `PlatformSchoolService` et jokers `LIKE` (`%`/`_`) non échappés
- **Identifiant** : `P1-025`
- **Gravité** : `[MINEUR]`
- **Axe & Module** : Axe A / Performance (`BE-5`) — `school`, `audit`
- **Localisation** : `backend/src/main/java/com/ecole/saas/school/application/PlatformSchoolService.java:68-73`, `backend/src/main/java/com/ecole/saas/audit/application/TenantAuditQueryService.java:118-120`, `backend/src/main/java/com/ecole/saas/audit/application/PlatformAuditQueryService.java:184-186`
- **Constat factuel** : `PlatformSchoolService.listSchools` ne borne pas la longueur de `search` (contrairement à `AuditQueryCriteria.MAX_SEARCH_LENGTH = 120`), et aucun des trois services n'échappe les caractères jokers SQL `%`, `_` et `\` avant `cb.like(...)`.
- **Risque** : Requêtes `LIKE '%...%'` potentiellement trop larges ou inattendues si `search` contient `%`/`_` ou une chaîne très longue.
- **Correction précise** : Borner `search` à 120 caractères dans `PlatformSchoolService.listSchools` et échapper `\`, `%`, `_` via `cb.like(..., pattern, '\\')` dans les trois services.
- **Test à ajouter** : tests unitaires/intégration vérifiant le rejet d'un `search > 120` et la recherche littérale contenant `%` ou `_`.

#### `P1-026` — Absence de tri secondaire déterministe (`id`) dans `SubscriptionPagination` et `SchoolPagination`
- **Identifiant** : `P1-026`
- **Gravité** : `[MINEUR]`
- **Axe & Module** : Axe A / Performance (`BE-5` Pagination) — `subscription`, `school`
- **Localisation** : `backend/src/main/java/com/ecole/saas/subscription/api/SubscriptionPagination.java:36`, `backend/src/main/java/com/ecole/saas/school/api/SchoolPagination.java:31` (à comparer à `AuditPagination.java:31`)
- **Constat factuel** : Contrairement à `AuditPagination.of` (`AuditPagination.java:31` : `.and(Sort.by(sortDirection, "id"))`), `SubscriptionPagination.of` et `SchoolPagination.of` n'ajoutent pas de tri secondaire sur `id`.
- **Risque** : Instabilité possible de l'ordre entre pages consécutives en cas d'ex æquo sur `createdAt` ou `status`.
- **Correction précise** : Ajouter `.and(Sort.by(sortDirection, "id"))` dans `SubscriptionPagination.of` (`l. 36`) et `SchoolPagination.of` (`l. 31`).
- **Test à ajouter** : test d'intégration vérifiant l'ordre stable sur deux pages avec horodatages identiques.

#### `P1-027` — Absence de validation de schéma `https?://` sur `School.website` et absence de `@Version` sur `School`
- **Identifiant** : `P1-027`
- **Gravité** : `[MINEUR]`
- **Axe & Module** : Axe A (Validation DTO & Concurrence — Quality Gate §2.1 & §2.2) — `school`
- **Localisation** : `backend/src/main/java/com/ecole/saas/school/api/CreateSchoolRequest.java:42-43`, `UpdatePlatformSchoolRequest.java:39-40`, `UpdateTenantSchoolRequest.java:35-36`, `backend/src/main/java/com/ecole/saas/school/domain/School.java:83-84`
- **Constat factuel** : Le champ `website` n'est contraint que par `@Size(max = 255)` sans validation de schéma HTTP/HTTPS (`https?://`), et l'entité `School` ne dispose pas d'un champ `@Version`.
- **Risque** : Persistance possible d'URL non HTTP (`javascript:...`) si affichées en lien côté frontend, et risque d'écrasement silencieux en cas de modification simultanée du profil d'un établissement par la plateforme et par le directeur.
- **Correction précise** : Ajouter une validation `@Pattern(regexp = "^https?://.*", ...)` (lorsque non nul/non vide) dans les DTO et `School`, et ajouter `version BIGINT NOT NULL DEFAULT 0` sur `schools` (`V16+`) avec `@Version` sur `School`.
- **Test à ajouter** : test de validation rejetant `website = "javascript:alert(1)"` (`400`) et test de conflit optimiste sur `School`.

---

### 2.4 Section `[CONFORME]` (19 points validés avec preuves)

| # | Exigence vérifiée | Preuves contrôlées (`fichier:ligne`) |
|---|---|---|
| `C-01` | **Zéro secret en clair & absence de `application-test.yml` dans `src/main/resources/`** | Listing complet `backend/src/main/resources/` (`application.yml`, `application-dev.yml`, `db/migration/V1..V15` uniquement) ; `application.yml:5-7, 29, 33` (variables `${...}` sans défaut secret) ; `SecurityProperties.java:17-52, 58-70` (validation Base64 >= 32 octets et masquage `toString()`) ; `ArchitectureTest.java:128-135`. |
| `C-02` | **Résolution exclusive du `TenantContext` depuis le JWT serveur + nettoyage `finally` (zéro header/param client)** | `TenantContextFilter.java:19-55` (`TenantContext.clear()` avant/après `try/finally`, lecture exclusive de `JwtClaims.CLAIM_TENANT_ID`) ; `TenantContext.java:35-61` ; recherche exhaustive `X-Tenant` / `getHeader` dans `src/main/java/` = 0 occurrence ; `TenantSubscriptionController:22-70`, `TenantSchoolController:20-56`, `TenantAuditController:28-90` ; tests spoofing `AuthIntegrationTest:140-145`, `SubscriptionIntegrationTest:523-532`, `SchoolAcademicIntegrationTest:606-612`, `AuditIntegrationTest:539-555`. |
| `C-03` | **Persistence multi-tenant `TenantAwareJpaTransactionManager` + `TenantIdentifierResolver` + `@TenantId`** | `MultiTenantPersistenceConfig.java:14-27`, `TenantAwareJpaTransactionManager.java:20-33` (`set_config('app.current_tenant_id', :tenantId, true)` en portée locale transactionnelle), `TenantIdentifierResolver.java:10-24`, `TenantScopedEntity.java:38-48`. |
| `C-04` | **Configuration JPA socle (`open-in-view=false`, `ddl-auto: validate`, fuseau UTC)** | `application.yml:9-15, 22-23`. |
| `C-05` | **Sécurité JWT, durées de vie courtes, validation `iss`/`exp`/`alg`/`token_type`/`subject_type`/`roles` & séparation plateforme** | `TokenLifetimes.java:7-9` (`15m`, `5m`, `7j`) ; `JwtCryptoConfig.java:40-46` (`MacAlgorithm.HS256`, `createDefaultWithIssuer`) ; `JwtAuthenticationConverter.java:20-68` (cloisonnement `USER` vs `PLATFORM_ADMIN`, rejet `ADMIN_SAAS` côté `USER` et `roles` sur `mfa_pending`) ; `PlatformSubscriptionController:24-26`, `PlatformSubscriptionPlanController:23-25`, `PlatformSchoolController:27-29`, `PlatformAuditController:30-32`, `AuditIntegrationTest:563-595`. |
| `C-06` | **Rotation atomique des refresh tokens (`PESSIMISTIC_WRITE`), détection de réutilisation et CSRF sur cookies `HttpOnly`/`Secure`/`SameSite=Strict`** | `RefreshTokenRepository.java:15-17`, `PlatformRefreshTokenRepository.java:15-17`, `AuthService.java:583-680` (révocation de toute la `familyId`), `RefreshCookieCodec.java:24-71`, `RefreshCookieFactory.java:22-40`, `SecurityConfig.java:26-57` (CSRF sur `/refresh` et `/logout`). |
| `C-07` | **Hachage `Argon2id` (OWASP) et chiffrement `AES-256-GCM` des secrets TOTP + anti-rejeu + MFA obligatoire** | `Argon2PasswordHasher.java:13-28` (`m=19456 KiB`, `t=2`, `p=1`), `AesGcmSecretCipher.java:23-98` (`AES/GCM/NoPadding`, IV 96 bits, tag 128 bits), `TotpGenerator.java:62-78`, `LoginSecurity.java:71-80` (`mfaLastUsedStep`), `MfaPolicy.java:9-21` (`DIRECTOR`, `PROMOTER`, `CENTER_MANAGER`, `ADMIN_SAAS`). |
| `C-08` | **Politique de route par défaut authentifiée (`AUTHORITY_ACCESS`) et séparation `mfa_pending`** | `SecurityConfig.java:72-89` (`anyRequest().hasAuthority(AUTHORITY_ACCESS)`, `/mfa/enroll` & `/mfa/verify` restreints, `frameOptions(deny)`, `referrerPolicy(NO_REFERRER)`). |
| `C-09` | **Zéro PII/secret dans les logs applicatifs et métadonnées d'audit d'authentification** | `AuthService.java:140-884`, `UserAccountService.java:45-51`, `GlobalProblemHandler.java:180-224` (seuls `accountId`/`familyId`/`requestId`/`URI` sont journalisés). |
| `C-10` | **Architecture domaine pur (zéro `org.springframework.*` dans `..domain..`), injection par constructeur, zéro `@Entity` exposée** | Vérifié exhaustivement sur les 64 fichiers `..domain..` de `shared` (6), `identity` (26), `subscription` (16), `school` (9), `audit` (7) ; 100 % des contrôleurs et services injectés par constructeur exposent des records `*Response` / `PageResponse<*>`; `ArchitectureTest.java:41-140`. |
| `C-11` | **`@Valid` + types boxés `@NotNull` sur les DTO d'entrée** | Vérifié sur tous les `*Request` de `identity.api`, `subscription.api` (`Long`/`Integer`/`Boolean` + `@NotNull`) et `school.api`, et `@Valid` sur chaque `@RequestBody`. |
| `C-12` | **Montants FCFA strictement en `long`/`BIGINT` & quota non destructif (`FIN-7`)** | `Subscription.amountXof` (`long`), `SubscriptionPlan.monthlyPriceXof`/`yearlyPriceXof` (`long`), `BIGINT` (`V2:27-28,86`, `V4:49`) ; `SubscriptionPolicy.evaluateQuota:90-104` (`SubscriptionTest:18-35`, `SubscriptionPolicyTest:70-129`). |
| `C-13` | **Pagination bornée (`MAX_SIZE=100`) & préchargement batch / agrégation SQL sans N+1** | `SubscriptionPagination:13`, `SchoolPagination:11`, `AuditPagination:11` ; `UserDirectoryQueryService:70` (`findByUserIdIn`), `SubscriptionSupport.toSnapshotPage:76-88` (`findAllById`), `TenantAuditQueryService.getAuditSummary:60-70, 125-153` (`CriteriaBuilder` `GROUP BY`). |
| `C-14` | **`Clock` injecté dans les services métier** | `AuthService`, `JwtTokenIssuer`, `PlatformSubscriptionService:64`, `SubscriptionGuardService:33`, `SubscriptionService:32`, `TenantSubscriptionService:41`, `PlatformSchoolService:43`, `AuditLogService:35`. |
| `C-15` | **Contrats d'erreurs `ProblemDetail` RFC 9457 en français via `BusinessException`** | `GlobalProblemHandler:38-259`, `BusinessException` + sous-classes métier ; `application.yml:20-21` (`spring.mvc.problemdetails.enabled: true`). |
| `C-16` | **Alignement exact des entités JPA (`identity`, `subscription`, `school`, `audit`) avec `V2`/`V3`/`V4`/`V5`/`V10`/`V12`/`V15`** | Vérifié colonne par colonne (hors absence de `version` traitée en `P1-003` et `P1-027` et index `P1-016`), incluant l'alignement complet des 17 valeurs d'`AuditCategory` avec `V15:6-46`. |
| `C-17` | **Immutabilité SQL + JPA (`audit_logs`, `platform_audit_logs`, `subscription_events`) et `REVOKE DELETE` sur tables à archivage logique** | Triggers `BEFORE UPDATE OR DELETE` (`V10:126-132`, `V12:6-16`), `GRANT SELECT, INSERT` (`V10:150`, `V4:97`), `REVOKE DELETE` sur 16 tables (`V12:84-101`), dépôts sans `delete*`, `@PreUpdate`/`@PreRemove` (`AuditLog:118-128`, `PlatformAuditLog:136-146`, `SubscriptionEvent:109-120`), preuves `AuditIntegrationTest:602-642` & `SubscriptionIntegrationTest:581-606`. |
| `C-18` | **Isolation multi-tenant PostgreSQL RLS fail-closed + `ecole_app` prouvée par Testcontainers** | `MultiTenantIsolationTest.java:107-256` et `AuthIntegrationTest.java:484-570` (`SET LOCAL ROLE ecole_app`, fail-closed sans tenant, `WITH CHECK`, blocage `platform_admins` et `platform_refresh_tokens` dès qu'un tenant est actif, `roles` en lecture seule). |
| `C-19` | **Exposition Actuator limitée à `health` (`show-details: never`)** | `application.yml:54-61` et `EcoleSaasApplicationTest.java:56-63` (`/actuator/health` -> 200 `{status: UP}`, `/actuator/env` -> 4xx). |

---

## 3. Matrice de vérification `V1`..`V15` (74 tables + socle `V1`)

Légende :
- **RLS fail-closed** : `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` + policy `tenant_id = current_tenant_id()` (ou `current_tenant_id() IS NULL` pour les tables plateforme `ADMIN_SAAS`).
- **Rôle `ecole_app`** : `NOSUPERUSER NOBYPASSRLS` (`V2:194`).

| Mig. | Table | `tenant_id` NOT NULL + FK `schools(id)` | Index préfixé `tenant_id` | `UNIQUE` / `FK` composites `(tenant_id, ...)` | `ENABLE` + `FORCE RLS` & policy fail-closed | Privilèges `ecole_app` (état final après `V12`..`V15`) | Immutabilité / Archivage logique | Statut / Notes |
|---|---|---|---|---|---|---|---|---|
| **V1** | *(infra SQL)* | Extension `pgcrypto` + `current_tenant_id()` | — | — | `NULLIF(current_setting('app.current_tenant_id', true), '')::uuid` | `EXECUTE` sur `current_tenant_id()` | — | `[MINEUR]` `P1-019` : non-UUID lève `22P02` |
| **V2** | `schools` | N/A (table racine des tenants) | `idx_schools_status`, `idx_schools_type` (V5) | `uq_schools_code` | Non (`[MINEUR]` `P1-020`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:85) | Archivage logique (`status='ARCHIVED'`, `archived_at` V5) | OK globale ; RLS écriture + `@Version` recommandés (`P1-020`, `P1-027`), test `REVOKE DELETE` manquant (`P1-016`) |
| **V2** | `subscription_plans` | N/A (catalogue global SaaS) | — | `uq_subscription_plans_code` | Non (`[MINEUR]` `P1-020`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:86) | Désactivation logique (`active`) | `[BLOQUANT]` pas de `version` (`P1-003`) ; RLS écriture recommandée (`P1-020`) ; test `REVOKE DELETE` manquant (`P1-016`) |
| **V2** | `roles` | N/A (référentiel global rôles) | PK `code` | — | Non (référentiel global) | `SELECT` seul (`REVOKE INSERT, UPDATE, DELETE` V3:52) | Référentiel statique | `[CONFORME]` |
| **V2** | `platform_admins` | N/A (comptes plateforme SaaS) | `uq_platform_admins_email` | `uq_platform_admins_email` | Oui (V12:22-26) : `current_tenant_id() IS NULL` | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:87) | Statut `DISABLED` | `[CONFORME]` |
| **V2** | `subscriptions` | Oui (`V2:82`) | `idx_subscriptions_tenant_id`, `idx_subscriptions_tenant_created_at` (V4), `uq_subscriptions_open_per_tenant` (V4) | `uq_subscriptions_tenant_id_id` (V12:38) | Oui (`V2:166-170`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:88) | Cycle `CANCELLED`/`SUSPENDED` | `[BLOQUANT]` pas de `version` (`P1-003`) ; `[MAJEUR]` `idx_subscriptions_status_ends_at` sans `tenant_id` (`V12:188-189`, `P1-016`) |
| **V2** | `users` | Oui (`V2:109`) | `idx_users_tenant_id` | `uq_users_tenant_email`, `uq_users_tenant_id_id` (V8:5) | Oui (`V2:172-176`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:89) | Statut `DISABLED` | `[CONFORME]` |
| **V2** | `user_roles` | Oui (`V2:132`) | `idx_user_roles_tenant_id`, `idx_user_roles_tenant_user`, `idx_user_roles_tenant_role_code` (V12) | `uq_user_roles_tenant_user_role`, `fk_user_roles_user` (V12:44-47) | Oui (`V2:178-182`) | `SELECT, INSERT, UPDATE, DELETE` | `CHECK (role_code <> 'ADMIN_SAAS')` (V12:107) | OK (`DELETE` utilisé pour retrait de rôle ; `P1-020`) |
| **V2** | `academic_years` | Oui (`V2:146`) | `idx_academic_years_tenant_id`, `uq_academic_years_current_per_tenant`, `idx_academic_years_tenant_dates` (V5) | `uq_academic_years_tenant_code`, `uq_academic_years_tenant_id_id` (V5:41) | Oui (`V2:184-188`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:90) | Statut `CLOSED`/`ARCHIVED` | `[CONFORME]` |
| **V3** | `refresh_tokens` | Oui (`V3:10`) | `idx_refresh_tokens_tenant_id`, `_tenant_family`, `_tenant_user`, `_tenant_expires` (V12) | `uq_refresh_tokens_tenant_hash`, `fk_refresh_tokens_user` (V12:49-52) | Oui (`V3:26-30`) | `SELECT, INSERT, UPDATE, DELETE` | Révocation `revoked_at` | OK (`DELETE` non utilisé en rotation ; `P1-020`) |
| **V3** | `platform_refresh_tokens` | N/A (plateforme SaaS) | `idx_platform_refresh_tokens_family`, `_admin` | `uq_platform_refresh_tokens_hash` | Oui (V12:28-32) : `current_tenant_id() IS NULL` | `SELECT, INSERT, UPDATE, DELETE` | Révocation `revoked_at` | OK (`P1-020`) |
| **V4** | `subscription_events` | Oui (`V4:42`) | `idx_subscription_events_tenant_id`, `_tenant_sub` | `uq_subscription_events_tenant_sub_seq`, `fk_subscription_events_subscription` (V12:54-57) | Oui (`V4:90-94`) | `SELECT, INSERT` (V4:97) | Immuable (`trg_subscription_events_immutable` V12:14-16) | `[CONFORME]` (verrou parent manquant au calcul `sequence_no`, `P1-003`) |
| **V5** | `evaluation_periods` | Oui (`V5:50`) | `idx_evaluation_periods_tenant_id`, `_tenant_year_order` | `fk_evaluation_periods_academic_year`, `uq_*_code`, `uq_*_order`, `uq_*_tenant_id_id` (V8:8) | Oui (`V5:201-205`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:91) | Statut `CLOSED`/`LOCKED` | `[CONFORME]` |
| **V5** | `grade_levels` | Oui (`V5:89`) | `idx_grade_levels_tenant_id`, `_tenant_order` | `uq_grade_levels_tenant_code`, `uq_grade_levels_tenant_id_id` | Oui (`V5:207-211`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:92) | Archivage logique (`active`) | `[CONFORME]` |
| **V5** | `subjects` | Oui (`V5:112`) | `idx_subjects_tenant_id`, `_tenant_name` | `uq_subjects_tenant_code`, `uq_subjects_tenant_id_id` | Oui (`V5:213-217`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:93) | Archivage logique (`active`) | `[CONFORME]` |
| **V5** | `class_rooms` | Oui (`V5:137`) | `idx_class_rooms_tenant_id`, `_tenant_year`, `_tenant_level` | `fk_*_academic_year`, `fk_*_grade_level`, `uq_*_year_code`, `uq_*_id_id` | Oui (`V5:219-223`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:94) | Statut `ARCHIVED` | `[CONFORME]` |
| **V5** | `class_subjects` | Oui (`V5:172`) | `idx_class_subjects_tenant_id`, `_tenant_class`, `_tenant_subject` | `fk_*_class_room`, `fk_*_subject`, `uq_*_class_subject`, `uq_*_id_id` (V8:11) | Oui (`V5:225-229`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:95) | Désactivation `active` | `[CONFORME]` |
| **V6** | `student_matricule_sequences` | Oui (`V6:6`) | `idx_student_matricule_sequences_tenant_id` | `uq_student_matricule_sequences_tenant_year` | Oui (`V6:225-229`) | `SELECT, INSERT, UPDATE` (V6:262) | Compteur atomique | `[CONFORME]` |
| **V6** | `students` | Oui (`V6:22`) | `idx_students_tenant_id`, `_status`, `_name`, `uq_students_tenant_user_id` | `uq_students_tenant_matricule`, `uq_*_id_id`, `fk_students_user` (V12:60-62) | Oui (`V6:231-235`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:96) | Archivage logique (`archived_at`, `status`) | `[CONFORME]` |
| **V6** | `student_enrollments` | Oui (`V6:63`) | `idx_student_enrollments_tenant_id`, `_student_year`, `_class_status`, `_year_status`, `uq_*_active_per_year` | `fk_*_student`, `fk_*_academic_year`, `fk_*_class_room`, `uq_*_id_id` (V12:41) | Oui (`V6:237-241`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:97) | Statuts `COMPLETED`/`WITHDRAWN`/`CANCELLED` | `[CONFORME]` |
| **V6** | `families` | Oui (`V6:107`) | `idx_families_tenant_id`, `_name`, `uq_families_tenant_code` | `uq_families_tenant_id_id` | Oui (`V6:243-247`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:98) | Archivage logique (`archived_at`, `status`) | `[CONFORME]` |
| **V6** | `guardians` | Oui (`V6:135`) | `idx_guardians_tenant_id`, `_family`, `_phone`, `_name`, `uq_guardians_tenant_user_id` | `fk_guardians_family`, `uq_*_id_id`, `fk_guardians_user` (V12:64-66) | Oui (`V6:249-253`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:99) | Archivage logique (`archived_at`, `status`) | `[CONFORME]` |
| **V6** | `student_guardians` | Oui (`V6:177`) | `idx_student_guardians_tenant_id`, `_student`, `_guardian`, 2 `uq_*` partiels | `fk_*_student`, `fk_*_guardian`, `uq_*_student_guardian` | Oui (`V6:255-259`) | `SELECT, INSERT, UPDATE` (`DELETE` révoqué V12:100) | Désactivation `active` | `[CONFORME]` |
| **V7** | `finance_sequences` | Oui (`V7:15`) | `idx_finance_sequences_tenant_id` | `uq_finance_sequences_tenant_type_year` | Oui (`V7:518-522`) | `SELECT, INSERT, UPDATE` (V7:607) | Compteur atomique | `[CONFORME]` |
| **V7** | `fee_items` | Oui (`V7:32`) | `idx_fee_items_tenant_id`, `_year`, `_level` | `fk_*_academic_year`, `fk_*_grade_level`, `uq_*_year_code`, `uq_*_id_id` | Oui (`V7:524-528`) | `SELECT, INSERT, UPDATE` (V7:609) | Désactivation `active` | `[CONFORME]` |
| **V7** | `student_invoices` | Oui (`V7:79`) | `idx_student_invoices_tenant_id`, `_student_year`, `_family`, `_status`, `_due_status` (V12), `_payer_guardian` (V12) | 4 FK V7 + `fk_student_invoices_enrollment` (V12:68-70), `uq_*_number`, `uq_*_id_id` | Oui (`V7:530-534`) | `SELECT, INSERT, UPDATE` (V7:610) | Annulation logique (`CANCELLED`), `version` (V12) | `[CONFORME]` |
| **V7** | `student_invoice_lines` | Oui (`V7:136`) | `idx_student_invoice_lines_tenant_id`, `_invoice` | `fk_*_invoice`, `fk_*_fee_item`, `uq_*_invoice_order` | Oui (`V7:536-540`) | `SELECT, INSERT, UPDATE, DELETE` (V7:618) | Éditable en `DRAFT` uniquement | OK (`P1-020`) |
| **V7** | `payment_schedules` | Oui (`V7:167`) | `idx_payment_schedules_tenant_id`, `_invoice`, `_due_status` | `fk_*_invoice`, `uq_*_invoice_no`, `uq_*_id_id` | Oui (`V7:542-546`) | `SELECT, INSERT, UPDATE, DELETE` (V7:619) | Éditable en `DRAFT` uniquement | OK (`P1-020`) |
| **V7** | `fee_adjustments` | Oui (`V7:197`) | `idx_fee_adjustments_tenant_id`, `_invoice`, `_status` | `fk_*_invoice`, `fk_*_student` | Oui (`V7:548-552`) | `SELECT, INSERT, UPDATE` (V7:611) | Workflow approbation, `version` (V12) | `[CONFORME]` |
| **V7** | `payments` | Oui (`V7:235`) | `idx_payments_tenant_id`, `_invoice`, `_student`, `_paid_at`, `_method_paid_at` (V12), `uq_*_ext_ref` | 4 FK composites, `uq_*_id_id`, `uq_*_idempotency` | Oui (`V7:554-558`) | `SELECT, INSERT` (V7:599) | Immuable (`trg_payments_immutable` V7:497) | `[CONFORME]` |
| **V7** | `payment_allocations` | Oui (`V7:287`) | `idx_payment_allocations_tenant_id`, `_payment`, `_schedule` | `fk_*_payment`, `fk_*_schedule`, `uq_*_payment_schedule` | Oui (`V7:560-564`) | `SELECT, INSERT` (V7:600) | Immuable (`trg_payment_allocations_immutable` V7:501) | `[CONFORME]` |
| **V7** | `receipts` | Oui (`V7:308`) | `idx_receipts_tenant_id`, `_student`, `_issued_at` | 3 FK composites, `uq_*_payment`, `uq_*_number`, `uq_*_token` | Oui (`V7:566-570`) | `SELECT, INSERT` (V7:601) | Immuable (`trg_receipts_immutable` V7:505) | `[CONFORME]` |
| **V7** | `payment_reversal_requests` | Oui (`V7:341`) | `idx_payment_reversal_requests_tenant_id`, `_status`, `uq_*_pending_per_payment` | 3 FK composites, `uq_*_id_id` | Oui (`V7:572-576`) | `SELECT, INSERT, UPDATE` (V7:612) | Workflow approbation, `version` (V12) | `[CONFORME]` |
| **V7** | `payment_reversals` | Oui (`V7:376`) | `idx_payment_reversals_tenant_id`, `_reversed_at` | 4 FK composites, `uq_*_original_payment`, `uq_*_request` | Oui (`V7:578-582`) | `SELECT, INSERT` (V7:602) | Immuable (`trg_payment_reversals_immutable` V7:509) | `[CONFORME]` |
| **V7** | `ledger_entries` | Oui (`V7:407`) | `idx_ledger_entries_tenant_id`, `_student_seq`, `_invoice_seq`, `_ref`, `uq_*_reversal_of` | 3 FK composites (incl. `reversal_of`), `uq_*_id_id`, `uq_*_sequence` | Oui (`V7:584-588`) | `SELECT, INSERT` (V7:603) | Immuable (`trg_ledger_entries_immutable` V7:513) | `[CONFORME]` |
| **V7** | `cash_closings` | Oui (`V7:463`) | `idx_cash_closings_tenant_id`, `_date` | `uq_cash_closings_tenant_date` | Oui (`V7:590-594`) | `SELECT, INSERT, UPDATE` (V7:613) | `SUBMITTED` -> `VALIDATED`, `version` (V12) | `[CONFORME]` |
| **V8** | `teachers` | Oui (`V8:20`) | `idx_teachers_tenant_id`, `_status`, `_name`, `uq_teachers_tenant_user_id` | `fk_teachers_user`, `uq_*_employee_code`, `uq_*_id_id` | Oui (`V8:420-424`) | `SELECT, INSERT, UPDATE` (V8:479) | Archivage logique (`archived_at`, `status`) | `[CONFORME]` |
| **V8** | `teacher_specialties` | Oui (`V8:61`) | `idx_teacher_specialties_tenant_id`, `_teacher`, `_subject`, `uq_*_primary_per_teacher` | `fk_*_teacher`, `fk_*_subject`, `uq_*_teacher_subject` | Oui (`V8:426-430`) | `SELECT, INSERT, UPDATE, DELETE` (V8:489) | Table de liaison | OK (`P1-020`) |
| **V8** | `teacher_assignments` | Oui (`V8:86`) | `idx_teacher_assignments_tenant_id`, `_teacher`, `_class`, `_year`, 2 `uq_*` partiels | 5 FK composites intra-tenant | Oui (`V8:432-436`) | `SELECT, INSERT, UPDATE` (V8:480) | Désactivation `active` | `[CONFORME]` |
| **V8** | `evaluations` | Oui (`V8:136`) | `idx_evaluations_tenant_id`, `_period_class`, `_class_subject`, `_date`, `_teacher` (V12) | 6 FK composites intra-tenant, `uq_*_id_id` | Oui (`V8:438-442`) | `SELECT, INSERT, UPDATE` (V8:481) | Statut `CANCELLED`/`LOCKED` | `[CONFORME]` |
| **V8** | `student_grades` | Oui (`V8:192`) | `idx_student_grades_tenant_id`, `_eval`, `_student` | `fk_*_evaluation`, `fk_*_student`, `uq_*_eval_student` | Oui (`V8:444-448`) | `SELECT, INSERT, UPDATE` (V8:482) | Historisation via évaluation verrouillée | `[CONFORME]` |
| **V8** | `period_report_cards` | Oui (`V8:227`) | `idx_period_report_cards_tenant_id`, `_period_class`, `_student_year` | 4 FK composites, `uq_*_period_student`, `uq_*_id_id` | Oui (`V8:450-454`) | `SELECT, INSERT, UPDATE` (V8:483) | Cycle `DRAFT`->`VALIDATED`->`LOCKED` | `[CONFORME]` |
| **V8** | `period_subject_results` | Oui (`V8:298`) | `idx_period_subject_results_tenant_id`, `_card`, `_subject` | 3 FK composites, `uq_*_card_subject` | Oui (`V8:456-460`) | `SELECT, INSERT, UPDATE, DELETE` (V8:490) | Recalcul en `DRAFT` uniquement | OK (`P1-020`) |
| **V8** | `attendance_sessions` | Oui (`V8:337`) | `idx_attendance_sessions_tenant_id`, `_class_date`, `_period`, `_year_date`, `_teacher_date` (V12) | 5 FK composites, `uq_*_id_id` | Oui (`V8:462-466`) | `SELECT, INSERT, UPDATE` (V8:484) | Statut `CANCELLED`/`LOCKED` | `[CONFORME]` |
| **V8** | `attendance_records` | Oui (`V8:382`) | `idx_attendance_records_tenant_id`, `_session`, `_student` | `fk_*_session`, `fk_*_student`, `uq_*_session_student` | Oui (`V8:468-472`) | `SELECT, INSERT, UPDATE` (V8:485) | Historique conservé | `[CONFORME]` |
| **V9** | `center_session_packs` | Oui (`V9:7`) | `idx_center_session_packs_tenant_id`, `_active`, `_year` | 3 FK composites, `uq_*_code`, `uq_*_id_id` | Oui (`V9:337-341`) | `SELECT, INSERT, UPDATE` (V9:390) | Désactivation `active` | `[CONFORME]` |
| **V9** | `center_groups` | Oui (`V9:47`) | `idx_center_groups_tenant_id`, `_year`, `_status`, `_subject` | 4 FK composites, `uq_*_year_code`, `uq_*_id_id` | Oui (`V9:343-347`) | `SELECT, INSERT, UPDATE` (V9:391) | Archivage logique (`archived_at`, `status`) | `[CONFORME]` |
| **V9** | `center_student_packs` | Oui (`V9:95`) | `idx_center_student_packs_tenant_id`, `_student`, `_group` | 3 FK composites, `uq_*_id_id` | Oui (`V9:349-353`) | `SELECT, INSERT, UPDATE` (V9:392) | Statuts, `version` (V12:174) | `[CONFORME]` |
| **V9** | `center_group_tutors` | Oui (`V9:141`) | `idx_center_group_tutors_tenant_id`, `_group`, `_teacher`, 2 `uq_*` partiels | 3 FK composites, `uq_*_id_id` | Oui (`V9:355-359`) | `SELECT, INSERT, UPDATE` (V9:393) | Désactivation `active` | `[CONFORME]` |
| **V9** | `center_group_enrollments` | Oui (`V9:184`) | `idx_center_group_enrollments_tenant_id`, `_group`, `_student`, `uq_*_active_student` | 3 FK composites, `uq_*_id_id` | Oui (`V9:361-365`) | `SELECT, INSERT, UPDATE` (V9:394) | Statuts `COMPLETED`/`WITHDRAWN`/`CANCELLED` | `[CONFORME]` |
| **V9** | `center_schedule_slots` | Oui (`V9:217`) | `idx_center_schedule_slots_tenant_id`, `_group`, `_teacher`, `_day` | 3 FK composites, `uq_*_id_id` | Oui (`V9:367-371`) | `SELECT, INSERT, UPDATE` (V9:395) | Désactivation `active` | `[CONFORME]` |
| **V9** | `center_sessions` | Oui (`V9:254`) | `idx_center_sessions_tenant_id`, `_group_date`, `_teacher_date`, `_date_status` | 4 FK composites, `uq_*_id_id` | Oui (`V9:373-377`) | `SELECT, INSERT, UPDATE` (V9:396) | Statut `CANCELLED`/`COMPLETED` | `[CONFORME]` |
| **V9** | `center_session_attendances` | Oui (`V9:295`) | `idx_center_session_attendances_tenant_id`, `_session`, `_student`, `_pack` | 4 FK composites, `uq_*_session_student` | Oui (`V9:379-383`) | `SELECT, INSERT, UPDATE` (V9:397) | Historique conservé | `[CONFORME]` |
| **V10** | `audit_logs` | Oui (`V10:15`) | 6 index préfixés `tenant_id` | `tenant_id REFERENCES schools(id)` | Oui (`V10:136-140`) | `SELECT, INSERT` (V10:150) | Immuable (`trg_audit_logs_immutable` V10:126) | `[CONFORME]` (17 catégories alignées via `V15:6-25`) |
| **V10** | `platform_audit_logs` | N/A (audit plateforme SaaS) | 5 index (`occurred`, `category`, `action`, `target_school`, `actor`) + `severity` (V12) | `target_school_id REFERENCES schools(id)` | Oui (`V10:143-147`) : `current_tenant_id() IS NULL` | `SELECT, INSERT` (V10:150) | Immuable (`trg_platform_audit_logs_immutable` V10:130) | `[CONFORME]` (17 catégories alignées via `V15:27-46`) |
| **V11** | `communication_sequences` | Oui (`V11:53`) | `idx_communication_sequences_tenant_id` | `uq_communication_sequences_tenant_year` | Oui (`V11:387-391`) | `SELECT, INSERT, UPDATE` (V11:428) | Compteur atomique | `[CONFORME]` |
| **V11** | `communication_templates` | Oui (`V11:69`) | `idx_communication_templates_tenant_id`, `_channel_active`, `_category_active` | `uq_*_code`, `uq_*_id_id` | Oui (`V11:393-397`) | `SELECT, INSERT, UPDATE` (V11:429) | Désactivation `active` | `[CONFORME]` |
| **V11** | `communication_contact_preferences` | Oui (`V11:110`) | `idx_communication_contact_prefs_tenant_id`, `_type` | `uq_communication_contact_prefs_tenant_target` | Oui (`V11:399-403`) | `SELECT, INSERT, UPDATE` (V11:430) | Historique consentement | `[CONFORME]` |
| **V11** | `communication_campaigns` | Oui (`V11:150`) | 5 index préfixés `tenant_id` + `_scheduled` (V12) | `fk_*_template`, `uq_*_ref`, `uq_*_idempotency`, `uq_*_id_id` | Oui (`V11:405-409`) | `SELECT, INSERT, UPDATE` (V11:431) | Statut `CANCELLED` | `[CONFORME]` |
| **V11** | `communication_deliveries` | Oui (`V11:259`) | 6 index préfixés `tenant_id` + `_queued` (V12) | `fk_*_campaign` + 2 FK V12 (`recipient_user`, `related_student`), `uq_*_id_id`, `uq_*_campaign_recipient_channel` | Oui (`V11:411-415`) | `SELECT, INSERT, UPDATE` (V11:432) | Historique conservé | `[CONFORME]` |
| **V11** | `communication_delivery_events` | Oui (`V11:340`) | `idx_communication_delivery_events_tenant_id`, `_delivery` | `fk_*_delivery`, `uq_*_delivery_seq` | Oui (`V11:417-421`) | `SELECT, INSERT` (V11:436) | Immuable (`trg_communication_delivery_events_immutable` V11:379) | `[CONFORME]` |
| **V13** | `schooldocs_sequences` | Oui (`V13:56`) | `idx_schooldocs_sequences_tenant_id` | `uq_schooldocs_sequences_tenant_year` | Oui (`V13:283-287`) | `SELECT, INSERT, UPDATE` (V13:318) | Compteur atomique | `[CONFORME]` |
| **V13** | `schooldocs_templates` | Oui (`V13:72`) | `idx_schooldocs_templates_tenant_id`, `_type_active`, `_active` | `uq_*_code`, `uq_*_id_id` | Oui (`V13:289-293`) | `SELECT, INSERT, UPDATE` (V13:319) | Désactivation `active`, `version` | `[CONFORME]` |
| **V13** | `schooldocs_documents` | Oui (`V13:115`) | 9 index préfixés `tenant_id` (incl. `uq_*_original_edition`) | 7 FK composites, 5 `uq_*` composites | Oui (`V13:295-299`) | `SELECT, INSERT, UPDATE` (V13:320) | Révocation logique (`REVOKED`), `version` | `[CONFORME]` |
| **V13** | `schooldocs_document_contents` | Oui (`V13:223`) | `idx_schooldocs_contents_tenant_id` | `fk_*_document`, `uq_*_document`, `uq_*_storage_key` | Oui (`V13:301-305`) | `SELECT, INSERT` (V13:324) | Immuable (`trg_schooldocs_document_contents_immutable` V13:244) | `[CONFORME]` |
| **V13** | `schooldocs_document_events` | Oui (`V13:251`) | `idx_schooldocs_events_tenant_id`, `_document` | 2 FK composites, `uq_*_doc_seq` | Oui (`V13:307-311`) | `SELECT, INSERT` (V13:325) | Immuable (`trg_schooldocs_document_events_immutable` V13:275) | `[CONFORME]` |
| **V14** | `schoolimport_sequences` | Oui (`V14:58`) | `idx_schoolimport_sequences_tenant_id` | `uq_schoolimport_sequences_tenant_year` | Oui (`V14:305-309`) | `SELECT, INSERT, UPDATE` (V14:340) | Compteur atomique | `[CONFORME]` |
| **V14** | `schoolimport_jobs` | Oui (`V14:74`) | 6 index préfixés `tenant_id` (incl. `uq_*_exec_idempotency`) | 3 FK composites, 4 `uq_*` composites | Oui (`V14:311-315`) | `SELECT, INSERT, UPDATE` (V14:341) | Annulation logique (`CANCELLED`), `version` | `[CONFORME]` |
| **V14** | `schoolimport_job_files` | Oui (`V14:189`) | `idx_schoolimport_files_tenant_id` | `fk_*_job`, `uq_*_job`, `uq_*_storage_key` | Oui (`V14:317-321`) | `SELECT, INSERT` (V14:346) | Immuable (`trg_schoolimport_job_files_immutable` V14:212) | `[CONFORME]` |
| **V14** | `schoolimport_job_rows` | Oui (`V14:219`) | `idx_schoolimport_rows_tenant_id`, `_job_rownum`, `_job_status` | `fk_*_job`, `uq_*_job_rownum` | Oui (`V14:323-327`) | `SELECT, INSERT, UPDATE` (V14:342) | Historique lot, `version` | `[CONFORME]` |
| **V14** | `schoolimport_job_events` | Oui (`V14:268`) | `idx_schoolimport_events_tenant_id`, `_job` | `fk_*_job`, `uq_*_job_seq` | Oui (`V14:329-333`) | `SELECT, INSERT` (V14:347) | Immuable (`trg_schoolimport_job_events_immutable` V14:297) | `[CONFORME]` |
| **V15** | `reporting_sequences` | Oui (`V15:60`) | `idx_reporting_sequences_tenant_id` | `uq_reporting_sequences_tenant_year` | Oui (`V15:218-222`) | `SELECT, INSERT, UPDATE` (V15:247) | Compteur atomique | `[CONFORME]` |
| **V15** | `reporting_snapshots` | Oui (`V15:76`) | 6 index préfixés `tenant_id` | 4 FK composites, 4 `uq_*` composites | Oui (`V15:224-228`) | `SELECT, INSERT, UPDATE` (V15:248) | Archivage logique (`ARCHIVED`), `version` | `[CONFORME]` |
| **V15** | `reporting_snapshot_contents` | Oui (`V15:159`) | `idx_reporting_contents_tenant_id` | `fk_*_snapshot`, `uq_*_snapshot`, `uq_*_storage_key` | Oui (`V15:230-234`) | `SELECT, INSERT` (V15:252) | Immuable (`trg_reporting_snapshot_contents_immutable` V15:182) | `[CONFORME]` |
| **V15** | `reporting_snapshot_events` | Oui (`V15:189`) | `idx_reporting_events_tenant_id`, `_snapshot` | `fk_*_snapshot`, `uq_*_snapshot_seq` | Oui (`V15:236-240`) | `SELECT, INSERT` (V15:253) | Immuable (`trg_reporting_snapshot_events_immutable` V15:210) | `[CONFORME]` |

### Note d'alignement Schéma SQL <-> Entités JPA (Passe 1)
- **Alignement 100 % confirmé** sur toutes les entités des modules `identity` (`User`, `PlatformAdmin`, `UserRole`, `Role`, `RefreshToken`, `PlatformRefreshToken`), `subscription` (`Subscription`, `SubscriptionPlan`, `SubscriptionEvent`), `school` (`School`) et `audit` (`AuditLog`, `PlatformAuditLog`), y compris l'extension des contraintes `chk_audit_logs_category` / `chk_platform_audit_logs_category` dans `V15:6-46` face aux 17 valeurs de `AuditCategory`.
- **Écarts relevés** :
  1. Absence de colonne `version` (et de `@Version`) sur `subscriptions`, `subscription_plans` (`P1-003`) et `schools` (`P1-027`).
  2. Index `idx_subscriptions_status_ends_at` (`V12:188-189`) non préfixé par `tenant_id` (`P1-016`).

---

## 4. Contrôle Quality Gate (`.agents/rules/quality-gate.md` — Périmètre Passe 1)

| Section Quality Gate | Statut | Bilan vérifié sur le périmètre Passe 1 (`shared`, `identity`, `subscription`, `school`, `audit`, `application*.yml`, `V1`..`V15`) |
|---|---|---|
| **1. Sécurité & Multi-Tenant (Bloquant)** | **KO** | **OK** : `TenantContext` exclusif depuis le JWT + nettoyage `finally` ; zéro secret ni `application-test.yml` dans `src/main/resources/` ; 69/69 tables métier avec `tenant_id`, index, FKs/UNIQUEs composites (après `V12`), `ENABLE`+`FORCE RLS` fail-closed et `ecole_app` moindre privilège ; 15/15 tables immuables protégées par trigger ; `Argon2id` + `AES-256-GCM` + TOTP anti-rejeu + rotation refresh token + CSRF.<br>**KO** : `UserAccountService` sans `@PreAuthorize` (`P1-001`) ; `SubscriptionPlanService`, `SubscriptionService` et `SchoolService` sans `@PreAuthorize` complet (`P1-004`) ; CORS/CSP absents (`P1-008`) ; rate limiting HTTP `/api/v1/auth/**` absent (`P1-009`) ; filtrage PII incomplet e-mails/téléphones/adresses dans `AuditMetadataSanitizer` (`P1-014`). |
| **2. Intégrité, Concurrence & Cohérence (Bloquant)** | **KO** | **OK** : `@Lock(PESSIMISTIC_WRITE)` sur rotation refresh tokens ; `@Valid` et types boxés `@NotNull` sur les DTO ; montants FCFA en `long`/`BIGINT` ; `Clock` injecté dans les services métier.<br>**KO** : `UserAccountService` sans `SubscriptionAccessPort` ni quota `max_users` (`P1-001`) ; `TenantSchoolService` sans `SubscriptionAccessPort` (`P1-002`) ; `Subscription` et `SubscriptionPlan` sans `@Version` / `@Lock(PESSIMISTIC_WRITE)` et calcul `MAX(sequenceNo)+1` non verrouillé (`P1-003`) ; `Instant.now()` dans callbacks JPA/handlers (`P1-022`). |
| **3. Performance & Scalabilité (Majeur)** | **KO** | **OK** : `open-in-view=false`, batch `findByUserIdIn` / `findAllById`, agrégations `CriteriaBuilder` `GROUP BY`, pagination bornée à 100.<br>**KO** : HikariCP non configuré (`P1-005`) ; probes Actuator et métriques absentes (`P1-006`) ; graceful shutdown & timeouts absents (`P1-007`) ; transactions imbriquées `REQUIRES_NEW` dans `PlatformSchoolService` (`P1-015`) ; `idx_subscriptions_status_ends_at` sans `tenant_id` (`P1-016`). |
| **4. Architecture & Maintenabilité (Bloquant)** | **KO** | **OK** : domaine pur sans `org.springframework.*` (64/64 fichiers) ; injection par constructeur uniquement ; zéro `@Entity` exposée ; `GlobalProblemHandler` RFC 9457 en français.<br>**KO** : perte de `requestId` sur 401/403 CSRF/JWT (`P1-010`) ; fuite potentielle `ex.getMessage()` sur `IllegalArgument`/`IllegalState`, omission `MaxUploadSizeExceededException`, absence de logs `WARN` 401/403 (`P1-011`) ; absence de log `ERROR` dans `AuditLogService` (`P1-012`) ; logs structurés MDC absents (`P1-013`). |
| **5. Preuve obligatoire (Bloquant / Majeur)** | **KO** | **OK** : `ArchitectureTest`, `MultiTenantIsolationTest`, `EcoleSaasApplicationTest`, `AuthIntegrationTest`, `SubscriptionIntegrationTest`, `SchoolAcademicIntegrationTest`, `AuditIntegrationTest` et 11 tests unitaires solides.<br>**KO** : absence de tests sur `GlobalProblemHandler` (`requestId`, `500`, JSON malformé), JWT `exp`/`iss`/`alg`, verrouillage `PlatformAdmin` (`P1-017`), concurrence `Subscription` (`P1-003`), `REVOKE DELETE` sur `schools`/`subscriptions`/`subscription_plans` (`P1-016`), et `SubscriptionAccessPort` sur `/api/v1/school` (`P1-002`). |
