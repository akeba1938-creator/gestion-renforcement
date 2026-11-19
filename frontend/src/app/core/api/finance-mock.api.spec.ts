import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { FinanceEntry } from '../models/finance.model';
import {
  FinanceMockApi,
  computeFinanceSummaryFromLedger,
  computeStudentBalancesFromLedger,
} from './finance-mock.api';
import { MOCK_API_OPTIONS } from './mock-api.util';

describe('FinanceMockApi & ledger rules', () => {
  let api: FinanceMockApi;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: MOCK_API_OPTIONS, useValue: { delayMs: 0, scenario: 'default' } }],
    });
    api = TestBed.inject(FinanceMockApi);
  });

  it('lists immutable entries with strictly positive integer FCFA amounts and valid REVERSAL link', async () => {
    const page = await firstValueFrom(
      api.listEntries({ page: 0, size: 20, sortBy: 'amountFcfa', sortDir: 'desc' })
    );
    expect(page.total).toBeGreaterThan(0);

    for (const entry of page.items) {
      expect(Number.isInteger(entry.amountFcfa)).toBe(true);
      expect(entry.amountFcfa).toBeGreaterThan(0);
    }

    const reversal = page.items.find((e) => e.entryType === 'REVERSAL');
    expect(reversal).toBeDefined();
    expect(reversal?.reversesEntryId).toBe('ent-pay-005');
    expect(reversal?.reversalReason).toBeTruthy();

    // Original payment entry remains untouched in the ledger
    const original = page.items.find((e) => e.id === 'ent-pay-005');
    expect(original?.entryType).toBe('PAYMENT');
    expect(original?.amountFcfa).toBe(15000);
  });

  it('computes student balances and summary dynamically from CHARGE, PAYMENT and REVERSAL entries', () => {
    const entries: FinanceEntry[] = [
      {
        id: 'c1',
        reference: 'FAC-1',
        studentId: 's1',
        studentFullName: 'Élève Un',
        entryType: 'CHARGE',
        label: 'Scolarité',
        amountFcfa: 50000,
        recordedAtUtc: '2026-10-01T08:00:00Z',
      },
      {
        id: 'c2',
        reference: 'FAC-2',
        studentId: 's1',
        studentFullName: 'Élève Un',
        entryType: 'CHARGE',
        label: 'Frais erreur',
        amountFcfa: 10000,
        recordedAtUtc: '2026-10-01T09:00:00Z',
      },
      {
        id: 'r-chg',
        reference: 'ANN-1',
        studentId: 's1',
        studentFullName: 'Élève Un',
        entryType: 'REVERSAL',
        label: 'Annulation charge',
        amountFcfa: 10000,
        reversesEntryId: 'c2',
        reversalReason: 'Facturation en double',
        recordedAtUtc: '2026-10-01T10:00:00Z',
      },
      {
        id: 'p1',
        reference: 'REC-1',
        studentId: 's1',
        studentFullName: 'Élève Un',
        entryType: 'PAYMENT',
        label: 'Paiement 1',
        amountFcfa: 20000,
        method: 'WAVE',
        recordedAtUtc: '2026-10-02T10:00:00Z',
      },
      {
        id: 'p2',
        reference: 'REC-2',
        studentId: 's1',
        studentFullName: 'Élève Un',
        entryType: 'PAYMENT',
        label: 'Paiement annulé',
        amountFcfa: 15000,
        method: 'CASH',
        recordedAtUtc: '2026-10-02T11:00:00Z',
      },
      {
        id: 'r-pay',
        reference: 'ANN-2',
        studentId: 's1',
        studentFullName: 'Élève Un',
        entryType: 'REVERSAL',
        label: 'Annulation paiement',
        amountFcfa: 15000,
        reversesEntryId: 'p2',
        reversalReason: 'Erreur de caisse',
        recordedAtUtc: '2026-10-02T12:00:00Z',
      },
    ];

    const [balance] = computeStudentBalancesFromLedger(entries);
    // expected = 50000 + 10000 - 10000 (CHARGE reversal) = 50000
    // collected = 20000 + 15000 - 15000 (PAYMENT reversal) = 20000
    expect(balance.expectedFcfa).toBe(50000);
    expect(balance.collectedFcfa).toBe(20000);
    expect(balance.balanceFcfa).toBe(30000);
    expect(balance.paymentStatus).toBe('PENDING');

    const summary = computeFinanceSummaryFromLedger(entries);
    expect(summary.totalExpectedFcfa).toBe(50000);
    expect(summary.totalCollectedFcfa).toBe(20000);
    expect(summary.totalOverdueFcfa).toBe(30000);
    expect(summary.collectionRatePercent).toBe(40);
  });

  it('rejects double reversal of an already-reversed entry and reversal of a REVERSAL entry', () => {
    const baseEntries: FinanceEntry[] = [
      {
        id: 'p1',
        reference: 'REC-1',
        studentId: 's1',
        studentFullName: 'Élève Un',
        entryType: 'PAYMENT',
        label: 'Paiement',
        amountFcfa: 20000,
        method: 'CASH',
        recordedAtUtc: '2026-10-02T10:00:00Z',
      },
      {
        id: 'r1',
        reference: 'ANN-1',
        studentId: 's1',
        studentFullName: 'Élève Un',
        entryType: 'REVERSAL',
        label: 'Première annulation',
        amountFcfa: 20000,
        reversesEntryId: 'p1',
        reversalReason: 'Doublon',
        recordedAtUtc: '2026-10-02T11:00:00Z',
      },
    ];

    const doubleReversal: FinanceEntry[] = [
      ...baseEntries,
      {
        id: 'r2',
        reference: 'ANN-2',
        studentId: 's1',
        studentFullName: 'Élève Un',
        entryType: 'REVERSAL',
        label: 'Double annulation',
        amountFcfa: 20000,
        reversesEntryId: 'p1',
        reversalReason: 'Tentative doublon',
        recordedAtUtc: '2026-10-02T12:00:00Z',
      },
    ];

    expect(() => computeStudentBalancesFromLedger(doubleReversal)).toThrow(
      'Double annulation interdite'
    );

    const reversalOfReversal: FinanceEntry[] = [
      ...baseEntries,
      {
        id: 'r3',
        reference: 'ANN-3',
        studentId: 's1',
        studentFullName: 'Élève Un',
        entryType: 'REVERSAL',
        label: 'Annulation de contre-écriture',
        amountFcfa: 20000,
        reversesEntryId: 'r1',
        reversalReason: 'Invalide',
        recordedAtUtc: '2026-10-02T12:05:00Z',
      },
    ];

    expect(() => computeStudentBalancesFromLedger(reversalOfReversal)).toThrow(
      "Impossible d'annuler une contre-écriture"
    );
  });

  it('simulates empty list and error scenarios', async () => {
    api.setScenario('empty');
    const emptyPage = await firstValueFrom(api.listEntries({ page: 0, size: 10 }));
    expect(emptyPage.items).toEqual([]);
    expect(emptyPage.total).toBe(0);

    api.setScenario('error');
    await expect(firstValueFrom(api.listEntries({ page: 0, size: 10 }))).rejects.toThrow(
      'Impossible de charger les écritures financières'
    );
  });
});
