import { DOCUMENT } from '@angular/common';
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { API_BASE_URL } from '../config/api.config';

function isOwnApiRequest(requestUrl: string, apiBaseUrl: string, appOrigin: string): boolean {
  try {
    const resolvedOrigin =
      appOrigin && appOrigin !== 'null' ? appOrigin : 'http://localhost';
    const apiUrl = new URL(apiBaseUrl, resolvedOrigin);
    const targetUrl = new URL(requestUrl, resolvedOrigin);

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
  } catch {
    return false;
  }
}

/**
 * Attaches the in-memory Bearer token ONLY to requests targeting our own API
 * (exact origin match + path segment boundary on `API_BASE_URL`).
 * Never attaches a client-chosen tenant header: the server resolves the tenant
 * strictly from the authenticated token.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).token();
  if (!token) {
    return next(req);
  }

  const apiBaseUrl = inject(API_BASE_URL);
  const document = inject(DOCUMENT);
  const appOrigin = document.location?.origin ?? 'http://localhost';

  if (!isOwnApiRequest(req.url, apiBaseUrl, appOrigin)) {
    return next(req);
  }

  const authorizedRequest = req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`,
    },
  });

  return next(authorizedRequest);
};
