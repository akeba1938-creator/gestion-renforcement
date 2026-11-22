# Audit Backend — Passe 3 : Finance, Communication, Documents, Imports & Reporting

- **Date d'audit** : 2026-11-21
- **Mode d'audit** : Analyse statique en lecture seule — zéro exécution Maven (`mvnw`), zéro modification de code applicatif, SQL ou configuration, zéro `git commit`.
- **Référentiel** : `.agents/workflows/audit-pass-3-finance-flux.md`, `.agents/rules/quality-gate.md`, `.agents/rules/finance.md`, `.agents/rules/security.md`, `.agents/rules/database.md`, `.agents/rules/backend.md`, `.agents/rules/testing.md`, ainsi que les ADR `0003`, `0006`, `0007`, `0008` et `0009`.
- **Périmètre contrôlé (100 % relu directement)** :
  - **367 fichiers Java principaux** (`backend/src/main/java/com/ecole/saas/`) : `finance` (110 fichiers), `communication` (91 fichiers), `schooldocs` (58 fichiers), `schoolimport` (61 fichiers), `reporting` (47 fichiers), ainsi que les contrôles transverses sur `shared/api/GlobalProblemHandler.java` et `subscription/application/SubscriptionGuardService.java`.
  - **6 migrations Flyway** (`backend/src/main/resources/db/migration/`) : `V7__finance_module.sql`, `V11__communication_module.sql`, `V12__security_and_integrity_hardening.sql`, `V13__schooldocs_module.sql`, `V14__schoolimport_module.sql`, `V15__reporting_module.sql`.
  - **3 fichiers de configuration YAML** : `backend/src/main/resources/application.yml`, `application-dev.yml`, `backend/src/test/resources/application-test.yml`.
  - **5 ADR** : `docs/adr/0003-registre-financier.md`, `0006-module-communication-passerelle-consentement-et-idempotence.md`, `0007-*.md`, `0008-*.md`, `0009-*.md`.
  - **20 fichiers de tests** (`backend/src/test/java/com/ecole/saas/`) : `FinanceIntegrationTest.java`, `CommunicationIntegrationTest.java`, `SchoolDocsIntegrationTest.java`, `SchoolImportIntegrationTest.java`, `ReportingIntegrationTest.java`, `MultiTenantIsolationTest.java`, `ArchitectureTest.java`, plus 13 fichiers de tests unitaires (`finance` : 3, `communication` : 3, `schooldocs` : 2, `schoolimport` : 2, `reporting` : 3).

---

## 1. Synthèse exécutive

### 1.1 Tableau récapitulatif des constats

| Module | `[BLOQUANT]` | `[MAJEUR]` | `[MINEUR]` | Total non-conformités | `[CONFORME]` vérifiés | Plage d'identifiants |
|---|---:|---:|---:|---:|---:|---|
| `finance` | 3 | 3 | 2 | **8** | **8** | `P3-001` .. `P3-008` |
| `communication` | 4 | 2 | 2 | **8** | **7** | `P3-009` .. `P3-016` |
| `schooldocs` | 0 | 2 | 2 | **4** | **8** | `P3-017` .. `P3-020` |
| `schoolimport` | 1 | 2 | 2 | **5** | **8** | `P3-021` .. `P3-025` |
| `reporting` | 0 | 0 | 5 | **5** | **8** | `P3-026` .. `P3-030` |
| **Total global Passe 3** | **8** | **9** | **13** | **30** | **39** | **`P3-001` .. `P3-030`** |

> **Cohérence des décomptes** : chaque ligne du tableau ci-dessus correspond exactement au nombre de constats détaillés et de lignes `[CONFORME]` recensées dans la Section 2 par module.

### 1.2 Synthèse des bloqueurs de mise en production (Passe 3)

Les fondations architecturales, comptables et multi-tenant des cinq modules de la Passe 3 sont globalement rigoureuses : **aucun** `float` ou `double` n'est utilisé sur les montants FCFA (100 % `long`/`BIGINT`), les cinq tables comptables de `finance` ainsi que les tables d'événements/contenus binaires de `communication`, `schooldocs`, `schoolimport` et `reporting` sont protégées en ajout seul (triggers SQL `BEFORE UPDATE OR DELETE`, privilèges `ecole_app` restreints à `SELECT, INSERT`, `@PreUpdate`/`@PreRemove`), la RLS fail-closed (`ENABLE` + `FORCE ROW LEVEL SECURITY`) couvre l'ensemble des tables métier, les domaines sont 100 % Java pur sans import Spring, et le découpage hors transaction des passerelles de `communication` est exemplaire.

Néanmoins, **la Passe 3 échoue au Quality Gate** en raison de **8 constats `[BLOQUANT]`** et **9 constats `[MAJEUR]`** devant impérativement être corrigés avant mise en production :

1. **Conformité Mobile Money et passerelles externes (`P3-001`, `P3-011`)** : aucun client HTTP ni webhook serveur à signature HMAC vérifiée en temps constant n'existe pour Mobile Money (`PaymentConfirmationSource.VERIFIED_WEBHOOK` est inutilisé alors que `PaymentService.recordPayment` autorise les encaissements `MOBILE_MONEY_*` en `MANUAL_DESK`) ni pour les opérateurs SMS/WhatsApp/Email de `communication` (stubs locaux uniquement, zéro timeout HTTP/SMTP configuré dans `application.yml`).
2. **Contournement du consentement marketing & DTO booléens sans `@NotNull` (`P3-009`, `P3-010`, `P3-003`)** : dans `ConsentPolicy.java:41-42`, la condition `applyMarketingOptOut && respectMarketingOptOut` annule l'effet de `!category.isMandatoryTransactional()` lorsque `respectMarketingOptOut = false`, permettant aux campagnes `GENERAL`, `CENTER_SESSION` et `CUSTOM` d'ignorer `marketingOptIn = false`. De plus, `UpsertContactPreferenceRequest` et `UpdateMyPreferenceRequest` omettent `@NotNull` sur les 5 booléens de consentement (convertis silencieusement en `true` par `!Boolean.FALSE.equals(...)`), tout comme `CreateFeeItemRequest`/`UpdateFeeItemRequest` sur `Boolean mandatory` (converti silencieusement en `false`).
3. **Faille Anti-CSV-Injection dans `schoolimport` (`P3-021`)** : `ImportFileSecurityPolicy.neutralizeCsvFormula` ne contrôle que `raw.charAt(0)` et laisse passer des formules précédées d'espaces ou de sauts de ligne (ex. `"  =CMD()"`) dans les exports CSV d'erreurs et modèles, contrairement à `CsvFormulaSanitizer` de `reporting`.
4. **Gardes d'abonnement, quotas et autorisations inter-modules (`P3-002`, `P3-012`, `P3-017`)** : `InAppInboxService.markAsRead` et `markAllAsRead` exécutent des écritures DB sous `requireReadAccess()` au lieu de `requireWriteAccess()` ; `FinanceAccountQueryService` (`FinanceAccountPort`) et deux méthodes de `CommunicationPortService` n'appellent pas `SubscriptionAccessPort` (et `FinanceAccountQueryService` n'a pas `@PreAuthorize`) ; `SchoolDocsSubscriptionGuard` ne passe pas l'usage agrégé `subscriptionAccessPort.currentUsage(QuotaResource.STORAGE_MB)`, ignorant le stockage consommé par `schoolimport` et `reporting`.
5. **Résilience multipart, reprise d'import et scalabilité SQL (`P3-004`, `P3-014`, `P3-022`, `P3-023`)** : `FinanceAccountQueryService.getFinancialReportingOverview` charge toutes les factures, échéances et écritures en mémoire JVM (`findAll()`/`findByAcademicYearId`) ; `application.yml` conserve la limite multipart par défaut à `1MB` (bloquant les imports valides de 1 à 5 Mo) sans handler `MaxUploadSizeExceededException` dans `GlobalProblemHandler` ; `SchoolImportJobService.executeJob` laisse un lot bloqué à vie en `PROCESSING` si la Phase 2 échoue.

---

## 2. Rapport détaillé par module

### 2.1 Module `finance` (110 fichiers Java + `V7` & `V12` + `ADR 0003`)

#### A. Disponibilité & Résilience
- **Timeouts passerelles & clients externes** : aucun client HTTP externe ni webhook Mobile Money (Wave, Orange Money, Free Money) n'est implémenté dans `finance` ni configuré dans `application.yml` (`P3-001`). Aucun appel réseau n'est donc exécuté à l'intérieur d'une transaction `@Transactional`, mais l'obligation métier de confirmation serveur signée (`.agents/rules/finance.md:10`) n'est pas réalisée.
- **Limites mémoire, pagination & ressources** : `FinancePagination.java:11, 33-65` borne strictement `size` à `1..100`, `page >= 0` et filtre les champs de tri via liste blanche. En revanche, `FinanceAccountQueryService.getFinancialReportingOverview` (`FinanceAccountQueryService.java:217-260`) charge en mémoire toutes les factures et échéances actives de l'établissement (`P3-004`). Aucun flux fichier/stream non fermé n'existe dans ce module.

#### B. Sécurité, Intégrité Financière & Flux Fichiers
- **Idempotence** : `V7__finance_module.sql:260` impose `UNIQUE (tenant_id, idempotency_key)` et `V7:275-277` impose l'index unique partiel `(tenant_id, payment_method, external_reference)`. `PaymentService.recordPayment` (`PaymentService.java:148-188`) normalise la clé (8–128 car.), verrouille `StudentInvoice` (`@Lock(PESSIMISTIC_WRITE)` dans `StudentInvoiceRepository.java:18-20`), recontrôle l'idempotence sous verrou et compare l'ensemble des champs via `Payment.matchesIdempotentRequest` (`Payment.java:110-127`).
- **Immutabilité & zéro `float`/`double`** : 100 % des montants FCFA dans `V7` (`BIGINT`) et les 110 fichiers Java (`long`/`Long`, `MoneyMath.java:9-90` avec `Math.addExact`/`subtractExact`/`multiplyExact`) sont strictement entiers. Les 5 tables en ajout seul (`payments`, `payment_allocations`, `receipts`, `payment_reversals`, `ledger_entries`) sont verrouillées par trigger SQL `BEFORE UPDATE OR DELETE`, privilèges `ecole_app` `SELECT, INSERT`, `@PreUpdate`/`@PreRemove` et dépôts Spring Data sans `delete`.
- **Reçus séquentiels, QR opaque, Maker-Checker & clôture de caisse** : `FinanceSequenceRepository.java:23-41` garantit une numérotation atomique sans trou (`INSERT ... ON CONFLICT DO UPDATE ... RETURNING`), `OpaqueTokenGenerator.java:10-23` génère un jeton opaque `SecureRandom` de 32 octets sans PII, `CashClosing` calcule les montants attendus en SQL et impose un motif si écart non nul, et toute remise/contre-passation/clôture interdit l'auto-validation (`FeeAdjustment.java:158-161`, `PaymentReversalRequest.java:90-93`, `CashClosing.java:124-127`).
- **Manquements bloquants** : confirmation Mobile Money autorisée en `MANUAL_DESK` sans webhook signé (`P3-001`), `FinanceAccountQueryService` dépourvu de `@PreAuthorize` et de `SubscriptionAccessPort` (`P3-002`), et `Boolean mandatory` sans `@NotNull` dans les DTOs de frais (`P3-003`).
- **Anti-CSV-Injection / XXE / SHA-256** : non applicable dans `finance` (aucun export CSV/XLSX ni parseur de fichier entrant).
- **Architecture & RLS** : domaine 100 % Java pur, injection par constructeur uniquement, aucune `@Entity` exposée, `Clock` injecté + fuseau `Africa/Dakar` (`FinanceSchoolContext.java:17-50`), 13 tables `V7` avec `ENABLE` + `FORCE ROW LEVEL SECURITY` fail-closed, FKs/UNIQUE composites et `@Version` (`V12:68-70, 162-172`).

#### C. Performance & Scalabilité
- **Agrégations SQL & batch** : `CashClosingService`, `FeeAdjustmentRepository`, `PaymentRepository`, `PaymentReversalRepository` et `LedgerEntryRepository` (au niveau établissement) agrègent en SQL/JPQL. `PaymentService.listPayments` précharge en batch (`findByPaymentIdIn`, `findByOriginalPaymentIdIn`) ; `StudentInvoiceService` utilise `findByIdIn` et `saveAll`.
- **Défauts relevés** : `FinanceAccountQueryService` charge les entités en mémoire pour `getFinancialReportingOverview` et `findStudentLedgerBalanceXof` (`P3-004`), et deux index datés manquent sur `fee_adjustments` et `payment_reversals` (`P3-007`).

#### D. Audit, Gestion d'Erreurs & Preuves de tests
- **Audit (`audit_logs`)** : factures, paiements, contre-passations, remises/bourses, clôtures et création/désactivation de frais sont journalisés sans PII ; seuls `updateFeeItem` et `activateFeeItem` omettent l'audit (`P3-005`).
- **Erreurs & Logs** : `BusinessException` traduite en `ProblemDetail` RFC 9457 en français avec `requestId` (`GlobalProblemHandler.java`), sans PII dans les logs.
- **Preuves de tests** : `FinanceIntegrationTest` (1 460 lignes) + 3 tests unitaires domaine (375 lignes) couvrent les flux principaux, l'immutabilité SQL, le Maker-Checker, la concurrence paiement et la RLS sur les 13 tables ; des lacunes demeurent sur `finance/application` et certains cas limites (`P3-006`).

#### Constats du module `finance` (`P3-001` .. `P3-008`)

##### `P3-001` — `[BLOQUANT]` — Absence de webhook serveur à signature vérifiée pour Mobile Money et confirmation autorisée au guichet (`MANUAL_DESK`)
- **Identifiant** : `P3-001`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `finance` — Axes A & B
- **Localisation** : `backend/src/main/java/com/ecole/saas/finance/domain/PaymentMethod.java:7-9`, `backend/src/main/java/com/ecole/saas/finance/domain/PaymentConfirmationSource.java:3-6`, `backend/src/main/java/com/ecole/saas/finance/application/PaymentService.java:133-212`, `backend/src/main/java/com/ecole/saas/finance/api/PaymentController.java:38-64`, `backend/src/main/resources/application.yml`
- **Constat factuel** : `PaymentConfirmationSource.VERIFIED_WEBHOOK` est défini dans l'énumération (`PaymentConfirmationSource.java:5`) et dans la contrainte SQL `chk_payments_confirmation_source` (`V7__finance_module.sql:272`), mais n'est référencé nulle part dans le code applicatif. Aucun contrôleur webhook, aucun client HTTP avec timeouts configurés ni aucun vérificateur de signature HMAC à temps constant n'est implémenté pour `MOBILE_MONEY_WAVE`, `MOBILE_MONEY_OM` et `MOBILE_MONEY_FREE`. À l'inverse, `PaymentService.recordPayment` (`PaymentService.java:208`) force `PaymentConfirmationSource.MANUAL_DESK` et permet d'encaisser du Mobile Money manuellement.
- **Risque** : Violation directe de `.agents/rules/finance.md:10` (« confirmation uniquement par notification serveur à signature vérifiée ») ; exposition à la fraude interne par saisie manuelle d'encaissements Mobile Money fictifs et impossibilité de recevoir les confirmations serveurs des opérateurs.
- **Correction précise** :
  1. Ajouter dans `application.yml` les propriétés bornées par opérateur sous `app.finance.mobile-money.<provider>` (`webhook-secret` via variable d'environnement, `connect-timeout-ms`, `read-timeout-ms`, `signature-tolerance-seconds`) et une classe `@ConfigurationProperties`.
  2. Implémenter un endpoint dédié `POST /api/v1/finance/webhooks/mobile-money/{provider}` vérifiant la signature HMAC en temps constant (`MessageDigest.isEqual`), la fenêtre d'horodatage anti-rejeu, l'unicité `external_reference` et l'idempotence `(tenant_id, idempotency_key)`, tout appel réseau de vérification éventuel devant s'exécuter **hors** `@Transactional`, puis enregistrer le paiement avec `PaymentConfirmationSource.VERIFIED_WEBHOOK`.
  3. Rejeter `MOBILE_MONEY_*` dans `PaymentService.recordPayment` sous `MANUAL_DESK` (avec `FinanceValidationException`).
  4. **Test à ajouter** : tests d'intégration Testcontainers (+ WireMock si interrogation de statut opérateur) couvrant signature valide (`VERIFIED_WEBHOOK`), signature absente/invalide (`401`/`403`), rejeu idempotent webhook, timeout HTTP borné et rejet de `MOBILE_MONEY_*` en guichet `MANUAL_DESK`.

##### `P3-002` — `[BLOQUANT]` — Absence de `@PreAuthorize` et de garde `SubscriptionAccessPort` sur `FinanceAccountQueryService`
- **Identifiant** : `P3-002`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `finance` — Axe B
- **Localisation** : `backend/src/main/java/com/ecole/saas/finance/application/FinanceAccountQueryService.java:54-56, 96-210` (croisé avec `backend/src/main/java/com/ecole/saas/reporting/application/ReportingQueryService.java:109-120, 417-418, 569-571`, `ReportingAccessScopeGuard.java:38-45, 74-112` et `ReportingSubscriptionGuard.java:34-36`)
- **Constat factuel** : Le service `@Service` `FinanceAccountQueryService` (implémentation du port public `FinanceAccountPort`) ne porte aucune annotation `@PreAuthorize` et n'injecte ni n'appelle `FinanceSubscriptionGuard.requireReadAccess()` (`SubscriptionAccessPort`) sur ses 5 méthodes publiques (`getStudentFinancialSummary`, `getStudentFinancialSummariesByIds`, `getFamilyFinancialSummary`, `findStudentLedgerBalanceXof`, `getFinancialReportingOverview`). Par contraste croisé, son principal consommateur `reporting` applique bien `@PreAuthorize(ReportingRoles.*)` (`ReportingQueryService.java:109`), `ReportingSubscriptionGuard.assertReadAllowed()` (`ReportingQueryService.java:118`) et `ReportingAccessScopeGuard` (`canSeeStudentFinance = false` pour `CENSOR`, `TEACHER`, `TUTOR`) avant d'appeler `financeAccountPort`.
- **Risque** : Bien que les routes `reporting` et `finance` (`/students/*/summary`, `/families/*/summary` via `LedgerQueryService`) soient actuellement gardées côté appelant, l'absence de défense en profondeur sur le port `FinanceAccountQueryService` expose toute nouvelle intégration ou appel direct interne à lire des soldes financiers sur un abonnement `SUSPENDED`/`CANCELLED` ou sans contrôle de rôle (`quality-gate.md` §1 & §2).
- **Correction précise** : Injecter `FinanceSubscriptionGuard` dans `FinanceAccountQueryService` et appeler `subscriptionGuard.requireReadAccess()` en entrée de chacune des 5 méthodes publiques de `FinanceAccountPort` ; ajouter `@PreAuthorize` (ou documenter/restreindre explicitement l'accès autorisé sur les méthodes du service applicatif tout en préservant l'accès scopé `reporting` pour `PARENT`/`STUDENT` sur `getStudentFinancialSummary`).
- **Test à ajouter** : test d'intégration vérifiant que `FinanceAccountPort` lève `FinanceSubscriptionReadDeniedException` lorsque l'abonnement est `SUSPENDED` ou absent.

##### `P3-003` — `[BLOQUANT]` — Champ obligatoire `Boolean mandatory` non annoté `@NotNull` dans `CreateFeeItemRequest` et `UpdateFeeItemRequest`
- **Identifiant** : `P3-003`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `finance` — Axe B
- **Localisation** : `backend/src/main/java/com/ecole/saas/finance/api/CreateFeeItemRequest.java:25`, `backend/src/main/java/com/ecole/saas/finance/api/UpdateFeeItemRequest.java:21`, `backend/src/main/java/com/ecole/saas/finance/api/FeeItemController.java:74, 92`
- **Constat factuel** : Le champ `Boolean mandatory` est dépourvu de `@NotNull` dans `CreateFeeItemRequest` (`ligne 25`) et `UpdateFeeItemRequest` (`ligne 21`). `FeeItemController` (`lignes 74 et 92`) utilise `Boolean.TRUE.equals(request.mandatory())`, convertissant silencieusement `null` en `false` (alors qu'en base `V7__finance_module.sql:40` définit `mandatory BOOLEAN NOT NULL DEFAULT TRUE`).
- **Risque** : Non-respect de `quality-gate.md` §2 (« champs obligatoires en types boxés (`Long`, `Integer`, `Boolean`) annotés `@NotNull` ») ; l'omission involontaire du champ dans la requête transforme silencieusement un frais obligatoire en frais facultatif.
- **Correction précise** : Annoter `Boolean mandatory` avec `@NotNull(message = "Le caractère obligatoire du frais est requis.")` dans `CreateFeeItemRequest.java:25` et `UpdateFeeItemRequest.java:21`, et transmettre `request.mandatory()` directement au service.
- **Test à ajouter** : test MockMvc vérifiant le rejet `400 Bad Request` (`urn:ecole:problem:validation-error`) lorsque `mandatory` est `null` ou absent sur `POST` et `PUT /api/v1/finance/fee-items`.

##### `P3-004` — `[MAJEUR]` — Chargement en mémoire (`findAll()` / `findByAcademicYearId` / `findByInvoiceIdIn`) dans `FinanceAccountQueryService`
- **Identifiant** : `P3-004`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `finance` — Axe C
- **Localisation** : `backend/src/main/java/com/ecole/saas/finance/application/FinanceAccountQueryService.java:116-121, 199-201, 217-260`
- **Constat factuel** :
  1. `getFinancialReportingOverview` (`lignes 217-249`) exécute `invoiceRepository.findByAcademicYearId(academicYearId)` ou `invoiceRepository.findAll()` pour calculer en mémoire JVM les compteurs par statut et les sommes `gross`/`discount`/`net`/`paid`/`balance`.
  2. Aux `lignes 253-260`, il charge toutes les entités `PaymentSchedule` via `scheduleRepository.findByInvoiceIdIn(activeInvoiceIds)` pour filtrer `isOverdueAt(today)` et sommer les échéances échues en Java.
  3. Aux `lignes 116-121` et `199-201`, toutes les lignes `LedgerEntry` de l'élève sont chargées en mémoire pour sommer `signedBalanceDeltaXof()`, alors qu'une requête `SUM` JPQL existe déjà au niveau établissement dans `LedgerEntryRepository.java:33-56`.
- **Risque** : Consommation mémoire O(N), latence dégradée sur les tableaux de bord `reporting`/`finance`, et dépassement potentiel de la limite PostgreSQL/JDBC des clauses `IN (:activeInvoiceIds)` (> 32 767 factures actives).
- **Correction précise** : Remplacer ces boucles Java par des requêtes d'agrégation SQL/JPQL dans `StudentInvoiceRepository` (comptage groupé par `status` + `COALESCE(SUM(...), 0L)`), `PaymentScheduleRepository` (`COUNT` + `COALESCE(SUM(s.amountXof - s.paidAmountXof), 0L)` joint à `StudentInvoice` pour `status IN ('ISSUED','PARTIALLY_PAID','PAID')` et `dueDate < :today`), et `LedgerEntryRepository` (`sumSignedBalanceByStudentId` et `GROUP BY studentId` pour `aggregateStudentsBatch`).
- **Test à ajouter** : test d'intégration dans `FinanceIntegrationTest` vérifiant l'exactitude des agrégats SQL multi-statuts (brouillons, annulées, échues vs non échues) par rapport aux écritures comptables.

##### `P3-005` — `[MAJEUR]` — Absence de trace `audit_logs` lors de `updateFeeItem` et `activateFeeItem`
- **Identifiant** : `P3-005`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `finance` — Axe D
- **Localisation** : `backend/src/main/java/com/ecole/saas/finance/application/FeeItemService.java:150-181`
- **Constat factuel** : `createFeeItem` (`lignes 138-144`) et `deactivateFeeItem` (`lignes 192-199`) enregistrent un événement dans `auditLogPort`, mais `updateFeeItem` (`lignes 150-170`, qui modifie `amountXof`, `label`, `category`, `frequency`) et `activateFeeItem` (`lignes 174-181`) n'émettent aucune trace d'audit.
- **Risque** : Une modification du tarif catalogue (`amount_xof`) reste invisible dans le journal `audit_logs` (`quality-gate.md` / Axe D).
- **Correction précise** : Appeler `auditLogPort.recordTenantEvent(AuditRecordCommand.success(...))` dans `updateFeeItem` et `activateFeeItem` en ne consignant que des métadonnées non sensibles (`feeItemId`, `code`, `amountXof`, `active`).
- **Test à ajouter** : assertion sur `/api/v1/audit/logs?category=FINANCE` dans `FinanceIntegrationTest` après `PUT /api/v1/finance/fee-items/{id}` et `POST .../activate`.

##### `P3-006` — `[MAJEUR]` — Couverture unitaire inexistante sur `finance/application` et lacunes sur `getFinancialReportingOverview`, `updateDraftInvoice` et la concurrence optimiste
- **Identifiant** : `P3-006`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `finance` — Axe D
- **Localisation** : `backend/src/test/java/com/ecole/saas/finance/domain/` (3 fichiers, 375 lignes), `backend/src/test/java/com/ecole/saas/FinanceIntegrationTest.java`
- **Constat factuel** : Aucun test unitaire n'existe pour les 13 services de `finance/application` ni de test `@WebMvcTest` sur `finance/api` ; les 3 tests unitaires ne couvrent que `finance/domain`. `FinanceIntegrationTest` ne teste ni `FinanceAccountPort.getFinancialReportingOverview`, ni `updateDraftInvoice`, ni la concurrence `@Version` sur `approveAdjustment` / `approveReversal` / `submitCashClosing`.
- **Risque** : Couverture réelle inférieure à l'objectif ≥ 85 % fixé par `.agents/rules/testing.md:6` sur `finance`, et régression possible sur les agrégations reporting et conflits optimistes.
- **Correction précise** : Ajouter des tests unitaires JUnit 5/Mockito sur les services applicatifs critiques (`PaymentService`, `StudentInvoiceService`, `FeeAdjustmentService`, `PaymentReversalService`, `CashClosingService`, `FinanceAccountQueryService`) ainsi que des tests d'intégration couvrant `getFinancialReportingOverview`, `updateDraftInvoice` et les conflits concurrents `@Version`.

##### `P3-007` — `[MINEUR]` — Index SQL composites manquants sur `fee_adjustments` et `payment_reversals`
- **Identifiant** : `P3-007`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `finance` — Axe C
- **Localisation** : `backend/src/main/resources/db/migration/V7__finance_module.sql:228-230, 401-402`, `backend/src/main/java/com/ecole/saas/finance/infrastructure/FeeAdjustmentRepository.java:20-50`, `PaymentReversalRepository.java:38-79`
- **Constat factuel** : `FeeAdjustmentRepository` agrège par `requested_at` sans index `(tenant_id, requested_at)` sur `fee_adjustments`, et `PaymentReversalRepository.aggregateByPaymentMethodAndReversedAtRange` filtre par `(payment_method, reversed_at)` sans index `(tenant_id, payment_method, reversed_at DESC)` sur `payment_reversals` (contrairement à `payments` indexé en `V12:197`).
- **Risque** : Parcours séquentiels sur historiques volumineux lors des clôtures et synthèses reporting.
- **Correction précise** : Ajouter une nouvelle migration `V16__finance_reporting_indexes.sql` créant ces deux index préfixés par `tenant_id` (`CREATE INDEX CONCURRENTLY` ou migration standard selon politique Flyway hors transaction si applicable). **Test à ajouter** : vérification du plan/migration dans les tests Testcontainers existants.

##### `P3-008` — `[MINEUR]` — Absence d'endpoints self-service `PARENT`/`STUDENT` pour reçus/factures et normalisation d'erreur `FinanceApiSupport`
- **Identifiant** : `P3-008`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `finance` — Axes B & D
- **Localisation** : `backend/src/main/java/com/ecole/saas/finance/application/FinanceRoles.java:5-6`, `backend/src/main/java/com/ecole/saas/finance/api/FinanceApiSupport.java:11-16`
- **Constat factuel** : (1) `FinanceRoles.READ_ROLES` exclut `PARENT` et `STUDENT`, protégeant le back-office mais privant les familles/élèves d'endpoints dédiés de consultation de leurs propres reçus et factures ; (2) `FinanceApiSupport.extractActorId` lève `IllegalArgumentException` brute si l'authentification est absente ou non-UUID (alors que `CommunicationApiSupport` vérifie `getToken().getSubject()` et lève une `BusinessException`).
- **Risque** : Couverture fonctionnelle portail famille incomplète côté API `finance` et diagnostic `ProblemDetail` moins précis en cas de jeton sans `sub` UUID.
- **Correction précise** : Si exposé au portail famille, créer des endpoints dédiés (`/api/v1/finance/me/...`) filtrés côté serveur via `FamilyDirectoryPort`/`StudentDirectoryPort` ; aligner `FinanceApiSupport.extractActorId` sur `CommunicationApiSupport`. **Test à ajouter** : test d'isolation `PARENT`/`STUDENT` inter-élèves sur les routes `/me` si ajoutées, et test unitaire sur `extractActorId`.

#### Points `[CONFORME]` vérifiés — `finance`

| # | Exigence vérifiée | Preuve (`fichier:ligne`) |
|---|---|---|
| 1 | Montants FCFA 100 % `long` / `BIGINT`, zéro `float`/`double`, arithmétique contrôlée `MoneyMath` | `MoneyMath.java:9-90`, `V7__finance_module.sql:39,88-92,240,413,465-469`, `ArchitectureTest.java:103-125` |
| 2 | Immutabilité absolue des 5 tables en ajout seul (triggers SQL `BEFORE UPDATE OR DELETE`, droits `ecole_app` `SELECT, INSERT`, `@PreUpdate`/`@PreRemove`, dépôts sans `delete`) | `V7__finance_module.sql:4-10,497-515,598-604`, `LedgerEntry.java:112-121`, `Payment.java:129-138`, `Receipt.java:91-100`, `PaymentAllocation.java:44-53`, `PaymentReversal.java:85-94`, `LedgerEntryRepository.java:18`, `FinanceIntegrationTest.java:500-560` |
| 3 | Reçus séquentiels sans trou par établissement + jeton QR opaque cryptographique sans PII | `FinanceSequenceRepository.java:23-41`, `FinanceCodeFormatter.java:29-49`, `OpaqueTokenGenerator.java:10-23`, `MoneyMathAndFormatterTest.java:50-74`, `FinanceIntegrationTest.java:343-344` |
| 4 | Concurrence (`@Lock(PESSIMISTIC_WRITE)` + `@Version`) et idempotence paiements (`UNIQUE (tenant_id, idempotency_key)`) | `StudentInvoiceRepository.java:18-20`, `V12__security_and_integrity_hardening.sql:162-172`, `PaymentService.java:148-188`, `FinanceIntegrationTest.java:277-315,917-946` |
| 5 | Séparation des tâches Maker-Checker (anti auto-approbation remises, contre-passations, clôture caisse) et cloisonnement `CASHIER` vs `DIRECTOR`/`PROMOTER` | `FeeAdjustment.java:158-161`, `PaymentReversalRequest.java:90-93`, `CashClosing.java:124-127`, `FinanceRoles.java:11-22`, `PaymentAdjustmentLedgerAndClosingDomainTest.java:79-103`, `FinanceIntegrationTest.java:563-684,971-1019` |
| 6 | Clôture de caisse journalière avec calcul SQL attendu et justification obligatoire en cas d'écart | `CashClosingService.java:112-159`, `CashClosing.java:95-110`, `PaymentRepository.java:40-51`, `PaymentReversalRepository.java:38-49`, `FinanceIntegrationTest.java:428-459,1022-1114` |
| 7 | RLS fail-closed (`ENABLE` + `FORCE ROW LEVEL SECURITY`) sur les 13 tables `V7` + FKs et unicités composites | `V7__finance_module.sql:517-595`, `V12__security_and_integrity_hardening.sql:68-70`, `FinanceIntegrationTest.java:742-888` |
| 8 | Domaine Java pur, injection par constructeur, zéro `@Entity` exposée, `Clock` injecté + fuseau `Africa/Dakar` | `ArchitectureTest.java:41-100,137-140`, `FinanceSchoolContext.java:17-50` |

---

### 2.2 Module `communication` (91 fichiers Java + `V11` & `V12` + `application*.yml` + `ADR 0006`)

#### A. Disponibilité & Résilience
- **Passerelles & Timeouts** : conformément à l'ADR `0006` (phase MVP hors réseau), seules des passerelles locales stubs (`LocalStubSmsGateway`, `LocalStubWhatsAppGateway`, `LocalStubEmailGateway`) sont implémentées. Aucun client HTTP/SMTP borné, aucune propriété de timeout (`connectTimeout`/`readTimeout`) dans `CommunicationGatewayProperties` / `application.yml`, ni aucun webhook opérateur signé n'existe encore (`P3-011`). `CommunicationCampaignService.dispatchViaGateway` (`lines 505-525`) capture les `RuntimeException` des passerelles en `DeliveryFailureCode.GATEWAY_ERROR`.
- **Transactions courtes hors réseau** : découpage strict en 3 phases (`CommunicationCampaignService.java:98-160, 184-199, 441-503` + `CommunicationDispatchPersistenceService`), prouvé par `CommunicationDispatchDecouplingTest.java:48-185` : aucune transaction DB n'est ouverte pendant `gateway.dispatch()` et `TenantContext` est restauré systématiquement.
- **Limites mémoire & pagination** : `CommunicationPagination.java:11` borne `size` à `1..100` avec liste blanche de tri. En revanche, `AudienceResolver.resolve` (`ALL_GUARDIANS`, `ALL_TEACHERS`, `ALL_STAFF`), l'envoi synchrone dans le thread HTTP et `InAppInboxService.markAllAsRead` chargent des collections non bornées (`P3-014`).

#### B. Sécurité, Intégrité Financière & Flux Fichiers
- **Idempotence** : `V11:189` impose `UNIQUE (tenant_id, idempotency_key)` + `request_hash VARCHAR(64)` SHA-256 (`CommunicationHashSupport.java:16-24`), et `V11:290` déduplique les remises par `(tenant_id, campaign_id, recipient_type, recipient_id, channel)`. Cependant, l'absence de verrou `pg_advisory_xact_lock` sur `idempotency_key` et de verrou `PESSIMISTIC_WRITE` lors du calcul `max(sequence_no) + 1` expose les requêtes concurrentes à des erreurs `409` (`P3-013`).
- **Montants FCFA & Immutabilité** : coûts unitaires et totaux 100 % en `long`/`BIGINT` (`SmsSegmentCalculator.java:54-92`, `V11:78, 173-175, 276-277`). `communication_delivery_events` est strictement en ajout seul (trigger `BEFORE UPDATE OR DELETE` `V11:42-48, 379-381`, droits `ecole_app` `SELECT, INSERT` `V11:435-437`, `@PreUpdate`/`@PreRemove` `DeliveryEvent.java:87-98`).
- **Consentement & Préférences** : `TemplateRenderer` réalise un rendu en une passe résistant à la ré-évaluation, mais deux failles bloquantes touchent le consentement : `ConsentPolicy.evaluate` neutralise l'opt-out marketing sur les campagnes non obligatoires (`P3-009`), et les DTOs de préférences omettent `@NotNull` sur 5 booléens transformés en `true` par défaut (`P3-010`). Par ailleurs, `quietHours` et les campagnes `SCHEDULED` ne sont pas exploités automatiquement (`P3-015`).
- **RBAC, Périmètre & `SubscriptionAccessPort`** : `CommunicationScopeGuard` cloisonne finement `CASHIER`, `CENSOR`, `TEACHER`/`TUTOR` et `PARENT`/`STUDENT`. En revanche, `InAppInboxService.markAsRead` et `markAllAsRead` mutent la base sous `requireReadAccess()` au lieu de `requireWriteAccess()`, et `CommunicationPortService` omet `requireReadAccess()` sur deux méthodes de lecture (`P3-012`).
- **Architecture & Configuration YAML** : domaine Java pur, injection constructeur, aucune `@Entity` exposée, `Clock` + `Africa/Dakar`, 6 tables `V11` avec RLS fail-closed et FKs composites (`V12:72-78`). Configuration YAML sûre (`open-in-view: false`, `ddl-auto: validate`, `problemdetails: true`, `show-details: never`, `application-test.yml` hors `src/main/resources`).

#### C. Performance & Scalabilité
- `CommunicationCampaignService.getSummary()` repose sur des agrégations SQL (`summarizeTotals`, `countGroupedByStatus`, `countGroupedByChannel`), les compteurs de campagne utilisent des `UPDATE` atomiques SQL (`incrementReadCounters`, `applyCounterDeltasAndRefreshStatus`), et `AudienceResolver`/`CommunicationDispatchPersistenceService`/`InAppInboxService` préchargent en batch et persistent via `saveAll`. Les index `V11` et `V12:215-221` sont alignés. Seul le chargement non borné des audiences globales et de `markAllAsRead` est problématique (`P3-014`).

#### D. Audit, Gestion d'Erreurs & Preuves de tests
- `audit_logs` trace modèles, préférences, création/dispatch/annulation/relance de campagnes et mises à jour de statuts sans PII. `BusinessException` -> `ProblemDetail` RFC 9457 en français avec `requestId`. Logs des passerelles sans PII (`tenantId`, `deliveryId`, `provider`, `costXof`).
- `CommunicationIntegrationTest` (1 463 lignes) + 3 tests unitaires (521 lignes) couvrent cycles de vie, découplage transactionnel, trigger SQL, `IN_APP`, RBAC et RLS sur les 6 tables `V11`, avec 4 scénarios manquants listés en `P3-016`.

#### Constats du module `communication` (`P3-009` .. `P3-016`)

##### `P3-009` — `[BLOQUANT]` — Contournement de `marketingOptIn = false` dans `ConsentPolicy.evaluate` sur les campagnes non obligatoires
- **Identifiant** : `P3-009`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `communication` — Axe B
- **Localisation** : `backend/src/main/java/com/ecole/saas/communication/domain/ConsentPolicy.java:41-51`, `backend/src/main/java/com/ecole/saas/communication/api/CommunicationCampaignController.java:61`
- **Constat factuel** : À `ConsentPolicy.java:41-42`, le code écrit :
  `boolean applyMarketingOptOut = respectMarketingOptOut || !category.isMandatoryTransactional();`
  suivi de `if (applyMarketingOptOut && respectMarketingOptOut && !preference.isMarketingOptIn())`.
  L'opérande redondant `&& respectMarketingOptOut` annule `!category.isMandatoryTransactional()` dès que `respectMarketingOptOut` vaut `false` (valeur par défaut de `Boolean.TRUE.equals(request.respectMarketingOptOut())` à `CommunicationCampaignController.java:61`). Ainsi, toute campagne `GENERAL`, `CENTER_SESSION` ou `CUSTOM` créée sans `respectMarketingOptOut: true` ignore `preference.isMarketingOptIn() == false`. À l'inverse, si `respectMarketingOptOut = true` est passé sur une catégorie transactionnelle obligatoire (`ABSENCE_ALERT`, `PAYMENT_RECEIPT`), la ligne 42 bloque l'envoi à tort.
- **Risque** : Envoi de communications non obligatoires à des parents/élèves ayant explicitement refusé l'opt-in marketing (`marketing_opt_in = false`), et blocage potentiel de notifications transactionnelles obligatoires si le drapeau est activé par l'appelant.
- **Correction précise** : Remplacer les lignes 41-51 de `ConsentPolicy.java` par :
  `if (!category.isMandatoryTransactional() && !preference.isMarketingOptIn()) { return ConsentDecision.blocked(DeliveryFailureCode.MARKETING_OPTED_OUT, "Le destinataire a refusé les communications non obligatoires (opt-out marketing)."); }`
  (ou n'évaluer `respectMarketingOptOut` que pour les catégories non obligatoires).
- **Test à ajouter** : test unitaire dans `CommunicationDomainEntitiesTest` et test d'intégration dans `CommunicationIntegrationTest` vérifiant qu'une campagne `GENERAL` (avec `respectMarketingOptOut = false`/omis) est bloquée (`MARKETING_OPTED_OUT`) pour un contact avec `marketingOptIn = false`, tandis que `PAYMENT_RECEIPT` reste autorisé même si `respectMarketingOptOut = true`.

##### `P3-010` — `[BLOQUANT]` — Booléens de consentement sans `@NotNull` dans les DTOs et réactivation silencieuse à `true`
- **Identifiant** : `P3-010`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `communication` — Axe B
- **Localisation** : `backend/src/main/java/com/ecole/saas/communication/api/UpsertContactPreferenceRequest.java:16-20`, `backend/src/main/java/com/ecole/saas/communication/api/UpdateMyPreferenceRequest.java:8-12`, `backend/src/main/java/com/ecole/saas/communication/api/ContactPreferenceController.java:43-47, 74-78`
- **Constat factuel** : Les 5 champs `Boolean` (`inAppEnabled`, `smsEnabled`, `whatsappEnabled`, `emailEnabled`, `marketingOptIn`) ne portent pas `@NotNull` dans `UpsertContactPreferenceRequest` ni `UpdateMyPreferenceRequest`. Dans `ContactPreferenceController` (`lignes 43-47` et `74-78`), `!Boolean.FALSE.equals(...)` convertit toute valeur `null` ou omise en `true`.
- **Risque** : Violation de `quality-gate.md` §2 (`Boolean` obligatoire annoté `@NotNull`) et réactivation silencieuse de `marketingOptIn` ou d'un canal préalablement refusé lors d'un appel `PUT` partiel.
- **Correction précise** : Annoter les 5 champs `Boolean` avec `@NotNull` dans `UpsertContactPreferenceRequest.java:16-20` et `UpdateMyPreferenceRequest.java:8-12` et transmettre directement les valeurs au service sans `!Boolean.FALSE.equals(...)`.
- **Test à ajouter** : test MockMvc vérifiant le statut `400 Bad Request` lorsqu'un des 5 champs booléens de consentement est absent/null sur `PUT /api/v1/communication/preferences` et `/preferences/me`.

##### `P3-011` — `[BLOQUANT]` — Absence de connecteurs opérateurs réels, de timeouts HTTP/SMTP configurés et de webhook opérateur signé
- **Identifiant** : `P3-011`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `communication` — Axes A & B
- **Localisation** : `backend/src/main/java/com/ecole/saas/communication/infrastructure/CommunicationGatewayProperties.java:9-49`, `LocalStubSmsGateway.java:16-63`, `LocalStubWhatsAppGateway.java:16-59`, `LocalStubEmailGateway.java:16-58`, `backend/src/main/resources/application.yml:42-52`, `backend/src/main/java/com/ecole/saas/communication/api/CommunicationCampaignController.java:203-216`
- **Constat factuel** : Conformément à l'ADR `0006`, seules des passerelles stubs locales sont présentes. `CommunicationGatewayProperties` et `application.yml` ne définissent aucun timeout de connexion ou de lecture (`connectTimeoutMs`, `readTimeoutMs`), aucun client HTTP/SMTP avec gestion des statuts non-2xx et retry borné, et `POST /api/v1/communication/deliveries/{deliveryId}/status` est un endpoint administrateur sans webhook opérateur authentifié par signature HMAC à temps constant.
- **Risque** : Impossibilité d'émettre SMS/WhatsApp/Email en production, et risque de blocage de threads HTTP/pool en cas d'intégration ultérieure sans timeouts explicites ni vérification de signature sur les accusés de remise.
- **Correction précise** : Ajouter `connectTimeoutMs` et `readTimeoutMs` bornés dans `CommunicationGatewayProperties` et `application.yml`, implémenter les adaptateurs opérateurs sous profils dédiés (timeouts explicites, pas de retry infini, toujours exécutés hors `@Transactional`), et ajouter un endpoint webhook de retour statut vérifiant la signature HMAC via `MessageDigest.isEqual`.
- **Test à ajouter** : tests WireMock couvrant timeout connexion/lecture -> `GATEWAY_ERROR`, HTTP 4xx -> `GATEWAY_REJECTED`, HTTP 5xx -> `GATEWAY_ERROR`, et webhook signé valide/invalide.

##### `P3-012` — `[BLOQUANT]` — `InAppInboxService.markAsRead`/`markAllAsRead` sous `requireReadAccess()` et lectures `CommunicationPortService` sans garde abonnement
- **Identifiant** : `P3-012`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `communication` — Axe B
- **Localisation** : `backend/src/main/java/com/ecole/saas/communication/application/InAppInboxService.java:95-175`, `backend/src/main/java/com/ecole/saas/communication/application/CommunicationPortService.java:241-253`
- **Constat factuel** :
  1. `InAppInboxService.markAsRead` (`ligne 98`) et `markAllAsRead` (`ligne 126`) sont des transactions `@Transactional` mutantes (mise à jour de `communication_deliveries`, insertion dans `communication_delivery_events`, mise à jour des compteurs de `communication_campaigns`), mais appellent `subscriptionGuard.requireReadAccess()` au lieu de `requireWriteAccess()`.
  2. `CommunicationPortService.findCampaignById` (`ligne 243`) et `countUnreadInAppNotifications` (`ligne 250`) n'appellent pas `subscriptionGuard.requireReadAccess()`.
- **Risque** : Un établissement sous abonnement `EXPIRED` peut continuer à modifier la base via `/api/v1/communication/inbox/*/read` et `/read-all` (`quality-gate.md` §2), et les lectures inter-modules via `CommunicationPort` ne vérifient pas l'état de l'abonnement.
- **Correction précise** : Appeler `subscriptionGuard.requireWriteAccess()` dans `InAppInboxService.markAsRead` et `markAllAsRead`, et appeler `subscriptionGuard.requireReadAccess()` dans `CommunicationPortService.findCampaignById` et `countUnreadInAppNotifications`.
- **Test à ajouter** : tests d'intégration vérifiant le rejet `403` sous abonnement `EXPIRED` sur `/inbox/{id}/read` et `/inbox/read-all`, et `CommunicationSubscriptionReadDeniedException` sur `CommunicationPort` sous statut `SUSPENDED`.

##### `P3-013` — `[MAJEUR]` — Race condition sur `idempotency_key` en création de campagne et sur `sequence_no` d'événements de remise
- **Identifiant** : `P3-013`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `communication` — Axe B
- **Localisation** : `backend/src/main/java/com/ecole/saas/communication/application/CommunicationDispatchPersistenceService.java:161-173`, `backend/src/main/java/com/ecole/saas/communication/application/CommunicationCampaignService.java:247`, `backend/src/main/java/com/ecole/saas/communication/application/InAppInboxService.java:108`
- **Constat factuel** :
  1. `prepareCreateCampaign` teste `campaignRepository.findByIdempotencyKey(idempotencyKey)` (`ligne 161`) sans verrou `pg_advisory_xact_lock` ni rattrapage d'une violation de contrainte `uq_communication_campaigns_tenant_idempotency` : deux appels simultanés identiques provoquent `201` + `409 data-conflict` au lieu de `{201, 200 X-Idempotent-Replay: true}`.
  2. `InAppInboxService.markAsRead` (`ligne 108`) et `CommunicationCampaignService.recordDeliveryStatus` (`ligne 247`) calculent `findMaxSequenceNoByDeliveryId(...) + 1` sans `@Lock(PESSIMISTIC_WRITE)` sur `MessageDelivery`, exposant deux transitions concurrentes d'une même remise à un conflit sur `uq_communication_delivery_events_tenant_delivery_seq`.
- **Risque** : Erreurs `409` parasites lors de doubles requêtes concurrentes (reprise réseau, double-clic) ou d'un accusé simultané `markAsRead`/callback.
- **Correction précise** : Prendre un verrou consultatif `pg_advisory_xact_lock` sur `(tenantId, hashtext(idempotencyKey))` dans `prepareCreateCampaign`, et verrouiller `MessageDelivery` via `@Lock(PESSIMISTIC_WRITE)` dans `markAsRead` et `recordDeliveryStatus`.
- **Test à ajouter** : test multi-thread dans `CommunicationIntegrationTest` vérifiant que deux `POST /campaigns` concurrents identiques retournent `{201, 200}`.

##### `P3-014` — `[MAJEUR]` — Chargement mémoire et envoi synchrone non bornés pour les audiences globales et `markAllAsRead`
- **Identifiant** : `P3-014`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `communication` — Axes A & C
- **Localisation** : `backend/src/main/java/com/ecole/saas/communication/application/AudienceResolver.java:169-191`, `backend/src/main/java/com/ecole/saas/communication/application/CommunicationCampaignService.java:488-503`, `backend/src/main/java/com/ecole/saas/communication/application/InAppInboxService.java:125-134`
- **Constat factuel** :
  1. `AudienceResolver.resolve(ALL_GUARDIANS / ALL_TEACHERS / ALL_STAFF)` charge l'ensemble des destinataires actifs en mémoire sans pagination ni plafond, et `dispatchQueuedItemsOutsideTransaction` exécute tous les appels passerelle séquentiellement dans la requête HTTP synchrone.
  2. `InAppInboxService.markAllAsRead` (`lignes 127-128`) charge toutes les remises non lues de l'utilisateur en mémoire sans borne.
- **Risque** : Saturation mémoire JVM et dépassement des timeouts HTTP sur des établissements à forte volumétrie dès le branchement de passerelles HTTP réelles.
- **Correction précise** : Plafonner l'envoi synchrone et traiter la file `QUEUED` via un worker asynchrone par lots de 100 avec `TenantContext` explicite, et traiter `markAllAsRead` par lots paginés bornés (ou mise à jour SQL bulk).
- **Test à ajouter** : test vérifiant le traitement par lots sur une campagne dépassant la taille de lot synchrone.

##### `P3-015` — `[MINEUR]` — Plages silencieuses (`quietHours`) stockées sans évaluation et campagnes `SCHEDULED` sans ordonnanceur
- **Identifiant** : `P3-015`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `communication` — Axes A & B
- **Localisation** : `backend/src/main/java/com/ecole/saas/communication/domain/ContactPreference.java:58-62`, `ConsentPolicy.java:17-61`, `CommunicationCampaign.java:76-77, 183`
- **Constat factuel** : `quietHoursStart` / `quietHoursEnd` sont persistés et validés (`V11:120-121`) mais jamais évalués par `ConsentPolicy`, et les campagnes en statut `SCHEDULED` ne disposent d'aucun job planifié pour être diffusées automatiquement à `scheduledAt`.
- **Risque** : Paramètres utilisateurs de tranquillité et planification différée sans effet automatique.
- **Correction précise** : Intégrer le contrôle `quietHours` (dans le fuseau établissement via `Clock`) pour les messages non urgents, et ajouter un ordonnanceur multi-tenant exécutant les campagnes `SCHEDULED` échues par lots. **Test à ajouter** : test unitaire `ConsentPolicy` sur plage silencieuse et test d'intégration d'exécution d'une campagne `SCHEDULED`.

##### `P3-016` — `[MINEUR]` — Lacunes ciblées dans la suite de tests `communication`
- **Identifiant** : `P3-016`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `communication` — Axe D
- **Localisation** : `backend/src/test/java/com/ecole/saas/CommunicationIntegrationTest.java`, `backend/src/test/java/com/ecole/saas/communication/domain/CommunicationDomainEntitiesTest.java`
- **Constat factuel** : Malgré une base de tests très complète (1 463 lignes d'intégration + 521 lignes unitaires), quatre scénarios restent non couverts : (1) `GENERAL` avec `marketingOptIn = false` sans `respectMarketingOptOut` (`P3-009`), (2) concurrence idempotente sur `POST /campaigns` (`P3-013`), (3) périmètre `TUTOR` sur `CENTER_GROUP_GUARDIANS`/`CENTER_GROUP_STUDENTS` dans `CommunicationScopeGuard`, (4) rejet `403` sous statut `EXPIRED` sur `POST /inbox/{id}/read` (`P3-012`).
- **Risque** : Régressions silencieuses sur ces 4 invariants spécifiques.
- **Correction précise** : Ajouter ces 4 scénarios de test dans `CommunicationDomainEntitiesTest` et `CommunicationIntegrationTest`.

#### Points `[CONFORME]` vérifiés — `communication`

| # | Exigence vérifiée | Preuve (`fichier:ligne`) |
|---|---|---|
| 1 | Découplage transactions courtes hors appels passerelle (3 phases) + restauration `TenantContext` | `CommunicationCampaignService.java:98-160,184-199,441-503`, `CommunicationDispatchDecouplingTest.java:48-185` |
| 2 | Immutabilité append-only `communication_delivery_events` (trigger SQL + `ecole_app` `SELECT, INSERT` + `@PreUpdate`/`@PreRemove` + dépôt sans `delete`) | `V11__communication_module.sql:42-48,379-381,435-437`, `DeliveryEvent.java:87-98`, `DeliveryEventRepository.java:14`, `CommunicationIntegrationTest.java:338-352` |
| 3 | Coûts FCFA strictement en `long`/`BIGINT` + calcul segments GSM-7/UCS-2 sans flottant | `SmsSegmentCalculator.java:24-92`, `V11__communication_module.sql:78,173-175,276-277`, `TemplateRendererAndSmsSegmentTest.java:45-75` |
| 4 | Empreinte SHA-256 `request_hash`, déduplication destinataires et rendu template en 1 passe | `V11__communication_module.sql:189,290`, `CommunicationHashSupport.java:16-24`, `TemplateRenderer.java:33-59`, `TemplateRendererAndSmsSegmentTest.java:14-43` |
| 5 | Agrégations SQL `/summary`, incréments atomiques des compteurs de campagne, préchargement batch & `saveAll` | `CommunicationCampaignService.java:378-416`, `CommunicationCampaignRepository.java:28-97`, `MessageDeliveryRepository.java:79-83`, `CommunicationDispatchPersistenceService.java:475-589,667-683`, `AudienceResolver.java:209-250`, `CommunicationIntegrationTest.java:1062-1172` |
| 6 | RLS fail-closed sur les 6 tables `V11`, FKs composites, boîte `IN_APP` isolée par `sub`, `audit_logs` et logs sans PII | `V11__communication_module.sql:387-421`, `V12__security_and_integrity_hardening.sql:72-78`, `InAppInboxService.java:55-121`, `LocalStubSmsGateway.java:55-60`, `CommunicationIntegrationTest.java:432-484,753-929` |
| 7 | Configuration YAML transverse sûre (`open-in-view: false`, `ddl-auto: validate`, `problemdetails: true`, `show-details: never`, `application-test.yml` hors `src/main/resources`) | `application.yml:9-23,54-61`, `application-dev.yml:1-16`, `application-test.yml:1-15`, `ArchitectureTest.java:128-135` |

---

### 2.3 Module `schooldocs` (58 fichiers Java + `V13` + `ADR 0007`)

#### A. Disponibilité & Résilience
- **Timeouts & clients externes** : aucun appel réseau HTTP/S3 dans le MVP ; le stockage est abstrait par `DocumentBinaryStorage` (`DocumentBinaryStorage.java:10-24`) et implémenté en base via `DatabaseDocumentBinaryStorage` (`DatabaseDocumentBinaryStorage.java:14-49`). Lors du futur basculement vers un stockage objet externe, des timeouts bornés devront être configurés.
- **Transactions courtes hors réseau** : aucun appel réseau dans `@Transactional` ; le rendu PDF 1.4 par `SimplePdfDocumentRenderer` (`SimplePdfDocumentRenderer.java:22-42`) s'exécute en mémoire via `ByteArrayOutputStream`.
- **Limites mémoire, parsing & ressources** : modèles/corps bornés à `20 000` caractères en DTO (`CreateDocumentTemplateRequest.java:33`) et `30 000` caractères rendus (`IssuedDocument.java:387`), `variables` bornées à `50` entrées (`IssueDocumentRequest.java:34`), binaire PDF borné à `5 Mo` (`DocumentSecurityPolicy.MAX_DOCUMENT_SIZE_BYTES = 5242880`, `V13:188, 237`).

#### B. Sécurité, Intégrité Financière & Flux Fichiers
- **Idempotence** : `UNIQUE (tenant_id, idempotency_key)` (`V13:166`) + `requestHash` SHA-256 (`IssuedDocumentService.java:133-143, 312-322`), avec point mineur sur la collision concurrente stricte (`P3-019`).
- **Immutabilité & zéro `float`/`double`** : `feeAmountXof` en `long`/`BIGINT` (`DocumentTemplate.java:62`, `IssuedDocument.java:111`, `V13:82, 140`). `schooldocs_document_contents` et `schooldocs_document_events` sont en ajout seul (triggers `BEFORE UPDATE OR DELETE`, droits `ecole_app` `SELECT, INSERT`, `@PreUpdate`/`@PreRemove`). Numérotation séquentielle sans trou (`DocumentSequenceRepository.nextSequenceValue`) et verrou `PESSIMISTIC_WRITE` sur `reissueDocument`/`revokeDocument`.
- **Type réel `%PDF-`, anti-path-traversal, jeton opaque & SHA-256** : `DocumentSecurityPolicy` valide l'en-tête `%PDF-` et la taille `<= 5 Mo` (`55-69`), rejette `..`, `\`, `/`, `\0` (`86-120`), génère un jeton aléatoire `SecureRandom` de 32 octets sans PII (`32-36`), et `DatabaseDocumentBinaryStorage` vérifie le SHA-256 à l'écriture et au téléchargement via `MessageDigest.isEqual` en temps constant (`30, 39` ; comportement de `verifyByToken` en cas de corruption relevé en `P3-020`).
- **RBAC & `SubscriptionAccessPort`** : `@PreAuthorize` sur tous les services, périmètre fin vérifié dans `SchoolDocsAccessScopeGuard` (`CENSOR`, `TEACHER`/`TUTOR`, `PARENT`, `STUDENT`). **Défaut majeur `P3-017`** : `SchoolDocsSubscriptionGuard.assertStorageQuotaAvailable` n'interroge pas `subscriptionAccessPort.currentUsage(QuotaResource.STORAGE_MB)` et ne contrôle que les octets locaux `schooldocs`, ignorant le quota partagé avec `schoolimport` et `reporting`.
- **Architecture & RLS** : domaine pur sans Spring, `Clock` + `Africa/Dakar`, DTO `@Valid` boxés `@NotNull`, 5 tables `V13` avec RLS fail-closed et FKs composites.

#### C. Performance & Scalabilité
- Agrégations JPQL directes dans `IssuedDocumentRepository` et `SchoolDocsDirectoryQueryService` (zéro `findAll()`), préchargement batch dans `IssuedDocumentService.buildBatchViews` (`789-848`) via `find*ByIds` -> `Map<UUID, ...>`, et index composites préfixés par `tenant_id` alignés dans `V13:66-67, 105-110, 195-218`.

#### D. Audit, Gestion d'Erreurs & Preuves de tests
- `AuditLogPort` (`AuditCategory.SCHOOLDOCS`) tracé sur modèles, émission, réédition, révocation et téléchargement sans fuite de PII ni de `verificationToken`.
- `SchoolDoc*Exception` -> `ProblemDetail` RFC 9457 en français, zéro PII dans les logs.
- Tests unitaires + `SchoolDocsIntegrationTest` (1 276 lignes) couvrant cycle de vie, idempotence, RBAC/scope et RLS ; il manque un test concurrent multi-thread (`P3-018`).

#### Constats du module `schooldocs` (`P3-017` .. `P3-020`)

##### `P3-017` — `[MAJEUR]` — `SchoolDocsSubscriptionGuard.assertStorageQuotaAvailable` ignore l'usage `STORAGE_MB` des autres modules
- **Identifiant** : `P3-017`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `schooldocs` — Axe B
- **Localisation** : `backend/src/main/java/com/ecole/saas/schooldocs/application/SchoolDocsSubscriptionGuard.java:53-60` (croisé avec `backend/src/main/java/com/ecole/saas/subscription/application/SubscriptionGuardService.java:106-112`)
- **Constat factuel** : `SchoolDocsSubscriptionGuard.assertStorageQuotaAvailable(long additionalBytes)` calcule `currentMiB` uniquement à partir de `issuedDocumentRepository.sumFileSizeBytes()` (`lignes 54-55`) et passe ce total partiel à `subscriptionAccessPort.assertQuotaAvailable(QuotaResource.STORAGE_MB, currentMiB, additionalMiB)` (`ligne 59`). Or `SubscriptionGuardService.assertQuotaAvailable` (`SubscriptionGuardService.java:106-112`) évalue le plafond uniquement sur le paramètre `currentUsage` reçu, sans agréger les `QuotaUsageProvider` (contrairement à `SchoolImportSubscriptionGuard.java:60-61` et `ReportingSubscriptionGuard.java:43-48` qui appellent `subscriptionAccessPort.currentUsage(QuotaResource.STORAGE_MB)`).
- **Risque** : Un établissement ayant saturé son quota `STORAGE_MB` via `schoolimport` ou `reporting` peut contourner le plafond contractuel en continuant à émettre et stocker des documents PDF dans `schooldocs`.
- **Correction précise** : Dans `SchoolDocsSubscriptionGuard.assertStorageQuotaAvailable`, appeler `long totalCurrentMiB = subscriptionAccessPort.currentUsage(QuotaResource.STORAGE_MB);` puis `subscriptionAccessPort.assertQuotaAvailable(QuotaResource.STORAGE_MB, totalCurrentMiB, additionalMiB);`.
- **Test à ajouter** : test d'intégration dans `SchoolDocsIntegrationTest` vérifiant le rejet `409 subscription-quota-exceeded` lorsque le quota `STORAGE_MB` est déjà consommé par `schoolimport`/`reporting`.

##### `P3-018` — `[MAJEUR]` — Absence de test multi-thread de concurrence sur `DocumentSequenceRepository` et `reissueDocument`
- **Identifiant** : `P3-018`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `schooldocs` — Axe D
- **Localisation** : `backend/src/test/java/com/ecole/saas/SchoolDocsIntegrationTest.java:324-380, 888-908`
- **Constat factuel** : Aucun test concurrent (`ExecutorService` / `CountDownLatch`) ne sollicite en parallèle `DocumentSequenceRepository.nextSequenceValue` ni `IssuedDocumentService.reissueDocument` (`@Lock(PESSIMISTIC_WRITE)` + `uq_schooldocs_documents_tenant_original_edition`).
- **Risque** : Une régression de sérialisation sous charge concurrente (duplicatas ou numérotation annuelle) ne serait pas détectée par la suite de tests.
- **Correction précise** : Ajouter un test multi-thread dans `SchoolDocsIntegrationTest` exécutant N émissions simultanées (numéros contigus sans trou ni doublon) et M rééditions concurrentes d'un même document (éditions `2..M+1`).

##### `P3-019` — `[MINEUR]` — Collision concurrente sur `Idempotency-Key` retournant `409 data-conflict` au lieu d'un rejeu `200 OK`
- **Identifiant** : `P3-019`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `schooldocs` — Axe B
- **Localisation** : `backend/src/main/java/com/ecole/saas/schooldocs/application/IssuedDocumentService.java:133-143, 253, 312-322, 421`
- **Constat factuel** : `issueDocument` et `reissueDocument` vérifient `findByIdempotencyKey` avant `save` sans verrou `pg_advisory_xact_lock` ni capture de `DataIntegrityViolationException` sur `uq_schooldocs_documents_tenant_idempotency` (traduite en `409` par `GlobalProblemHandler.java:193-208`).
- **Risque** : Un retry réseau strictement simultané reçoit `409` au lieu de `200 X-Idempotent-Replay: true` (aucun doublon créé grâce à la contrainte `UNIQUE`).
- **Correction précise** : Poser un `pg_advisory_xact_lock` sur `(tenant_id, idempotency_key)` ou relire l'existant après capture de `DataIntegrityViolationException` et vérifier `requestHash`. **Test à ajouter** : test concurrent idempotent sur `POST /api/v1/schooldocs/documents`.

##### `P3-020` — `[MINEUR]` — `verifyByToken` lève `409` en cas de corruption binaire au lieu de retourner `integrityVerified = false`
- **Identifiant** : `P3-020`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `schooldocs` — Axe B
- **Localisation** : `backend/src/main/java/com/ecole/saas/schooldocs/application/IssuedDocumentService.java:666-688`, `backend/src/main/java/com/ecole/saas/schooldocs/infrastructure/DatabaseDocumentBinaryStorage.java:36-48`
- **Constat factuel** : `verifyByToken` appelle `binaryStorage.load(document.getId())` (`ligne 666`), qui appelle `DocumentSecurityPolicy.assertIntegrity(bytes, entity.getSha256Hash())` (`DatabaseDocumentBinaryStorage.java:39`) et lève `SchoolDocConflictException` (`409`) si les octets stockés ont été altérés, court-circuitant le retour de `DocumentVerificationView` avec `integrityVerified = false` et `authentic = false`.
- **Risque** : L'endpoint `/verify/{token}` renvoie une erreur `409` au lieu d'un verdict structuré `integrityVerified = false`.
- **Correction précise** : Capturer `SchoolDocConflictException` autour de `binaryStorage.load` dans `verifyByToken` pour renseigner `integrityVerified = false` (`authentic = false`). **Test à ajouter** : test d'intégration vérifiant `/verify/{token}` après altération binaire directe en base.

#### Points `[CONFORME]` vérifiés — `schooldocs`

| # | Exigence vérifiée | Preuve (`fichier:ligne`) |
|---|---|---|
| 1 | Zéro `float`/`double` & frais FCFA en `long`/`BIGINT` | `DocumentTemplate.java:62`, `IssuedDocument.java:111`, `V13__schooldocs_module.sql:82,140`, `ArchitectureTest.java:103-125` |
| 2 | Immutabilité append-only `contents` & `events` (Java + privilèges `ecole_app` + triggers SQL `BEFORE UPDATE OR DELETE`) | `DocumentContent.java:70-80`, `DocumentEvent.java:76-86`, `DocumentContentRepository.java:11-16`, `DocumentEventRepository.java:13-21`, `V13__schooldocs_module.sql:244-246,275-277,323-326` |
| 3 | Numérotation séquentielle atomique sans trou & verrouillage `PESSIMISTIC_WRITE` sur duplicatas et révocation | `DocumentSequenceRepository.java:20-36`, `IssuedDocumentRepository.java:19-21`, `IssuedDocumentService.java:325-340,477-482` |
| 4 | Vérification type réel `%PDF-`, taille `<= 5 Mo`, anti-path-traversal, SHA-256 temps constant & jeton opaque `SecureRandom` | `DocumentSecurityPolicy.java:32-120`, `DatabaseDocumentBinaryStorage.java:24-48` |
| 5 | RBAC `@PreAuthorize` + périmètre fin (`CENSOR`, `TEACHER`/`TUTOR`, `PARENT`, `STUDENT`, masquage modèles inactifs) | `SchoolDocsRoles.java:5-22`, `SchoolDocsAccessScopeGuard.java:27-175`, `DocumentTemplateService.java:162-179` |
| 6 | Agrégations SQL/JPQL + préchargement par lots zéro N+1 | `IssuedDocumentRepository.java:43-126`, `IssuedDocumentService.java:709-752,789-848` |
| 7 | Domaine Java pur, `Clock` + `Africa/Dakar`, DTO `@Valid` boxés `@NotNull`, RLS fail-closed `V13` | `schooldocs/domain/**`, `IssuedDocumentService.java:167-170`, `CreateDocumentTemplateRequest.java:10-46`, `V13__schooldocs_module.sql:283-326` |
| 8 | Journal `audit_logs` `SCHOOLDOCS` sans fuite de jeton/PII + tests Testcontainers PostgreSQL | `IssuedDocumentService.java:271-283,449-462,494-506,626-636`, `SchoolDocsIntegrationTest.java:450-462,845-1012` |

---

### 2.4 Module `schoolimport` (61 fichiers Java + `V14` + `ADR 0008`)

#### A. Disponibilité & Résilience
- **Timeouts & clients externes** : aucun appel réseau externe ; stockage binaire abstrait derrière `ImportFileBinaryStorage` et implémenté en base par `DatabaseImportFileStorage`.
- **Transactions courtes & découpage** : `createAndAnalyzeJob` valide format réel et SHA-256 avant `requiresNewTxTemplate` (`SchoolImportJobService.java:202-217`), et `executeJob` (`360-552`) découpe en Phase 1 (`REQUIRES_NEW` + `PESSIMISTIC_WRITE` -> `PROCESSING`), Phase 2 (`SchoolImportRowExecutor` avec `REQUIRES_NEW` + `TenantContext.executeWithTenant` par ligne en `PARTIAL_SKIP_ERRORS`) et Phase 3 (`REQUIRES_NEW` finalisation + `saveAll`). Cependant, un échec en Phase 2 laisse le lot bloqué en `PROCESSING` (`P3-023`).
- **Limites mémoire, parsing & ressources** : `ImportFileSecurityPolicy` (`22-27`) borne strictement à `5 Mo` fichier, `20 Mo` décompressé XLSX, `100` entrées ZIP, `2 000` lignes, `50` colonnes et `1 000` car./cellule ; `SecureTabularFileParser` ferme `ZipInputStream` via `try-with-resources`. **Défaut majeur `P3-022`** : `application.yml` n'augmente pas `spring.servlet.multipart.max-file-size` (défaut `1MB`) et `GlobalProblemHandler` ne gère pas `MaxUploadSizeExceededException`.

#### B. Sécurité, Intégrité Financière & Flux Fichiers
- **Idempotence** : `UNIQUE (tenant_id, idempotency_key)` à la création (`V14:121`) + index unique partiel `(tenant_id, execution_idempotency_key)` (`V14:170-172`) + verrou `PESSIMISTIC_WRITE` dans `executeJob`.
- **Anti-CSV-Injection** : **Défaut bloquant `P3-021`** — `ImportFileSecurityPolicy.neutralizeCsvFormula` (`168-177`) ne vérifie que `raw.charAt(0)` et laisse passer les formules précédées d'espaces ou de sauts de ligne.
- **Anti-XXE, Anti-ZIP-Bomb, Magic bytes & path-traversal** : détection exclusivement par octets magiques (`PK\x03\x04` pour XLSX, UTF-8 strict sans octet NUL ni signatures binaires interdites pour CSV) dans `ImportFileSecurityPolicy.detectAndValidateFormat` (`40-80`) ; `SecureTabularFileParser.parseXmlSecurely` (`413-432`) désactive `disallow-doctype-decl`, entités externes, DTD et active `FEATURE_SECURE_PROCESSING` ; `extractValidatedXlsxEntries` (`252-287`) rejette `..`, `/`, `\` et coupe dès `> 20 Mo` ou `> 100` entrées ; `sanitizeFileName` et `buildTenantScopedStorageKey` préviennent le path-traversal.
- **Intégrité SHA-256, RBAC, PII & `SubscriptionAccessPort`** : SHA-256 calculé et recontrôlé avant exécution (`P3-025` sur `equalsIgnoreCase`) ; `@PreAuthorize` + `SchoolImportAccessGuard` (cloisonnement `TEACHER`/`TUTOR` sur ses propres lots et classes/matières) ; `ImportRowSanitizer` masque CNI/NIN/médical/secrets ; `SchoolImportSubscriptionGuard` vérifie `read`/`write`, `school.active()`, `STUDENTS` et le quota partagé `STORAGE_MB` via `currentUsage`.
- **Montants FCFA, Architecture & RLS** : zéro `float`/`double` (`long` dans `ImportFieldParser.parseOptionalAmountXof:248-270`), domaine Java pur, `Clock` + `Africa/Dakar`, 5 tables `V14` avec RLS fail-closed, triggers `BEFORE UPDATE OR DELETE` sur `files` et `events`.

#### C. Performance & Scalabilité
- `SchoolImportRowAnalyzer` précharge systématiquement toutes les références en lot (`findClassRoomsByAcademicYear`, `findStudentsByMatricules`, etc.) -> zéro N+1 sur 2 000 lignes ; `rowRepository.saveAll` utilisé en création et finalisation ; agrégations SQL directes dans `ImportJobRepository` ; index composites préfixés par `tenant_id` alignés dans `V14`.

#### D. Audit, Gestion d'Erreurs & Preuves de tests
- `AuditLogPort` (`AuditCategory.SCHOOLIMPORT`) tracé sur création, exécution, annulation et téléchargement du CSV d'erreurs sans PII sensible. `BusinessException` -> `ProblemDetail` RFC 9457 en français.
- Tests unitaires + `SchoolImportIntegrationTest` (1 069 lignes) complets, à compléter sur ZIP-bomb et concurrence (`P3-024`).

#### Constats du module `schoolimport` (`P3-021` .. `P3-025`)

##### `P3-021` — `[BLOQUANT]` — Neutralisation Anti-CSV-Injection incomplète sur espaces et sauts de ligne initiaux
- **Identifiant** : `P3-021`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `schoolimport` — Axe B
- **Localisation** : `backend/src/main/java/com/ecole/saas/schoolimport/domain/ImportFileSecurityPolicy.java:168-177` (utilisé par `ImportErrorReportFormatter.java:16-36` et `ImportColumnCatalog.java:91-111`)
- **Constat factuel** : `ImportFileSecurityPolicy.neutralizeCsvFormula(String raw)` n'inspecte que `raw.charAt(0)` (`=`, `+`, `-`, `@`, `\t`, `\r`), sans ignorer les espaces ou retours à la ligne initiaux avant le déclencheur de formule (à l'inverse de `reporting/domain/CsvFormulaSanitizer.java:69-83`).
- **Risque** : Une cellule importée contenant une charge utile précédée d'espaces ou de `\n` (ex. `"  =CMD()"`, `"\n+1+1"`) et ré-exportée dans le rapport d'erreurs CSV n'est pas neutralisée par `'` et expose le poste client ouvrant le CSV dans Excel/LibreOffice Calc à une exécution de formule (CSV-Injection).
- **Correction précise** : Aligner `ImportFileSecurityPolicy.neutralizeCsvFormula` sur `CsvFormulaSanitizer.neutralizeFormula` : supprimer `\0`, inspecter les blancs initiaux (`' '`, `'\t'`, `'\r'`, `'\n'`), déclencher sur `\t`/`\r` ou sur `=`, `+`, `-`, `@` en première position non-blanche, et préfixer `'`.
- **Test à ajouter** : cas de test dans `SchoolImportDomainPolicyTest` et `SchoolImportIntegrationTest` vérifiant la neutralisation de `"  =CMD()"`, `"\n+1+1"`, `"\t@SUM(1,1)"` et des octets `\0`.

##### `P3-022` — `[MAJEUR]` — Absence de configuration multipart `5MB` dans `application.yml` et de handler `MaxUploadSizeExceededException`
- **Identifiant** : `P3-022`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `schoolimport` — Axe A
- **Localisation** : `backend/src/main/resources/application.yml:1-61`, `backend/src/main/java/com/ecole/saas/shared/api/GlobalProblemHandler.java:87-100`, `backend/src/main/java/com/ecole/saas/schoolimport/api/SchoolImportController.java:86-91`
- **Constat factuel** : La politique métier fixe la taille maximale des fichiers à `5 Mo` (`ImportFileSecurityPolicy.java:22`, `V14__schoolimport_module.sql:150, 205`), mais `application.yml` ne configure pas `spring.servlet.multipart.max-file-size` (valeur par défaut Spring Boot `1MB`), et `GlobalProblemHandler` ne capture pas `MaxUploadSizeExceededException`. De plus, `SchoolImportController.createImportJob` appelle `file.getBytes()` (`ligne 88`) sans tester préalablement `file.getSize()`.
- **Risque** : Tout fichier valide compris entre `1 Mo` et `5 Mo` est rejeté en `500 Internal Server Error` avant le contrôleur, et tout dépassement multipart produit un `500` non structuré au lieu d'un `ProblemDetail` en français.
- **Correction précise** : Ajouter `spring.servlet.multipart.max-file-size: 5MB` et `max-request-size: 6MB` dans `application.yml`, ajouter `@ExceptionHandler(MaxUploadSizeExceededException.class)` dans `GlobalProblemHandler` retournant `413 Payload Too Large` (ou `400`) en `ProblemDetail` RFC 9457 français avec `requestId`, et vérifier `file.getSize() > ImportFileSecurityPolicy.MAX_FILE_SIZE_BYTES` avant `file.getBytes()` dans `SchoolImportController`.
- **Test à ajouter** : test MockMvc dans `SchoolImportIntegrationTest` couvrant un upload valide de `2 Mo` et un dépassement multipart > `5 Mo`.

##### `P3-023` — `[MAJEUR]` — Lot d'import bloqué définitivement en `PROCESSING` si la Phase 2 ou la Phase 3 échoue
- **Identifiant** : `P3-023`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `schoolimport` — Axe A
- **Localisation** : `backend/src/main/java/com/ecole/saas/schoolimport/application/SchoolImportJobService.java:377-465`
- **Constat factuel** : La Phase 1 de `executeJob` commite dans `requiresNewTxTemplate` le passage du lot au statut `PROCESSING` (`lignes 378-435`). Si la Phase 2 (`freshAnalysis`, `lignes 442-456` — par exemple si une période d'évaluation ou année scolaire a été verrouillée entre l'upload et l'exécution) ou la Phase 3 lève une exception, aucun bloc compensatoire ne fait basculer le lot en statut terminal `FAILED`.
- **Risque** : Le lot reste figé indéfiniment en statut `PROCESSING` (impossible à ré-exécuter ou annuler).
- **Correction précise** : Encadrer les Phases 2 et 3 d'un `try/catch (RuntimeException ex)` ouvrant une transaction `REQUIRES_NEW` compensatoire (sous `TenantContext.executeWithTenant`) pour passer le lot à `FAILED`, enregistrer `ImportJobEventType.FAILED` et journaliser l'échec dans `audit_logs` avant de relancer `ex`.
- **Test à ajouter** : test d'intégration dans `SchoolImportIntegrationTest` simulant une exception en Phase 2 (ex. période verrouillée après upload) et vérifiant que le lot passe à `FAILED`.

##### `P3-024` — `[MINEUR]` — Tests manquants pour les limites ZIP-bomb et la concurrence sur `executeJob` / séquences
- **Identifiant** : `P3-024`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `schoolimport` — Axe D
- **Localisation** : `backend/src/test/java/com/ecole/saas/schoolimport/infrastructure/SecureTabularFileParserTest.java:58-93`, `backend/src/test/java/com/ecole/saas/SchoolImportIntegrationTest.java`
- **Constat factuel** : Les garde-fous anti-ZIP-bomb (`MAX_XLSX_UNCOMPRESSED_BYTES = 20 Mo`, `MAX_XLSX_ENTRIES = 100`) codés dans `SecureTabularFileParser.java:256-282` n'ont pas de test unitaire dédié, et `SchoolImportIntegrationTest` ne teste pas la concurrence multi-thread sur `executeJob` (`PESSIMISTIC_WRITE`) ni `ImportSequenceRepository`.
- **Risque** : Régression non couverte sur la protection ZIP-bomb et la sérialisation d'exécution concurrente.
- **Correction précise** : Ajouter deux tests unitaires ZIP-bomb (> 100 entrées et > 20 Mo décompressés) dans `SecureTabularFileParserTest` et un test concurrent `executeJob` dans `SchoolImportIntegrationTest`.

##### `P3-025` — `[MINEUR]` — Collision concurrente sur `idempotency_key` d'upload et comparaison SHA-256 non temps-constant
- **Identifiant** : `P3-025`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `schoolimport` — Axe B
- **Localisation** : `backend/src/main/java/com/ecole/saas/schoolimport/application/SchoolImportJobService.java:220-230, 414`
- **Constat factuel** : (1) `createAndAnalyzeJob` teste `findByIdempotencyKey` sans gestion de collision concurrente sur `uq_schoolimport_jobs_tenant_idempotency` (`409` au lieu de `200` replay) ; (2) la `ligne 414` compare `computedSha256.equalsIgnoreCase(job.getFileSha256())` au lieu d'une comparaison en temps constant `MessageDigest.isEqual`.
- **Risque** : `409` sur double upload strictement simultané (sans doublon en base) et incohérence mineure de comparaison cryptographique.
- **Correction précise** : Poser un `pg_advisory_xact_lock` ou rattraper `DataIntegrityViolationException` en création, et utiliser `MessageDigest.isEqual` à la ligne 414. **Test à ajouter** : test concurrent upload idempotent.

#### Points `[CONFORME]` vérifiés — `schoolimport`

| # | Exigence vérifiée | Preuve (`fichier:ligne`) |
|---|---|---|
| 1 | Transactions courtes hors réseau & découpage 3 phases (`REQUIRES_NEW` + `TenantContext` par ligne en `PARTIAL_SKIP_ERRORS`) | `SchoolImportJobService.java:202-217,378-551`, `SchoolImportRowExecutor.java:173-196,251-291` |
| 2 | Limites strictes fichier/parsing (`5 Mo`, `20 Mo`, `100` entrées, `2 000` lignes, `50` col., `1 000` car.) + `try-with-resources` | `ImportFileSecurityPolicy.java:22-27`, `SecureTabularFileParser.java:57-120,178-184,252-295,333-358` |
| 3 | Détection par type réel (octets magiques + UTF-8 strict sans NUL) & anti-path-traversal | `ImportFileSecurityPolicy.java:40-80,127-163`, `SecureTabularFileParser.java:263-268` |
| 4 | Durcissement anti-XXE complet (`disallow-doctype-decl`, entités/DTD désactivées, `FEATURE_SECURE_PROCESSING`) | `SecureTabularFileParser.java:413-432`, `SecureTabularFileParserTest.java:59-70` |
| 5 | Idempotence création + exécution (`UNIQUE` + `execution_idempotency_key` + `PESSIMISTIC_WRITE`) | `V14__schoolimport_module.sql:121,170-172`, `SchoolImportJobService.java:220-230,383-399` |
| 6 | Intégrité SHA-256 upload/stockage/exécution + masquage PII (`[MASQUE]`) dans `row_preview_json` | `ImportJobFile.java:74-78`, `ImportRowSanitizer.java:17-76`, `SchoolImportJobService.java:204,413-418` |
| 7 | RBAC fin (`SECRETARY`/`CENSOR`/`TEACHER`/`TUTOR`) + quotas `STUDENTS` & `STORAGE_MB` partagé | `SchoolImportAccessGuard.java:21-108`, `SchoolImportSubscriptionGuard.java:39-75` |
| 8 | Préchargement batch zéro N+1 + `saveAll` + agrégations SQL + zéro `float`/`double` + RLS `V14` + `audit_logs` | `SchoolImportRowAnalyzer.java:256-294,474-503,655-668`, `SchoolImportJobService.java:311,497,695-720`, `ImportFieldParser.java:248-270`, `V14__schoolimport_module.sql:305-348` |

---

### 2.5 Module `reporting` (47 fichiers Java + `V15` + `ADR 0009`)

#### A. Disponibilité & Résilience
- **Timeouts & clients externes** : aucun appel réseau externe ; stockage abstrait derrière `ReportSnapshotBinaryStorage` et implémenté en base par `DatabaseReportSnapshotBinaryStorage`.
- **Transactions courtes hors réseau** : aucun appel réseau dans `@Transactional`.
- **Limites mémoire, pagination & plages de dates** : snapshots/exports bornés à `5 Mo` (`ReportSecurityPolicy.MAX_SNAPSHOT_SIZE_BYTES = 5242880`, `V15:132, 175`), pagination bornée à `100` (`ReportingPagination.java:11`), plage de dates bornée à `366` jours (`ReportingMetricsCalculator.java:14, 20-34`, avec point mineur `P3-026` sur borne semi-ouverte). Aucun flux non fermé.

#### B. Sécurité, Intégrité Financière & Flux Fichiers
- **Idempotence** : `UNIQUE (tenant_id, idempotency_key)` (`V15:115`) + empreinte canonique `requestHash` incluant `actorUserId` (`ReportSecurityPolicy.java:59-83`, `ReportSnapshotService.java:132-154`, point mineur concurrent `P3-029`).
- **Immutabilité & zéro `float`/`double`** : 100 % des montants FCFA en `long`/`BIGINT` (`ReportingModels`, `ReportingMetricsCalculator.addXof` avec `Math.addExact`), taux en `BigDecimal` (`RoundingMode.HALF_UP`, 2 décimales). Tables `reporting_snapshot_contents` et `reporting_snapshot_events` strictement en ajout seul (`@PreUpdate`/`@PreRemove`, dépôts sans `delete`, privilèges `ecole_app` `SELECT, INSERT`, triggers `BEFORE UPDATE OR DELETE` `V15:182-184, 210-212`).
- **Anti-CSV-Injection** : `CsvFormulaSanitizer` (`16-88`) neutralise systématiquement `=`, `+`, `-`, `@`, `\t`, `\r` y compris après espaces, tabulations et sauts de ligne initiaux, supprime `\0` et applique l'échappement RFC 4180 sur les 6 exports CSV de `ReportPayloadSerializer`.
- **Intégrité SHA-256 & nommage sûr** : empreinte SHA-256 calculée et vérifiée à l'écriture et au téléchargement via `MessageDigest.isEqual` en temps constant (`ReportSecurityPolicy.java:113-126`, `DatabaseReportSnapshotBinaryStorage.java:31, 46`), retournée dans `X-Report-Sha256` (`ReportingController.java:164`) ; anti-path-traversal (`buildSafeFileName`, `validateStorageObjectKey`).
- **RBAC, périmètre fin & `SubscriptionAccessPort`** : `@PreAuthorize` sur toutes les méthodes applicatives (`ReportingRoles.java:7-31`), `ReportingAccessScopeGuard` (`27-240`) cloisonnant `CENSOR` (`canSeeStudentFinance = false`), `TEACHER`/`TUTOR` (`canSeeStudentFinance = false`, propres snapshots uniquement), `PARENT` (enfants rattachés), `STUDENT` (soi-même) et excluant `ADMIN_SAAS`. `ReportingSubscriptionGuard` vérifie `read`/`write` et le quota partagé `STORAGE_MB` via `currentUsage` (point mineur `P3-027` sur `school.active()`).
- **Architecture & RLS** : domaine pur sans Spring, injection constructeur, aucune `@Entity` exposée, `Clock` + `Africa/Dakar`, 4 tables `V15` avec RLS fail-closed et FKs composites.

#### C. Performance & Scalabilité
- Agrégations consolidées via les ports amont sans N+1 côté `reporting`, et `computeFinancialClassBreakdowns` (`ReportingQueryService.java:653-715`) par préchargement batch (`findActiveEnrollmentsByClassRooms` + `getStudentFinancialSummariesByIds`). Index composites préfixés par `tenant_id` dans `V15:70-71, 141-154, 179-180, 205-208`. Seul un appel `.size()` sur liste de documents est relevé en `P3-028` (et la performance de l'implémentation amont `FinanceAccountQueryService` est traitée en `P3-004`).

#### D. Audit, Gestion d'Erreurs & Preuves de tests
- `AuditLogPort` (`AuditCategory.REPORTING`) tracé sur export, génération, téléchargement et archivage sans PII sensible. `Reporting*Exception` -> `ProblemDetail` RFC 9457 en français, zéro PII dans les logs.
- `ReportingIntegrationTest` (1 271 lignes) + 3 tests unitaires très complets, sous réserve d'un test concurrent séquences/archivage (`P3-030`).

#### Constats du module `reporting` (`P3-026` .. `P3-030`)

##### `P3-026` — `[MINEUR]` — Plage de dates semi-ouverte non bornée dans `ReportingMetricsCalculator.validateDateRange`
- **Identifiant** : `P3-026`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `reporting` — Axe A
- **Localisation** : `backend/src/main/java/com/ecole/saas/reporting/domain/ReportingMetricsCalculator.java:20-34`
- **Constat factuel** : `validateDateRange(fromDate, toDate)` n'applique `MAX_DATE_RANGE_DAYS = 366L` que lorsque `fromDate` et `toDate` sont tous deux non nuls. Une requête ne passant qu'une seule borne (sans `academicYearId`) reste non bornée sur l'autre extrémité.
- **Risque** : Agrégation sur une plage temporelle ouverte lors d'un appel avec une seule date.
- **Correction précise** : Exiger que `fromDate` et `toDate` soient fournis conjointement (ou borner implicitement la borne manquante à 366 jours). **Test à ajouter** : test unitaire dans `ReportingMetricsCalculatorTest`.

##### `P3-027` — `[MINEUR]` — Absence de vérification `school.active()` dans `ReportingSubscriptionGuard.assertWriteAllowed()`
- **Identifiant** : `P3-027`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `reporting` — Axe B
- **Localisation** : `backend/src/main/java/com/ecole/saas/reporting/application/ReportingSubscriptionGuard.java:38-40`
- **Constat factuel** : Contrairement à `SchoolDocsSubscriptionGuard.java:42-51` et `SchoolImportSubscriptionGuard.java:43-52`, `ReportingSubscriptionGuard.assertWriteAllowed()` appelle `subscriptionAccessPort.requireOperationalSubscription()` sans vérifier `requireSchoolProfile().active()`.
- **Risque** : Un établissement désactivé (`active = false`) mais encore sous abonnement actif peut générer ou archiver des snapshots.
- **Correction précise** : Contrôler `if (!requireSchoolProfile().active())` dans `assertWriteAllowed()` et lever `ReportingConflictException("school-inactive", ...)`. **Test à ajouter** : test dans `ReportingIntegrationTest`.

##### `P3-028` — `[MINEUR]` — Chargement de la liste des documents de l'élève uniquement pour appeler `.size()`
- **Identifiant** : `P3-028`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `reporting` — Axe C
- **Localisation** : `backend/src/main/java/com/ecole/saas/reporting/application/ReportingQueryService.java:573`
- **Constat factuel** : `buildStudentOverview` appelle `schoolDocsDirectoryPort.findDocumentsByStudentId(studentId).size()` pour obtenir le nombre de documents émis pour un élève.
- **Risque** : Chargement et mapping inutiles des entités documentaires pour un simple comptage.
- **Correction précise** : Ajouter `countDocumentsByStudentId(UUID studentId)` sur `SchoolDocsDirectoryPort` adossé à une requête JPQL `COUNT`. **Test à ajouter** : test de non-régression sur `buildStudentOverview`.

##### `P3-029` — `[MINEUR]` — Collision concurrente d'idempotence sur `generateSnapshot` retournant `409` au lieu de `200`
- **Identifiant** : `P3-029`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `reporting` — Axe B
- **Localisation** : `backend/src/main/java/com/ecole/saas/reporting/application/ReportSnapshotService.java:144-154, 221`
- **Constat factuel** : `generateSnapshot` vérifie `findByIdempotencyKey` avant `save` sans verrou `pg_advisory_xact_lock` ni capture de `DataIntegrityViolationException` sur `uq_reporting_snapshots_tenant_idempotency`.
- **Risque** : Un double appel strictement simultané avec même `Idempotency-Key` reçoit `409 data-conflict` au lieu de `200 X-Idempotent-Replay: true` (aucun doublon en base).
- **Correction précise** : Poser un `pg_advisory_xact_lock` ou rattraper `DataIntegrityViolationException` pour relire le snapshot existant et renvoyer le rejeu idempotent. **Test à ajouter** : test multi-thread dans `ReportingIntegrationTest`.

##### `P3-030` — `[MINEUR]` — Absence de test multi-thread de concurrence sur `ReportSequenceRepository` et `archiveSnapshot`
- **Identifiant** : `P3-030`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `reporting` — Axe D
- **Localisation** : `backend/src/test/java/com/ecole/saas/ReportingIntegrationTest.java:402-498`
- **Constat factuel** : Aucun test concurrent multi-thread ne sollicite simultanément `ReportSequenceRepository.nextSequenceValue` ni `ReportSnapshotService.archiveSnapshot` (`@Lock(PESSIMISTIC_WRITE)`).
- **Risque** : Régression de concurrence sur séquences ou archivage non couverte par la suite de tests.
- **Correction précise** : Ajouter un test multi-thread dans `ReportingIntegrationTest` pour la génération concurrente de numéros de snapshot et l'archivage parallèle (un `200`, un `409`).

#### Points `[CONFORME]` vérifiés — `reporting`

| # | Exigence vérifiée | Preuve (`fichier:ligne`) |
|---|---|---|
| 1 | Zéro `float`/`double`, FCFA strictement en `long`/`BIGINT` (`Math.addExact`), taux en `BigDecimal` `HALF_UP` | `ReportingModels.java:115-207`, `ReportingMetricsCalculator.java:40-71`, `ArchitectureTest.java:103-125` |
| 2 | Anti-CSV-Injection complet (y compris après espaces/tabulations/retours ligne initiaux) sur les 6 exports CSV | `CsvFormulaSanitizer.java:16-88`, `ReportPayloadSerializer.java:172,218,282,310,334,369`, `CsvFormulaSanitizerTest.java:13-23` |
| 3 | Idempotence snapshots (`UNIQUE (tenant_id, idempotency_key)` + `requestHash` incluant `actorUserId`) | `V15__reporting_module.sql:115`, `ReportSecurityPolicy.java:59-83`, `ReportSnapshotService.java:132-154` |
| 4 | Immutabilité append-only `contents` & `events` + verrou `PESSIMISTIC_WRITE` sur archivage | `ReportSnapshotContent.java:77-85`, `ReportSnapshotEvent.java:75-83`, `ReportSnapshotService.java:368-374`, `V15__reporting_module.sql:182-184,210-212,251-254` |
| 5 | Intégrité SHA-256 à l'écriture et au téléchargement (`MessageDigest.isEqual`) + en-tête `X-Report-Sha256` + anti-path-traversal | `ReportSecurityPolicy.java:113-126`, `DatabaseReportSnapshotBinaryStorage.java:31,46`, `ReportingController.java:164` |
| 6 | RBAC `@PreAuthorize` + périmètre fin (`CENSOR`, `TEACHER`/`TUTOR`, `PARENT`, `STUDENT`, masquage finance, exclusion `ADMIN_SAAS`) | `ReportingRoles.java:9-28`, `ReportingAccessScopeGuard.java:60-199`, `ReportingIntegrationTest.java:527-666` |
| 7 | Garde `SubscriptionAccessPort` lecture/écriture + quota partagé `STORAGE_MB` via `currentUsage` | `ReportingSubscriptionGuard.java:34-49`, `ReportingStorageQuotaProvider.java:16-34` |
| 8 | Agrégations consolidées sans N+1 + préchargement batch + RLS `V15` fail-closed + `audit_logs` `REPORTING` sans PII | `ReportingQueryService.java:235-715`, `V15__reporting_module.sql:218-254`, `ReportExportService.java:124-130`, `ReportSnapshotService.java:246-252,341-351,385-393` |

---

## 3. Matrice de conformité transverse

| Exigence transverse | `finance` | `communication` | `schooldocs` | `schoolimport` | `reporting` |
|---|---|---|---|---|---|
| **1. Timeouts passerelles & clients externes** | **KO** (`P3-001` : aucune passerelle Mobile Money ni timeout HTTP) | **KO** (`P3-011` : stubs locaux uniquement, zéro timeout HTTP/SMTP) | **N/A** (stockage DB local ; interface prête S3) | **N/A** (stockage DB local ; zéro client HTTP) | **N/A** (stockage DB local ; zéro client HTTP) |
| **2. Transactions courtes hors réseau** | **OK** (zéro appel réseau dans `@Transactional`) | **OK** (découpage strict en 3 phases vérifié) | **OK** (zéro appel réseau) | **PARTIEL** (`REQUIRES_NEW` par phase/ligne OK ; `P3-023` blocage `PROCESSING` si échec Phase 2) | **OK** (zéro appel réseau) |
| **3. Limites mémoire / parsing / streams** | **PARTIEL** (pagination `1..100` OK ; `P3-004` chargement mémoire `getFinancialReportingOverview`) | **PARTIEL** (pagination `1..100` OK ; `P3-014` audiences globales & `markAllAsRead` non bornés) | **OK** (`5 Mo` PDF, `20k`/`30k` car., `50` vars) | **PARTIEL** (`5 Mo`, `20 Mo`, `100` entrées, `2 000` lignes + `try-with-resources` OK ; `P3-022` `application.yml` `1MB` par défaut) | **PARTIEL** (`5 Mo`, page `<= 100` OK ; `P3-026` plage semi-ouverte) |
| **4. Idempotence** | **OK** (`UNIQUE` + `PESSIMISTIC_WRITE` ; webhook absent `P3-001`) | **PARTIEL** (`UNIQUE` + SHA-256 OK ; `P3-013` race concurrente) | **OK** (`UNIQUE` + `requestHash` ; `P3-019` mineur concurrence) | **OK** (`UNIQUE` création + exécution ; `P3-025` mineur concurrence) | **OK** (`UNIQUE` + `requestHash` ; `P3-029` mineur concurrence) |
| **5. Immutabilité (financière / événements / contenus)** | **OK** (5 tables : trigger SQL + `ecole_app` `SELECT, INSERT` + `@PreUpdate`/`@PreRemove`) | **OK** (`communication_delivery_events` : trigger SQL + `SELECT, INSERT` + `@PreUpdate`/`@PreRemove`) | **OK** (`contents` & `events` : triggers SQL + `SELECT, INSERT` + `@PreUpdate`/`@PreRemove`) | **OK** (`files` & `events` : triggers SQL + `SELECT, INSERT` + `@PreUpdate`/`@PreRemove`) | **OK** (`contents` & `events` : triggers SQL + `SELECT, INSERT` + `@PreUpdate`/`@PreRemove`) |
| **6. Zéro `float` / `double` (FCFA `long`/`BIGINT`)** | **OK** (100 % `long`/`BIGINT` + `MoneyMath`) | **OK** (100 % `long`/`BIGINT` + `SmsSegmentCalculator`) | **OK** (100 % `long`/`BIGINT`) | **OK** (100 % `long` dans `ImportFieldParser`) | **OK** (100 % `long`/`BIGINT` + `BigDecimal` taux) |
| **7. Anti-CSV-Injection** | **N/A** (aucun export CSV/XLSX) | **N/A** (aucun export CSV/XLSX) | **N/A** (PDF uniquement) | **KO** (`P3-021` : espaces/blancs initiaux non filtrés) | **OK** (`CsvFormulaSanitizer` complet sur les 6 exports) |
| **8. Anti-XXE / Anti-ZIP-Bomb / Magic bytes & path-traversal** | **N/A** (aucun parseur/stockage fichier) | **N/A** (aucun parseur/stockage fichier) | **OK** (`%PDF-`, anti-`..`, jeton opaque) | **OK** (magic bytes, XXE désactivé, `20 Mo`/`100` entrées, anti-`..` ; `P3-024` test ZIP-bomb) | **OK** (anti-`..`, JSON/CSV) |
| **9. Intégrité SHA-256** | **N/A** (vérification reçus par jeton opaque `SecureRandom`) | **OK** (`request_hash` SHA-256 sur campagnes) | **OK** (`MessageDigest.isEqual` à l'écriture et lecture ; `P3-020` mineur `verifyByToken`) | **OK** (upload, stockage, pré-exécution ; `P3-025` mineur `equalsIgnoreCase`) | **OK** (`MessageDigest.isEqual` + `X-Report-Sha256`) |
| **10. Agrégations SQL & Zéro N+1** | **KO** (`P3-004` : `getFinancialReportingOverview` & solde élève en mémoire) | **OK** (`getSummary()`, incréments atomiques, batch & `saveAll`) | **OK** (agrégations JPQL + préchargement batch) | **OK** (préchargement batch + `saveAll` + SQL `COUNT`/`SUM`) | **OK** (agrégations & batch ; `P3-028` mineur `.size()`) |
| **11. Journal `audit_logs` sans PII** | **PARTIEL** (`P3-005` : `updateFeeItem` & `activateFeeItem` non audités) | **OK** (actions critiques auditées sans PII) | **OK** (actions critiques auditées sans PII ni jeton) | **OK** (actions critiques auditées sans PII) | **OK** (exports, générations, téléchargements, archivages sans PII) |

---

## 4. Contrôle Quality Gate (`.agents/rules/quality-gate.md`) — Périmètre Passe 3

| Section Quality Gate | Statut | Synthèse vérifiée & constats associés |
|---|---|---|
| **1. Sécurité & Multi-Tenant (Bloquant)** | **KO** | **Points conformes** : `TenantContext` exclusivement issu du JWT ; `V7` et `V11`–`V15` conformes (`tenant_id`, FK/UNIQUE composites, RLS `ENABLE` + `FORCE` fail-closed, `ecole_app` moindre privilège, triggers append-only) ; `application-test.yml` hors `src/main/resources` ; zéro PII dans les logs et `audit_logs`. **Points KO** : `P3-001` (absence webhook Mobile Money signé), `P3-002` (`FinanceAccountQueryService` sans `@PreAuthorize`), `P3-009` (`ConsentPolicy` contournant `marketingOptIn = false`), `P3-011` (absence webhook opérateur signé dans `communication`), `P3-021` (CSV-Injection après espaces initiaux dans `schoolimport`). |
| **2. Intégrité, Concurrence & Cohérence (Bloquant)** | **KO** | **Points conformes** : zéro `float`/`double` (100 % `long`/`BIGINT`), `Clock` injecté + fuseau `Africa/Dakar`, `@Lock(PESSIMISTIC_WRITE)` / `@Version` sur les mutations critiques, Maker-Checker financier conforme. **Points KO** : `P3-002` (`FinanceAccountQueryService` sans `SubscriptionAccessPort`), `P3-003` (`CreateFeeItemRequest`/`UpdateFeeItemRequest` `mandatory` sans `@NotNull`), `P3-010` (5 booléens de consentement sans `@NotNull` convertis en `true`), `P3-012` (`InAppInboxService` écriture sous `requireReadAccess()` + `CommunicationPortService`), `P3-013` (race condition idempotence/séquence `communication`), `P3-017` (`SchoolDocsSubscriptionGuard` ignorant l'usage `STORAGE_MB` des autres modules), `P3-022` (multipart `1MB` par défaut), `P3-023` (lot bloqué en `PROCESSING` si Phase 2 échoue). |
| **3. Performance & Scalabilité (Majeur)** | **KO** | **Points conformes** : `communication`, `schooldocs`, `schoolimport` et `reporting` appliquent le préchargement par lots (`find*ByIds` -> `Map`), `saveAll` et les agrégations SQL/JPQL avec index préfixés par `tenant_id`. **Points KO** : `P3-004` (`FinanceAccountQueryService` charge factures, échéances et écritures en mémoire JVM), `P3-014` (audiences globales et `markAllAsRead` non bornés), `P3-007` (2 index datés manquants en `finance`), `P3-028` (`.size()` sur liste de documents). |
| **4. Architecture & Maintenabilité (Bloquant)** | **OK** | Domaine 100 % Java pur (zéro import `org.springframework.*` dans `..domain..`) sur les 5 modules, injection par constructeur uniquement (zéro `@Autowired` sur champ), zéro cycle Spring Modulith (`ArchitectureTest`), aucune `@Entity` exposée par les contrôleurs, et traduction systématique de `BusinessException` en `ProblemDetail` RFC 9457 en français avec `requestId` (réserve mineure `P3-008` sur `FinanceApiSupport` et `P3-022` sur `MaxUploadSizeExceededException`). |
| **5. Preuve obligatoire (Bloquant)** | **KO** | **Points conformes** : 5 suites Testcontainers PostgreSQL et 13 classes de tests unitaires couvrent RLS `ecole_app`, immutabilité SQL, RBAC/périmètres, anti-XXE, SHA-256 et découplage transactionnel. **Points KO** : absence de tests unitaires `finance/application` et lacunes sur `getFinancialReportingOverview`/`updateDraftInvoice`/`@Version` (`P3-006`), absence de tests de concurrence multi-thread sur séquences/duplicatas `schooldocs` (`P3-018`) et lacunes ciblées `P3-001`, `P3-011`, `P3-016`, `P3-024`, `P3-030`. |
