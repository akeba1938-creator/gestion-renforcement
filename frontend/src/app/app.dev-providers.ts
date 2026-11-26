import { Provider } from '@angular/core';
import { ABSENCES_DEMO_FALLBACK } from './features/absences/data-access/absences-demo-fallback.token';
import { AbsencesDevFallbackService } from './features/absences/data-access/absences-dev-fallback.service';
import { FINANCES_DEMO_FALLBACK } from './features/finances/data-access/finances-demo-fallback.token';
import { FinancesDevFallbackService } from './features/finances/data-access/finances-dev-fallback.service';
import { GRADES_DEMO_FALLBACK } from './features/grades/data-access/grades-demo-fallback.token';
import { GradesDevFallbackService } from './features/grades/data-access/grades-dev-fallback.service';
import { SETTINGS_DEMO_FALLBACK } from './features/settings/data-access/settings-demo-fallback.token';
import { SettingsDevFallbackService } from './features/settings/data-access/settings-dev-fallback.service';
import { STUDENTS_DEMO_FALLBACK } from './features/students/data-access/students-demo-fallback.token';
import { StudentsDevFallbackService } from './features/students/data-access/students-dev-fallback.service';

/**
 * Development-only providers enabling clean local demo fallback when no backend
 * is running. Replaced by `app.dev-providers.prod.ts` during production builds.
 */
export const devProviders: readonly Provider[] = [
  { provide: STUDENTS_DEMO_FALLBACK, useExisting: StudentsDevFallbackService },
  { provide: FINANCES_DEMO_FALLBACK, useExisting: FinancesDevFallbackService },
  { provide: ABSENCES_DEMO_FALLBACK, useExisting: AbsencesDevFallbackService },
  { provide: GRADES_DEMO_FALLBACK, useExisting: GradesDevFallbackService },
  { provide: SETTINGS_DEMO_FALLBACK, useExisting: SettingsDevFallbackService },
];
