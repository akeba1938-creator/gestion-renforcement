# Spécification — Module `reporting` (synthèses consolidées, snapshots idempotents et exports sécurisés)

## Objectif
Fournir aux écoles privées et centres de renforcement un module de reporting multi-tenant isolé par établissement qui agrège, en lecture seule et de façon strictement unidirectionnelle, les indicateurs des modules amont (`academic`, `student`, `family`, `teacher`, `grade`, `attendance`, `finance`, `schooldocs`, `schoolimport`) :
- **Indicateurs académiques et réussite** : effectifs actifs, taux de remplissage par classe, moyennes générales/min/max, taux de réussite (`>= barème / 2`), répartition des mentions, avancement des bulletins (`DRAFT`, `VALIDATED`, `LOCKED`).
- **Assiduité et absences** : séances d'appel, présences, retards et minutes cumulées, absences justifiées/injustifiées, excusés, taux de présence global et par classe.
- **Indicateurs financiers strictement en entiers FCFA (`long` / `BIGINT`)** : facturation brute, remises et bourses approuvées/en attente, net facturé, encaissé, impayés, taux de recouvrement, échéances en retard, contre-passations, ventilation par mode de paiement, cohérence registre comptable, clôtures de caisse soumises/validées et écarts cumulés, ventilation par classe.
- **Activité opérationnelle** : documents scolaires officiels (`schooldocs` : émis, duplicatas, révoqués, expirés, frais FCFA, stockage, types) et imports massifs (`schoolimport` : lots par statut/type, lignes traitées/créées/mises à jour/ignorées/en erreur, stockage).
- **Synthèse consolidée individuelle élève** : profil non sensible, inscription, résultats scolaires, assiduité, situation financière (selon habilitation) et activité documentaire.
- **Exports directs JSON / CSV protégés contre la CSV-Injection** et **snapshots persistés immuables et idempotents** avec numérotation séquentielle `RPT-<CODE_ECOLE>-<ANNEE>-NNNNNN`, empreinte SHA-256, archivage logique et journalisation d'audit (`AuditCategory.REPORTING`).

## Acteurs et permissions
- **Promoteur (`PROMOTER`), Directeur (`DIRECTOR`), Responsable de centre (`CENTER_MANAGER`)** :
  - Accès complet aux 6 vues consolidées (`EXECUTIVE`, `ACADEMIC`, `ATTENDANCE`, `FINANCIAL`, `OPERATIONS`, `STUDENT_OVERVIEW`), aux exports JSON/CSV, aux snapshots et à l'archivage logique de snapshots.
- **Censeur (`CENSOR`) et Secrétariat (`SECRETARY`)** :
  - Accès à `ACADEMIC`, `ATTENDANCE`, `OPERATIONS` et `STUDENT_OVERVIEW` (bloc financier masqué pour `CENSOR`), ainsi qu'aux exports et snapshots de ces types.
  - Accès refusé (`403`) sur `/executive`, `/financial` et l'archivage de snapshots.
- **Caissier (`CASHIER`)** :
  - Accès à `FINANCIAL` et `STUDENT_OVERVIEW` (avec bloc financier), ainsi qu'aux exports et snapshots correspondants.
  - Accès refusé (`403`) sur `/executive`, `/academic`, `/attendance`, `/operations` et l'archivage de snapshots.
- **Enseignant (`TEACHER`) et Intervenant (`TUTOR`)** :
  - Accès **strictement limité à leurs classes activement affectées** (`TeacherDirectoryPort`) sur `ACADEMIC`, `ATTENDANCE` et `STUDENT_OVERVIEW` (élèves inscrits dans leurs classes uniquement, bloc financier masqué).
  - Snapshots visibles limités à ceux qu'ils ont eux-mêmes générés (`generatedBy == sub JWT`). Tout accès hors périmètre ou aux rapports financiers/opérations/exécutif retourne `403`.
- **Parent (`PARENT`)** :
  - Accès **strictement limité à la synthèse `STUDENT_OVERVIEW` (avec bloc financier), aux exports et à ses propres snapshots** pour ses enfants activement rattachés (`FamilyDirectoryPort`). Tout accès à un autre élève ou aux rapports établissement/classe retourne `403`.
- **Élève (`STUDENT`)** :
  - Accès **strictement limité à sa propre synthèse `STUDENT_OVERVIEW` (avec bloc financier), à son export et à ses propres snapshots** (`StudentDirectoryPort.findStudentContactByUserId`).
- **Administrateur SaaS (`ADMIN_SAAS`)** :
  - Accès refusé (`403`) sur l'ensemble des routes tenant `/api/v1/reporting/**`.

## Frontières Spring Modulith (strictement acyclique)
- **Ports consommés (unidirectionnels)** : `SchoolDirectory`, `SubscriptionAccessPort` (+ SPI `QuotaUsageProvider` sur `STORAGE_MB`), `AcademicStructurePort`, `StudentDirectoryPort`, `FamilyDirectoryPort`, `TeacherDirectoryPort`, `GradeSummaryPort`, `AttendanceSummaryPort`, `FinanceAccountPort`, `SchoolDocsDirectoryPort`, `SchoolImportDirectoryPort`, `AuditLogPort`.
- **Port public exposé** : `ReportingDirectoryPort` (`com.ecole.saas.reporting`, lecture seule).

## Règles métier et sécurité
1. **Zéro flottant et intégrité financière** : tous les montants FCFA sont strictement en `long` / `BIGINT` avec `Math.addExact` ; tous les taux et moyennes sont calculés en `BigDecimal` (`RoundingMode.HALF_UP`, 2 décimales) dans le domaine pur (`ReportingMetricsCalculator`).
2. **Protection anti-CSV-Injection (`CsvFormulaSanitizer`)** : toute cellule débutant par `=`, `+`, `-`, `@`, `\t`, `\r` (y compris après espaces ou caractères de contrôle initiaux) est neutralisée par préfixage `'` avant formatage RFC 4180. En-têtes HTTP obligatoires sur les téléchargements/exports : `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, `X-Report-Sha256`.
3. **Snapshots persistés idempotents (`reporting_snapshots`, `reporting_snapshot_contents`, `reporting_snapshot_events`)** :
   - Clé d'idempotence obligatoire (`Idempotency-Key` ou `idempotencyKey`), unique par `(tenant_id, idempotency_key)` avec empreinte SHA-256 canonique : rejeu identique → `200 OK` + `X-Idempotent-Replay: true` sans doublon ; payload différent → `409 Conflict`.
   - Numérotation séquentielle atomique `RPT-<CODE_ECOLE>-<ANNEE>-NNNNNN` résolue avec `Clock` injecté et le fuseau établissement (`Africa/Dakar` par défaut).
   - Stockage binaire privé immuable (`≤ 5 Mo`, SHA-256 contrôlé en écriture et téléchargement) comptabilisé dans `QuotaResource.STORAGE_MB`.
   - Archivage logique verrouillé (`@Lock(PESSIMISTIC_WRITE)` + `@Version`), motif obligatoire (`5..500` car.), aucune suppression physique (`REVOKE DELETE` pour `ecole_app`).
4. **Garde abonnement et isolation multi-tenant** :
   - `EXPIRED` : lecture, exports directs et téléchargements autorisés (`200`), génération et archivage de snapshots bloqués (`403`). `SUSPENDED` / `CANCELLED` / `MISSING` : tout bloqué (`403`).
   - Tenant issu exclusivement du JWT (`TenantContext`) + Hibernate `@TenantId` + RLS PostgreSQL activée et forcée (`fail-closed`) + FKs composites `(tenant_id, ...)` sur les 4 tables de `V15`.

## Modèle de données (migration `V15__reporting_module.sql`)
- Extension non destructive de `chk_audit_logs_category` et `chk_platform_audit_logs_category` (`REPORTING`).
- 4 tables métier sous RLS forcée fail-closed :
  - `reporting_sequences`
  - `reporting_snapshots` *(pas de `DELETE` pour `ecole_app`)*
  - `reporting_snapshot_contents` *(immuable, ajout seul, trigger SQL `BEFORE UPDATE OR DELETE`)*
  - `reporting_snapshot_events` *(immuable, ajout seul, trigger SQL `BEFORE UPDATE OR DELETE`)*
