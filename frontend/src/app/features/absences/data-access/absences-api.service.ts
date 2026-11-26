import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, switchMap, throwError } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api.config';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  AbsenceClassOption,
  AbsenceRecord,
  AbsencesListQuery,
  AbsencesPageResult,
  AttendanceRecordResponseDto,
  AttendanceReportViewDto,
  AttendanceSessionResponseDto,
  BackendPageResponse,
  ClassRoomResponseDto,
  CreateAttendanceSessionRequestDto,
  DEFAULT_ABSENCE_CLASSES,
  JustifyAbsenceInput,
  JustifyAttendanceRecordRequestDto,
  SubmitRollCallInput,
  SubmitRollCallResult,
  UpsertAttendanceRecordsRequestDto,
  buildKpisFromAttendanceReport,
  buildStudentInitials,
  computeFallbackKpisFromSessions,
  extractAbsenceRecordsFromSessions,
  extractRosterFromSessions,
  formatTimeSlotLabel,
  getTodayDakarIsoDate,
  resolveAbsenceStatus,
} from '../models/absences.model';
import { ABSENCES_DEMO_FALLBACK } from './absences-demo-fallback.token';

const BACKEND_UNAVAILABLE_STATUSES = new Set([0, 200, 404, 502, 503, 504]);

/**
 * Strictly typed HTTP client aligned with backend:
 * - `AttendanceController` (`/api/v1/attendance/sessions`, `/api/v1/attendance/records/{id}/justify`)
 * - `ReportingController` (`/api/v1/reporting/attendance`)
 * - `ClassRoomController` (`/api/v1/academic/classes`)
 *
 * Tenant isolation is enforced strictly server-side from the authenticated Bearer token;
 * no `tenantId` header or query param is ever sent by the client.
 *
 * In local development with an active demo login session and no backend running,
 * delegates transparently to the optional dev-only `ABSENCES_DEMO_FALLBACK` provider
 * (which is stripped completely from production builds via `fileReplacements`).
 */
@Injectable({ providedIn: 'root' })
export class AbsencesApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly demoFallback = inject(ABSENCES_DEMO_FALLBACK, { optional: true });

  private get attendanceBaseUrl(): string {
    return `${this.apiBaseUrl.replace(/\/+$/, '')}/attendance`;
  }

  private get reportingAttendanceUrl(): string {
    return `${this.apiBaseUrl.replace(/\/+$/, '')}/reporting/attendance`;
  }

  private get academicClassesUrl(): string {
    return `${this.apiBaseUrl.replace(/\/+$/, '')}/academic/classes`;
  }

  list(query: AbsencesListQuery): Observable<AbsencesPageResult> {
    let sessionParams = new HttpParams()
      .set('page', '0')
      .set('size', '50')
      .set('sortBy', query.sortBy ?? 'sessionDate')
      .set('direction', query.direction ?? 'DESC');

    if (query.classRoomId?.trim()) {
      sessionParams = sessionParams.set('classRoomId', query.classRoomId.trim());
    }
    if (query.date?.trim()) {
      sessionParams = sessionParams
        .set('fromDate', query.date.trim())
        .set('toDate', query.date.trim());
    }

    let reportParams = new HttpParams();
    if (query.classRoomId?.trim()) {
      reportParams = reportParams.set('classRoomId', query.classRoomId.trim());
    }
    if (query.date?.trim()) {
      reportParams = reportParams
        .set('fromDate', query.date.trim())
        .set('toDate', query.date.trim());
    }

    const classesParams = new HttpParams()
      .set('page', '0')
      .set('size', '100')
      .set('sortBy', 'name')
      .set('direction', 'ASC');

    const sessions$ = this.http.get<BackendPageResponse<AttendanceSessionResponseDto>>(
      `${this.attendanceBaseUrl}/sessions`,
      { params: sessionParams }
    );

    const report$ = this.http
      .get<AttendanceReportViewDto>(this.reportingAttendanceUrl, { params: reportParams })
      .pipe(catchError(() => of<AttendanceReportViewDto | null>(null)));

    const classes$ = this.http
      .get<BackendPageResponse<ClassRoomResponseDto>>(this.academicClassesUrl, {
        params: classesParams,
      })
      .pipe(catchError(() => of<BackendPageResponse<ClassRoomResponseDto> | null>(null)));

    return forkJoin({
      sessions: sessions$,
      report: report$,
      classes: classes$,
    }).pipe(
      map(({ sessions, report, classes }) => {
        const allRecords = extractAbsenceRecordsFromSessions(sessions.content);
        const searchLower = query.search?.trim().toLowerCase() ?? '';
        const classFilter = query.className?.trim() ?? '';
        const statusFilter = query.status ?? 'ALL';
        const dateFilter = query.date?.trim() ?? '';

        const filtered = allRecords.filter((item) => {
          if (classFilter && item.className !== classFilter && item.classRoomId !== classFilter) {
            return false;
          }
          if (statusFilter !== 'ALL' && item.status !== statusFilter) {
            return false;
          }
          if (dateFilter && item.sessionDate !== dateFilter) {
            return false;
          }
          if (!searchLower) {
            return true;
          }
          return (
            item.studentName.toLowerCase().includes(searchLower) ||
            item.matricule.toLowerCase().includes(searchLower) ||
            item.className.toLowerCase().includes(searchLower) ||
            (item.justificationReason?.toLowerCase().includes(searchLower) ?? false) ||
            (item.justificationDocumentRef?.toLowerCase().includes(searchLower) ?? false) ||
            (item.notes?.toLowerCase().includes(searchLower) ?? false)
          );
        });

        const size = Math.max(1, Math.min(100, Math.trunc(query.size || 8)));
        const totalElements = filtered.length;
        const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size);
        const requestedPage = Math.max(0, Math.trunc(query.page || 0));
        const safePage = totalPages === 0 ? 0 : Math.min(requestedPage, totalPages - 1);
        const start = safePage * size;
        const content = filtered.slice(start, start + size);

        const todayIso = getTodayDakarIsoDate();
        const kpis = report
          ? buildKpisFromAttendanceReport(report, allRecords, todayIso)
          : computeFallbackKpisFromSessions(sessions.content, allRecords, todayIso);

        const availableClasses = this.mergeClassOptions(classes?.content ?? [], sessions.content);
        const rollCallRosterByClass = extractRosterFromSessions(sessions.content);

        return {
          content,
          page: safePage,
          size,
          totalElements,
          totalPages,
          first: safePage === 0,
          last: totalPages === 0 || safePage >= totalPages - 1,
          kpis,
          availableClasses,
          rollCallRosterByClass,
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

  submitRollCall(input: SubmitRollCallInput): Observable<SubmitRollCallResult> {
    const createPayload: CreateAttendanceSessionRequestDto = {
      classRoomId: input.classRoomId,
      sessionDate: input.sessionDate,
      timeSlot: input.timeSlot,
      label: input.label?.trim() || `Appel ${input.className}`,
      notes: input.notes?.trim() || undefined,
      initializePresent: true,
    };

    const recordsPayload: UpsertAttendanceRecordsRequestDto = {
      records: input.entries.map((entry) => ({
        studentId: entry.studentId,
        status: entry.status,
        lateMinutes:
          entry.status === 'LATE' && entry.lateMinutes && entry.lateMinutes > 0
            ? Math.trunc(entry.lateMinutes)
            : undefined,
        notes: entry.notes?.trim() || undefined,
      })),
    };

    return this.http
      .post<AttendanceSessionResponseDto>(`${this.attendanceBaseUrl}/sessions`, createPayload)
      .pipe(
        switchMap((createdSession) =>
          this.http.put<AttendanceSessionResponseDto>(
            `${this.attendanceBaseUrl}/sessions/${encodeURIComponent(createdSession.id)}/records`,
            recordsPayload
          )
        ),
        switchMap((updatedSession) =>
          this.http.post<AttendanceSessionResponseDto>(
            `${this.attendanceBaseUrl}/sessions/${encodeURIComponent(updatedSession.id)}/submit`,
            {}
          )
        ),
        map((submittedSession) => {
          let presentCount = 0;
          let absentCount = 0;
          let lateCount = 0;
          for (const rec of submittedSession.records) {
            if (rec.status === 'PRESENT') {
              presentCount += 1;
            } else if (rec.status === 'LATE') {
              lateCount += 1;
            } else {
              absentCount += 1;
            }
          }
          return {
            sessionId: submittedSession.id,
            className: submittedSession.classRoomName?.trim() || input.className,
            sessionDate: submittedSession.sessionDate,
            timeSlot: submittedSession.timeSlot,
            presentCount,
            absentCount,
            lateCount,
          };
        }),
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.submitRollCall(input);
          }
          return throwError(() => error);
        })
      );
  }

  justifyRecord(input: JustifyAbsenceInput, existing?: AbsenceRecord): Observable<AbsenceRecord> {
    const encodedId = encodeURIComponent(input.recordId.trim());
    const payload: JustifyAttendanceRecordRequestDto = {
      reason: input.reason.trim(),
      documentRef: input.documentRef?.trim() || undefined,
    };

    return this.http
      .post<AttendanceRecordResponseDto>(
        `${this.attendanceBaseUrl}/records/${encodedId}/justify`,
        payload
      )
      .pipe(
        map((dto) => this.mapJustifiedDtoToRecord(dto, existing)),
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.justifyRecord(input);
          }
          return throwError(() => error);
        })
      );
  }

  private mapJustifiedDtoToRecord(
    dto: AttendanceRecordResponseDto,
    existing?: AbsenceRecord
  ): AbsenceRecord {
    const fullName =
      `${dto.studentFirstName} ${dto.studentLastName}`.trim() || existing?.studentName || 'Élève';
    const timeSlot = existing?.timeSlot ?? 'MORNING';

    return {
      id: dto.id,
      sessionId: dto.sessionId,
      studentId: dto.studentId,
      studentName: fullName,
      studentInitials: buildStudentInitials(fullName),
      matricule: dto.studentMatricule || existing?.matricule || '—',
      classRoomId: existing?.classRoomId ?? '',
      className: existing?.className ?? 'Non affectée',
      sessionDate: existing?.sessionDate ?? getTodayDakarIsoDate(),
      recordedAtUtc: dto.createdAt || existing?.recordedAtUtc || new Date().toISOString(),
      timeSlot,
      slotLabel: existing?.slotLabel ?? formatTimeSlotLabel(timeSlot),
      subjectName: existing?.subjectName ?? null,
      teacherName: existing?.teacherName ?? null,
      rawStatus: dto.status,
      status: resolveAbsenceStatus(dto.status, dto.justified),
      lateMinutes: dto.lateMinutes,
      justified: dto.justified || dto.status === 'EXCUSED',
      justificationReason: dto.justificationReason,
      justificationDocumentRef: dto.justificationDocumentRef,
      justifiedAtUtc: dto.justifiedAt,
      notes: dto.notes ?? existing?.notes ?? null,
    };
  }

  private mergeClassOptions(
    classes: readonly ClassRoomResponseDto[],
    sessions: readonly AttendanceSessionResponseDto[]
  ): readonly AbsenceClassOption[] {
    const byId = new Map<string, AbsenceClassOption>();
    for (const cls of classes) {
      byId.set(cls.id, { id: cls.id, code: cls.code, name: cls.name });
    }
    for (const sess of sessions) {
      if (!byId.has(sess.classRoomId)) {
        const name = sess.classRoomName?.trim() || sess.classRoomCode?.trim();
        if (name) {
          byId.set(sess.classRoomId, {
            id: sess.classRoomId,
            code: sess.classRoomCode?.trim() || name,
            name,
          });
        }
      }
    }
    if (byId.size === 0) {
      return DEFAULT_ABSENCE_CLASSES;
    }
    return Array.from(byId.values()).sort((a, b) => a.name.localeCompare(b.name, 'fr'));
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
