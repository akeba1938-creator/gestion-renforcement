---
name: frontend-ui-review
description: Checklist d'audit frontend avant commit ou PR : contraste WCAG 2.1 AA clair/sombre, navigation clavier (Tab/Escape), OnPush, zéro .subscribe() nu, tabular-nums, budgets de bundle 500 kB, zéro Layout Shift, 6 états UI et français naturel.
---
# Revue UI/UX, Accessibilité & Performance Frontend

## Étapes
1. **Périmètre** : lister les fichiers modifiés dans `frontend/` (`git diff --name-only`).
2. **Performance & Hygiène Angular 22** :
   - `ChangeDetectionStrategy.OnPush` présent sur chaque composant, 100 % standalone, zéro `zone.js`.
   - Zéro `any`, zéro `as any`, `@for` toujours muni d'un `track` stable.
   - **Zéro `.subscribe()` nu** : vérifier l'usage de `toSignal()`, `httpResource()` ou `takeUntilDestroyed()`.
   - Vérifier les **budgets de bundle** (`initial <= 500 kB`, `anyComponentStyle <= 4 kB`), le lazy loading des routes, les imports unitaires `@lucide/angular` et `npm run check:prod-bundle`.
   - Vérifier **zéro Layout Shift (CLS)** : présence de *skeleton loaders* dimensionnés lors des chargements.
3. **Design System & Discipline Visuelle** (`.agents/rules/design-system-constitution.md`) :
   - Surfaces `bg-base-200` / `bg-base-100` + `border-base-300`, zéro ombre lourde, zéro dégradé décoratif, zéro couleur hex/rgb en dur.
   - **1 seul `btn-primary`** par écran ou modale ; max 4 KPI par ligne sur desktop ; `rounded-xl` (cartes/modales/drawers) et `rounded-lg` (boutons/champs/badges).
   - Typographie `Plus Jakarta Sans` (titres) + `Inter` (corps), **`tabular-nums`** et `text-right` sur tous les montants FCFA (`FcfaPipe`) et colonnes numériques, `DakarDatePipe` (`Africa/Dakar`), `truncate` + infobulle sur noms longs.
   - Transitions `150ms`–`250ms` `ease-out` avec `prefers-reduced-motion`, et anti-double clic sur toutes les actions asynchrones.
4. **Les 6 États UI & Formulaires** :
   - Vérifier les 6 états : (1) Skeleton, (2) Vide + CTA, (3) Vide après filtre + « Réinitialiser les filtres », (4) Erreur RFC 9457 + « Réessayer » + `requestId` copiable, (5) Toast succès, (6) Indicateur réseau/hors-ligne PWA.
   - Vérifier les formulaires : labels visibles au-dessus, erreurs en français naturel sous le champ, **saisie préservée** en cas d'erreur serveur, confirmation explicite sur actions destructives.
5. **Accessibilité WCAG 2.1 AA (Clair & Sombre)** :
   - Contrastes ≥ `4.5:1` vérifiés en **clair et sombre** ; aucun statut porté par la couleur seule.
   - Navigation clavier complète (`Tab`, `Shift+Tab`, `Escape`, flèches sur onglets/menus), `focus-visible` global, *skip-to-content*, **`FocusTrap` Angular CDK** + restauration du focus sur modales/drawers.
   - Responsive mobile (`< 768px`) : bascule en cartes empilées, cibles tactiles ≥ `44px`, zéro débordement horizontal.
6. **Sécurité & Validation finale** :
   - Zéro `localStorage`/`sessionStorage` pour jetons ou PII ; zéro header tenant client ; données fictives uniquement ; textes en **français naturel** sans anglicismes ni messages techniques bruts.
   - Vérifier le passage de `cd frontend && npm test && npm run build`.
7. **Livrable** : restituer les constats par gravité (**Bloquant**, **Majeur**, **Mineur**) avec `fichier:ligne` et verdict (**Approuver** / **Modifications requises**).
