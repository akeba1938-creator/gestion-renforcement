import { ChangeDetectionStrategy, reflectComponentType } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ProblemDetailError } from '../../core/models/problem-detail.model';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { ThemeService } from '../../core/services/theme.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { SettingsApiService } from './data-access/settings-api.service';
import {
  DEFAULT_NOTIFICATION_PREFERENCES,
  EvaluationPeriodItem,
  MfaEnrollmentPayload,
  MfaVerifyResult,
  NotificationPreferences,
  SchoolSettings,
  SettingsSnapshot,
  UpdateAcademicSettingsInput,
  UpdateAcademicSettingsResult,
  UpdateSchoolSettingsInput,
} from './models/settings.model';
import { SettingsPageComponent } from './settings-page';

const SAMPLE_SCHOOL: SchoolSettings = {
  id: 'school-1',
  code: 'ECOLE-DKR-001',
  name: 'Groupe Scolaire Démo',
  shortName: 'GSD',
  address: 'Avenue Cheikh Anta Diop, Fann',
  city: 'Dakar',
  region: 'Dakar',
  countryCode: 'SN',
  phone: '+221338214590',
  email: 'direction@ecole-demo.invalid',
  motto: 'Excellence',
  ministryCode: 'MEN-01',
  website: 'https://ecole-demo.invalid',
  currency: 'FCFA',
  timezone: 'Africa/Dakar',
  gradingPeriodSystem: 'TRIMESTER',
  gradingScale: 20,
  updatedAtUtc: '2026-10-15T10:30:00Z',
};

const SAMPLE_SNAPSHOT: SettingsSnapshot = {
  school: SAMPLE_SCHOOL,
  academicYears: [
    {
      id: 'ay-2025-2026',
      code: '2025-2026',
      label: 'Année scolaire 2025-2026',
      startDate: '2025-10-01',
      endDate: '2026-07-31',
      periodSystem: 'TRIMESTER',
      status: 'ACTIVE',
      current: true,
    },
    {
      id: 'ay-2024-2025',
      code: '2024-2025',
      label: 'Année scolaire 2024-2025',
      startDate: '2024-10-02',
      endDate: '2025-07-30',
      periodSystem: 'TRIMESTER',
      status: 'CLOSED',
      current: false,
    },
  ],
  periodsByYearId: {
    'ay-2025-2026': [
      {
        id: 'per-t1',
        academicYearId: 'ay-2025-2026',
        code: 'T1',
        name: 'Trimestre 1',
        periodType: 'TRIMESTER',
        sequenceOrder: 1,
        startDate: '2025-10-01',
        endDate: '2025-12-20',
        weight: 1,
        status: 'CLOSED',
      },
      {
        id: 'per-t2',
        academicYearId: 'ay-2025-2026',
        code: 'T2',
        name: 'Trimestre 2',
        periodType: 'TRIMESTER',
        sequenceOrder: 2,
        startDate: '2026-01-05',
        endDate: '2026-03-31',
        weight: 1,
        status: 'OPEN',
      },
      {
        id: 'per-t3',
        academicYearId: 'ay-2025-2026',
        code: 'T3',
        name: 'Trimestre 3',
        periodType: 'TRIMESTER',
        sequenceOrder: 3,
        startDate: '2026-04-13',
        endDate: '2026-07-31',
        weight: 1,
        status: 'PLANNED',
      },
    ],
  },
  security: {
    accountId: 'usr-1',
    fullName: 'Direction Démo',
    email: 'direction@ecole-demo.invalid',
    roles: ['DIRECTOR'],
    mfaEnabled: false,
    mfaRequiredByPolicy: true,
    accessTokenMinutes: 15,
    refreshSessionDays: 7,
    mfaChallengeMinutes: 5,
    maxLoginFailures: 5,
    lockoutMinutes: 15,
  },
  preferences: { ...DEFAULT_NOTIFICATION_PREFERENCES },
};

interface SettingsApiStub {
  loadSettings: ReturnType<typeof vi.fn<() => Observable<SettingsSnapshot>>>;
  listPeriodsForYear: ReturnType<typeof vi.fn<(yearId: string) => Observable<readonly EvaluationPeriodItem[]>>>;
  updateSchool: ReturnType<typeof vi.fn<(input: UpdateSchoolSettingsInput) => Observable<SchoolSettings>>>;
  updateAcademicSettings: ReturnType<
    typeof vi.fn<(input: UpdateAcademicSettingsInput) => Observable<UpdateAcademicSettingsResult>>
  >;
  enrollMfa: ReturnType<typeof vi.fn<() => Observable<MfaEnrollmentPayload>>>;
  verifyMfa: ReturnType<typeof vi.fn<(code: string) => Observable<MfaVerifyResult>>>;
  updatePreferences: ReturnType<
    typeof vi.fn<(prefs: NotificationPreferences) => Observable<NotificationPreferences>>
  >;
}

describe('SettingsPageComponent', () => {
  function createApiStub(overrides?: Partial<{
    loadSettings: () => Observable<SettingsSnapshot>;
    listPeriodsForYear: (yearId: string) => Observable<readonly EvaluationPeriodItem[]>;
    updateSchool: (input: UpdateSchoolSettingsInput) => Observable<SchoolSettings>;
    updateAcademicSettings: (
      input: UpdateAcademicSettingsInput
    ) => Observable<UpdateAcademicSettingsResult>;
    enrollMfa: () => Observable<MfaEnrollmentPayload>;
    verifyMfa: (code: string) => Observable<MfaVerifyResult>;
    updatePreferences: (prefs: NotificationPreferences) => Observable<NotificationPreferences>;
  }>): SettingsApiStub {
    return {
      loadSettings: vi.fn(overrides?.loadSettings ?? (() => of(SAMPLE_SNAPSHOT))),
      listPeriodsForYear: vi.fn(overrides?.listPeriodsForYear ?? (() => of([]))),
      updateSchool: vi.fn(
        overrides?.updateSchool ??
          ((input: UpdateSchoolSettingsInput) =>
            of({
              ...input.currentSchool,
              name: input.name,
              address: input.address,
              city: input.city,
              region: input.region,
              phone: input.phone,
              email: input.email,
              updatedAtUtc: '2026-11-26T12:00:00Z',
            }))
      ),
      updateAcademicSettings: vi.fn(
        overrides?.updateAcademicSettings ??
          ((input: UpdateAcademicSettingsInput) =>
            of({
              year: {
                ...SAMPLE_SNAPSHOT.academicYears[0],
                id: input.academicYearId,
                startDate: input.startDate,
                endDate: input.endDate,
                periodSystem: input.periodSystem,
                current: input.setAsCurrent,
                status: 'ACTIVE',
              },
              openedPeriod: null,
            }))
      ),
      enrollMfa: vi.fn(
        overrides?.enrollMfa ??
          (() =>
            of({
              secret: 'JBSWY3DPEHPK3PXP01SN',
              otpauthUri: 'otpauth://totp/EcoleSaaS:direction@ecole-demo.invalid?secret=JBSWY3DPEHPK3PXP01SN',
              digits: 6,
              periodSeconds: 30,
            }))
      ),
      verifyMfa: vi.fn(overrides?.verifyMfa ?? (() => of({ mfaEnabled: true }))),
      updatePreferences: vi.fn(
        overrides?.updatePreferences ?? ((prefs: NotificationPreferences) => of(prefs))
      ),
    };
  }

  async function setup(options?: {
    api?: SettingsApiStub;
    initialQueryParams?: Record<string, string>;
    isOnline?: boolean;
  }) {
    const api = options?.api ?? createApiStub();
    const initialMap = convertToParamMap(options?.initialQueryParams ?? {});
    const queryParamMap$ = new BehaviorSubject(initialMap);

    const activatedRouteStub = {
      snapshot: { queryParamMap: initialMap },
      queryParamMap: queryParamMap$.asObservable(),
    };

    const isOnline = options?.isOnline ?? true;

    await TestBed.configureTestingModule({
      imports: [SettingsPageComponent],
      providers: [
        provideRouter([]),
        { provide: SettingsApiService, useValue: api },
        { provide: ActivatedRoute, useValue: activatedRouteStub },
        {
          provide: NetworkStatusService,
          useValue: { isOnline: () => isOnline },
        },
      ],
    }).compileComponents();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(SettingsPageComponent);
    const toastService = TestBed.inject(ToastService);
    const themeService = TestBed.inject(ThemeService);

    return { fixture, api, router, navigateSpy, toastService, themeService, queryParamMap$ };
  }

  afterEach(() => {
    TestBed.inject(ToastService).clear();
  });

  it('uses standalone component with ChangeDetectionStrategy.OnPush', () => {
    const mirror = reflectComponentType(SettingsPageComponent);
    expect(mirror?.isStandalone).toBe(true);
    const cmpDef = (SettingsPageComponent as unknown as { ɵcmp?: { onPush?: boolean } }).ɵcmp;
    expect(cmpDef?.onPush).toBe(true);
    expect(ChangeDetectionStrategy.OnPush).toBeDefined();
  });

  it('renders header with title, school & active year subtitle, and strictly 1 btn-primary', async () => {
    const { fixture } = await setup();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Paramètres');
    expect(el.textContent).toContain('Groupe Scolaire Démo · Année scolaire 2025-2026');

    const primaryButtons = el.querySelectorAll('.btn-primary');
    expect(primaryButtons.length).toBe(1);
    expect(primaryButtons[0]?.textContent).toContain('Enregistrer les modifications');

    // Read-only school fields
    expect(el.querySelector<HTMLInputElement>('#school-code-readonly')?.value).toBe('ECOLE-DKR-001');
    expect(el.querySelector<HTMLInputElement>('#school-currency-readonly')?.value).toBe('FCFA');
    expect(el.querySelector<HTMLInputElement>('#school-timezone-readonly')?.value).toBe('Africa/Dakar');
  });

  it('shows skeleton loader while loading (State 1)', async () => {
    const pending$ = new Subject<SettingsSnapshot>();
    const api = createApiStub({ loadSettings: () => pending$.asObservable() });
    const { fixture } = await setup({ api });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('app-skeleton-loader').length).toBe(1);

    pending$.next(SAMPLE_SNAPSHOT);
    pending$.complete();
    fixture.detectChanges();

    expect(el.querySelectorAll('app-skeleton-loader').length).toBe(0);
    expect(el.querySelector<HTMLInputElement>('#school-name-input')?.value).toBe(
      'Groupe Scolaire Démo'
    );
  });

  it('shows empty state when academic years list is empty (State 2)', async () => {
    const api = createApiStub({
      loadSettings: () =>
        of({
          ...SAMPLE_SNAPSHOT,
          academicYears: [],
          periodsByYearId: {},
        }),
    });
    const { fixture } = await setup({ api, initialQueryParams: { tab: 'academic' } });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-empty-state')?.textContent).toContain(
      'Aucune année scolaire configurée'
    );
  });

  it('shows filtered empty state with reset action on academic years list (State 3)', async () => {
    const { fixture } = await setup({ initialQueryParams: { tab: 'academic' } });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const archivedFilterBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(
      (btn) => btn.textContent?.trim() === 'Archivée'
    );
    archivedFilterBtn?.click();
    fixture.detectChanges();

    const emptyState = el.querySelector('app-empty-state');
    expect(emptyState).not.toBeNull();
    expect(emptyState?.textContent).toContain('Aucune année scolaire ne correspond à ce filtre');

    const resetBtn = emptyState?.querySelector<HTMLButtonElement>('button');
    resetBtn?.click();
    fixture.detectChanges();

    expect(el.querySelectorAll('table tbody tr').length).toBe(2);
  });

  it('renders RFC 9457 error state with copyable requestId and retry action (State 4)', async () => {
    let attempts = 0;
    const api = createApiStub({
      loadSettings: () => {
        attempts += 1;
        if (attempts === 1) {
          return throwError(
            () =>
              new ProblemDetailError({
                type: 'urn:ecole:problem:service-unavailable',
                title: 'Service indisponible',
                status: 503,
                detail: 'Impossible de récupérer les paramètres.',
                requestId: 'req-settings-load-503',
              })
          );
        }
        return of(SAMPLE_SNAPSHOT);
      },
    });

    const { fixture } = await setup({ api });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const errorState = el.querySelector('app-error-state');
    expect(errorState?.textContent).toContain('Impossible de récupérer les paramètres.');
    expect(errorState?.textContent).toContain('req-settings-load-503');

    const retryBtn = Array.from(errorState!.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => b.textContent?.includes('Réessayer')
    );
    retryBtn?.click();
    fixture.detectChanges();

    expect(attempts).toBe(2);
    expect(el.querySelector('app-error-state')).toBeNull();
  });

  it('displays offline PWA banner and disables save when navigator is offline (State 6)', async () => {
    const { fixture } = await setup({ isOnline: false });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="settings-offline-banner"]')?.textContent).toContain(
      'Mode hors-ligne détecté'
    );
    expect(el.querySelector<HTMLButtonElement>('.btn-primary')?.disabled).toBe(true);
  });

  it('synchronizes tabs with URL query param (?tab=...) and preserves edited form input across tab switches', async () => {
    const { fixture, navigateSpy } = await setup();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const nameInput = el.querySelector<HTMLInputElement>('#school-name-input')!;
    nameInput.value = 'Lycée Modifié Non Sauvegardé';
    nameInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    // Switch to Academic tab
    el.querySelector<HTMLButtonElement>('#settings-tab-academic')?.click();
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: { tab: 'academic' },
        replaceUrl: true,
      })
    );
    expect(el.textContent).toContain('01/10/2025');
    expect(el.textContent).toContain('31/07/2026');

    // Return to School tab — unsaved input must be preserved
    el.querySelector<HTMLButtonElement>('#settings-tab-school')?.click();
    fixture.detectChanges();

    expect(el.querySelector<HTMLInputElement>('#school-name-input')?.value).toBe(
      'Lycée Modifié Non Sauvegardé'
    );
  });

  it('validates E.164 phone, preserves input on RFC 9457 save error, and shows toast on success (State 5)', async () => {
    let failOnce = true;
    const api = createApiStub({
      updateSchool: (input) => {
        if (failOnce) {
          failOnce = false;
          return throwError(
            () =>
              new ProblemDetailError({
                type: 'urn:ecole:problem:validation-error',
                title: 'Erreur de validation',
                status: 422,
                detail: 'Certains champs sont invalides.',
                requestId: 'req-school-save-422',
                errors: [{ field: 'email', message: 'Domaine e-mail non autorisé.' }],
              })
          );
        }
        return of({
          ...input.currentSchool,
          name: input.name,
          phone: input.phone,
          email: input.email,
        });
      },
    });

    const { fixture, toastService } = await setup({ api });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const phoneInput = el.querySelector<HTMLInputElement>('#school-phone-input')!;
    const emailInput = el.querySelector<HTMLInputElement>('#school-email-input')!;
    const saveBtn = el.querySelector<HTMLButtonElement>('.btn-primary')!;

    // Invalid phone triggers client validation
    phoneInput.value = '771234567';
    phoneInput.dispatchEvent(new Event('input'));
    saveBtn.click();
    fixture.detectChanges();

    expect(el.querySelector('#school-phone-error')?.textContent).toContain('E.164');
    expect(api.updateSchool).not.toHaveBeenCalled();

    // Fix phone -> server RFC 9457 error preserves inputs
    phoneInput.value = '+221771234567';
    phoneInput.dispatchEvent(new Event('input'));
    emailInput.value = 'contact@ecole-dakar.sn';
    emailInput.dispatchEvent(new Event('input'));
    saveBtn.click();
    fixture.detectChanges();

    expect(el.textContent).toContain('req-school-save-422');
    expect(el.textContent).toContain('Domaine e-mail non autorisé.');
    expect(phoneInput.value).toBe('+221771234567');
    expect(emailInput.value).toBe('contact@ecole-dakar.sn');

    // Second save succeeds with Toast
    saveBtn.click();
    fixture.detectChanges();

    expect(
      toastService.toasts().some((t) => t.tone === 'success' && t.title === 'Paramètres enregistrés')
    ).toBe(true);
  });

  it('opens MFA drawer from Sécurité tab, preserves TOTP code on RFC 9457 error, and activates MFA on success', async () => {
    let failOnce = true;
    const api = createApiStub({
      verifyMfa: () => {
        if (failOnce) {
          failOnce = false;
          return throwError(
            () =>
              new ProblemDetailError({
                type: 'urn:ecole:problem:invalid-mfa-code',
                title: 'Code TOTP invalide',
                status: 422,
                detail: 'Le code saisi est expiré ou incorrect.',
                requestId: 'req-mfa-422',
                errors: [{ field: 'code', message: 'Code invalide.' }],
              })
          );
        }
        return of({ mfaEnabled: true });
      },
    });

    const { fixture, toastService } = await setup({
      api,
      initialQueryParams: { tab: 'security' },
    });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Non configurée');
    expect(el.textContent).toContain('15 min');
    expect(el.textContent).toContain('7 jours');

    const mfaBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Configurer / Réinitialiser MFA')
    );
    expect(mfaBtn?.classList.contains('btn-outline')).toBe(true);
    mfaBtn?.click();
    fixture.detectChanges();

    expect(api.enrollMfa).toHaveBeenCalledTimes(1);
    expect(el.textContent).toContain('JBSWY3DPEHPK3PXP01SN');

    const codeInput = el.querySelector<HTMLInputElement>('#mfa-totp-code')!;
    const form = el.querySelector<HTMLFormElement>('#mfa-verify-form')!;

    codeInput.value = '654321';
    codeInput.dispatchEvent(new Event('input'));
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    // Preserves input on RFC 9457 error
    expect(el.textContent).toContain('req-mfa-422');
    expect(codeInput.value).toBe('654321');

    // Retry succeeds -> updates MFA status and displays toast
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(el.textContent).toContain('MFA activée');
    expect(
      toastService.toasts().some((t) => t.tone === 'success' && t.title === 'MFA configurée')
    ).toBe(true);
  });

  it('switches ThemeService live and saves notification preferences on Préférences tab', async () => {
    const { fixture, api, themeService, toastService } = await setup({
      initialQueryParams: { tab: 'preferences' },
    });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;

    const darkBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Thème sombre')
    );
    darkBtn?.click();
    fixture.detectChanges();
    expect(themeService.theme()).toBe('dark');

    const lightBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Thème clair')
    );
    lightBtn?.click();
    fixture.detectChanges();
    expect(themeService.theme()).toBe('light');

    const receiptToggle = el.querySelector<HTMLInputElement>('#pref-receipts-toggle')!;
    receiptToggle.click();
    fixture.detectChanges();

    const saveBtn = el.querySelector<HTMLButtonElement>('.btn-primary')!;
    saveBtn.click();
    fixture.detectChanges();

    expect(api.updatePreferences).toHaveBeenCalledWith(
      expect.objectContaining({
        receiptNotificationsEnabled: false,
        absenceNotificationsEnabled: true,
      })
    );
    expect(
      toastService
        .toasts()
        .some((t) => t.tone === 'success' && t.title === 'Préférences enregistrées')
    ).toBe(true);
  });
});
