# ADR 0007 — Module `schooldocs` : génération PDF 1.4 pur Java, stockage binaire privé sous RLS, duplicatas verrouillés et contrôle fin de périmètre

- Statut : accepté

## Contexte
Le module `schooldocs` doit permettre aux écoles privées et centres de renforcement d'éditer, rééditer, vérifier, révoquer et distribuer des documents scolaires officiels (certificats de scolarité, attestations, convocations) tout en garantissant :
1. L'absence totale de cycle Spring Modulith (`ApplicationModules.verify()`) avec les modules amont (`school`, `subscription`, `academic`, `student`, `family`, `teacher`, `audit`).
2. Une intégrité binaire vérifiable sans ajouter de dépendance tierce lourde ou non auditée dans le MVP.
3. Une sérialisation concurrente fiable des numéros d'édition de duplicatas et des révocations.
4. Le cloisonnement strict des documents selon le tenant (JWT + RLS fail-closed) et selon le périmètre individuel (`STUDENT`, `PARENT`, `TEACHER`/`TUTOR`).

## Décision
1. **Graphe acyclique et quota stockage `STORAGE_MB`** :
   - `schooldocs` consomme unidirectionnellement `SchoolDirectory`, `SubscriptionAccessPort`, `AcademicStructurePort`, `StudentDirectoryPort`, `FamilyDirectoryPort`, `TeacherDirectoryPort` et `AuditLogPort`.
   - `schooldocs` implémente le SPI `QuotaUsageProvider` (`com.ecole.saas.subscription`) pour remonter l'usage stockage réel (`QuotaResource.STORAGE_MB`, arrondi au MiB supérieur en arithmétique entière) et vérifie la disponibilité avant toute persistance binaire.
   - `schooldocs` expose `SchoolDocsDirectoryPort` en lecture seule pour les modules aval (`reporting`).
2. **Générateur PDF 1.4 déterministe en Java pur et stockage binaire abstrait (`DocumentBinaryStorage`)** :
   - Les contrats `OfficialDocumentRenderer` et `DocumentBinaryStorage` résident dans `domain` en Java pur.
   - `SimplePdfDocumentRenderer` (`infrastructure`) produit un fichier PDF 1.4 A4 conforme (`%PDF-1.4 ... %%EOF`, `WinAnsiEncoding` pour les accents français, en-tête officiel, mention `DUPLICATA`, jeton opaque de vérification).
   - `DatabaseDocumentBinaryStorage` (`infrastructure`) stocke les octets dans `schooldocs_document_contents` (table strictement append-only protégée par RLS fail-closed, privilèges `SELECT, INSERT` uniquement et trigger SQL `BEFORE UPDATE OR DELETE`), avec vérification systématique des octets magiques `%PDF-`, de la limite 5 Mo et de l'empreinte SHA-256 à l'écriture et au téléchargement. Un basculement ultérieur vers un stockage S3 privé s'effectue par simple remplacement d'adaptateur d'infrastructure.
3. **Concurrence et intégrité des duplicatas/révocations** :
   - `reissueDocument` et `revokeDocument` acquièrent un verrou pessimiste `LockModeType.PESSIMISTIC_WRITE` sur le document cible (et sur le document original racine lors d'une réédition d'un duplicata), complété par `@Version` et des index uniques composites `(tenant_id, original_document_id, edition_number)`.
   - La numérotation officielle (`DOC-<CODE_ECOLE>-<ANNEE>-NNNNNN`) est générée atomiquement par `(tenant_id, sequence_year)` via `INSERT ... ON CONFLICT DO UPDATE ... RETURNING` sur `schooldocs_sequences`, l'année étant résolue via `Clock` injecté et le fuseau de l'établissement (défaut `Africa/Dakar`).
4. **Vérification d'authenticité authentifiée intra-tenant et périmètre fin** :
   - `GET /api/v1/schooldocs/verify/{verificationToken}` repose sur un jeton opaque aléatoire de 32 octets (Base64URL, zéro donnée personnelle), isolé par tenant JWT + RLS et soumis à `SchoolDocsAccessScopeGuard` afin d'empêcher toute fuite inter-élèves.
   - Les modèles inactifs (`active = false`) sont masqués en lecture et prévisualisation hors rôles `PROMOTER`, `DIRECTOR`, `CENTER_MANAGER` et `SECRETARY`.

## Conséquences
- Zéro dépendance externe supplémentaire, démarrage rapide et tests déterministes hors réseau.
- Défense en profondeur complète : JWT + `@PreAuthorize` + `SchoolDocsAccessScopeGuard` + `@TenantId` + RLS PostgreSQL forcée + triggers d'immutabilité + vérification SHA-256.
- Remplacement futur du stockage binaire PostgreSQL par un bucket S3 privé ou d'un moteur PDF avancé sans impact sur `domain`, `application` ni `api`.
