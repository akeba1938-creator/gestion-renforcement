import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { routes } from '../../app.routes';
import { AuthService } from '../auth/auth.service';
import { UserRole } from '../models/user.model';
import { roleGuard } from './role.guard';

function makeRoute(roles?: readonly UserRole[]): ActivatedRouteSnapshot {
  return { data: roles ? { roles } : {} } as unknown as ActivatedRouteSnapshot;
}

function setUserWithRole(authService: AuthService, role: UserRole): void {
  authService.setSession('tok-memory', {
    id: `usr-${role.toLowerCase()}`,
    fullName: `Utilisateur ${role}`,
    email: `${role.toLowerCase()}@ecole-demo.invalid`,
    role,
    tenantId: 'tenant-demo-1',
    tenantName: 'École Démo',
  });
}

describe('roleGuard', () => {
  let authService: AuthService;
  let router: Router;
  const dummyState = {} as RouterStateSnapshot;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes)],
    });
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  it('redirects unauthenticated users to /login', () => {
    const result = TestBed.runInInjectionContext(() =>
      roleGuard(makeRoute(['DIRECTOR']), dummyState)
    );
    expect(result instanceof UrlTree).toBe(true);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });

  it('allows access when authenticated user role matches route.data.roles', () => {
    setUserWithRole(authService, 'CASHIER');

    const result = TestBed.runInInjectionContext(() =>
      roleGuard(makeRoute(['DIRECTOR', 'CASHIER']), dummyState)
    );
    expect(result).toBe(true);
  });

  it('redirects TEACHER on an admin route to /forbidden', () => {
    setUserWithRole(authService, 'TEACHER');

    const result = TestBed.runInInjectionContext(() =>
      roleGuard(makeRoute(['DIRECTOR', 'SECRETARY']), dummyState)
    );
    expect(result instanceof UrlTree).toBe(true);
    expect(router.serializeUrl(result as UrlTree)).toBe('/forbidden');
  });

  it('redirects PARENT on an admin route to /forbidden', () => {
    setUserWithRole(authService, 'PARENT');

    const result = TestBed.runInInjectionContext(() =>
      roleGuard(makeRoute(['DIRECTOR', 'CASHIER']), dummyState)
    );
    expect(result instanceof UrlTree).toBe(true);
    expect(router.serializeUrl(result as UrlTree)).toBe('/forbidden');
  });

  it('does not loop when a non-admin authenticated user navigates to /dashboard via the real router', async () => {
    setUserWithRole(authService, 'TEACHER');

    const navigated = await router.navigateByUrl('/dashboard');
    expect(navigated).toBe(true);
    expect(router.url).toBe('/forbidden');
  });
});
