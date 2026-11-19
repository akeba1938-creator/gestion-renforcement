import { Routes } from '@angular/router';

/**
 * Development-only routes.
 * Replaced by `app.dev-routes.prod.ts` (empty array) during production builds
 * via `fileReplacements` in `angular.json`, so `/theme-preview` and `/dev-login`
 * are excluded from the production bundle.
 */
export const devRoutes: Routes = [
  {
    path: 'theme-preview',
    title: 'Prévisualisation du thème — École SaaS',
    loadComponent: () =>
      import('./features/theme-preview/theme-preview').then((m) => m.ThemePreviewComponent),
  },
  {
    path: 'dev-login',
    title: 'Connexion démo — École SaaS',
    loadComponent: () =>
      import('./features/auth/dev-login-page').then((m) => m.DevLoginPageComponent),
  },
];
