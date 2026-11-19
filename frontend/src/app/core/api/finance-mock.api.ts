import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FinanceSummary, PaymentRecord } from '../models/finance.model';
import { Page, PageQuery } from '../models/page.model';
import { FinanceApi } from './finance.api';
import {
  MOCK_API_OPTIONS,
  MockApiOptions,
  MockScenario,
  buildMockPage,
  simulateMockRequest,
} from './mock-api.util';

/**
 * Purely fictional finance dataset for frontend development and tests.
 * All monetary values are integers in FCFA (XOF).
 */
const FICTIONAL_PAYMENTS: readonly PaymentRecord[] = [
  {
    id: 'pay-demo-001',
    reference: 'REC-2026-0001',
    studentId: 'stu-demo-001',
    studentFullName: 'Awa Ndiaye-Démo',
    label: 'Scolarité Octobre 2026',
    amountFcfa: 45000,
    method: 'WAVE',
    status: 'POSTED',
    recordedAtUtc: '2026-10-05T09:15:00Z',
  },
  {
    id: 'pay-demo-002',
    reference: 'REC-2026-0002',
    studentId: 'stu-demo-004',
    studentFullName: 'Ibrahima Fall-Démo',
    label: 'Scolarité Octobre 2026',
    amountFcfa: 40000,
    method: 'ORANGE_MONEY',
    status: 'POSTED',
    recordedAtUtc: '2026-10-06T11:30:00Z',
  },
  {
    id: 'pay-demo-003',
    reference: 'REC-2026-0003',
    studentId: 'stu-demo-002',
    studentFullName: 'Moussa Diop-Démo',
    label: 'Acompte Inscription 2026',
    amountFcfa: 20000,
    method: 'CASH',
    status: 'PENDING',
    recordedAtUtc: '2026-10-07T14:45:00Z',
  },
  {
    id: 'pay-demo-004',
    reference: 'REC-2026-0004',
    studentId: 'stu-demo-005',
    studentFullName: 'Khady Ba-Démo',
    label: 'Frais de dossier',
    amountFcfa: 15000,
    method: 'CASH',
    status: 'POSTED',
    recordedAtUtc: '2026-10-08T16:10:00Z',
  },
  {
    id: 'pay-demo-005',
    reference: 'REC-2026-0005',
    studentId: 'stu-demo-003',
    studentFullName: 'Fatou Sall-Démo',
    label: 'Contre-écriture doublon',
    amountFcfa: -15000,
    method: 'BANK_TRANSFER',
    status: 'REVERSED',
    recordedAtUtc: '2026-10-09T10:00:00Z',
  },
];

const PAYMENT_COMPARATORS: Record<string, (a: PaymentRecord, b: PaymentRecord) => number> = {
  reference: (a, b) => a.reference.localeCompare(b.reference),
  studentFullName: (a, b) => a.studentFullName.localeCompare(b.studentFullName, 'fr'),
  amountFcfa: (a, b) => a.amountFcfa - b.amountFcfa,
  recordedAtUtc: (a, b) => a.recordedAtUtc.localeCompare(b.recordedAtUtc),
  status: (a, b) => a.status.localeCompare(b.status),
};

const EMPTY_SUMMARY: FinanceSummary = {
  totalExpectedFcfa: 0,
  totalCollectedFcfa: 0,
  totalOverdueFcfa: 0,
  collectionRatePercent: 0,
};

@Injectable({ providedIn: 'root' })
export class FinanceMockApi implements FinanceApi {
  private readonly defaultOptions = inject(MOCK_API_OPTIONS);
  private options: MockApiOptions = { ...this.defaultOptions };

  configureMock(partial: Partial<MockApiOptions>): void {
    this.options = { ...this.options, ...partial };
  }

  setScenario(scenario: MockScenario): void {
    this.options = { ...this.options, scenario };
  }

  listPayments(query: PageQuery): Observable<Page<PaymentRecord>> {
    const emptyPage: Page<PaymentRecord> = {
      items: [],
      page: Math.max(0, Math.trunc(query.page)),
      size: Math.max(1, Math.trunc(query.size)),
      total: 0,
    };

    return simulateMockRequest(
      this.options,
      'Impossible de charger les écritures financières. Veuillez réessayer.',
      emptyPage,
      () =>
        buildMockPage(
          FICTIONAL_PAYMENTS,
          query,
          (record, term) =>
            record.reference.toLowerCase().includes(term) ||
            record.studentFullName.toLowerCase().includes(term) ||
            record.label.toLowerCase().includes(term),
          PAYMENT_COMPARATORS
        )
    );
  }

  getSummary(): Observable<FinanceSummary> {
    return simulateMockRequest(
      this.options,
      'Impossible de charger le résumé financier. Veuillez réessayer.',
      EMPTY_SUMMARY,
      () => ({
        totalExpectedFcfa: 2450000,
        totalCollectedFcfa: 1875000,
        totalOverdueFcfa: 575000,
        collectionRatePercent: 77,
      })
    );
  }
}
