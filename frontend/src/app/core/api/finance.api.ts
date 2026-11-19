import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { FinanceSummary, PaymentRecord } from '../models/finance.model';
import { Page, PageQuery } from '../models/page.model';

export interface FinanceApi {
  listPayments(query: PageQuery): Observable<Page<PaymentRecord>>;
  getSummary(): Observable<FinanceSummary>;
}

export const FINANCE_API = new InjectionToken<FinanceApi>('FINANCE_API');
