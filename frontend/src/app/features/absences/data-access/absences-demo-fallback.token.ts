import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AbsenceRecord,
  AbsencesListQuery,
  AbsencesPageResult,
  JustifyAbsenceInput,
  SubmitRollCallInput,
  SubmitRollCallResult,
} from '../models/absences.model';

/**
 * Optional development-only fallback contract used exclusively when the app
 * runs in local development with an active demo session and no running backend.
 * Excluded from production builds via `app.dev-providers.prod.ts`.
 */
export interface AbsencesDemoFallback {
  isDemoSessionActive(): boolean;
  list(query: AbsencesListQuery): Observable<AbsencesPageResult>;
  submitRollCall(input: SubmitRollCallInput): Observable<SubmitRollCallResult>;
  justifyRecord(input: JustifyAbsenceInput): Observable<AbsenceRecord>;
}

export const ABSENCES_DEMO_FALLBACK = new InjectionToken<AbsencesDemoFallback>(
  'ABSENCES_DEMO_FALLBACK'
);
