import { HttpClient, HttpHeaders, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import {
  ProblemDetailError,
  extractFieldErrors,
  isProblemDetailError,
} from '../models/problem-detail.model';
import { problemDetailInterceptor } from './problem-detail.interceptor';

describe('problemDetailInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([problemDetailInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('normalizes an RFC 9457 validation response (400) with field errors and requestId', async () => {
    const requestPromise = firstValueFrom(http.post('/api/v1/students', {})).catch(
      (err: unknown) => err
    );

    const req = httpMock.expectOne('/api/v1/students');
    req.flush(
      {
        type: 'urn:ecole:problem:validation-error',
        title: 'Données invalides',
        status: 400,
        detail: 'Certains champs de la requête sont invalides.',
        instance: '/api/v1/students',
        requestId: 'req-abc-123',
        errors: [
          { field: 'phone', message: 'Le numéro doit suivre le format +221.' },
          { field: 'amountFcfa', message: 'Le montant doit être positif.' },
        ],
      },
      { status: 400, statusText: 'Bad Request' }
    );

    const error = await requestPromise;
    expect(isProblemDetailError(error)).toBe(true);
    const problemError = error as ProblemDetailError;
    expect(problemError.problem.type).toBe('urn:ecole:problem:validation-error');
    expect(problemError.problem.title).toBe('Données invalides');
    expect(problemError.problem.status).toBe(400);
    expect(problemError.problem.detail).toBe('Certains champs de la requête sont invalides.');
    expect(problemError.problem.instance).toBe('/api/v1/students');
    expect(problemError.problem.requestId).toBe('req-abc-123');
    expect(extractFieldErrors(problemError.problem)).toEqual({
      phone: 'Le numéro doit suivre le format +221.',
      amountFcfa: 'Le montant doit être positif.',
    });
  });

  it('falls back to X-Request-Id header when requestId is absent from the body', async () => {
    const requestPromise = firstValueFrom(http.get('/api/v1/students/404')).catch(
      (err: unknown) => err
    );

    const req = httpMock.expectOne('/api/v1/students/404');
    req.flush(
      {
        type: 'urn:ecole:problem:not-found',
        title: 'Ressource introuvable',
        status: 404,
        detail: "L'élève demandé est introuvable.",
      },
      {
        status: 404,
        statusText: 'Not Found',
        headers: new HttpHeaders({ 'X-Request-Id': 'hdr-trace-987' }),
      }
    );

    const error = await requestPromise;
    expect(isProblemDetailError(error)).toBe(true);
    expect((error as ProblemDetailError).problem.requestId).toBe('hdr-trace-987');
    expect((error as ProblemDetailError).problem.status).toBe(404);
  });

  it('never leaks raw HTML or stack traces on non-RFC-9457 server errors', async () => {
    const requestPromise = firstValueFrom(http.get('/api/v1/finance/summary')).catch(
      (err: unknown) => err
    );

    const req = httpMock.expectOne('/api/v1/finance/summary');
    req.flush(
      '<html><body><h1>502 Bad Gateway</h1>java.lang.NullPointerException at com.ecole.Secret()</body></html>',
      {
        status: 502,
        statusText: 'Bad Gateway',
        headers: new HttpHeaders({ 'X-Request-Id': 'gw-502-id' }),
      }
    );

    const error = await requestPromise;
    expect(isProblemDetailError(error)).toBe(true);
    const problem = (error as ProblemDetailError).problem;
    expect(problem.status).toBe(502);
    expect(problem.title).toBe('Service temporairement indisponible');
    expect(problem.detail).not.toContain('NullPointerException');
    expect(problem.detail).not.toContain('<html>');
    expect(problem.requestId).toBe('gw-502-id');
  });

  it('sanitizes technical details if an unexpected stack trace appears inside a JSON detail field', async () => {
    const requestPromise = firstValueFrom(http.get('/api/v1/grades')).catch(
      (err: unknown) => err
    );

    const req = httpMock.expectOne('/api/v1/grades');
    req.flush(
      {
        status: 500,
        title: 'Erreur interne',
        detail: 'org.hibernate.exception.SQLGrammarException: relation does not exist',
      },
      { status: 500, statusText: 'Internal Server Error' }
    );

    const error = await requestPromise;
    expect(isProblemDetailError(error)).toBe(true);
    const problem = (error as ProblemDetailError).problem;
    expect(problem.status).toBe(500);
    expect(problem.detail).not.toContain('hibernate');
    expect(problem.detail).toContain('erreur interne');
  });

  it('normalizes network failures (status 0) into a French connectivity ProblemDetail', async () => {
    const requestPromise = firstValueFrom(http.get('/api/v1/students')).catch(
      (err: unknown) => err
    );

    const req = httpMock.expectOne('/api/v1/students');
    req.error(new ProgressEvent('error'));

    const error = await requestPromise;
    expect(isProblemDetailError(error)).toBe(true);
    const problem = (error as ProblemDetailError).problem;
    expect(problem.status).toBe(0);
    expect(problem.type).toBe('urn:ecole:problem:network-error');
    expect(problem.title).toBe('Connexion impossible');
    expect(problem.detail).toContain('connexion réseau');
  });

  it('preserves business 403, 409, and 422 ProblemDetail messages', async () => {
    const statuses = [
      { status: 403, title: 'Accès refusé', detail: 'Permission insuffisante.' },
      { status: 409, title: 'Conflit', detail: 'Ce matricule existe déjà.' },
      { status: 422, title: 'Règle métier', detail: 'Capacité de classe atteinte.' },
    ];

    for (const item of statuses) {
      const promise = firstValueFrom(http.post('/api/v1/test', {})).catch((err: unknown) => err);
      const req = httpMock.expectOne('/api/v1/test');
      req.flush(
        {
          type: `urn:ecole:problem:s-${item.status}`,
          title: item.title,
          status: item.status,
          detail: item.detail,
        },
        { status: item.status, statusText: 'Error' }
      );
      const error = await promise;
      expect(isProblemDetailError(error)).toBe(true);
      expect((error as ProblemDetailError).problem.status).toBe(item.status);
      expect((error as ProblemDetailError).problem.detail).toBe(item.detail);
    }
  });
});
