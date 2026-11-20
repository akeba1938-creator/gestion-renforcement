# Spécification — Modules `school` et `academic` (établissement et structure académique)

## Objectif
Gérer le cycle de vie et le profil des établissements (écoles privées et centres de renforcement) ainsi que leur structure académique complète isolée par tenant : années scolaires, périodes d'évaluation (trimestres, semestres ou périodes personnalisées), niveaux, matières, classes/groupes et affectation des matières par classe (avec coefficients et volumes horaires).

## Acteurs et permissions
- **Administrateur SaaS (`ADMIN_SAAS`)** :
  - Gestion plateforme des établissements (`/api/v1/platform/schools/**`) : création, consultation, liste paginée/filtrée, mise à jour, suspension, réactivation, archivage.
  - Aucun accès direct aux ressources tenant (`/api/v1/school`, `/api/v1/academic/**` → `403`).
- **Promoteur (`PROMOTER`)** :
  - Consultation de son établissement (`/api/v1/school`, lecture seule) et de toute la structure académique (`/api/v1/academic/**`, lecture seule).
  - Toute écriture sur `/api/v1/school` ou `/api/v1/academic/**` est refusée (`403`).
- **Directeur (`DIRECTOR`) et Responsable de centre (`CENTER_MANAGER`)** :
  - Consultation et mise à jour du profil et des paramètres pédagogiques de leur établissement (`/api/v1/school`), sans pouvoir modifier `code`, `schoolType`, `currency` ni `status`.
  - Lecture et écriture complète sur toute la structure académique de leur établissement (`/api/v1/academic/**`).
- **Censeur (`CENSOR`), Secrétariat (`SECRETARY`), Caissier (`CASHIER`), Enseignant (`TEACHER`), Intervenant (`TUTOR`)** :
  - Aucun accès aux paramètres établissement (`/api/v1/school` → `403`).
  - Lecture seule sur `/api/v1/academic/**` ; toute écriture refusée (`403`).
- **Parent (`PARENT`) et Élève (`STUDENT`)** :
  - Aucun accès aux endpoints d'administration établissement et académique (`403`).
- **Autres modules applicatifs** :
  - Frontière publique `SchoolDirectory` (`com.ecole.saas.school`) exposant `SchoolSummary` et `SchoolProfileSnapshot`.
  - Frontière publique `AcademicStructurePort` (`com.ecole.saas.academic`) exposant les vues immuables (`AcademicYearSummary`, `EvaluationPeriodSummary`, `GradeLevelSummary`, `SubjectSummary`, `ClassRoomSummary`, `ClassSubjectSummary`).

## Règles métier
1. **Établissement (`schools`)** :
   - Code unique globalement, normalisé en majuscules (`^[A-Z0-9][A-Z0-9_-]{1,31}$`), immuable après création (utilisé pour les matricules).
   - Téléphone optionnel validé au format E.164 (`+221...`), fuseau horaire validé (`ZoneId`, défaut `Africa/Dakar`), devise ISO 3 lettres (défaut `XOF`), code pays ISO 2 lettres (défaut `SN`), système d'évaluation par défaut (`TRIMESTER`, `SEMESTER`, `CUSTOM`), barème de notation (`10`, `20` ou `100`, défaut `20`).
   - Machine à états stricte : `ACTIVE → SUSPENDED | ARCHIVED`, `SUSPENDED → ACTIVE | ARCHIVED`, `ARCHIVED` terminal. Suspension et archivage exigent un motif.
   - Un établissement non `ACTIVE` ne peut plus être modifié via `/api/v1/school`.
2. **Années scolaires (`academic_years`)** :
   - Uniques par `(tenant_id, code)` ; `end_date > start_date`.
   - Création en statut `DRAFT` (système de périodes initialisé par défaut depuis l'établissement si absent).
   - Machine à états stricte : `DRAFT → ACTIVE | ARCHIVED`, `ACTIVE → CLOSED`, `CLOSED → ARCHIVED`, `ARCHIVED` terminal. Jamais de suppression d'historique d'année passée.
   - Une seule année courante (`is_current = true`) par tenant (index unique partiel PostgreSQL) et uniquement sur une année `ACTIVE`. La bascule d'année courante désactive l'ancienne dans la même transaction. Clôture et archivage remettent `is_current = false`.
   - Modification interdite sur une année `CLOSED` ou `ARCHIVED`. Le changement de dates vérifie qu'aucune période d'évaluation existante n'en sort.
3. **Périodes d'évaluation (`evaluation_periods`)** :
   - Rattachées à une année scolaire du même tenant (`(tenant_id, academic_year_id)`), uniques par `(tenant_id, academic_year_id, code)` et `(tenant_id, academic_year_id, sequence_order)`.
   - Dates incluses dans `[academicYear.startDate, academicYear.endDate]`, sans chevauchement entre périodes de la même année, `end_date > start_date`, poids entier `1..100`.
   - Cohérence avec le système de l'année (`TRIMESTER` : 3 trimestres max ; `SEMESTER` : 2 semestres max ; `CUSTOM` : libre). Génération automatique de 3 trimestres ou 2 semestres disponible si aucune période n'existe encore.
   - Cycle de vie : `PLANNED → OPEN → CLOSED → LOCKED`, réouverture `CLOSED → OPEN` autorisée tant que non verrouillée, `LOCKED` terminal et immuable.
4. **Niveaux (`grade_levels`) et Matières (`subjects`)** :
   - Uniques par `(tenant_id, code)` (normalisé en majuscules).
   - Cycles couvrant écoles privées et centres de renforcement (`PRESCHOOL`, `PRIMARY`, `MIDDLE_SCHOOL`, `HIGH_SCHOOL`, `TUTORING`, `OTHER`).
   - Coefficients entiers (`1..50`), aucun flottant.
   - Archivage logique par `active = false` : un niveau ou une matière inactif ne peut plus être utilisé pour une nouvelle création/affectation, tout en préservant l'historique.
5. **Classes (`class_rooms`) et Matières de classe (`class_subjects`)** :
   - Classe unique par `(tenant_id, academic_year_id, code)`, capacité entière `1..500`, archivage/réactivation logique (`ACTIVE` / `ARCHIVED`).
   - Matière de classe unique par `(tenant_id, class_room_id, subject_id)`, coefficient entier effectif (`1..50`, par défaut celui de la matière), volume horaire hebdomadaire optionnel (`1..40`), obligatoire/facultatif, activation/désactivation logique.
   - Impossibilité de modifier/affecter sur une classe archivée ou sur une année scolaire clôturée/archivée.
6. **Isolation multi-tenant** :
   - Le tenant provient exclusivement du JWT (`TenantContext`), jamais d'un en-tête, paramètre ou corps client.
   - Défense en profondeur : Hibernate `@TenantId` + RLS PostgreSQL activée et forcée (`fail-closed`) + clés étrangères composites `(tenant_id, <fk_id>)` sur toutes les relations inter-tables de `academic`.

## Modèle de données (migration `V5__school_and_academic_structure.sql`)
- Enrichissement de `schools` et `academic_years` (sans modifier `V1` à `V4`).
- Nouvelles tables métier avec `tenant_id NOT NULL`, index, unicités incluant `tenant_id`, FKs composites intra-tenant, RLS activée + forcée et droits `ecole_app` :
  - `evaluation_periods`
  - `grade_levels`
  - `subjects`
  - `class_rooms`
  - `class_subjects`

## Critères d'acceptation
- Un `ADMIN_SAAS` peut créer, lister, modifier, suspendre, réactiver et archiver un établissement ; un code dupliqué retourne `409` (`urn:ecole:problem:school-already-exists`).
- Un `DIRECTOR` ou `CENTER_MANAGER` peut consulter et mettre à jour son propre établissement via `/api/v1/school` (sans pouvoir altérer `code`, `schoolType` ni `status`) ; un `PROMOTER` a lecture seule ; les autres rôles établissement et `ADMIN_SAAS` reçoivent `403`.
- Un `DIRECTOR` ou `CENTER_MANAGER` peut gérer le cycle de vie complet d'une année scolaire, générer ou créer des trimestres/semestres sans chevauchement ni débordement de dates, gérer niveaux, matières, classes et matières de classe ; une année `CLOSED`/`ARCHIVED` ou une période `LOCKED` refuse toute modification (`409`).
- Deux établissements A et B peuvent utiliser les mêmes codes (`2026-2027`, `T1`, `6E`, `MATH`, `6E-A`). A ne peut ni lire, ni modifier, ni référencer les ressources de B (`404`), même en forgeant `X-Tenant-Id` ou `?tenantId=...`.
- Sous `ecole_app` (PostgreSQL RLS), toutes les tables métier isolent `SELECT`/`UPDATE`/`DELETE`, bloquent tout `INSERT` cross-tenant et retournent `0` ligne sans `TenantContext`.

## Hors périmètre
- Téléversement binaire du logo établissement sur S3 (seul le champ `logo_object_key` est réservé en base).
- Affectation des enseignants aux classes/matières (module `teacher`), inscriptions d'élèves (module `student`), saisies de notes et bulletins (module `grade`).
