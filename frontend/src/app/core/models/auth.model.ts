import { AuthUser, UserRole } from './user.model';

export type AuthStatus = 'AUTHENTICATED' | 'MFA_REQUIRED' | 'MFA_ENROLLMENT_REQUIRED';

export type SubjectType = 'USER' | 'PLATFORM_ADMIN';

export interface TenantLoginRequest {
  readonly schoolCode: string;
  readonly email: string;
  readonly password: string;
}

export interface PlatformLoginRequest {
  readonly email: string;
  readonly password: string;
}

export interface MfaVerifyRequest {
  readonly code: string;
}

export interface AuthenticatedUserResponse {
  readonly id: string;
  readonly subjectType: SubjectType;
  readonly email: string;
  readonly firstName: string | null;
  readonly lastName: string | null;
  readonly roles: readonly string[];
  readonly mfaEnabled: boolean;
  readonly tenantId: string | null;
  readonly schoolCode: string | null;
  readonly schoolName: string | null;
}

export interface AuthResponse {
  readonly status: AuthStatus;
  readonly accessToken: string | null;
  readonly mfaToken: string | null;
  readonly expiresInSeconds: number;
  readonly mfaRequired: boolean;
  readonly mfaEnrollmentRequired: boolean;
  readonly user: AuthenticatedUserResponse | null;
}

export interface MfaEnrollResponse {
  readonly secret: string;
  readonly otpauthUri: string;
  readonly digits: number;
  readonly periodSeconds: number;
}

export interface CsrfTokenResponse {
  readonly headerName: string;
  readonly parameterName: string;
  readonly token: string;
}

const ROLE_PRIORITY: readonly UserRole[] = [
  'SAAS_ADMIN',
  'PROMOTER',
  'DIRECTOR',
  'CENSOR',
  'SECRETARY',
  'CASHIER',
  'TEACHER',
  'PARENT',
  'STUDENT',
];

const BACKEND_ROLE_ALIASES: Readonly<Record<string, UserRole>> = {
  ADMIN_SAAS: 'SAAS_ADMIN',
  SAAS_ADMIN: 'SAAS_ADMIN',
  PROMOTER: 'PROMOTER',
  DIRECTOR: 'DIRECTOR',
  CENTER_MANAGER: 'DIRECTOR',
  CENSOR: 'CENSOR',
  SECRETARY: 'SECRETARY',
  CASHIER: 'CASHIER',
  TEACHER: 'TEACHER',
  TUTOR: 'TEACHER',
  PARENT: 'PARENT',
  STUDENT: 'STUDENT',
};

export function resolvePrimaryRole(
  roles: readonly string[],
  subjectType: SubjectType
): UserRole {
  if (subjectType === 'PLATFORM_ADMIN') {
    return 'SAAS_ADMIN';
  }

  const mapped = new Set<UserRole>();
  for (const rawRole of roles) {
    const normalized = BACKEND_ROLE_ALIASES[rawRole.trim().toUpperCase()];
    if (normalized) {
      mapped.add(normalized);
    }
  }

  for (const candidate of ROLE_PRIORITY) {
    if (mapped.has(candidate)) {
      return candidate;
    }
  }

  return 'STUDENT';
}

export function mapBackendUserToAuthUser(dto: AuthenticatedUserResponse): AuthUser {
  const fullNameParts = [dto.firstName, dto.lastName]
    .filter((value): value is string => typeof value === 'string' && value.trim().length > 0)
    .map((value) => value.trim());

  const fullName = fullNameParts.length > 0 ? fullNameParts.join(' ') : dto.email;
  const tenantName =
    dto.schoolName?.trim() ||
    (dto.subjectType === 'PLATFORM_ADMIN' ? 'Plateforme École SaaS' : 'École SaaS');

  return {
    id: dto.id,
    fullName,
    email: dto.email,
    role: resolvePrimaryRole(dto.roles, dto.subjectType),
    tenantId: dto.tenantId ?? '',
    tenantName,
  };
}
