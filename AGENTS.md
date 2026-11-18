# AGENTS.md — SaaS de gestion scolaire (Sénégal)

Source unique des règles du projet. Lue par Antigravity et par Claude Code (via `CLAUDE.md`).
Les règles détaillées par zone sont dans `.agents/rules/`. Les spécifications sont dans `docs/`.

## Produit
SaaS multi-établissements pour écoles privées et centres de renforcement. Valeur principale : recouvrement des frais, notes et bulletins, migration Excel, communication avec les parents.
- Devise FCFA (XOF) sans décimales. Interface et messages d'erreur en français. Code en anglais.
- Stockage des dates en UTC, affichage Africa/Dakar. Téléphones au format E.164 (+221…).

## Pile
Java 21, Spring Boot 4.x (Jakarta EE 11), Maven, PostgreSQL + Flyway, Spring Data JPA, Spring Security 7, Angular 22 + TypeScript, Docker.
Versions exactes figées dans `pom.xml` et `package.json`. Ne change pas de version majeure sans demande.

## Commandes
- Backend : `cd backend && mvn verify`
- Frontend : `cd frontend && npm ci && npm test && npm run build`
- Local : `docker compose -f infra/docker-compose.yml up -d`
Une tâche n'est pas terminée tant que ces commandes ne passent pas. Indique le résultat.

## Règles non négociables
1. **Multi-tenant** : le tenant vient du jeton authentifié côté serveur, jamais du client. Chaque ressource REST a un test d'isolation entre établissements.
2. **Finance** : montants en entiers, écritures non modifiables (annulation par contre-écriture), idempotence des paiements, confirmation mobile money uniquement par notification serveur vérifiée.
3. **Base de données** : toute modification passe par une nouvelle migration Flyway. Une migration appliquée n'est jamais modifiée.
4. **Sécurité** : aucun secret dans le code ni les journaux ; entrées validées ; autorisations vérifiées côté serveur ; fichiers contrôlés par type réel.
5. **Architecture** : couches `api / application / domain / infrastructure` ; aucune annotation Spring dans `domain` ; pas d'accès direct aux tables d'un autre module ; entités JPA jamais exposées par l'API.
6. **Tests** : aucun code accepté sans tests. Pas de H2 pour les tests d'intégration (Testcontainers PostgreSQL).
7. **Périmètre** : ne développe rien de V2/V3 sans demande explicite (voir `docs/product/roadmap.md`).

## Méthode de travail
1. Lire la spécification du module (`docs/specs/`). Si elle manque ou est ambiguë, la compléter ou poser la question.
2. Proposer un plan court (entités, règles, API, tests) et attendre validation.
3. Écrire les tests d'acceptation, puis le code.
4. Un changement = une branche = une pull request courte.
5. Mettre à jour la spécification ; ajouter une ADR (`docs/adr/`) si une décision structurante est prise.
6. Pour la sécurité, la finance et le multi-tenant : expliquer précisément ce qui a changé, pour relecture humaine.

## Où lire quoi
| Tâche | Document |
|---|---|
| Comprendre le produit, les rôles | `docs/product/` |
| Architecture, multi-tenant, sécurité | `docs/architecture/` |
| Spécification d'un module | `docs/specs/<module>.md` |
| Conventions, tests, définition de « terminé » | `docs/engineering/` |
| Pourquoi un choix a été fait | `docs/adr/` |
| Sauvegarde, incident | `docs/runbooks/` |
| Avancement | `docs/status.md` |

## Interdits
Faire confiance au tenant/rôle envoyé par le navigateur ; modifier une écriture financière ; modifier une migration appliquée ; utiliser `float`/`double` pour de l'argent ; journaliser des données personnelles ; accepter un fichier selon son extension ; ajouter une dépendance non vérifiée ; utiliser de vraies données d'élèves en développement.
