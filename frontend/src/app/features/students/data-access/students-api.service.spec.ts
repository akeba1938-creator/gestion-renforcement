import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { problemDetailInterceptor } from '../../../core/interceptors/problem-detail.interceptor';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import { BackendPageResponse, StudentResponseDto } from '../models/students.model';
import { StudentsApiService } from './students-api.service';
import { STUDENTS_DEMO_FALLBACK } from './students-demo-fallback.token';
import { StudentsDevFallbackService } from './students-dev-fallback.service';

const SAMPLE_DTO: StudentResponseDto = {
  id: '11111111-1111-4111-8111-111111111111',
  matricule: '2026-0001',
  firstName: 'Awa',
  lastName: 'Ndiaye',
  gender: 'FEMALE',
  birthDate: '2010-05-12',
  birthPlace: 'Dakar',
  nationality: 'Sénégalaise',
  address: 'Mermoz, Dakar',
  phone: '+221770000001',
  email: 'awa.ndiaye@ecole-demo.invalid',
  photoObjectKey: null,
  medicalNotes: null,
  admissionDate: '2026-10-01',
  status: 'ACTIVE',
  statusReason: null,
  archivedAt: null,
  createdAt: '2026-10-01T09:00:00Z',
  updatedAt: '2026-10-01T09:00:00Z',
};

describe('StudentsApiService', () => {
  describe('production mode (no dev fallback provider)', () => {
    let service: StudentsApiService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(withInterceptors([problemDetailInterceptor])),
          provideHttpClientTesting(),
        ],
      });
      service = TestBed.inject(StudentsApiService);
      httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
      httpMock.verify();
    });

    it('requests GET /api/v1/students with exact backend pagination, sort, search, and status params', async () => {
      const promise = firstValueFrom(
        service.list({
          page: 1,
          size: 8,
          search: 'Ndiaye',
          status: 'ACTIVE',
          sortBy: 'lastName',
          direction: 'ASC',
        })
      );

      const req = httpMock.expectOne((request) => request.url === '/api/v1/students');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('page')).toBe('1');
      expect(req.request.params.get('size')).toBe('8');
      expect(req.request.params.get('sortBy')).toBe('lastName');
      expect(req.request.params.get('direction')).toBe('ASC');
      expect(req.request.params.get('search')).toBe('Ndiaye');
      expect(req.request.params.get('status')).toBe('ACTIVE');
      expect(req.request.params.has('tenantId')).toBe(false);
      expect(req.request.headers.has('X-Tenant-Id')).toBe(false);

      const backendPage: BackendPageResponse<StudentResponseDto> = {
        content: [SAMPLE_DTO],
        page: 1,
        size: 8,
        totalElements: 9,
        totalPages: 2,
        first: false,
        last: true,
      };
      req.flush(backendPage);

      const result = await promise;
      expect(result.page).toBe(1);
      expect(result.totalElements).toBe(9);
      expect(result.totalPages).toBe(2);
      expect(result.first).toBe(false);
      expect(result.last).toBe(true);
      expect(result.content.length).toBe(1);
      expect(result.content[0]?.fullName).toBe('Awa Ndiaye');
      expect(result.content[0]?.initials).toBe('AN');
      expect(result.content[0]?.guardianPhone).toBe('+221770000001');
    });

    it('posts strict CreateStudentRequest payload to POST /api/v1/students', async () => {
      const promise = firstValueFrom(
        service.create({
          firstName: 'Awa',
          lastName: 'Ndiaye',
          className: 'Terminale S2',
          guardianName: 'Mamadou Ndiaye',
          guardianPhone: '+221770000001',
          gender: 'FEMALE',
          birthDate: '2010-05-12',
        })
      );

      const req = httpMock.expectOne('/api/v1/students');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toMatchObject({
        firstName: 'Awa',
        lastName: 'Ndiaye',
        gender: 'FEMALE',
        birthDate: '2010-05-12',
        phone: '+221770000001',
      });
      expect(typeof req.request.body.admissionDate).toBe('string');

      req.flush(SAMPLE_DTO, { status: 201, statusText: 'Created' });

      const created = await promise;
      expect(created.id).toBe(SAMPLE_DTO.id);
      expect(created.className).toBe('Terminale S2');
      expect(created.guardianName).toBe('Mamadou Ndiaye');
      expect(created.guardianPhone).toBe('+221770000001');
    });

    it('propagates RFC 9457 ProblemDetailError when backend is unreachable in production', async () => {
      const promise = firstValueFrom(service.list({ page: 0, size: 8 }));

      const req = httpMock.expectOne((request) => request.url === '/api/v1/students');
      req.flush(
        {
          type: 'urn:ecole:problem:service-unavailable',
          title: 'Service indisponible',
          status: 503,
          detail: 'La base élèves est en maintenance.',
          requestId: 'req-prod-503',
        },
        { status: 503, statusText: 'Service Unavailable' }
      );

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
      try {
        await promise;
      } catch (err) {
        const problem = (err as ProblemDetailError).problem;
        expect(problem.status).toBe(503);
        expect(problem.requestId).toBe('req-prod-503');
      }
    });
  });

  describe('development fallback guard', () => {
    let service: StudentsApiService;
    let httpMock: HttpTestingController;
    let authService: AuthService;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(withInterceptors([problemDetailInterceptor])),
          provideHttpClientTesting(),
          { provide: STUDENTS_DEMO_FALLBACK, useExisting: StudentsDevFallbackService },
        ],
      });
      service = TestBed.inject(StudentsApiService);
      httpMock = TestBed.inject(HttpTestingController);
      authService = TestBed.inject(AuthService);
    });

    afterEach(() => {
      authService.clearSession();
      httpMock.verify();
    });

    it('falls back to demo dataset ONLY when dev demo session is active and backend is down', async () => {
      authService.setSession('demo-memory-token', {
        id: 'usr-demo',
        fullName: 'Direction Démo',
        email: 'direction@ecole-demo.invalid',
        role: 'DIRECTOR',
        tenantId: 'tenant-demo-01',
        tenantName: 'Groupe Scolaire Démo',
      });

      const listPromise = firstValueFrom(service.list({ page: 0, size: 8 }));
      const req = httpMock.expectOne((request) => request.url === '/api/v1/students');
      req.error(new ProgressEvent('error'));

      const page = await listPromise;
      expect(page.totalElements).toBeGreaterThan(8);
      expect(page.content.length).toBe(8);
      expect(page.totalPages).toBeGreaterThanOrEqual(2);

      // Creating a student in demo mode adds it to the fictional in-memory list
      const createPromise = firstValueFrom(
        service.create({
          firstName: 'Mariama',
          lastName: 'Diagne-Démo',
          className: 'Terminale S2',
          guardianName: 'Ousmane Diagne-Démo',
          guardianPhone: '+221000000099',
        })
      );
      const postReq = httpMock.expectOne('/api/v1/students');
      postReq.flush('Not Found', { status: 404, statusText: 'Not Found' });

      const created = await createPromise;
      expect(created.fullName).toBe('Mariama Diagne-Démo');
      expect(created.matricule).toMatch(/^DEMO-2026-/);
    });

    it('does NOT fall back when authenticated with a real user token', async () => {
      authService.setSession('real-jwt-access-token', {
        id: 'usr-real',
        fullName: 'Directeur Réel',
        email: 'dir@lycee.sn',
        role: 'DIRECTOR',
        tenantId: 'tenant-01',
        tenantName: 'Lycée Test',
      });

      const promise = firstValueFrom(service.list({ page: 0, size: 8 }));
      const req = httpMock.expectOne((request) => request.url === '/api/v1/students');
      req.error(new ProgressEvent('error'));

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
    });

    it('does NOT swallow structured RFC 9457 validation errors even in dev demo session', async () => {
      authService.setSession('demo-memory-token', {
        id: 'usr-demo',
        fullName: 'Direction Démo',
        email: 'direction@ecole-demo.invalid',
        role: 'DIRECTOR',
        tenantId: 'tenant-demo-01',
        tenantName: 'Groupe Scolaire Démo',
      });

      const promise = firstValueFrom(
        service.create({
          firstName: 'A',
          lastName: 'B',
          className: '3ème A',
          guardianName: 'Tuteur Test',
          guardianPhone: '+221770000001',
        })
      );
      const req = httpMock.expectOne('/api/v1/students');
      req.flush(
        {
          type: 'urn:ecole:problem:validation-error',
          title: 'Validation échouée',
          status: 400,
          detail: 'Le numéro de téléphone est invalide.',
          requestId: 'req-val-400',
          errors: [{ field: 'phone', message: 'Format E.164 invalide.' }],
        },
        { status: 400, statusText: 'Bad Request' }
      );

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
    });
  });
});
