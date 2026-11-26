import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import {
  MfaEnrollmentPayload,
  MfaVerifyResult,
  NotificationPreferences,
  SchoolSettings,
  SettingsSnapshot,
  UpdateAcademicSettingsInput,
  UpdateAcademicSettingsResult,
  UpdateSchoolSettingsInput,
} from '../models/settings.model';

/**
 * Optional development-only fallback contract used exclusively when the app
 * runs in local development with an active demo session and no running backend.
 * Excluded from production builds via `app.dev-providers.prod.ts`.
 */
export interface SettingsDemoFallback {
  isDemoSessionActive(): boolean;
  loadSettings(): Observable<SettingsSnapshot>;
  updateSchool(input: UpdateSchoolSettingsInput): Observable<SchoolSettings>;
  updateAcademicSettings(
    input: UpdateAcademicSettingsInput
  ): Observable<UpdateAcademicSettingsResult>;
  enrollMfa(): Observable<MfaEnrollmentPayload>;
  verifyMfa(code: string): Observable<MfaVerifyResult>;
  updatePreferences(preferences: NotificationPreferences): Observable<NotificationPreferences>;
}

export const SETTINGS_DEMO_FALLBACK = new InjectionToken<SettingsDemoFallback>(
  'SETTINGS_DEMO_FALLBACK'
);
