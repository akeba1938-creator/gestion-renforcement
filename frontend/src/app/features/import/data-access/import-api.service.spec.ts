import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { problemDetailInterceptor } from '../../../core/interceptors/problem-detail.interceptor';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  BackendPageResponse,
  ImportJobDto,
  ImportJobRowDto,
} from '../models/import.model';
import { ImportApiService } from './import-api.service';
import { IMPORT_DEMO_FALLBACK } from './import-demo-fallback.token';
import { ImportDevFallbackService } from './import-dev-fallback.service';

const SAMPLE_JOB_DTO: ImportJobDto = {
  id: '11111111-1111-4111-8111-111111111111',
  jobNumber: 'IMP-2026-0001',
  importType: 'STUDENTS',
  sourceFormat: 'CSV',
  validationMode: 'PARTIAL_SKIP_ERRORS',
  duplicateStrategy: 'SKIP_DUPLICATES',
  status: 'PREVIEW_READY',
  academicYearId: null,
  evaluationId: null,
  attendanceSessionId: null,
  fileName: 'eleves-rentree.csv',
  contentType: 'text/csv; charset=UTF-8',
  fileSizeBytes: 2048,
  fileSha256: 'abc123',
  totalRows: 2,
  validRows: 1,
  errorRows: 1,
  duplicateRows: 0,
  createdCount: 0,
  updatedCount: 0,
  skippedCount: 0,
  quotaResource: 'STUDENTS',
  quotaLimit: 1000,
  quotaUsedBefore: 120,
  quotaProjectedAfter: 121,
  createdBy: '22222222-2222-4222-8222-222222222222',
  analyzedAt: '2026-11-26T10:00:00Z',
  executedAt: null,
  executedBy: null,
  cancelledAt: null,
  cancelledBy: null,
  cancellationReason: null,
  createdAt: '2026-11-26T10:00:00Z',
};

const SAMPLE_ROW_DTO: ImportJobRowDto = {
  id: 'row-1',
  rowNumber: 2,
  naturalKey: 'ECO-2026-001',
  rowStatus: 'VALID',
  plannedAction: 'CREATE',
  appliedAction: null,
  errorCode: null,
  errorField: null,
  errorMessage: null,
  targetEntityType: 'STUDENT',
  targetEntityId: null,
  rowPreviewJson: JSON.stringify({
    matricule: 'ECO-2026-001',
    first_name: 'Awa',
    last_name: 'Ndiaye',
    class_code: '6E-A',
    phone: '+221770000001',
  }),
};

describe('ImportApiService', () => {
  describe('production mode (no dev fallback provider)', () => {
    let service: ImportApiService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(withInterceptors([problemDetailInterceptor])),
          provideHttpClientTesting(),
        ],
      });
      service = TestBed.inject(ImportApiService);
      httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
      httpMock.verify();
    });

    it('downloads CSV template from GET /api/v1/schoolimport/templates/{type}/download without tenantId', async () => {
      const promise = firstValueFrom(service.downloadTemplate('STUDENTS'));

      const req = httpMock.expectOne('/api/v1/schoolimport/templates/STUDENTS/download');
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      expect(req.request.params.has('tenantId')).toBe(false);
      expect(req.request.headers.has('X-Tenant-Id')).toBe(false);

      req.flush(new Blob(['first_name;last_name\r\n'], { type: 'text/csv; charset=UTF-8' }), {
        headers: {
          'Content-Disposition': 'attachment; filename="modele-eleves.csv"',
          'Content-Type': 'text/csv; charset=UTF-8',
        },
      });

      const download = await promise;
      expect(download.fileName).toBe('modele-eleves.csv');
      expect(download.blob.size).toBeGreaterThan(0);
    });

    it('uploads file multipart to POST /api/v1/schoolimport/jobs with exact query params and Idempotency-Key', async () => {
      const file = new File(['first_name;last_name\r\nAwa;Ndiaye'], 'eleves-rentree.csv', {
        type: 'text/csv',
      });

      const promise = firstValueFrom(
        service.createImportJob({
          file,
          importType: 'STUDENTS',
          validationMode: 'PARTIAL_SKIP_ERRORS',
          duplicateStrategy: 'SKIP_DUPLICATES',
          executeImmediately: false,
          idempotencyKey: 'idem-upload-01',
        })
      );

      const req = httpMock.expectOne((r) => r.url === '/api/v1/schoolimport/jobs');
      expect(req.request.method).toBe('POST');
      expect(req.request.body instanceof FormData).toBe(true);
      expect(req.request.params.get('importType')).toBe('STUDENTS');
      expect(req.request.params.get('validationMode')).toBe('PARTIAL_SKIP_ERRORS');
      expect(req.request.params.get('duplicateStrategy')).toBe('SKIP_DUPLICATES');
      expect(req.request.params.get('executeImmediately')).toBe('false');
      expect(req.request.headers.get('Idempotency-Key')).toBe('idem-upload-01');
      expect(req.request.params.has('tenantId')).toBe(false);
      expect(req.request.headers.has('X-Tenant-Id')).toBe(false);

      req.flush(SAMPLE_JOB_DTO);

      const job = await promise;
      expect(job.id).toBe(SAMPLE_JOB_DTO.id);
      expect(job.jobNumber).toBe('IMP-2026-0001');
    });

    it('lists and maps job rows from GET /api/v1/schoolimport/jobs/{jobId}/rows', async () => {
      const promise = firstValueFrom(
        service.listJobRows(SAMPLE_JOB_DTO.id, {
          rowStatus: 'VALID',
          page: 0,
          size: 20,
        })
      );

      const req = httpMock.expectOne(
        (r) => r.url === `/api/v1/schoolimport/jobs/${SAMPLE_JOB_DTO.id}/rows`
      );
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('rowStatus')).toBe('VALID');
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('20');
      expect(req.request.params.get('sortBy')).toBe('rowNumber');
      expect(req.request.params.get('direction')).toBe('ASC');

      const pageDto: BackendPageResponse<ImportJobRowDto> = {
        content: [SAMPLE_ROW_DTO],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      };
      req.flush(pageDto);

      const page = await promise;
      expect(page.content).toHaveLength(1);
      expect(page.content[0]?.studentName).toBe('Awa Ndiaye');
      expect(page.content[0]?.className).toBe('6E-A');
      expect(page.content[0]?.guardianPhone).toBe('+221770000001');
      expect(page.content[0]?.rowStatus).toBe('VALID');
    });

    it('executes an analyzed job via POST /api/v1/schoolimport/jobs/{jobId}/execute with confirmExecution=true', async () => {
      const promise = firstValueFrom(service.executeJob(SAMPLE_JOB_DTO.id, 'idem-exec-01'));

      const req = httpMock.expectOne(`/api/v1/schoolimport/jobs/${SAMPLE_JOB_DTO.id}/execute`);
      expect(req.request.method).toBe('POST');
      expect(req.request.headers.get('Idempotency-Key')).toBe('idem-exec-01');
      expect(req.request.body).toEqual({
        confirmExecution: true,
        idempotencyKey: 'idem-exec-01',
      });

      req.flush({
        ...SAMPLE_JOB_DTO,
        status: 'COMPLETED_WITH_ERRORS',
        createdCount: 1,
        skippedCount: 1,
        executedAt: '2026-11-26T10:02:00Z',
      });

      const executed = await promise;
      expect(executed.status).toBe('COMPLETED_WITH_ERRORS');
      expect(executed.createdCount).toBe(1);
    });

    it('downloads error report CSV from GET /api/v1/schoolimport/jobs/{jobId}/errors/download', async () => {
      const promise = firstValueFrom(service.downloadErrorReport(SAMPLE_JOB_DTO.id));

      const req = httpMock.expectOne(
        `/api/v1/schoolimport/jobs/${SAMPLE_JOB_DTO.id}/errors/download`
      );
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');

      req.flush(new Blob(['row_number;error_message\r\n2;Invalide'], { type: 'text/csv' }), {
        headers: {
          'Content-Disposition': 'attachment; filename="erreurs-imp-2026-0001.csv"',
        },
      });

      const report = await promise;
      expect(report.fileName).toBe('erreurs-imp-2026-0001.csv');
    });

    it('propagates normalized RFC 9457 ProblemDetailError with requestId on server error', async () => {
      const promise = firstValueFrom(service.executeJob(SAMPLE_JOB_DTO.id));

      const req = httpMock.expectOne(`/api/v1/schoolimport/jobs/${SAMPLE_JOB_DTO.id}/execute`);
      req.flush(
        {
          type: 'urn:ecole:problem:conflict',
          title: 'Lot déjà exécuté',
          status: 409,
          detail: "Ce lot d'import a déjà été traité.",
          requestId: 'req-import-409',
        },
        { status: 409, statusText: 'Conflict' }
      );

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
      try {
        await promise;
      } catch (err) {
        const problemErr = err as ProblemDetailError;
        expect(problemErr.problem.status).toBe(409);
        expect(problemErr.problem.requestId).toBe('req-import-409');
        expect(problemErr.problem.detail).toContain('déjà été traité');
      }
    });
  });

  describe('dev demo fallback mode', () => {
    let service: ImportApiService;
    let httpMock: HttpTestingController;
    let authService: AuthService;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(withInterceptors([problemDetailInterceptor])),
          provideHttpClientTesting(),
          { provide: IMPORT_DEMO_FALLBACK, useExisting: ImportDevFallbackService },
        ],
      });
      service = TestBed.inject(ImportApiService);
      httpMock = TestBed.inject(HttpTestingController);
      authService = TestBed.inject(AuthService);
      authService.setSession('demo-memory-token', {
        id: 'usr-demo-director',
        fullName: 'Direction Démo',
        email: 'direction@ecole-demo.invalid',
        role: 'DIRECTOR',
        tenantId: 'tenant-demo-01',
        tenantName: 'École Démo',
      });
    });

    afterEach(() => {
      httpMock.verify();
    });

    it('falls back cleanly in demo mode when backend is unreachable and parses uploaded CSV', async () => {
      const csvFile = new File(
        [
          'first_name;last_name;class_code;phone\r\n' +
            'Aminata;Diallo;6E-A;+221000000001\r\n' +
            'Moussa;Fall;3E-B;77123\r\n',
        ],
        'eleves-demo.csv',
        { type: 'text/csv' }
      );

      const jobPromise = firstValueFrom(
        service.createImportJob({
          file: csvFile,
          importType: 'STUDENTS',
          validationMode: 'PARTIAL_SKIP_ERRORS',
        })
      );

      const createReq = httpMock.expectOne((r) => r.url === '/api/v1/schoolimport/jobs');
      createReq.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

      const job = await jobPromise;
      expect(job.status).toBe('PREVIEW_READY');
      expect(job.totalRows).toBe(2);
      expect(job.validRows).toBe(1);
      expect(job.errorRows).toBe(1);

      const rowsPromise = firstValueFrom(service.listJobRows(job.id, { rowStatus: 'ERROR' }));
      const rowsReq = httpMock.expectOne(
        (r) => r.url === `/api/v1/schoolimport/jobs/${job.id}/rows`
      );
      rowsReq.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

      const rows = await rowsPromise;
      expect(rows.content).toHaveLength(1);
      expect(rows.content[0]?.rowStatus).toBe('ERROR');
      expect(rows.content[0]?.anomaly).toContain('+221');
    });

    it('never falls back when backend responds with a real RFC 9457 error carrying requestId', async () => {
      const promise = firstValueFrom(service.downloadTemplate('STUDENTS'));

      const req = httpMock.expectOne('/api/v1/schoolimport/templates/STUDENTS/download');
      req.flush(
        new Blob(
          [
            JSON.stringify({
              type: 'urn:ecole:problem:service-unavailable',
              title: 'Maintenance',
              status: 503,
              detail: 'Service temporairement en maintenance.',
              requestId: 'req-real-503',
            }),
          ],
          { type: 'application/problem+json' }
        ),
        {
          status: 503,
          statusText: 'Service Unavailable',
          headers: { 'X-Request-Id': 'req-real-503' },
        }
      );

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
    });
  });
});
