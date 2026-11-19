import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let authService: AuthService;
  let router: Router;
  const dummyRoute = {} as ActivatedRouteSnapshot;
  const dummyState = {} as RouterStateSnapshot;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([])],
    });
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  it('redirects unauthenticated users to /login', () => {
    const result = TestBed.runInInjectionContext(() => authGuard(dummyRoute, dummyState));
    expect(result instanceof UrlTree).toBe(true);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });

  it('allows authenticated users through', () => {
    authService.setSession('tok-memory', {
      id: 'usr-1',
      fullName: 'Direction Démo',
      email: 'direction@ecole-demo.invalid',
      role: 'DIRECTOR',
      tenantId: 'tenant-1',
      tenantName: 'École Démo',
    });

    const result = TestBed.runInInjectionContext(() => authGuard(dummyRoute, dummyState));
    expect(result).toBe(true);
  });
});
