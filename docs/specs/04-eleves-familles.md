# Spécification — Modules `student` et `family` (élèves, inscriptions annuelles et familles/tuteurs)

## Objectif
Gérer les dossiers élèves (avec génération séquentielle et isolée par établissement des matricules), les inscriptions annuelles et affectations aux classes (validées via `AcademicStructurePort`), le contrôle de l'état d'abonnement et des quotas élèves (via `SubscriptionAccessPort` et déclaration d'usage courant via `QuotaUsageProvider`), ainsi que les familles, parents/tuteurs légaux et liens élève-parent/tuteur.

## Acteurs et permissions
- **Directeur (`DIRECTOR`), Responsable de centre (`CENTER_MANAGER`), Secrétariat (`SECRETARY`)** :
  - Lecture et écriture complètes sur les dossiers élèves (`/api/v1/students/**`), les inscriptions (`/api/v1/enrollments/**`), les familles (`/api/v1/families/**`), les parents/tuteurs (`/api/v1/guardians/**`) et les liens élève-tuteur (`/api/v1/students/{studentId}/guardians/**`).
- **Promoteur (`PROMOTER`), Caissier (`CASHIER`), Censeur (`CENSOR`), Enseignant (`TEACHER`), Intervenant (`TUTOR`)** :
  - Lecture seule sur l'ensemble des endpoints ci-dessus ; toute écriture est refusée (`403`).
- **Administrateur SaaS (`ADMIN_SAAS`), Parent (`PARENT`), Élève (`STUDENT`)** :
  - Accès refusé (`403`) sur ces endpoints back-office établissement (les endpoints portail `PARENT`/`STUDENT` et la restriction par classes d'enseignement seront branchés avec les modules `teacher` et portail parent).
- **Autres modules applicatifs** :
  - `student` expose `StudentDirectoryPort` (`StudentSummary`, `StudentEnrollmentSummary`) et implémente `QuotaUsageProvider` (`QuotaResource.STUDENTS`).
  - `family` expose `FamilyDirectoryPort` (`FamilySummary`, `GuardianSummary`, `StudentGuardianSummary`) et consomme `StudentDirectoryPort` de façon unidirectionnelle (aucun cycle Spring Modulith).

## Règles métier
1. **Dossiers élèves (`students`) et matricules (`student_matricule_sequences`)** :
   - Format du matricule : `CODE_ECOLE-ANNEE-NNNN` (ex. `IND-2026-0001`), généré côté serveur via un compteur atomique isolé par `(tenant_id, sequence_year)`, immuable après création et unique par `(tenant_id, matricule)`.
   - Validation : prénom/nom obligatoires, genre (`MALE`, `FEMALE`, `OTHER`), date de naissance ≥ `1900-01-01` et non future, date d'admission ≥ date de naissance, téléphone optionnel au format E.164 (`+221...`), e-mail normalisé en minuscules.
   - Machine à états : `ACTIVE → TRANSFERRED | GRADUATED | WITHDRAWN | ARCHIVED` ; réactivation vers `ACTIVE` possible depuis `TRANSFERRED`, `GRADUATED`, `WITHDRAWN` ; `ARCHIVED` terminal et immuable (archivage logique uniquement, aucune suppression physique).
2. **Contrôle d'abonnement et quotas élèves (`SubscriptionAccessPort` & `QuotaUsageProvider`)** :
   - L'usage `STUDENTS` correspond au nombre d'élèves au statut `ACTIVE` dans l'établissement courant et est remonté automatiquement à `GET /api/v1/subscriptions/current/quotas`.
   - Création et réactivation d'élève vérifient `assertQuotaAvailable(QuotaResource.STUDENTS, activeCount, 1)` : `409` (`subscription-quota-exceeded`) en cas de dépassement, `403` (`subscription-access-denied`) si l'abonnement n'autorise pas l'écriture.
   - Toute écriture (`student` et `family`) exige un abonnement opérationnel (`TRIAL_ACTIVE`, `ACTIVE`, `PAST_DUE_GRACE`).
   - En abonnement `EXPIRED`, la lecture seule reste autorisée (`200`) et les écritures sont refusées (`403`). En `SUSPENDED`, `CANCELLED` ou `MISSING`, lecture et écriture sont bloquées (`403`).
3. **Inscriptions annuelles (`student_enrollments`)** :
   - Valident l'existence et le statut `ACTIVE` de l'élève, l'existence et la mutabilité (`DRAFT`/`ACTIVE`) de l'année scolaire et le statut `ACTIVE` de la classe via `AcademicStructurePort`, ainsi que l'appartenance de la classe à l'année scolaire demandée.
   - Au plus une inscription `ACTIVE` par `(tenant_id, student_id, academic_year_id)` (index unique partiel PostgreSQL).
   - Contrôle strict de la capacité de la classe (`effectif actif < capacity`, sinon `409 classroom-capacity-exceeded`), à l'inscription comme lors d'un changement de classe (`transfer-class`) au sein de la même année.
   - Transitions d'inscription : `ACTIVE → COMPLETED | WITHDRAWN | TRANSFERRED | CANCELLED` (refusées sur année clôturée/archivée).
4. **Familles (`families`), parents/tuteurs (`guardians`) et liens (`student_guardians`)** :
   - Code famille optionnel normalisé en majuscules, unique par `(tenant_id, code)` lorsqu'il est renseigné.
   - Téléphone principal du parent/tuteur obligatoire au format E.164 (`+221...`), téléphone secondaire optionnel E.164, rattachement optionnel à une famille `ACTIVE`.
   - Lien unique par `(tenant_id, student_id, guardian_id)`, archivage/désactivation logique uniquement.
   - Au plus **1 contact principal actif** (`primary_contact = true`) et **1 responsable financier actif** (`financial_responsible = true`) par élève (index uniques partiels PostgreSQL + bascule atomique en transaction).
5. **Isolation multi-tenant** :
   - Tenant déduit exclusivement du JWT côté serveur (`TenantContext`), jamais d'un en-tête, paramètre ou corps client.
   - Défense en profondeur : Hibernate `@TenantId` + RLS PostgreSQL activée et forcée (`fail-closed`) + clés étrangères composites `(tenant_id, <fk_id>)` sur les 6 tables de `V6`.

## Modèle de données (migration `V6__students_and_families.sql`)
- `student_matricule_sequences`
- `students`
- `student_enrollments`
- `families`
- `guardians`
- `student_guardians`

## Critères d'acceptation
- Deux établissements A et B génèrent des matricules séquentiels indépendants (`CODEA-2026-0001`, `CODEB-2026-0001`).
- L'atteinte du plafond `maxStudents` bloque la création ou réactivation suivante (`409 subscription-quota-exceeded`), et `GET /api/v1/subscriptions/current/quotas` reflète l'usage réel `STUDENTS`.
- Un abonnement `EXPIRED` autorise les lectures `GET` (`200`) et bloque les écritures (`403`) ; un abonnement `SUSPENDED` ou absent bloque lecture et écriture (`403`).
- Une inscription dans une classe pleine, archivée, appartenant à une autre année ou sur une année clôturée est refusée (`409` / `400`).
- La désignation d'un nouveau contact principal ou responsable financier bascule automatiquement l'ancien lien dans la même transaction.
- Un utilisateur de l'établissement A ne peut ni lire, ni modifier, ni référencer un élève, une classe, une famille ou un tuteur de l'établissement B (`404`), même avec `X-Tenant-Id` ou `?tenantId=...` forgés.
- Sous le rôle PostgreSQL `ecole_app`, la RLS isole strictement les 6 tables de `V6` et retourne `0` ligne sans `TenantContext`.

## Hors périmètre
- Téléversement binaire des photos élèves vers S3 (champ `photo_object_key` réservé).
- Portail parent/élève (`/api/v1/me/**`), génération de liens d'accès parent temporaires, et filtrage fin par classes affectées à un enseignant (module `teacher`).
