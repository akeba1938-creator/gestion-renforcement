import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { problemDetailInterceptor } from '../../../core/interceptors/problem-detail.interceptor';
import { AuthenticatedUserResponse, MfaEnrollResponse } from '../../../core/models/auth.model';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  AcademicYearResponseDto,
  BackendPageResponse,
  ContactPreferenceResponseDto,
  EvaluationPeriodResponseDto,
  SchoolResponseDto,
  mapSchoolDtoToSettings,
} from '../models/settings.model';
import { SettingsApiService } from './settings-api.service';
import { SETTINGS_DEMO_FALLBACK } from './settings-demo-fallback.token';
import { SettingsDevFallbackService } from './settings-dev-fallback.service';

const SAMPLE_SCHOOL_DTO: SchoolResponseDto = {
  id: '11111111-1111-4111-8111-111111111111',
  code: 'LYC-DKR-01',
  name: 'Lycée d’Excellence de Dakar',
  shortName: 'LED',
  schoolType: 'SECONDARY',
  status: 'ACTIVE',
  phone: '+221338214590',
  email: 'direction@lycee-dakar.sn',
  address: 'Avenue Bourguiba, Point E',
  city: 'Dakar',
  region: 'Dakar',
  countryCode: 'SN',
  motto: 'Savoir et Rigueur',
  ministryCode: 'MEN-2026-001',
  website: 'https://lycee-dakar.sn',
  timezone: 'Africa/Dakar',
  currency: 'XOF',
  gradingPeriodSystem: 'TRIMESTER',
  gradingScale: 20,
  statusReason: null,
  suspendedAt: null,
  archivedAt: null,
  createdAt: '2025-09-01T08:00:00Z',
  updatedAt: '2026-10-12T10:15:00Z',
};

const SAMPLE_YEAR_ACTIVE_DTO: AcademicYearResponseDto = {
  id: '22222222-2222-4222-8222-222222222221',
  code: '2025-2026',
  label: 'Année scolaire 2025-2026',
  startDate: '2025-10-01',
  endDate: '2026-07-31',
  periodSystem: 'TRIMESTER',
  status: 'ACTIVE',
  current: true,
  closedAt: null,
  archivedAt: null,
  createdAt: '2025-09-01T08:00:00Z',
  updatedAt: '2025-09-10T08:00:00Z',
};

const SAMPLE_YEAR_CLOSED_DTO: AcademicYearResponseDto = {
  id: '22222222-2222-4222-8222-222222222222',
  code: '2024-2025',
  label: 'Année scolaire 2024-2025',
  startDate: '2024-10-02',
  endDate: '2025-07-30',
  periodSystem: 'TRIMESTER',
  status: 'CLOSED',
  current: false,
  closedAt: '2025-08-01T12:00:00Z',
  archivedAt: null,
  createdAt: '2024-09-01T08:00:00Z',
  updatedAt: '2025-08-01T12:00:00Z',
};

const SAMPLE_PERIODS_DTO: readonly EvaluationPeriodResponseDto[] = [
  {
    id: '33333333-3333-4333-8333-333333333331',
    academicYearId: SAMPLE_YEAR_ACTIVE_DTO.id,
    code: 'T1',
    name: 'Trimestre 1',
    periodType: 'TRIMESTER',
    sequenceOrder: 1,
    startDate: '2025-10-01',
    endDate: '2025-12-20',
    weight: 1,
    status: 'CLOSED',
    lockedAt: null,
    createdAt: '2025-09-01T08:00:00Z',
    updatedAt: '2025-12-21T08:00:00Z',
  },
  {
    id: '33333333-3333-4333-8333-333333333332',
    academicYearId: SAMPLE_YEAR_ACTIVE_DTO.id,
    code: 'T2',
    name: 'Trimestre 2',
    periodType: 'TRIMESTER',
    sequenceOrder: 2,
    startDate: '2026-01-05',
    endDate: '2026-03-31',
    weight: 1,
    status: 'OPEN',
    lockedAt: null,
    createdAt: '2025-09-01T08:00:00Z',
    updatedAt: '2026-01-05T08:00:00Z',
  },
];

const SAMPLE_ME_DTO: AuthenticatedUserResponse = {
  id: '44444444-4444-4444-8444-444444444444',
  subjectType: 'USER',
  email: 'directeur@lycee-dakar.sn',
  firstName: 'Mamadou',
  lastName: 'Diop',
  roles: ['DIRECTOR'],
  mfaEnabled: true,
  tenantId: SAMPLE_SCHOOL_DTO.id,
  schoolCode: SAMPLE_SCHOOL_DTO.code,
  schoolName: SAMPLE_SCHOOL_DTO.name,
};

const SAMPLE_PREF_DTO: ContactPreferenceResponseDto = {
  id: '55555555-5555-4555-8555-555555555555',
  contactType: 'USER',
  contactId: SAMPLE_ME_DTO.id,
  inAppEnabled: true,
  smsEnabled: true,
  whatsappEnabled: false,
  emailEnabled: true,
  marketingOptIn: false,
  preferredChannel: 'EMAIL',
  preferredLanguage: 'fr',
  quietHoursStart: '21:00:00',
  quietHoursEnd: '07:00:00',
  consentSource: 'SELF_SERVICE',
  consentRecordedAt: '2026-09-01T08:00:00Z',
  customized: true,
};

describe('SettingsApiService', () => {
  describe('production mode (no dev fallback provider)', () => {
    let service: SettingsApiService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(withInterceptors([problemDetailInterceptor])),
          provideHttpClientTesting(),
        ],
      });
      service = TestBed.inject(SettingsApiService);
      httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
      httpMock.verify();
    });

    it('loads school, academic years, periods, auth/me, and preferences with zero tenantId', async () => {
      const promise = firstValueFrom(service.loadSettings());

      const schoolReq = httpMock.expectOne('/api/v1/school');
      expect(schoolReq.request.method).toBe('GET');
      expect(schoolReq.request.headers.has('X-Tenant-Id')).toBe(false);
      expect(schoolReq.request.params.has('tenantId')).toBe(false);

      const yearsReq = httpMock.expectOne((req) => req.url === '/api/v1/academic/years');
      expect(yearsReq.request.method).toBe('GET');
      expect(yearsReq.request.params.get('sortBy')).toBe('startDate');
      expect(yearsReq.request.params.get('direction')).toBe('DESC');
      expect(yearsReq.request.params.has('tenantId')).toBe(false);

      const meReq = httpMock.expectOne('/api/v1/auth/me');
      expect(meReq.request.method).toBe('GET');

      const prefReq = httpMock.expectOne('/api/v1/communication/preferences/me');
      expect(prefReq.request.method).toBe('GET');

      const yearsPage: BackendPageResponse<AcademicYearResponseDto> = {
        content: [SAMPLE_YEAR_ACTIVE_DTO, SAMPLE_YEAR_CLOSED_DTO],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
        first: true,
        last: true,
      };

      schoolReq.flush(SAMPLE_SCHOOL_DTO);
      yearsReq.flush(yearsPage);
      meReq.flush(SAMPLE_ME_DTO);
      prefReq.flush(SAMPLE_PREF_DTO);

      const periodsReq = httpMock.expectOne(
        (req) => req.url === `/api/v1/academic/years/${SAMPLE_YEAR_ACTIVE_DTO.id}/periods`
      );
      expect(periodsReq.request.method).toBe('GET');
      expect(periodsReq.request.params.has('tenantId')).toBe(false);
      periodsReq.flush({
        content: SAMPLE_PERIODS_DTO,
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
        first: true,
        last: true,
      });

      const snapshot = await promise;
      expect(snapshot.school?.name).toBe('Lycée d’Excellence de Dakar');
      expect(snapshot.school?.code).toBe('LYC-DKR-01');
      expect(snapshot.school?.currency).toBe('FCFA');
      expect(snapshot.school?.timezone).toBe('Africa/Dakar');
      expect(snapshot.academicYears.length).toBe(2);
      expect(snapshot.periodsByYearId[SAMPLE_YEAR_ACTIVE_DTO.id]?.length).toBe(2);
      expect(snapshot.security.mfaEnabled).toBe(true);
      expect(snapshot.security.mfaRequiredByPolicy).toBe(true);
      expect(snapshot.security.accessTokenMinutes).toBe(15);
      expect(snapshot.security.refreshSessionDays).toBe(7);
      expect(snapshot.preferences.preferredChannel).toBe('EMAIL');
      expect(snapshot.preferences.quietHoursStart).toBe('21:00');
    });

    it('updates school via PUT /api/v1/school without sending tenantId', async () => {
      const currentSchool = mapSchoolDtoToSettings(SAMPLE_SCHOOL_DTO);
      const promise = firstValueFrom(
        service.updateSchool({
          name: 'Lycée Moderne de Dakar',
          address: 'Sacré-Cœur 3',
          city: 'Dakar',
          region: 'Dakar',
          phone: '+221771234567',
          email: 'contact@lycee-dakar.sn',
          currentSchool,
        })
      );

      const req = httpMock.expectOne('/api/v1/school');
      expect(req.request.method).toBe('PUT');
      expect(req.request.headers.has('X-Tenant-Id')).toBe(false);
      expect('tenantId' in (req.request.body as Record<string, unknown>)).toBe(false);
      expect(req.request.body).toEqual(
        expect.objectContaining({
          name: 'Lycée Moderne de Dakar',
          phone: '+221771234567',
          email: 'contact@lycee-dakar.sn',
          address: 'Sacré-Cœur 3',
          timezone: 'Africa/Dakar',
        })
      );

      req.flush({
        ...SAMPLE_SCHOOL_DTO,
        name: 'Lycée Moderne de Dakar',
        phone: '+221771234567',
      });

      const updated = await promise;
      expect(updated.name).toBe('Lycée Moderne de Dakar');
      expect(updated.phone).toBe('+221771234567');
    });

    it('updates academic year, sets current, and opens planned period sequentially', async () => {
      const plannedPeriodId = '33333333-3333-4333-8333-333333333333';
      const promise = firstValueFrom(
        service.updateAcademicSettings({
          academicYearId: SAMPLE_YEAR_CLOSED_DTO.id,
          label: SAMPLE_YEAR_CLOSED_DTO.label,
          startDate: '2024-10-01',
          endDate: '2025-07-31',
          periodSystem: 'TRIMESTER',
          setAsCurrent: true,
          shouldActivateYear: false,
          activePeriodId: plannedPeriodId,
          shouldOpenPeriod: true,
        })
      );

      const updateReq = httpMock.expectOne(
        `/api/v1/academic/years/${SAMPLE_YEAR_CLOSED_DTO.id}`
      );
      expect(updateReq.request.method).toBe('PUT');
      expect(updateReq.request.body).toEqual({
        label: SAMPLE_YEAR_CLOSED_DTO.label,
        startDate: '2024-10-01',
        endDate: '2025-07-31',
        periodSystem: 'TRIMESTER',
      });
      updateReq.flush({ ...SAMPLE_YEAR_CLOSED_DTO, current: false });

      const currentReq = httpMock.expectOne(
        `/api/v1/academic/years/${SAMPLE_YEAR_CLOSED_DTO.id}/set-current`
      );
      expect(currentReq.request.method).toBe('POST');
      currentReq.flush({ ...SAMPLE_YEAR_CLOSED_DTO, current: true, status: 'ACTIVE' });

      const openReq = httpMock.expectOne(`/api/v1/academic/periods/${plannedPeriodId}/open`);
      expect(openReq.request.method).toBe('POST');
      openReq.flush({
        ...SAMPLE_PERIODS_DTO[1],
        id: plannedPeriodId,
        name: 'Trimestre 3',
        status: 'OPEN',
      });

      const result = await promise;
      expect(result.year.current).toBe(true);
      expect(result.openedPeriod?.id).toBe(plannedPeriodId);
      expect(result.openedPeriod?.status).toBe('OPEN');
    });

    it('enrolls and verifies MFA via /api/v1/auth/mfa/enroll and /verify', async () => {
      const enrollPromise = firstValueFrom(service.enrollMfa());
      const enrollReq = httpMock.expectOne('/api/v1/auth/mfa/enroll');
      expect(enrollReq.request.method).toBe('POST');
      const enrollResponse: MfaEnrollResponse = {
        secret: 'JBSWY3DPEHPK3PXP',
        otpauthUri: 'otpauth://totp/EcoleSaaS:directeur@lycee-dakar.sn?secret=JBSWY3DPEHPK3PXP',
        digits: 6,
        periodSeconds: 30,
      };
      enrollReq.flush(enrollResponse);

      const enrolled = await enrollPromise;
      expect(enrolled.secret).toBe('JBSWY3DPEHPK3PXP');
      expect(enrolled.digits).toBe(6);

      const verifyPromise = firstValueFrom(service.verifyMfa('654321'));
      const verifyReq = httpMock.expectOne('/api/v1/auth/mfa/verify');
      expect(verifyReq.request.method).toBe('POST');
      expect(verifyReq.request.withCredentials).toBe(true);
      expect(verifyReq.request.body).toEqual({ code: '654321' });
      verifyReq.flush({
        status: 'AUTHENTICATED',
        accessToken: 'jwt-new',
        mfaToken: null,
        expiresInSeconds: 900,
        mfaRequired: false,
        mfaEnrollmentRequired: false,
        user: SAMPLE_ME_DTO,
      });

      const verified = await verifyPromise;
      expect(verified.mfaEnabled).toBe(true);
    });

    it('updates notification preferences via PUT /api/v1/communication/preferences/me', async () => {
      const promise = firstValueFrom(
        service.updatePreferences({
          receiptNotificationsEnabled: false,
          absenceNotificationsEnabled: true,
          inAppEnabled: true,
          emailEnabled: false,
          smsEnabled: true,
          whatsappEnabled: true,
          preferredChannel: 'SMS',
          preferredLanguage: 'fr',
          quietHoursStart: '22:00',
          quietHoursEnd: '06:30',
        })
      );

      const req = httpMock.expectOne('/api/v1/communication/preferences/me');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({
        inAppEnabled: true,
        smsEnabled: true,
        whatsappEnabled: true,
        emailEnabled: false,
        marketingOptIn: false,
        preferredChannel: 'SMS',
        preferredLanguage: 'fr',
        quietHoursStart: '22:00',
        quietHoursEnd: '06:30',
      });
      req.flush({
        ...SAMPLE_PREF_DTO,
        emailEnabled: false,
        whatsappEnabled: true,
        preferredChannel: 'SMS',
      });

      const updated = await promise;
      expect(updated.preferredChannel).toBe('SMS');
      expect(updated.receiptNotificationsEnabled).toBe(false);
      expect(updated.absenceNotificationsEnabled).toBe(true);
    });

    it('propagates RFC 9457 ProblemDetailError in production mode', async () => {
      const promise = firstValueFrom(service.loadSettings());

      const schoolReq = httpMock.expectOne('/api/v1/school');
      const yearsReq = httpMock.expectOne((req) => req.url === '/api/v1/academic/years');
      const meReq = httpMock.expectOne('/api/v1/auth/me');
      const prefReq = httpMock.expectOne('/api/v1/communication/preferences/me');

      schoolReq.flush(
        {
          type: 'urn:ecole:problem:service-unavailable',
          title: 'Service établissement indisponible',
          status: 503,
          detail: 'Impossible de charger la configuration de l’établissement.',
          requestId: 'req-settings-503',
        },
        { status: 503, statusText: 'Service Unavailable' }
      );
      if (!yearsReq.cancelled) {
        yearsReq.flush({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        });
      }
      if (!meReq.cancelled) {
        meReq.flush(SAMPLE_ME_DTO);
      }
      if (!prefReq.cancelled) {
        prefReq.flush(SAMPLE_PREF_DTO);
      }

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
      try {
        await promise;
      } catch (err) {
        const problem = (err as ProblemDetailError).problem;
        expect(problem.status).toBe(503);
        expect(problem.requestId).toBe('req-settings-503');
      }
    });
  });

  describe('development fallback guard', () => {
    let service: SettingsApiService;
    let httpMock: HttpTestingController;
    let authService: AuthService;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(withInterceptors([problemDetailInterceptor])),
          provideHttpClientTesting(),
          { provide: SETTINGS_DEMO_FALLBACK, useExisting: SettingsDevFallbackService },
        ],
      });
      service = TestBed.inject(SettingsApiService);
      httpMock = TestBed.inject(HttpTestingController);
      authService = TestBed.inject(AuthService);
    });

    afterEach(() => {
      authService.clearSession();
      httpMock.verify();
    });

    it('falls back to demo dataset ONLY when dev demo session is active and backend is unreachable', async () => {
      authService.setSession('demo-memory-token', {
        id: 'usr-demo',
        fullName: 'Direction Démo',
        email: 'direction@ecole-demo.invalid',
        role: 'DIRECTOR',
        tenantId: 'tenant-demo-01',
        tenantName: 'Groupe Scolaire Démo',
      });

      const promise = firstValueFrom(service.loadSettings());

      const schoolReq = httpMock.expectOne('/api/v1/school');
      const yearsReq = httpMock.expectOne((req) => req.url === '/api/v1/academic/years');
      const meReq = httpMock.expectOne('/api/v1/auth/me');
      const prefReq = httpMock.expectOne('/api/v1/communication/preferences/me');

      schoolReq.error(new ProgressEvent('error'));
      if (!yearsReq.cancelled) {
        yearsReq.error(new ProgressEvent('error'));
      }
      if (!meReq.cancelled) {
        meReq.error(new ProgressEvent('error'));
      }
      if (!prefReq.cancelled) {
        prefReq.error(new ProgressEvent('error'));
      }

      const snapshot = await promise;
      expect(snapshot.school?.code).toBe('ECOLE-DKR-001');
      expect(snapshot.school?.currency).toBe('FCFA');
      expect(snapshot.school?.timezone).toBe('Africa/Dakar');
      expect(snapshot.academicYears.length).toBeGreaterThan(0);
    });

    it('does NOT fall back when authenticated with a real JWT token', async () => {
      authService.setSession('real-jwt-access-token', {
        id: 'usr-real',
        fullName: 'Directeur Réel',
        email: 'directeur@lycee.sn',
        role: 'DIRECTOR',
        tenantId: 'tenant-01',
        tenantName: 'Lycée Test',
      });

      const promise = firstValueFrom(service.loadSettings());
      const schoolReq = httpMock.expectOne('/api/v1/school');
      const yearsReq = httpMock.expectOne((req) => req.url === '/api/v1/academic/years');
      const meReq = httpMock.expectOne('/api/v1/auth/me');
      const prefReq = httpMock.expectOne('/api/v1/communication/preferences/me');

      schoolReq.error(new ProgressEvent('error'));
      if (!yearsReq.cancelled) {
        yearsReq.error(new ProgressEvent('error'));
      }
      if (!meReq.cancelled) {
        meReq.error(new ProgressEvent('error'));
      }
      if (!prefReq.cancelled) {
        prefReq.error(new ProgressEvent('error'));
      }

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
    });

    it('does NOT swallow structured RFC 9457 errors with requestId even in demo session', async () => {
      authService.setSession('demo-memory-token', {
        id: 'usr-demo',
        fullName: 'Direction Démo',
        email: 'direction@ecole-demo.invalid',
        role: 'DIRECTOR',
        tenantId: 'tenant-demo-01',
        tenantName: 'Groupe Scolaire Démo',
      });

      const currentSchool = mapSchoolDtoToSettings(SAMPLE_SCHOOL_DTO);
      const promise = firstValueFrom(
        service.updateSchool({
          name: 'Test Conflit',
          address: 'Dakar',
          city: 'Dakar',
          region: 'Dakar',
          phone: '+221338214590',
          email: 'direction@ecole-demo.invalid',
          currentSchool,
        })
      );

      const req = httpMock.expectOne('/api/v1/school');
      req.flush(
        {
          type: 'urn:ecole:problem:validation-error',
          title: 'Erreur de validation',
          status: 422,
          detail: 'Le nom contient des caractères non autorisés.',
          requestId: 'req-demo-conflict-422',
        },
        { status: 422, statusText: 'Unprocessable Entity' }
      );

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
    });
  });
});
