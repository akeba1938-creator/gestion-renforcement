import { DOCUMENT } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap, throwError } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  AuthResponse,
  AuthenticatedUserResponse,
  CsrfTokenResponse,
  MfaEnrollResponse,
  MfaVerifyRequest,
  PlatformLoginRequest,
  TenantLoginRequest,
  mapBackendUserToAuthUser,
} from '../models/auth.model';
import { ProblemDetailError } from '../models/problem-detail.model';
import { AuthUser, UserRole } from '../models/user.model';

const DEFAULT_CSRF_COOKIE_NAME = 'XSRF-TOKEN';
const DEFAULT_CSRF_HEADER_NAME = 'X-XSRF-TOKEN';

/**
 * In-memory authentication state and `/api/v1/auth` client.
 *
 * Security rules:
 * - Access tokens, temporary MFA tokens, and user details are stored strictly in memory
 *   via signals, never in localStorage or sessionStorage.
 * - Refresh tokens are managed exclusively by the backend via `HttpOnly` cookies
 *   (`withCredentials: true` + CSRF header on `/refresh` and `/logout`).
 * - Frontend role checks and route guards are strictly for UI/display ergonomics
 *   (affichage) and NEVER constitute a security barrier: all authorization and
 *   tenant isolation checks are enforced server-side from the verified token.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient, { optional: true });
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly document = inject(DOCUMENT);

  private readonly tokenSignal = signal<string | null>(null);
  private readonly userSignal = signal<AuthUser | null>(null);
  private readonly mfaTokenSignal = signal<string | null>(null);
  private readonly mfaEnrollmentRequiredSignal = signal<boolean>(false);

  private refreshInFlight$: Observable<AuthResponse> | null = null;

  readonly token = this.tokenSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(
    () => this.tokenSignal() !== null && this.userSignal() !== null
  );
  readonly role = computed<UserRole | null>(() => this.userSignal()?.role ?? null);
  readonly hasPendingMfa = computed(() => this.mfaTokenSignal() !== null);
  readonly mfaEnrollmentRequired = this.mfaEnrollmentRequiredSignal.asReadonly();

  private get authBaseUrl(): string {
    return `${this.apiBaseUrl.replace(/\/+$/, '')}/auth`;
  }

  login(credentials: TenantLoginRequest): Observable<AuthResponse> {
    const http = this.requireHttpClient();
    const payload: TenantLoginRequest = {
      schoolCode: credentials.schoolCode.trim(),
      email: credentials.email.trim(),
      password: credentials.password,
    };

    return http
      .post<AuthResponse>(`${this.authBaseUrl}/login`, payload, {
        withCredentials: true,
      })
      .pipe(tap((response) => this.applyAuthResponse(response)));
  }

  loginPlatform(credentials: PlatformLoginRequest): Observable<AuthResponse> {
    const http = this.requireHttpClient();
    const payload: PlatformLoginRequest = {
      email: credentials.email.trim(),
      password: credentials.password,
    };

    return http
      .post<AuthResponse>(`${this.authBaseUrl}/platform/login`, payload, {
        withCredentials: true,
      })
      .pipe(tap((response) => this.applyAuthResponse(response)));
  }

  enrollMfa(): Observable<MfaEnrollResponse> {
    const http = this.requireHttpClient();
    const pendingToken = this.mfaTokenSignal() ?? this.tokenSignal();
    if (!pendingToken) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:authentication-required',
            title: 'Session MFA expirée',
            status: 401,
            detail: 'Votre session de vérification a expiré. Veuillez vous reconnecter.',
          })
      );
    }

    return http.post<MfaEnrollResponse>(
      `${this.authBaseUrl}/mfa/enroll`,
      {},
      {
        headers: new HttpHeaders({
          Authorization: `Bearer ${pendingToken}`,
        }),
      }
    );
  }

  verifyMfa(code: string): Observable<AuthResponse> {
    const http = this.requireHttpClient();
    const pendingToken = this.mfaTokenSignal();
    if (!pendingToken) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:authentication-required',
            title: 'Session MFA expirée',
            status: 401,
            detail: 'Votre session de vérification a expiré. Veuillez vous reconnecter.',
          })
      );
    }

    const payload: MfaVerifyRequest = { code: code.trim() };

    return http
      .post<AuthResponse>(`${this.authBaseUrl}/mfa/verify`, payload, {
        withCredentials: true,
        headers: new HttpHeaders({
          Authorization: `Bearer ${pendingToken}`,
        }),
      })
      .pipe(tap((response) => this.applyAuthResponse(response)));
  }

  /**
   * Single-flight session refresh backed by the backend `HttpOnly` refresh cookie
   * and CSRF protection (`X-XSRF-TOKEN`). Clears local state on failure.
   */
  refresh(): Observable<AuthResponse> {
    if (this.refreshInFlight$) {
      return this.refreshInFlight$;
    }

    const http = this.requireHttpClient();
    const request$ = this.ensureCsrfHeader(http).pipe(
      switchMap(({ headerName, token }) =>
        http.post<AuthResponse>(
          `${this.authBaseUrl}/refresh`,
          {},
          {
            withCredentials: true,
            headers: token ? new HttpHeaders({ [headerName]: token }) : undefined,
          }
        )
      ),
      tap((response) => {
        if (response.status === 'AUTHENTICATED' && response.accessToken && response.user) {
          this.applyAuthResponse(response);
        } else {
          this.clearSession();
        }
      }),
      catchError((error: unknown) => {
        this.clearSession();
        return throwError(() => error);
      }),
      finalize(() => {
        this.refreshInFlight$ = null;
      }),
      shareReplay(1)
    );

    this.refreshInFlight$ = request$;
    return request$;
  }

  /**
   * Revokes the refresh token family on the server and always clears the local
   * in-memory session, even if the network request fails.
   */
  logout(): Observable<void> {
    this.clearSession();
    if (!this.http) {
      return of(undefined);
    }

    const http = this.http;
    return this.ensureCsrfHeader(http).pipe(
      switchMap(({ headerName, token }) =>
        http.post<void>(
          `${this.authBaseUrl}/logout`,
          {},
          {
            withCredentials: true,
            headers: token ? new HttpHeaders({ [headerName]: token }) : undefined,
          }
        )
      ),
      map(() => undefined),
      catchError(() => of(undefined)),
      finalize(() => {
        this.clearSession();
      })
    );
  }

  fetchCurrentUser(): Observable<AuthUser> {
    const http = this.requireHttpClient();
    return http.get<AuthenticatedUserResponse>(`${this.authBaseUrl}/me`).pipe(
      map((dto) => mapBackendUserToAuthUser(dto)),
      tap((user) => {
        this.userSignal.set(user);
      })
    );
  }

  cancelMfaChallenge(): void {
    this.mfaTokenSignal.set(null);
    this.mfaEnrollmentRequiredSignal.set(false);
  }

  setSession(token: string, user: AuthUser): void {
    this.mfaTokenSignal.set(null);
    this.mfaEnrollmentRequiredSignal.set(false);
    this.tokenSignal.set(token);
    this.userSignal.set(user);
  }

  clearSession(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    this.mfaTokenSignal.set(null);
    this.mfaEnrollmentRequiredSignal.set(false);
  }

  /**
   * UI/display helper only — never relied upon for security.
   */
  hasAnyRole(allowedRoles: readonly UserRole[]): boolean {
    const currentRole = this.role();
    if (!currentRole) {
      return false;
    }
    return allowedRoles.includes(currentRole);
  }

  private applyAuthResponse(response: AuthResponse): void {
    if (response.status === 'AUTHENTICATED' && response.accessToken && response.user) {
      this.setSession(response.accessToken, mapBackendUserToAuthUser(response.user));
      return;
    }

    if (
      (response.status === 'MFA_REQUIRED' || response.status === 'MFA_ENROLLMENT_REQUIRED') &&
      response.mfaToken
    ) {
      this.tokenSignal.set(null);
      this.userSignal.set(null);
      this.mfaTokenSignal.set(response.mfaToken);
      this.mfaEnrollmentRequiredSignal.set(response.status === 'MFA_ENROLLMENT_REQUIRED');
    }
  }

  private ensureCsrfHeader(http: HttpClient): Observable<{ headerName: string; token: string }> {
    const cookieToken = this.readCookie(DEFAULT_CSRF_COOKIE_NAME);
    if (cookieToken) {
      return of({ headerName: DEFAULT_CSRF_HEADER_NAME, token: cookieToken });
    }

    return http
      .get<CsrfTokenResponse>(`${this.authBaseUrl}/csrf`, {
        withCredentials: true,
      })
      .pipe(
        map((response) => ({
          headerName: response.headerName?.trim() || DEFAULT_CSRF_HEADER_NAME,
          token: response.token,
        }))
      );
  }

  private readCookie(name: string): string | null {
    const cookieString = this.document?.cookie ?? '';
    if (!cookieString) {
      return null;
    }

    const prefix = `${name}=`;
    for (const part of cookieString.split(';')) {
      const trimmed = part.trim();
      if (trimmed.startsWith(prefix)) {
        const value = trimmed.slice(prefix.length).trim();
        if (!value) {
          return null;
        }
        try {
          return decodeURIComponent(value);
        } catch {
          return value;
        }
      }
    }

    return null;
  }

  private requireHttpClient(): HttpClient {
    if (!this.http) {
      throw new Error('HttpClient is required to communicate with /api/v1/auth.');
    }
    return this.http;
  }
}
