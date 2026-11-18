# ADR 0002 — Multi-tenant par colonne discriminante et Row-Level Security
- Statut : accepté
## Décision
Une base partagée, `tenant_id` sur chaque table, filtrage Hibernate `@TenantId` et RLS PostgreSQL.
## Conséquences
Exploitation simple et peu coûteuse. Exige des tests d'isolation systématiques. Évolution possible vers un schéma par tenant pour de gros clients.
## Alternatives écartées
Base par tenant (coût d'exploitation), schéma par tenant (complexité des migrations).
