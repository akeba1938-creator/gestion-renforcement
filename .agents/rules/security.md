---
trigger: model_decision
description: "À appliquer pour toute modification touchant authentification, permissions, tenant, fichiers, secrets ou liens d'accès parent."
---
# Sécurité
- Tenant déduit du jeton côté serveur ; Hibernate `@TenantId` + RLS PostgreSQL (`set_config` par transaction).
- Tâches de fond : tenant passé explicitement et restauré avant d'agir.
- JWT d'accès courts, renouvellement à rotation en cookie HttpOnly protégé du CSRF. Autorisations par `@PreAuthorize` dans les services.
- Mots de passe : Argon2id. Authentification multifacteur pour directeur, promoteur, administrateurs. Limitation des tentatives.
- Accès parent : jeton aléatoire long, court, révocable.
- Fichiers : type réel vérifié, taille limitée, renommage sûr, stockage privé, URL signée courte.
- QR Code : jeton opaque uniquement. Pas de donnée personnelle dans les journaux.
- Chaque nouvelle ressource REST : test d'isolation entre tenants + test par rôle autorisé et refusé.
