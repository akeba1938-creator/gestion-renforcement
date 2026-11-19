---
trigger: glob
globs: "frontend/**"
description: "Conventions Angular : structure, mobile-first, hors-ligne, charte."
---
# Frontend (Angular 22)
- Organisation par fonctionnalités : `core/`, `shared/`, `layouts/`, `features/`. Composants autonomes, signaux, sans Zone.js.
- UI : Tailwind CSS 4, daisyUI 5, Angular CDK (comportements) et Lucide (`@lucide/angular`). Ne pas utiliser Angular Material.
- Formulaires : Signal Forms ou Reactive Forms, validation côté client ET serveur. Le frontend n'est jamais une barrière de sécurité.
- Mobile-first, léger : images compressées, pagination, chargement progressif.
- PWA : appel et saisie des notes hors-ligne (service worker, IndexedDB), synchronisation différée, gestion des conflits.
- Pas de `localStorage` pour les jetons ni les données sensibles.
- Charte : #172554, #2563EB, #0D9488, fond #F8FAFC, accent #F59E0B. Polices Inter et Plus Jakarta Sans. Thèmes clair et sombre. Les couleurs sont définies par les thèmes daisyUI ; ne pas coder de couleurs en dur dans les composants.
- Textes en français, erreurs compréhensibles, confirmation des opérations sensibles.
- Tests : Vitest (unitaires), Playwright (parcours critiques en version mobile).
