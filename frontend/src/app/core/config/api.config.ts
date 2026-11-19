import { InjectionToken } from '@angular/core';

/**
 * Base URL of our own backend API.
 * Used by `authInterceptor` to ensure Bearer tokens are never sent to third-party origins.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => '/api/v1',
});
