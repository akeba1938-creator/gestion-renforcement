import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { problemDetailInterceptor } from '../../../core/interceptors/problem-detail.interceptor';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  AcademicReportViewDto,
  BackendPageResponse,
  EvaluationResponseDto,
  ReportCardResponseDto,
  StudentGradeResponseDto,
} from '../models/grades.model';
import { GradesApiService } from './grades-api.service';
import { GRADES_DEMO_FALLBACK } from './grades-demo-fallback.token';
import { GradesDevFallbackService } from './grades-dev-fallback.service';

const YEAR_ID = '11111111-1111-4111-8111-111111111111';
const PERIOD_T1_ID = '22222222-2222-4222-8222-222222222222';
const CLASS_TS2_ID = '33333333-3333-4333-8333-333333333333';
const SUBJECT_MATH_ID = '44444444-4444-4444-8444-444444444444';
const STUDENT_ID = '55555555-5555-4555-8555-555555555555';
const REPORT_CARD_ID = '66666666-6666-4666-8666-666666666666';
const EVALUATION_ID = '77777777-7777-4777-8777-777777777777';

const SAMPLE_REPORT_CARD_DTO: ReportCardResponseDto = {
  id: REPORT_CARD_ID,
  academicYearId: YEAR_ID,
  evaluationPeriodId: PERIOD_T1_ID,
  periodCode: 'T1',
  periodName: 'Trimestre 1',
  classRoomId: CLASS_TS2_ID,
  classRoomCode: 'TS2',
  classRoomName: 'Terminale S2',
  studentId: STUDENT_ID,
  studentMatricule: '2026-0001',
  studentFirstName: 'Aminata',
  studentLastName: 'Diallo',
  gradingScale: 20,
  generalAverage: '16.45',
  classAverage: '12.80',
  minClassAverage: '8.20',
  maxClassAverage: '16.45',
  rankInClass: 1,
  classSize: 34,
  tiedRank: false,
  totalWeightedScore: '361.90',
  totalCoefficients: 22,
  mention: 'TRES_BIEN',
  councilDecision: 'FELICITATIONS',
  homeroomAppreciation: 'Excellent trimestre, rigueur scientifique.',
  directorAppreciation: 'Félicitations du conseil.',
  status: 'LOCKED',
  computedAt: '2026-11-25T16:30:00Z',
  validatedBy: null,
  validatedAt: '2026-11-25T17:00:00Z',
  lockedBy: null,
  lockedAt: '2026-11-25T18:00:00Z',
  subjectResults: [
    {
      id: '88888888-8888-4888-8888-888888888888',
      subjectId: SUBJECT_MATH_ID,
      classSubjectId: '99999999-9999-4999-8999-999999999999',
      subjectCode: 'MATH',
      subjectName: 'Mathématiques',
      coefficient: 5,
      averageScore: '17.50',
      classSubjectAverage: '12.40',
      rankInSubject: 1,
      evaluationCount: 2,
      teacherAppreciation: 'Excellente maîtrise.',
      appreciatedBy: null,
      appreciatedAt: null,
    },
  ],
};

const SAMPLE_EVALUATION_DTO: EvaluationResponseDto = {
  id: EVALUATION_ID,
  academicYearId: YEAR_ID,
  evaluationPeriodId: PERIOD_T1_ID,
  periodCode: 'T1',
  periodStatus: 'OPEN',
  classRoomId: CLASS_TS2_ID,
  classRoomCode: 'TS2',
  classRoomName: 'Terminale S2',
  subjectId: SUBJECT_MATH_ID,
  classSubjectId: '99999999-9999-4999-8999-999999999999',
  subjectCode: 'MATH',
  subjectName: 'Mathématiques',
  teacherId: null,
  teacherFullName: 'M. Diop',
  title: 'Devoir de Mathématiques — T1',
  evaluationType: 'HOMEWORK',
  evaluationDate: '2026-11-26',
  maxScore: 20,
  weight: 5,
  status: 'DRAFT',
  validatedBy: null,
  validatedAt: null,
  lockedBy: null,
  lockedAt: null,
  createdBy: null,
  notes: null,
  createdAt: '2026-11-26T09:00:00Z',
  updatedAt: '2026-11-26T09:00:00Z',
};

const SAMPLE_ACADEMIC_REPORT_DTO: AcademicReportViewDto = {
  schoolCode: 'LYC-DKR',
  schoolName: 'Lycée Démo',
  academicYearId: YEAR_ID,
  academicYearCode: '2026-2027',
  evaluationPeriodId: PERIOD_T1_ID,
  evaluationPeriodCode: 'T1',
  gradingScale: 20,
  classCount: 6,
  totalActiveStudentsInSchool: 240,
  totalEnrolledInScope: 34,
  totalCapacityInScope: 40,
  occupancyRatePercent: 85.0,
  reportCardCount: 34,
  rankedStudentCount: 34,
  passedStudentCount: 29,
  passRatePercent: 85.3,
  overallAverage: '13.48',
  minAverage: '7.50',
  maxAverage: '17.20',
  draftReportCardCount: 4,
  validatedReportCardCount: 18,
  lockedReportCardCount: 12,
  generatedAt: '2026-11-26T10:00:00Z',
};

describe('GradesApiService', () => {
  describe('production mode (no dev fallback provider)', () => {
    let service: GradesApiService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(withInterceptors([problemDetailInterceptor])),
          provideHttpClientTesting(),
        ],
      });
      service = TestBed.inject(GradesApiService);
      httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
      httpMock.verify();
    });

    it('requests GET /api/v1/grades/report-cards, evaluations, and reporting/academic with exact backend params and zero tenantId', async () => {
      const promise = firstValueFrom(
        service.list({
          page: 1,
          size: 8,
          term: 'T1',
          evaluationPeriodId: PERIOD_T1_ID,
          classRoomId: CLASS_TS2_ID,
          sortBy: 'rankInClass',
          direction: 'ASC',
        })
      );

      const cardsReq = httpMock.expectOne((req) => req.url === '/api/v1/grades/report-cards');
      expect(cardsReq.request.method).toBe('GET');
      expect(cardsReq.request.params.get('page')).toBe('1');
      expect(cardsReq.request.params.get('size')).toBe('8');
      expect(cardsReq.request.params.get('sortBy')).toBe('rankInClass');
      expect(cardsReq.request.params.get('direction')).toBe('ASC');
      expect(cardsReq.request.params.get('classRoomId')).toBe(CLASS_TS2_ID);
      expect(cardsReq.request.params.get('evaluationPeriodId')).toBe(PERIOD_T1_ID);
      expect(cardsReq.request.params.has('tenantId')).toBe(false);
      expect(cardsReq.request.headers.has('X-Tenant-Id')).toBe(false);

      const evalsReq = httpMock.expectOne((req) => req.url === '/api/v1/grades/evaluations');
      expect(evalsReq.request.method).toBe('GET');
      expect(evalsReq.request.params.get('classRoomId')).toBe(CLASS_TS2_ID);
      expect(evalsReq.request.params.has('tenantId')).toBe(false);

      const reportReq = httpMock.expectOne((req) => req.url === '/api/v1/reporting/academic');
      expect(reportReq.request.method).toBe('GET');
      expect(reportReq.request.params.get('evaluationPeriodId')).toBe(PERIOD_T1_ID);
      expect(reportReq.request.params.has('tenantId')).toBe(false);

      const cardsPage: BackendPageResponse<ReportCardResponseDto> = {
        content: [SAMPLE_REPORT_CARD_DTO],
        page: 1,
        size: 8,
        totalElements: 9,
        totalPages: 2,
        first: false,
        last: true,
      };
      const evalsPage: BackendPageResponse<EvaluationResponseDto> = {
        content: [SAMPLE_EVALUATION_DTO],
        page: 0,
        size: 50,
        totalElements: 14,
        totalPages: 1,
        first: true,
        last: true,
      };

      cardsReq.flush(cardsPage);
      evalsReq.flush(evalsPage);
      reportReq.flush(SAMPLE_ACADEMIC_REPORT_DTO);

      const result = await promise;
      expect(result.page).toBe(1);
      expect(result.totalElements).toBe(9);
      expect(result.content.length).toBe(1);
      expect(result.content[0]?.studentFullName).toBe('Aminata Diallo');
      expect(result.content[0]?.generalAverage).toBe(16.45);
      expect(result.content[0]?.mention).toBe('TRES_BIEN');
      expect(result.kpis.overallAverage).toBe(13.48);
      expect(result.kpis.passRatePercent).toBe(85.3);
      expect(result.kpis.evaluationsCount).toBe(14);
      expect(result.kpis.readyReportCardsCount).toBe(30);
    });

    it('creates evaluation via POST /api/v1/grades/evaluations then saves entries via PUT /{id}/entries without tenantId', async () => {
      const promise = firstValueFrom(
        service.saveEvaluationGrades({
          evaluationPeriodId: PERIOD_T1_ID,
          termCode: 'T1',
          classRoomId: CLASS_TS2_ID,
          subjectId: SUBJECT_MATH_ID,
          evaluationType: 'COMPOSITION',
          coefficient: 5,
          title: 'Composition de Mathématiques — T1',
          evaluationDate: '2026-11-26',
          entries: [{ studentId: STUDENT_ID, score: 17.5 }],
        })
      );

      const createReq = httpMock.expectOne('/api/v1/grades/evaluations');
      expect(createReq.request.method).toBe('POST');
      expect(createReq.request.headers.has('X-Tenant-Id')).toBe(false);
      expect(createReq.request.body).toEqual({
        evaluationPeriodId: PERIOD_T1_ID,
        classRoomId: CLASS_TS2_ID,
        subjectId: SUBJECT_MATH_ID,
        title: 'Composition de Mathématiques — T1',
        evaluationType: 'COMPOSITION',
        evaluationDate: '2026-11-26',
        maxScore: 20,
        weight: 5,
        notes: null,
      });

      createReq.flush(
        { ...SAMPLE_EVALUATION_DTO, title: 'Composition de Mathématiques — T1' },
        { status: 201, statusText: 'Created' }
      );

      const upsertReq = httpMock.expectOne(`/api/v1/grades/evaluations/${EVALUATION_ID}/entries`);
      expect(upsertReq.request.method).toBe('PUT');
      expect(upsertReq.request.body).toEqual({
        entries: [
          {
            studentId: STUDENT_ID,
            attendanceStatus: 'PRESENT',
            score: 17.5,
            comment: null,
          },
        ],
      });

      const savedEntries: readonly StudentGradeResponseDto[] = [
        {
          id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
          evaluationId: EVALUATION_ID,
          studentId: STUDENT_ID,
          studentMatricule: '2026-0001',
          studentFirstName: 'Aminata',
          studentLastName: 'Diallo',
          attendanceStatus: 'PRESENT',
          score: '17.50',
          comment: null,
          enteredBy: null,
          enteredAt: '2026-11-26T10:00:00Z',
          updatedBy: null,
          updatedAt: '2026-11-26T10:00:00Z',
        },
      ];
      upsertReq.flush(savedEntries);

      const saved = await promise;
      expect(saved.evaluationId).toBe(EVALUATION_ID);
      expect(saved.savedCount).toBe(1);
      expect(saved.classAverage).toBe(17.5);
    });

    it('computes class report cards via POST /api/v1/grades/report-cards/compute', async () => {
      const promise = firstValueFrom(
        service.generateReportCards({
          classRoomId: CLASS_TS2_ID,
          evaluationPeriodId: PERIOD_T1_ID,
          termCode: 'T1',
        })
      );

      const req = httpMock.expectOne('/api/v1/grades/report-cards/compute');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        classRoomId: CLASS_TS2_ID,
        evaluationPeriodId: PERIOD_T1_ID,
      });
      req.flush([SAMPLE_REPORT_CARD_DTO]);

      const result = await promise;
      expect(result.generatedCount).toBe(1);
      expect(result.classRoomName).toBe('Terminale S2');
    });

    it('propagates RFC 9457 ProblemDetailError when backend returns an error in production', async () => {
      const promise = firstValueFrom(service.list({ page: 0, size: 8, term: 'T1' }));

      const cardsReq = httpMock.expectOne((req) => req.url === '/api/v1/grades/report-cards');
      const evalsReq = httpMock.expectOne((req) => req.url === '/api/v1/grades/evaluations');
      const reportReq = httpMock.expectOne((req) => req.url === '/api/v1/reporting/academic');

      cardsReq.flush(
        {
          type: 'urn:ecole:problem:service-unavailable',
          title: 'Service notes indisponible',
          status: 503,
          detail: 'Le module notes et bulletins est temporairement indisponible.',
          requestId: 'req-grades-prod-503',
        },
        { status: 503, statusText: 'Service Unavailable' }
      );
      if (!evalsReq.cancelled) {
        evalsReq.flush({
          content: [],
          page: 0,
          size: 50,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        });
      }
      if (!reportReq.cancelled) {
        reportReq.flush(SAMPLE_ACADEMIC_REPORT_DTO);
      }

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
      try {
        await promise;
      } catch (err) {
        const problem = (err as ProblemDetailError).problem;
        expect(problem.status).toBe(503);
        expect(problem.requestId).toBe('req-grades-prod-503');
      }
    });
  });

  describe('development fallback guard', () => {
    let service: GradesApiService;
    let httpMock: HttpTestingController;
    let authService: AuthService;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(withInterceptors([problemDetailInterceptor])),
          provideHttpClientTesting(),
          { provide: GRADES_DEMO_FALLBACK, useExisting: GradesDevFallbackService },
        ],
      });
      service = TestBed.inject(GradesApiService);
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

      const listPromise = firstValueFrom(service.list({ page: 0, size: 8, term: 'T1' }));
      const cardsReq = httpMock.expectOne((req) => req.url === '/api/v1/grades/report-cards');
      const evalsReq = httpMock.expectOne((req) => req.url === '/api/v1/grades/evaluations');
      const reportReq = httpMock.expectOne((req) => req.url === '/api/v1/reporting/academic');

      cardsReq.error(new ProgressEvent('error'));
      if (!evalsReq.cancelled) {
        evalsReq.error(new ProgressEvent('error'));
      }
      if (!reportReq.cancelled) {
        reportReq.error(new ProgressEvent('error'));
      }

      const result = await listPromise;
      expect(result.totalElements).toBeGreaterThan(0);
      expect(result.kpis.overallAverage).not.toBeNull();
      expect(result.classes.length).toBeGreaterThan(0);
      expect(result.subjects.length).toBeGreaterThan(0);

      // Saving evaluation grades in demo fallback updates state cleanly
      const savePromise = firstValueFrom(
        service.saveEvaluationGrades({
          evaluationPeriodId: 'term-t1',
          termCode: 'T1',
          classRoomId: result.classes[0].id,
          subjectId: result.subjects[0].id,
          evaluationType: 'HOMEWORK',
          coefficient: 4,
          title: 'Devoir démo',
          evaluationDate: '2026-11-26',
          entries: [{ studentId: result.studentsForEntry[0].studentId, score: 18 }],
        })
      );
      const postReq = httpMock.expectOne('/api/v1/grades/evaluations');
      postReq.flush('Not Found', { status: 404, statusText: 'Not Found' });

      const saved = await savePromise;
      expect(saved.savedCount).toBe(1);
      expect(saved.classAverage).toBe(18);
    });

    it('does NOT fall back when authenticated with a real user token', async () => {
      authService.setSession('real-jwt-access-token', {
        id: 'usr-real',
        fullName: 'Directeur Réel',
        email: 'direction@lycee.sn',
        role: 'DIRECTOR',
        tenantId: 'tenant-01',
        tenantName: 'Lycée Test',
      });

      const promise = firstValueFrom(service.list({ page: 0, size: 8, term: 'T1' }));
      const cardsReq = httpMock.expectOne((req) => req.url === '/api/v1/grades/report-cards');
      const evalsReq = httpMock.expectOne((req) => req.url === '/api/v1/grades/evaluations');
      const reportReq = httpMock.expectOne((req) => req.url === '/api/v1/reporting/academic');

      cardsReq.error(new ProgressEvent('error'));
      if (!evalsReq.cancelled) {
        evalsReq.error(new ProgressEvent('error'));
      }
      if (!reportReq.cancelled) {
        reportReq.error(new ProgressEvent('error'));
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
        service.saveEvaluationGrades({
          evaluationPeriodId: PERIOD_T1_ID,
          termCode: 'T1',
          classRoomId: CLASS_TS2_ID,
          subjectId: SUBJECT_MATH_ID,
          evaluationType: 'COMPOSITION',
          coefficient: 5,
          title: 'Composition clôturée',
          evaluationDate: '2026-11-26',
          entries: [{ studentId: STUDENT_ID, score: 15 }],
        })
      );

      const req = httpMock.expectOne('/api/v1/grades/evaluations');
      req.flush(
        {
          type: 'urn:ecole:problem:period-closed',
          title: 'Trimestre clôturé',
          status: 409,
          detail: 'Impossible de saisir des notes sur une période déjà clôturée.',
          requestId: 'req-grades-closed-409',
        },
        { status: 409, statusText: 'Conflict' }
      );

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
    });
  });
});
