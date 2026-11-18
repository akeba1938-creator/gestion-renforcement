---
trigger: glob
globs: "**/db/migration/**, **/*.sql"
description: "Règles PostgreSQL et migrations Flyway."
---
# Base de données
- Nouvelle migration `V<n>__description.sql` pour tout changement. Ne jamais modifier une migration appliquée.
- Tables au pluriel en snake_case. Colonne `tenant_id` non nulle, clé étrangère et index sur toute table métier.
- Unicités incluant `tenant_id`. Clés techniques distinctes des identifiants métier (matricule, numéro de reçu).
- Row-Level Security activée sur les tables métier.
- Changements sans interruption : ajouter, migrer, puis supprimer (expand and contract). `CREATE INDEX CONCURRENTLY` sur tables existantes.
- Archivage logique des dossiers scolaires. Jamais de suppression d'historique d'année passée.
