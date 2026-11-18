# Multi-tenant
- Un établissement = un tenant. `tenant_id` sur toute table métier.
- Le tenant vient du jeton authentifié côté serveur.
- Défense en profondeur : Hibernate `@TenantId` + Row-Level Security PostgreSQL alimentée par `set_config` à chaque transaction.
- Tâches de fond : tenant explicite, restauré avant d'agir.
- Fichiers : chemin par tenant, stockage privé, URL signées courtes.
- Super-admin : accès aux données personnelles seulement avec motif, journalisé.
- Tests : isolation automatique pour chaque ressource, exécutée en CI.
