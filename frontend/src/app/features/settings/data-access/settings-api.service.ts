import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, switchMap, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { API_BASE_URL } from '../../../core/config/api.config';
import {
  AuthResponse,
  AuthenticatedUserResponse,
  MfaEnrollResponse,
  MfaVerifyRequest,
} from '../../../core/models/auth.model';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  AcademicYearItem,
  AcademicYearResponseDto,
  ActivateAcademicYearRequestDto,
  BackendPageResponse,
  ContactPreferenceResponseDto,
  DEFAULT_NOTIFICATION_PREFERENCES,
  EvaluationPeriodItem,
  EvaluationPeriodResponseDto,
  MfaEnrollmentPayload,
  MfaVerifyResult,
  NotificationPreferences,
  SENSITIVE_MFA_ROLES,
  SchoolResponseDto,
  SchoolSettings,
  SecurityPolicySnapshot,
  SettingsSnapshot,
  UpdateAcademicSettingsInput,
  UpdateAcademicSettingsResult,
  UpdateAcademicYearRequestDto,
  UpdateMyPreferenceRequestDto,
  UpdateSchoolSettingsInput,
  UpdateTenantSchoolRequestDto,
  formatLocalTimeForInput,
  mapAcademicYearDtoToItem,
  mapEvaluationPeriodDtoToItem,
  mapSchoolDtoToSettings,
} from '../models/settings.model';
import { SETTINGS_DEMO_FALLBACK } from './settings-demo-fallback.token';

const BACKEND_UNAVAILABLE_STATUSES = new Set([0, 200, 404, 502, 503, 504]);

/**
 * Strictly typed HTTP client aligned with backend controllers:
 * - `TenantSchoolController` (`/api/v1/school`)
 * - `AcademicYearController` (`/api/v1/academic/years`)
 * - `EvaluationPeriodController` (`/api/v1/academic/years/{yearId}/periods`, `/api/v1/academic/periods/{periodId}/open`)
 * - `AuthController` (`/api/v1/auth/me`, `/api/v1/auth/mfa/enroll`, `/api/v1/auth/mfa/verify`)
 * - `ContactPreferenceController` (`/api/v1/communication/preferences/me`)
 *
 * Tenant isolation is enforced strictly server-side from the authenticated Bearer token;
 * no `tenantId` header, query param, or payload field is ever sent by the client.
 *
 * In local development with an active demo session and no running backend, delegates
 * transparently to the optional dev-only `SETTINGS_DEMO_FALLBACK` provider (stripped from
 * production builds via `app.dev-providers.prod.ts`).
 */
@Injectable({ providedIn: 'root' })
export class SettingsApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly authService = inject(AuthService);
  private readonly demoFallback = inject(SETTINGS_DEMO_FALLBACK, { optional: true });

  private localReceiptNotifications = DEFAULT_NOTIFICATION_PREFERENCES.receiptNotificationsEnabled;
  private localAbsenceNotifications = DEFAULT_NOTIFICATION_PREFERENCES.absenceNotificationsEnabled;

  private get baseUrl(): string {
    return this.apiBaseUrl.replace(/\/+$/, '');
  }

  private get schoolUrl(): string {
    return `${this.baseUrl}/school`;
  }

  private get academicYearsUrl(): string {
    return `${this.baseUrl}/academic/years`;
  }

  private get academicPeriodsUrl(): string {
    return `${this.baseUrl}/academic/periods`;
  }

  private get authBaseUrl(): string {
    return `${this.baseUrl}/auth`;
  }

  private get preferencesMeUrl(): string {
    return `${this.baseUrl}/communication/preferences/me`;
  }

  loadSettings(): Observable<SettingsSnapshot> {
    const yearsParams = new HttpParams()
      .set('page', '0')
      .set('size', '20')
      .set('sortBy', 'startDate')
      .set('direction', 'DESC');

    const school$ = this.http.get<SchoolResponseDto>(this.schoolUrl);
    const years$ = this.http.get<BackendPageResponse<AcademicYearResponseDto>>(
      this.academicYearsUrl,
      { params: yearsParams }
    );
    const me$ = this.http
      .get<AuthenticatedUserResponse>(`${this.authBaseUrl}/me`)
      .pipe(catchError(() => of<AuthenticatedUserResponse | null>(null)));
    const preferences$ = this.http
      .get<ContactPreferenceResponseDto>(this.preferencesMeUrl)
      .pipe(catchError(() => of<ContactPreferenceResponseDto | null>(null)));

    return forkJoin({
      school: school$,
      yearsPage: years$,
      me: me$,
      preferences: preferences$,
    }).pipe(
      switchMap(({ school, yearsPage, me, preferences }) => {
        const academicYears = yearsPage.content.map(mapAcademicYearDtoToItem);
        const currentYear =
          academicYears.find((item) => item.current) ??
          academicYears.find((item) => item.status === 'ACTIVE') ??
          academicYears[0] ??
          null;

        const periodsRequest$ = currentYear
          ? this.listPeriodsForYear(currentYear.id).pipe(
              catchError(() => of<readonly EvaluationPeriodItem[]>([]))
            )
          : of<readonly EvaluationPeriodItem[]>([]);

        return periodsRequest$.pipe(
          map((periods) => {
            const periodsByYearId: Record<string, readonly EvaluationPeriodItem[]> = {};
            if (currentYear) {
              periodsByYearId[currentYear.id] = periods;
            }

            return {
              school: mapSchoolDtoToSettings(school),
              academicYears,
              periodsByYearId,
              security: this.buildSecuritySnapshot(me),
              preferences: this.mapContactPreferenceDto(preferences),
            };
          })
        );
      }),
      catchError((error: unknown) => {
        if (this.shouldFallbackToDemo(error) && this.demoFallback) {
          return this.demoFallback.loadSettings();
        }
        return throwError(() => error);
      })
    );
  }

  listPeriodsForYear(yearId: string): Observable<readonly EvaluationPeriodItem[]> {
    const params = new HttpParams().set('page', '0').set('size', '20');
    return this.http
      .get<BackendPageResponse<EvaluationPeriodResponseDto>>(
        `${this.academicYearsUrl}/${encodeURIComponent(yearId)}/periods`,
        { params }
      )
      .pipe(
        map((response) =>
          [...response.content]
            .map(mapEvaluationPeriodDtoToItem)
            .sort((a, b) => a.sequenceOrder - b.sequenceOrder)
        )
      );
  }

  updateSchool(input: UpdateSchoolSettingsInput): Observable<SchoolSettings> {
    const payload: UpdateTenantSchoolRequestDto = {
      name: input.name.trim(),
      shortName: input.currentSchool.shortName || null,
      phone: input.phone.trim() || null,
      email: input.email.trim() || null,
      address: input.address.trim() || null,
      city: input.city.trim() || null,
      region: input.region.trim() || null,
      countryCode: input.currentSchool.countryCode || 'SN',
      motto: input.currentSchool.motto || null,
      ministryCode: input.currentSchool.ministryCode || null,
      website: input.currentSchool.website || null,
      timezone: input.currentSchool.timezone || 'Africa/Dakar',
      gradingPeriodSystem: input.currentSchool.gradingPeriodSystem,
      gradingScale: input.currentSchool.gradingScale,
    };

    return this.http.put<SchoolResponseDto>(this.schoolUrl, payload).pipe(
      map(mapSchoolDtoToSettings),
      catchError((error: unknown) => {
        if (this.shouldFallbackToDemo(error) && this.demoFallback) {
          return this.demoFallback.updateSchool(input);
        }
        return throwError(() => error);
      })
    );
  }

  updateAcademicSettings(
    input: UpdateAcademicSettingsInput
  ): Observable<UpdateAcademicSettingsResult> {
    const encodedYearId = encodeURIComponent(input.academicYearId);
    const updatePayload: UpdateAcademicYearRequestDto = {
      label: input.label.trim(),
      startDate: input.startDate,
      endDate: input.endDate,
      periodSystem: input.periodSystem,
    };

    const updateYear$ = this.http.put<AcademicYearResponseDto>(
      `${this.academicYearsUrl}/${encodedYearId}`,
      updatePayload
    );

    const activateOrSetCurrent$ = (yearDto: AcademicYearResponseDto): Observable<AcademicYearResponseDto> => {
      if (input.shouldActivateYear) {
        const body: ActivateAcademicYearRequestDto = { makeCurrent: input.setAsCurrent };
        return this.http.post<AcademicYearResponseDto>(
          `${this.academicYearsUrl}/${encodedYearId}/activate`,
          body
        );
      }
      if (input.setAsCurrent && !yearDto.current) {
        return this.http.post<AcademicYearResponseDto>(
          `${this.academicYearsUrl}/${encodedYearId}/set-current`,
          {}
        );
      }
      return of(yearDto);
    };

    return updateYear$.pipe(
      switchMap((updatedDto) => activateOrSetCurrent$(updatedDto)),
      switchMap((finalYearDto) => {
        const mappedYear = mapAcademicYearDtoToItem(finalYearDto);
        if (input.activePeriodId && input.shouldOpenPeriod) {
          return this.http
            .post<EvaluationPeriodResponseDto>(
              `${this.academicPeriodsUrl}/${encodeURIComponent(input.activePeriodId)}/open`,
              {}
            )
            .pipe(
              map((periodDto) => ({
                year: mappedYear,
                openedPeriod: mapEvaluationPeriodDtoToItem(periodDto),
              }))
            );
        }
        return of({ year: mappedYear, openedPeriod: null });
      }),
      catchError((error: unknown) => {
        if (this.shouldFallbackToDemo(error) && this.demoFallback) {
          return this.demoFallback.updateAcademicSettings(input);
        }
        return throwError(() => error);
      })
    );
  }

  enrollMfa(): Observable<MfaEnrollmentPayload> {
    return this.http.post<MfaEnrollResponse>(`${this.authBaseUrl}/mfa/enroll`, {}).pipe(
      map((dto) => ({
        secret: dto.secret,
        otpauthUri: dto.otpauthUri,
        digits: dto.digits,
        periodSeconds: dto.periodSeconds,
      })),
      catchError((error: unknown) => {
        if (this.shouldFallbackToDemo(error) && this.demoFallback) {
          return this.demoFallback.enrollMfa();
        }
        return throwError(() => error);
      })
    );
  }

  verifyMfa(code: string): Observable<MfaVerifyResult> {
    const payload: MfaVerifyRequest = { code: code.trim() };
    return this.http
      .post<AuthResponse>(`${this.authBaseUrl}/mfa/verify`, payload, { withCredentials: true })
      .pipe(
        map((response) => ({
          mfaEnabled: response.user?.mfaEnabled ?? true,
        })),
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.verifyMfa(code);
          }
          return throwError(() => error);
        })
      );
  }

  updatePreferences(preferences: NotificationPreferences): Observable<NotificationPreferences> {
    this.localReceiptNotifications = preferences.receiptNotificationsEnabled;
    this.localAbsenceNotifications = preferences.absenceNotificationsEnabled;

    const payload: UpdateMyPreferenceRequestDto = {
      inAppEnabled: preferences.inAppEnabled,
      smsEnabled: preferences.smsEnabled,
      whatsappEnabled: preferences.whatsappEnabled,
      emailEnabled: preferences.emailEnabled,
      marketingOptIn: false,
      preferredChannel: preferences.preferredChannel,
      preferredLanguage: preferences.preferredLanguage || 'fr',
      quietHoursStart: preferences.quietHoursStart || null,
      quietHoursEnd: preferences.quietHoursEnd || null,
    };

    return this.http.put<ContactPreferenceResponseDto>(this.preferencesMeUrl, payload).pipe(
      map((dto) => this.mapContactPreferenceDto(dto)),
      catchError((error: unknown) => {
        if (this.shouldFallbackToDemo(error) && this.demoFallback) {
          return this.demoFallback.updatePreferences(preferences);
        }
        return throwError(() => error);
      })
    );
  }

  private buildSecuritySnapshot(me: AuthenticatedUserResponse | null): SecurityPolicySnapshot {
    const sessionUser = this.authService.user();
    const roles = me?.roles?.length
      ? me.roles
      : sessionUser?.role
        ? [sessionUser.role]
        : ['DIRECTOR'];
    const fullName = me
      ? [me.firstName, me.lastName]
          .filter((part): part is string => typeof part === 'string' && part.trim().length > 0)
          .join(' ') || me.email
      : sessionUser?.fullName ?? 'Administrateur';

    const mfaRequiredByPolicy =
      me?.subjectType === 'PLATFORM_ADMIN' ||
      roles.some((role) => SENSITIVE_MFA_ROLES.has(role.trim().toUpperCase()));

    return {
      accountId: me?.id ?? sessionUser?.id ?? 'unknown',
      fullName,
      email: me?.email ?? sessionUser?.email ?? '—',
      roles,
      mfaEnabled: me?.mfaEnabled ?? false,
      mfaRequiredByPolicy,
      accessTokenMinutes: 15,
      refreshSessionDays: 7,
      mfaChallengeMinutes: 5,
      maxLoginFailures: 5,
      lockoutMinutes: 15,
    };
  }

  private mapContactPreferenceDto(
    dto: ContactPreferenceResponseDto | null
  ): NotificationPreferences {
    if (!dto) {
      return {
        ...DEFAULT_NOTIFICATION_PREFERENCES,
        receiptNotificationsEnabled: this.localReceiptNotifications,
        absenceNotificationsEnabled: this.localAbsenceNotifications,
      };
    }

    return {
      receiptNotificationsEnabled: this.localReceiptNotifications,
      absenceNotificationsEnabled: this.localAbsenceNotifications,
      inAppEnabled: dto.inAppEnabled,
      emailEnabled: dto.emailEnabled,
      smsEnabled: dto.smsEnabled,
      whatsappEnabled: dto.whatsappEnabled,
      preferredChannel: dto.preferredChannel ?? 'IN_APP',
      preferredLanguage: dto.preferredLanguage?.trim() || 'fr',
      quietHoursStart: formatLocalTimeForInput(
        dto.quietHoursStart,
        DEFAULT_NOTIFICATION_PREFERENCES.quietHoursStart
      ),
      quietHoursEnd: formatLocalTimeForInput(
        dto.quietHoursEnd,
        DEFAULT_NOTIFICATION_PREFERENCES.quietHoursEnd
      ),
    };
  }

  private shouldFallbackToDemo(error: unknown): boolean {
    if (!this.demoFallback?.isDemoSessionActive()) {
      return false;
    }

    if (error instanceof ProblemDetailError) {
      if (error.problem.requestId) {
        return false;
      }
      return BACKEND_UNAVAILABLE_STATUSES.has(error.problem.status);
    }

    if (error instanceof HttpErrorResponse) {
      if (error.headers?.get('X-Request-Id')) {
        return false;
      }
      return BACKEND_UNAVAILABLE_STATUSES.has(error.status);
    }

    return false;
  }
}
