# Quality Gate Obligatoire (avant toute fin de tâche)

À chaque ajout, modification ou suppression de code, migration SQL ou configuration,
effectuer cette auto-vérification AVANT de proposer `.\mvnw.cmd verify -B` :

## 1. Sécurité & Multi-Tenant (Bloquant)
- **Tenant** : résolu exclusivement via `TenantContext` (JWT serveur), jamais depuis un en-tête, query param ou body client.
- **SQL / Flyway** : ne jamais modifier une migration déjà appliquée (`V1`..`Vn`). Toute nouvelle table métier porte `tenant_id UUID NOT NULL REFERENCES schools(id)`, des index préfixés par `tenant_id`, des `UNIQUE` et `FOREIGN KEY` composites `(tenant_id, ...)`, `ENABLE` + `FORCE ROW LEVEL SECURITY` fail-closed, et des privilèges `ecole_app` au moindre privilège (`REVOKE DELETE` sur les tables historiques/archivage logique ; `SELECT, INSERT` + trigger `BEFORE UPDATE OR DELETE` sur les tables immuables).
- **RBAC & Périmètre** : `@PreAuthorize` explicite sur chaque service applicatif ; restriction fine vérifiée pour `TEACHER`/`TUTOR` et `PARENT`/`STUDENT` ; masquage des PII sensibles (ex. CNI/NIN) hors rôles habilités ; aucune donnée sensible dans les logs ou `audit_logs`.
- **Secrets** : aucun secret ni fichier de test (`application-test.yml`) dans `src/main/resources/`.

## 2. Intégrité, Concurrence & Cohérence (Bloquant)
- **Concurrence** : `@Lock(PESSIMISTIC_WRITE)` et/ou `@Version` (ou `pg_advisory_xact_lock`) sur toute mutation de solde, capacité, quota, pack ou séquence.
- **DTO & Validation** : `@Valid` présent sur chaque `@RequestBody` ; champs obligatoires en types boxés (`Long`, `Integer`, `Boolean`) annotés `@NotNull` (jamais de primitifs silencieux sous Jackson 3).
- **Temps & Monnaie** : montants FCFA strictement en `long` / `BIGINT` (zéro `float`/`double`) ; `Clock` injecté + fuseau établissement (défaut `Africa/Dakar`), jamais d'appel direct à `Instant.now()` / `LocalDate.now()`.
- **Garde d'abonnement** : `SubscriptionAccessPort` vérifié en lecture (`assertReadAllowed()`) et écriture sur tous les services métier.

## 3. Performance & Scalabilité (Majeur)
- **Zéro N+1** : sur toute liste paginée ou traitement en lot, précharger les références inter-ports en batch (`find*ByIds` -> `Map<UUID, ...>`) et persister via `saveAll`.
- **Transactions courtes** : aucun appel passerelle/externe à l'intérieur d'une transaction `@Transactional` ; privilégier les agrégations et incréments atomiques JPQL/SQL plutôt que `findAll()` en mémoire.

## 4. Architecture & Maintenabilité (Bloquant)
- **Domaine pur** : zéro import `org.springframework.*` dans `..domain..`.
- **Spring Modulith** : injection par constructeur uniquement, zéro cycle entre modules, aucune `@Entity` exposée par un contrôleur.
- **Erreurs** : `BusinessException` traduite en `ProblemDetail` RFC 9457 (en français), avec `requestId`.

## 5. Preuve obligatoire
- Chaque nouvelle règle critique (RBAC, RLS, concurrence, immutabilité, batch) doit être couverte par un test unitaire ou d'intégration Testcontainers PostgreSQL.
- En fin de tâche, inclure un bref récapitulatif **Contrôle Quality Gate : OK / Points vérifiés**.
