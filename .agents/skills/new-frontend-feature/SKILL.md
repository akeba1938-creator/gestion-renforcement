---
name: new-frontend-feature
description: Workflow complet pour créer un écran métier SaaS dans frontend/src/app/features/ (route lazy-loaded, roleGuard, filtrage menu par rôle, KPIs, tableau desktop / cartes mobile, filtres URL, drawer création/édition, confirmation sur action sensible, 6 états UI, tests Vitest).
---
# Nouvelle fonctionnalité Frontend (`features/`)

## Étapes
1. **Spécification & périmètre** : lire `docs/specs/<module>.md` et les contrôleurs backend `/api/v1/...` correspondants (DTOs, pagination, filtres, permissions `@PreAuthorize`).
2. **Plan court** : valider les routes, rôles autorisés, sous-composants (`pages/`, `components/`, `data-access/`), interactions (drawers/modales) et tests avant de coder.
3. **Routing & visibilité par rôle** :
   - Créer le module dans `frontend/src/app/features/<module>/` et déclarer une route **lazy-loaded** (`loadComponent` / `loadChildren`) dans `app.routes.ts`.
   - Protéger la route avec `authGuard` + `roleGuard` (`data.roles` aligné sur le backend).
   - Filtrer les entrées du menu dans le layout (`AdminLayout`, `TeacherLayout`, `ParentLayout`) selon le rôle courant afin d'éviter tout lien menant à `/forbidden`.
4. **Données & filtres synchronisés dans l'URL** :
   - Brancher le service API typé (voir skill `api-contract-sync`) via Signals (`toSignal()`, `httpResource()` ou `takeUntilDestroyed()` ; zéro `.subscribe()` nu, zéro `any`).
   - Synchroniser recherche, filtres, tri et page active dans les **query params URL** (`ActivatedRoute` + `Router.navigate`) pour préserver l'état au rafraîchissement et au partage.
5. **Mise en page SaaS Desktop dense + Mobile-first** :
   - `PageHeader` avec **1 seul `btn-primary`** (ex. création).
   - Bandeau de **KPIs** (maximum **4 par ligne** sur desktop, `tabular-nums`, `FcfaPipe` pour les montants).
   - `FilterBar` compacte + **tableau haute densité sur desktop** (`text-left` texte, `text-right tabular-nums` montants FCFA/nombres, `DakarDatePipe`, `truncate` + infobulle sur noms longs) basculant automatiquement en **cartes empilées** sur mobile (`< 768px`, zones tactiles ≥ `44px`).
   - **Drawer latéral** (`@angular/cdk`, `FocusTrap`, `Escape`) pour la création, l'édition ou le détail ; **dialogue de confirmation** (`ConfirmDialog`, `btn-error`) avant toute opération sensible (archivage, contre-écriture financière, clôture de période) ; anti-double clic sur chaque soumission et préservation de la saisie en cas d'erreur serveur.
6. **Implémenter les 6 états UI obligatoires** (`.agents/rules/design-system-constitution.md`) :
   1. Chargement par *skeleton loaders*.
   2. Vide initial avec icône Lucide + CTA.
   3. Vide après filtre avec bouton « Réinitialiser les filtres ».
   4. Erreur RFC 9457 avec bouton « Réessayer » et `requestId` copiable.
   5. Succès par toast discret (`ToastService`).
   6. Indicateur discret d'état réseau / hors-ligne PWA.
7. **Tests & Quality Gate** :
   - Écrire les tests unitaires **Vitest** (données fictives uniquement) : rendu nominal, filtres URL, bascule des 6 états, validation formulaire, confirmation sensible, accessibilité clavier/ARIA.
   - Exécuter `cd frontend && npm test && npm run build && npm run check:prod-bundle` et appliquer `.agents/rules/frontend-quality-gate.md`.
