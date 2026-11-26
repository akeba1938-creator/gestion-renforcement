import { Provider } from '@angular/core';

/**
 * Production override: strips all development-only demo fallback providers and datasets.
 */
export const devProviders: readonly Provider[] = [];
