import { DOCUMENT } from '@angular/common';
import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { DEFAULT_POST_LOGIN_URL, sanitizeReturnUrl } from '../auth/return-url.util';
import { API_BASE_URL } from '../config/api.config';
import { ProblemDetailError } from '../models/problem-detail.model';

const IS_AUTH_RETRY = new HttpContextToken<boolean>(() => false);

const AUTH_BYPASS_SEGMENTS: readonly string[] = [
  '/auth/login',
  '/auth/platform/login',
  '/auth/refresh',
  '/auth/logout',
  '/auth/csrf',
  '/auth/mfa/verify',
  '/auth/mfa/enroll',
];

function resolveRequestUrl(requestUrl: string, appOrigin: string): URL | null {
  try {
    const resolvedOrigin = appOrigin && appOrigin !== 'null' ? appOrigin : 'http://localhost';
    return new URL(requestUrl, resolvedOrigin);
  } catch {
    return null;
  }
}

function isOwnApiRequest(requestUrl: string, apiBaseUrl: string, appOrigin: string): boolean {
  const apiUrl = resolveRequestUrl(apiBaseUrl, appOrigin);
  const targetUrl = resolveRequestUrl(requestUrl, appOrigin);
  if (!apiUrl || !targetUrl) {
    return false;
  }

  if (targetUrl.origin !== apiUrl.origin) {
    return false;
  }

  const normalizedApiPath = apiUrl.pathname.replace(/\/+$/, '');
  if (normalizedApiPath === '') {
    return true;
  }

  return (
    targetUrl.pathname === normalizedApiPath ||
    targetUrl.pathname.startsWith(`${normalizedApiPath}/`)
  );
}

function isAuthBypassEndpoint(requestUrl: string, apiBaseUrl: string, appOrigin: string): boolean {
  const apiUrl = resolveRequestUrl(apiBaseUrl, appOrigin);
  const targetUrl = resolveRequestUrl(requestUrl, appOrigin);
  if (!apiUrl || !targetUrl) {
    return false;
  }

  const normalizedApiPath = apiUrl.pathname.replace(/\/+$/, '');
  const relativePath = targetUrl.pathname.slice(normalizedApiPath.length);
  return AUTH_BYPASS_SEGMENTS.some(
    (segment) => relativePath === segment || relativePath.startsWith(`${segment}/`)
  );
}

function isUnauthorizedError(error: unknown): boolean {
  if (error instanceof HttpErrorResponse) {
    return error.status === 401;
  }
  if (error instanceof ProblemDetailError) {
    return error.problem.status === 401;
  }
  return false;
}

function redirectToLogin(router: Router | null): void {
  if (!router) {
    return;
  }
  const currentUrl = router.url;
  if (currentUrl.startsWith('/login')) {
    return;
  }
  const safeReturnUrl = sanitizeReturnUrl(currentUrl);
  const queryParams =
    safeReturnUrl !== DEFAULT_POST_LOGIN_URL ? { returnUrl: safeReturnUrl } : undefined;
  void router.navigate(['/login'], { queryParams });
}

/**
 * Attaches the in-memory Bearer token ONLY to requests targeting our own API
 * (exact origin match + path segment boundary on `API_BASE_URL`).
 * Never attaches a client-chosen tenant header: the server resolves the tenant
 * strictly from the authenticated token.
 *
 * On `401 Unauthorized` for authenticated API requests, performs a single-flight
 * cookie-backed refresh via `AuthService.refresh()`, retries the original request
 * once, or clears the session and redirects to `/login` on failure.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const apiBaseUrl = inject(API_BASE_URL);
  const document = inject(DOCUMENT);
  const router = inject(Router, { optional: true });
  const appOrigin = document.location?.origin ?? 'http://localhost';

  if (!isOwnApiRequest(req.url, apiBaseUrl, appOrigin)) {
    return next(req);
  }

  if (isAuthBypassEndpoint(req.url, apiBaseUrl, appOrigin)) {
    return next(req);
  }

  const token = authService.token();
  const outboundRequest =
    token && !req.headers.has('Authorization')
      ? req.clone({
          setHeaders: {
            Authorization: `Bearer ${token}`,
          },
        })
      : req;

  return next(outboundRequest).pipe(
    catchError((error: unknown) => {
      if (!isUnauthorizedError(error) || !token || req.context.get(IS_AUTH_RETRY)) {
        return throwError(() => error);
      }

      return authService.refresh().pipe(
        switchMap(() => {
          const refreshedToken = authService.token();
          if (!refreshedToken) {
            authService.clearSession();
            redirectToLogin(router);
            return throwError(() => error);
          }

          const retriedRequest = req.clone({
            context: req.context.set(IS_AUTH_RETRY, true),
            setHeaders: {
              Authorization: `Bearer ${refreshedToken}`,
            },
          });
          return next(retriedRequest);
        }),
        catchError((refreshError: unknown) => {
          authService.clearSession();
          redirectToLogin(router);
          return throwError(() => refreshError);
        })
      );
    })
  );
};
