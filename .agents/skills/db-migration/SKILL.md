---
name: db-migration
description: Ajoute une migration de base de données Flyway pour PostgreSQL de façon sûre. À utiliser pour tout changement de schéma, de table, d'index ou de contrainte.
---
# Migration de base de données

## Étapes
1. Identifier le dernier numéro dans `backend/src/main/resources/db/migration/`.
2. Créer `V<n+1>__description.sql`. Ne jamais modifier une migration existante.
3. Respecter `.agents/rules/database.md` : `tenant_id`, index, Row-Level Security, expand and contract.
4. Lancer les tests avec Testcontainers PostgreSQL.
5. Vérifier la compatibilité avec les données existantes.
