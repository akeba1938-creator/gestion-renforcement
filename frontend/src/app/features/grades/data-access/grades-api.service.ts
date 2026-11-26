import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, switchMap, throwError } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api.config';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  AcademicReportViewDto,
  BackendPageResponse,
  ComputeReportCardsRequestDto,
  DEFAULT_GRADE_TERMS,
  EvaluationResponseDto,
  GenerateReportCardsInput,
  GenerateReportCardsResult,
  GradeClassOption,
  GradeEntryStudentOption,
  GradeSubjectOption,
  GradesListQuery,
  GradesPageResult,
  ReportCardResponseDto,
  SaveEvaluationGradesInput,
  SaveEvaluationGradesResult,
  StudentGradeResponseDto,
  StudentReportCardRecord,
  buildKpisFromAcademicReport,
  computeKpisFromRecords,
  isUuid,
  mapReportCardDtoToRecord,
  mapSaveEvaluationEntriesToUpsertDto,
  mapSaveEvaluationInputToCreateDto,
  parseNumericValue,
  roundToTwoDecimals,
} from '../models/grades.model';
import { GRADES_DEMO_FALLBACK } from './grades-demo-fallback.token';

const BACKEND_UNAVAILABLE_STATUSES = new Set([0, 200, 404, 502, 503, 504]);

/**
 * Strictly typed HTTP client aligned with backend:
 * - `ReportCardController` (`/api/v1/grades/report-cards`, `/api/v1/grades/report-cards/compute`)
 * - `EvaluationController` (`/api/v1/grades/evaluations`, `/api/v1/grades/evaluations/{id}/entries`)
 * - `ReportingController` (`/api/v1/reporting/academic`)
 *
 * Tenant isolation is enforced strictly server-side from the authenticated Bearer token;
 * no `tenantId` header, query param, or payload property is ever sent by the client.
 *
 * In local development with an active demo login session and no backend running,
 * delegates transparently to the optional dev-only `GRADES_DEMO_FALLBACK` provider
 * (stripped completely from production builds via `fileReplacements`).
 */
@Injectable({ providedIn: 'root' })
export class GradesApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly demoFallback = inject(GRADES_DEMO_FALLBACK, { optional: true });

  private get gradesBaseUrl(): string {
    return `${this.apiBaseUrl.replace(/\/+$/, '')}/grades`;
  }

  private get reportingAcademicUrl(): string {
    return `${this.apiBaseUrl.replace(/\/+$/, '')}/reporting/academic`;
  }

  list(query: GradesListQuery): Observable<GradesPageResult> {
    let reportCardParams = new HttpParams()
      .set('page', String(Math.max(0, Math.trunc(query.page))))
      .set('size', String(Math.max(1, Math.min(100, Math.trunc(query.size)))))
      .set('sortBy', query.sortBy ?? 'rankInClass')
      .set('direction', query.direction ?? 'ASC');

    let evaluationParams = new HttpParams()
      .set('page', '0')
      .set('size', '50')
      .set('sortBy', 'evaluationDate')
      .set('direction', 'DESC');

    let academicReportParams = new HttpParams();

    if (query.evaluationPeriodId && isUuid(query.evaluationPeriodId)) {
      reportCardParams = reportCardParams.set('evaluationPeriodId', query.evaluationPeriodId);
      evaluationParams = evaluationParams.set('evaluationPeriodId', query.evaluationPeriodId);
      academicReportParams = academicReportParams.set(
        'evaluationPeriodId',
        query.evaluationPeriodId
      );
    }

    if (query.classRoomId && isUuid(query.classRoomId)) {
      reportCardParams = reportCardParams.set('classRoomId', query.classRoomId);
      evaluationParams = evaluationParams.set('classRoomId', query.classRoomId);
      academicReportParams = academicReportParams.set('classRoomId', query.classRoomId);
    }

    if (query.subjectId && isUuid(query.subjectId)) {
      evaluationParams = evaluationParams.set('subjectId', query.subjectId);
    }

    const reportCards$ = this.http.get<BackendPageResponse<ReportCardResponseDto>>(
      `${this.gradesBaseUrl}/report-cards`,
      { params: reportCardParams }
    );

    const evaluations$ = this.http
      .get<BackendPageResponse<EvaluationResponseDto>>(`${this.gradesBaseUrl}/evaluations`, {
        params: evaluationParams,
      })
      .pipe(catchError(() => of<BackendPageResponse<EvaluationResponseDto> | null>(null)));

    const academicReport$ = this.http
      .get<AcademicReportViewDto>(this.reportingAcademicUrl, { params: academicReportParams })
      .pipe(catchError(() => of<AcademicReportViewDto | null>(null)));

    return forkJoin({
      reportCards: reportCards$,
      evaluations: evaluations$,
      academicReport: academicReport$,
    }).pipe(
      map(({ reportCards, evaluations, academicReport }) => {
        const mapped = reportCards.content.map(mapReportCardDtoToRecord);

        const classMap = new Map<string, GradeClassOption>();
        const subjectMap = new Map<string, GradeSubjectOption>();
        const studentMap = new Map<string, GradeEntryStudentOption>();

        for (const card of mapped) {
          if (!classMap.has(card.classRoomId)) {
            classMap.set(card.classRoomId, {
              id: card.classRoomId,
              code: card.classRoomCode,
              name: card.classRoomName,
            });
          }
          if (!studentMap.has(card.studentId)) {
            studentMap.set(card.studentId, {
              studentId: card.studentId,
              studentFullName: card.studentFullName,
              studentInitials: card.studentInitials,
              studentMatricule: card.studentMatricule,
              classRoomId: card.classRoomId,
              classRoomName: card.classRoomName,
            });
          }
          for (const subject of card.subjectResults) {
            if (!subjectMap.has(subject.subjectId)) {
              subjectMap.set(subject.subjectId, {
                id: subject.subjectId,
                code: subject.subjectCode,
                name: subject.subjectName,
                defaultCoefficient: subject.coefficient,
              });
            }
          }
        }

        if (evaluations) {
          for (const ev of evaluations.content) {
            if (!classMap.has(ev.classRoomId)) {
              classMap.set(ev.classRoomId, {
                id: ev.classRoomId,
                code: ev.classRoomCode,
                name: ev.classRoomName,
              });
            }
            if (!subjectMap.has(ev.subjectId)) {
              subjectMap.set(ev.subjectId, {
                id: ev.subjectId,
                code: ev.subjectCode,
                name: ev.subjectName,
                defaultCoefficient: Math.max(1, Math.trunc(ev.weight || 1)),
              });
            }
          }
        }

        const searchLower = query.search?.trim().toLowerCase() ?? '';
        const classFilter = query.classRoomId?.trim() ?? '';
        const subjectFilter = query.subjectId?.trim() ?? '';

        const filtered = mapped.filter((card) => {
          if (
            classFilter &&
            classFilter !== 'ALL' &&
            card.classRoomId !== classFilter &&
            card.classRoomCode !== classFilter &&
            card.classRoomName !== classFilter
          ) {
            return false;
          }
          if (
            subjectFilter &&
            subjectFilter !== 'ALL' &&
            !card.subjectResults.some(
              (sub) =>
                sub.subjectId === subjectFilter ||
                sub.subjectCode === subjectFilter ||
                sub.subjectName === subjectFilter
            )
          ) {
            return false;
          }
          if (!searchLower) {
            return true;
          }
          return (
            card.studentFullName.toLowerCase().includes(searchLower) ||
            card.studentMatricule.toLowerCase().includes(searchLower) ||
            card.classRoomName.toLowerCase().includes(searchLower)
          );
        });

        const totalEvaluations = evaluations?.totalElements ?? 0;
        const enteredGradesCount = mapped.reduce(
          (sum, card) =>
            sum + card.subjectResults.reduce((s, subject) => s + subject.evaluationCount, 0),
          0
        );

        const kpis = academicReport
          ? buildKpisFromAcademicReport(
              academicReport,
              query.term,
              totalEvaluations,
              enteredGradesCount
            )
          : computeKpisFromRecords(mapped, query.term, totalEvaluations);

        const clientFiltered =
          Boolean(searchLower) ||
          Boolean(subjectFilter && subjectFilter !== 'ALL') ||
          Boolean(classFilter && classFilter !== 'ALL' && !isUuid(classFilter));

        return {
          content: filtered,
          page: reportCards.page,
          size: reportCards.size,
          totalElements: clientFiltered ? filtered.length : reportCards.totalElements,
          totalPages: clientFiltered
            ? filtered.length > 0
              ? 1
              : 0
            : reportCards.totalPages,
          first: reportCards.first,
          last: clientFiltered ? true : reportCards.last,
          kpis,
          classes: Array.from(classMap.values()).sort((a, b) => a.name.localeCompare(b.name, 'fr')),
          subjects: Array.from(subjectMap.values()).sort((a, b) =>
            a.name.localeCompare(b.name, 'fr')
          ),
          terms: DEFAULT_GRADE_TERMS,
          studentsForEntry: Array.from(studentMap.values()),
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

  getReportCard(reportCardId: string): Observable<StudentReportCardRecord> {
    const encodedId = encodeURIComponent(reportCardId.trim());
    return this.http
      .get<ReportCardResponseDto>(`${this.gradesBaseUrl}/report-cards/${encodedId}`)
      .pipe(
        map(mapReportCardDtoToRecord),
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.getReportCard(reportCardId);
          }
          return throwError(() => error);
        })
      );
  }

  saveEvaluationGrades(input: SaveEvaluationGradesInput): Observable<SaveEvaluationGradesResult> {
    const createPayload = mapSaveEvaluationInputToCreateDto(input);
    const upsertPayload = mapSaveEvaluationEntriesToUpsertDto(input.entries);

    return this.http
      .post<EvaluationResponseDto>(`${this.gradesBaseUrl}/evaluations`, createPayload)
      .pipe(
        switchMap((created) =>
          this.http
            .put<readonly StudentGradeResponseDto[]>(
              `${this.gradesBaseUrl}/evaluations/${encodeURIComponent(created.id)}/entries`,
              upsertPayload
            )
            .pipe(
              map((savedEntries) => {
                const scores = savedEntries
                  .map((entry) => parseNumericValue(entry.score))
                  .filter((val): val is number => val !== null);
                const classAverage =
                  scores.length > 0
                    ? roundToTwoDecimals(scores.reduce((s, v) => s + v, 0) / scores.length)
                    : null;
                return {
                  evaluationId: created.id,
                  title: created.title,
                  savedCount: savedEntries.length,
                  classAverage,
                };
              })
            )
        ),
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.saveEvaluationGrades(input);
          }
          return throwError(() => error);
        })
      );
  }

  generateReportCards(input: GenerateReportCardsInput): Observable<GenerateReportCardsResult> {
    const payload: ComputeReportCardsRequestDto = {
      classRoomId: input.classRoomId.trim(),
      evaluationPeriodId: input.evaluationPeriodId.trim(),
    };

    return this.http
      .post<readonly ReportCardResponseDto[]>(`${this.gradesBaseUrl}/report-cards/compute`, payload)
      .pipe(
        map((generated) => ({
          generatedCount: generated.length,
          termCode: input.termCode,
          classRoomName: generated[0]?.classRoomName ?? 'Classe sélectionnée',
        })),
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.generateReportCards(input);
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
