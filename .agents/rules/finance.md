---
trigger: model_decision
description: "À appliquer pour paiements, frais, échéanciers, reçus, caisse, relances ou abonnements."
---
# Finance
- Montants en `long` (FCFA), `bigint` en base. Jamais `float` ni `double`.
- Registre d'écritures non modifiable. Correction = écriture d'annulation avec motif. Aucun UPDATE/DELETE sur une écriture.
- Reçus séquentiels par établissement, sans trou. QR de vérification = jeton opaque.
- Remises, bourses, annulations : demande puis approbation par un rôle autorisé.
- Mobile money : référence obligatoire ; confirmation uniquement par notification serveur à signature vérifiée ; idempotence.
- Clôture de caisse journalière avec écart constaté.
- Quotas d'abonnement : le dépassement bloque l'ajout, ne supprime jamais de données.
