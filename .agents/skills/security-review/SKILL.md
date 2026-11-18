---
name: security-review
description: Revue de sécurité d'un changement de code (tenant, permissions, validation, secrets, finance, fichiers). À utiliser avant de fusionner du code touchant la sécurité, la finance ou le multi-tenant.
---
# Revue de sécurité

## Étapes
1. Lister les fichiers modifiés (`git diff main...`).
2. Vérifier : tenant jamais pris du client, `@PreAuthorize` présent, entrées validées, aucune requête SQL native sans filtre tenant.
3. Vérifier : aucun secret, aucune donnée personnelle dans les journaux, fichiers contrôlés par type réel.
4. Vérifier la finance : montants entiers, écritures non modifiables, idempotence, confirmation mobile money par notification serveur.
5. Vérifier que chaque ressource REST a un test d'isolation et des tests par rôle.
6. Produire un rapport : problèmes critiques, importants, mineurs, avec fichier et ligne.
