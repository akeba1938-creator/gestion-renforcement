---
name: frontend-design
description: Philosophie de craft visuel Stripe/Linear pour Angular 22 + Tailwind CSS 4 + daisyUI 5 OKLCH : éliminer le rendu template générique, surfaces translucides douces, encre neutre sur les KPI, bordures fines, micro-interactions maîtrisées et zéro défaut de finition.
---
# Frontend Design — Craft Visuel SaaS (Standard Stripe / Linear)

Skill de direction visuelle et de finition d'interface pour `frontend/`. Il complète `.agents/rules/design-system-constitution.md` et `.agents/rules/frontend-quality-gate.md` en éliminant le rendu « template IA / daisyUI générique » au profit d'une esthétique institutionnelle sobre, précise et immédiatement lisible en modes **clair et sombre**.

## 1. Philosophie : Zéro Rendu « Template Générique »
- **Sobriété intentionnelle** : la couleur porte uniquement l'information sémantique utile (action principale, tendance, statut, focus), jamais la décoration gratuite.
- **Clarté par contraste de surfaces** : hiérarchie calme `bg-base-200` (toile) → `bg-base-100` (cartes, tableaux, panneaux) → bordures ultra-fines `border border-base-300/80` + ombre discrète `shadow-xs`.
- **Interdits absolus** :
  - Aucune couleur `#hex`, `rgb()`, `hsl()` ou classe arbitraire (`bg-[#...]`, `text-[#...]`) dans les composants : 100 % tokens sémantiques Tailwind 4 / daisyUI 5 OKLCH.
  - Aucune ombre lourde (`shadow-lg`, `shadow-xl`) ni dégradé décoratif sur les cartes ou boutons.
  - Aucun bloc coloré criard qui rivalise avec les données métier.

## 2. Anti-Template daisyUI (Statuts & Badges *Soft / Tinted*)
- **Ne jamais utiliser** les classes pleines criardes (`badge-success`, `badge-warning`, `badge-error`, `badge-info`, `badge-neutral`) pour les statuts métier, compteurs d'alertes ou indicateurs de tendance.
- **Utiliser systématiquement** des surfaces translucides douces bordées :
  - Succès : `bg-success/10 text-success border border-success/20`
  - Avertissement : `bg-warning/10 text-warning border border-warning/20`
  - Erreur : `bg-error/10 text-error border border-error/20`
  - Information : `bg-info/10 text-info border border-info/20`
  - Neutre : `bg-base-200/80 text-base-content/75 border border-base-300/80`
- **Garde-fous de mise en page** :
  - Toujours appliquer `whitespace-nowrap` et `inline-flex items-center` sur les badges, étiquettes de classe et micro-tendances afin qu'aucun libellé (`En attente`, `Orange Money`, `CM2 (Primaire)`) ne soit rogné ni cassé sur deux lignes.
  - Ajouter un micro-point indicateur optionnel (`h-1.5 w-1.5 rounded-full bg-<tone>`) avec `aria-hidden="true"` pour renforcer la lisibilité sans jamais s'appuyer sur la couleur seule.

## 3. Hiérarchie Typographique & Encre Neutre
- **Encre neutre obligatoire sur les grands chiffres** : tous les montants FCFA, totaux cumulés et valeurs KPI principales utilisent **`text-base-content`** (jamais `text-success`, `text-warning`, `text-accent` ou `text-secondary` sur un gros chiffre).
- **Typographie structurée** :
  - Titres et chiffres KPI : `font-display tracking-tight tabular-nums` (`Plus Jakarta Sans`).
  - Corps, cellules et formulaires : `font-sans` (`Inter`).
  - Identifiants techniques (`matricule`, `receiptRef`, `requestId`, téléphones `+221`) : `font-mono tabular-nums`.
- **Niveaux d'encre maîtrisés** :
  - Donnée principale : `text-base-content` (`font-semibold` / `font-bold`).
  - Libellés secondaires et métadonnées : `text-base-content/60`.
  - En-têtes de tableau discrets : `text-xs font-semibold uppercase tracking-wider text-base-content/50`.
- **Cartes KPI (`StatCardComponent`)** :
  - Maximum **4 cartes par ligne** sur desktop.
  - Valeur principale en `text-base-content font-display tabular-nums whitespace-nowrap tracking-tight`.
  - Icône Lucide discrète placée en haut à droite dans un conteneur *soft* (`h-8 w-8 rounded-lg bg-<tone>/10 text-<tone> border border-<tone>/20`).
  - Micro-badge de tendance doux en pied de carte accompagné d'un contexte concis en `text-base-content/60`.

## 4. Surfaces, Bordures & Micro-interactions
- **Géométrie régulière** : grille 4px/8px (`p-4`, `p-6`, `gap-4`, `gap-6`), `rounded-xl` (cartes, panneaux, drawers, modales) et `rounded-lg` (boutons, champs, badges, filtres segmentés).
- **Bordures & élévation** : `border border-base-300/80 shadow-xs` sur les cartes et conteneurs principaux.
- **Transitions douces (`150ms–200ms`)** :
  - Toujours spécifier `transition-colors duration-150 ease-out motion-reduce:transition-none` (ou `duration-200` sur les barres de graphiques).
  - Survol de ligne de tableau : `hover:bg-base-200/50`.
  - Cartes ou éléments actionnables : changement subtil de bordure (`hover:border-base-300`) ou de fond (`hover:bg-base-200/70`), jamais de rebond ni de zoom exagéré.
- **Hiérarchie d'action** : strictement **1 seul `btn-primary`** par écran ou modale ; `btn-outline` / `btn-ghost` pour le reste ; anti-double clic (`[disabled]`) systématique.

## 5. Tableaux, Graphiques SVG & Zéro Défaut de Finition
- **Formatage FCFA irréprochable** :
  - Entiers uniquement via `FcfaPipe` (zéro décimale), alignés à droite (`text-right tabular-nums whitespace-nowrap`).
  - Séparateur de milliers en **espace insécable standard (`\u00A0`)** — jamais d'espace fine insécable (`\u202F`) qui colle les chiffres — afin que `20 150 000 FCFA` reste aéré et lisible.
- **Tableaux haute densité sans défilement horizontal parasite** :
  - Padding compact et équilibré (`px-3 py-3`) sur desktop, `whitespace-nowrap` sur les colonnes courtes (Classe, Date, Moyen, Statut, Montant, Action), `truncate` + `[title]` sur les noms longs.
  - Bascule mobile (`< 768px`) en cartes empilées propres avec cibles tactiles `≥ 44×44px` (`min-h-11`).
- **Graphiques SVG sur mesure (`CollectionChartComponent`, etc.)** :
  - Lignes de grille horizontales très fines (`stroke-base-300/60`) et axe de base propre.
  - Barres à coins arrondis en haut uniquement (base plate ancrée sur l'axe).
  - État de survol **et** focus clavier (`tabindex="0"`) sur chaque période, avec atténuation douce des autres barres et infobulle propre affichant les montants exacts en FCFA (Réalisé vs Objectif).
  - Équivalent tableau `sr-only` obligatoire pour les lecteurs d'écran.

## 6. Checklist d'Application Avant Livraison
1. **Angular 22** : 100 % standalone, zoneless, `ChangeDetectionStrategy.OnPush`, Signals (`input`, `computed`, `signal`), `@if`/`@for (track)`, zéro `any`, zéro `.subscribe()` nu.
2. **Thèmes & WCAG 2.1 AA** : contraste ≥ `4.5:1` vérifié sur les surfaces *soft* en modes clair et sombre, `focus-visible` visible, zéro couleur en dur.
3. **Les 6 États** : chargement (skeletons zéro CLS), vide initial, vide après filtre, erreur RFC 9457 (`requestId` copiable + Réessayer), succès (toast), hors-ligne PWA.
4. **Validation technique** : `cd frontend && npm test && npm run build && npm run check:prod-bundle` verts avec bundle initial `< 500 kB`.
