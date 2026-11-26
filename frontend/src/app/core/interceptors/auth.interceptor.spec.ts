import { DOCUMENT } from '@angular/common';
import { HttpClient, HttpHeaders, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from '../auth/auth.service';
import { API_BASE_URL } from '../config/api.config';
import { AuthResponse } from '../models/auth.model';
import { authInterceptor } from './auth.interceptor';
import { problemDetailInterceptor } from './problem-detail.interceptor';

function setupTesting(apiBaseUrl: string, withToken = true, withProblemInterceptor = false) {
  const interceptors = withProblemInterceptor
    ? [authInterceptor, problemDetailInterceptor]
    : [authInterceptor];

  TestBed.configureTestingModule({
    providers: [
      { provide: API_BASE_URL, useValue: apiBaseUrl },
      provideRouter([]),
      provideHttpClient(withInterceptors(interceptors)),
      provideHttpClientTesting(),
    ],
  });

  const doc = TestBed.inject(DOCUMENT);
  doc.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';

  const authService = TestBed.inject(AuthService);
  if (withToken) {
    authService.setSession('secret-memory-jwt', {
      id: 'usr-1',
      fullName: 'Direction Démo',
      email: 'direction@ecole-demo.invalid',
      role: 'DIRECTOR',
      tenantId: 'tenant-1',
      tenantName: 'École Démo',
    });
  }

  return {
    http: TestBed.inject(HttpClient),
    httpMock: TestBed.inject(HttpTestingController),
    authService,
    router: TestBed.inject(Router),
  };
}

const REFRESHED_AUTH_RESPONSE: AuthResponse = {
  status: 'AUTHENTICATED',
  accessToken: 'refreshed-memory-jwt',
  mfaToken: null,
  expiresInSeconds: 900,
  mfaRequired: false,
  mfaEnrollmentRequired: false,
  user: {
    id: 'usr-1',
    subjectType: 'USER',
    email: 'direction@ecole-demo.invalid',
    firstName: 'Direction',
    lastName: 'Démo',
    roles: ['DIRECTOR'],
    mfaEnabled: true,
    tenantId: 'tenant-1',
    schoolCode: 'ECOLE-01',
    schoolName: 'École Démo',
  },
};

describe('authInterceptor', () => {
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
  });

  it('attaches Bearer token for relative requests targeting /api/v1 and never adds a tenant header', () => {
    const { http, httpMock } = setupTesting('/api/v1');

    http.get('/api/v1/students').subscribe();
    const req1 = httpMock.expectOne('/api/v1/students');
    expect(req1.request.headers.get('Authorization')).toBe('Bearer secret-memory-jwt');
    expect(req1.request.headers.keys().some((k) => k.toLowerCase().includes('tenant'))).toBe(false);
    req1.flush({});

    http.get('/api/v1').subscribe();
    const req2 = httpMock.expectOne('/api/v1');
    expect(req2.request.headers.get('Authorization')).toBe('Bearer secret-memory-jwt');
    req2.flush({});
  });

  it('does NOT attach token for non-matching segment boundaries or non-API relative URLs', () => {
    const { http, httpMock } = setupTesting('/api/v1');

    const forbiddenRelativeUrls = [
      '/api/v10/x',
      '/api/v1evil',
      '/api/v1/../public/info',
      '/assets/config.json',
    ];

    for (const url of forbiddenRelativeUrls) {
      http.get(url).subscribe();
      const req = httpMock.expectOne(url);
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    }
  });

  it('does NOT attach token for third-party domains or protocol-relative external URLs', () => {
    const { http, httpMock } = setupTesting('/api/v1');

    const thirdPartyUrls = [
      'https://tiers.example.com/api/v1/x',
      '//tiers.example.com/api/v1/x',
    ];

    for (const url of thirdPartyUrls) {
      http.get(url).subscribe();
      const req = httpMock.expectOne(url);
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    }
  });

  it('matches exact origin and segment boundary when API_BASE_URL is absolute, rejecting lookalike domains', () => {
    const { http, httpMock } = setupTesting('https://api.ecole.example/api/v1');

    http.get('https://api.ecole.example/api/v1/students').subscribe();
    const okReq = httpMock.expectOne('https://api.ecole.example/api/v1/students');
    expect(okReq.request.headers.get('Authorization')).toBe('Bearer secret-memory-jwt');
    okReq.flush({});

    const lookalikeUrls = [
      'https://api.ecole.example.evil.com/api/v1/students',
      'https://api.ecole.example:8443/api/v1/students',
      'http://api.ecole.example/api/v1/students',
      'https://api.ecole.example/api/v10/students',
      'https://tiers.example.com/api/v1/x',
    ];

    for (const url of lookalikeUrls) {
      http.get(url).subscribe();
      const req = httpMock.expectOne(url);
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    }
  });

  it('does NOT attach Authorization header when unauthenticated', () => {
    const { http, httpMock } = setupTesting('/api/v1', false);

    http.get('/api/v1/students').subscribe();
    const req = httpMock.expectOne('/api/v1/students');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('does NOT attach access token on /api/v1/auth/login and preserves explicit MFA Bearer header', () => {
    const { http, httpMock } = setupTesting('/api/v1', true);

    http.post('/api/v1/auth/login', {}).subscribe();
    const loginReq = httpMock.expectOne('/api/v1/auth/login');
    expect(loginReq.request.headers.has('Authorization')).toBe(false);
    loginReq.flush({});

    http
      .post(
        '/api/v1/auth/mfa/verify',
        { code: '123456' },
        { headers: new HttpHeaders({ Authorization: 'Bearer mfa-temp-token' }) }
      )
      .subscribe();
    const mfaReq = httpMock.expectOne('/api/v1/auth/mfa/verify');
    expect(mfaReq.request.headers.get('Authorization')).toBe('Bearer mfa-temp-token');
    mfaReq.flush({});
  });

  it('refreshes once on concurrent 401 responses and retries protected requests with the new token', () => {
    const { http, httpMock, authService } = setupTesting('/api/v1', true, true);
    let studentsResult: unknown;
    let financeResult: unknown;

    http.get('/api/v1/students').subscribe((data) => {
      studentsResult = data;
    });
    http.get('/api/v1/finance').subscribe((data) => {
      financeResult = data;
    });

    const firstStudents = httpMock.expectOne('/api/v1/students');
    const firstFinance = httpMock.expectOne('/api/v1/finance');
    firstStudents.flush({ status: 401, detail: 'Expiré' }, { status: 401, statusText: 'Unauthorized' });
    firstFinance.flush({ status: 401, detail: 'Expiré' }, { status: 401, statusText: 'Unauthorized' });

    // Single-flight: only ONE csrf + refresh pair is issued
    const csrfReq = httpMock.expectOne('/api/v1/auth/csrf');
    csrfReq.flush({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'xsrf-1' });

    const refreshReq = httpMock.expectOne('/api/v1/auth/refresh');
    expect(refreshReq.request.withCredentials).toBe(true);
    refreshReq.flush(REFRESHED_AUTH_RESPONSE);

    const retryStudents = httpMock.expectOne('/api/v1/students');
    const retryFinance = httpMock.expectOne('/api/v1/finance');
    expect(retryStudents.request.headers.get('Authorization')).toBe('Bearer refreshed-memory-jwt');
    expect(retryFinance.request.headers.get('Authorization')).toBe('Bearer refreshed-memory-jwt');
    retryStudents.flush({ items: ['a'] });
    retryFinance.flush({ total: 1000 });

    expect(studentsResult).toEqual({ items: ['a'] });
    expect(financeResult).toEqual({ total: 1000 });
    expect(authService.token()).toBe('refreshed-memory-jwt');
  });

  it('clears session and redirects to /login when refresh fails after a 401', () => {
    const { http, httpMock, authService, router } = setupTesting('/api/v1', true, true);
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    let caughtError = false;

    http.get('/api/v1/students').subscribe({
      error: () => {
        caughtError = true;
      },
    });

    const initialReq = httpMock.expectOne('/api/v1/students');
    initialReq.flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    const csrfReq = httpMock.expectOne('/api/v1/auth/csrf');
    csrfReq.flush({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'xsrf-2' });

    const refreshReq = httpMock.expectOne('/api/v1/auth/refresh');
    refreshReq.flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    expect(caughtError).toBe(true);
    expect(authService.isAuthenticated()).toBe(false);
    expect(authService.token()).toBeNull();
    expect(navSpy).toHaveBeenCalledWith(['/login'], { queryParams: undefined });
  });
});
