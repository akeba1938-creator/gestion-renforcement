import { UserRole } from '../models/user.model';

/**
 * Single source of truth for frontend route & navigation visibility by role.
 *
 * IMPORTANT: Frontend role filtering is strictly for UI ergonomics so users do
 * not see menu links leading to /forbidden. Authorization is always enforced
 * server-side on every /api/v1 endpoint.
 */
export const ADMIN_ROLES: readonly UserRole[] = [
  'SAAS_ADMIN',
  'PROMOTER',
  'DIRECTOR',
  'SECRETARY',
  'CASHIER',
  'CENSOR',
];

export const ROUTE_ROLES = {
  dashboard: ADMIN_ROLES,
  students: ['PROMOTER', 'DIRECTOR', 'SECRETARY', 'CASHIER', 'CENSOR'] as readonly UserRole[],
  grades: ['PROMOTER', 'DIRECTOR', 'CENSOR'] as readonly UserRole[],
  attendance: ['PROMOTER', 'DIRECTOR', 'SECRETARY', 'CENSOR'] as readonly UserRole[],
  finance: ['SAAS_ADMIN', 'PROMOTER', 'DIRECTOR', 'SECRETARY', 'CASHIER'] as readonly UserRole[],
  import: ['PROMOTER', 'DIRECTOR', 'SECRETARY', 'CASHIER', 'CENSOR'] as readonly UserRole[],
  settings: ['SAAS_ADMIN', 'PROMOTER', 'DIRECTOR'] as readonly UserRole[],
} as const;
