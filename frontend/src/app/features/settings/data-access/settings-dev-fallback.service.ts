import { Injectable, inject } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  AcademicYearItem,
  DEFAULT_NOTIFICATION_PREFERENCES,
  E164_PHONE_PATTERN,
  EMAIL_PATTERN,
  EvaluationPeriodItem,
  MfaEnrollmentPayload,
  MfaVerifyResult,
  NotificationPreferences,
  SchoolSettings,
  SettingsSnapshot,
  TOTP_CODE_PATTERN,
  UpdateAcademicSettingsInput,
  UpdateAcademicSettingsResult,
  UpdateSchoolSettingsInput,
} from '../models/settings.model';
import { SettingsDemoFallback } from './settings-demo-fallback.token';

const DEV_DEMO_TOKEN = 'demo-memory-token';

function buildInitialDemoSchool(): SchoolSettings {
  return {
    id: 'school-demo-001',
    code: 'ECOLE-DKR-001',
    name: 'Groupe Scolaire Démo',
    shortName: 'GSD Dakar',
    address: 'Avenue Cheikh Anta Diop, Immeuble Fann Résidence',
    city: 'Dakar',
    region: 'Dakar',
    countryCode: 'SN',
    phone: '+221338214590',
    email: 'direction@ecole-demo.invalid',
    motto: 'Excellence, Rigueur et Citoyenneté',
    ministryCode: 'MEN-DKR-2026-041',
    website: 'https://ecole-demo.invalid',
    currency: 'FCFA',
    timezone: 'Africa/Dakar',
    gradingPeriodSystem: 'TRIMESTER',
    gradingScale: 20,
    updatedAtUtc: '2026-10-15T10:30:00Z',
  };
}

function buildInitialDemoAcademicYears(): AcademicYearItem[] {
  return [
    {
      id: 'ay-demo-2026-2027',
      code: '2026-2027',
      label: 'Année scolaire 2026-2027',
      startDate: '2026-10-01',
      endDate: '2027-07-31',
      periodSystem: 'TRIMESTER',
      status: 'ACTIVE',
      current: true,
    },
    {
      id: 'ay-demo-2025-2026',
      code: '2025-2026',
      label: 'Année scolaire 2025-2026',
      startDate: '2025-10-01',
      endDate: '2026-07-31',
      periodSystem: 'TRIMESTER',
      status: 'CLOSED',
      current: false,
    },
    {
      id: 'ay-demo-2024-2025',
      code: '2024-2025',
      label: 'Année scolaire 2024-2025',
      startDate: '2024-10-02',
      endDate: '2025-07-30',
      periodSystem: 'TRIMESTER',
      status: 'ARCHIVED',
      current: false,
    },
  ];
}

function buildInitialDemoPeriods(): Record<string, EvaluationPeriodItem[]> {
  return {
    'ay-demo-2026-2027': [
      {
        id: 'per-demo-2627-t1',
        academicYearId: 'ay-demo-2026-2027',
        code: 'T1',
        name: 'Trimestre 1',
        periodType: 'TRIMESTER',
        sequenceOrder: 1,
        startDate: '2026-10-01',
        endDate: '2026-12-19',
        weight: 1,
        status: 'OPEN',
      },
      {
        id: 'per-demo-2627-t2',
        academicYearId: 'ay-demo-2026-2027',
        code: 'T2',
        name: 'Trimestre 2',
        periodType: 'TRIMESTER',
        sequenceOrder: 2,
        startDate: '2027-01-04',
        endDate: '2027-03-31',
        weight: 1,
        status: 'PLANNED',
      },
      {
        id: 'per-demo-2627-t3',
        academicYearId: 'ay-demo-2026-2027',
        code: 'T3',
        name: 'Trimestre 3',
        periodType: 'TRIMESTER',
        sequenceOrder: 3,
        startDate: '2027-04-12',
        endDate: '2027-07-31',
        weight: 1,
        status: 'PLANNED',
      },
    ],
    'ay-demo-2025-2026': [
      {
        id: 'per-demo-2526-t1',
        academicYearId: 'ay-demo-2025-2026',
        code: 'T1',
        name: 'Trimestre 1',
        periodType: 'TRIMESTER',
        sequenceOrder: 1,
        startDate: '2025-10-01',
        endDate: '2025-12-20',
        weight: 1,
        status: 'LOCKED',
      },
      {
        id: 'per-demo-2526-t2',
        academicYearId: 'ay-demo-2025-2026',
        code: 'T2',
        name: 'Trimestre 2',
        periodType: 'TRIMESTER',
        sequenceOrder: 2,
        startDate: '2026-01-05',
        endDate: '2026-03-31',
        weight: 1,
        status: 'LOCKED',
      },
      {
        id: 'per-demo-2526-t3',
        academicYearId: 'ay-demo-2025-2026',
        code: 'T3',
        name: 'Trimestre 3',
        periodType: 'TRIMESTER',
        sequenceOrder: 3,
        startDate: '2026-04-13',
        endDate: '2026-07-31',
        weight: 1,
        status: 'LOCKED',
      },
    ],
    'ay-demo-2024-2025': [],
  };
}

/**
 * Purely fictional settings dataset for local development mode only (`/dev-login` without backend).
 * Never bundled in production builds (excluded via `app.dev-providers.prod.ts` file replacement).
 */
@Injectable({ providedIn: 'root' })
export class SettingsDevFallbackService implements SettingsDemoFallback {
  private readonly authService = inject(AuthService);

  private school: SchoolSettings = buildInitialDemoSchool();
  private academicYears: AcademicYearItem[] = buildInitialDemoAcademicYears();
  private periodsByYearId: Record<string, EvaluationPeriodItem[]> = buildInitialDemoPeriods();
  private mfaEnabled = true;
  private preferences: NotificationPreferences = { ...DEFAULT_NOTIFICATION_PREFERENCES };
  private mfaEnrollCounter = 0;

  isDemoSessionActive(): boolean {
    return this.authService.token() === DEV_DEMO_TOKEN;
  }

  loadSettings(): Observable<SettingsSnapshot> {
    const user = this.authService.user();
    const clonedPeriods: Record<string, readonly EvaluationPeriodItem[]> = {};
    for (const [yearId, list] of Object.entries(this.periodsByYearId)) {
      clonedPeriods[yearId] = list.map((period) => ({ ...period }));
    }

    return of({
      school: { ...this.school },
      academicYears: this.academicYears.map((year) => ({ ...year })),
      periodsByYearId: clonedPeriods,
      security: {
        accountId: user?.id ?? 'usr-demo-director',
        fullName: user?.fullName ?? 'Direction Démo',
        email: user?.email ?? 'direction@ecole-demo.invalid',
        roles: [user?.role ?? 'DIRECTOR'],
        mfaEnabled: this.mfaEnabled,
        mfaRequiredByPolicy: true,
        accessTokenMinutes: 15,
        refreshSessionDays: 7,
        mfaChallengeMinutes: 5,
        maxLoginFailures: 5,
        lockoutMinutes: 15,
      },
      preferences: { ...this.preferences },
    });
  }

  updateSchool(input: UpdateSchoolSettingsInput): Observable<SchoolSettings> {
    const name = input.name.trim();
    const phone = input.phone.trim();
    const email = input.email.trim();

    if (!name) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:validation-error',
            title: 'Erreur de validation',
            status: 422,
            detail: "Le nom de l'établissement est obligatoire.",
            requestId: 'req-demo-school-422',
            errors: [{ field: 'name', message: "Le nom de l'établissement est obligatoire." }],
          })
      );
    }

    if (phone && !E164_PHONE_PATTERN.test(phone)) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:validation-error',
            title: 'Numéro invalide',
            status: 422,
            detail: 'Le numéro de téléphone doit respecter le format E.164 (ex. +221770000001).',
            requestId: 'req-demo-school-phone-422',
            errors: [
              {
                field: 'phone',
                message: 'Format E.164 attendu (ex. +221338214590 ou +221770000001).',
              },
            ],
          })
      );
    }

    if (email && !EMAIL_PATTERN.test(email)) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:validation-error',
            title: 'E-mail invalide',
            status: 422,
            detail: "L'adresse e-mail officielle est invalide.",
            requestId: 'req-demo-school-email-422',
            errors: [{ field: 'email', message: "Veuillez saisir une adresse e-mail valide." }],
          })
      );
    }

    this.school = {
      ...this.school,
      name,
      address: input.address.trim(),
      city: input.city.trim(),
      region: input.region.trim(),
      phone,
      email,
      updatedAtUtc: new Date().toISOString(),
    };

    return of({ ...this.school });
  }

  updateAcademicSettings(
    input: UpdateAcademicSettingsInput
  ): Observable<UpdateAcademicSettingsResult> {
    const existing = this.academicYears.find((year) => year.id === input.academicYearId);
    if (!existing) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:academic-year-not-found',
            title: 'Année scolaire introuvable',
            status: 404,
            detail: "L'année scolaire sélectionnée n'existe plus.",
            requestId: 'req-demo-ay-404',
          })
      );
    }

    if (input.startDate >= input.endDate) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:validation-error',
            title: 'Dates incohérentes',
            status: 422,
            detail: 'La date de fin doit être postérieure à la date de début.',
            requestId: 'req-demo-ay-422',
            errors: [
              {
                field: 'endDate',
                message: 'La date de fin doit être postérieure à la date de début.',
              },
            ],
          })
      );
    }

    const updatedYear: AcademicYearItem = {
      ...existing,
      label: input.label.trim() || existing.label,
      startDate: input.startDate,
      endDate: input.endDate,
      periodSystem: input.periodSystem,
      status: input.setAsCurrent || input.shouldActivateYear ? 'ACTIVE' : existing.status,
      current: input.setAsCurrent ? true : existing.current,
    };

    this.academicYears = this.academicYears.map((year) => {
      if (year.id === updatedYear.id) {
        return updatedYear;
      }
      if (input.setAsCurrent && year.current) {
        return { ...year, current: false, status: year.status === 'ACTIVE' ? 'CLOSED' : year.status };
      }
      return year;
    });

    let openedPeriod: EvaluationPeriodItem | null = null;
    if (input.activePeriodId) {
      const yearPeriods = this.periodsByYearId[updatedYear.id] ?? [];
      this.periodsByYearId[updatedYear.id] = yearPeriods.map((period) => {
        if (period.id === input.activePeriodId) {
          const next: EvaluationPeriodItem = { ...period, status: 'OPEN' };
          openedPeriod = next;
          return next;
        }
        if (period.status === 'OPEN') {
          return { ...period, status: 'CLOSED' };
        }
        return period;
      });
    }

    return of({ year: updatedYear, openedPeriod });
  }

  enrollMfa(): Observable<MfaEnrollmentPayload> {
    this.mfaEnrollCounter += 1;
    const suffix = String(this.mfaEnrollCounter).padStart(2, '0');
    const secret = `JBSWY3DPEHPK3PXP${suffix}SN`;
    const email = encodeURIComponent(this.authService.user()?.email ?? 'direction@ecole-demo.invalid');
    return of({
      secret,
      otpauthUri: `otpauth://totp/EcoleSaaS:${email}?secret=${secret}&issuer=EcoleSaaS&digits=6&period=30`,
      digits: 6,
      periodSeconds: 30,
    });
  }

  verifyMfa(code: string): Observable<MfaVerifyResult> {
    const trimmed = code.trim();
    if (!TOTP_CODE_PATTERN.test(trimmed)) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:invalid-mfa-code',
            title: 'Code MFA invalide',
            status: 422,
            detail: 'Veuillez saisir un code TOTP valide à 6 chiffres.',
            requestId: 'req-demo-mfa-422',
            errors: [{ field: 'code', message: 'Le code TOTP doit contenir exactement 6 chiffres.' }],
          })
      );
    }

    this.mfaEnabled = true;
    return of({ mfaEnabled: true });
  }

  updatePreferences(preferences: NotificationPreferences): Observable<NotificationPreferences> {
    this.preferences = { ...preferences };
    return of({ ...this.preferences });
  }
}
