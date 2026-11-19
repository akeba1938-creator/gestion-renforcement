export type PaymentMethod = 'CASH' | 'WAVE' | 'ORANGE_MONEY' | 'BANK_TRANSFER';

export type PaymentEntryStatus = 'POSTED' | 'REVERSED' | 'PENDING';

export interface PaymentRecord {
  id: string;
  reference: string;
  studentId: string;
  studentFullName: string;
  label: string;
  /** Integer amount in FCFA (XOF), never floating point. */
  amountFcfa: number;
  method: PaymentMethod;
  status: PaymentEntryStatus;
  /** UTC ISO-8601 timestamp. */
  recordedAtUtc: string;
}

export interface FinanceSummary {
  /** All amounts are integers in FCFA (XOF). */
  totalExpectedFcfa: number;
  totalCollectedFcfa: number;
  totalOverdueFcfa: number;
  collectionRatePercent: number;
}
