# Spécification — Module `finance` (frais, facturation, échéanciers, encaissements, reçus et registre comptable immuable)

## Objectif
Gérer le cycle financier complet d'un établissement scolaire ou centre de renforcement isolé par tenant : catalogue des frais en FCFA entiers (`FeeItem`), facturation élève et échéanciers (`StudentInvoice`, `StudentInvoiceLine`, `PaymentSchedule`), remises et bourses encadrées par workflow d'approbation (`FeeAdjustment`), encaissements immuables avec idempotence (`Payment`, `PaymentAllocation`), reçus séquentiels sans trou avec jeton QR opaque (`Receipt`), contre-passations tracées (`PaymentReversalRequest`, `PaymentReversal`), registre comptable immuable en ajout seul (`LedgerEntry`) et clôtures journalières de caisse (`CashClosing`).

## Acteurs et permissions
- **Promoteur (`PROMOTER`)** :
  - Lecture sur tout `/api/v1/finance/**`.
  - Approbation / rejet des remises et bourses, approbation / rejet des contre-passations d'encaissements, annulation de factures non payées, validation des clôtures de caisse.
  - Aucune création de frais ni encaissement direct (`403`).
- **Directeur (`DIRECTOR`)** :
  - Lecture sur tout `/api/v1/finance/**`.
  - Gestion complète du catalogue des frais (`/api/v1/finance/fee-items/**`), facturation brouillon/émission, demande et approbation/rejet de remises/bourses, demande et approbation/rejet de contre-passations, annulation de factures, validation des clôtures de caisse.
  - **Encaissement (`POST /api/v1/finance/payments`) et soumission de clôture de caisse (`POST /api/v1/finance/cash-closings`) refusés (`403`)** afin de préserver la séparation des tâches avec le caissier.
- **Responsable de centre (`CENTER_MANAGER`)** :
  - Lecture et opérations complètes sur le module finance dans le périmètre de son centre (catalogue, facturation, encaissement, remises/bourses, contre-passations, clôture de caisse).
- **Caissier (`CASHIER`)** :
  - Lecture sur tout `/api/v1/finance/**`.
  - Encaissement (`POST /api/v1/finance/payments`), facturation brouillon/émission, soumission de clôture de caisse, soumission de demandes de remises/bourses et de demandes de contre-passation.
  - Aucune modification du catalogue des frais, aucune approbation de remise/bourse/contre-passation, aucune annulation de facture ni validation de clôture (`403`).
- **Secrétariat (`SECRETARY`)** :
  - Lecture sur `/api/v1/finance/**` (hors clôtures de caisse → `403`).
  - Facturation brouillon/émission, soumission de demandes de remises/bourses et de demandes de contre-passation.
  - Aucun encaissement, aucune approbation ni modification du catalogue (`403`).
- **Administrateur SaaS (`ADMIN_SAAS`), Censeur (`CENSOR`), Enseignant (`TEACHER`), Intervenant (`TUTOR`), Parent (`PARENT`), Élève (`STUDENT`)** :
  - Accès refusé (`403`) sur `/api/v1/finance/**`.
- **Autres modules applicatifs** :
  - `finance` consomme `SchoolDirectory`, `SubscriptionAccessPort`, `AcademicStructurePort`, `StudentDirectoryPort` et `FamilyDirectoryPort` de manière unidirectionnelle (aucun cycle Spring Modulith).
  - `finance` expose `FinanceAccountPort` en lecture seule (`StudentFinancialSummary`, `FamilyFinancialSummary`).

## Règles métier
1. **Devise et montants** :
   - Tous les montants sont stockés, calculés et échangés en entiers FCFA (`long` / `BIGINT`, devise `XOF`), sans `float` ni `double`, avec contrôle de débordement arithmétique (`Math.addExact`, `Math.multiplyExact`).
2. **Catalogue des frais (`fee_items`)** :
   - Code normalisé en majuscules unique par `(tenant_id, academic_year_id, code)` ; année scolaire et niveau optionnel validés via `AcademicStructurePort`.
   - Aucune création/modification sur une année scolaire `CLOSED` ou `ARCHIVED`. Archivage logique par `active = false`.
3. **Facturation et échéanciers (`student_invoices`, `student_invoice_lines`, `payment_schedules`)** :
   - Élève validé actif via `StudentDirectoryPort`, inscription annuelle reliée si existante, responsable payeur/famille résolu automatiquement via `FamilyDirectoryPort.findFinancialResponsibleByStudentId` ou validé s'il est fourni.
   - Numéro séquentiel isolé par établissement et année (`FAC-<CODE_ECOLE>-<ANNEE>-NNNNNN`).
   - Génération d'échéancier en *N* parts entières exactes (le reste euclidien est porté sur la première échéance pour garantir `sum(schedules) == netAmountXof`).
   - Machine à états : `DRAFT → ISSUED → PARTIALLY_PAID → PAID`, et `DRAFT | ISSUED (paidAmountXof == 0) → CANCELLED`.
   - L'émission (`ISSUED`) crée une écriture comptable immuable `CHARGE` (`DEBIT`). L'annulation d'une facture émise sans paiement crée une contre-écriture `CHARGE_REVERSAL` (`CREDIT`).
4. **Remises et bourses encadrées (`fee_adjustments`)** :
   - Types `DISCOUNT` et `SCHOLARSHIP`, calcul en montant fixe FCFA (`FIXED_AMOUNT`) ou pourcentage entier `1..100` (`PERCENTAGE`, division entière sans flottant).
   - Workflow obligatoire `PENDING_APPROVAL → APPROVED | REJECTED` avec motif obligatoire.
   - À l'approbation : vérification `resolvedAmountXof <= balanceXof`, écriture comptable `DISCOUNT_CREDIT` ou `SCHOLARSHIP_CREDIT` (`CREDIT`), mise à jour de la facture et réduction des échéances non soldées de la dernière vers la première.
5. **Encaissements immuables, idempotence et reçus (`payments`, `payment_allocations`, `receipts`)** :
   - Clé d'idempotence obligatoire (`Idempotency-Key` en en-tête ou champ dédié), unique par `(tenant_id, idempotency_key)` : rejeu identique → `200 OK` (`X-Idempotent-Replay: true`) sans doublon ; rejeu avec payload différent → `409 Conflict`.
   - Modes : `CASH`, `CHECK`, `BANK_TRANSFER`, `MOBILE_MONEY_WAVE`, `MOBILE_MONEY_OM`, `MOBILE_MONEY_FREE`, `OTHER`. Référence externe obligatoire et unique par `(tenant_id, payment_method, external_reference)` pour chèque, virement et mobile money.
   - Source de confirmation `MANUAL_DESK` en guichet MVP (`VERIFIED_WEBHOOK` réservé aux notifications serveur signées MVP+, jamais injectable par le client).
   - Imputation totale ou partielle (`0 < amountXof <= invoice.balanceXof`) sur une échéance ciblée ou en FIFO, tracée dans `payment_allocations`.
   - Reçu généré atomiquement avec numérotation séquentielle sans trou (`REC-<CODE_ECOLE>-<ANNEE>-NNNNNN`) et `verification_token` opaque (32 octets aléatoires Base64URL, aucune donnée personnelle) vérifiable via `GET /api/v1/finance/receipts/verify/{token}`.
6. **Contre-passation tracée et registre comptable immuable (`payment_reversal_requests`, `payment_reversals`, `ledger_entries`)** :
   - **Aucune modification ni suppression (`UPDATE` / `DELETE`)** sur `payments`, `payment_allocations`, `receipts`, `payment_reversals` et `ledger_entries`, garanti à la fois côté application, par privilèges PostgreSQL (`SELECT, INSERT` uniquement pour `ecole_app`) et par triggers SQL `BEFORE UPDATE OR DELETE`.
   - Toute annulation d'encaissement suit `PENDING_APPROVAL → APPROVED | REJECTED`. L'approbation insère un enregistrement immuable `payment_reversals`, ajoute une écriture `PAYMENT_REVERSAL` (`DEBIT`, liée à l'écriture `PAYMENT_CREDIT` d'origine via `reversal_of_entry_id`) et recalcule la facture et les échéances.
7. **Clôture de caisse journalière (`cash_closings`)** :
   - Calcule l'attendu espèces du jour (fuseau de l'établissement, défaut `Africa/Dakar`) : `expectedCashXof = cashInXof - cashOutReversalsXof`, puis `discrepancyXof = countedCashXof - expectedCashXof`.
   - Justification obligatoire si `discrepancyXof != 0`. Workflow `SUBMITTED → VALIDATED`.
8. **Contrôle abonnement et isolation multi-tenant** :
   - Garde `SubscriptionAccessPort` : lecture seule autorisée en `EXPIRED`, bloquée en `SUSPENDED` / `CANCELLED` / `MISSING` ; écriture exige un abonnement opérationnel.
   - Défense en profondeur : tenant issu exclusivement du JWT (`TenantContext`) + Hibernate `@TenantId` + RLS PostgreSQL activée et forcée (`fail-closed`) + clés étrangères composites `(tenant_id, <fk_id>)`.

## Modèle de données (migration `V7__finance_module.sql`)
- `finance_sequences`
- `fee_items`
- `student_invoices`, `student_invoice_lines`, `payment_schedules`
- `fee_adjustments`
- `payments`, `payment_allocations`, `receipts` *(strictement immuables, ajout seul)*
- `payment_reversal_requests`, `payment_reversals` *(ajout seul)*
- `ledger_entries` *(strictement immuable, ajout seul)*
- `cash_closings`

## Critères d'acceptation
- Les écritures comptables et encaissements validés ne peuvent être ni modifiés ni supprimés, y compris par SQL direct (`UPDATE` / `DELETE` rejetés par PostgreSQL).
- Un rejeu d'encaissement avec la même clé d'idempotence retourne le paiement et le reçu existants sans créer de doublon ; une réutilisation avec un payload différent retourne `409`.
- `DIRECTOR` et `PROMOTER` ne peuvent pas enregistrer d'encaissement (`403`) ; `CASHIER` et `SECRETARY` ne peuvent pas approuver une remise, une bourse ou une contre-passation (`403`).
- Le solde calculé par le registre comptable (`SUM(DEBIT) - SUM(CREDIT)`) est strictement égal au solde des factures actives après émission, remise/bourse, paiement et contre-passation.
- Deux établissements A et B sont strictement isolés tant au niveau API REST (y compris avec `X-Tenant-Id` / `?tenantId` forgés → `404`) qu'au niveau PostgreSQL RLS sous `ecole_app` (0 ligne sans `TenantContext`).

## Hors périmètre
- Paiement en ligne via webhook agrégateur mobile money (MVP+).
- Relances automatisées SMS/WhatsApp planifiées (module `communication`), génération PDF des reçus/factures (module `schooldocs`) et portail parent `/api/v1/me/**` (V2).
