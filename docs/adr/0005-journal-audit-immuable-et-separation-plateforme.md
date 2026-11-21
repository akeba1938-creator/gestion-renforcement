# ADR 0005 — Journal d'audit immuable en ajout seul, séparation plateforme et frontière `AuditLogPort` acyclique

- Statut : accepté

## Contexte
Le module `audit` doit tracer de manière fiable et infalsifiable les opérations critiques de tous les modules métier (`identity`, `subscription`, `school`, `academic`, `student`, `family`, `finance`, `teacher`, `grade`, `attendance`, `center`), tout en respectant :
1. L'absence totale de cycle entre modules Spring Modulith (`ApplicationModules.verify()`).
2. La séparation stricte entre données d'établissement (isolées par `tenant_id` + RLS fail-closed) et opérations d'administration plateforme (`ADMIN_SAAS`).
3. L'immutabilité absolue en ajout seul et la protection contre la journalisation de données sensibles.

## Décision
1. **Module socle transversal (`business modules -> audit -> shared`)** :
   - `audit` expose la frontière publique `AuditLogPort` (et ses commandes/enums immuables) dans `com.ecole.saas.audit` et ne dépend d'aucun module métier (uniquement de `shared`).
   - L'existence d'un établissement cible lors d'une consultation supervisée `ADMIN_SAAS` s'appuie sur l'intégrité référentielle de sa propre table (`audit_logs.tenant_id REFERENCES schools(id)`), évitant toute dépendance vers `school` ou accès SQL direct à une table tierce.
2. **Séparation en deux tables append-only distinctes (`V10__audit_module.sql`)** :
   - `audit_logs` : `tenant_id NOT NULL`, Hibernate `@TenantId` et politique RLS forcée `tenant_id = current_tenant_id()` (fail-closed).
   - `platform_audit_logs` : table dédiée `ADMIN_SAAS` avec politique RLS forcée `current_tenant_id() IS NULL`, garantissant au niveau PostgreSQL qu'aucune session établissement ne peut lire ni insérer d'événement plateforme.
   - Les deux tables sont protégées contre tout `UPDATE` et `DELETE` par quatre verrous cumulés : entités sans setters avec `@PreUpdate`/`@PreRemove`, dépôts Spring Data `Repository` sans méthode de suppression, droits PostgreSQL `ecole_app` limités à `SELECT, INSERT`, et triggers SQL `BEFORE UPDATE OR DELETE`.
3. **Assainissement pur dans le domaine (`AuditMetadataSanitizer`)** :
   - Toute clé sensible est supprimée et tout motif sensible (JWT, `Bearer`, `otpauth://`, CNI/NIN/passeport, secrets opaques) est masqué en `[MASQUE]` avant sérialisation JSON déterministe.
   - L'accès `ADMIN_SAAS` au journal d'un établissement exige un motif explicite (≥ 10 caractères) journalisé à la fois dans `platform_audit_logs` et dans `audit_logs` de l'établissement.

## Conséquences
- Graphe Spring Modulith strictement acyclique vérifié automatiquement par `ArchitectureTest`.
- Transparence complète pour les établissements lorsqu'un administrateur SaaS consulte leurs journaux d'audit.
- Impossibilité technique de falsifier ou supprimer l'historique d'audit, même en cas de compromission applicative.
