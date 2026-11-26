import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { DEFAULT_POST_LOGIN_URL, sanitizeReturnUrl } from '../auth/return-url.util';

/**
 * Route guard checking in-memory authentication state.
 * Redirects unauthenticated users to /login while preserving a safe `returnUrl`.
 * Note: UI navigation guard only; real access control is enforced by the backend.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  const safeReturnUrl = sanitizeReturnUrl(state?.url);
  if (safeReturnUrl !== DEFAULT_POST_LOGIN_URL) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: safeReturnUrl } });
  }

  return router.createUrlTree(['/login']);
};
