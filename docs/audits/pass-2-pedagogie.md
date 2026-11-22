# Audit Backend — Passe 2 : Pédagogie & Structure (`academic`, `student`, `family`, `teacher`, `grade`, `attendance`, `center`)

**Date d'audit** : 2026-11-21  
**Mode d'exécution** : Analyse statique 100 % en lecture seule (aucune modification du code Java, SQL ou YAML, aucune exécution Maven/`mvnw`, aucun `git commit`).  
**Référentiels appliqués** : `.agents/workflows/audit-pass-2-pedagogie.md`, `.agents/rules/quality-gate.md`, `.agents/rules/security.md`, `.agents/rules/backend.md`, `.agents/rules/grades.md`, `.agents/rules/database.md`, `.agents/rules/finance.md`, `.agents/rules/testing.md`.

---

## 1. En-tête & Synthèse exécutive

### 1.1 Périmètre audité (lecture exhaustive sans échantillonnage)

| Catégorie | Volume vérifié | Détail |
|---|---:|---|
| `academic` (`backend/src/main/java/com/ecole/saas/academic/`) | **82** fichiers | 27 `api`, 8 `application`, 32 `domain`, 7 `infrastructure`, 8 ports/racine |
| `student` (`backend/src/main/java/com/ecole/saas/student/`) | **54** fichiers | 15 `api`, 8 `application`, 18 `domain`, 5 `infrastructure`, 8 ports/racine |
| `family` (`backend/src/main/java/com/ecole/saas/family/`) | **57** fichiers | 16 `api`, 8 `application`, 19 `domain`, 4 `infrastructure`, 10 ports/racine |
| `teacher` (`backend/src/main/java/com/ecole/saas/teacher/`) | **45** fichiers | 17 `api`, 7 `application`, 11 `domain`, 4 `infrastructure`, 6 ports/racine |
| `grade` (`backend/src/main/java/com/ecole/saas/grade/`) | **62** fichiers | 17 `api`, 9 `application`, 21 `domain`, 5 `infrastructure`, 10 ports/racine |
| `attendance` (`backend/src/main/java/com/ecole/saas/attendance/`) | **42** fichiers | 12 `api`, 7 `application`, 12 `domain`, 3 `infrastructure`, 8 ports/racine |
| `center` (`backend/src/main/java/com/ecole/saas/center/`) | **86** fichiers | 34 `api`, 10 `application`, 25 `domain`, 10 `infrastructure`, 7 ports/racine |
| **Total Java principal Passe 2** | **428** fichiers | 100 % des 7 modules pédagogiques lus |
| Migrations Flyway (`backend/src/main/resources/db/migration/`) | **V2, V5, V6, V8, V9, V12** (+ scan `V1`..`V15`) | RLS, `ecole_app`, FK composites, contraintes `CHECK`, index |
| Tests (`backend/src/test/java/com/ecole/saas/`) | **17** fichiers | 6 suites intégration/architecture (`ArchitectureTest`, `MultiTenantIsolationTest`, `SchoolAcademicIntegrationTest`, `StudentFamilyIntegrationTest`, `TeacherGradeAttendanceIntegrationTest`, `CenterIntegrationTest`) + 11 suites unitaires domaine |

### 1.2 Tableau récapitulatif des constats par module

| Module | `[BLOQUANT]` | `[MAJEUR]` | `[MINEUR]` | Total non-conformités | `[CONFORME]` vérifiés |
|---|---:|---:|---:|---:|---:|
| `academic` | 2 | 1 | 4 | **7** | 11 |
| `student` | 2 | 1 | 2 | **5** | 12 |
| `family` | 2 | 2 | 3 | **7** | 8 |
| `teacher` | 3 | 2 | 2 | **7** | 10 |
| `grade` | 2 | 3 | 1 | **6** | 12 |
| `attendance` | 3 | 2 | 1 | **6** | 10 |
| `center` | 2 | 1 | 1 | **4** | 13 |
| **Total Global Passe 2** | **16** | **12** | **14** | **42** | **76** |

### 1.3 Synthèse exécutive des risques majeurs

1. **Ports inter-modules sans `@PreAuthorize` ni garde d'abonnement (transverse aux 7 modules)** : Tous les services d'accès REST des 7 modules portent `@PreAuthorize` et appellent `SubscriptionAccessPort`. En revanche, les **7 services d'implémentation de ports inter-modules** (`AcademicStructureQueryService`, `StudentDirectoryQueryService`, `FamilyDirectoryQueryService`, `TeacherDirectoryQueryService`, `GradeSummaryQueryService`, `AttendanceSummaryQueryService`, `CenterDirectoryQueryService`) ainsi que les méthodes de lecture/pré-contrôle de `GradeImportPortAdapter`, `AttendanceImportPortAdapter`, `FamilyImportPortAdapter` et `TeacherImportPortAdapter` ne portent ni `@PreAuthorize` ni appel à `*SubscriptionGuard.requireReadAccess()`.
2. **Défauts de verrouillage concurrent (`@Lock` / `@Version` / `pg_advisory_xact_lock`)** : Bien que la séquence des matricules, les quotas d'élèves, les capacités d'inscription et les packs du centre (`CenterStudentPack`) soient verrouillés, plusieurs mutations critiques manquent de sérialisation : bascule d'année courante et génération de périodes (`academic`), désignation unique `primaryContact` / `financialResponsible` (`family`), bascule `primarySpecialty` / `homeroomTeacher` (`teacher`), cycle de vie et saisie sur évaluations/bulletins (`grade`), séances d'appel et pointages (`attendance`), et surtout concurrence `upsertAttendance` vs `cancelSession` sur `center_sessions` pouvant entraîner une perte de crédit de pack payant (`center`).
3. **Failles RBAC / IDOR et exposition PII** :
   - `attendance` : un `TEACHER`/`TUTOR` peut lire les feuilles d'appel d'une autre matière dans une classe où il est affecté (`getSession`, `listSessions`).
   - `center` : `CenterSummaryService.getStudentOverview` expose à un `TUTOR` le bulletin scolaire complet et l'assiduité scolaire régulière sans vérifier son périmètre scolaire.
   - `student` et `teacher` : `StudentResponse` expose `medicalNotes`, `address`, `phone`, `email` aux rôles `TEACHER`/`TUTOR`/`CASHIER`, et `TeacherResponse` expose `nationalIdNumber` (CNI/NIN) à `PROMOTER` et `CENSOR`.
4. **Performance & Intégrité métier** : 4 boucles N+1 résiduelles (`TeacherService.listSpecialties`, `GradeSummaryQueryService.findStudentYearSummaries`, `AttendanceSessionService.getStudentSummary`, `CenterPackService.listStudentPacks`), un `findAll()` non borné (`AttendanceSessionService.computeStudentSummaryInternal`) et 3 méthodes de port non paginées (`findAllSubjects`, `findAllActiveGuardians`, `findAllActiveTeachers`), ainsi que l'absence de détection de doublons à la création d'élève (`student`) et de tuteur (`family`) et l'absence de circuit contrôlé de réouverture d'un bulletin `LOCKED` (`grade`).

---

## 2. Rapport détaillé par module

---

### 2.1 Module `academic` (82 fichiers)

#### Performance
- Batching inter-entités conforme dans `ClassRoomService.listClassRooms`, `ClassRoomService.listClassSubjects` et `AcademicStructureQueryService` (méthodes `find*ByIds` et `findActiveSubjectsForClass`), pagination bornée à `MAX_SIZE = 100` via `AcademicPagination`, et persistance par lot via `saveAll` dans `EvaluationPeriodService.generateStandardPeriods`.
- Points d'amélioration : `findAllSubjects()` sans pagination (`P2-004`) et deux index SQL composites secondaires manquants (`P2-005`).

#### Sécurité / Périmètre
- Les 5 services REST (`AcademicYearManagementService`, `ClassRoomService`, `EvaluationPeriodService`, `GradeLevelService`, `SubjectService`) appliquent `@PreAuthorize` sur toutes leurs méthodes publiques, contrôlent `TenantContext.requireCurrent()`, n'exposent aucune `@Entity`, et journalisent sans PII.
- Écart bloquant identifié par le contrôle transverse : `AcademicStructureQueryService` ne porte ni `@PreAuthorize` ni garde d'abonnement (`P2-002`).

#### Concurrence / Validation / Clock / Abonnement
- `Clock` injecté et utilisé proprement ; `AcademicSubscriptionGuard` vérifié en lecture/écriture sur les 5 services REST ; `@Valid` et types boxés utilisés sur les DTO d'entrée.
- Écarts : absence de verrouillage sur la bascule d'année courante et la création/génération de périodes (`P2-001`), réduction de capacité d'une classe sans contrôle des inscriptions actives (`P2-003`), absence de `@NotNull` sur deux champs `PUT` (`P2-006`) et couverture de tests à compléter (`P2-007`).

#### Points `[CONFORME]` vérifiés (`academic`)
1. **Batching inter-entités** : `ClassRoomService.listClassRooms` (`backend/src/main/java/com/ecole/saas/academic/application/ClassRoomService.java:106-117`), `ClassRoomService.listClassSubjects` (`backend/src/main/java/com/ecole/saas/academic/application/ClassRoomService.java:253-256`) et `AcademicStructureQueryService` (`backend/src/main/java/com/ecole/saas/academic/application/AcademicStructureQueryService.java:94-215, 241-259`) utilisent `findAllById` -> `Map<UUID, ...>`.
2. **Pagination & tri bornés** : `AcademicPagination.of` / `ofFixedOrder` (`backend/src/main/java/com/ecole/saas/academic/api/AcademicPagination.java:9-64`, `MAX_SIZE = 100`, liste blanche de champs de tri) appliqué sur les 5 contrôleurs REST.
3. **Persistance en lot & transactions courtes** : `EvaluationPeriodService.generateStandardPeriods` (`backend/src/main/java/com/ecole/saas/academic/application/EvaluationPeriodService.java:143`) utilise `evaluationPeriodRepository.saveAll(generated)`. Aucun appel externe bloquant dans `@Transactional`.
4. **`@PreAuthorize` sur les services REST** : toutes les méthodes publiques de `AcademicYearManagementService` (`:68, 79, 89, 97, 132, 158, 180, 207, 227`), `ClassRoomService` (`:79, 120, 131, 170, 195, 218, 240, 259, 288, 309, 328`), `EvaluationPeriodService` (`:58, 67, 75, 130, 154, 184, 203, 223, 243`), `GradeLevelService` (`:40, 58, 66, 78, 88, 98`) et `SubjectService` (`:41, 69, 77, 96, 111, 121`) portent `@PreAuthorize`.
5. **Contrôleurs & DTO** : zéro `@Entity` exposée sur les 5 contrôleurs (`ArchitectureTest.java:92-100`).
6. **Multi-tenant & RLS SQL** : `TenantContext.requireCurrent()` systématique ; tables `academic_years` (`V2:184-188`) et `V5` (`V5:201-229`) avec `tenant_id`, FK composites, `ENABLE` + `FORCE ROW LEVEL SECURITY` fail-closed et `REVOKE DELETE FROM ecole_app` (`V12:90-95`).
7. **PII & Logs** : aucune PII dans les entités ni dans les logs (`AcademicYearManagementService.java:128, 176, 203, 223, 243`).
8. **Domaine pur & injection constructeur** : zéro import `org.springframework.*` dans `com.ecole.saas.academic.domain` (32 fichiers) ; injection par constructeur uniquement.
9. **`@Valid` & types boxés** : `@Valid` sur chaque `@RequestBody` des 5 contrôleurs ; champs obligatoires des DTO en `Integer`/`Boolean`/enums.
10. **`Clock` injecté** : `clock.instant()` dans `AcademicYearManagementService.java:51, 213, 233` et `EvaluationPeriodService.java:43, 250` ; zéro appel `*.now()`.
11. **Garde d'abonnement sur services REST** : `AcademicSubscriptionGuard` (`backend/src/main/java/com/ecole/saas/academic/application/AcademicSubscriptionGuard.java:15-30`) appelé en lecture et en écriture dans les 5 services REST.

#### Constats (`academic`)

##### `P2-001`
- **Identifiant** : `P2-001`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `academic` — Axe C (Concurrence)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/academic/application/AcademicYearManagementService.java:158-205, 252-259`
  - `backend/src/main/java/com/ecole/saas/academic/application/EvaluationPeriodService.java:75-152`
  - `backend/src/main/java/com/ecole/saas/academic/domain/AcademicYear.java:19-57`
  - `backend/src/main/java/com/ecole/saas/academic/domain/EvaluationPeriod.java:19-63`
- **Constat factuel** :
  1. `AcademicYearManagementService.activateAcademicYear` (`:160-178`) et `setCurrentAcademicYear` (`:182-205`) exécutent `clearCurrentYearIfOther(yearId)` (`:252-259`, lecture `findByCurrentTrue()` + `saveAndFlush`) puis marquent l'année cible comme courante sans aucun verrou (`@Lock(PESSIMISTIC_WRITE)`, `@Version` ou `pg_advisory_xact_lock`).
  2. `EvaluationPeriodService.createPeriod` (`:77-128`) et `generateStandardPeriods` (`:132-152`) contrôlent en mémoire l'unicité, le plafond TRIMESTER/SEMESTER et l'absence de chevauchement sans verrouiller l'année scolaire parente.
  3. Aucune entité académique ne dispose de colonne `version` (`@Version`).
- **Risque** : Collision concurrente sur l'index unique partiel `uq_academic_years_current_per_tenant` (`V2:161-163`) générant une `DataIntegrityViolationException` non maîtrisée, ou création de périodes chevauchantes / dépassant le quota TRIMESTER/SEMESTER.
- **Correction précise** :
  - Sérialiser par verrou consultatif `pg_advisory_xact_lock` (ou `@Lock(LockModeType.PESSIMISTIC_WRITE)`) la bascule d'année courante dans `AcademicYearManagementService` et la création/génération de périodes par `academicYearId` dans `EvaluationPeriodService` :
    ```java
    // Dans AcademicYearRepository / AcademicLockRepository :
    @Query(value = "SELECT pg_advisory_xact_lock(hashtextextended(current_setting('app.current_tenant_id', true) || ':academic-current', 0))", nativeQuery = true)
    void lockCurrentAcademicYearForTenant();
    ```
  - Ajouter en migration `V16__pedagogy_optimistic_locking.sql` `version BIGINT NOT NULL DEFAULT 0` + `@Version` sur `academic_years`, `evaluation_periods` et `class_rooms`.
- **Test à ajouter** : Test d'intégration Testcontainers multi-threads (`CountDownLatch`) sur `setCurrentAcademicYear` (2 années simultanées -> exactement une `current = true`) et sur `generateStandardPeriods` / `createPeriod` (1 succès 201, 1 conflit 409 propre).

##### `P2-002`
- **Identifiant** : `P2-002`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `academic` — Axes B & C (RBAC `@PreAuthorize` & Garde d'abonnement sur port)
- **Localisation** : `backend/src/main/java/com/ecole/saas/academic/application/AcademicStructureQueryService.java:40-260`
- **Constat factuel** : `AcademicStructureQueryService` (service applicatif `@Service` implémentant `AcademicStructurePort`) ne porte aucune annotation `@PreAuthorize` sur sa classe ou ses 14 méthodes publiques (`findCurrentAcademicYear`, `findAcademicYearById`, `findAcademicYearByCode`, `findAcademicYearsByIds`, `findEvaluationPeriodsByYear`, `findEvaluationPeriodById`, `findEvaluationPeriodsByIds`, `findGradeLevelById`, `findGradeLevelsByIds`, `findSubjectById`, `findSubjectsByIds`, `findAllSubjects`, `findClassRoomById`, `findClassRoomsByIds`, `findClassRoomsByAcademicYear`, `findActiveSubjectsForClass`), et n'injecte pas `AcademicSubscriptionGuard` (`assertReadAllowed()`).
- **Risque** : Non-respect du Quality Gate §1 & §2 : un appel au port sous abonnement `SUSPENDED`/`CANCELLED`/`MISSING` ou sans contexte de sécurité authentifié n'est pas bloqué par le module `academic`.
- **Correction précise** :
  ```java
  @Service
  @PreAuthorize("isAuthenticated()")
  public class AcademicStructureQueryService implements AcademicStructurePort {
      private final AcademicSubscriptionGuard subscriptionGuard;
      // ... dans chaque méthode publique :
      TenantContext.requireCurrent();
      subscriptionGuard.requireReadAccess();
  ```
- **Test à ajouter** : Test d'intégration vérifiant que les appels à `AcademicStructurePort` sont refusés lorsque le tenant est `SUSPENDED` ou sans abonnement actif, et rejetés sans principal authentifié.

##### `P2-003`
- **Identifiant** : `P2-003`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `academic` — Axe C (Intégrité / Concurrence)
- **Localisation** : `backend/src/main/java/com/ecole/saas/academic/application/ClassRoomService.java:170-193`
- **Constat factuel** : `ClassRoomService.updateClassRoom` (`:172-193`) met à jour `capacity` (`room.update(..., capacity)`) sans verrouiller la classe ni vérifier si la nouvelle capacité devient inférieure au nombre d'élèves déjà inscrits activement (`StudentEnrollment` en statut `ACTIVE`).
- **Risque** : Une classe contenant 35 élèves actifs peut voir sa capacité ramenée à 20, faussant les contrôles de capacité et les transferts/inscriptions ultérieurs.
- **Correction précise** : Exposer un SPI/port de lecture d'effectif actif (sans créer de cycle Modulith, ex. `ActiveClassEnrollmentCounterPort` implémenté côté `student`) et acquérir un verrou consultatif sur `classRoomId` avant de rejeter en `409 Conflict` toute réduction de `capacity` en dessous de l'effectif `ACTIVE`.
- **Test à ajouter** : Test d'intégration vérifiant le rejet `409` lors d'un `PUT /api/v1/classes/{id}` abaissant `capacity` sous le nombre d'élèves `ACTIVE` inscrits.

##### `P2-004`
- **Identifiant** : `P2-004`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `academic` — Axe A (Performance — chargement non borné)
- **Localisation** : `backend/src/main/java/com/ecole/saas/academic/application/AcademicStructureQueryService.java:185-192`
- **Constat factuel** : `AcademicStructureQueryService.findAllSubjects()` appelle `subjectRepository.findAll()` sans pagination ni tri en base (`ORDER BY name ASC`).
- **Risque** : Chargement non borné si le référentiel des matières devient volumineux, et ordre non déterministe pour `schoolimport`.
- **Correction précise** : Remplacer `subjectRepository.findAll()` par une requête triée et bornée (ex. `findAll(PageRequest.of(0, 500, Sort.by("name").ascending())).getContent()` ou variante paginée sur `AcademicStructurePort`).
- **Test à ajouter** : Test vérifiant le tri déterministe et le respect de la borne sur `findAllSubjects()`.

##### `P2-005`
- **Identifiant** : `P2-005`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `academic` — Axe A (Index SQL)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/academic/infrastructure/AcademicYearRepository.java:21` vs `V2:160`
  - `backend/src/main/java/com/ecole/saas/academic/application/ClassRoomService.java:81-101` vs `V5:162-167`
- **Constat factuel** : `AcademicYearRepository.findByStatus(AcademicYearStatus, Pageable)` filtre sur `(tenant_id, status)` et `ClassRoomService.listClassRooms` filtre fréquemment sur `(tenant_id, academic_year_id, status)` sans index composites dédiés dans `V2` ou `V5`.
- **Risque** : Balayage séquentiel intra-tenant sur ces filtres fréquents.
- **Correction précise** : Ajouter dans une migration `V16+` :
  ```sql
  CREATE INDEX CONCURRENTLY idx_academic_years_tenant_status
      ON academic_years (tenant_id, status);
  CREATE INDEX CONCURRENTLY idx_class_rooms_tenant_year_status
      ON class_rooms (tenant_id, academic_year_id, status);
  ```
- **Test à ajouter** : Test Testcontainers vérifiant la présence des deux index dans `pg_indexes`.

##### `P2-006`
- **Identifiant** : `P2-006`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `academic` — Axe C (Validation DTO)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/academic/api/UpdateClassSubjectRequest.java:15` et `ClassRoomController.java:157`
  - `backend/src/main/java/com/ecole/saas/academic/api/UpdateSubjectRequest.java:16` et `Subject.java:68`
- **Constat factuel** : `UpdateClassSubjectRequest.mandatory` (`Boolean`) n'est pas annoté `@NotNull`, mais `ClassRoomController.updateClassSubject` (`:157`) le convertit via `Boolean.TRUE.equals(request.mandatory())` en `boolean` primitif. De même, `UpdateSubjectRequest.category` n'est pas annoté `@NotNull`, et `Subject.update` (`:68`) applique `SubjectCategory.GENERAL` par défaut si `null`.
- **Risque** : Écrasement silencieux de `mandatory` à `false` et de `category` à `GENERAL` lors d'un `PUT` omettant ces champs.
- **Correction précise** : Annoter `@NotNull` `UpdateClassSubjectRequest.mandatory` et `UpdateSubjectRequest.category` (et accepter `Boolean` côté service si une valeur optionnelle doit être conservée).
- **Test à ajouter** : Test `@WebMvcTest` vérifiant le retour `400 Bad Request` si `mandatory` ou `category` est omis sur `PUT`.

##### `P2-007`
- **Identifiant** : `P2-007`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `academic` — Axe C (Couverture de tests)
- **Localisation** : `backend/src/test/java/com/ecole/saas/SchoolAcademicIntegrationTest.java:246-482`
- **Constat factuel** : Aucun test ne couvre la concurrence sur `AcademicYear` (`activateAcademicYear` / `setCurrentAcademicYear`), la création concurrente de `EvaluationPeriod`, ni le rejet d'omission sur `PUT /subjects` et `PUT /classes/{id}/subjects/{id}`.
- **Risque** : Régression non détectée sur ces invariants critiques.
- **Correction précise** : Ajouter les scénarios concurrents et de validation DTO dans `SchoolAcademicIntegrationTest`.
- **Test à ajouter** : Cf. tests décrits en `P2-001`, `P2-003` et `P2-006`.

---

### 2.2 Module `student` (54 fichiers)

#### Performance
- Batching inter-ports conforme dans `StudentEnrollmentService.enrichPage`, comptage agrégé `GROUP BY` dans `StudentDirectoryQueryService.countActiveEnrollmentsByClassRooms`, pagination bornée `MAX_SIZE = 100` via `StudentPagination`, indexation `V6` solide.
- Point d'amélioration mineur : matérialisation d'UUID dans `StudentTeacherScopeGuard` et index fonctionnel `LOWER(last_name)` (`P2-011`).

#### Sécurité / Périmètre
- `StudentService` et `StudentEnrollmentService` portent `@PreAuthorize` sur toutes leurs méthodes publiques ; `StudentTeacherScopeGuard` restreint finement `TEACHER` et `TUTOR` aux élèves activement inscrits dans leurs classes ; `PARENT` et `STUDENT` sont bloqués (403) sur le back-office ; aucune PII dans les logs.
- Écarts bloquants : `StudentResponse` expose sans masquage les données médicales et coordonnées personnelles aux rôles `TEACHER`/`TUTOR`/`CASHIER` (`P2-008`), et `StudentDirectoryQueryService` ne porte ni `@PreAuthorize` ni `StudentSubscriptionGuard` (`P2-009`).

#### Concurrence / Validation / Clock / Abonnement
- Génération séquentielle atomique de matricules via `INSERT ... ON CONFLICT DO UPDATE ... RETURNING` (`MatriculeSequenceRepository`), verrous `pg_advisory_xact_lock` sur le quota élèves et la capacité de classe (`StudentLockRepository`), `@Valid` + types boxés, `Clock` + zone `Africa/Dakar`, et `StudentSubscriptionGuard` sur les 2 services REST.
- Écarts : absence de détection de doublon avant création d'un élève et génération de matricule (`P2-010`), couverture concurrentielle sur `createStudent` et `@Version` manquants (`P2-012`).

#### Points `[CONFORME]` vérifiés (`student`)
1. **Batching inter-ports** : `StudentEnrollmentService.enrichPage` (`backend/src/main/java/com/ecole/saas/student/application/StudentEnrollmentService.java:333-361`) précharge `Student`, `AcademicYearSummary` et `ClassRoomSummary` en lot ; `StudentDirectoryQueryService.countActiveEnrollmentsByClassRooms` (`:242-261`) agrège via `GROUP BY e.classRoomId`.
2. **Pagination & tri bornés** : `StudentPagination.of` (`backend/src/main/java/com/ecole/saas/student/api/StudentPagination.java:9-54`, `MAX_SIZE = 100`, tri sur liste blanche).
3. **Index principaux & `saveAll` / transactions** : index `V6:53-58, 95-102` ; aucun `save()` en boucle ni appel réseau dans `@Transactional`.
4. **`@PreAuthorize` sur services REST** : toutes les méthodes publiques de `StudentService` (`:78, 116, 126, 138, 195, 231, 250, 268, 287, 306`) et `StudentEnrollmentService` (`:87, 132, 146, 160, 219, 253, 270, 287, 304`) sont annotées `@PreAuthorize`.
5. **Périmètre `TEACHER`/`TUTOR` & blocage `PARENT`/`STUDENT`** : `StudentTeacherScopeGuard` (`backend/src/main/java/com/ecole/saas/student/application/StudentTeacherScopeGuard.java:20-97`) vérifié par `StudentFamilyIntegrationTest.java:621-629`.
6. **Contrôleurs, RLS & moindre privilège** : zéro `@Entity` exposée ; RLS forcée sur `student_matricule_sequences`, `students`, `student_enrollments` (`V6:225-241`) et `REVOKE DELETE FROM ecole_app` (`V12:96-97`).
7. **PII dans les logs** : aucune PII journalisée (aucun `Logger` applicatif, métadonnées d'audit limitées aux UUID, matricule et statut).
8. **Domaine pur & injection** : zéro import `org.springframework.*` dans `com.ecole.saas.student.domain` (18 fichiers), injection par constructeur uniquement.
9. **Verrous critiques de base** : `MatriculeSequenceRepository.java:21-37` (`ON CONFLICT DO UPDATE`), `StudentLockRepository.java:21-29` (`pg_advisory_xact_lock` quota et capacité), index unique `uq_student_enrollments_active_per_year` (`V6:91-93`).
10. **`@Valid` & types boxés** : `@Valid` présent sur chaque `@RequestBody` (`StudentController.java:74, 95, 117, 126, 136, 145`, `StudentEnrollmentController.java:69, 87, 98, 111, 122, 133`), tous les champs obligatoires sont boxés (`Boolean repeating`).
11. **`Clock` + fuseau établissement** : `Clock` injecté avec zone `Africa/Dakar` dans `StudentService.java:57, 293, 342-362` et `StudentEnrollmentService.java:64, 419-432`.
12. **Garde d'abonnement & quotas sur services REST** : `StudentSubscriptionGuard` (`backend/src/main/java/com/ecole/saas/student/application/StudentSubscriptionGuard.java:13-39`) contrôle lecture, écriture et quota `STUDENTS` sur `StudentService` et `StudentEnrollmentService`.

#### Constats (`student`)

##### `P2-008`
- **Identifiant** : `P2-008`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `student` — Axe B (Masquage des PII sensibles)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/student/api/StudentResponse.java:10-53` (`:17-23, 39-45`)
  - `backend/src/main/java/com/ecole/saas/student/api/StudentController.java:40-71`
  - `backend/src/main/java/com/ecole/saas/student/application/StudentService.java:45-46, 80-136`
- **Constat factuel** : `StudentService.READ_ROLES` autorise `TEACHER`, `TUTOR` et `CASHIER` à appeler `listStudents`, `getStudent` et `getStudentByMatricule`. `StudentResponse.from(Student)` renvoie en clair `medicalNotes` (`student.getMedicalNotes()`), `address`, `phone`, `email`, `birthPlace` et `nationality` sans aucun masquage par rôle.
- **Risque** : Divulgation de données médicales d'élèves mineurs (`medicalNotes`) et de coordonnées personnelles (`address`, `phone`, `email`) à des rôles non habilités (`TEACHER`, `TUTOR`, `CASHIER`).
- **Correction précise** :
  ```java
  // Dans StudentService :
  public record StudentView(Student student, boolean includeSensitivePii) {}
  // Dans StudentTeacherScopeGuard : autoriser uniquement DIRECTOR, CENTER_MANAGER, SECRETARY (hors TEACHER/TUTOR/CASHIER)
  // Dans StudentResponse.from(StudentView view) :
  view.includeSensitivePii() ? s.getMedicalNotes() : null,
  view.includeSensitivePii() ? s.getAddress() : null,
  view.includeSensitivePii() ? s.getPhone() : null,
  view.includeSensitivePii() ? s.getEmail() : null
  ```
- **Test à ajouter** : Dans `StudentFamilyIntegrationTest`, vérifier qu'un `TEACHER` / `TUTOR` affecté à la classe de l'élève reçoit `200 OK` avec `medicalNotes == null`, `address == null`, `phone == null`, `email == null`, alors que `SECRETARY` et `DIRECTOR` les reçoivent en clair.

##### `P2-009`
- **Identifiant** : `P2-009`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `student` — Axes B & C (RBAC `@PreAuthorize` & Garde d'abonnement sur port)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/student/application/StudentDirectoryQueryService.java:34-291`
  - `backend/src/main/java/com/ecole/saas/student/application/StudentImportPortAdapter.java:21-89`
- **Constat factuel** : `StudentDirectoryQueryService` (service applicatif `@Service` implémentant `StudentDirectoryPort`) n'a aucune annotation `@PreAuthorize` sur ses 17 méthodes publiques et n'appelle jamais `StudentSubscriptionGuard.requireReadAccess()`. Par ailleurs, `StudentImportPortAdapter` (`:21-89`) ne porte pas de `@PreAuthorize` explicite sur sa classe/méthodes (bien qu'il délègue directement à `StudentService` et `StudentEnrollmentService`). *(Note : `StudentQuotaUsageProvider` est un SPI interne consommé par `subscription` et ne doit pas appeler `SubscriptionAccessPort` pour éviter toute récursion.)*
- **Risque** : Un appel au port `StudentDirectoryPort` sous abonnement `SUSPENDED` / `CANCELLED` / `MISSING` ou sans principal authentifié n'est pas bloqué par `student`.
- **Correction précise** :
  - Injecter `StudentSubscriptionGuard` dans `StudentDirectoryQueryService`, appeler `subscriptionGuard.requireReadAccess()` dans chacune de ses 17 méthodes publiques et annoter la classe `@PreAuthorize("isAuthenticated()")`.
  - Annoter explicitement `StudentImportPortAdapter` avec `@PreAuthorize(StudentService.WRITE_ROLES)`.
- **Test à ajouter** : Test d'intégration vérifiant que `StudentDirectoryPort` lève `StudentSubscriptionReadDeniedException` lorsque le tenant est `SUSPENDED` ou sans abonnement.

##### `P2-010`
- **Identifiant** : `P2-010`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `student` — Axe C (Intégrité métier / Doublons à l'inscription)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/student/application/StudentService.java:138-193`
  - `backend/src/main/java/com/ecole/saas/student/infrastructure/StudentRepository.java:14-26`
- **Constat factuel** : `StudentService.createStudent` acquiert le verrou de quota, puis appelle immédiatement `matriculeSequenceRepository.nextSequenceValue(sequenceYear)` (`:167`) et enregistre un nouvel élève sans aucune vérification d'existence d'un doublon sur `(firstName, lastName, birthDate)` dans le tenant.
- **Risque** : Création d'élèves en doublon lors de réinscriptions ou saisies simultanées, génération de multiples matricules pour un même élève et consommation indue de la séquence et du quota `STUDENTS` (violation de `.agents/rules/grades.md:10`).
- **Correction précise** : Ajouter `existsByNormalizedIdentity(String firstName, String lastName, LocalDate birthDate)` dans `StudentRepository`, l'appeler sous `studentLockRepository.lockStudentQuotaForTenant()` **avant** `nextSequenceValue(sequenceYear)`, et lever une `StudentAlreadyExistsException` (`409 Conflict`). Ajouter en `V16+` l'index fonctionnel `(tenant_id, LOWER(last_name), LOWER(first_name), birth_date)`.
- **Test à ajouter** : Test d'intégration vérifiant le refus `409` à la création d'un second élève de mêmes prénom, nom et date de naissance sans incrémenter la séquence de matricule.

##### `P2-011`
- **Identifiant** : `P2-011`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `student` — Axe A (Performance & Index SQL)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/student/application/StudentTeacherScopeGuard.java:64-72`
  - `backend/src/main/java/com/ecole/saas/student/application/StudentService.java:84-97`
  - `backend/src/main/java/com/ecole/saas/student/infrastructure/StudentRepository.java:20-21`
- **Constat factuel** : `StudentTeacherScopeGuard.resolveVisibleStudentIds()` charge en mémoire toutes les entités `StudentEnrollment` des classes affectées pour construire `Set<UUID>` injecté dans un `IN (...)` de `Specification<Student>`, et `StudentRepository.findByLastNameLowerIn` utilise `LOWER(s.lastName) IN :lowerLastNames` sans index fonctionnel sur `LOWER(last_name)`.
- **Risque** : Coût mémoire et clause `IN (...)` volumineuse pour les enseignants multi-classes sur grands établissements.
- **Correction précise** : Utiliser une projection `SELECT DISTINCT e.studentId` (ou sous-requête Criteria `EXISTS`) et créer en `V16+` `CREATE INDEX CONCURRENTLY idx_students_tenant_lower_last_name ON students (tenant_id, LOWER(last_name));`.
- **Test à ajouter** : Test de non-régression de `GET /api/v1/students` pour un `TEACHER` multi-classes.

##### `P2-012`
- **Identifiant** : `P2-012`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `student` — Axe C (Couverture de tests & Verrouillage optimiste)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/student/domain/Student.java:19-83`
  - `backend/src/main/java/com/ecole/saas/student/domain/StudentEnrollment.java:16-58`
  - `backend/src/test/java/com/ecole/saas/StudentFamilyIntegrationTest.java:974-1030`
- **Constat factuel** : La concurrence est testée sur `enrollStudent` (`:975-1007`), mais pas sur `StudentService.createStudent` (séquence des matricules et respect du quota `maxStudents`) ; `Student` et `StudentEnrollment` n'ont pas de colonne `version` (`@Version`).
- **Risque** : Absence de preuve automatisée sur la création concurrente d'élèves et risque d'écrasement de mise à jour concurrente de profil.
- **Correction précise** : Ajouter un test concurrentiel Testcontainers sur `POST /api/v1/students` (matricules distincts, quota respecté) et ajouter `@Version` sur `Student` / `StudentEnrollment` en `V16+`.
- **Test à ajouter** : Test concurrentiel `createStudent` dans `StudentFamilyIntegrationTest`.

---

### 2.3 Module `family` (57 fichiers)

#### Performance
- Batching inter-entités conforme dans `GuardianService`, `StudentGuardianService` et `FamilyDirectoryQueryService.mapStudentGuardianLinks`, pagination bornée `MAX_SIZE = 100` via `FamilyPagination`.
- Écarts : `findAllActiveGuardians()` non borné + index manquant sur `guardians(tenant_id, status, last_name, first_name)` (`P2-015`), et pagination/tri en mémoire dans `FamilyService.listFamilyStudents` (`P2-017`).

#### Sécurité / Périmètre
- Les 3 services REST (`FamilyService`, `GuardianService`, `StudentGuardianService`) portent `@PreAuthorize` sur toutes leurs méthodes publiques ; `FamilyTeacherScopeGuard` restreint finement `TEACHER`/`TUTOR` et masque `nationalIdNumber` dans `GuardianResponse` hors `DIRECTOR`/`CENTER_MANAGER`/`SECRETARY` ; RLS forcée et `REVOKE DELETE` appliqués.
- Écart bloquant : `FamilyDirectoryQueryService` ne porte ni `@PreAuthorize` ni `FamilySubscriptionGuard`, et `FamilyImportPortAdapter.linkOrUpdateGuardianForImport` lit directement `linkRepository` sans `@PreAuthorize` ni garde préalable (`P2-014`).

#### Concurrence / Validation / Clock / Abonnement
- `Clock` injecté ; `FamilySubscriptionGuard` vérifié sur les 3 services REST ; `@Valid` présent sur chaque `@RequestBody`.
- Écarts : absence de verrou sur la bascule atomique `primaryContact` / `financialResponsible` (`P2-013`), absence de détection de doublon à la création de tuteur (`P2-016`), 5 champs `Boolean` sans `@NotNull` dans `UpdateStudentGuardianRequest` (`P2-018`) et couverture de tests concurrents manquante (`P2-019`).

#### Points `[CONFORME]` vérifiés (`family`)
1. **Batching inter-entités** : `GuardianService.listGuardians` (`backend/src/main/java/com/ecole/saas/family/application/GuardianService.java:119-128`), `StudentGuardianService.listStudentGuardians` (`backend/src/main/java/com/ecole/saas/family/application/StudentGuardianService.java:77-83`), `GuardianService.listGuardianStudents` (`:291-298`) et `FamilyDirectoryQueryService.mapStudentGuardianLinks` (`backend/src/main/java/com/ecole/saas/family/application/FamilyDirectoryQueryService.java:188-198`) préchargent en batch.
2. **Pagination & tri bornés** : `FamilyPagination.of` / `ofFixedOrder` (`MAX_SIZE = 100`) sur `FamilyController`, `GuardianController` et `StudentGuardianController`.
3. **`@PreAuthorize` sur services REST** : toutes les méthodes publiques de `FamilyService`, `GuardianService` et `StudentGuardianService` portent `@PreAuthorize(FamilyService.READ_ROLES / WRITE_ROLES)`.
4. **Périmètre `TEACHER`/`TUTOR` & masquage PII CNI/NIN** : `FamilyTeacherScopeGuard` (`:24-167`, testé dans `StudentFamilyIntegrationTest.java:795-972`) et masquage à `null` de `nationalIdNumber` dans `GuardianResponse.java:45` pour tout rôle hors `DIRECTOR`, `CENTER_MANAGER`, `SECRETARY`.
5. **Contrôleurs, RLS & moindre privilège** : zéro `@Entity` exposée ; RLS forcée sur `families`, `guardians`, `student_guardians` (`V6:243-259`) et `REVOKE DELETE FROM ecole_app` (`V12:98-100`).
6. **Logs, domaine pur & injection** : zéro PII dans les logs ; zéro import `org.springframework.*` dans `com.ecole.saas.family.domain` (19 fichiers) ; injection par constructeur uniquement.
7. **`Clock` injecté** : `clock.instant()` dans `FamilyService.java:54, 181` et `GuardianService.java:52, 242` ; zéro `*.now()`.
8. **Garde d'abonnement sur services REST** : `FamilySubscriptionGuard` (`backend/src/main/java/com/ecole/saas/family/application/FamilySubscriptionGuard.java:12-34`) appelé en lecture et en écriture sur les 3 services REST.

#### Constats (`family`)

##### `P2-013`
- **Identifiant** : `P2-013`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `family` — Axe C (Concurrence)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/family/application/StudentGuardianService.java:121-126, 171-176, 248-264`
  - `backend/src/main/java/com/ecole/saas/family/domain/StudentGuardianLink.java:15-53`
- **Constat factuel** : Dans `StudentGuardianService.linkGuardianToStudent` (`:121-126`) et `updateLink` (`:171-176`), la bascule de l'unique `primaryContact` et de l'unique `financialResponsible` par élève appelle `clearExistingPrimaryContact` / `clearExistingFinancialResponsible` (`:248-264`, lecture + `saveAndFlush`) sans `@Lock(PESSIMISTIC_WRITE)`, `@Version` ni verrou consultatif `pg_advisory_xact_lock` sur `(tenant_id, student_id)`.
- **Risque** : Deux requêtes concurrentes promouvant deux tuteurs distincts comme `primaryContact=true` ou `financialResponsible=true` pour le même élève entrent en collision sur les index uniques partiels `uq_student_guardians_primary_per_student` et `uq_student_guardians_financial_per_student` (`V6:209-215`), déclenchant une `DataIntegrityViolationException` (`409`) imprévue.
- **Correction précise** : Acquérir un verrou consultatif `pg_advisory_xact_lock(hashtextextended(current_setting('app.current_tenant_id', true) || ':student-guardian:' || :studentId, 0))` au début de `linkGuardianToStudent` et `updateLink`, et ajouter `@Version` sur `StudentGuardianLink` en `V16+`.
- **Test à ajouter** : Test d'intégration Testcontainers à 2 threads simultanés liant/promouvant 2 tuteurs différents en `primaryContact=true` et `financialResponsible=true` pour le même élève -> aucune `500`/collision et exactement 1 seul lien actif par drapeau.

##### `P2-014`
- **Identifiant** : `P2-014`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `family` — Axes B & C (RBAC `@PreAuthorize` & Garde d'abonnement sur port/adaptateur)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/family/application/FamilyDirectoryQueryService.java:33-254`
  - `backend/src/main/java/com/ecole/saas/family/application/FamilyImportPortAdapter.java:26-143` (`:107-113`)
- **Constat factuel** :
  1. `FamilyDirectoryQueryService` (implémentant `FamilyDirectoryPort`) ne porte aucune annotation `@PreAuthorize` sur ses 16 méthodes publiques et n'appelle jamais `FamilySubscriptionGuard.requireReadAccess()`.
  2. `FamilyImportPortAdapter` ne porte aucune annotation `@PreAuthorize`, et `linkOrUpdateGuardianForImport` (`:107-113`) interroge directement `linkRepository.findByStudentIdAndActiveTrueOrderByPrimaryContactDescCreatedAtAsc(command.studentId())` avant de déléguer à `StudentGuardianService` (qui, lui, porte `@PreAuthorize` et `subscriptionGuard`).
- **Risque** : Contournement de la garde d'abonnement et absence de `@PreAuthorize` sur un port public exposant les coordonnées familiales et tuteurs.
- **Correction précise** : Injecter `FamilySubscriptionGuard` dans `FamilyDirectoryQueryService` (`requireReadAccess()` sur chaque méthode publique) et annoter `@PreAuthorize("isAuthenticated()")` ; ajouter `@PreAuthorize(FamilyService.WRITE_ROLES)` et `subscriptionGuard.requireWriteAccess()` (ou déplacer la recherche de lien existant dans `StudentGuardianService`) dans `FamilyImportPortAdapter`.
- **Test à ajouter** : Test d'intégration vérifiant que `FamilyDirectoryPort` lève `FamilySubscriptionReadDeniedException` quand le tenant est `SUSPENDED` ou sans abonnement, et que `FamilyImportPort` rejette tout rôle non habilité.

##### `P2-015`
- **Identifiant** : `P2-015`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `family` — Axe A (Performance & Index SQL)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/family/application/FamilyDirectoryQueryService.java:156-162`
  - `backend/src/main/java/com/ecole/saas/family/infrastructure/GuardianRepository.java:26`
  - `backend/src/main/resources/db/migration/V6__students_and_families.sql:165-172`
- **Constat factuel** : `FamilyDirectoryQueryService.findAllActiveGuardians()` charge tous les tuteurs `ACTIVE` du tenant sans pagination via `guardianRepository.findByStatusOrderByLastNameAscFirstNameAsc(GuardianStatus.ACTIVE)` ; de plus, `guardians` (`V6:165-172`) n'a pas d'index sur `(tenant_id, status, last_name, first_name)`.
- **Risque** : Consommation mémoire excessive et tri séquentiel en base sur de grands établissements lors des campagnes `communication`.
- **Correction précise** : Ajouter une méthode paginée (`Pageable` / lots de 500) sur `FamilyDirectoryPort` pour `AudienceResolver` et créer en `V16+` :
  ```sql
  CREATE INDEX CONCURRENTLY idx_guardians_tenant_status_name
      ON guardians (tenant_id, status, last_name, first_name);
  ```
- **Test à ajouter** : Test d'intégration paginé sur `FamilyDirectoryQueryService` et vérification de la présence de l'index dans `pg_indexes`.

##### `P2-016`
- **Identifiant** : `P2-016`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `family` — Axe C (Intégrité métier / Doublons)
- **Localisation** : `backend/src/main/java/com/ecole/saas/family/application/GuardianService.java:144-189`
- **Constat factuel** : `GuardianService.createGuardian` ne réalise aucun contrôle d'unicité ou de détection de doublon (ex. sur `(LOWER(TRIM(firstName)), LOWER(TRIM(lastName)), phone)` ou sur `nationalIdNumber` non nul) avant insertion (`V6:169` crée un index non unique `idx_guardians_tenant_phone`).
- **Risque** : Création répétée du même tuteur lors d'inscriptions de fratries ou d'imports, fragmentant les liens familiaux et la facturation.
- **Correction précise** : Vérifier l'existence d'un doublon sous verrou consultatif par `(tenantId, phone)` dans `GuardianService.createGuardian` et lever une `BusinessException` (`409 Conflict`).
- **Test à ajouter** : Test d'intégration vérifiant le rejet `409` lors de la création d'un tuteur en doublon dans le même tenant.

##### `P2-017`
- **Identifiant** : `P2-017`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `family` — Axe A (Performance)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/family/application/FamilyService.java:217-254`
  - `backend/src/main/java/com/ecole/saas/family/application/FamilyTeacherScopeGuard.java:89-108`
- **Constat factuel** : `FamilyService.listFamilyStudents` (`:242-253`) charge tous les `StudentSummary` de la famille, trie en Java et pagine via `subList(start, end)` ; `FamilyTeacherScopeGuard` matérialise les UUID en mémoire pour injecter un `IN (...)` dans la `Specification`.
- **Risque** : Pagination/tri hors base et clause `IN (...)` étendue pour des enseignants multi-classes.
- **Correction précise** : Pousser les filtres via sous-requêtes `EXISTS` / projections `SELECT DISTINCT` et borner `studentIds` avant appel `findStudentsByIds`.
- **Test à ajouter** : Test de non-régression sur `GET /api/v1/families/{id}/students` avec plusieurs pages.

##### `P2-018`
- **Identifiant** : `P2-018`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `family` — Axe C (Validation DTO)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/family/api/UpdateStudentGuardianRequest.java:7-19`
  - `backend/src/main/java/com/ecole/saas/family/api/StudentGuardianController.java:73-91`
  - `backend/src/main/java/com/ecole/saas/family/application/StudentGuardianService.java:154-163`
- **Constat factuel** : Les 5 champs `Boolean` de `UpdateStudentGuardianRequest` (`primaryContact`, `financialResponsible`, `legalCustody`, `emergencyContact`, `authorizedPickup`) ne portent pas `@NotNull`, mais `StudentGuardianController.updateLink` (`:82-86`) les passe via `Boolean.TRUE.equals(...)` aux paramètres primitifs `boolean` de `StudentGuardianService.updateLink` (`:158-162`).
- **Risque** : Révocation silencieuse à `false` d'autorisations parentales (`authorizedPickup`, `legalCustody`, etc.) si le client omet un champ lors d'un `PUT`.
- **Correction précise** : Annoter `@NotNull` les 5 `Boolean` sur `UpdateStudentGuardianRequest` (ou préserver les valeurs existantes quand `null` côté service).
- **Test à ajouter** : Test `@WebMvcTest` vérifiant le rejet `400` si un des 5 drapeaux est absent lors du `PUT`.

##### `P2-019`
- **Identifiant** : `P2-019`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `family` — Axe C (Couverture de tests)
- **Localisation** : `backend/src/test/java/com/ecole/saas/StudentFamilyIntegrationTest.java`
- **Constat factuel** : Aucun test concurrentiel ne couvre la bascule `primaryContact` / `financialResponsible` sur `StudentGuardianService`, ni la détection de doublon sur `GuardianService.createGuardian`.
- **Risque** : Absence de filet de non-régression automatisé sur ces cas.
- **Correction précise** : Ajouter les tests décrits en `P2-013` et `P2-016`.
- **Test à ajouter** : Cf. `P2-013` et `P2-016`.

---

### 2.4 Module `teacher` (45 fichiers)

#### Performance
- Batching inter-ports conforme dans `TeacherAssignmentService.enrichPage` et `enrichAssignments`, pagination bornée `MAX_SIZE = 100` via `TeacherPagination`, indexation `V8` complète sur `teachers`, `teacher_specialties`, `teacher_assignments`.
- Écarts majeurs : N+1 dans `TeacherService.listSpecialties` (`P2-023`) et `findAllActiveTeachers()` non borné dans `TeacherDirectoryQueryService` (`P2-024`).

#### Sécurité / Périmètre
- `TeacherService` et `TeacherAssignmentService` appliquent `@PreAuthorize` sur toutes leurs méthodes publiques ; les endpoints `/me` dérivent exclusivement `userId` du `JwtAuthenticationToken` ; `TEACHER`/`TUTOR` sont bloqués sur le back-office ; RLS forcée et `GRANT` sans `DELETE` sur `teachers` et `teacher_assignments` ; zéro PII dans les logs.
- Écarts bloquants : `TeacherResponse` expose sans masquage `nationalIdNumber` à `PROMOTER` et `CENSOR` (`P2-020`), et `TeacherDirectoryQueryService` (plus `TeacherImportPortAdapter`) ne porte ni `@PreAuthorize` ni `TeacherSubscriptionGuard` (`P2-022`).

#### Concurrence / Validation / Clock / Abonnement
- `Clock` injecté ; `TeacherSubscriptionGuard` vérifié sur les 2 services REST ; `@Valid` présent sur chaque `@RequestBody` ; `hourlyRateXof` strictement en `Long`/`BIGINT` (`@Min(0)`).
- Écarts : absence de verrou sur la bascule `primarySpecialty` et `homeroomTeacher` (`P2-021`), `homeroomTeacher` sans `@NotNull` en `PUT` (`P2-025`), et détail de contrôleur/couverture de tests (`P2-026`).

#### Points `[CONFORME]` vérifiés (`teacher`)
1. **Batching sur affectations** : `TeacherAssignmentService.enrichPage` et `enrichAssignments` (`backend/src/main/java/com/ecole/saas/teacher/application/TeacherAssignmentService.java:313-366`) préchargent `Teacher` (`findAllById`), `AcademicYearSummary`, `ClassRoomSummary` et `SubjectSummary` via `find*ByIds`.
2. **Pagination & tri bornés** : `TeacherPagination.of` (`backend/src/main/java/com/ecole/saas/teacher/api/TeacherPagination.java:9-53`, `MAX_SIZE = 100`, liste blanche).
3. **Index SQL** : index `V8:50-56, 75-81, 116-127` alignés avec les requêtes des 3 repositories.
4. **`@PreAuthorize` sur services REST** : toutes les méthodes publiques de `TeacherService` (`:72, 98, 106, 118, 176, 212, 243, 263, 282, 302, 313, 351`) et `TeacherAssignmentService` (`:75, 109, 127, 137, 228, 258`) portent `@PreAuthorize`.
5. **Périmètre `/me` & isolation back-office** : `TeacherController.getOwnProfile` (`:62-68`) et `getOwnAssignments` (`:70-83`) dérivent `userId` du JWT via `TeacherApiSupport.extractActorId` ; accès back-office refusé aux `TEACHER`/`TUTOR` (`TeacherGradeAttendanceIntegrationTest.java:420-427`).
6. **RLS & moindre privilège DB** : RLS forcée sur `teachers`, `teacher_specialties`, `teacher_assignments` (`V8:420-436`) ; `ecole_app` limité à `SELECT, INSERT, UPDATE` (sans `DELETE`) sur `teachers` et `teacher_assignments` (`V8:478-486`).
7. **Logs, domaine pur, injection & zéro `@Entity` exposée** : zéro PII journalisée, zéro `org.springframework.*` dans `com.ecole.saas.teacher.domain` (11 fichiers), injection par constructeur uniquement.
8. **Monnaie & `@Valid`** : `hourlyRateXof` en `Long`/`BIGINT` (zéro `float`/`double`), `@Valid` sur chaque `@RequestBody` (`TeacherController`, `TeacherAssignmentController`).
9. **`Clock` injecté** : `clock.instant()` dans `TeacherService.java:53, 289` ; zéro `*.now()`.
10. **Garde d'abonnement sur services REST** : `TeacherSubscriptionGuard` (`backend/src/main/java/com/ecole/saas/teacher/application/TeacherSubscriptionGuard.java:12-34`) appelé en lecture et en écriture sur `TeacherService` et `TeacherAssignmentService`.

#### Constats (`teacher`)

##### `P2-020`
- **Identifiant** : `P2-020`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `teacher` — Axe B (Masquage des PII sensibles)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/teacher/api/TeacherResponse.java:23, 46`
  - `backend/src/main/java/com/ecole/saas/teacher/api/TeacherController.java:44-90`
  - `backend/src/main/java/com/ecole/saas/teacher/application/TeacherRoles.java:5-6`
- **Constat factuel** : `TeacherService.listTeachers` et `getTeacher` autorisent `TeacherRoles.BACKOFFICE_READ_ROLES` (`PROMOTER`, `DIRECTOR`, `CENTER_MANAGER`, `CENSOR`, `SECRETARY`), et `TeacherResponse.from(Teacher)` expose en clair `nationalIdNumber` (CNI/NIN) à l'ensemble de ces rôles (notamment `PROMOTER` et `CENSOR`), contrairement à `family` qui restreint la CNI à `DIRECTOR`, `CENTER_MANAGER` et `SECRETARY`. (À noter également que `hourlyRateXof` et `address` sont exposés à `CENSOR`.)
- **Risque** : Divulgation de numéros de CNI/NIN et données personnelles/contractuelles d'enseignants à des rôles pédagogiques ou non habilités (`CENSOR`, `PROMOTER`).
- **Correction précise** : Introduire `TeacherView(Teacher teacher, boolean includeSensitivePii)` dans `TeacherService` pour masquer `nationalIdNumber` (et `address`/`hourlyRateXof` si non habilité, hors `DIRECTOR`, `CENTER_MANAGER`, `SECRETARY` et hors vue `/me` du titulaire), et adapter `TeacherResponse.from(TeacherView)`.
- **Test à ajouter** : Test d'intégration dans `TeacherGradeAttendanceIntegrationTest` vérifiant que `GET /api/v1/teachers/{id}` renvoie `nationalIdNumber == null` pour `CENSOR` et `PROMOTER`, mais en clair pour `DIRECTOR`/`SECRETARY` et sur `/me`.

##### `P2-021`
- **Identifiant** : `P2-021`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `teacher` — Axe C (Concurrence)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/teacher/application/TeacherService.java:339-348`
  - `backend/src/main/java/com/ecole/saas/teacher/application/TeacherAssignmentService.java:191-193, 248-250, 287-294`
- **Constat factuel** :
  1. `TeacherService.addSpecialty` (`:339-344`) lit `findByTeacherIdAndPrimarySpecialtyTrue(teacherId)`, rétrograde l'ancienne spécialité principale et appelle `saveAndFlush` avant d'insérer la nouvelle, sans verrou (`@Lock`, `@Version` ni `pg_advisory_xact_lock`).
  2. `TeacherAssignmentService.createAssignment` (`:191-193`) et `updateAssignment` (`:248-250`) appellent `clearExistingHomeroom(classRoomId, ...)` (`:287-294`) avec `saveAndFlush` sans aucun verrou.
- **Risque** : En exécution concurrente, deux requêtes simultanées entrent en collision sur les index uniques partiels `uq_teacher_specialties_primary_per_teacher` (`V8:75-77`) ou `uq_teacher_assignments_active_homeroom_per_class` (`V8:120-122`), provoquant une `DataIntegrityViolationException` (`409` imprévue).
- **Correction précise** : Acquérir `pg_advisory_xact_lock` par `(tenantId, teacherId)` dans `TeacherService.addSpecialty` et par `(tenantId, classRoomId)` dans `TeacherAssignmentService` avant la rétrogradation + `@Version` en `V16+`.
- **Test à ajouter** : Test concurrentiel Testcontainers désignant simultanément deux professeurs principaux sur la même classe -> succès propre et exactement un `homeroomTeacher = true`.

##### `P2-022`
- **Identifiant** : `P2-022`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `teacher` — Axes B & C (RBAC `@PreAuthorize` & Garde d'abonnement sur port/adaptateur)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/teacher/application/TeacherDirectoryQueryService.java:30-221`
  - `backend/src/main/java/com/ecole/saas/teacher/application/TeacherImportPortAdapter.java:19-75` (`:70-73`)
- **Constat factuel** :
  1. `TeacherDirectoryQueryService` (implémentant `TeacherDirectoryPort`) ne porte aucune annotation `@PreAuthorize` sur ses 12 méthodes publiques et n'appelle jamais `TeacherSubscriptionGuard.requireReadAccess()`.
  2. `TeacherImportPortAdapter` ne porte aucune annotation `@PreAuthorize` sur ses méthodes publiques et lit directement `specialtyRepository.existsByTeacherIdAndSubjectId(...)` (`:71`) après délégation à `teacherService.updateTeacher`.
- **Risque** : Un appel au port `TeacherDirectoryPort` sous abonnement `SUSPENDED` / `CANCELLED` / `MISSING` ou hors contexte authentifié n'est pas bloqué par le module `teacher`.
- **Correction précise** : Injecter `TeacherSubscriptionGuard` dans `TeacherDirectoryQueryService` (`requireReadAccess()` dans toutes les méthodes publiques) et annoter `@PreAuthorize("isAuthenticated()")` ; ajouter `@PreAuthorize(TeacherRoles.WRITE_ROLES)` sur `TeacherImportPortAdapter`.
- **Test à ajouter** : Test d'intégration vérifiant que `TeacherDirectoryPort` lève `TeacherSubscriptionReadDeniedException` lorsque le tenant est `SUSPENDED` ou sans abonnement.

##### `P2-023`
- **Identifiant** : `P2-023`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `teacher` — Axe A (Performance — N+1 résiduel)
- **Localisation** : `backend/src/main/java/com/ecole/saas/teacher/application/TeacherService.java:302-311, 394-400`
- **Constat factuel** : `TeacherService.listSpecialties(UUID teacherId)` (`:308-310`) itère sur `List<TeacherSpecialty>` et appelle `toSpecialtyView` (`:394-400`), qui invoque `academicStructurePort.findSubjectById(specialty.getSubjectId())` pour chaque élément au lieu d'un préchargement batch.
- **Risque** : Exécution de N requêtes SQL unitaires sur `subjects` lors de la consultation des spécialités d'un enseignant.
- **Correction précise** : Précharger en un seul appel `Map<UUID, SubjectSummary> subjectsById = academicStructurePort.findSubjectsByIds(subjectIds);` avant le mapping dans `TeacherService.listSpecialties`.
- **Test à ajouter** : Test d'intégration sur `GET /api/v1/teachers/{id}/specialties` avec plusieurs spécialités vérifiant l'absence de requêtes répétées.

##### `P2-024`
- **Identifiant** : `P2-024`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `teacher` — Axe A (Performance — chargement non borné)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/teacher/application/TeacherDirectoryQueryService.java:98-105`
  - `backend/src/main/java/com/ecole/saas/teacher/infrastructure/TeacherRepository.java:26`
- **Constat factuel** : `TeacherDirectoryQueryService.findAllActiveTeachers()` appelle `teacherRepository.findByStatusOrderByLastNameAscFirstNameAsc(TeacherStatus.ACTIVE)` sans pagination (`List<TeacherSummary>`).
- **Risque** : Chargement complet en mémoire de tous les enseignants actifs du tenant lors de la résolution d'audience (`communication`).
- **Correction précise** : Fournir une variante paginée/par lots (`Pageable`) sur `TeacherDirectoryPort`.
- **Test à ajouter** : Test vérifiant la pagination sur `TeacherDirectoryQueryService`.

##### `P2-025`
- **Identifiant** : `P2-025`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `teacher` — Axe C (Validation DTO)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/teacher/api/UpdateTeacherAssignmentRequest.java:9`
  - `backend/src/main/java/com/ecole/saas/teacher/api/TeacherAssignmentController.java:86`
  - `backend/src/main/java/com/ecole/saas/teacher/application/TeacherAssignmentService.java:232`
- **Constat factuel** : `UpdateTeacherAssignmentRequest.homeroomTeacher` (`Boolean`) ne porte pas `@NotNull`, mais `TeacherAssignmentController.updateAssignment` (`:86`) le convertit via `Boolean.TRUE.equals(...)` vers le paramètre primitif `boolean` de `TeacherAssignmentService.updateAssignment`.
- **Risque** : Perte silencieuse du statut de professeur principal lorsqu'un `PUT /api/v1/teacher-assignments/{id}` omet `homeroomTeacher`.
- **Correction précise** : Annoter `@NotNull` `UpdateTeacherAssignmentRequest.homeroomTeacher` (ou préserver la valeur existante si `null` côté service).
- **Test à ajouter** : Test `@WebMvcTest` vérifiant le rejet `400` lorsque `homeroomTeacher` est omis sur `PUT`.

##### `P2-026`
- **Identifiant** : `P2-026`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `teacher` — Axe C (Validation DTO & Couverture de tests)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/teacher/api/TeacherController.java:137-145`
  - `backend/src/test/java/com/ecole/saas/TeacherGradeAttendanceIntegrationTest.java:770-819`
- **Constat factuel** : `TeacherController.linkUserAccount` (`:140-141`) déclare `@RequestBody LinkTeacherUserRequest request` (requis par défaut) tout en testant `request != null ? request.userId() : null`. Par ailleurs, `TeacherGradeAttendanceIntegrationTest` ne teste pas le rejet de `DELETE` sous `ecole_app` sur `teachers`/`teacher_assignments`, ni la concurrence sur `homeroomTeacher` / `primarySpecialty`.
- **Risque** : Comportement ambigu sur body vide (`400`) et couverture de preuve incomplète sur les invariants `V8`.
- **Correction précise** : Ajouter `required = false` (ou supprimer le test `!= null` redondant) et compléter `TeacherGradeAttendanceIntegrationTest` avec les assertions `DELETE` refusé et concurrence `P2-021`.
- **Test à ajouter** : Assertions `DELETE` sous `ecole_app` et test concurrentiel `P2-021`.

---

### 2.5 Module `grade` (62 fichiers)

#### Performance
- Batching inter-ports conforme dans `EvaluationService` (`enrichEvaluationPage`, `enrichGradeViews`) et `ReportCardService` (`enrichReportCards`), persistance en lot via `saveAll`/`deleteAll`, pagination bornée `MAX_SIZE = 100` (`GradePagination`).
- Écarts majeurs : N+1 dans `GradeSummaryQueryService.findStudentYearSummaries` (`P2-029`) et index composites annuels manquants sur `period_report_cards` et `evaluations` (`P2-030`).

#### Sécurité / Périmètre
- `EvaluationService` (8 méthodes) et `ReportCardService` (7 méthodes) portent `@PreAuthorize(GradeRoles.*)`; `GradeTeacherScopeGuard` isole finement `TEACHER`/`TUTOR` aux couples `(classRoomId, subjectId)` affectés et au professeur principal ; `PARENT`, `STUDENT`, `CASHIER`, `ADMIN_SAAS` sont exclus ; zéro PII dans les logs ; domaine pur 21 fichiers ; RLS et `GRANT` sans `DELETE` sur les tables principales.
- Écarts : absence de `@PreAuthorize` et de garde d'abonnement sur `GradeSummaryQueryService` et `GradeImportPortAdapter.findEvaluationImportContext` (`P2-028`), et possibilité pour un `TEACHER` de fournir un `teacherId` tiers dans `createEvaluation` (`P2-032`).

#### Concurrence / Validation / Clock / Abonnement
- `Clock` injecté ; `@Valid` + types boxés partout ; calcul conforme à `.agents/rules/grades.md` (`GradingCalculator`, `RankingCalculator`, `MentionPolicy`) ; blocage strict sur période `CLOSED`/`LOCKED` ; `GradeSubscriptionGuard` vérifié sur les 2 services REST.
- Écarts : absence de `@Version` et de `@Lock(PESSIMISTIC_WRITE)`/`pg_advisory_xact_lock` sur l'ensemble des entités/repositories `grade` (`P2-027`), absence de circuit contrôlé d'amendement d'un bulletin `LOCKED` (`P2-031`), et bornes Bean Validation manquantes sur les DTO de notes (`P2-032`).

#### Points `[CONFORME]` vérifiés (`grade`)
1. **Batching inter-ports** : `EvaluationService.enrichEvaluationPage` (`:562-595`), `EvaluationService.enrichGradeViews` (`:625-644`) et `ReportCardService.enrichReportCards` (`:573-617`) préchargent via `find*ByIds` / `findByReportCardIdIn`.
2. **Persistance en lot** : `EvaluationService.upsertGrades` (`:481`) et `ReportCardService.computeClassReportCards` (`:307, 374, 377`) utilisent `saveAll` et `deleteAll`.
3. **Pagination & tri bornés** : `GradePagination` (`GradePagination.java:11-40`, `MAX_SIZE = 100`, liste blanche).
4. **`@PreAuthorize` sur services REST** : 8 méthodes publiques de `EvaluationService` et 7 méthodes publiques de `ReportCardService` annotées `@PreAuthorize(GradeRoles.*)`. Aucune `@Entity` exposée.
5. **Tenant & acteur JWT** : `TenantContext.requireCurrent()` systématique ; `actorId` extrait côté serveur (`GradeApiSupport.java:11-16`).
6. **Périmètre fin `TEACHER`/`TUTOR` & exclusion rôles tiers** : `GradeTeacherScopeGuard` (`:44-108`) contrôle `(classRoomId, subjectId)`, professeur principal (`homeroomAppreciation`) et direction (`directorAppreciation`/`councilDecision`) ; `PARENT`/`STUDENT`/`CASHIER`/`ADMIN_SAAS` bloqués (`TeacherGradeAttendanceIntegrationTest.java:651-665`).
7. **Domaine pur & zéro `float`/`double`** : zéro `org.springframework.*` dans `grade.domain` (21 fichiers) ; calculs en `BigDecimal` (`RoundingMode.HALF_UP`).
8. **Règles de calcul `grades.md`** : normalisation barème `(10, 20, 100)`, `ABSENT_UNJUSTIFIED = 0`, `ABSENT_JUSTIFIED`/`EXEMPTED` exclus, moyenne pondérée sur matières notées (`GradingCalculator.java:45-137`), ex æquo `1, 2, 2, 4` (`RankingCalculator.java:22-57`), mentions (`MentionPolicy.java:22-51`).
9. **Blocage période fermée/verrouillée** : `EvaluationService.assertPeriodOpenForGrading` (`:521-527`) et `ReportCardService.assertPeriodNotLocked` (`:555-565`).
10. **PII, logs & RLS** : zéro PII dans logs/audit ; RLS forcée (`V8:438-460`) et `GRANT SELECT, INSERT, UPDATE` sans `DELETE` sur `evaluations`, `student_grades`, `period_report_cards` (`V8:481-483`).
11. **`Clock` & `@Valid`/boxés** : `clock.instant()` dans `EvaluationService.java:98, 327, 354, 456` et `ReportCardService.java:86, 268, 479, 519, 540` ; `@Valid` et types boxés sur tous les DTO d'entrée.
12. **Garde d'abonnement sur services REST** : `GradeSubscriptionGuard` vérifié en lecture et en écriture sur `EvaluationService` et `ReportCardService`.

#### Constats (`grade`)

##### `P2-027`
- **Identifiant** : `P2-027`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `grade` — Axe C (Concurrence)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/grade/domain/PeriodReportCard.java:19-208`
  - `backend/src/main/java/com/ecole/saas/grade/domain/Evaluation.java:19-178`
  - `backend/src/main/java/com/ecole/saas/grade/domain/StudentGrade.java:18-105`
  - `backend/src/main/java/com/ecole/saas/grade/infrastructure/PeriodReportCardRepository.java:11-32`
  - `backend/src/main/java/com/ecole/saas/grade/infrastructure/EvaluationRepository.java:10-15`
  - `backend/src/main/java/com/ecole/saas/grade/application/ReportCardService.java:115-393, 458-553`
  - `backend/src/main/java/com/ecole/saas/grade/application/EvaluationService.java:274-494`
- **Constat factuel** : Aucune des 4 entités (`Evaluation`, `StudentGrade`, `PeriodReportCard`, `PeriodSubjectResult`) ne possède `@Version` (ni colonne `version` dans `V8`/`V12`). Aucun repository du module ne déclare `@Lock(LockModeType.PESSIMISTIC_WRITE)` ni `pg_advisory_xact_lock`. Les saisies (`upsertGrades`), recalculs de classe (`computeClassReportCards`) et transitions (`lockEvaluation`, `lockReportCard`, `cancelEvaluation`) s'exécutent sans sérialisation.
- **Risque** : Perte de mise à jour (`lost update`), modification/saisie simultanée passant avant le commit d'un verrouillage (`lockEvaluation`/`lockReportCard`), ou violation de contrainte unique (`uq_period_report_cards_tenant_period_student`, `uq_student_grades_tenant_eval_student`).
- **Correction précise** :
  - Migration `V16+` : ajouter `version BIGINT NOT NULL DEFAULT 0` sur `evaluations`, `student_grades`, `period_report_cards`, `period_subject_results`, plus `@Version` sur les 4 entités.
  - Ajouter `@Lock(LockModeType.PESSIMISTIC_WRITE) findByIdForUpdate(...)` sur `EvaluationRepository` et `PeriodReportCardRepository`, et acquérir un verrou consultatif `pg_advisory_xact_lock` par `(tenant_id, evaluation_period_id, class_room_id)` au début de `ReportCardService.computeClassReportCards`.
- **Test à ajouter** : Test d'intégration Testcontainers multi-threads vérifiant le recalcul concurrent de bulletins et les courses `upsertGrades` vs `lockEvaluation` et `updateReportCardAppreciations` vs `lockReportCard`.

##### `P2-028`
- **Identifiant** : `P2-028`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `grade` — Axes B & C (RBAC `@PreAuthorize` & Garde d'abonnement sur port/adaptateur)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/grade/application/GradeSummaryQueryService.java:37-182`
  - `backend/src/main/java/com/ecole/saas/grade/application/GradeImportPortAdapter.java:28-75`
- **Constat factuel** : `GradeSummaryQueryService` (implémentant `GradeSummaryPort`) n'a aucune annotation `@PreAuthorize` ni appel à `GradeSubscriptionGuard.requireReadAccess()`. De même, `GradeImportPortAdapter.findEvaluationImportContext(UUID)` (`:48-75`) accède directement aux repositories sans `@PreAuthorize` ni appel à `GradeSubscriptionGuard.requireReadAccess()`.
- **Risque** : Un appel au port sous abonnement `SUSPENDED`/`CANCELLED`/`MISSING` ou sans principal authentifié n'est pas bloqué par le module `grade`.
- **Correction précise** : Injecter `GradeSubscriptionGuard` dans `GradeSummaryQueryService` et `GradeImportPortAdapter` avec `requireReadAccess()` sur chaque méthode de lecture ; annoter `GradeSummaryQueryService` avec `@PreAuthorize("isAuthenticated()")` et `GradeImportPortAdapter` avec `@PreAuthorize(GradeRoles.EVALUATION_WRITE_ROLES)`.
- **Test à ajouter** : Test d'intégration vérifiant que `GradeSummaryPort` et `GradeImportPort.findEvaluationImportContext` lèvent `GradeSubscriptionReadDeniedException` quand le tenant est `SUSPENDED` ou sans abonnement.

##### `P2-029`
- **Identifiant** : `P2-029`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `grade` — Axe A (Performance — N+1)
- **Localisation** : `backend/src/main/java/com/ecole/saas/grade/application/GradeSummaryQueryService.java:63-73, 268-274`
- **Constat factuel** : `findStudentYearSummaries(UUID studentId, UUID academicYearId)` charge la liste des `PeriodReportCard` et appelle `this::toSummary` en boucle, lequel exécute `subjectResultRepository.findByReportCardIdOrderBySubjectNameAsc(card.getId())` pour chaque bulletin (1 + N requêtes).
- **Risque** : Multiplication des requêtes SQL lors de la consultation des relevés annuels d'un élève.
- **Correction précise** : Collecter les `cardIds`, exécuter en un seul appel `subjectResultRepository.findByReportCardIdIn(cardIds)`, grouper dans une `Map<UUID, List<PeriodSubjectResult>>` triée par `subjectName`, puis construire les résumés.
- **Test à ajouter** : Test d'intégration avec 3 bulletins annuels vérifiant l'agrégation complète en un seul batch.

##### `P2-030`
- **Identifiant** : `P2-030`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `grade` — Axe A (Index SQL)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/grade/infrastructure/PeriodReportCardRepository.java:24-26`
  - `backend/src/main/java/com/ecole/saas/grade/application/EvaluationService.java:160-162`
  - `backend/src/main/resources/db/migration/V8__teacher_grade_attendance.sql:184-187, 291-293`
- **Constat factuel** : `PeriodReportCardRepository.findByAcademicYearId` / `findByAcademicYearIdAndClassRoomIdIn` (utilisés par `getPeriodReportingOverview`) et le filtre `academicYearId` dans `EvaluationService.listEvaluations` ne disposent d'aucun index composite préfixé par `(tenant_id, academic_year_id)` dans `V8` ou `V12`.
- **Risque** : Balayage séquentiel multi-années sur `period_report_cards` et `evaluations`.
- **Correction précise** : Ajouter en `V16+` :
  ```sql
  CREATE INDEX CONCURRENTLY idx_period_report_cards_tenant_year_class
      ON period_report_cards (tenant_id, academic_year_id, class_room_id);
  CREATE INDEX CONCURRENTLY idx_evaluations_tenant_year_date
      ON evaluations (tenant_id, academic_year_id, evaluation_date DESC);
  ```
- **Test à ajouter** : Test Testcontainers vérifiant la présence des index dans `pg_indexes`.

##### `P2-031`
- **Identifiant** : `P2-031`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `grade` — Axe C (Règle métier `grades.md`)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/grade/domain/PeriodReportCard.java:180-208`
  - `backend/src/main/java/com/ecole/saas/grade/application/ReportCardService.java:460-553`
- **Constat factuel** : `.agents/rules/grades.md:7` stipule : « Bulletin : brouillon → validé → verrouillé. Modification après verrouillage : autorisation, motif, historique. » Dans l'implémentation actuelle, l'état `LOCKED` est strictement irréversible (`assertNotLocked()` bloque toute modification sans procédure autorisée de réouverture/amendement tracée).
- **Risque** : Impossibilité opérationnelle de corriger une erreur avérée sur un bulletin déjà verrouillé tout en garantissant l'autorisation, le motif et l'historique immuable.
- **Correction précise** : Ajouter `ReportCardService.reopenLockedReportCard(UUID reportCardId, String reason, UUID actorId)` protégée par `@PreAuthorize("hasRole('DIRECTOR')")`, exigeant un motif `@NotBlank` et une période non `LOCKED`, insérant une trace immuable (table `period_report_card_amendments` en `V16+` + `AuditAction`) avant de repasser le bulletin à `VALIDATED` (ou `DRAFT`).
- **Test à ajouter** : Test d'intégration vérifiant le refus `403` pour `CENSOR`/`TEACHER`, `400` sans motif, et le succès pour `DIRECTOR` avec persistance de l'historique d'amendement.

##### `P2-032`
- **Identifiant** : `P2-032`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `grade` — Axes B & C (Validation DTO & `teacherId` dans `createEvaluation`)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/grade/api/CreateEvaluationRequest.java:20, 28`
  - `backend/src/main/java/com/ecole/saas/grade/api/UpdateEvaluationRequest.java:20`
  - `backend/src/main/java/com/ecole/saas/grade/api/UpsertGradesRequest.java:13-24`
  - `backend/src/main/java/com/ecole/saas/grade/application/EvaluationService.java:235-239, 504-518`
- **Constat factuel** : `maxScore` et `score` n'ont pas `@DecimalMin`/`@DecimalMax` en couche API ; `UpsertGradesRequest.entries` n'a pas de `@Size(max = 200)` ; et dans `EvaluationService.createEvaluation`, un `TEACHER` affecté peut soumettre un `teacherId` tiers dans la requête.
- **Risque** : Validation des bornes différée au domaine, lot non borné et attribution possible d'une évaluation à un autre enseignant par un `TEACHER`.
- **Correction précise** : Ajouter `@DecimalMin("0.01") @DecimalMax("100.00")` sur `maxScore`, `@DecimalMin("0.00") @DecimalMax("100.00")` sur `score`, `@Size(max = 200)` sur `UpsertGradesRequest.entries`, et imposer l'enseignant authentifié dans `resolveTeacherId` lorsque `teacherScopeGuard.isRestrictedTeacher()` est vrai.
- **Test à ajouter** : Test `@WebMvcTest` sur les bornes et test d'intégration vérifiant qu'un `TEACHER` ne peut pas imputer une évaluation à un collègue.

---

### 2.6 Module `attendance` (42 fichiers)

#### Performance
- Batching conforme dans `AttendanceSessionService.enrichSessionPage`, `enrichRecordViews` et `AttendanceSummaryQueryService.getAttendanceReportingOverview` ; `saveAll` utilisé sur `createSession` et `upsertRecords` ; `AttendancePagination` bornée à `MAX_SIZE = 100`.
- Écarts majeurs : appel à `sessionRepository.findAll()` et filtrage en mémoire dans `computeStudentSummaryInternal`/`computeClassSummariesInternal` (`P2-036`), boucle N+1 dans `getStudentSummary` (`P2-037`), et index SQL secondaire manquant (`P2-038`).

#### Sécurité / Périmètre
- Les 9 méthodes publiques de `AttendanceSessionService` portent `@PreAuthorize(AttendanceRoles.*)` ; justification réservée à `DIRECTOR`/`CENTER_MANAGER`/`CENSOR`/`SECRETARY` et verrouillage/annulation à `ADMIN_MANAGE_ROLES` ; prévention du path traversal sur `justificationDocumentRef` ; zéro `@Entity` exposée ; domaine pur ; RLS forcée et `GRANT` sans `DELETE`.
- Écarts bloquants : faille IDOR dans `getSession` et `listSessions` permettant à un `TEACHER`/`TUTOR` d'accéder aux séances d'une matière non affectée dans sa classe (`P2-033`), et absence de `@PreAuthorize` et de garde d'abonnement sur `AttendanceSummaryQueryService` et `AttendanceImportPortAdapter.findSessionImportContext` (`P2-035`).

#### Concurrence / Validation / Clock / Abonnement
- `Clock` injecté ; `@Valid` et types boxés sur tous les DTO ; `AttendanceSubscriptionGuard` appelé en lecture et en écriture sur `AttendanceSessionService`.
- Écarts : absence de `@Version` et `@Lock(PESSIMISTIC_WRITE)` sur `AttendanceSession`/`AttendanceRecord` (`P2-034`), garde d'abonnement manquante sur ports (`P2-035`), et bornes Bean Validation manquantes sur `UpsertAttendanceRecordsRequest` (`P2-038`).

#### Points `[CONFORME]` vérifiés (`attendance`)
1. **Batching inter-ports** : `AttendanceSessionService.enrichSessionPage` (`:709-743`), `enrichRecordViews` (`:777-796`) et `AttendanceSummaryQueryService.getAttendanceReportingOverview` (`:125-131`) préchargent en batch.
2. **Persistance en lot** : `AttendanceSessionService.createSession` (`:269`) et `upsertRecords` (`:353`) utilisent `recordRepository.saveAll(...)`.
3. **Pagination & tri bornés** : `AttendancePagination` (`AttendancePagination.java:11-50`, `MAX_SIZE = 100`, liste blanche).
4. **`@PreAuthorize` sur service REST** : toutes les 9 méthodes publiques de `AttendanceSessionService` (`:116, 173, 185, 286, 359, 382, 403, 424, 458, 488`) portent `@PreAuthorize(AttendanceRoles.*)`. Aucune `@Entity` exposée.
5. **Séparation des rôles de justification & verrouillage** : `AttendanceRoles.JUSTIFY_ROLES` (`:9-10`) et `ADMIN_MANAGE_ROLES` (`:11-12`).
6. **Sécurité documentaire** : prévention du path traversal sur `justificationDocumentRef` (`AttendanceRecord.java:21-22, 200-210`).
7. **Domaine pur & zéro `float`/`double`** : zéro `org.springframework.*` dans `attendance.domain` (12 fichiers).
8. **PII, logs & RLS** : zéro PII dans logs/audit ; RLS forcée sur `attendance_sessions` et `attendance_records` (`V8:462-472`) ; `GRANT SELECT, INSERT, UPDATE` sans `DELETE` (`V8:484-485`).
9. **`Clock` & `@Valid`/boxés** : `clock.instant()` dans `AttendanceSessionService.java:93, 369, 389, 444` ; `@Valid` et types boxés partout.
10. **Garde d'abonnement sur service REST** : `AttendanceSubscriptionGuard` vérifié en lecture et en écriture sur toutes les méthodes de `AttendanceSessionService`.

#### Constats (`attendance`)

##### `P2-033`
- **Identifiant** : `P2-033`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `attendance` — Axe B (Sécurité / IDOR périmètre `TEACHER`/`TUTOR`)
- **Localisation** : `backend/src/main/java/com/ecole/saas/attendance/application/AttendanceSessionService.java:130-146, 179-180`
- **Constat factuel** :
  1. `getSession(UUID sessionId)` (`:180`) appelle `teacherScopeGuard.assertCanAccessClass(session.getClassRoomId())` au lieu de `teacherScopeGuard.assertCanAccessSessionScope(session.getClassRoomId(), session.getSubjectId())`.
  2. `listSessions(...)` (`:130-146`) ne vérifie `assertCanAccessSessionScope` que si `classRoomId != null`, et n'injecte dans la `Specification` qu'un filtre `root.get("classRoomId").in(scopedClasses)`, sans restreindre `subjectId` aux matières affectées à l'enseignant dans chaque classe.
- **Risque** : Un `TEACHER` ou `TUTOR` affecté uniquement à `(Classe 3E-A, Mathématiques)` peut lire les feuilles d'appel et justifications d'absence de `(Classe 3E-A, Français)` via `GET /api/v1/attendance/sessions` et `GET /api/v1/attendance/sessions/{sessionId}`.
- **Correction précise** :
  - À `AttendanceSessionService.java:180`, remplacer par `teacherScopeGuard.assertCanAccessSessionScope(session.getClassRoomId(), session.getSubjectId())`.
  - Dans `listSessions`, charger les affectations `(classRoomId, subjectId)` via `TeacherDirectoryPort.findActiveAssignmentsByUserId(userId)` et filtrer en `Specification` par `(subjectId IS NULL AND classRoomId IN allowedClasses) OR (couple (classRoomId, subjectId) dans les affectations)`.
- **Test à ajouter** : Dans `TeacherGradeAttendanceIntegrationTest`, créer une séance `(ClassA, Physics)` par le directeur et vérifier que le `TUTOR` affecté à `(ClassA, Math)` reçoit `403` sur `GET /api/v1/attendance/sessions/{physicsSessionId}` et ne la voit pas dans `GET /api/v1/attendance/sessions`.

##### `P2-034`
- **Identifiant** : `P2-034`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `attendance` — Axe C (Concurrence)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/attendance/domain/AttendanceSession.java:18-160`
  - `backend/src/main/java/com/ecole/saas/attendance/domain/AttendanceRecord.java:17-106`
  - `backend/src/main/java/com/ecole/saas/attendance/infrastructure/AttendanceSessionRepository.java:11-25`
  - `backend/src/main/java/com/ecole/saas/attendance/infrastructure/AttendanceRecordRepository.java:10-19`
  - `backend/src/main/java/com/ecole/saas/attendance/application/AttendanceSessionService.java:288-456`
- **Constat factuel** : Ni `AttendanceSession` ni `AttendanceRecord` ne portent `@Version` (ni colonne `version` dans `V8`/`V12`), et aucun repository n'expose `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
- **Risque** : Race conditions entre `upsertRecords` et `lockSession`/`cancelSession` (écriture de pointages sur une séance verrouillée/annulée en parallèle), entre `justifyRecord` et `upsertRecords` (réinitialisation de `justified=false` lors d'un passage concurrent à `PRESENT`), et collision sur `uq_attendance_records_tenant_session_student`.
- **Correction précise** : Ajouter `version BIGINT NOT NULL DEFAULT 0` sur `attendance_sessions` et `attendance_records` en `V16+`, ajouter `@Version` sur les deux entités, et utiliser `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`findByIdForUpdate`) sur `AttendanceSessionRepository` dans `upsertRecords`, `submitSession`, `lockSession`, `cancelSession` et `justifyRecord`.
- **Test à ajouter** : Test d'intégration Testcontainers concurrent `upsertRecords` vs `lockSession` et double `upsertRecords`.

##### `P2-035`
- **Identifiant** : `P2-035`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `attendance` — Axes B & C (RBAC `@PreAuthorize` & Garde d'abonnement sur port/adaptateur)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/attendance/application/AttendanceSummaryQueryService.java:36-187`
  - `backend/src/main/java/com/ecole/saas/attendance/application/AttendanceImportPortAdapter.java:27-75`
- **Constat factuel** : `AttendanceSummaryQueryService` (implémentant `AttendanceSummaryPort`) et `AttendanceImportPortAdapter.findSessionImportContext` (`:47-75`) ne portent aucune annotation `@PreAuthorize` et n'appellent pas `AttendanceSubscriptionGuard.requireReadAccess()`.
- **Risque** : Contournement de la garde d'abonnement et absence de `@PreAuthorize` sur des services applicatifs exposés.
- **Correction précise** : Injecter `AttendanceSubscriptionGuard` dans `AttendanceSummaryQueryService` et `AttendanceImportPortAdapter` avec `requireReadAccess()` sur chaque méthode de lecture ; annoter `AttendanceSummaryQueryService` avec `@PreAuthorize("isAuthenticated()")` et `AttendanceImportPortAdapter` avec `@PreAuthorize(AttendanceRoles.ROLL_CALL_WRITE_ROLES)`.
- **Test à ajouter** : Test vérifiant `AttendanceSubscriptionReadDeniedException` lorsque le tenant est `SUSPENDED` ou sans abonnement.

##### `P2-036`
- **Identifiant** : `P2-036`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `attendance` — Axe A (Performance — `findAll()` & filtrage mémoire)
- **Localisation** : `backend/src/main/java/com/ecole/saas/attendance/application/AttendanceSessionService.java:530-540, 581-591`
- **Constat factuel** :
  1. `computeStudentSummaryInternal` (`:530-534`) appelle `sessionRepository.findAll()` lorsque `evaluationPeriodId == null && academicYearId == null`, puis filtre `status != CANCELLED` et `fromDate`/`toDate` en Java.
  2. `computeClassSummariesInternal` (`:581-585`) appelle `findByAcademicYearIdAndStatusNot(...)` (toutes classes confondues) lorsque `evaluationPeriodId == null` sans couple complet `(fromDate, toDate)`, puis filtre `classRoomId` et dates en Java.
- **Risque** : Chargement en mémoire de toutes les séances d'appel multi-années/multi-classes du tenant sur `GET /api/v1/attendance/summaries/students/{id}` ou `GET /api/v1/attendance/summaries/classes/{id}`.
- **Correction précise** : Remplacer par des requêtes `Specification<AttendanceSession>` (ou requêtes JPQL dédiées) poussant `classRoomId`, `academicYearId`, `evaluationPeriodId`, `sessionDate >= fromDate`, `sessionDate <= toDate` et `status <> CANCELLED` directement en base.
- **Test à ajouter** : Test d'intégration appelant `getStudentSummary` sans `academicYearId`/`evaluationPeriodId` (avec `fromDate`/`toDate`) et `getClassSummaries` avec seulement `fromDate`.

##### `P2-037`
- **Identifiant** : `P2-037`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `attendance` — Axe A (Performance — N+1 dans `getStudentSummary`)
- **Localisation** : `backend/src/main/java/com/ecole/saas/attendance/application/AttendanceSessionService.java:474-483`
- **Constat factuel** : `getStudentSummary` exécute `allowedClasses.stream().flatMap(cid -> studentDirectoryPort.findActiveEnrollmentsByClassRoom(cid).stream()).anyMatch(...)`, déclenchant jusqu'à N appels inter-ports en boucle pour un enseignant affecté à N classes.
- **Risque** : Dégradation N+1 lors du contrôle de périmètre (en particulier sur les rejets `403` qui parcourent toutes les classes).
- **Correction précise** : Utiliser `studentDirectoryPort.isStudentActivelyEnrolledInAnyClass(studentId, allowedClasses)` en 1 seule requête SQL `EXISTS`.
- **Test à ajouter** : Test vérifiant l'accès et le refus `403` pour un enseignant affecté à 3 classes.

##### `P2-038`
- **Identifiant** : `P2-038`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `attendance` — Axes A & C (Index SQL & validation DTO)
- **Localisation** :
  - `backend/src/main/resources/db/migration/V8__teacher_grade_attendance.sql:374-378`
  - `backend/src/main/java/com/ecole/saas/attendance/api/UpsertAttendanceRecordsRequest.java:12-23`
- **Constat factuel** : Absence d'index composite `(tenant_id, class_room_id, evaluation_period_id)` sur `attendance_sessions` pour `findByClassRoomIdAndEvaluationPeriodIdAndStatusNot` ; `UpsertAttendanceRecordsRequest.records` n'a pas `@Size(max = 200)` et `lateMinutes` n'a pas `@Min(1) @Max(300)` en Bean Validation.
- **Risque** : Filtrage légèrement moins sélectif sur classes à fort historique et validation des bornes différée au domaine.
- **Correction précise** : Ajouter `CREATE INDEX CONCURRENTLY idx_attendance_sessions_tenant_class_period ON attendance_sessions (tenant_id, class_room_id, evaluation_period_id);` en `V16+` et `@Size(max = 200)` / `@Min(1) @Max(300)` sur le DTO.
- **Test à ajouter** : Test `@WebMvcTest` sur `UpsertAttendanceRecordsRequest`.

---

### 2.7 Module `center` (86 fichiers)

#### Performance
- Comptage agrégé `countByGroupIdInAndStatus`, préchargement batch via `findTeachersByIds`/`findStudentsByIds`, `generateSessions` optimisé en lot, `upsertAttendance`/`cancelSession` avec verrouillage ordonné `findByIdInForUpdate` (`ORDER BY p.id ASC`) et `saveAll`, indexation `V9` complète et pagination bornée `MAX_SIZE = 100` (`CenterPagination`).
- Écarts : N+1 dans `CenterPackService.listStudentPacks` (`P2-041`) et filtrage par date en mémoire dans `CenterDirectoryQueryService.getStudentCenterSummary` (`P2-042`).

#### Sécurité / Périmètre
- Les 4 services REST (`CenterGroupService`, `CenterPackService`, `CenterSessionService`, `CenterSummaryService`) portent `@PreAuthorize(CenterRoles.*)` ; `CenterTutorScopeGuard` restreint `TEACHER`/`TUTOR` aux cohortes/séances affectées et masque les packs financiers ; `PARENT`/`STUDENT`/`ADMIN_SAAS` sont exclus ; domaine pur (25 fichiers) ; zéro PII dans les logs ; RLS forcée et `GRANT` sans `DELETE` sur les 8 tables `V9`.
- Écarts bloquants : `CenterDirectoryQueryService` sans `@PreAuthorize` ni `CenterSubscriptionGuard` et fuite horizontale des notes/assiduité scolaires régulières vers un `TUTOR` dans `CenterSummaryService.getStudentOverview` (`P2-040`).

#### Concurrence / Validation / Temps / Monnaie / Abonnement
- Monnaie FCFA strictement en `long`/`Long`/`BIGINT` (zéro `float`/`double`), snapshot tarifaire immuable `hourly_rate_xof_snapshot` (`V12`), verrouillage consultatif sur la capacité d'inscription (`enrollStudent`) et double verrouillage `@Version` + `@Lock(PESSIMISTIC_WRITE)` ordonné sur `CenterStudentPack`, `Clock` injecté avec fuseau `Africa/Dakar`, `@Valid` et types boxés partout, `CenterSubscriptionGuard` + `CenterSchoolGuard` sur les 4 services REST.
- Écarts : absence de `@Version` et de `@Lock(PESSIMISTIC_WRITE)`/`pg_advisory_xact_lock` sur `CenterSession`, `CenterGroup.updateGroup`, `createSession`/`generateSessions` et `CenterSessionAttendance` (`P2-039`), plus validation mineure dates/DTO (`P2-042`).

#### Points `[CONFORME]` vérifiés (`center`)
1. **Agrégation & batching** : `CenterGroupService.listGroups` (`:146-153`) et `CenterDirectoryQueryService.findActiveGroupsByAcademicYear` (`:90-99`) via `countByGroupIdInAndStatus` ; `listGroupTutors` (`:326-332`), `listGroupEnrollments` (`:430-439`) et `toSessionView` (`:842-855`) préchargent en lot.
2. **Planification & pointage en lot** : `CenterSessionService.generateSessions` (`:331-413`), `upsertAttendance` (`:606-690`) et `cancelSession` (`:498-527`) préchargent, verrouillent de façon ordonnée (`ORDER BY p.id ASC`) et persistent via `saveAll`.
3. **Indexation SQL `V9`** : index préfixés `tenant_id` alignés sur toutes les requêtes des 9 repositories (`V9`).
4. **Pagination & tri bornés** : `CenterPagination` (`:11-52`, `MAX_SIZE = 100`, liste blanche de champs de tri).
5. **`@PreAuthorize` sur services REST** : toutes les méthodes publiques de `CenterGroupService` (9), `CenterPackService` (10), `CenterSessionService` (12) et `CenterSummaryService` (2) portent `@PreAuthorize(CenterRoles.*)`. Aucune `@Entity` exposée.
6. **Périmètre `TEACHER`/`TUTOR` & exclusion rôles tiers** : `CenterTutorScopeGuard` (`:45-134`) restreint aux cohortes/séances affectées, à sa propre charge et masque `packs` dans `getStudentOverview` (`:90-92`) ; `PARENT`/`STUDENT`/`ADMIN_SAAS` exclus (`CenterIntegrationTest.java:359-364`).
7. **Domaine pur & injection** : zéro `org.springframework.*` dans `center.domain` (25 fichiers), injection par constructeur uniquement.
8. **PII, logs & RLS** : zéro PII dans logs/audit ; RLS forcée sur les 8 tables `V9` (`V9:337-383`) et `GRANT SELECT, INSERT, UPDATE` sans `DELETE` (`V9:389-398`).
9. **Monnaie FCFA & snapshot tarifaire** : `long`/`Long`/`BIGINT` partout (vérifié statiquement et par `ArchitectureTest.java:103-125`), prorata en `BigDecimal` `HALF_UP` (`CenterParticipationCalculator.java:104-124`), snapshot `hourly_rate_xof_snapshot` (`V12:177-180`, `CenterSessionService.java:443-444`).
10. **Verrous existants sur packs & inscriptions** : `CenterLockRepository.lockGroupCapacity` (`CenterGroupService.java:468`), `@Version` (`V12:174-175`, `CenterStudentPack.java:75-77`) + `@Lock(PESSIMISTIC_WRITE)` ordonné (`CenterStudentPackRepository.java:32-38`).
11. **`Clock` + fuseau `Africa/Dakar`** : `Clock` injecté dans `CenterGroupService`, `CenterSessionService` et `CenterSchoolGuard.todayInSchoolZone` (`CenterSchoolGuard.java:18, 46-59`).
12. **`@Valid` & types boxés** : présent sur chaque `@RequestBody`, zéro primitif silencieux.
13. **Garde d'abonnement & école active sur services REST** : `CenterSubscriptionGuard` + `CenterSchoolGuard.requireActiveSchoolForWrite()` sur les 4 services REST.

#### Constats (`center`)

##### `P2-039`
- **Identifiant** : `P2-039`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `center` — Axe C (Concurrence)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/center/domain/CenterSession.java:23-83`
  - `backend/src/main/java/com/ecole/saas/center/domain/CenterGroup.java:23-80`
  - `backend/src/main/java/com/ecole/saas/center/domain/CenterSessionAttendance.java:19-63`
  - `backend/src/main/java/com/ecole/saas/center/application/CenterSessionService.java:261-454, 489-541, 547-702`
  - `backend/src/main/java/com/ecole/saas/center/application/CenterGroupService.java:222-255`
- **Constat factuel** :
  1. `CenterSession`, `CenterGroup` et `CenterSessionAttendance` n'ont pas `@Version` (seule `center_student_packs` a reçu `version` en `V12:174-175`), et `CenterSessionRepository` n'a pas de `@Lock(PESSIMISTIC_WRITE)`. Ainsi, `upsertAttendance(sessionId)` et `cancelSession(sessionId)` peuvent s'exécuter concurremment : si `cancelSession` lit `existingAttendances` avant le commit de `upsertAttendance`, la séance passe `CANCELLED` sans rembourser le crédit débité sur `CenterStudentPack`.
  2. `CenterGroupService.updateGroup` (`:240-253`) vérifie la capacité sans acquérir `centerLockRepository.lockGroupCapacity(groupId)`, en concurrence directe avec `enrollStudent`.
  3. `createSession` et `generateSessions` vérifient les conflits horaires (`assertNoSessionConflicts`) sans verrou consultatif sur la cohorte ou l'intervenant.
- **Risque** : Consommation définitive d'un crédit de pack payant sur une séance annulée (perte financière directe), dépassement de capacité de cohorte, ou double réservation horaire d'un intervenant.
- **Correction précise** :
  1. Migration `V16+` : ajouter `version BIGINT NOT NULL DEFAULT 0` sur `center_groups`, `center_sessions` et `center_session_attendances` + `@Version` sur les 3 entités.
  2. Ajouter `@Lock(LockModeType.PESSIMISTIC_WRITE) findByIdForUpdate(...)` sur `CenterSessionRepository` dans `startSession`, `completeSession`, `cancelSession` et `upsertAttendance`.
  3. Appeler `centerLockRepository.lockGroupCapacity(groupId)` dans `CenterGroupService.updateGroup`, et sérialiser `createSession`/`generateSessions` par `pg_advisory_xact_lock` sur cohorte/intervenant.
- **Test à ajouter** : Test d'intégration Testcontainers multi-threads exécutant en parallèle `upsertAttendance` et `cancelSession` sur la même séance, et `enrollStudent` vs `updateGroup`.

##### `P2-040`
- **Identifiant** : `P2-040`
- **Gravité** : `[BLOQUANT]`
- **Module & Axe** : `center` — Axes B & C (Périmètre `TUTOR`, RBAC `@PreAuthorize` & Garde d'abonnement)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/center/application/CenterDirectoryQueryService.java:46-240`
  - `backend/src/main/java/com/ecole/saas/center/application/CenterSummaryService.java:67-112`
- **Constat factuel** :
  1. `CenterDirectoryQueryService` (implémentant `CenterDirectoryPort`) n'a ni `@PreAuthorize` ni appel à `CenterSubscriptionGuard.requireReadAccess()`.
  2. Dans `CenterSummaryService.getStudentOverview` (`:86-105`), lorsqu'un `TUTOR` consulte un élève inscrit dans une de ses cohortes avec `evaluationPeriodId` ou `academicYearId`, le service retourne le bulletin scolaire régulier complet (`gradeSummaryPort.findStudentPeriodSummary`) et la synthèse d'assiduité scolaire globale (`attendanceSummaryPort.getStudentPeriodSummary`/`getStudentSummary`) sans vérifier l'affectation scolaire du tuteur, contournant l'isolation stricte des modules `grade` et `attendance`.
- **Risque** : Fuite horizontale de bulletins scolaires complets (toutes matières, rang, appréciations direction) vers un intervenant externe de soutien scolaire ; contournement de la garde d'abonnement sur `CenterDirectoryPort`.
- **Correction précise** :
  - Injecter `CenterSubscriptionGuard` dans `CenterDirectoryQueryService` (`requireReadAccess()` sur chaque méthode publique) et ajouter `@PreAuthorize("isAuthenticated()")`.
  - Dans `CenterSummaryService.getStudentOverview` (`:94-105`), lorsque `tutorScopeGuard.isRestrictedTutor()` est vrai, ne pas charger ni exposer `gradeSummary` ni `schoolAttendanceSummary` (les laisser à `null`, comme pour `packs` aux lignes 90-92).
- **Test à ajouter** : Test dans `CenterIntegrationTest` vérifiant qu'un `TUTOR` appelant `GET /api/v1/center/students/{id}/overview?evaluationPeriodId=...` reçoit `periodGradeSummary == null` et `schoolAttendanceSummary == null`, et test de `CenterDirectoryPort` sous tenant `SUSPENDED`.

##### `P2-041`
- **Identifiant** : `P2-041`
- **Gravité** : `[MAJEUR]`
- **Module & Axe** : `center` — Axe A (Performance — N+1)
- **Localisation** : `backend/src/main/java/com/ecole/saas/center/application/CenterPackService.java:217-236, 387-390`
- **Constat factuel** : `listStudentPacks` exécute `studentPackRepository.findAll(spec, pageable).map(this::toStudentPackView)` (`:235`), et `toStudentPackView` (`:387-390`) appelle individuellement `studentDirectoryPort.findStudentById(studentPack.getStudentId())` pour chaque élément de la page.
- **Risque** : Jusqu'à 101 requêtes par page sur `GET /api/v1/center/student-packs`.
- **Correction précise** : Collecter les `studentId` de `page.getContent()`, précharger via `studentDirectoryPort.findStudentsByIds(studentIds)` -> `Map<UUID, StudentSummary>`, puis mapper la page.
- **Test à ajouter** : Test d'intégration listant plusieurs `student-packs` d'élèves distincts avec vérification du chargement en lot.

##### `P2-042`
- **Identifiant** : `P2-042`
- **Gravité** : `[MINEUR]`
- **Module & Axe** : `center` — Axes A, B & C (Bornes dates séance, validation DTO & filtrage SQL)
- **Localisation** :
  - `backend/src/main/java/com/ecole/saas/center/application/CenterSessionService.java:261-309`
  - `backend/src/main/java/com/ecole/saas/center/api/CenterReasonRequest.java:5-8`
  - `backend/src/main/java/com/ecole/saas/center/api/UpsertCenterAttendanceRequest.java:13-15`
  - `backend/src/main/java/com/ecole/saas/center/application/CenterDirectoryQueryService.java:147-149`
- **Constat factuel** : `createSession` ne vérifie pas que `sessionDate` est dans `[group.startDate, group.endDate]` ; `CenterReasonRequest.reason` n'a pas `@NotBlank` (utilisé sur `archiveGroup`, `cancelStudentPack`, `cancelSession`) ; `UpsertCenterAttendanceRequest.records` n'a pas `@Size(max = 200)` ; `CenterDirectoryQueryService.getStudentCenterSummary` charge tous les pointages de l'élève avant filtrage par date en Java.
- **Risque** : Séances unitaires créées hors bornes de la cohorte, validation retardée au domaine et chargement non filtré par dates sur historique long.
- **Correction précise** : Valider `sessionDate` dans `[group.startDate, group.endDate]` dans `createSession`, introduire `RequiredCenterReasonRequest` (`@NotBlank @Size(max = 255)`), ajouter `@Size(max = 200)` sur `UpsertCenterAttendanceRequest.records`, et filtrer par plage de dates en JPQL dans `CenterSessionAttendanceRepository`.
- **Test à ajouter** : Test de rejet d'une séance unitaire hors dates de cohorte et test `@WebMvcTest` sur `UpsertCenterAttendanceRequest`.

---

## 3. Tableau récapitulatif transversal

| Module | N+1 résiduel | `findAll()` / non borné | `saveAll` / lots | `@PreAuthorize` | `SubscriptionAccessPort` (R/W) | `@Lock` / `@Version` | `Clock` (0 `*.now()`) | `@Valid` / types boxés | PII / Domaine pur / FCFA |
|---|---|---|---|---|---|---|---|---|---|
| **`academic`** | **OK** (batch `findAllById`) | **Partiel** (`P2-004` `findAllSubjects()` ; REST ≤ 100) | **OK** (`generateStandardPeriods`) | **Partiel / KO** (`P2-002` manquant sur `AcademicStructureQueryService` ; OK sur 5 services REST) | **Partiel / KO** (`P2-002` manquant sur port ; OK sur 5 services REST) | **KO** (`P2-001` `isCurrent` & `EvaluationPeriod` ; `P2-003` `capacity`) | **OK** (`clock.instant()`) | **Partiel** (`@Valid` & boxés, mais `P2-006` `@NotNull` `PUT`) | **OK** (0 PII, domaine pur) |
| **`student`** | **OK** (batch `StudentEnrollmentService` & `GROUP BY`) | **OK** (REST ≤ 100, port borné) | **OK** (mutations unitaires) | **Partiel / KO** (`P2-009` manquant sur `StudentDirectoryQueryService` ; OK sur 2 services REST) | **Partiel / KO** (`P2-009` manquant sur port ; OK sur 2 services REST + quota) | **Partiel** (matricule atomique + `pg_advisory_xact_lock` quota/capacité ; manque doublons `P2-010` & `@Version` `P2-012`) | **OK** (`Clock` + `Africa/Dakar`) | **OK** (`@Valid` & boxés partout) | **KO PII** (`P2-008` `StudentResponse`) ; domaine pur **OK** |
| **`family`** | **OK** (batch `findAllById` / `findStudentsByIds`) | **Partiel** (`P2-015` `findAllActiveGuardians()` ; `P2-017` `listFamilyStudents` mémoire) | **OK** (mutations unitaires) | **Partiel / KO** (`P2-014` manquant sur `FamilyDirectoryQueryService` & `FamilyImportPortAdapter`) | **Partiel / KO** (`P2-014` manquant sur port/adaptateur ; OK sur 3 services REST) | **KO** (`P2-013` bascule `primary`/`financial` ; doublons `P2-016`) | **OK** (`clock.instant()`) | **Partiel** (`@Valid` & boxés, mais `P2-018` 5 `Boolean` sans `@NotNull` en `PUT`) | **OK** (`nationalIdNumber` masqué, domaine pur) |
| **`teacher`** | **KO** (`P2-023` `listSpecialties`) | **Partiel** (`P2-024` `findAllActiveTeachers()` ; REST ≤ 100) | **OK** (mutations unitaires) | **Partiel / KO** (`P2-022` manquant sur `TeacherDirectoryQueryService` & `TeacherImportPortAdapter`) | **Partiel / KO** (`P2-022` manquant sur port ; OK sur 2 services REST) | **KO** (`P2-021` bascule `primarySpecialty` & `homeroomTeacher`) | **OK** (`clock.instant()`) | **Partiel** (`@Valid` & boxés, mais `P2-025` `homeroomTeacher` sans `@NotNull` en `PUT`) | **KO PII** (`P2-020` CNI à `PROMOTER`/`CENSOR`) ; domaine pur & `Long` XOF **OK** |
| **`grade`** | **KO** (`P2-029` `findStudentYearSummaries`) | **OK** (`Specification` paginée ≤ 100) | **OK** (`upsertGrades`, `computeClassReportCards`) | **Partiel / KO** (`P2-028` manquant sur `GradeSummaryQueryService` & `GradeImportPortAdapter.find*`) | **Partiel / KO** (`P2-028` manquant sur port/adaptateur ; OK sur 2 services REST) | **KO** (`P2-027` aucun `@Version` ni `@Lock`) | **OK** (`clock.instant()`) | **OK** (`@Valid` & boxés ; bornes `P2-032`) | **OK** (0 PII, domaine pur, `BigDecimal`, 0 `float`/`double`) |
| **`attendance`** | **KO** (`P2-037` `getStudentSummary`) | **KO** (`P2-036` `findAll()` dans `computeStudentSummaryInternal`) | **OK** (`createSession`, `upsertRecords`) | **Partiel / KO** (`P2-035` manquant sur ports + IDOR `P2-033` `getSession`/`listSessions`) | **Partiel / KO** (`P2-035` manquant sur port/adaptateur ; OK sur `AttendanceSessionService`) | **KO** (`P2-034` aucun `@Version` ni `@Lock`) | **OK** (`clock.instant()`) | **OK** (`@Valid` & boxés ; bornes `P2-038`) | **OK** (0 PII, domaine pur) |
| **`center`** | **KO** (`P2-041` `listStudentPacks`) | **OK** (`Specification` paginée ≤ 100 ; mineur `P2-042` sur port) | **OK** (`generateSessions`, `upsertAttendance`, `cancelSession`) | **Partiel / KO** (`P2-040` manquant sur `CenterDirectoryQueryService` + fuite `TUTOR` `getStudentOverview`) | **Partiel / KO** (`P2-040` manquant sur port ; OK sur 4 services REST) | **Partiel / KO** (`@Version`+`@Lock` OK sur `CenterStudentPack`, `pg_advisory_xact_lock` `enrollStudent` ; manquant `CenterSession`/`updateGroup`/`createSession` `P2-039`) | **OK** (`Clock` + `Africa/Dakar`) | **OK** (`@Valid` & boxés ; mineur `P2-042`) | **OK** (0 PII, domaine pur, FCFA `long`/`BIGINT`, 0 `float`/`double`) |

---

## 4. Contrôle Quality Gate (`.agents/rules/quality-gate.md`)

Verdict global Passe 2 : **KO** (16 constats `[BLOQUANT]`, 12 `[MAJEUR]`, 14 `[MINEUR]`).

| Section Quality Gate | Verdict | Points conformes & Écarts bloquants / majeurs constatés |
|---|---|---|
| **1. Sécurité & Multi-Tenant (Bloquant)** | **KO** | **OK** : `TenantContext` exclusivement côté JWT serveur ; RLS `ENABLE` + `FORCE` fail-closed, FK & `UNIQUE` composites `(tenant_id, ...)` et `REVOKE DELETE`/moindre privilège `ecole_app` sur `V2/V5/V6/V8/V9/V12` ; aucun secret ni `application-test.yml` dans `src/main/resources/`. **KO** : absence de `@PreAuthorize` sur les 7 `*QueryService` de ports et 4 adaptateurs d'import (`P2-002`, `P2-009`, `P2-014`, `P2-022`, `P2-028`, `P2-035`, `P2-040`) ; faille IDOR `TEACHER`/`TUTOR` par matière dans `attendance` (`P2-033`) ; fuite bulletin/assiduité scolaire vers `TUTOR` dans `center` (`P2-040`) ; exposition PII sans masquage dans `StudentResponse` (`P2-008`) et `TeacherResponse` (`P2-020`). |
| **2. Intégrité, Concurrence & Cohérence (Bloquant)** | **KO** | **OK** : `Clock` injecté + fuseau `Africa/Dakar` (zéro `*.now()`) ; montants FCFA strictement en `long`/`Long`/`BIGINT` (zéro `float`/`double`) ; `@Valid` présent sur chaque `@RequestBody` ; types boxés sur DTO ; `SubscriptionAccessPort` vérifié sur les 21 services REST. **KO** : verrous `@Lock`/`@Version`/`pg_advisory_xact_lock` manquants sur mutations concurrentes critiques (`P2-001`, `P2-003`, `P2-013`, `P2-021`, `P2-027`, `P2-034`, `P2-039`) ; détection de doublons manquante (`P2-010`, `P2-016`) ; `SubscriptionAccessPort` absent sur les 7 `*QueryService` et lectures d'adaptateurs d'import (`P2-002`, `P2-009`, `P2-014`, `P2-022`, `P2-028`, `P2-035`, `P2-040`) ; réouverture contrôlée de bulletin `LOCKED` manquante (`P2-031`) ; `@NotNull` / bornes manquants sur quelques DTO `PUT` (`P2-006`, `P2-018`, `P2-025`, `P2-032`, `P2-038`, `P2-042`). |
| **3. Performance & Scalabilité (Majeur)** | **KO** | **OK** : `saveAll` sur les traitements de lot ; zéro appel externe bloquant dans `@Transactional` ; pagination REST bornée à `100` sur les 7 modules. **KO** : 4 boucles N+1 résiduelles (`P2-023`, `P2-029`, `P2-037`, `P2-041`), 1 `findAll()` non borné (`P2-036`), 3 méthodes de port non paginées (`P2-004`, `P2-015`, `P2-024`), filtrage en mémoire (`P2-017`, `P2-036`, `P2-042`) et index SQL composites manquants (`P2-005`, `P2-011`, `P2-015`, `P2-030`, `P2-038`). |
| **4. Architecture & Maintenabilité (Bloquant)** | **OK** | **OK** : zéro import `org.springframework.*` dans `..domain..` sur l'ensemble des 7 modules (138 fichiers `domain`), injection par constructeur uniquement, aucune `@Entity` exposée par les contrôleurs, frontières Modulith vérifiées par `ArchitectureTest`, erreurs `BusinessException` traduites en `ProblemDetail` RFC 9457 en français. *(Réserve fonctionnelle `P2-031` sur le workflow d'amendement après verrouillage de bulletin.)* |
| **5. Preuve obligatoire** | **KO** | **OK** : 6 suites d'intégration/architecture Testcontainers + 11 suites unitaires domaine présentes et structurées. **KO** : couverture manquante sur la concurrence multi-threads (`P2-001`, `P2-007`, `P2-012`, `P2-013`, `P2-019`, `P2-021`, `P2-026`, `P2-027`, `P2-034`, `P2-039`), sur les scénarios IDOR/PII (`P2-008`, `P2-020`, `P2-033`, `P2-040`) et sur l'application des gardes d'abonnement aux ports inter-modules (`P2-002`, `P2-009`, `P2-014`, `P2-022`, `P2-028`, `P2-035`, `P2-040`). |
