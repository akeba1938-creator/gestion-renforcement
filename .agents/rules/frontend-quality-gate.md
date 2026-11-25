---
trigger: glob
globs: "frontend/**"
description: "Quality Gate obligatoire pour toute modification dans frontend/ (Angular 22, OnPush, Signals, RxJS, RFC 9457, FCFA, sécurité, build)."
---
# Frontend Quality Gate Obligatoire (avant toute fin de tâche)

Miroir frontend de `.agents/rules/quality-gate.md`. À appliquer systématiquement à chaque ajout, modification ou suppression dans `frontend/` avant de conclure.

## 1. Angular 22 Moderne & Performance (Bloquant)
- **Standalone & Zoneless** : 100 % composants, pipes et directives standalone ; aucune dépendance `zone.js`.
- **Change Detection** : `changeDetection: ChangeDetectionStrategy.OnPush` obligatoire sur tous les composants.
- **Signals & Control Flow** : privilégier `signal()`, `computed()`, `input()`, `output()`, `viewChild()`/`viewChildren()`, ainsi que la syntaxe `@if` et `@for` avec **`track` obligatoire** (clé stable, jamais `$index` sur des listes mutables).
- **Typage strict** : **zéro `any`**, zéro cast non sécurisé (`as any`), contrôle de nullité explicite ; conformité `strict` et `strictTemplates`.
- **Cycle de vie RxJS** : **zéro `.subscribe()` nu** ; consommer les flux via `toSignal()`, `httpResource()` ou `takeUntilDestroyed(destroyRef)`.
- **Architecture** : respecter strictement `core/`, `shared/`, `layouts/`, `features/` avec lazy loading (`loadComponent` / `loadChildren`).

## 2. Sécurité Client & Multi-Tenant (Bloquant)
- **Session 100 % en mémoire** : zéro `localStorage` et zéro `sessionStorage` pour les jetons d'accès, rôles persistants ou données personnelles.
- **Multi-tenant serveur** : aucun en-tête, query param ou champ `tenantId` envoyé par le navigateur pour sélectionner un établissement ; le tenant vient uniquement du JWT vérifié côté serveur.
- **Rôles UI ≠ sécurité** : `roleGuard` et le filtrage du menu par rôle relèvent exclusivement de l'ergonomie UX ; les autorisations réelles sont toujours appliquées côté backend.
- **Scope Bearer** : `authInterceptor` attache `Authorization: Bearer` uniquement aux requêtes vers `API_BASE_URL` (`/api/v1`) après contrôle d'origine et de segment.
- **Données & prod** : données strictement fictives en dev/test ; aucun artefact dev (`demo-memory-token`, `theme-preview`, `dev-login`) dans le build de production.

## 3. Contrat API & Erreurs RFC 9457 (Bloquant)
- **Typage strict RFC 9457 (`ProblemDetail`)** : toute erreur HTTP backend est normalisée vers un modèle typé (`type`, `title`, `status`, `detail`, `instance`, `requestId`, erreurs de champs) sans jamais exposer d'erreur brute technique.
- **Traçabilité `requestId`** : extraire le `requestId` (corps `ProblemDetail` ou en-tête `X-Request-Id`) et l'afficher de façon copiable dans les états d'erreur pour le support.
- **Session expirée (`401`)** : gestion centralisée via tentative unique de refresh cookie `HttpOnly` (sans tempête de requêtes) ou nettoyage de session + redirection `/login`.

## 4. Conventions Métier Sénégal (Bloquant)
- **Monnaie FCFA (XOF)** : montants en entiers uniquement via `FcfaPipe` (zéro décimale, zéro `float`/`double`), avec **`tabular-nums`** et alignement à droite (`text-right`) dans les tableaux et totaux.
- **Temps** : UTC ISO-8601 en échange API, affichage systématique en fuseau `Africa/Dakar` via `DakarDatePipe`.
- **Téléphones & langue** : format international E.164 (`+221…`) ; interface et messages utilisateur en français naturel, code et identifiants en anglais.

## 5. Design System, 6 États & Accessibilité (Bloquant)
- Conformité stricte à `.agents/rules/design-system-constitution.md` : discipline visuelle (tokens daisyUI 5 OKLCH, zéro couleur en dur, 1 seul `btn-primary` par écran/modale), les 6 états obligatoires, formulaires préservant la saisie, WCAG 2.1 AA (contrastes `4.5:1` clair/sombre, clavier, `FocusTrap` CDK, `Escape`).

## 6. Validation Obligatoire Avant Livraison
- Tests unitaires **Vitest** couvrant tout nouveau composant, service, garde, intercepteur ou pipe (états nominal, vide, erreur RFC 9457, a11y).
- Exécuter et confirmer le succès de :
  ```bash
  cd frontend && npm test && npm run build && npm run check:prod-bundle
  ```
- Respecter les budgets de bundle (`initial <= 500 kB` en avertissement / `1 MB` max ; `anyComponentStyle <= 4 kB` / `8 kB` max).
- Inclure en fin de tâche : **Contrôle Frontend Quality Gate : OK / Points vérifiés**.
