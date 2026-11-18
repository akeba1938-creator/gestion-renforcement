# Spécification — Socle : authentification et multi-tenant
## Objectif
Créer des établissements, authentifier les utilisateurs, appliquer rôles, permissions et isolation par tenant.
## Règles métier
- Un utilisateur appartient à un seul établissement, sauf administrateurs SaaS.
- Le tenant est déduit du jeton.
- Création d'un établissement : code unique, forfait, administrateur initial.
## Critères d'acceptation
- Étant donné deux établissements A et B, quand un utilisateur de A demande une ressource de B, alors réponse 404 ou 403.
- Étant donné un utilisateur sans la permission requise, quand il appelle un point d'entrée protégé, alors 403.
- Après 5 échecs de connexion, le compte est temporairement limité.
- Une migration vide démarre l'application avec le schéma, la RLS et les rôles de base.
## Questions ouvertes
- Politique exacte de mots de passe, durée des jetons, canal du code multifacteur.
