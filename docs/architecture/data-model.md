# Modèle de données
Le schéma réel vit dans `backend/src/main/resources/db/migration/` (source de vérité). Ce document ne garde que les principes.

- Noms en snake_case, tables au pluriel, `tenant_id` partout, clés techniques distinctes des identifiants métier.
- Entités principales : voir `docs/product/glossary.md` et le cahier des charges (section 11.2).
- Matricule : `CODE_ECOLE-ANNEE-NUMERO`, unique par établissement.
- Écritures financières en ajout seul. Archivage logique des dossiers scolaires.
