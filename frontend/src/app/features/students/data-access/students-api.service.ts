import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api.config';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  BackendPageResponse,
  CreateStudentInput,
  DEFAULT_STUDENT_CLASSES,
  StudentRecord,
  StudentResponseDto,
  StudentsListQuery,
  StudentsPageResult,
  mapCreateInputToRequestDto,
  mapStatusFilterToBackendParam,
  mapStudentDtoToRecord,
} from '../models/students.model';
import { STUDENTS_DEMO_FALLBACK } from './students-demo-fallback.token';

const BACKEND_UNAVAILABLE_STATUSES = new Set([0, 200, 404, 502, 503, 504]);

/**
 * Typed HTTP client for `/api/v1/students` aligned strictly with backend `StudentController`.
 *
 * Tenant isolation is enforced strictly server-side from the authenticated Bearer token;
 * no `tenantId` header or query param is ever sent by the client.
 *
 * In local development with an active demo login session and no backend running,
 * delegates transparently to the optional dev-only `STUDENTS_DEMO_FALLBACK` provider
 * (which is stripped completely from production builds via `fileReplacements`).
 */
@Injectable({ providedIn: 'root' })
export class StudentsApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly demoFallback = inject(STUDENTS_DEMO_FALLBACK, { optional: true });

  private get studentsUrl(): string {
    return `${this.apiBaseUrl.replace(/\/+$/, '')}/students`;
  }

  list(query: StudentsListQuery): Observable<StudentsPageResult> {
    let params = new HttpParams()
      .set('page', String(Math.max(0, Math.trunc(query.page))))
      .set('size', String(Math.max(1, Math.min(100, Math.trunc(query.size)))))
      .set('sortBy', query.sortBy ?? 'lastName')
      .set('direction', query.direction ?? 'ASC');

    const trimmedSearch = query.search?.trim();
    if (trimmedSearch) {
      params = params.set('search', trimmedSearch);
    }

    const backendStatus = mapStatusFilterToBackendParam(query.status);
    if (backendStatus) {
      params = params.set('status', backendStatus);
    }

    return this.http.get<BackendPageResponse<StudentResponseDto>>(this.studentsUrl, { params }).pipe(
      map((response) => {
        const records = response.content.map((dto) => mapStudentDtoToRecord(dto));
        const filtered = records.filter((student) => {
          if (query.className && student.className !== query.className) {
            return false;
          }
          if (query.status === 'PENDING' && student.status !== 'PENDING') {
            return false;
          }
          return true;
        });

        const availableClasses = Array.from(
          new Set([
            ...DEFAULT_STUDENT_CLASSES,
            ...records
              .map((student) => student.className)
              .filter((name) => name && name !== 'Non affecté'),
          ])
        );

        return {
          content: filtered,
          page: response.page,
          size: response.size,
          totalElements:
            query.className || query.status === 'PENDING' ? filtered.length : response.totalElements,
          totalPages: response.totalPages,
          first: response.first,
          last: response.last,
          availableClasses,
        };
      }),
      catchError((error: unknown) => {
        if (this.shouldFallbackToDemo(error) && this.demoFallback) {
          return this.demoFallback.list(query);
        }
        return throwError(() => error);
      })
    );
  }

  getById(studentId: string): Observable<StudentRecord> {
    const encodedId = encodeURIComponent(studentId.trim());
    return this.http.get<StudentResponseDto>(`${this.studentsUrl}/${encodedId}`).pipe(
      map((dto) => mapStudentDtoToRecord(dto)),
      catchError((error: unknown) => {
        if (this.shouldFallbackToDemo(error) && this.demoFallback) {
          return this.demoFallback.getById(studentId);
        }
        return throwError(() => error);
      })
    );
  }

  create(input: CreateStudentInput): Observable<StudentRecord> {
    const payload = mapCreateInputToRequestDto(input);
    return this.http.post<StudentResponseDto>(this.studentsUrl, payload).pipe(
      map((dto) =>
        mapStudentDtoToRecord(dto, {
          className: input.className.trim() || undefined,
          guardianName: input.guardianName.trim() || undefined,
          guardianPhone: dto.phone ?? input.guardianPhone.trim(),
        })
      ),
      catchError((error: unknown) => {
        if (this.shouldFallbackToDemo(error) && this.demoFallback) {
          return this.demoFallback.create(input);
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
