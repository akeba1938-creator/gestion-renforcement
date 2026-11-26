import { Provider } from '@angular/core';
import { STUDENTS_DEMO_FALLBACK } from './features/students/data-access/students-demo-fallback.token';
import { StudentsDevFallbackService } from './features/students/data-access/students-dev-fallback.service';

/**
 * Development-only providers enabling clean local demo fallback when no backend
 * is running. Replaced by `app.dev-providers.prod.ts` during production builds.
 */
export const devProviders: readonly Provider[] = [
  { provide: STUDENTS_DEMO_FALLBACK, useExisting: StudentsDevFallbackService },
];
