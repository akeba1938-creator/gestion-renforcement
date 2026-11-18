---
name: new-module
description: Guide la création d'un nouveau module métier du backend (spécification, plan, migration, tests, implémentation). À utiliser quand on démarre ou étend un module comme student, finance ou grade.
---
# Nouveau module

## Étapes
1. Lire `docs/specs/<module>.md`. Si elle manque, la créer depuis `docs/specs/_template.md` et demander validation.
2. Proposer un plan : entités, règles métier, API, migrations, permissions, tests. Attendre validation.
3. Créer la migration Flyway (avec `tenant_id`, index, Row-Level Security).
4. Écrire d'abord les tests d'acceptation et d'isolation multi-tenant.
5. Implémenter dans l'ordre : `domain`, `application`, `infrastructure`, `api`.
6. Lancer `cd backend && mvn verify` et corriger.
7. Mettre à jour la spécification et `docs/status.md`. Ajouter une ADR si une décision structurante est prise.

## Rappels
Respecter `AGENTS.md` et les règles de `.agents/rules/`. Aucune annotation Spring dans `domain`.
