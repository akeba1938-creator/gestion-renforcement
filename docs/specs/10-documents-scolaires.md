# Spécification — Module `schooldocs` (documents scolaires et administratifs officiels)

## Objectif
Gérer les modèles de documents scolaires et administratifs d'un établissement, l'émission officielle et séquentielle de certificats et attestations (certificats de scolarité, attestations d'inscription, d'assiduité, de réussite, convocations, documents administratifs personnalisés), les duplicatas/rééditions tracés avec motif, la révocation irréversible, la vérification d'authenticité par jeton opaque et le téléchargement PDF sécurisé — avec contrôle RBAC fin, périmètre élève/tuteur/classe et isolation multi-tenant stricte.

## Acteurs et permissions
- **Directeur (`DIRECTOR`), Responsable de centre (`CENTER_MANAGER`), Secrétariat (`SECRETARY`)** :
  - Gestion complète des modèles (`/api/v1/schooldocs/templates/**`).
  - Émission et réédition (duplicatas) de tous les types de documents officiels.
  - Consultation, téléchargement, historique, vérification d'authenticité et synthèse établissement.
  - Révocation réservée à `DIRECTOR`, `CENTER_MANAGER` et `PROMOTER` (`SECRETARY` → `403`).
- **Promoteur (`PROMOTER`)** :
  - Gestion des modèles, consultation, téléchargement, vérification, synthèse et révocation de documents officiels.
  - Aucune émission/réédition directe (`403`).
- **Censeur (`CENSOR`)** :
  - Lecture/aperçu des **modèles actifs uniquement** (`active = true` forcé côté serveur).
  - Émission et réédition limitées aux types pédagogiques et disciplinaires (`ATTENDANCE_ATTESTATION`, `SUCCESS_ATTESTATION`, `SUMMONS`) ; les certificats de scolarité, attestations d'inscription et documents administratifs personnalisés sont refusés (`403`).
  - Consultation, téléchargement, vérification et synthèse de l'établissement. Aucune révocation (`403`).
- **Caissier (`CASHIER`)** :
  - Lecture/aperçu des modèles actifs uniquement, consultation, téléchargement et vérification des documents de l'établissement. Aucune écriture ni synthèse (`403`).
- **Enseignant (`TEACHER`) et Intervenant (`TUTOR`)** :
  - Lecture/aperçu des **modèles actifs uniquement** (`active = true` forcé côté serveur).
  - Consultation, historique, téléchargement et vérification **strictement restreints aux élèves activement inscrits dans leurs classes affectées** (ou aux documents rattachés à une de leurs classes via `TeacherDirectoryPort` + `StudentDirectoryPort`). Tout accès hors périmètre retourne `403` (`urn:ecole:problem:schooldocs-scope-forbidden`).
  - Aucune émission, réédition, révocation ni gestion de modèles (`403`).
- **Parent (`PARENT`)** :
  - Consultation, historique, téléchargement et vérification **strictement restreints aux documents des élèves qui lui sont activement rattachés** (ou adressés à son propre `guardianId` via `FamilyDirectoryPort`). Tout accès hors périmètre retourne `403`.
  - Aucun accès aux modèles, à l'émission, à la révocation ni à la synthèse (`403`).
- **Élève (`STUDENT`)** :
  - Consultation, historique, téléchargement et vérification **strictement restreints à ses propres documents** (résolus via `StudentDirectoryPort.findStudentContactByUserId`). Tout accès à un autre élève retourne `403`.
  - Aucun accès aux modèles, à l'émission, à la révocation ni à la synthèse (`403`).
- **Administrateur SaaS (`ADMIN_SAAS`)** :
  - Accès refusé (`403`) sur l'ensemble des routes tenant `/api/v1/schooldocs/**`.

## Frontières Spring Modulith (graphe strictement acyclique)
- **Ports consommés (unidirectionnels)** :
  - `SchoolDirectory` (`school`)
  - `SubscriptionAccessPort` (`subscription`) + implémentation du SPI `QuotaUsageProvider` (`QuotaResource.STORAGE_MB`)
  - `AcademicStructurePort` (`academic`)
  - `StudentDirectoryPort` (`student`)
  - `FamilyDirectoryPort` (`family`)
  - `TeacherDirectoryPort` (`teacher`)
  - `AuditLogPort` (`audit`) — nouvelle catégorie `AuditCategory.SCHOOLDOCS`
- **Port public exposé** : `SchoolDocsDirectoryPort` (`com.ecole.saas.schooldocs`, lecture seule).

## Règles métier principales
1. **Modèles de documents (`schooldocs_templates`)** :
   - Code normalisé en majuscules (`^[A-Z0-9][A-Z0-9_-]{1,49}$`), unique par `(tenant_id, code)`.
   - Moteur `DocumentTemplateRenderer` (domaine pur) en une seule passe sur `{{variable}}`, empêchant toute ré-évaluation de contenu injecté.
   - Frais éventuels (timbre/duplicata) strictement en entiers FCFA (`long` / `BIGINT >= 0`, zéro flottant).
   - Archivage logique (`active = false`) : un modèle inactif ne peut plus servir à une nouvelle émission (`409`) et reste invisible en lecture/aperçu hors rôles `PROMOTER`, `DIRECTOR`, `CENTER_MANAGER`, `SECRETARY`.
2. **Émission officielle séquentielle et idempotente (`schooldocs_documents`, `schooldocs_sequences`)** :
   - Numérotation séquentielle atomique unique par établissement et année (`DOC-<CODE_ECOLE>-<ANNEE>-NNNNNN`), l'année étant calculée avec `Clock` injecté dans le fuseau de l'établissement (défaut `Africa/Dakar`).
   - Clé d'idempotence obligatoire (`Idempotency-Key` ou `idempotencyKey`), unique par `(tenant_id, idempotency_key)` avec empreinte SHA-256 : rejeu identique → `200 OK` (`X-Idempotent-Replay: true`) sans doublon ni saut de séquence ; payload différent → `409 Conflict`.
   - Jeton opaque de vérification (`verification_token` : 32 octets aléatoires Base64URL, zéro donnée personnelle, jamais journalisé).
   - Génération PDF 1.4 valide en Java pur (`SimplePdfDocumentRenderer`) avec contrôle d'octets magiques (`%PDF-`), plafond 5 Mo, empreinte SHA-256, contrôle du quota `QuotaResource.STORAGE_MB` et stockage binaire privé immuable (`schooldocs_document_contents`).
3. **Duplicatas / rééditions tracées et révocation (`PESSIMISTIC_WRITE`)** :
   - Toute réédition (`POST /api/v1/schooldocs/documents/{id}/reissue`) et toute révocation (`POST /api/v1/schooldocs/documents/{id}/revoke`) verrouille le document cible (et son original racine lors d'une réédition) en `PESSIMISTIC_WRITE`.
   - Chaque duplicata reçoit son propre numéro officiel séquentiel unique, son propre jeton opaque, `is_duplicate = true`, `original_document_id` pointant vers l'original racine, `edition_number` séquentiel (`2, 3, ...`, unique par `(tenant_id, original_document_id, edition_number)`) et un motif obligatoire (`5..500` caractères).
   - L'original consigne l'événement immuable `DUPLICATE_ISSUED` relié au duplicata. Un document révoqué ne peut plus être réédité (`409 document-revoked`).
   - La révocation (`ISSUED → REVOKED`) exige un motif explicite (`5..500` caractères) et est irréversible (`409 document-already-revoked` en cas de doublon).
4. **Vérification d'authenticité et téléchargement sécurisé** :
   - `GET /api/v1/schooldocs/verify/{verificationToken}` (authentifié intra-tenant, soumis à la RLS et au périmètre appelant) évalue dynamiquement `VALID | EXPIRED | REVOKED` à partir de `Clock` et contrôle l'intégrité binaire SHA-256.
   - `GET /api/v1/schooldocs/documents/{id}/download` contrôle RBAC, périmètre, type réel `%PDF-` et SHA-256, renvoie `Content-Type: application/pdf`, `Content-Disposition: attachment` assaini, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, `X-Document-Sha256`, et enregistre `SCHOOLDOCS_DOCUMENT_DOWNLOADED` dans `audit_logs`.
5. **Performance, abonnement et isolation multi-tenant** :
   - Zéro N+1 sur les listes paginées (préchargement en lot via `find*ByIds`) et agrégations SQL/JPQL sur `/summary`.
   - Garde abonnement (`SubscriptionAccessPort.assertReadAllowed()` en lecture, `requireOperationalSubscription()` + quota `STORAGE_MB` en écriture).
   - Tenant issu exclusivement du JWT (`TenantContext`) + Hibernate `@TenantId` + RLS PostgreSQL activée et forcée (`fail-closed`) + clés étrangères composites `(tenant_id, <fk_id>)`.

## Modèle de données (migration `V13__schooldocs_module.sql`)
- Extension non destructive de `chk_audit_logs_category` et `chk_platform_audit_logs_category` (`SCHOOLDOCS`).
- 5 tables métier isolées par `tenant_id NOT NULL`, RLS activée et forcée (`fail-closed`), droits moindre privilège `ecole_app` :
  - `schooldocs_sequences`
  - `schooldocs_templates`
  - `schooldocs_documents`
  - `schooldocs_document_contents` *(immuable, ajout seul, trigger SQL `BEFORE UPDATE OR DELETE`)*
  - `schooldocs_document_events` *(immuable, ajout seul, trigger SQL `BEFORE UPDATE OR DELETE`)*
