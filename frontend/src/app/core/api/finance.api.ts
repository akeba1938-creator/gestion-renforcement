import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { FinanceEntry, FinanceSummary, StudentBalance } from '../models/finance.model';
import { Page, PageQuery } from '../models/page.model';

export interface FinanceApi {
  listEntries(query: PageQuery): Observable<Page<FinanceEntry>>;
  getStudentBalances(): Observable<StudentBalance[]>;
  getStudentBalance(studentId: string): Observable<StudentBalance | null>;
  getSummary(): Observable<FinanceSummary>;
}

export const FINANCE_API = new InjectionToken<FinanceApi>('FINANCE_API');
