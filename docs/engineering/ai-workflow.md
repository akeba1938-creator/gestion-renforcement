# Travailler avec les agents (Claude Code et Antigravity)

## Qui lit quoi
| Fichier | Antigravity | Claude Code |
|---|---|---|
| `AGENTS.md` | Oui, toujours | Via `CLAUDE.md` (`@AGENTS.md`) |
| `CLAUDE.md` | Non | Oui |
| `.agents/rules/*.md` | Oui (frontmatter obligatoire) | Via `backend/CLAUDE.md` et `frontend/CLAUDE.md` |
| `.agents/skills/<nom>/SKILL.md` | Oui (choisi selon la description, ou mentionné par son nom) | Via `.claude/commands/` qui pointent vers les mêmes fichiers |

Antigravity ne charge pas les règles des sous-dossiers situés sous le dossier de travail : c'est pourquoi les règles spécifiques sont dans `.agents/rules/` avec des déclencheurs `glob` ou `model_decision`.
Les workflows (`.agents/workflows/`) sont obsolètes dans Antigravity : les procédures sont des skills. Pour migrer d'anciens workflows, utiliser la commande `/migrate-workflows`.

## Boucle de travail
1. Spécifier (`docs/specs/`). 2. Plan validé. 3. Tests puis code. 4. Vérifier (build, tests, revue de sécurité). 5. PR courte. 6. Mettre à jour la spec, le statut, l'ADR.

## Bonnes pratiques
- Un module à la fois, une session par tâche. Ne pas demander « toute l'application ».
- Relire soi-même tout code de finance, de sécurité et de multi-tenant.
- Ne jamais coller de vraies données d'élèves ni de secrets dans un prompt.
- Garder `AGENTS.md` court ; mettre le détail dans les règles et les spécifications.
