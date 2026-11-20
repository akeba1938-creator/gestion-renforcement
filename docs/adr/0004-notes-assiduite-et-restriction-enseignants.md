# ADR 0004 — Moteur de calcul des notes en domaine pur et restriction fine des enseignants via `TeacherDirectoryPort`

- Statut : accepté

## Contexte
Les modules `teacher`, `grade` et `attendance` doivent :
1. Respecter strictement la frontière Spring Modulith (aucun cycle entre modules) et l'indépendance de la couche `domain` (Java pur sans dépendance Spring).
2. Garantir la reproductibilité et la vérifiabilité des calculs de moyennes, arrondis, absences et classements ex æquo.
3. Restreindre finement les comptes `TEACHER` et `TUTOR` à leurs propres classes et matières affectées, y compris sur la lecture des élèves (`student`), des notes (`grade`) et de l'assiduité (`attendance`).

## Décision
1. **Graphe acyclique des ports** :
   - `identity` expose `UserDirectoryPort` (lecture seule) pour valider l'appartenance intra-tenant d'un compte lié à un enseignant.
   - `teacher` ne dépend pas de `student` et expose `TeacherDirectoryPort`.
   - `student`, `grade` et `attendance` consomment `TeacherDirectoryPort` de façon unidirectionnelle pour appliquer la restriction fine `TEACHER`/`TUTOR` côté serveur à partir du sujet JWT.
2. **Calculs pédagogiques en domaine pur** :
   - `GradingCalculator`, `GradingPolicy`, `MentionPolicy`, `RankingCalculator` (`grade.domain`) et `AttendanceSummaryCalculator` (`attendance.domain`) utilisent exclusivement `BigDecimal` (`RoundingMode.HALF_UP`, 2 décimales) et des entiers, sans aucun flottant ni annotation Spring.
   - Par défaut (politique sénégalaise), `ABSENT_UNJUSTIFIED` compte pour `0`, `ABSENT_JUSTIFIED` et `EXEMPTED` sont neutres, les notes d'évaluations sur un barème inférieur sont normalisées sur `SchoolProfileSnapshot.gradingScale`, et le classement applique l'ordre de compétition standard (`1, 2, 2, 4`) avec indicateur `tiedRank`.
3. **Blocage strict sur période fermée/verrouillée** :
   - Toute mutation d'évaluation ou de note exige `EvaluationPeriod.status == OPEN` ; dès `CLOSED` ou `LOCKED`, l'opération est refusée en `409 Conflict` (`urn:ecole:problem:evaluation-period-closed`).

## Conséquences
- Aucune dépendance circulaire dans Spring Modulith (`ApplicationModules.verify()`).
- Les règles de calcul sont testables unitairement en quelques millisecondes sans contexte Spring et prêtes pour la personnalisation par établissement si besoin en V2.
- La défense en profondeur reste complète : JWT + `@PreAuthorize` + garde de périmètre enseignant + `@TenantId` + RLS PostgreSQL forcée (`fail-closed`).
