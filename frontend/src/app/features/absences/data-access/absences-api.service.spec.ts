import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { problemDetailInterceptor } from '../../../core/interceptors/problem-detail.interceptor';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  AttendanceRecordResponseDto,
  AttendanceReportViewDto,
  AttendanceSessionResponseDto,
  BackendPageResponse,
  ClassRoomResponseDto,
} from '../models/absences.model';
import { AbsencesApiService } from './absences-api.service';
import { ABSENCES_DEMO_FALLBACK } from './absences-demo-fallback.token';
import { AbsencesDevFallbackService } from './absences-dev-fallback.service';

const SAMPLE_SESSION_DTO: AttendanceSessionResponseDto = {
  id: '11111111-1111-4111-8111-111111111111',
  academicYearId: '22222222-2222-4222-8222-222222222222',
  evaluationPeriodId: null,
  periodCode: 'T1',
  classRoomId: '33333333-3333-4333-8333-333333333333',
  classRoomCode: 'TS2',
  classRoomName: 'Terminale S2',
  subjectId: null,
  subjectCode: 'MATH',
  subjectName: 'Mathématiques',
  teacherId: '44444444-4444-4444-8444-444444444444',
  teacherFullName: 'M. Ousmane Dieng',
  sessionDate: '2026-11-26',
  timeSlot: 'MORNING',
  startTime: '08:00:00',
  endTime: '12:00:00',
  label: 'Appel Terminale S2',
  status: 'SUBMITTED',
  recordedBy: '44444444-4444-4444-8444-444444444444',
  submittedAt: '2026-11-26T08:20:00Z',
  lockedBy: null,
  lockedAt: null,
  notes: null,
  createdAt: '2026-11-26T08:10:00Z',
  updatedAt: '2026-11-26T08:20:00Z',
  records: [
    {
      id: '55555555-5555-4555-8555-555555555551',
      sessionId: '11111111-1111-4111-8111-111111111111',
      studentId: '66666666-6666-4666-8666-666666666661',
      studentMatricule: '2026-0001',
      studentFirstName: 'Aminata',
      studentLastName: 'Diallo',
      status: 'PRESENT',
      lateMinutes: null,
      justified: false,
      justificationReason: null,
      justificationDocumentRef: null,
      justifiedBy: null,
      justifiedAt: null,
      notes: null,
      recordedBy: '44444444-4444-4444-8444-444444444444',
      updatedBy: null,
      createdAt: '2026-11-26T08:15:00Z',
      updatedAt: '2026-11-26T08:15:00Z',
    },
    {
      id: '55555555-5555-4555-8555-555555555552',
      sessionId: '11111111-1111-4111-8111-111111111111',
      studentId: '66666666-6666-4666-8666-666666666662',
      studentMatricule: '2026-0007',
      studentFirstName: 'Khady',
      studentLastName: 'Sall',
      status: 'ABSENT',
      lateMinutes: null,
      justified: false,
      justificationReason: null,
      justificationDocumentRef: null,
      justifiedBy: null,
      justifiedAt: null,
      notes: 'Non signalée',
      recordedBy: '44444444-4444-4444-8444-444444444444',
      updatedBy: null,
      createdAt: '2026-11-26T08:15:00Z',
      updatedAt: '2026-11-26T08:15:00Z',
    },
    {
      id: '55555555-5555-4555-8555-555555555553',
      sessionId: '11111111-1111-4111-8111-111111111111',
      studentId: '66666666-6666-4666-8666-666666666663',
      studentMatricule: '2026-0013',
      studentFirstName: 'Ibrahima',
      studentLastName: 'Kane',
      status: 'LATE',
      lateMinutes: 15,
      justified: false,
      justificationReason: null,
      justificationDocumentRef: null,
      justifiedBy: null,
      justifiedAt: null,
      notes: 'Retard transport',
      recordedBy: '44444444-4444-4444-8444-444444444444',
      updatedBy: null,
      createdAt: '2026-11-26T08:20:00Z',
      updatedAt: '2026-11-26T08:20:00Z',
    },
  ],
};

const SAMPLE_REPORT_DTO: AttendanceReportViewDto = {
  schoolCode: 'LYC-DKR',
  schoolName: 'Lycée Démo',
  fromDate: '2026-11-26',
  toDate: '2026-11-26',
  sessionCount: 6,
  totalRecords: 180,
  presentCount: 168,
  lateCount: 4,
  totalLateMinutes: 55,
  absentCount: 8,
  justifiedAbsenceCount: 3,
  unjustifiedAbsenceCount: 5,
  excusedCount: 0,
  attendanceRatePercent: 95.6,
  classes: [],
  generatedAt: '2026-11-26T10:00:00Z',
};

const SAMPLE_CLASS_DTO: ClassRoomResponseDto = {
  id: '33333333-3333-4333-8333-333333333333',
  code: 'TS2',
  name: 'Terminale S2',
  gradeLevelName: 'Terminale',
  status: 'ACTIVE',
};

describe('AbsencesApiService', () => {
  describe('production mode (no dev fallback provider)', () => {
    let service: AbsencesApiService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(withInterceptors([problemDetailInterceptor])),
          provideHttpClientTesting(),
        ],
      });
      service = TestBed.inject(AbsencesApiService);
      httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
      httpMock.verify();
    });

    it('requests GET /api/v1/attendance/sessions, reporting/attendance, and academic/classes with exact params and zero tenantId', async () => {
      const promise = firstValueFrom(
        service.list({
          page: 0,
          size: 8,
          classRoomId: SAMPLE_CLASS_DTO.id,
          date: '2026-11-26',
          status: 'ALL',
        })
      );

      const sessionsReq = httpMock.expectOne((req) => req.url === '/api/v1/attendance/sessions');
      expect(sessionsReq.request.method).toBe('GET');
      expect(sessionsReq.request.params.get('classRoomId')).toBe(SAMPLE_CLASS_DTO.id);
      expect(sessionsReq.request.params.get('fromDate')).toBe('2026-11-26');
      expect(sessionsReq.request.params.get('toDate')).toBe('2026-11-26');
      expect(sessionsReq.request.params.has('tenantId')).toBe(false);
      expect(sessionsReq.request.headers.has('X-Tenant-Id')).toBe(false);

      const reportReq = httpMock.expectOne((req) => req.url === '/api/v1/reporting/attendance');
      expect(reportReq.request.method).toBe('GET');
      expect(reportReq.request.params.get('classRoomId')).toBe(SAMPLE_CLASS_DTO.id);
      expect(reportReq.request.params.get('fromDate')).toBe('2026-11-26');
      expect(reportReq.request.params.get('toDate')).toBe('2026-11-26');
      expect(reportReq.request.params.has('tenantId')).toBe(false);

      const classesReq = httpMock.expectOne((req) => req.url === '/api/v1/academic/classes');
      expect(classesReq.request.method).toBe('GET');
      expect(classesReq.request.params.has('tenantId')).toBe(false);

      const sessionsPage: BackendPageResponse<AttendanceSessionResponseDto> = {
        content: [SAMPLE_SESSION_DTO],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      };
      const classesPage: BackendPageResponse<ClassRoomResponseDto> = {
        content: [SAMPLE_CLASS_DTO],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      };

      sessionsReq.flush(sessionsPage);
      reportReq.flush(SAMPLE_REPORT_DTO);
      classesReq.flush(classesPage);

      const result = await promise;
      // PRESENT record is excluded from absences/tardiness register; ABSENT and LATE remain
      expect(result.content.length).toBe(2);
      expect(result.content.some((r) => r.studentName === 'Khady Sall' && r.status === 'UNJUSTIFIED')).toBe(true);
      expect(result.content.some((r) => r.studentName === 'Ibrahima Kane' && r.status === 'LATE' && r.lateMinutes === 15)).toBe(true);
      expect(result.kpis.attendanceRatePercent).toBe(95.6);
      expect(result.kpis.unjustifiedCount).toBe(5);
      expect(result.kpis.lateCount).toBe(4);
      expect(result.availableClasses.length).toBe(1);
      expect(result.rollCallRosterByClass[SAMPLE_CLASS_DTO.id]?.length).toBe(3);
    });

    it('creates session, upserts records, and submits roll call sequentially via AttendanceController endpoints', async () => {
      const promise = firstValueFrom(
        service.submitRollCall({
          classRoomId: SAMPLE_CLASS_DTO.id,
          className: 'Terminale S2',
          sessionDate: '2026-11-26',
          timeSlot: 'MORNING',
          notes: 'Appel du matin',
          entries: [
            { studentId: '66666666-6666-4666-8666-666666666661', status: 'PRESENT' },
            { studentId: '66666666-6666-4666-8666-666666666662', status: 'ABSENT' },
            { studentId: '66666666-6666-4666-8666-666666666663', status: 'LATE', lateMinutes: 15 },
          ],
        })
      );

      const createReq = httpMock.expectOne('/api/v1/attendance/sessions');
      expect(createReq.request.method).toBe('POST');
      expect(createReq.request.headers.has('X-Tenant-Id')).toBe(false);
      expect(createReq.request.body).toEqual({
        classRoomId: SAMPLE_CLASS_DTO.id,
        sessionDate: '2026-11-26',
        timeSlot: 'MORNING',
        label: 'Appel Terminale S2',
        notes: 'Appel du matin',
        initializePresent: true,
      });
      createReq.flush(SAMPLE_SESSION_DTO, { status: 201, statusText: 'Created' });

      const upsertReq = httpMock.expectOne(
        `/api/v1/attendance/sessions/${SAMPLE_SESSION_DTO.id}/records`
      );
      expect(upsertReq.request.method).toBe('PUT');
      expect(upsertReq.request.body).toEqual({
        records: [
          {
            studentId: '66666666-6666-4666-8666-666666666661',
            status: 'PRESENT',
            lateMinutes: undefined,
            notes: undefined,
          },
          {
            studentId: '66666666-6666-4666-8666-666666666662',
            status: 'ABSENT',
            lateMinutes: undefined,
            notes: undefined,
          },
          {
            studentId: '66666666-6666-4666-8666-666666666663',
            status: 'LATE',
            lateMinutes: 15,
            notes: undefined,
          },
        ],
      });
      upsertReq.flush(SAMPLE_SESSION_DTO);

      const submitReq = httpMock.expectOne(
        `/api/v1/attendance/sessions/${SAMPLE_SESSION_DTO.id}/submit`
      );
      expect(submitReq.request.method).toBe('POST');
      submitReq.flush(SAMPLE_SESSION_DTO);

      const result = await promise;
      expect(result.sessionId).toBe(SAMPLE_SESSION_DTO.id);
      expect(result.presentCount).toBe(1);
      expect(result.absentCount).toBe(1);
      expect(result.lateCount).toBe(1);
    });

    it('justifies an absence via POST /api/v1/attendance/records/{recordId}/justify', async () => {
      const recordId = '55555555-5555-4555-8555-555555555552';
      const promise = firstValueFrom(
        service.justifyRecord({
          recordId,
          reason: 'Certificat médical reçu',
          documentRef: 'CERT-2026-099',
        })
      );

      const req = httpMock.expectOne(`/api/v1/attendance/records/${recordId}/justify`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        reason: 'Certificat médical reçu',
        documentRef: 'CERT-2026-099',
      });

      const responseDto: AttendanceRecordResponseDto = {
        ...SAMPLE_SESSION_DTO.records[1],
        justified: true,
        justificationReason: 'Certificat médical reçu',
        justificationDocumentRef: 'CERT-2026-099',
        justifiedAt: '2026-11-26T10:30:00Z',
      };
      req.flush(responseDto);

      const updated = await promise;
      expect(updated.justified).toBe(true);
      expect(updated.status).toBe('JUSTIFIED');
      expect(updated.justificationDocumentRef).toBe('CERT-2026-099');
    });

    it('propagates RFC 9457 ProblemDetailError when backend fails in production mode', async () => {
      const promise = firstValueFrom(service.list({ page: 0, size: 8 }));

      const sessionsReq = httpMock.expectOne((req) => req.url === '/api/v1/attendance/sessions');
      const reportReq = httpMock.expectOne((req) => req.url === '/api/v1/reporting/attendance');
      const classesReq = httpMock.expectOne((req) => req.url === '/api/v1/academic/classes');

      sessionsReq.flush(
        {
          type: 'urn:ecole:problem:service-unavailable',
          title: 'Service absences indisponible',
          status: 503,
          detail: 'Le registre des absences est momentanément indisponible.',
          requestId: 'req-abs-prod-503',
        },
        { status: 503, statusText: 'Service Unavailable' }
      );
      if (!reportReq.cancelled) {
        reportReq.flush(SAMPLE_REPORT_DTO);
      }
      if (!classesReq.cancelled) {
        classesReq.flush({
          content: [],
          page: 0,
          size: 100,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        });
      }

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
      try {
        await promise;
      } catch (err) {
        const problem = (err as ProblemDetailError).problem;
        expect(problem.status).toBe(503);
        expect(problem.requestId).toBe('req-abs-prod-503');
      }
    });
  });

  describe('development fallback guard', () => {
    let service: AbsencesApiService;
    let httpMock: HttpTestingController;
    let authService: AuthService;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(withInterceptors([problemDetailInterceptor])),
          provideHttpClientTesting(),
          { provide: ABSENCES_DEMO_FALLBACK, useExisting: AbsencesDevFallbackService },
        ],
      });
      service = TestBed.inject(AbsencesApiService);
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

      const listPromise = firstValueFrom(service.list({ page: 0, size: 8 }));
      const sessionsReq = httpMock.expectOne((req) => req.url === '/api/v1/attendance/sessions');
      const reportReq = httpMock.expectOne((req) => req.url === '/api/v1/reporting/attendance');
      const classesReq = httpMock.expectOne((req) => req.url === '/api/v1/academic/classes');

      sessionsReq.error(new ProgressEvent('error'));
      if (!reportReq.cancelled) {
        reportReq.error(new ProgressEvent('error'));
      }
      if (!classesReq.cancelled) {
        classesReq.error(new ProgressEvent('error'));
      }

      const result = await listPromise;
      expect(result.totalElements).toBeGreaterThan(0);
      expect(result.availableClasses.length).toBeGreaterThan(0);

      // Justifying a record in dev fallback updates it in memory
      const unjustified = result.content.find((item) => item.status === 'UNJUSTIFIED');
      expect(unjustified).toBeDefined();

      const justifyPromise = firstValueFrom(
        service.justifyRecord({
          recordId: unjustified!.id,
          reason: 'Motif médical vérifié',
          documentRef: 'CERT-DEMO-01',
        })
      );
      const justifyReq = httpMock.expectOne(
        `/api/v1/attendance/records/${encodeURIComponent(unjustified!.id)}/justify`
      );
      justifyReq.flush('Not Found', { status: 404, statusText: 'Not Found' });

      const updated = await justifyPromise;
      expect(updated.justified).toBe(true);
      expect(updated.status).toBe('JUSTIFIED');
    });

    it('does NOT fall back when authenticated with a real JWT token', async () => {
      authService.setSession('real-jwt-access-token', {
        id: 'usr-real',
        fullName: 'Enseignant Réel',
        email: 'prof@lycee.sn',
        role: 'TEACHER',
        tenantId: 'tenant-01',
        tenantName: 'Lycée Test',
      });

      const promise = firstValueFrom(service.list({ page: 0, size: 8 }));
      const sessionsReq = httpMock.expectOne((req) => req.url === '/api/v1/attendance/sessions');
      const reportReq = httpMock.expectOne((req) => req.url === '/api/v1/reporting/attendance');
      const classesReq = httpMock.expectOne((req) => req.url === '/api/v1/academic/classes');

      sessionsReq.error(new ProgressEvent('error'));
      if (!reportReq.cancelled) {
        reportReq.error(new ProgressEvent('error'));
      }
      if (!classesReq.cancelled) {
        classesReq.error(new ProgressEvent('error'));
      }

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
    });

    it('does NOT swallow structured RFC 9457 business errors even in dev demo session', async () => {
      authService.setSession('demo-memory-token', {
        id: 'usr-demo',
        fullName: 'Direction Démo',
        email: 'direction@ecole-demo.invalid',
        role: 'DIRECTOR',
        tenantId: 'tenant-demo-01',
        tenantName: 'Groupe Scolaire Démo',
      });

      const promise = firstValueFrom(
        service.justifyRecord({
          recordId: 'abs-demo-001',
          reason: 'Test conflit',
        })
      );

      const req = httpMock.expectOne('/api/v1/attendance/records/abs-demo-001/justify');
      req.flush(
        {
          type: 'urn:ecole:problem:attendance-session-locked',
          title: 'Séance verrouillée',
          status: 409,
          detail: 'La feuille d’appel est verrouillée et ne peut plus être modifiée.',
          requestId: 'req-abs-lock-409',
        },
        { status: 409, statusText: 'Conflict' }
      );

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
    });
  });
});
