import { DOCUMENT } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { AuthResponse } from '../models/auth.model';
import { AuthUser } from '../models/user.model';
import { AuthService } from './auth.service';
import { sanitizeReturnUrl } from './return-url.util';

const DEMO_USER: AuthUser = {
  id: 'usr-demo-1',
  fullName: 'Direction Démo',
  email: 'direction@ecole-demo.invalid',
  role: 'DIRECTOR',
  tenantId: 'tenant-demo-1',
  tenantName: 'École Démo',
};

const AUTHENTICATED_RESPONSE: AuthResponse = {
  status: 'AUTHENTICATED',
  accessToken: 'jwt-access-memory',
  mfaToken: null,
  expiresInSeconds: 900,
  mfaRequired: false,
  mfaEnrollmentRequired: false,
  user: {
    id: 'usr-100',
    subjectType: 'USER',
    email: 'directrice@lycee-dakar.sn',
    firstName: 'Aminata',
    lastName: 'Ndiaye',
    roles: ['DIRECTOR'],
    mfaEnabled: true,
    tenantId: 'tenant-dkr-01',
    schoolCode: 'LYC-DKR-01',
    schoolName: 'Lycée Excellence Dakar',
  },
};

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let doc: Document;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    doc = TestBed.inject(DOCUMENT);
    doc.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts unauthenticated with null token and user', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(service.user()).toBeNull();
    expect(service.role()).toBeNull();
    expect(service.hasPendingMfa()).toBe(false);
  });

  it('stores token and user in memory only, never in localStorage or sessionStorage', () => {
    const localSetSpy = vi.spyOn(Storage.prototype, 'setItem');

    service.setSession('jwt-memory-only', DEMO_USER);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.token()).toBe('jwt-memory-only');
    expect(service.user()).toEqual(DEMO_USER);
    expect(service.role()).toBe('DIRECTOR');
    expect(localSetSpy).not.toHaveBeenCalled();

    localSetSpy.mockRestore();
  });

  it('posts tenant credentials to /api/v1/auth/login with credentials and stores session in memory', () => {
    const storageSpy = vi.spyOn(Storage.prototype, 'setItem');
    let received: AuthResponse | undefined;

    service
      .login({
        schoolCode: ' LYC-DKR-01 ',
        email: ' directrice@lycee-dakar.sn ',
        password: 'SecretPassword!2026',
      })
      .subscribe((res) => {
        received = res;
      });

    const req = httpMock.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    expect(req.request.body).toEqual({
      schoolCode: 'LYC-DKR-01',
      email: 'directrice@lycee-dakar.sn',
      password: 'SecretPassword!2026',
    });
    req.flush(AUTHENTICATED_RESPONSE);

    expect(received?.status).toBe('AUTHENTICATED');
    expect(service.isAuthenticated()).toBe(true);
    expect(service.token()).toBe('jwt-access-memory');
    expect(service.user()).toEqual({
      id: 'usr-100',
      fullName: 'Aminata Ndiaye',
      email: 'directrice@lycee-dakar.sn',
      role: 'DIRECTOR',
      tenantId: 'tenant-dkr-01',
      tenantName: 'Lycée Excellence Dakar',
    });
    expect(storageSpy).not.toHaveBeenCalled();
    storageSpy.mockRestore();
  });

  it('handles MFA_REQUIRED challenge and completes login via /api/v1/auth/mfa/verify', () => {
    service
      .login({
        schoolCode: 'LYC-DKR-01',
        email: 'directrice@lycee-dakar.sn',
        password: 'SecretPassword!2026',
      })
      .subscribe();

    const loginReq = httpMock.expectOne('/api/v1/auth/login');
    loginReq.flush({
      status: 'MFA_REQUIRED',
      accessToken: null,
      mfaToken: 'mfa-pending-jwt',
      expiresInSeconds: 300,
      mfaRequired: true,
      mfaEnrollmentRequired: false,
      user: null,
    } satisfies AuthResponse);

    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(service.hasPendingMfa()).toBe(true);
    expect(service.mfaEnrollmentRequired()).toBe(false);

    service.verifyMfa('654321').subscribe();

    const mfaReq = httpMock.expectOne('/api/v1/auth/mfa/verify');
    expect(mfaReq.request.method).toBe('POST');
    expect(mfaReq.request.withCredentials).toBe(true);
    expect(mfaReq.request.headers.get('Authorization')).toBe('Bearer mfa-pending-jwt');
    expect(mfaReq.request.body).toEqual({ code: '654321' });
    mfaReq.flush(AUTHENTICATED_RESPONSE);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.hasPendingMfa()).toBe(false);
    expect(service.token()).toBe('jwt-access-memory');
  });

  it('supports platform admin login and maps ADMIN_SAAS to SAAS_ADMIN', () => {
    service
      .loginPlatform({
        email: 'ops@ecole-saas.sn',
        password: 'PlatformSecret!2026',
      })
      .subscribe();

    const req = httpMock.expectOne('/api/v1/auth/platform/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    req.flush({
      status: 'AUTHENTICATED',
      accessToken: 'jwt-platform-access',
      mfaToken: null,
      expiresInSeconds: 900,
      mfaRequired: false,
      mfaEnrollmentRequired: false,
      user: {
        id: 'adm-1',
        subjectType: 'PLATFORM_ADMIN',
        email: 'ops@ecole-saas.sn',
        firstName: 'Ousmane',
        lastName: 'Fall',
        roles: ['ADMIN_SAAS'],
        mfaEnabled: true,
        tenantId: null,
        schoolCode: null,
        schoolName: null,
      },
    } satisfies AuthResponse);

    expect(service.role()).toBe('SAAS_ADMIN');
    expect(service.user()?.tenantName).toBe('Plateforme École SaaS');
  });

  it('deduplicates concurrent refresh() calls with CSRF token and withCredentials', () => {
    const results: AuthResponse[] = [];
    service.refresh().subscribe((res) => results.push(res));
    service.refresh().subscribe((res) => results.push(res));

    const csrfReq = httpMock.expectOne('/api/v1/auth/csrf');
    expect(csrfReq.request.method).toBe('GET');
    expect(csrfReq.request.withCredentials).toBe(true);
    csrfReq.flush({
      headerName: 'X-XSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-token-123',
    });

    const refreshReq = httpMock.expectOne('/api/v1/auth/refresh');
    expect(refreshReq.request.method).toBe('POST');
    expect(refreshReq.request.withCredentials).toBe(true);
    expect(refreshReq.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token-123');
    refreshReq.flush(AUTHENTICATED_RESPONSE);

    expect(results).toHaveLength(2);
    expect(service.isAuthenticated()).toBe(true);
    expect(service.token()).toBe('jwt-access-memory');
  });

  it('clears the in-memory session when refresh() fails', () => {
    service.setSession('stale-jwt', DEMO_USER);
    let failed = false;

    service.refresh().subscribe({
      error: () => {
        failed = true;
      },
    });

    const csrfReq = httpMock.expectOne('/api/v1/auth/csrf');
    csrfReq.flush({
      headerName: 'X-XSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-token-err',
    });

    const refreshReq = httpMock.expectOne('/api/v1/auth/refresh');
    refreshReq.flush(
      {
        type: 'urn:ecole:problem:authentication-failed',
        title: 'Authentification échouée',
        status: 401,
        detail: 'Session expirée. Veuillez vous reconnecter.',
      },
      { status: 401, statusText: 'Unauthorized' }
    );

    expect(failed).toBe(true);
    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(service.user()).toBeNull();
  });

  it('calls /api/v1/auth/logout with credentials and clears session even if backend fails', () => {
    service.setSession('active-jwt', DEMO_USER);
    let completed = false;

    service.logout().subscribe({
      complete: () => {
        completed = true;
      },
    });

    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();

    const csrfReq = httpMock.expectOne('/api/v1/auth/csrf');
    csrfReq.flush({
      headerName: 'X-XSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-logout-1',
    });

    const logoutReq = httpMock.expectOne('/api/v1/auth/logout');
    expect(logoutReq.request.method).toBe('POST');
    expect(logoutReq.request.withCredentials).toBe(true);
    expect(logoutReq.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-logout-1');
    logoutReq.flush(null, { status: 503, statusText: 'Service Unavailable' });

    expect(completed).toBe(true);
    expect(service.isAuthenticated()).toBe(false);
  });

  it('evaluates hasAnyRole against the in-memory user role', () => {
    expect(service.hasAnyRole(['DIRECTOR'])).toBe(false);

    service.setSession('jwt-memory-only', DEMO_USER);

    expect(service.hasAnyRole(['DIRECTOR', 'PROMOTER'])).toBe(true);
    expect(service.hasAnyRole(['CASHIER', 'PARENT'])).toBe(false);
  });

  it('sanitizes post-login returnUrl strictly to safe internal routes', () => {
    expect(sanitizeReturnUrl('/finance')).toBe('/finance');
    expect(sanitizeReturnUrl('/students?page=2')).toBe('/students?page=2');
    expect(sanitizeReturnUrl(null)).toBe('/dashboard');
    expect(sanitizeReturnUrl('')).toBe('/dashboard');
    expect(sanitizeReturnUrl('/')).toBe('/dashboard');
    expect(sanitizeReturnUrl('/login')).toBe('/dashboard');
    expect(sanitizeReturnUrl('/forbidden')).toBe('/dashboard');
    expect(sanitizeReturnUrl('https://evil.example/phish')).toBe('/dashboard');
    expect(sanitizeReturnUrl('//evil.example/phish')).toBe('/dashboard');
    expect(sanitizeReturnUrl('/\\evil.example')).toBe('/dashboard');
  });
});
