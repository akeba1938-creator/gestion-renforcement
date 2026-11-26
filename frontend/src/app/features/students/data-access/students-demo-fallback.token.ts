import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateStudentInput,
  StudentRecord,
  StudentsListQuery,
  StudentsPageResult,
} from '../models/students.model';

/**
 * Optional development-only fallback contract used exclusively when the app
 * runs in local development with an active demo session and no running backend.
 * Excluded from production builds via `app.dev-providers.prod.ts`.
 */
export interface StudentsDemoFallback {
  isDemoSessionActive(): boolean;
  list(query: StudentsListQuery): Observable<StudentsPageResult>;
  getById(studentId: string): Observable<StudentRecord>;
  create(input: CreateStudentInput): Observable<StudentRecord>;
}

export const STUDENTS_DEMO_FALLBACK = new InjectionToken<StudentsDemoFallback>(
  'STUDENTS_DEMO_FALLBACK'
);
