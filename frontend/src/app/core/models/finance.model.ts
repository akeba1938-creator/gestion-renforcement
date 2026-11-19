export type LedgerEntryType = 'CHARGE' | 'PAYMENT' | 'REVERSAL';

export type PaymentMethod = 'CASH' | 'WAVE' | 'ORANGE_MONEY' | 'BANK_TRANSFER';

export type TuitionPaymentStatus = 'PAID' | 'PENDING' | 'OVERDUE';

/**
 * Immutable financial ledger entry.
 * Cancellations are recorded as a new `REVERSAL` entry with a positive integer
 * amount, referencing `reversesEntryId` and a `reversalReason`. The original
 * entry is never modified.
 */
export interface FinanceEntry {
  id: string;
  reference: string;
  studentId: string;
  studentFullName: string;
  entryType: LedgerEntryType;
  label: string;
  /** Strictly positive integer amount in FCFA (XOF), never floating point. */
  amountFcfa: number;
  method?: PaymentMethod;
  /** Required when `entryType === 'REVERSAL'`: references the original CHARGE or PAYMENT. */
  reversesEntryId?: string;
  /** Required when `entryType === 'REVERSAL'`. */
  reversalReason?: string;
  /** UTC ISO-8601 timestamp. */
  recordedAtUtc: string;
}

export interface StudentBalance {
  studentId: string;
  /** All amounts are integers in FCFA (XOF). */
  expectedFcfa: number;
  collectedFcfa: number;
  balanceFcfa: number;
  paymentStatus: TuitionPaymentStatus;
}

export interface FinanceSummary {
  /** All amounts are integers in FCFA (XOF). */
  totalExpectedFcfa: number;
  totalCollectedFcfa: number;
  totalOverdueFcfa: number;
  collectionRatePercent: number;
}
