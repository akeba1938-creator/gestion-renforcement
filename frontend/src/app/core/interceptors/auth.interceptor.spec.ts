import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../auth/auth.service';
import { API_BASE_URL } from '../config/api.config';
import { authInterceptor } from './auth.interceptor';

function setupTesting(apiBaseUrl: string, withToken = true) {
  TestBed.configureTestingModule({
    providers: [
      { provide: API_BASE_URL, useValue: apiBaseUrl },
      provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(),
    ],
  });

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
  };
}

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
});
