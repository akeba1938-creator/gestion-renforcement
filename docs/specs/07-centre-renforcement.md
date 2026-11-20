# Spécification — Module `center` (mode centre de renforcement / soutien scolaire)

## Objectif
Gérer le fonctionnement dédié d'un centre de renforcement ou d'un programme de soutien scolaire isolé par tenant : catalogue de forfaits et packs de séances en FCFA entiers, attribution de packs aux élèves, groupes/cohortes de renforcement, affectation des intervenants/enseignants, inscription des élèves avec contrôle de capacité, plannings hebdomadaires récurrents sans conflit horaire, génération et conduite de séances datées, suivi individuel de présence/participation (avec consommation et restitution atomiques des crédits de séances) et synthèses pédagogiques / charge intervenants.

## Acteurs et permissions
- **Directeur (`DIRECTOR`), Responsable de centre (`CENTER_MANAGER`)** :
  - Gestion complète de tout `/api/v1/center/**` (catalogue de packs FCFA, packs élèves, cohortes, affectations intervenants, inscriptions élèves, créneaux, séances, pointages, clôtures, annulations et synthèses).
- **Censeur (`CENSOR`)** :
  - Lecture complète sur `/api/v1/center/**` et gestion pédagogique des cohortes, affectations intervenants, créneaux, séances et pointages/clôtures.
  - Aucune création/modification du catalogue tarifaire des packs ni annulation de packs élèves (`403`).
- **Secrétariat (`SECRETARY`)** :
  - Lecture complète sur `/api/v1/center/**`, inscription/sortie des élèves aux cohortes et attribution de packs existants aux élèves.
  - Aucune modification du catalogue des packs, des cohortes, des plannings ni des pointages (`403`).
- **Promoteur (`PROMOTER`) et Caissier (`CASHIER`)** :
  - Lecture seule sur `/api/v1/center/**` ; toute écriture est refusée (`403`).
- **Enseignant (`TEACHER`) et Intervenant (`TUTOR`)** :
  - Accès restreint strictement aux **cohortes et séances auxquelles ils sont activement affectés** (résolu côté serveur à partir du sujet JWT via `TeacherDirectoryPort`).
  - Peuvent consulter leurs cohortes, leurs créneaux, leurs séances, la liste des élèves inscrits à leurs cohortes, démarrer (`start`), pointer la présence/participation (`attendance`) et clôturer (`complete`) leurs séances, ainsi que consulter leur propre charge (`GET /api/v1/center/tutors/{myTeacherId}/workload`) et la synthèse des élèves inscrits dans leurs cohortes.
  - Tout accès hors périmètre affecté retourne `403` (`urn:ecole:problem:center-tutor-scope-forbidden`).
  - Aucun accès aux packs élèves (`/api/v1/center/student-packs/**`) ni à la gestion administrative (`403`).
- **Administrateur SaaS (`ADMIN_SAAS`), Parent (`PARENT`), Élève (`STUDENT`)** :
  - Accès refusé (`403`) sur ces endpoints back-office.

## Frontières publiques Spring Modulith (sans cycle)
- **Ports consommés (unidirectionnels, lecture seule)** :
  - `SchoolDirectory` (`school`) : contrôle de l'établissement actif et profil établissement.
  - `SubscriptionAccessPort` (`subscription`) : garde d'accès lecture/écriture selon l'état d'abonnement (`EXPIRED` → lecture seule `200`, écriture `403` ; `SUSPENDED`/`CANCELLED`/`MISSING` → `403`).
  - `AcademicStructurePort` (`academic`) : validation de l'année scolaire (mutable en écriture), du niveau et de la matière.
  - `StudentDirectoryPort` (`student`) : validation des élèves actifs et enrichissement des vues.
  - `TeacherDirectoryPort` (`teacher`) : validation des intervenants actifs et restriction fine `TEACHER`/`TUTOR`.
  - `GradeSummaryPort` (`grade`) et `AttendanceSummaryPort` (`attendance`) : enrichissement pédagogique optionnel dans la synthèse élève (`GET /api/v1/center/students/{studentId}/overview`).
- **Port public exposé (`com.ecole.saas.center`)** :
  - `CenterDirectoryPort` exposant `CenterGroupSummary`, `CenterGroupEnrollmentSummary`, `CenterSessionPackSummary`, `StudentCenterSummary` et `TutorCenterWorkloadSummary`.

## Règles métier principales
1. **Forfaits et packs en FCFA entiers (`center_session_packs`, `center_student_packs`)** :
   - Tous les montants (`price_xof`, `hourly_rate_xof`, rémunérations estimées) sont strictement en entiers FCFA (`long` / `BIGINT`, `>= 0`), sans `float` ni `double`.
   - Modes : `SESSION_PACK` (carnet de `1..500` séances obligatoire), `MONTHLY_FLAT` et `TERM_FLAT` (forfait période avec plafond optionnel de séances et durée optionnelle `1..365` jours).
   - Code normalisé en majuscules, unique par `(tenant_id, code)`. Archivage logique par `active = false`.
   - À l'attribution à un élève actif (`center_student_packs`), le code, le libellé, le mode et le prix FCFA sont figés par snapshot.
2. **Cohortes, intervenants et inscriptions (`center_groups`, `center_group_tutors`, `center_group_enrollments`)** :
   - Cohorte unique par `(tenant_id, academic_year_id, code)`, types `EXAM_PREP | SUBJECT_SUPPORT | HOLIDAY_CAMP | SMALL_GROUP | INDIVIDUAL`, capacité entière `1..200`.
   - Machine à états stricte : `DRAFT → ACTIVE | ARCHIVED`, `ACTIVE → COMPLETED | ARCHIVED`, `COMPLETED → ARCHIVED` (`ARCHIVED` terminal avec motif obligatoire).
   - Au plus 1 intervenant référent actif (`lead_tutor = true`) par cohorte (bascule atomique).
   - Au plus 1 inscription `ACTIVE` par `(tenant_id, group_id, student_id)` et contrôle strict de la capacité (`409 center-group-capacity-exceeded`).
3. **Plannings hebdomadaires et séances (`center_schedule_slots`, `center_sessions`)** :
   - Détection de conflits horaires (chevauchement cohorte ou intervenant) en domaine pur (`CenterScheduleConflictChecker`) sur créneaux actifs et séances non annulées (`409 center-schedule-conflict`).
   - Génération déterministe en lot (`CenterSessionGenerator`, max 93 jours / 200 séances par lot) depuis les créneaux actifs avec déduplication idempotente des séances déjà créées.
   - Cycle de vie séance : `SCHEDULED → IN_PROGRESS | COMPLETED | CANCELLED`, `IN_PROGRESS → COMPLETED | CANCELLED` (`COMPLETED` et `CANCELLED` terminaux, motif obligatoire sur annulation).
4. **Suivi de participation et consommation/restitution de packs (`center_session_attendances`)** :
   - Pointages `PRESENT`, `LATE` (`lateMinutes` obligatoire `1..300`), `ABSENT`, `EXCUSED`, avec note de participation optionnelle (`1..5`, uniquement si `PRESENT`/`LATE`) et indicateur `homeworkDone`.
   - `CenterPackConsumptionPolicy` (domaine pur) : un pointage `PRESENT`/`LATE` consomme 1 crédit sur le pack éligible (`ACTIVE → EXHAUSTED` à l'épuisement) ; une correction vers `ABSENT`/`EXCUSED` ou une annulation de séance restitue automatiquement le crédit consommé (`EXHAUSTED → ACTIVE` si non expiré).
   - `CenterParticipationCalculator` (domaine pur) : taux de présence, note moyenne de participation, taux de devoirs faits (`BigDecimal`, `HALF_UP`, 2 décimales) et rémunération estimée des intervenants en FCFA entiers (`(minutes * hourlyRateXof) / 60`).
5. **Isolation multi-tenant** :
   - Tenant déduit exclusivement du JWT côté serveur (`TenantContext`), jamais d'un en-tête, paramètre ou corps client.
   - Défense en profondeur : Hibernate `@TenantId` + RLS PostgreSQL activée et forcée (`fail-closed`) + clés étrangères composites `(tenant_id, <fk_id>)` sur les 8 tables de `V9`.

## Modèle de données (migration `V9__center_module.sql`)
8 tables isolées par `tenant_id NOT NULL`, indexées, avec unicités incluant `tenant_id`, FKs composites intra-tenant, RLS activée et forcée (`fail-closed`) et privilèges `SELECT, INSERT, UPDATE` pour `ecole_app` :
- `center_session_packs`
- `center_groups`
- `center_student_packs`
- `center_group_tutors`
- `center_group_enrollments`
- `center_schedule_slots`
- `center_sessions`
- `center_session_attendances`

## Hors périmètre
- Encaissement comptable et reçus des packs (gérés exclusivement par le module `finance` et son registre comptable immuable).
- Génération PDF des feuilles de présence ou fiches de suivi (module `schooldocs`) et notifications parents automatiques (module `communication`).
