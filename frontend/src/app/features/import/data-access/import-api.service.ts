import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api.config';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  BackendPageResponse,
  CreateImportJobInput,
  ImportFileDownload,
  ImportJobDto,
  ImportJobRowDto,
  ImportJobRowsPage,
  ImportTemplateDto,
  ImportType,
  ListImportRowsQuery,
  extractCsvFilename,
  mapJobRowDtoToPreviewRow,
} from '../models/import.model';
import { IMPORT_DEMO_FALLBACK } from './import-demo-fallback.token';

const BACKEND_UNAVAILABLE_STATUSES = new Set([0, 200, 404, 502, 503, 504]);
const DEFAULT_CSV_CONTENT_TYPE = 'text/csv; charset=UTF-8';

/**
 * Typed HTTP client for `/api/v1/schoolimport` aligned strictly with backend `SchoolImportController`.
 *
 * Tenant isolation is enforced strictly server-side from the authenticated JWT;
 * zero `tenantId` header, query param, or multipart field is ever sent by the client.
 *
 * In local development with an active demo session and no running backend, delegates
 * transparently to the optional dev-only `IMPORT_DEMO_FALLBACK` provider (stripped from
 * production builds via `app.dev-providers.prod.ts`).
 */
@Injectable({ providedIn: 'root' })
export class ImportApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly demoFallback = inject(IMPORT_DEMO_FALLBACK, { optional: true });

  private get importBaseUrl(): string {
    return `${this.apiBaseUrl.replace(/\/+$/, '')}/schoolimport`;
  }

  listTemplates(): Observable<readonly ImportTemplateDto[]> {
    return this.http.get<readonly ImportTemplateDto[]>(`${this.importBaseUrl}/templates`).pipe(
      catchError((error: unknown) => {
        if (this.shouldFallbackToDemo(error) && this.demoFallback) {
          return this.demoFallback.listTemplates();
        }
        return throwError(() => error);
      })
    );
  }

  downloadTemplate(importType: ImportType): Observable<ImportFileDownload> {
    const encodedType = encodeURIComponent(importType);
    const fallbackFileName = `modele-import-${importType.toLowerCase()}.csv`;

    return this.http
      .get(`${this.importBaseUrl}/templates/${encodedType}/download`, {
        observe: 'response',
        responseType: 'blob',
      })
      .pipe(
        map((response) => {
          const blob = response.body ?? new Blob([], { type: DEFAULT_CSV_CONTENT_TYPE });
          const fileName = extractCsvFilename(
            response.headers.get('Content-Disposition'),
            fallbackFileName
          );
          const contentType = response.headers.get('Content-Type') ?? DEFAULT_CSV_CONTENT_TYPE;
          return { blob, fileName, contentType };
        }),
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.downloadTemplate(importType);
          }
          return throwError(() => error);
        })
      );
  }

  createImportJob(input: CreateImportJobInput): Observable<ImportJobDto> {
    const formData = new FormData();
    formData.append('file', input.file, input.file.name);

    let params = new HttpParams()
      .set('importType', input.importType)
      .set('executeImmediately', String(Boolean(input.executeImmediately)));

    if (input.validationMode) {
      params = params.set('validationMode', input.validationMode);
    }
    if (input.duplicateStrategy) {
      params = params.set('duplicateStrategy', input.duplicateStrategy);
    }

    let headers = new HttpHeaders();
    const idempotencyKey = input.idempotencyKey?.trim();
    if (idempotencyKey) {
      headers = headers.set('Idempotency-Key', idempotencyKey);
    }

    return this.http
      .post<ImportJobDto>(`${this.importBaseUrl}/jobs`, formData, { params, headers })
      .pipe(
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.createImportJob(input);
          }
          return throwError(() => error);
        })
      );
  }

  getJob(jobId: string): Observable<ImportJobDto> {
    const encodedId = encodeURIComponent(jobId.trim());
    return this.http.get<ImportJobDto>(`${this.importBaseUrl}/jobs/${encodedId}`).pipe(
      catchError((error: unknown) => {
        if (this.shouldFallbackToDemo(error) && this.demoFallback) {
          return this.demoFallback.getJob(jobId);
        }
        return throwError(() => error);
      })
    );
  }

  listJobRows(jobId: string, query: ListImportRowsQuery = {}): Observable<ImportJobRowsPage> {
    const encodedId = encodeURIComponent(jobId.trim());
    let params = new HttpParams()
      .set('page', String(Math.max(0, Math.trunc(query.page ?? 0))))
      .set('size', String(Math.max(1, Math.min(200, Math.trunc(query.size ?? 50)))))
      .set('sortBy', query.sortBy ?? 'rowNumber')
      .set('direction', query.direction ?? 'ASC');

    if (query.rowStatus) {
      params = params.set('rowStatus', query.rowStatus);
    }
    if (query.plannedAction) {
      params = params.set('plannedAction', query.plannedAction);
    }

    return this.http
      .get<BackendPageResponse<ImportJobRowDto>>(`${this.importBaseUrl}/jobs/${encodedId}/rows`, {
        params,
      })
      .pipe(
        map((response) => ({
          content: response.content.map((row) => mapJobRowDtoToPreviewRow(row)),
          page: response.page,
          size: response.size,
          totalElements: response.totalElements,
          totalPages: response.totalPages,
          first: response.first,
          last: response.last,
        })),
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.listJobRows(jobId, query);
          }
          return throwError(() => error);
        })
      );
  }

  executeJob(jobId: string, idempotencyKey?: string): Observable<ImportJobDto> {
    const encodedId = encodeURIComponent(jobId.trim());
    const trimmedKey = idempotencyKey?.trim();
    const payload: { readonly confirmExecution: true; readonly idempotencyKey?: string } =
      trimmedKey ? { confirmExecution: true, idempotencyKey: trimmedKey } : { confirmExecution: true };

    let headers = new HttpHeaders();
    if (trimmedKey) {
      headers = headers.set('Idempotency-Key', trimmedKey);
    }

    return this.http
      .post<ImportJobDto>(`${this.importBaseUrl}/jobs/${encodedId}/execute`, payload, { headers })
      .pipe(
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.executeJob(jobId, idempotencyKey);
          }
          return throwError(() => error);
        })
      );
  }

  cancelJob(jobId: string, reason: string): Observable<ImportJobDto> {
    const encodedId = encodeURIComponent(jobId.trim());
    return this.http
      .post<ImportJobDto>(`${this.importBaseUrl}/jobs/${encodedId}/cancel`, {
        reason: reason.trim(),
      })
      .pipe(
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.cancelJob(jobId, reason);
          }
          return throwError(() => error);
        })
      );
  }

  downloadErrorReport(jobId: string): Observable<ImportFileDownload> {
    const encodedId = encodeURIComponent(jobId.trim());
    const fallbackFileName = `rapport-erreurs-${jobId.slice(0, 8)}.csv`;

    return this.http
      .get(`${this.importBaseUrl}/jobs/${encodedId}/errors/download`, {
        observe: 'response',
        responseType: 'blob',
      })
      .pipe(
        map((response) => {
          const blob = response.body ?? new Blob([], { type: DEFAULT_CSV_CONTENT_TYPE });
          const fileName = extractCsvFilename(
            response.headers.get('Content-Disposition'),
            fallbackFileName
          );
          const contentType = response.headers.get('Content-Type') ?? DEFAULT_CSV_CONTENT_TYPE;
          return { blob, fileName, contentType };
        }),
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.downloadErrorReport(jobId);
          }
          return throwError(() => error);
        })
      );
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
