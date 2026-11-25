---
trigger: glob
globs: "frontend/**"
description: "Constitution du Design System SaaS : direction artistique Linear/Stripe, tokens OKLCH, typographie, géométrie, formulaires, 6 états UI et WCAG 2.1 AA."
---
# Design System Constitution — École SaaS

Référence visuelle, ergonomique et d'accessibilité obligatoire pour toute interface dans `frontend/`.

## 1. Direction Artistique & Discipline Visuelle
- **Positionnement** : SaaS institutionnel moderne (standard **Linear / Stripe**), sobre, lisible et orienté productivité quotidienne.
- **Hiérarchie des surfaces** :
  - Fond d'application : `bg-base-200`.
  - Cartes, panneaux, tableaux, barres de navigation : `bg-base-100`.
  - Séparations et contours : `border border-base-300` (`1px solid`).
  - **Interdit** : pas d'ombres lourdes (`shadow-lg`/`shadow-xl` décoratives) ni de dégradés décoratifs ; privilégier la clarté par contraste de surfaces et bordures fines.
- **Zéro couleur en dur** : aucune couleur `#hex`, `rgb()` ou classe arbitraire (`bg-[#...]`, `text-[#...]`) dans les composants — utiliser uniquement les tokens sémantiques Tailwind CSS 4 / daisyUI 5 OKLCH.
- **Hiérarchie d'action** : **1 seul `btn-primary`** par écran ou modale. Utiliser `btn-ghost` ou `btn-outline` pour les actions secondaires, et `btn-error` avec **confirmation obligatoire** pour toute action destructive ou sensible.
- **Densité KPI** : maximum **4 cartes KPI par ligne** sur desktop.
- **Textes longs** : appliquer `truncate` avec infobulle (`title` ou tooltip accessible) sur les noms d'établissements, d'élèves, de classes ou libellés longs afin de préserver la grille.

## 2. Palette & Tokens OKLCH (`src/styles.css`)
- **Couleurs de marque** définies exclusivement en OKLCH dans les thèmes daisyUI 5 (`light` et `dark`) :
  - **Marine profond** : `#172554` (`neutral` / `base-content` en clair).
  - **Bleu primaire** : `#2563EB` (`primary`).
  - **Sarcelle secondaire** : `#0D9488` (`secondary`).
  - **Fond clair** : `#F8FAFC` (`base-100` en clair).
  - **Accent ambre** : `#F59E0B` (`accent`).
- **Statuts sémantiques** : `success`, `warning`, `error`, `info` calibrés pour garantir un contraste **WCAG 2.1 AA ≥ 4.5:1** en thèmes **clair et sombre**. Ne jamais transmettre un statut par la couleur seule.

## 3. Typographie & Données Chiffrées
- **Titres (`font-display`)** : **`Plus Jakarta Sans`** (graisses `600`–`700`, `tracking-tight`) pour `h1`, `h2`, `h3`, titres de pages et valeurs KPI.
- **Corps & UI (`font-sans`)** : **`Inter`** (graisses `400`–`600`) pour les paragraphes, tableaux, formulaires, badges et menus.
- **Chiffres tabulaires (`tabular-nums`)** : obligatoire sur tous les montants FCFA, moyennes, notes, effectifs, taux et dates en liste/tableau (`font-mono` pour matricules, codes et `requestId`).
- **Alignement** : texte aligné à gauche (`text-left`) ; montants FCFA et colonnes numériques alignés à droite (`text-right`).

## 4. Géométrie & Micro-interactions
- **Grille 4px / 8px** : utiliser exclusivement l'échelle régulière (`p-4`, `p-6`, `gap-4`, `gap-6`, `gap-2`).
- **Rayons** :
  - `rounded-xl` pour cartes, modales et drawers.
  - `rounded-lg` pour boutons, champs de saisie et badges.
- **Transitions** : durées courtes `150ms`–`250ms` en `ease-out`, respectant impérativement `prefers-reduced-motion` (`motion-reduce:transition-none`).
- **Anti-double clic obligatoire** : toute action asynchrone (soumission formulaire, paiement, validation, suppression) désactive le déclencheur (`[disabled]="submitting()"`) et affiche un état de chargement jusqu'à résolution.

## 5. Formulaires
- **Labels persistants** : `<label for="...">` toujours visible **au-dessus** de chaque champ (jamais remplacé par un simple `placeholder`).
- **Gestion des erreurs** : messages en français naturel affichés directement **sous le champ** concerné (`aria-invalid` + `aria-describedby`), **sans jamais effacer la saisie utilisateur** en cas d'erreur de validation ou d'erreur serveur RFC 9457.
- **Formats métier** : montants FCFA en entiers positifs sans décimales, téléphones au format E.164 `+221…`, dates affichées en `Africa/Dakar`.

## 6. Les 6 États Obligatoires sur Chaque Écran
Chaque vue connectée à des données doit implémenter explicitement ces 6 états :
1. **Chargement** : *skeleton loaders* reproduisant la géométrie réelle (cartes KPI, lignes de tableau, fiches) pour éviter tout Layout Shift (CLS).
2. **Vide (aucune donnée)** : icône Lucide sobre, explication claire en français et bouton **CTA** d'action principale.
3. **Vide après filtre** : message dédié distinguant l'absence de correspondance avec les critères actifs + bouton **« Réinitialiser les filtres »**.
4. **Erreur RFC 9457** : bloc `role="alert"` affichant le message métier (`detail`/`title`), le **`requestId` copiable** et un bouton **« Réessayer »**.
5. **Succès** : notification **toast** discrète et accessible (`role="status"`, `aria-live="polite"`) après toute mutation réussie.
6. **Réseau / Hors-ligne PWA** : indicateur discret d'état réseau hors-ligne (et de synchronisation en attente sur les écrans compatibles PWA comme l'appel et la saisie de notes).

## 7. Responsive & Accessibilité (WCAG 2.1 AA)
- **Desktop (Admin / Comptable)** : tableaux haute densité avec en-têtes lisibles et **drawers latéraux** pour la consultation, la création ou l'édition rapide sans perdre le contexte de liste.
- **Mobile (Enseignant / Parent, `< 768px`)** : bascule des tableaux larges en **cartes empilées** lisibles sans débordement horizontal, avec zones tactiles **≥ 44×44px**.
- **Accessibilité obligatoire** :
  - Anneau `focus-visible` visible globalement.
  - Lien d'évitement (*Aller au contenu principal* / *skip-to-content*).
  - **`FocusTrap` Angular CDK** (`CdkTrapFocus` / `@angular/cdk/dialog` / `overlay`), restauration du focus à la fermeture et fermeture par **`Escape`** sur toute modale et tout drawer.
  - Contrastes WCAG 2.1 AA (`≥ 4.5:1` texte courant, `≥ 3:1` éléments graphiques/bordures) validés en modes **clair et sombre**.
