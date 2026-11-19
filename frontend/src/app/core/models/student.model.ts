export type StudentStatus = 'ACTIVE' | 'INACTIVE' | 'TRANSFERRED';

/**
 * Student domain model (student module only).
 * Tuition balances and payment statuses belong strictly to the finance module (`StudentBalance`).
 */
export interface Student {
  id: string;
  matricule: string;
  firstName: string;
  lastName: string;
  className: string;
  /** E.164 phone format (e.g. +221000000001 in fictional dev data). */
  guardianPhone: string;
  status: StudentStatus;
  /** UTC ISO-8601 timestamp. */
  enrolledAtUtc: string;
}
