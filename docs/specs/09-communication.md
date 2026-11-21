# Spécification — Module `communication` (notifications multicanaux, campagnes, consentement et boîte de réception in-app)

## Objectif
Fournir aux écoles privées et centres de renforcement un module de communication multicanal (`IN_APP`, `SMS`, `WHATSAPP`, `EMAIL`) isolé par établissement, couvrant :
- la gestion de modèles de messages réutilisables avec variables `{{...}}` et estimation des coûts en entiers FCFA ;
- la gestion des préférences de contact et du consentement (opt-in/opt-out par canal et marketing) ;
- les campagnes et envois ciblés ou groupés (tuteurs, familles, élèves, classes, cohortes centre, enseignants/intervenants, personnel) avec idempotence stricte et déduplication des destinataires ;
- le suivi complet du cycle de vie des remises (`QUEUED`, `SENT`, `DELIVERED`, `FAILED`, `READ`) via un adaptateur abstrait de passerelle sans appel réseau externe en test et un journal immuable en ajout seul ;
- la boîte de réception in-app personnelle (`/api/v1/communication/inbox/**`) ;
- la frontière publique Spring Modulith `CommunicationPort` (`com.ecole.saas.communication`) pour les notifications métier déclenchées par d'autres modules (absence, reçu de paiement, publication de bulletin, rappel d'échéance).

## Acteurs et permissions
- **Promoteur (`PROMOTER`), Directeur (`DIRECTOR`), Responsable de centre (`CENTER_MANAGER`), Secrétariat (`SECRETARY`)** :
  - Gestion complète des modèles (`/api/v1/communication/templates/**`), des préférences de contact (`/api/v1/communication/preferences`), des campagnes/envois (`/api/v1/communication/campaigns/**`), du suivi/relance des remises (`/api/v1/communication/deliveries/**`) et de la synthèse (`/api/v1/communication/summary`).
- **Caissier (`CASHIER`)** :
  - Consultation des modèles actifs et des campagnes/remises financières uniquement.
  - Envoi de communications **strictement limité aux catégories financières** (`PAYMENT_RECEIPT`, `PAYMENT_DUE_REMINDER`) vers les familles/tuteurs/élèves ; toute autre catégorie ou gestion de modèles est refusée (`403`).
- **Censeur (`CENSOR`)** :
  - Consultation des modèles actifs et des campagnes/remises non financières.
  - Envoi de convocations, alertes d'absence, publications de bulletins et messages pédagogiques (`SUMMONS`, `ABSENCE_ALERT`, `REPORT_CARD_PUBLISHED`, `CENTER_SESSION`, `GENERAL`, `ANNOUNCEMENT`) ; toute communication financière ou édition de modèles est refusée (`403`).
- **Enseignant (`TEACHER`) et Intervenant (`TUTOR`)** :
  - Consultation des modèles actifs et de leurs propres envois uniquement.
  - Envoi limité aux catégories `GENERAL`, `ABSENCE_ALERT`, `CENTER_SESSION` et **strictement restreint aux classes (`TeacherDirectoryPort`) et cohortes centre (`CenterDirectoryPort`) qui leur sont activement affectées** (et aux élèves qui y sont inscrits). Tout envoi hors périmètre ou global est refusé (`403 urn:ecole:problem:communication-scope-forbidden`).
- **Parent (`PARENT`) et Élève (`STUDENT`)** :
  - Accès exclusif à leur boîte de réception in-app (`/api/v1/communication/inbox/**`) et à leurs préférences personnelles (`/api/v1/communication/preferences/me`).
  - Tout accès aux routes back-office (`/templates`, `/campaigns`, `/deliveries`, `/summary`, `/preferences` admin) est refusé (`403`).
- **Administrateur SaaS (`ADMIN_SAAS`)** :
  - Accès refusé (`403`) sur toutes les routes tenant `/api/v1/communication/**`.

## Frontières Spring Modulith (strictement acyclique)
- **Ports consommés (unidirectionnels, lecture seule sauf audit)** :
  - `SchoolDirectory` (`school`)
  - `SubscriptionAccessPort` (`subscription`)
  - `AcademicStructurePort` (`academic`)
  - `StudentDirectoryPort` (`student`)
  - `FamilyDirectoryPort` (`family`)
  - `TeacherDirectoryPort` (`teacher`)
  - `CenterDirectoryPort` (`center`)
  - `UserDirectoryPort` (`identity`)
  - `AuditLogPort` (`audit`)
- **Port public exposé** : `CommunicationPort` (`com.ecole.saas.communication`).

## Règles métier
1. **Modèles de messages (`communication_templates`)** :
   - Code normalisé en majuscules (`^[A-Z0-9][A-Z0-9_-]{1,49}$`), unique par `(tenant_id, code)`.
   - Objet obligatoire pour `EMAIL` et `IN_APP`, optionnel pour `SMS` et `WHATSAPP`.
   - Coût unitaire estimé en entiers FCFA (`long` / `BIGINT >= 0`).
   - Moteur `TemplateRenderer` (domaine pur) en une seule passe : aucune ré-interprétation des variables injectées (`{{...}}`).
   - Calcul des segments SMS en domaine pur (`SmsSegmentCalculator`) : GSM-7 (160/153) vs Unicode (70/67), coût calculé avec `Math.multiplyExact`.
   - Archivage logique (`active = false`) : un modèle inactif ne peut plus être choisi pour un nouvel envoi (`409`).
2. **Consentement et préférences (`communication_contact_preferences`)** :
   - Unicité par `(tenant_id, contact_type, contact_id)` (`GUARDIAN`, `STUDENT`, `TEACHER`, `STAFF_USER`).
   - Évaluation pure par `ConsentPolicy` :
     - Si le canal demandé est désactivé → remise enregistrée en `FAILED` avec `failure_code = CHANNEL_OPTED_OUT` (coût `0` FCFA).
     - Si `marketing_opt_in = false` sur une annonce ou campagne respectant l'opt-out marketing → `FAILED` (`MARKETING_OPTED_OUT`). Les notifications transactionnelles obligatoires (`ABSENCE_ALERT`, `PAYMENT_RECEIPT`, `PAYMENT_DUE_REMINDER`, `REPORT_CARD_PUBLISHED`, `SUMMONS`) ne sont pas bloquées par `marketing_opt_in = false` (mais respectent toujours la désactivation explicite du canal).
     - Si la coordonnée requise est absente (téléphone E.164 pour SMS/WhatsApp, e-mail pour EMAIL, `userId` pour IN_APP) → `FAILED` (`MISSING_CONTACT_ADDRESS`).
3. **Campagnes, audiences et idempotence (`communication_campaigns`, `communication_deliveries`)** :
   - Référence séquentielle atomique par établissement et année (`MSG-<CODE_ECOLE>-<ANNEE>-NNNNNN`).
   - Clé d'idempotence obligatoire (`Idempotency-Key` ou `idempotencyKey`), unique par `(tenant_id, idempotency_key)` avec empreinte SHA-256 : rejeu identique → `200 OK` (`X-Idempotent-Replay: true`) sans doublon ; payload différent → `409 Conflict`.
   - Déduplication stricte par `(tenant_id, campaign_id, recipient_type, recipient_id, channel)` : un parent ayant plusieurs enfants dans une classe/cohorte ne reçoit qu'un seul message par campagne.
   - Adaptateur abstrait `ChannelDeliveryGateway` (`InAppDeliveryGateway`, `LocalStubSmsGateway`, `LocalStubWhatsAppGateway`, `LocalStubEmailGateway`) : aucun appel réseau externe en test ni secret en dur.
4. **Machine à états des remises et journal immuable (`communication_delivery_events`)** :
   - Transitions strictes (`DeliveryStatusMachine`) : `QUEUED → SENT | DELIVERED | FAILED`, `SENT → DELIVERED | FAILED | READ`, `DELIVERED → READ`, `FAILED → QUEUED` (relance technique `retry-failed` réservée aux échecs `GATEWAY_ERROR`), `READ` terminal (idempotent).
   - Journal `communication_delivery_events` strictement immuable en ajout seul : entité sans setters avec `@PreUpdate`/`@PreRemove`, dépôt sans `delete*`, privilèges `ecole_app` limités à `SELECT, INSERT` et trigger SQL `BEFORE UPDATE OR DELETE`.
5. **Boîte de réception in-app (`/api/v1/communication/inbox/**`)** :
   - Filtrée exclusivement par `(tenant_id, recipient_user_id = sub JWT, channel = IN_APP)`. Un utilisateur ne peut ni lire ni marquer lu le message d'un autre utilisateur (`404`).
6. **Garde abonnement et isolation multi-tenant** :
   - `EXPIRED` → lecture seule `200`, écriture `403` ; `SUSPENDED` / `CANCELLED` / `MISSING` → lecture et écriture `403`.
   - Tenant déduit exclusivement du JWT (`TenantContext`) + Hibernate `@TenantId` + RLS PostgreSQL activée et forcée (`fail-closed`) sur les 6 tables de `V11`.

## Modèle de données (migration `V11__communication_module.sql`)
- Extension non destructive des contraintes `chk_audit_logs_category` et `chk_platform_audit_logs_category` (`COMMUNICATION`).
- 6 nouvelles tables isolées par `tenant_id NOT NULL`, RLS activée et forcée (`fail-closed`), droits `ecole_app` :
  - `communication_sequences`
  - `communication_templates`
  - `communication_contact_preferences`
  - `communication_campaigns`
  - `communication_deliveries`
  - `communication_delivery_events` *(immuable, ajout seul, trigger SQL)*
