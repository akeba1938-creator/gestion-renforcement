# Définition de « terminé »
- Spécification à jour et critères d'acceptation couverts par des tests.
- Tests d'isolation et de permissions pour toute ressource nouvelle.
- Migration ajoutée, aucune migration existante modifiée.
- `mvn verify` et `npm test` passent ; CI verte.
- Relecture humaine (obligatoire pour finance, sécurité, multi-tenant).
- `docs/status.md` mis à jour ; ADR si décision structurante.
