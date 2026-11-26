import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { devRoutes as prodDevRoutes } from '../../app.dev-routes.prod';
import { AuthService } from '../../core/auth/auth.service';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';
import { problemDetailInterceptor } from '../../core/interceptors/problem-detail.interceptor';
import { AuthResponse } from '../../core/models/auth.model';
import { LoginPageComponent } from './login-page';

@Component({
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<p>Target</p>',
})
class DummyTargetComponent {}

const AUTHENTICATED_PAYLOAD: AuthResponse = {
  status: 'AUTHENTICATED',
  accessToken: 'jwt-live-memory',
  mfaToken: null,
  expiresInSeconds: 900,
  mfaRequired: false,
  mfaEnrollmentRequired: false,
  user: {
    id: 'usr-202',
    subjectType: 'USER',
    email: 'direction@lycee-dakar.sn',
    firstName: 'Fatou',
    lastName: 'Diallo',
    roles: ['DIRECTOR'],
    mfaEnabled: true,
    tenantId: 'tenant-dkr-01',
    schoolCode: 'LYC-DKR-01',
    schoolName: 'Lycée Excellence Dakar',
  },
};

function setInputValue(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}

async function setupLoginPage(returnUrl?: string) {
  await TestBed.configureTestingModule({
    imports: [LoginPageComponent],
    providers: [
      provideRouter([
        { path: 'dashboard', component: DummyTargetComponent },
        { path: 'finance', component: DummyTargetComponent },
      ]),
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: {
            queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}),
          },
        },
      },
      provideHttpClient(withInterceptors([authInterceptor, problemDetailInterceptor])),
      provideHttpClientTesting(),
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(LoginPageComponent);
  fixture.detectChanges();

  return {
    fixture,
    el: fixture.nativeElement as HTMLElement,
    httpMock: TestBed.inject(HttpTestingController),
    authService: TestBed.inject(AuthService),
    router: TestBed.inject(Router),
  };
}

describe('LoginPageComponent', () => {
  afterEach(() => {
    TestBed.inject(HttpTestingController, null)?.verify();
  });

  it('uses ChangeDetectionStrategy.OnPush and renders the institutional layout with a single btn-primary', async () => {
    const { el } = await setupLoginPage();

    const cmpMeta = (LoginPageComponent as unknown as { ɵcmp?: { onPush?: boolean } }).ɵcmp;
    expect(cmpMeta?.onPush).toBe(true);

    const aside = el.querySelector('aside');
    expect(aside?.classList.contains('bg-neutral')).toBe(true);
    expect(aside?.classList.contains('text-neutral-content')).toBe(true);
    expect(aside?.querySelectorAll('ul li').length).toBe(3);

    expect(el.querySelectorAll('.btn-primary').length).toBe(1);
    expect(el.querySelector('[data-testid="login-theme-toggle"]')).not.toBeNull();
    expect(el.textContent).not.toContain('mode démonstration');
  });

  it('toggles password visibility with accessible aria-pressed and input type', async () => {
    const { fixture, el } = await setupLoginPage();

    const passwordInput = el.querySelector<HTMLInputElement>('#login-password');
    const toggleBtn = el.querySelector<HTMLButtonElement>(
      '[data-testid="toggle-password-visibility"]'
    );

    expect(passwordInput?.type).toBe('password');
    expect(toggleBtn?.getAttribute('aria-pressed')).toBe('false');

    toggleBtn?.click();
    fixture.detectChanges();

    expect(passwordInput?.type).toBe('text');
    expect(toggleBtn?.getAttribute('aria-pressed')).toBe('true');

    toggleBtn?.click();
    fixture.detectChanges();

    expect(passwordInput?.type).toBe('password');
    expect(toggleBtn?.getAttribute('aria-pressed')).toBe('false');
  });

  it('submits tenant credentials to /api/v1/auth/login and redirects to safe returnUrl', async () => {
    const { fixture, el, httpMock, authService, router } = await setupLoginPage('/finance');
    const navSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    setInputValue(el.querySelector<HTMLInputElement>('#login-school-code')!, 'LYC-DKR-01');
    setInputValue(el.querySelector<HTMLInputElement>('#login-email')!, 'direction@lycee-dakar.sn');
    setInputValue(el.querySelector<HTMLInputElement>('#login-password')!, 'MotDePasse!2026');
    fixture.detectChanges();

    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const submitBtn = el.querySelector<HTMLButtonElement>('[data-testid="login-submit-btn"]');
    expect(submitBtn?.disabled).toBe(true);

    const req = httpMock.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    expect(req.request.body).toEqual({
      schoolCode: 'LYC-DKR-01',
      email: 'direction@lycee-dakar.sn',
      password: 'MotDePasse!2026',
    });
    req.flush(AUTHENTICATED_PAYLOAD);
    fixture.detectChanges();

    expect(authService.isAuthenticated()).toBe(true);
    expect(navSpy).toHaveBeenCalledWith('/finance');
  });

  it('rejects unsafe external returnUrl and falls back to /dashboard', async () => {
    const { fixture, el, httpMock, router } = await setupLoginPage('https://evil.example/steal');
    const navSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    setInputValue(el.querySelector<HTMLInputElement>('#login-school-code')!, 'LYC-DKR-01');
    setInputValue(el.querySelector<HTMLInputElement>('#login-email')!, 'direction@lycee-dakar.sn');
    setInputValue(el.querySelector<HTMLInputElement>('#login-password')!, 'MotDePasse!2026');
    fixture.detectChanges();

    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    httpMock.expectOne('/api/v1/auth/login').flush(AUTHENTICATED_PAYLOAD);
    fixture.detectChanges();

    expect(navSpy).toHaveBeenCalledWith('/dashboard');
  });

  it('transitions smoothly to 6-digit MFA step, supports OTP paste, verifies and redirects', async () => {
    const { fixture, el, httpMock, authService, router } = await setupLoginPage();
    const navSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    setInputValue(el.querySelector<HTMLInputElement>('#login-school-code')!, 'LYC-DKR-01');
    setInputValue(el.querySelector<HTMLInputElement>('#login-email')!, 'direction@lycee-dakar.sn');
    setInputValue(el.querySelector<HTMLInputElement>('#login-password')!, 'MotDePasse!2026');
    fixture.detectChanges();

    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    httpMock.expectOne('/api/v1/auth/login').flush({
      status: 'MFA_REQUIRED',
      accessToken: null,
      mfaToken: 'mfa-jwt-step-2',
      expiresInSeconds: 300,
      mfaRequired: true,
      mfaEnrollmentRequired: false,
      user: null,
    } satisfies AuthResponse);
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="mfa-step"]')).not.toBeNull();
    const otpInput = el.querySelector<HTMLInputElement>('#mfa-code')!;
    expect(otpInput.getAttribute('inputmode')).toBe('numeric');
    expect(otpInput.getAttribute('autocomplete')).toBe('one-time-code');
    expect(otpInput.classList.contains('tabular-nums')).toBe(true);

    // Simulate pasting formatted code "482 910"
    const pasteEvent = new Event('paste', { cancelable: true }) as ClipboardEvent;
    Object.defineProperty(pasteEvent, 'clipboardData', {
      value: { getData: () => '482 910' },
    });
    otpInput.dispatchEvent(pasteEvent);
    fixture.detectChanges();

    expect(otpInput.value).toBe('482910');

    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const mfaReq = httpMock.expectOne('/api/v1/auth/mfa/verify');
    expect(mfaReq.request.method).toBe('POST');
    expect(mfaReq.request.withCredentials).toBe(true);
    expect(mfaReq.request.headers.get('Authorization')).toBe('Bearer mfa-jwt-step-2');
    expect(mfaReq.request.body).toEqual({ code: '482910' });
    mfaReq.flush(AUTHENTICATED_PAYLOAD);
    fixture.detectChanges();

    expect(authService.isAuthenticated()).toBe(true);
    expect(navSpy).toHaveBeenCalledWith('/dashboard');
  });

  it('returns to credentials step on "Retour à la connexion" while preserving typed schoolCode and email', async () => {
    const { fixture, el, httpMock, authService } = await setupLoginPage();

    setInputValue(el.querySelector<HTMLInputElement>('#login-school-code')!, 'LYC-DKR-01');
    setInputValue(el.querySelector<HTMLInputElement>('#login-email')!, 'direction@lycee-dakar.sn');
    setInputValue(el.querySelector<HTMLInputElement>('#login-password')!, 'MotDePasse!2026');
    fixture.detectChanges();

    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    httpMock.expectOne('/api/v1/auth/login').flush({
      status: 'MFA_REQUIRED',
      accessToken: null,
      mfaToken: 'mfa-jwt-cancel',
      expiresInSeconds: 300,
      mfaRequired: true,
      mfaEnrollmentRequired: false,
      user: null,
    } satisfies AuthResponse);
    fixture.detectChanges();

    el.querySelector<HTMLButtonElement>('[data-testid="mfa-back-btn"]')?.click();
    fixture.detectChanges();

    expect(authService.hasPendingMfa()).toBe(false);
    expect(el.querySelector<HTMLInputElement>('#login-school-code')?.value).toBe('LYC-DKR-01');
    expect(el.querySelector<HTMLInputElement>('#login-email')?.value).toBe(
      'direction@lycee-dakar.sn'
    );
  });

  it('displays RFC 9457 errors (401, 423, 429, network) in role="alert" with requestId and preserves input', async () => {
    const { fixture, el, httpMock } = await setupLoginPage();

    const schoolInput = el.querySelector<HTMLInputElement>('#login-school-code')!;
    const emailInput = el.querySelector<HTMLInputElement>('#login-email')!;
    const passwordInput = el.querySelector<HTMLInputElement>('#login-password')!;

    setInputValue(schoolInput, 'LYC-DKR-01');
    setInputValue(emailInput, 'direction@lycee-dakar.sn');
    setInputValue(passwordInput, 'MauvaisMotDePasse');
    fixture.detectChanges();

    // 401 Invalid credentials with RFC 9457 ProblemDetail + requestId
    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    httpMock.expectOne('/api/v1/auth/login').flush(
      {
        type: 'urn:ecole:problem:authentication-failed',
        title: 'Authentification échouée',
        status: 401,
        detail: 'Identifiants invalides.',
        requestId: 'req-auth-401-abc',
      },
      { status: 401, statusText: 'Unauthorized' }
    );
    fixture.detectChanges();

    let alert = el.querySelector('[data-testid="login-error-alert"]');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(alert?.textContent).toContain('Identifiants invalides.');
    expect(el.querySelector('[data-testid="login-error-request-id"]')?.textContent?.trim()).toBe(
      'req-auth-401-abc'
    );
    // Input preserved on error
    expect(schoolInput.value).toBe('LYC-DKR-01');
    expect(emailInput.value).toBe('direction@lycee-dakar.sn');
    expect(passwordInput.value).toBe('MauvaisMotDePasse');

    // 423 Account locked
    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    httpMock.expectOne('/api/v1/auth/login').flush(
      {
        type: 'urn:ecole:problem:account-locked',
        title: 'Compte temporairement verrouillé',
        status: 423,
        detail:
          'Compte temporairement verrouillé suite à plusieurs tentatives échouées. Réessayez plus tard.',
        requestId: 'req-lock-423-xyz',
      },
      { status: 423, statusText: 'Locked' }
    );
    fixture.detectChanges();

    alert = el.querySelector('[data-testid="login-error-alert"]');
    expect(alert?.textContent).toContain('Compte temporairement verrouillé');
    expect(alert?.textContent).toContain('req-lock-423-xyz');

    // 429 Too many requests
    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    httpMock.expectOne('/api/v1/auth/login').flush(
      {
        type: 'urn:ecole:problem:too-many-requests',
        title: 'Trop de requêtes',
        status: 429,
        detail: 'Trop de tentatives ont été effectuées. Veuillez patienter avant de réessayer.',
      },
      { status: 429, statusText: 'Too Many Requests' }
    );
    fixture.detectChanges();

    alert = el.querySelector('[data-testid="login-error-alert"]');
    expect(alert?.textContent).toContain('Trop de tentatives');

    // Network error (status 0)
    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    httpMock.expectOne('/api/v1/auth/login').error(new ProgressEvent('error'));
    fixture.detectChanges();

    alert = el.querySelector('[data-testid="login-error-alert"]');
    expect(alert?.textContent).toContain('Connexion impossible');
  });

  it('supports platform admin login mode and posts to /api/v1/auth/platform/login', async () => {
    const { fixture, el, httpMock, router } = await setupLoginPage();
    const navSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    el.querySelector<HTMLButtonElement>('[data-testid="login-mode-platform"]')?.click();
    fixture.detectChanges();

    expect(el.querySelector('#login-school-code')).toBeNull();

    setInputValue(el.querySelector<HTMLInputElement>('#login-email')!, 'ops@ecole-saas.sn');
    setInputValue(el.querySelector<HTMLInputElement>('#login-password')!, 'PlatformPass!2026');
    fixture.detectChanges();

    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    const req = httpMock.expectOne('/api/v1/auth/platform/login');
    expect(req.request.withCredentials).toBe(true);
    expect(req.request.body).toEqual({
      email: 'ops@ecole-saas.sn',
      password: 'PlatformPass!2026',
    });
    req.flush({
      ...AUTHENTICATED_PAYLOAD,
      user: {
        ...AUTHENTICATED_PAYLOAD.user!,
        subjectType: 'PLATFORM_ADMIN',
        roles: ['ADMIN_SAAS'],
        tenantId: null,
        schoolCode: null,
        schoolName: null,
      },
    });
    fixture.detectChanges();

    expect(navSpy).toHaveBeenCalledWith('/dashboard');
  });

  it('initiates MFA enrollment when backend returns MFA_ENROLLMENT_REQUIRED', async () => {
    const { fixture, el, httpMock } = await setupLoginPage();

    setInputValue(el.querySelector<HTMLInputElement>('#login-school-code')!, 'LYC-DKR-01');
    setInputValue(el.querySelector<HTMLInputElement>('#login-email')!, 'direction@lycee-dakar.sn');
    setInputValue(el.querySelector<HTMLInputElement>('#login-password')!, 'MotDePasse!2026');
    fixture.detectChanges();

    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    httpMock.expectOne('/api/v1/auth/login').flush({
      status: 'MFA_ENROLLMENT_REQUIRED',
      accessToken: null,
      mfaToken: 'mfa-enroll-jwt',
      expiresInSeconds: 300,
      mfaRequired: true,
      mfaEnrollmentRequired: true,
      user: null,
    } satisfies AuthResponse);
    fixture.detectChanges();

    const enrollReq = httpMock.expectOne('/api/v1/auth/mfa/enroll');
    expect(enrollReq.request.headers.get('Authorization')).toBe('Bearer mfa-enroll-jwt');
    enrollReq.flush({
      secret: 'JBSWY3DPEHPK3PXP',
      otpauthUri: 'otpauth://totp/EcoleSaaS:direction@lycee-dakar.sn?secret=JBSWY3DPEHPK3PXP',
      digits: 6,
      periodSeconds: 30,
    });
    fixture.detectChanges();

    const enrollBox = el.querySelector('[data-testid="mfa-enrollment-box"]');
    expect(enrollBox?.textContent).toContain('JBSWY3DPEHPK3PXP');
  });

  it('keeps production devRoutes empty so /theme-preview and /dev-login are excluded', () => {
    expect(prodDevRoutes).toEqual([]);
  });
});
