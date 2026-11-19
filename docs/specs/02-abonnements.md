# Spécification — Module `subscription` (abonnements, forfaits et quotas)

## Objectif
Gérer le catalogue global des forfaits SaaS, le cycle de vie complet des abonnements par établissement (période d'essai, activation, renouvellement, changement de forfait, impayé avec période de grâce, suspension, annulation), le calcul des quotas effectifs (élèves, utilisateurs, stockage) et exposer une frontière publique Spring Modulith permettant aux autres modules de vérifier l'état d'abonnement et la disponibilité des quotas.

## Acteurs et permissions
- **Administrateur SaaS (`ADMIN_SAAS`)** :
  - Gestion complète du catalogue des forfaits (`/api/v1/platform/subscription-plans/**`).
  - Gestion complète des abonnements et de l'historique d'un établissement (`/api/v1/platform/schools/{schoolId}/subscriptions/**`).
- **Promoteur (`PROMOTER`), Directeur (`DIRECTOR`), Responsable de centre (`CENTER_MANAGER`)** :
  - Consultation de l'abonnement courant, des quotas effectifs, de l'historique et des événements de **leur propre établissement uniquement** (`/api/v1/subscriptions/**`).
- **Tous les utilisateurs authentifiés (avec jeton d'accès valide)** :
  - Consultation du catalogue des forfaits actifs (`/api/v1/subscription-plans/**`).
- **Autres modules applicatifs** (via la frontière publique `SubscriptionAccessPort`) :
  - Vérification de l'état opérationnel et des quotas pour le tenant courant sans exposer les entités internes ni les tables du module.

## Règles métier
1. **Devise et montants** : tous les montants sont stockés et échangés en entiers FCFA (`long` / `BIGINT`), positifs ou nuls. Une période d'essai démarre avec un montant facturé à `0` FCFA.
2. **Catalogue des forfaits (`subscription_plans`)** :
   - Code normalisé en majuscules, unique globalement.
   - Quotas de base (`max_students`, `max_users`, `max_storage_mb`) strictement positifs ; durée d'essai par défaut `trial_days` comprise entre `0` et `365` jours.
   - Un forfait désactivé (`active = false`) ne peut plus être souscrit ni choisi lors d'un changement de forfait, mais reste consultable pour les abonnements historiques.
3. **Unicité de l'abonnement ouvert par établissement** :
   - Un établissement ne peut avoir qu'un seul abonnement ouvert (`TRIAL`, `ACTIVE` ou `PAST_DUE`) à la fois (garanti côté applicatif et par index unique partiel PostgreSQL).
4. **Machine à états stricte** :
   - `TRIAL` → `ACTIVE`, `SUSPENDED`, `CANCELLED`
   - `ACTIVE` → `PAST_DUE`, `SUSPENDED`, `CANCELLED`
   - `PAST_DUE` → `ACTIVE`, `SUSPENDED`, `CANCELLED`
   - `SUSPENDED` → `ACTIVE`, `CANCELLED`
   - `CANCELLED` → état terminal (aucune transition sortante).
   - Suspension et annulation exigent un motif explicite.
5. **Quotas effectifs** :
   - `effective_max = base_quota + extra_quota` pour chaque ressource (`STUDENTS`, `USERS`, `STORAGE_MB`).
   - Les extensions (`extra_*`) sont positives ou nulles et ajustables avec motif.
   - Lors d'un changement de forfait, les quotas de base sont alignés sur le nouveau forfait ; les extensions peuvent être conservées ou réinitialisées explicitement.
6. **Évaluation de l'état d'accès effectif (`EffectiveAccessState`)** :
   - `TRIAL_ACTIVE` (`TRIAL` et `now < trial_ends_at` et `now < ends_at`) : lecture et écriture autorisées.
   - `ACTIVE` (`ACTIVE` et `now < ends_at`) : lecture et écriture autorisées.
   - `PAST_DUE_GRACE` (`PAST_DUE` et `grace_period_ends_at != null` et `now < grace_period_ends_at`) : lecture et écriture autorisées.
   - `EXPIRED` (`TRIAL`, `ACTIVE` ou `PAST_DUE` dont l'échéance/essai/grâce est dépassée) : **lecture seule autorisée** (`readAllowed = true`, `writeAllowed = false`).
   - `SUSPENDED`, `CANCELLED`, `MISSING` : lecture et écriture opérationnelles bloquées (`readAllowed = false`, `writeAllowed = false`).
7. **Journal d'événements (`subscription_events`)** :
   - Chaque création ou mutation d'abonnement produit un événement immuable numéroté (`sequence_no` unique par `(tenant_id, subscription_id)`) dans la même transaction.
8. **Isolation multi-tenant** :
   - Sur `/api/v1/subscriptions/**`, le tenant est déduit exclusivement du JWT côté serveur (`TenantContext`), jamais d'un en-tête, paramètre ou corps client.
   - Les opérations `ADMIN_SAAS` vérifient l'existence de l'établissement via `SchoolDirectory` et encapsulent explicitement le contexte tenant avant ouverture de la transaction (`set_config` + `@TenantId`).

## Cas limites
- Dépassement d'entier lors du calcul `base + extra` → rejeté par le domaine.
- Démarrage d'essai sur un forfait avec `trial_days = 0` sans surcharge explicite → refusé (400).
- Changement de forfait vers un forfait inactif → refusé (409).
- Utilisation atteignant exactement la limite (`currentUsage + requestedAdditional == limit`) → autorisée ; au-delà (`> limit`) → refusée (409 `subscription-quota-exceeded`).
- Demande de quota sur un abonnement expiré, suspendu, annulé ou absent → refusée (403 `subscription-access-denied`).

## Modèle de données (résumé)
- `subscription_plans` (global) : complété en V4 par `description` et `trial_days`.
- `subscriptions` (isolée par `tenant_id`, RLS forcée) : complétée en V4 par `trial_ends_at`, `grace_period_ends_at`, `suspended_at`, `cancelled_at`, `status_reason`, `extra_students`, `extra_users`, `extra_storage_mb`, et index unique partiel `uq_subscriptions_open_per_tenant`.
- `subscription_events` (isolée par `tenant_id`, RLS forcée, ajout seul pour `ecole_app`) : historique numéroté des opérations d'abonnement.

## API (résumé)
- Public authentifié : `GET /api/v1/subscription-plans`, `GET /api/v1/subscription-plans/{planId}`.
- Plateforme (`ADMIN_SAAS`) :
  - `/api/v1/platform/subscription-plans` (`GET`, `POST`, `PUT /{planId}`, `POST /{planId}/activate`, `POST /{planId}/deactivate`).
  - `/api/v1/platform/schools/{schoolId}/subscriptions` (`POST /trial`, `POST`, `GET /current`, `GET`, `GET /{subscriptionId}/events`, `POST /{subscriptionId}/activate`, `POST /{subscriptionId}/renew`, `PUT /{subscriptionId}/plan`, `PUT /{subscriptionId}/quotas`, `POST /{subscriptionId}/past-due`, `POST /{subscriptionId}/suspend`, `POST /{subscriptionId}/cancel`).
- Établissement (`PROMOTER`, `DIRECTOR`, `CENTER_MANAGER`) :
  - `GET /api/v1/subscriptions/current`
  - `GET /api/v1/subscriptions/current/quotas`
  - `GET /api/v1/subscriptions`
  - `GET /api/v1/subscriptions/{subscriptionId}/events`

## Critères d'acceptation
- Étant donné un établissement en essai actif (14 jours, 100 élèves max), quand un module vérifie le quota pour `99 + 1` élèves, alors l'opération est autorisée ; pour `100 + 1` élèves, alors `SubscriptionQuotaExceededException` (409) est levée.
- Étant donné un abonnement dont `ends_at` est dépassé (`EXPIRED`), quand un module demande l'état d'accès, alors `readAllowed = true`, `writeAllowed = false`, et `assertQuotaAvailable` lève `SubscriptionAccessDeniedException` (403).
- Étant donné deux établissements A et B, quand le directeur de A appelle `GET /api/v1/subscriptions/current` avec `X-Tenant-Id: <B>` ou `GET /api/v1/subscriptions/{subB}/events`, alors il reçoit uniquement les données de A ou 404 pour la ressource de B.
- Étant donné un secrétaire ou un enseignant, quand il appelle `/api/v1/subscriptions/current` ou `/api/v1/platform/subscription-plans`, alors il reçoit 403 `ProblemDetail` RFC 9457.
- Sous le rôle PostgreSQL `ecole_app`, la RLS isole `subscriptions` et `subscription_events`, bloque tout `INSERT` cross-tenant et interdit `UPDATE`/`DELETE` sur `subscription_events`.

## Hors périmètre
- Facturation automatique, paiement en ligne des abonnements (mobile money / carte) et relances planifiées automatiques (phases ultérieures).

## Questions ouvertes
- Politique automatique de transition `ACTIVE` → `PAST_DUE` → `SUSPENDED` par tâche planifiée (`@Scheduled`) à définir lors du module de facturation SaaS.
