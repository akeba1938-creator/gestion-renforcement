# Spécification — Module `audit` (journal d'audit immuable en ajout seul)

## Objectif
Assurer une traçabilité immuable, infalsifiable et multi-tenant des actions critiques de la plateforme et des établissements (authentification, sécurité, abonnements, administration établissement et structure académique, dossiers élèves et familles, opérations financières, enseignants/notes/verrouillages/assiduité et centre de renforcement), via :
- une frontière publique Spring Modulith `AuditLogPort` (`com.ecole.saas.audit`) permettant aux modules métier d'enregistrer leurs événements de façon découplée et sans cycle ;
- une API REST strictement en lecture seule de consultation, filtrage et pilotage pour la direction d'établissement et l'administration plateforme (`ADMIN_SAAS`).

## Acteurs et permissions
- **Promoteur (`PROMOTER`), Directeur (`DIRECTOR`), Responsable de centre (`CENTER_MANAGER`)** :
  - Consultation filtrée, paginée et triée du journal d'audit et de la synthèse de **leur propre établissement uniquement** (`/api/v1/audit/logs`, `/api/v1/audit/logs/{id}`, `/api/v1/audit/summary`).
  - Aucun accès au journal plateforme `/api/v1/platform/audit/**` (`403`).
- **Administrateur SaaS (`ADMIN_SAAS`)** :
  - Consultation filtrée, paginée et triée du journal plateforme (`/api/v1/platform/audit/logs`, `/api/v1/platform/audit/logs/{id}`).
  - Consultation supervisée du journal d'un établissement cible (`/api/v1/platform/schools/{schoolId}/audit/logs?reason=...`) avec **motif obligatoire** (10 à 500 caractères) automatiquement journalisé dans `platform_audit_logs` et dans `audit_logs` de l'établissement consulté.
  - Aucun accès direct à `/api/v1/audit/**` (`403`).
- **Secrétariat (`SECRETARY`), Caissier (`CASHIER`), Censeur (`CENSOR`), Enseignant (`TEACHER`), Intervenant (`TUTOR`), Parent (`PARENT`), Élève (`STUDENT`)** :
  - Accès refusé (`403`) sur l'ensemble des routes `/api/v1/audit/**` et `/api/v1/platform/audit/**`.
- **Toutes méthodes d'écriture HTTP (`POST`, `PUT`, `PATCH`, `DELETE`)** :
  - Strictement inexistantes (`405 Method Not Allowed` au format `ProblemDetail` RFC 9457).

## Règles métier et sécurité
1. **Immutabilité stricte en ajout seul (défense en profondeur à 4 niveaux)** :
   - **Domaine** : entités `AuditLog` et `PlatformAuditLog` sans setters, colonnes `updatable = false`, callbacks `@PreUpdate` et `@PreRemove` levant `UnsupportedOperationException`.
   - **Dépôts** : `AuditLogRepository` et `PlatformAuditLogRepository` étendent `org.springframework.data.repository.Repository` (aucune méthode `delete*`).
   - **Droits PostgreSQL (`ecole_app`)** : `GRANT SELECT, INSERT` uniquement (aucun `UPDATE` ni `DELETE`).
   - **Triggers SQL** : `BEFORE UPDATE OR DELETE` sur `audit_logs` et `platform_audit_logs` exécutant `prevent_audit_immutable_mutation()` (SQLSTATE `55000`), bloquant toute altération y compris pour le propriétaire de table.
2. **Isolation multi-tenant et séparation stricte plateforme** :
   - Le tenant de `/api/v1/audit/**` provient exclusivement du JWT vérifié côté serveur (`TenantContext`), jamais d'un en-tête (`X-Tenant-Id`), paramètre ou corps client.
   - `audit_logs` : `tenant_id UUID NOT NULL REFERENCES schools(id)`, Hibernate `@TenantId` + RLS PostgreSQL activée et forcée en fail-closed (`USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id())`).
   - `platform_audit_logs` : table dédiée sans `tenant_id`, RLS PostgreSQL activée et forcée (`USING (current_tenant_id() IS NULL) WITH CHECK (current_tenant_id() IS NULL)`), garantissant son invisibilité et son inaccessibilité depuis toute session établissement.
3. **Zéro donnée sensible dans les métadonnées (`AuditMetadataSanitizer`, domaine pur)** :
   - Suppression systématique des clés sensibles (`password`, `passwd`, `pwd`, `secret`, `token`, `jwt`, `bearer`, `cookie`, `authorization`, `credential`, `api_key`, `private_key`, `encryption_key`, `totp`, `otp`, `pin`, `cni`, `nin`, `passport`, `piece_identite`, `numero_piece`, `iban`, `cvv`, `raw_body`, `raw_payload`, `verification_token`, etc.).
   - Masquage (`[MASQUE]`) des motifs sensibles dans les valeurs, résumés et motifs (JWT, `Bearer`/`Basic`, `otpauth://`, paires `clé=valeur` sensibles, numéros de CNI/NIN/passeport, chaînes opaques ≥ 40 caractères).
   - Bornage strict (max 25 entrées, valeurs ≤ 200 caractères, JSON sérialisé ≤ 4096 caractères).
4. **Corrélation et acteur** :
   - Enrichissement automatique côté serveur par `AuditActorResolver` (`actorId`, `actorType`, `actorRoles` depuis le JWT authentifié et `correlationId` depuis `MDC.requestId` alimenté par `TenantContextFilter`).
5. **Frontière Spring Modulith acyclique** :
   - `audit` ne dépend que de `shared`. Tous les modules métier consomment `AuditLogPort` de manière unidirectionnelle.

## Modèle de données (migration `V10__audit_module.sql`)
- `prevent_audit_immutable_mutation()`
- `audit_logs` (isolée par `tenant_id`, RLS fail-closed, ajout seul, trigger immuable)
- `platform_audit_logs` (séparée pour `ADMIN_SAAS`, RLS `current_tenant_id() IS NULL`, ajout seul, trigger immuable)

## Critères d'acceptation
- Les actions critiques des modules `identity`, `subscription`, `school`, `academic`, `student`, `family`, `finance`, `teacher`, `grade`, `attendance` et `center` créent des entrées immuables dans `audit_logs` (et `platform_audit_logs` pour les opérations plateforme) sans aucune donnée sensible.
- `PROMOTER`, `DIRECTOR` et `CENTER_MANAGER` consultent uniquement les événements de leur établissement ; les autres rôles établissement et `ADMIN_SAAS` reçoivent `403` sur `/api/v1/audit/**`.
- `ADMIN_SAAS` consulte `/api/v1/platform/audit/**` et `/api/v1/platform/schools/{schoolId}/audit/logs?reason=...` (refus `400` sans motif suffisant, `404` si établissement inconnu, double journalisation en cas d'accès valide).
- Toute tentative d'`UPDATE` ou `DELETE` sur `audit_logs` ou `platform_audit_logs` est rejetée par PostgreSQL sous `ecole_app` et sous le rôle propriétaire.
- Sous `ecole_app`, la RLS isole strictement `audit_logs` (0 ligne sans `TenantContext`, rejet d'`INSERT` cross-tenant) et rend `platform_audit_logs` totalement inaccessible lorsqu'un contexte tenant est actif.
