export type StudentStatus = 'ACTIVE' | 'INACTIVE' | 'TRANSFERRED';

export type TuitionPaymentStatus = 'PAID' | 'PENDING' | 'OVERDUE';

export interface Student {
  id: string;
  matricule: string;
  firstName: string;
  lastName: string;
  className: string;
  /** E.164 phone format (e.g. +221770000001). */
  guardianPhone: string;
  status: StudentStatus;
  paymentStatus: TuitionPaymentStatus;
  /** Integer amount in FCFA (XOF), never floating point. */
  balanceFcfa: number;
  /** UTC ISO-8601 timestamp. */
  enrolledAtUtc: string;
}
