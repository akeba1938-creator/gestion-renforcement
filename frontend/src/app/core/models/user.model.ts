export type UserRole =
  | 'SAAS_ADMIN'
  | 'PROMOTER'
  | 'DIRECTOR'
  | 'SECRETARY'
  | 'CASHIER'
  | 'CENSOR'
  | 'TEACHER'
  | 'PARENT'
  | 'STUDENT';

export interface AuthUser {
  id: string;
  fullName: string;
  email: string;
  role: UserRole;
  tenantId: string;
  tenantName: string;
}
