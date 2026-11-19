import { Routes } from '@angular/router';
import { devRoutes } from './app.dev-routes';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { UserRole } from './core/models/user.model';
import { AdminLayoutComponent } from './layouts/admin/admin-layout';

const ADMIN_ROLES: readonly UserRole[] = [
  'SAAS_ADMIN',
  'PROMOTER',
  'DIRECTOR',
  'SECRETARY',
  'CASHIER',
  'CENSOR',
];

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
    component: AdminLayoutComponent,
    canActivate: [authGuard, roleGuard],
    data: { roles: ADMIN_ROLES },
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        title: 'Tableau de bord — École SaaS',
        loadComponent: () =>
          import('./features/dashboard/dashboard-page').then((m) => m.DashboardPageComponent),
      },
      {
        path: 'students',
        title: 'Élèves — École SaaS',
        canActivate: [roleGuard],
        data: { roles: ['PROMOTER', 'DIRECTOR', 'SECRETARY', 'CASHIER', 'CENSOR'] satisfies UserRole[] },
        loadComponent: () =>
          import('./features/students/students-page').then((m) => m.StudentsPageComponent),
      },
      {
        path: 'grades',
        title: 'Notes et bulletins — École SaaS',
        canActivate: [roleGuard],
        // Le rôle TEACHER est volontairement exclu de l'espace admin : il sera
        // rattaché à son propre espace lorsque TeacherLayoutComponent sera branché.
        data: { roles: ['PROMOTER', 'DIRECTOR', 'CENSOR'] satisfies UserRole[] },
        loadComponent: () =>
          import('./features/grades/grades-page').then((m) => m.GradesPageComponent),
      },
      {
        path: 'attendance',
        title: 'Absences — École SaaS',
        canActivate: [roleGuard],
        // Le rôle TEACHER sera rattaché à TeacherLayoutComponent lors de son branchement.
        data: { roles: ['PROMOTER', 'DIRECTOR', 'SECRETARY', 'CENSOR'] satisfies UserRole[] },
        loadComponent: () =>
          import('./features/attendance/attendance-page').then((m) => m.AttendancePageComponent),
      },
      {
        path: 'finance',
        title: 'Finances — École SaaS',
        canActivate: [roleGuard],
        data: { roles: ['SAAS_ADMIN', 'PROMOTER', 'DIRECTOR', 'SECRETARY', 'CASHIER'] satisfies UserRole[] },
        loadComponent: () =>
          import('./features/finance/finance-page').then((m) => m.FinancePageComponent),
      },
      {
        path: 'import',
        title: 'Import Excel — École SaaS',
        canActivate: [roleGuard],
        data: { roles: ['PROMOTER', 'DIRECTOR', 'SECRETARY', 'CASHIER', 'CENSOR'] satisfies UserRole[] },
        loadComponent: () =>
          import('./features/import/import-page').then((m) => m.ImportPageComponent),
      },
      {
        path: 'settings',
        title: 'Paramètres — École SaaS',
        canActivate: [roleGuard],
        data: { roles: ['SAAS_ADMIN', 'PROMOTER', 'DIRECTOR'] satisfies UserRole[] },
        loadComponent: () =>
          import('./features/settings/settings-page').then((m) => m.SettingsPageComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
