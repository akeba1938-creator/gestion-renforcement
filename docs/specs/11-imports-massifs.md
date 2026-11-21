# Spécification — Module `schoolimport` (imports massifs de données scolaires CSV / XLSX)

## Objectif
Permettre aux écoles privées et centres de renforcement de migrer et mettre à jour massivement leurs données depuis Excel (`.xlsx`) ou CSV (`.csv`) de manière sécurisée, idempotente et traçable :
- **6 types d'imports pris en charge** : élèves (`STUDENTS`), familles/parents/tuteurs (`FAMILIES_GUARDIANS`), enseignants/intervenants (`TEACHERS`), inscriptions aux classes (`ENROLLMENTS`), notes d'évaluation (`GRADES`) et présences/feuilles d'appel (`ATTENDANCE`) ;
- **Workflow en deux temps** : téléversement sécurisé + **analyse à sec / prévisualisation** ligne par ligne sans écriture métier, puis **exécution contrôlée** (ou exécution immédiate optionnelle) ;
- **Validation stricte ligne par ligne** (`STRICT_ALL_OR_NOTHING` tout ou rien, ou `PARTIAL_SKIP_ERRORS` avec transactions `REQUIRES_NEW` isolées) et **rapports d'erreurs détaillés** consultables en pagination et exportables en CSV sécurisé ;
- **Gestion contrôlée des doublons** (`FAIL_ON_DUPLICATE`, `SKIP_DUPLICATES`, `UPSERT_UPDATE`) ;
- **Respect des quotas d'abonnement** (`STUDENTS`, `STORAGE_MB` agrégé avec `schooldocs`) et des invariants des modules cibles (capacités de classes, périodes d'évaluation `OPEN`, verrouillages) ;
- **Traçabilité complète** : numérotation séquentielle `IMP-<CODE_ECOLE>-<ANNEE>-NNNNNN`, stockage binaire immuable des fichiers source (SHA-256), journal chronologique immuable des événements et audit `AuditCategory.SCHOOLIMPORT`.

## Acteurs et permissions
- **Directeur (`DIRECTOR`), Responsable de centre (`CENTER_MANAGER`)** :
  - Accès complet (lecture, modèles, téléversement/analyse, exécution, annulation, rapport d'erreurs, synthèse) sur les 6 types d'imports.
- **Secrétariat (`SECRETARY`)** :
  - Lecture, modèles, synthèse et import/exécution/annulation uniquement sur `STUDENTS`, `FAMILIES_GUARDIANS` et `ENROLLMENTS`.
  - Toute tentative sur `TEACHERS`, `GRADES` ou `ATTENDANCE` est refusée (`403`).
- **Censeur (`CENSOR`)** :
  - Lecture, modèles, synthèse et import/exécution/annulation uniquement sur `TEACHERS`, `GRADES` et `ATTENDANCE`.
  - Toute tentative sur `STUDENTS`, `FAMILIES_GUARDIANS` ou `ENROLLMENTS` est refusée (`403`).
- **Promoteur (`PROMOTER`)** :
  - Lecture seule sur `/api/v1/schoolimport/jobs/**`, `/templates/**`, `/errors/download` et `/summary`. Toute écriture (`POST`) est refusée (`403`).
- **Enseignant (`TEACHER`) et Intervenant (`TUTOR`)** :
  - Import/exécution/annulation uniquement sur `GRADES` et `ATTENDANCE` et **strictement restreint aux classes/matières qui leur sont activement affectées** (`TeacherDirectoryPort`).
  - Consultation strictement limitée à leurs propres lots (`createdBy == sub JWT`) et aux modèles `GRADES`/`ATTENDANCE`. Tout accès hors périmètre ou au lot d'un autre utilisateur retourne `403`.
- **Caissier (`CASHIER`), Administrateur SaaS (`ADMIN_SAAS`), Parent (`PARENT`), Élève (`STUDENT`)** :
  - Accès refusé (`403`) sur l'ensemble des routes `/api/v1/schoolimport/**`.

## Frontières Spring Modulith (strictement acyclique)
- **Ports consommés (unidirectionnels)** :
  - `SchoolDirectory` (`school`), `SubscriptionAccessPort` (`subscription`) + implémentation `QuotaUsageProvider` (`STORAGE_MB`), `AuditLogPort` (`audit`), `AcademicStructurePort` (`academic`), `StudentDirectoryPort` + `StudentImportPort` (`student`), `FamilyDirectoryPort` + `FamilyImportPort` (`family`), `TeacherDirectoryPort` + `TeacherImportPort` (`teacher`), `GradeImportPort` (`grade`), `AttendanceImportPort` (`attendance`).
- **Port public exposé** : `SchoolImportDirectoryPort` (`com.ecole.saas.schoolimport`, lecture seule).

## Règles métier et sécurité
1. **Contrôle réel des fichiers (jamais par extension seule)** :
   - Taille maximale `5 Mo`, max `2 000` lignes/lot, max `50` colonnes, max `1 000` caractères/cellule.
   - CSV : rejet des octets NUL et signatures binaires (`%PDF-`, PNG, JPEG, ELF, MZ, OLE2), validation UTF-8 stricte (BOM optionnel).
   - XLSX : octets magiques ZIP `PK\x03\x04`, entrées OOXML obligatoires (`[Content_Types].xml`, `xl/workbook.xml`, feuille `xl/worksheets/sheet*.xml`), anti-ZIP-bomb (≤ `20 Mo` décompressés, ≤ `100` entrées, rejet `..`), parser XML durci anti-XXE (`disallow-doctype-decl`, entités externes désactivées).
   - Exports CSV (modèles & rapports d'erreurs) : neutralisation anti-CSV-Injection (`=`, `+`, `-`, `@`, `\t`, `\r`).
2. **Analyse à sec, préchargement lot et quotas** :
   - Zéro N+1 : toutes les références inter-modules sont préchargées par lot avant la validation ligne par ligne ; persistance par `saveAll`.
   - Montants éventuels (ex. taux horaire enseignant `hourly_rate_xof`) strictement en entiers FCFA (`long`/`BIGINT`), notes en `BigDecimal`, dates résolues via `Clock` injecté + fuseau établissement (`Africa/Dakar` par défaut), téléphones normalisés E.164 (`+221...`).
   - Masquage systématique des PII sensibles (`national_id_number`/CNI/NIN, `medical_notes`) dans `row_preview_json`, les logs et `audit_logs`.
   - Projection et contrôle strict des quotas `STUDENTS` et `STORAGE_MB` (agrégation multi-fournisseurs `schooldocs` + `schoolimport` dans `subscription`).
3. **Exécution idempotente, verrouillée et isolée** :
   - `Idempotency-Key` obligatoire à l'upload (empreinte SHA-256 fichier + paramètres) et à l'exécution (`X-Idempotent-Replay: true` sur rejeu identique, `409` en cas de conflit).
   - Verrouillage `@Lock(PESSIMISTIC_WRITE)` sur `ImportJob` lors des transitions `execute` et `cancel` (+ `@Version`).
   - En `STRICT_ALL_OR_NOTHING`, rollback intégral en cas d'erreur ; en `PARTIAL_SKIP_ERRORS`, chaque ligne valide est exécutée en transaction `REQUIRES_NEW` explicitement encadrée par `TenantContext.executeWithTenant(tenantId, ...)`.
4. **Isolation multi-tenant** :
   - Tenant déduit exclusivement du JWT (`TenantContext`) + Hibernate `@TenantId` + RLS PostgreSQL activée et forcée (`fail-closed`) + FKs composites `(tenant_id, ...)` sur les 5 tables de `V14`.

## Modèle de données (migration `V14__schoolimport_module.sql`)
- Extension non destructive de `chk_audit_logs_category` et `chk_platform_audit_logs_category` (`SCHOOLIMPORT`).
- 5 tables métier sous RLS forcée fail-closed :
  - `schoolimport_sequences`
  - `schoolimport_jobs` *(pas de `DELETE` pour `ecole_app`)*
  - `schoolimport_job_files` *(strictement immuable, ajout seul, trigger SQL `BEFORE UPDATE OR DELETE`)*
  - `schoolimport_job_rows` *(pas de `DELETE` pour `ecole_app`)*
  - `schoolimport_job_events` *(strictement immuable, ajout seul, trigger SQL `BEFORE UPDATE OR DELETE`)*
