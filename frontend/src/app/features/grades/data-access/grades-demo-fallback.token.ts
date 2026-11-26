import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import {
  GenerateReportCardsInput,
  GenerateReportCardsResult,
  GradesListQuery,
  GradesPageResult,
  SaveEvaluationGradesInput,
  SaveEvaluationGradesResult,
  StudentReportCardRecord,
} from '../models/grades.model';

/**
 * Optional development-only fallback contract used exclusively when the app
 * runs in local development with an active demo session and no running backend.
 * Excluded from production builds via `app.dev-providers.prod.ts`.
 */
export interface GradesDemoFallback {
  isDemoSessionActive(): boolean;
  list(query: GradesListQuery): Observable<GradesPageResult>;
  getReportCard(reportCardId: string): Observable<StudentReportCardRecord>;
  saveEvaluationGrades(input: SaveEvaluationGradesInput): Observable<SaveEvaluationGradesResult>;
  generateReportCards(input: GenerateReportCardsInput): Observable<GenerateReportCardsResult>;
}

export const GRADES_DEMO_FALLBACK = new InjectionToken<GradesDemoFallback>('GRADES_DEMO_FALLBACK');
