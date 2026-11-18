# ADR 0003 — Registre financier non modifiable
- Statut : accepté
## Décision
Les paiements produisent des écritures en ajout seul ; une erreur se corrige par une écriture d'annulation avec motif ; montants en entiers FCFA.
## Conséquences
Traçabilité et audit solides ; les rapports lisent les écritures ; aucune modification directe en base.
