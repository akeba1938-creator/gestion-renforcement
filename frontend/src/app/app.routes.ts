import { Routes } from '@angular/router';
import { devRoutes } from './app.dev-routes';
import { ROUTE_ROLES, SHELL_ROLES } from './core/config/role-access.config';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  ...devRoutes,
  {
    path: 'login',
    title: 'Connexion — École SaaS',
    loadComponent: () => import('./features/auth/login-page').then((m) => m.LoginPageComponent),
  },
  {
    path: 'forbidden',
    title: 'Accès refusé — École SaaS',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auth/forbidden-page').then((m) => m.ForbiddenPageComponent),
  },
  {
    path: '',
    loadComponent: () =>
      import('./layouts/admin/admin-layout').then((m) => m.AdminLayoutComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: SHELL_ROLES },
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        title: 'Tableau de bord — École SaaS',
        canActivate: [roleGuard],
        data: { roles: ROUTE_ROLES.dashboard },
        loadComponent: () =>
          import('./features/dashboard/dashboard-page').then((m) => m.DashboardPageComponent),
      },
      {
        path: 'students',
        title: 'Élèves — École SaaS',
        canActivate: [roleGuard],
        data: { roles: ROUTE_ROLES.students },
        loadComponent: () =>
          import('./features/students/students-page').then((m) => m.StudentsPageComponent),
      },
      {
        path: 'grades',
        title: 'Notes et bulletins — École SaaS',
        canActivate: [roleGuard],
        // Le rôle TEACHER est volontairement exclu de l'espace admin : il sera
        // rattaché à son propre espace lorsque TeacherLayoutComponent sera branché.
        data: { roles: ROUTE_ROLES.grades },
        loadComponent: () =>
          import('./features/grades/grades-page').then((m) => m.GradesPageComponent),
      },
      {
        path: 'absences',
        title: 'Absences & Présences — École SaaS',
        canActivate: [roleGuard],
        data: { roles: ROUTE_ROLES.attendance },
        loadComponent: () =>
          import('./features/absences/absences-page').then((m) => m.AbsencesPageComponent),
      },
      {
        path: 'attendance',
        redirectTo: 'absences',
        pathMatch: 'full',
      },
      {
        path: 'finances',
        title: 'Finances & Caisse — École SaaS',
        canActivate: [roleGuard],
        data: { roles: ROUTE_ROLES.finance },
        loadComponent: () =>
          import('./features/finances/finances-page').then((m) => m.FinancesPageComponent),
      },
      {
        path: 'finance',
        redirectTo: 'finances',
        pathMatch: 'full',
      },
      {
        path: 'import',
        title: 'Import Excel — École SaaS',
        canActivate: [roleGuard],
        data: { roles: ROUTE_ROLES.import },
        loadComponent: () =>
          import('./features/import/import-page').then((m) => m.ImportPageComponent),
      },
      {
        path: 'settings',
        title: 'Paramètres — École SaaS',
        canActivate: [roleGuard],
        data: { roles: ROUTE_ROLES.settings },
        loadComponent: () =>
          import('./features/settings/settings-page').then((m) => m.SettingsPageComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
