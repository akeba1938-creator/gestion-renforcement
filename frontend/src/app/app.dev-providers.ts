import { Provider } from '@angular/core';
import { FINANCES_DEMO_FALLBACK } from './features/finances/data-access/finances-demo-fallback.token';
import { FinancesDevFallbackService } from './features/finances/data-access/finances-dev-fallback.service';
import { STUDENTS_DEMO_FALLBACK } from './features/students/data-access/students-demo-fallback.token';
import { StudentsDevFallbackService } from './features/students/data-access/students-dev-fallback.service';

/**
 * Development-only providers enabling clean local demo fallback when no backend
 * is running. Replaced by `app.dev-providers.prod.ts` during production builds.
 */
export const devProviders: readonly Provider[] = [
  { provide: STUDENTS_DEMO_FALLBACK, useExisting: StudentsDevFallbackService },
  { provide: FINANCES_DEMO_FALLBACK, useExisting: FinancesDevFallbackService },
];
