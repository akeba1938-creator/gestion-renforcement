# ADR 0008 — Module `schoolimport` : parsing CSV/XLSX pur Java sécurisé, ports d'import acycliques, transactions isolées et agrégation `STORAGE_MB`

- Statut : accepté

## Contexte
Le module `schoolimport` doit permettre d'importer massivement des données scolaires (élèves, familles/tuteurs, enseignants, inscriptions, notes, présences) depuis CSV ou Excel (`.xlsx`) tout en garantissant :
1. L'absence totale de cycle Spring Modulith (`ApplicationModules.verify()`) et l'encapsulation complète des règles métier des modules amont (`student`, `family`, `teacher`, `grade`, `attendance`, `academic`, `subscription`).
2. Une sécurité stricte contre les fichiers malveillants (usurpation d'extension, XXE, ZIP bomb, CSV-Injection) sans introduire de dépendance tierce lourde non auditée dans le MVP.
3. Une fiabilité transactionnelle totale sous PostgreSQL RLS en mode tout-ou-rien (`STRICT_ALL_OR_NOTHING`) comme en mode partiel (`PARTIAL_SKIP_ERRORS`).
4. Une comptabilisation exacte du quota de stockage `STORAGE_MB` partagé entre `schooldocs` et `schoolimport`.

## Décision
1. **Ports publics d'import unidirectionnels dans les modules amont** :
   - Chaque module métier cible expose son propre port d'import (`StudentImportPort`, `FamilyImportPort`, `TeacherImportPort`, `GradeImportPort`, `AttendanceImportPort`) et des méthodes de préchargement lot sur son port de lecture (`*DirectoryPort` / `AcademicStructurePort`).
   - Les adaptateurs délèguent aux services applicatifs internes existants (`StudentService`, `StudentEnrollmentService`, `FamilyService`, `GuardianService`, `StudentGuardianService`, `TeacherService`, `EvaluationService`, `AttendanceSessionService`) afin de conserver 100 % des invariants métier, verrous consultatifs (`pg_advisory_xact_lock`), contrôles RBAC et journaux d'audit, sans aucun cycle Spring Modulith.
2. **Parseur CSV/XLSX pur Java 21 durci (`SecureTabularFileParser`)** :
   - Zéro dépendance externe ajoutée à `pom.xml`.
   - Détection du format réel par octets magiques (`PK\x03\x04` pour XLSX ; UTF-8 strict sans octet NUL ni signature binaire pour CSV), limites de volume (`5 Mo`, `20 Mo` décompressés, `100` entrées ZIP, `2 000` lignes, `50` colonnes, `1 000` car./cellule), rejet de `..`, parseur XML durci anti-XXE (`disallow-doctype-decl`), et neutralisation anti-CSV-Injection sur tous les exports CSV.
3. **Verrouillage `PESSIMISTIC_WRITE` et transactions `REQUIRES_NEW` encadrées par `TenantContext`** :
   - Les transitions `execute` et `cancel` acquièrent `@Lock(LockModeType.PESSIMISTIC_WRITE)` sur `ImportJob`.
   - En mode `PARTIAL_SKIP_ERRORS`, chaque ligne valide est appliquée dans sa propre transaction `PROPAGATION_REQUIRES_NEW` encadrée par `TenantContext.executeWithTenant(tenantId, ...)` afin que `TenantAwareJpaTransactionManager` exécute `set_config('app.current_tenant_id', ..., true)` sur chaque connexion et qu'un échec ponctuel ne corrompe jamais le reste du lot.
4. **Agrégation multi-fournisseurs de `QuotaUsageProvider` dans `subscription`** :
   - `TenantSubscriptionService` et `SubscriptionGuardService` agrègent désormais `List<QuotaUsageProvider>` par `QuotaResource` par addition exacte (`Math.addExact`), permettant à `SchoolDocsStorageQuotaProvider` et `SchoolImportStorageQuotaProvider` de partager fidèlement le quota `STORAGE_MB`.

## Conséquences
- Graphe Spring Modulith strictement acyclique vérifié par `ArchitectureTest` ; zéro `org.springframework.*` dans `domain` et zéro `float`/`double` dans `schoolimport`.
- Défense en profondeur complète : JWT + `@PreAuthorize` + garde RBAC fin par type et périmètre enseignant + `@TenantId` + RLS PostgreSQL forcée + tables immuables `schoolimport_job_files` et `schoolimport_job_events` protégées par triggers SQL.
- Possibilité ultérieure de remplacer `DatabaseImportFileStorage` par un stockage S3 privé sans modifier `domain`, `application` ni `api`.
