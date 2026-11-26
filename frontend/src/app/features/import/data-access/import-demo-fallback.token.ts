import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateImportJobInput,
  ImportFileDownload,
  ImportJobDto,
  ImportJobRowsPage,
  ImportTemplateDto,
  ImportType,
  ListImportRowsQuery,
} from '../models/import.model';

/**
 * Optional development-only fallback contract used exclusively when the app
 * runs in local development with an active demo session and no running backend.
 * Excluded completely from production builds via `app.dev-providers.prod.ts`.
 */
export interface ImportDemoFallback {
  isDemoSessionActive(): boolean;
  listTemplates(): Observable<readonly ImportTemplateDto[]>;
  downloadTemplate(importType: ImportType): Observable<ImportFileDownload>;
  createImportJob(input: CreateImportJobInput): Observable<ImportJobDto>;
  getJob(jobId: string): Observable<ImportJobDto>;
  listJobRows(jobId: string, query?: ListImportRowsQuery): Observable<ImportJobRowsPage>;
  executeJob(jobId: string, idempotencyKey?: string): Observable<ImportJobDto>;
  cancelJob(jobId: string, reason: string): Observable<ImportJobDto>;
  downloadErrorReport(jobId: string): Observable<ImportFileDownload>;
}

export const IMPORT_DEMO_FALLBACK = new InjectionToken<ImportDemoFallback>(
  'IMPORT_DEMO_FALLBACK'
);
