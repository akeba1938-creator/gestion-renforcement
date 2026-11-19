import { Injectable, computed, signal } from '@angular/core';
import { AuthUser, UserRole } from '../models/user.model';

/**
 * In-memory authentication state.
 *
 * Security rules:
 * - Access tokens and user details are stored strictly in memory via signals,
 *   never in localStorage or sessionStorage.
 * - Frontend role checks and route guards are strictly for UI/display ergonomics
 *   (affichage) and NEVER constitute a security barrier: all authorization and
 *   tenant isolation checks are enforced server-side from the verified token.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenSignal = signal<string | null>(null);
  private readonly userSignal = signal<AuthUser | null>(null);

  readonly token = this.tokenSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(
    () => this.tokenSignal() !== null && this.userSignal() !== null
  );
  readonly role = computed<UserRole | null>(() => this.userSignal()?.role ?? null);

  setSession(token: string, user: AuthUser): void {
    this.tokenSignal.set(token);
    this.userSignal.set(user);
  }

  clearSession(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
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
}
