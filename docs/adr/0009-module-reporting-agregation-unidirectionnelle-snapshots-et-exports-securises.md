# ADR 0009 — Module `reporting` : agrégation unidirectionnelle sans cycle, snapshots idempotents immuables et exports protégés anti-CSV-Injection

- Statut : accepté

## Contexte
Le module `reporting` doit consolider des indicateurs multi-domaines (académique, assiduité, finance en entiers FCFA, documents scolaires, imports massifs et synthèse élève) tout en garantissant :
1. L'absence totale de cycle Spring Modulith (`ApplicationModules.verify()`) et l'interdiction absolue d'accéder directement aux tables SQL ou entités JPA des modules amont.
2. Des performances sans N+1 lors des agrégations par établissement, année scolaire, période et classe.
3. Un cloisonnement strict par tenant (JWT + `@TenantId` + RLS PostgreSQL forcée fail-closed) et par périmètre utilisateur (`TEACHER`/`TUTOR`, `PARENT`, `STUDENT`).
4. Des exports CSV protégés contre la CSV-Injection et des snapshots persistés idempotents, vérifiables par SHA-256 et comptabilisés dans le quota `STORAGE_MB`.

## Décision
1. **Enrichissements en lecture seule (batch / JPQL) des ports publics amont** :
   - `reporting` consomme exclusivement les interfaces publiques existantes (`SchoolDirectory`, `SubscriptionAccessPort`, `AcademicStructurePort`, `StudentDirectoryPort`, `FamilyDirectoryPort`, `TeacherDirectoryPort`, `GradeSummaryPort`, `AttendanceSummaryPort`, `FinanceAccountPort`, `SchoolDocsDirectoryPort`, `SchoolImportDirectoryPort`, `AuditLogPort`).
   - Des méthodes agrégées en lecture seule (préchargement batch / requêtes JPQL `GROUP BY` / `SUM` / `COUNT`, zéro N+1) sont ajoutées à ces ports amont sans jamais exposer d'entité JPA ni créer de dépendance vers `reporting`.
   - `reporting` implémente `QuotaUsageProvider` (`QuotaResource.STORAGE_MB`) pour partager fidèlement le quota de stockage avec `schooldocs` et `schoolimport`.
2. **Domaine pur sans Spring ni flottants** :
   - `ReportingMetricsCalculator`, `CsvFormulaSanitizer` et `ReportSecurityPolicy` vivent dans `com.ecole.saas.reporting.domain` en Java pur.
   - Tous les montants FCFA restent en `long` / `BIGINT` (`Math.addExact`) et tous les taux/moyennes en `BigDecimal` (`RoundingMode.HALF_UP`, 2 décimales), vérifié automatiquement par `ArchitectureTest`.
   - Toute cellule CSV débutant par `=`, `+`, `-`, `@`, `\t` ou `\r` (y compris après espaces initiaux) est neutralisée par préfixage `'`.
3. **Snapshots persistés idempotents et immuables (`V15__reporting_module.sql`)** :
   - Chaque génération de snapshot exige une `Idempotency-Key` unique par `(tenant_id, idempotency_key)` avec contrôle d'empreinte SHA-256 canonique (`200 OK` + `X-Idempotent-Replay: true` sur rejeu identique, `409 Conflict` sur conflit).
   - Les octets JSON/CSV (`reporting_snapshot_contents`) et le journal d'événements (`reporting_snapshot_events`) sont strictement append-only (entités sans setters avec `@PreUpdate`/`@PreRemove`, dépôts sans `delete*`, privilèges `ecole_app` limités à `SELECT, INSERT` et triggers SQL `BEFORE UPDATE OR DELETE`).
   - L'archivage logique de `reporting_snapshots` acquiert `@Lock(PESSIMISTIC_WRITE)` (+ `@Version`) et exige un motif tracé, sans jamais supprimer physiquement de snapshot.

## Conséquences
- Graphe Spring Modulith strictement acyclique ; zéro dépendance tierce ajoutée à `pom.xml`.
- Défense en profondeur complète : JWT + `@PreAuthorize` + `ReportingAccessScopeGuard` + `@TenantId` + RLS PostgreSQL forcée + triggers d'immutabilité + intégrité SHA-256 + audit `AuditCategory.REPORTING`.
- Migration ultérieure du stockage binaire des snapshots vers un stockage objet S3 privé possible sans impact sur `domain`, `application` ni `api`.
