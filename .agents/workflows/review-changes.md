# Workflow : Revue Quality Gate des changements courants

Déclencher ce workflow avant un commit ou une fusion pour auditer uniquement les fichiers ajoutés, modifiés ou supprimés sur la branche active.

## Étapes
1. Identifier les fichiers modifiés, ajoutés ou supprimés par rapport à `main` (sans rien modifier).
2. Appliquer strictement chaque point de `.agents/rules/quality-gate.md` uniquement sur le périmètre modifié :
   - Sécurité, isolation multi-tenant, RLS, privilèges `ecole_app`, RBAC/périmètre, secrets.
   - Concurrence (`@Lock`, `@Version`, idempotence), `@Valid` / types boxés, `Clock` / FCFA `long`, garde d'abonnement.
   - Performance (zéro N+1, `saveAll`, transactions courtes, agrégations SQL).
   - Architecture (domaine Java pur, injection constructeur, zéro cycle Spring Modulith, `ProblemDetail`).
3. Classer les constats par gravité : **Bloquant**, **Majeur**, **Mineur**, ou **Conforme**.
4. Pour chaque constat Bloquant ou Majeur, indiquer le fichier exact, la ligne concernée et la correction précise à appliquer.
5. Ne modifier aucun fichier et ne faire aucun `git commit` sans accord explicite de l'utilisateur.
