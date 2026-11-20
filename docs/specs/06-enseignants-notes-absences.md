# Spécification — Modules `teacher`, `grade` et `attendance` (enseignants, notes/bulletins et assiduité)

## Objectif
Gérer les dossiers enseignants et intervenants (écoles privées et centres de renforcement), leurs spécialités et affectations aux classes/matières par année scolaire, les évaluations, la saisie et validation des notes, le calcul des moyennes, rangs et appréciations de période/bulletin, ainsi que les séances d'appel, pointages, justifications d'absences et synthèses d'assiduité — avec restriction fine des rôles `TEACHER`/`TUTOR` à leurs classes/matières affectées et isolation multi-tenant stricte.

## Acteurs et permissions
- **Directeur (`DIRECTOR`), Responsable de centre (`CENTER_MANAGER`)** :
  - Gestion complète de `teacher`, `grade` (dont verrouillage des évaluations et bulletins) et `attendance` (dont verrouillage et annulation des séances d'appel).
- **Censeur (`CENSOR`)** :
  - Gestion complète de `teacher`, gestion des évaluations/notes/calcul et validation des bulletins (verrouillage final réservé à `DIRECTOR` / `CENTER_MANAGER`), et gestion complète de `attendance`.
- **Secrétariat (`SECRETARY`)** :
  - Lecture seule sur `teacher`, `grade` et `attendance`, plus justification des absences et retards (`POST /api/v1/attendance/records/{recordId}/justify`).
- **Promoteur (`PROMOTER`)** :
  - Lecture seule sur `teacher`, `grade` et `attendance`.
- **Enseignant (`TEACHER`) et Intervenant (`TUTOR`)** :
  - `teacher` : accès uniquement à son propre dossier (`GET /api/v1/teachers/me`) et à ses propres affectations (`GET /api/v1/teachers/me/assignments`).
  - `student` : lecture restreinte aux seuls élèves et inscriptions de ses classes affectées (`TeacherDirectoryPort`).
  - `grade` : création/modification d'évaluations, saisie de notes (en `DRAFT`, sur période `OPEN`) et rédaction des appréciations matière uniquement sur ses `(classRoomId, subjectId)` affectés ; appréciation professeur principal uniquement sur sa classe principale (`homeroomTeacher = true`).
  - `attendance` : création de séances, pointage, soumission et consultation uniquement sur ses classes affectées (et `(classRoomId, subjectId)` si une matière est précisée).
  - Tout accès hors périmètre affecté retourne `403` (`urn:ecole:problem:teacher-assignment-forbidden`).
- **Caissier (`CASHIER`), Administrateur SaaS (`ADMIN_SAAS`), Parent (`PARENT`), Élève (`STUDENT`)** :
  - Accès refusé (`403`) sur ces endpoints back-office.

## Frontières publiques Spring Modulith (sans cycle)
- `UserDirectoryPort` (`com.ecole.saas.identity`) : validation en lecture seule de l'appartenance intra-tenant d'un compte utilisateur.
- `TeacherDirectoryPort` (`com.ecole.saas.teacher`) : dossiers enseignants, affectations actives, vérification d'affectation à une classe, à un couple `(classe, matière)` ou en tant que professeur principal.
- `GradeSummaryPort` (`com.ecole.saas.grade`) : synthèses de bulletins par élève/période, par élève/année et statistiques de classe.
- `AttendanceSummaryPort` (`com.ecole.saas.attendance`) : synthèses d'assiduité par élève/période (ou plage de dates) et par classe.

## Règles métier principales
1. **Enseignants (`teachers`, `teacher_specialties`, `teacher_assignments`)** :
   - Code employé normalisé en majuscules unique par tenant ; téléphone principal E.164 obligatoire ; taux horaire optionnel en entier FCFA (`long` / `BIGINT`, `>= 0`).
   - Liaison optionnelle et unique par tenant à un compte `users` actif validé via `UserDirectoryPort`.
   - Transitions d'état : `ACTIVE ↔ SUSPENDED`, `ACTIVE | SUSPENDED → ARCHIVED` (terminal, motif obligatoire).
   - Affectations validées contre `AcademicStructurePort` (année mutable, classe `ACTIVE` appartenant à l'année, `ClassSubject` actif). Au plus un professeur principal actif par classe (bascule atomique).
2. **Notes et bulletins (`evaluations`, `student_grades`, `period_report_cards`, `period_subject_results`)** :
   - **Blocage strict sur période non ouverte** : toute création/modification/validation/annulation d'évaluation ou saisie/modification de note est strictement refusée (`409 urn:ecole:problem:evaluation-period-closed`) dès que `EvaluationPeriod.status` est `PLANNED`, `CLOSED` ou `LOCKED`.
   - Barème respectant `SchoolProfileSnapshot.gradingScale` (`10`, `20` ou `100`) : une évaluation peut avoir `0 < maxScore <= gradingScale` (ex. interrogation sur 10) et est ramenée sur `gradingScale` lors du calcul de moyenne.
   - Statuts de présence à l'évaluation : `PRESENT` (note obligatoire dans `[0, maxScore]`), `ABSENT_UNJUSTIFIED` (compte pour `0` par défaut), `ABSENT_JUSTIFIED` et `EXEMPTED` (neutres, exclus du numérateur et dénominateur).
   - Moyenne matière pondérée par poids d'évaluation, moyenne générale pondérée par coefficient sur les matières notées, arrondi à 2 décimales `HALF_UP`, classement de compétition avec ex æquo (`1, 2, 2, 4`, drapeau `tiedRank`), mentions proportionnelles au barème. Tous les calculs résident dans `com.ecole.saas.grade.domain` en Java pur sans Spring.
   - Cycle bulletin : `DRAFT → VALIDATED → LOCKED` ; toute mutation est bloquée sur bulletin `LOCKED` ou période `LOCKED`.
3. **Assiduité (`attendance_sessions`, `attendance_records`)** :
   - Feuilles d'appel par classe (et optionnellement période, matière, enseignant et créneau `MORNING | AFTERNOON | FULL_DAY | CUSTOM` avec horaires `startTime < endTime`).
   - Pointage par élève activement inscrit dans la classe (`StudentDirectoryPort`) : `PRESENT`, `LATE` (`lateMinutes` obligatoire dans `1..300`), `ABSENT`, `EXCUSED`.
   - Justification d'une absence/retard avec motif obligatoire et référence sécurisée de pièce justificative (stockage privé, sans traversée de chemin), bloquée sur séance ou période `LOCKED`.
   - Synthèses calculées dans `AttendanceSummaryCalculator` (Java pur) avec taux de présence `(present + late) / total * 100` arrondi à 2 décimales `HALF_UP`.

## Modèle de données (migration `V8__teacher_grade_attendance.sql`)
- 9 nouvelles tables isolées par `tenant_id NOT NULL` avec index, unicités composites incluant `tenant_id`, clés étrangères composites `(tenant_id, <fk_id>)`, RLS activée et forcée (`fail-closed`) et privilèges `ecole_app` :
  - `teachers`, `teacher_specialties`, `teacher_assignments`
  - `evaluations`, `student_grades`, `period_report_cards`, `period_subject_results`
  - `attendance_sessions`, `attendance_records`
