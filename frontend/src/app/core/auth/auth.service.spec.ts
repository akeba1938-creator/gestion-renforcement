import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { AuthUser } from '../models/user.model';
import { AuthService } from './auth.service';

const DEMO_USER: AuthUser = {
  id: 'usr-demo-1',
  fullName: 'Direction Démo',
  email: 'direction@ecole-demo.invalid',
  role: 'DIRECTOR',
  tenantId: 'tenant-demo-1',
  tenantName: 'École Démo',
};

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AuthService);
  });

  it('starts unauthenticated with null token and user', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(service.user()).toBeNull();
    expect(service.role()).toBeNull();
  });

  it('stores token and user in memory only, never in localStorage or sessionStorage', () => {
    const localSetSpy = vi.spyOn(Storage.prototype, 'setItem');

    service.setSession('jwt-memory-only', DEMO_USER);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.token()).toBe('jwt-memory-only');
    expect(service.user()).toEqual(DEMO_USER);
    expect(service.role()).toBe('DIRECTOR');
    expect(localSetSpy).not.toHaveBeenCalled();

    localSetSpy.mockRestore();
  });

  it('evaluates hasAnyRole against the in-memory user role', () => {
    expect(service.hasAnyRole(['DIRECTOR'])).toBe(false);

    service.setSession('jwt-memory-only', DEMO_USER);

    expect(service.hasAnyRole(['DIRECTOR', 'PROMOTER'])).toBe(true);
    expect(service.hasAnyRole(['CASHIER', 'PARENT'])).toBe(false);
  });

  it('clears the in-memory session on clearSession()', () => {
    service.setSession('jwt-memory-only', DEMO_USER);
    service.clearSession();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(service.user()).toBeNull();
  });
});
