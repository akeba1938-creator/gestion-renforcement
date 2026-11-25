---
name: design-system-primitives
description: Guide pas-à-pas pour créer ou enrichir les composants réutilisables de frontend/src/app/shared/ avec Tailwind CSS 4, daisyUI 5 OKLCH et @angular/cdk (PageHeader, FilterBar, EmptyState, ErrorState avec requestId, Skeleton, ToastService, ConfirmDialog, Drawer, FormField FCFA/+221).
---
# Primitifs du Design System (`shared/`)

## Étapes
1. **Relire la constitution** : appliquer `.agents/rules/design-system-constitution.md` et `.agents/rules/frontend-quality-gate.md`, puis inventorier `frontend/src/app/shared/` pour éviter toute duplication.
2. **Contrat Angular 22** :
   - Composant 100 % standalone, `changeDetection: ChangeDetectionStrategy.OnPush`, Signals uniquement (`input()`, `input.required()`, `output()`, `model()`, `computed()`), `@if`/`@for (...; track ...)`, zéro `any`.
3. **Intégrer `@angular/cdk` pour les comportements critiques** :
   - `ConfirmDialog` & `Drawer` : utiliser `@angular/cdk/dialog` ou `@angular/cdk/overlay` avec **`FocusTrap`** (`cdkTrapFocus`), restauration du focus à la fermeture, clic backdrop et fermeture **`Escape`**.
   - `ToastService` : gestion par Signals + `LiveAnnouncer` (`@angular/cdk/a11y`) en `aria-live="polite"`.
4. **Respecter strictement les tokens & géométrie** :
   - Surfaces `bg-base-100` / `bg-base-200`, bordures `border-base-300`, `rounded-xl` (cartes/modales/drawers), `rounded-lg` (boutons/champs/badges), grille 4px/8px (`p-4`, `p-6`, `gap-4`, `gap-6`), transitions `150ms`–`250ms` `ease-out` avec `motion-reduce:transition-none`.
   - Zéro couleur hex/rgb en dur ; 1 seul `btn-primary` par modale/drawer ; anti-double clic (`disabled` + état chargement) sur les actions asynchrones.
   - Icônes `@lucide/angular` en import unitaire (`aria-hidden="true"` si décoratives).
5. **Catalogue des primitifs `frontend/src/app/shared/` à construire / maintenir** :
   - `PageHeader` : `h1` (`font-display`), fil d'Ariane optionnel, slot d'action principale unique (`btn-primary`).
   - `FilterBar` : recherche + filtres compacts sur une ligne desktop / empilés mobile + bouton « Réinitialiser les filtres ».
   - `EmptyState` : variantes « aucune donnée » (icône Lucide + CTA) et « vide après filtre » (bouton « Réinitialiser les filtres »).
   - `ErrorState` : bloc `role="alert"` affichant le message RFC 9457, le **`requestId` copiable** et le bouton **« Réessayer »**.
   - `Skeleton` : variantes `card`, `kpi`, `table-rows`, `form` (zéro Layout Shift).
   - `ToastService` & `ToastContainer` : toasts discrets succès/info/erreur avec fermeture manuelle et temporisée.
   - `ConfirmDialog` : confirmation explicite (`btn-error` sur action destructive) avec CDK `FocusTrap` + `Escape` + état chargement anti-double clic.
   - `Drawer` : tiroir latéral desktop (`rounded-xl`, `bg-base-100`, `border-base-300`) avec CDK `FocusTrap` + `Escape`.
   - `FormField` : label visible au-dessus du champ, aide, erreur en français sous le champ (sans effacer la saisie), aides dédiées pour montants entiers **FCFA** (`tabular-nums`) et téléphones **`+221`**.
   - `DataTable` : tri, pagination, `text-right tabular-nums` pour les colonnes numériques/FCFA, `truncate` + infobulle sur noms longs, bascule cartes sur mobile.
6. **Tests & validation** :
   - Écrire les tests **Vitest** pour chaque primitif (rendu nominal, états vide/erreur/chargement, clavier `Tab`/`Escape`, attributs ARIA, anti-double clic).
   - Lancer `cd frontend && npm test && npm run build` et vérifier les budgets (`4 kB` style composant).
