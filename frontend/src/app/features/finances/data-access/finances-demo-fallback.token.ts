import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import {
  FinanceTransactionRecord,
  FinancesListQuery,
  FinancesPageResult,
  ReceiptVerificationResult,
  RecordPaymentInput,
} from '../models/finances.model';

/**
 * Optional development-only fallback contract used exclusively when the app
 * runs in local development with an active demo session and no running backend.
 * Excluded from production builds via `app.dev-providers.prod.ts`.
 */
export interface FinancesDemoFallback {
  isDemoSessionActive(): boolean;
  list(query: FinancesListQuery): Observable<FinancesPageResult>;
  recordPayment(input: RecordPaymentInput): Observable<FinanceTransactionRecord>;
  verifyReceipt(verificationToken: string): Observable<ReceiptVerificationResult>;
  confirmPendingTransaction(transactionId: string): Observable<FinanceTransactionRecord>;
}

export const FINANCES_DEMO_FALLBACK = new InjectionToken<FinancesDemoFallback>(
  'FINANCES_DEMO_FALLBACK'
);
