import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  FinanceEntry,
  FinanceSummary,
  StudentBalance,
  TuitionPaymentStatus,
} from '../models/finance.model';
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
 * Purely fictional immutable finance ledger for frontend development and tests.
 * - Every amount is a strictly positive integer in FCFA (XOF).
 * - Original entries are never modified; cancellations are new `REVERSAL` entries
 *   referencing `reversesEntryId` with a `reversalReason`.
 */
const FICTIONAL_ENTRIES: readonly FinanceEntry[] = [
  // Charges (frais dus)
  {
    id: 'ent-chg-001',
    reference: 'FAC-2026-0001',
    studentId: 'stu-demo-001',
    studentFullName: 'Awa Ndiaye-Démo',
    entryType: 'CHARGE',
    label: 'Scolarité Octobre 2026',
    amountFcfa: 45000,
    recordedAtUtc: '2026-10-01T08:00:00Z',
  },
  {
    id: 'ent-chg-002',
    reference: 'FAC-2026-0002',
    studentId: 'stu-demo-002',
    studentFullName: 'Moussa Diop-Démo',
    entryType: 'CHARGE',
    label: 'Scolarité Octobre 2026',
    amountFcfa: 55000,
    recordedAtUtc: '2026-10-01T08:05:00Z',
  },
  {
    id: 'ent-chg-003',
    reference: 'FAC-2026-0003',
    studentId: 'stu-demo-003',
    studentFullName: 'Fatou Sall-Démo',
    entryType: 'CHARGE',
    label: 'Scolarité Octobre 2026',
    amountFcfa: 75000,
    recordedAtUtc: '2026-10-01T08:10:00Z',
  },
  {
    id: 'ent-chg-004',
    reference: 'FAC-2026-0004',
    studentId: 'stu-demo-004',
    studentFullName: 'Ibrahima Fall-Démo',
    entryType: 'CHARGE',
    label: 'Scolarité Octobre 2026',
    amountFcfa: 40000,
    recordedAtUtc: '2026-10-01T08:15:00Z',
  },
  {
    id: 'ent-chg-005',
    reference: 'FAC-2026-0005',
    studentId: 'stu-demo-005',
    studentFullName: 'Khady Ba-Démo',
    entryType: 'CHARGE',
    label: 'Scolarité Octobre 2026',
    amountFcfa: 40000,
    recordedAtUtc: '2026-10-01T08:20:00Z',
  },
  // Paiements
  {
    id: 'ent-pay-001',
    reference: 'REC-2026-0001',
    studentId: 'stu-demo-001',
    studentFullName: 'Awa Ndiaye-Démo',
    entryType: 'PAYMENT',
    label: 'Paiement Scolarité Octobre 2026',
    amountFcfa: 45000,
    method: 'WAVE',
    recordedAtUtc: '2026-10-05T09:15:00Z',
  },
  {
    id: 'ent-pay-002',
    reference: 'REC-2026-0002',
    studentId: 'stu-demo-004',
    studentFullName: 'Ibrahima Fall-Démo',
    entryType: 'PAYMENT',
    label: 'Paiement Scolarité Octobre 2026',
    amountFcfa: 40000,
    method: 'ORANGE_MONEY',
    recordedAtUtc: '2026-10-06T11:30:00Z',
  },
  {
    id: 'ent-pay-003',
    reference: 'REC-2026-0003',
    studentId: 'stu-demo-002',
    studentFullName: 'Moussa Diop-Démo',
    entryType: 'PAYMENT',
    label: 'Acompte Scolarité Octobre 2026',
    amountFcfa: 20000,
    method: 'CASH',
    recordedAtUtc: '2026-10-07T14:45:00Z',
  },
  {
    id: 'ent-pay-004',
    reference: 'REC-2026-0004',
    studentId: 'stu-demo-005',
    studentFullName: 'Khady Ba-Démo',
    entryType: 'PAYMENT',
    label: 'Acompte Scolarité Octobre 2026',
    amountFcfa: 15000,
    method: 'CASH',
    recordedAtUtc: '2026-10-08T16:10:00Z',
  },
  {
    id: 'ent-pay-005',
    reference: 'REC-2026-0005',
    studentId: 'stu-demo-003',
    studentFullName: 'Fatou Sall-Démo',
    entryType: 'PAYMENT',
    label: 'Paiement saisi en doublon',
    amountFcfa: 15000,
    method: 'BANK_TRANSFER',
    recordedAtUtc: '2026-10-09T09:30:00Z',
  },
  // Contre-écriture : nouvelle écriture positive référençant l'écriture d'origine (jamais modifiée)
  {
    id: 'ent-rev-001',
    reference: 'ANN-2026-0001',
    studentId: 'stu-demo-003',
    studentFullName: 'Fatou Sall-Démo',
    entryType: 'REVERSAL',
    label: 'Annulation du paiement REC-2026-0005',
    amountFcfa: 15000,
    reversesEntryId: 'ent-pay-005',
    reversalReason: 'Doublon de saisie bancaire',
    recordedAtUtc: '2026-10-09T10:00:00Z',
  },
];

const ENTRY_COMPARATORS: Record<string, (a: FinanceEntry, b: FinanceEntry) => number> = {
  reference: (a, b) => a.reference.localeCompare(b.reference),
  studentFullName: (a, b) => a.studentFullName.localeCompare(b.studentFullName, 'fr'),
  entryType: (a, b) => a.entryType.localeCompare(b.entryType),
  amountFcfa: (a, b) => a.amountFcfa - b.amountFcfa,
  recordedAtUtc: (a, b) => a.recordedAtUtc.localeCompare(b.recordedAtUtc),
};

const EMPTY_SUMMARY: FinanceSummary = {
  totalExpectedFcfa: 0,
  totalCollectedFcfa: 0,
  totalOverdueFcfa: 0,
  collectionRatePercent: 0,
};

/**
 * Validates ledger integrity and computes per-student balances directly from entries.
 * - Rejects non-integer or non-positive amounts.
 * - Inspects the referenced original entry (`reversesEntryId`) to deduct from CHARGE or PAYMENT.
 * - Rejects reversing a REVERSAL entry and rejects double reversal of an already-reversed entry.
 */
export function computeStudentBalancesFromLedger(
  entries: readonly FinanceEntry[]
): StudentBalance[] {
  const byId = new Map<string, FinanceEntry>();
  for (const entry of entries) {
    if (!Number.isInteger(entry.amountFcfa) || entry.amountFcfa <= 0) {
      throw new Error(`Montant FCFA invalide pour l'écriture ${entry.id}.`);
    }
    byId.set(entry.id, entry);
  }

  const reversedEntryIds = new Set<string>();
  const totalsByStudent = new Map<string, { expectedFcfa: number; collectedFcfa: number }>();

  const ensureStudent = (studentId: string) => {
    let acc = totalsByStudent.get(studentId);
    if (!acc) {
      acc = { expectedFcfa: 0, collectedFcfa: 0 };
      totalsByStudent.set(studentId, acc);
    }
    return acc;
  };

  for (const entry of entries) {
    const acc = ensureStudent(entry.studentId);

    if (entry.entryType === 'CHARGE') {
      acc.expectedFcfa += entry.amountFcfa;
    } else if (entry.entryType === 'PAYMENT') {
      acc.collectedFcfa += entry.amountFcfa;
    } else if (entry.entryType === 'REVERSAL') {
      if (!entry.reversesEntryId || !entry.reversalReason?.trim()) {
        throw new Error(
          `La contre-écriture ${entry.id} doit référencer une écriture d'origine et un motif.`
        );
      }

      const original = byId.get(entry.reversesEntryId);
      if (!original) {
        throw new Error(`Écriture d'origine introuvable : ${entry.reversesEntryId}.`);
      }
      if (original.entryType === 'REVERSAL') {
        throw new Error(`Impossible d'annuler une contre-écriture (${original.id}).`);
      }
      if (reversedEntryIds.has(original.id)) {
        throw new Error(`Double annulation interdite pour l'écriture ${original.id}.`);
      }
      reversedEntryIds.add(original.id);

      const originalAcc = ensureStudent(original.studentId);
      if (original.entryType === 'CHARGE') {
        originalAcc.expectedFcfa -= entry.amountFcfa;
      } else {
        originalAcc.collectedFcfa -= entry.amountFcfa;
      }
    }
  }

  return Array.from(totalsByStudent.entries()).map(([studentId, { expectedFcfa, collectedFcfa }]) => {
    const balanceFcfa = Math.max(0, expectedFcfa - collectedFcfa);
    let paymentStatus: TuitionPaymentStatus;
    if (balanceFcfa === 0) {
      paymentStatus = 'PAID';
    } else if (collectedFcfa > 0) {
      paymentStatus = 'PENDING';
    } else {
      paymentStatus = 'OVERDUE';
    }

    return {
      studentId,
      expectedFcfa,
      collectedFcfa,
      balanceFcfa,
      paymentStatus,
    };
  });
}

export function computeFinanceSummaryFromLedger(entries: readonly FinanceEntry[]): FinanceSummary {
  const balances = computeStudentBalancesFromLedger(entries);
  const totalExpectedFcfa = balances.reduce((sum, b) => sum + b.expectedFcfa, 0);
  const totalCollectedFcfa = balances.reduce((sum, b) => sum + b.collectedFcfa, 0);
  const totalOverdueFcfa = balances.reduce((sum, b) => sum + b.balanceFcfa, 0);
  const collectionRatePercent =
    totalExpectedFcfa > 0 ? Math.round((totalCollectedFcfa * 100) / totalExpectedFcfa) : 0;

  return {
    totalExpectedFcfa,
    totalCollectedFcfa,
    totalOverdueFcfa,
    collectionRatePercent,
  };
}

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

  listEntries(query: PageQuery): Observable<Page<FinanceEntry>> {
    const emptyPage: Page<FinanceEntry> = {
      items: [],
      page: Math.max(0, Math.trunc(query.page)),
      size: Math.max(1, Math.min(100, Math.trunc(query.size))),
      total: 0,
    };

    return simulateMockRequest(
      this.options,
      'Impossible de charger les écritures financières. Veuillez réessayer.',
      emptyPage,
      () =>
        buildMockPage(
          FICTIONAL_ENTRIES,
          query,
          (record, term) =>
            record.reference.toLowerCase().includes(term) ||
            record.studentFullName.toLowerCase().includes(term) ||
            record.label.toLowerCase().includes(term),
          ENTRY_COMPARATORS
        )
    );
  }

  getStudentBalances(): Observable<StudentBalance[]> {
    return simulateMockRequest(
      this.options,
      'Impossible de calculer les soldes élèves. Veuillez réessayer.',
      [],
      () => computeStudentBalancesFromLedger(FICTIONAL_ENTRIES)
    );
  }

  getStudentBalance(studentId: string): Observable<StudentBalance | null> {
    return simulateMockRequest(
      this.options,
      'Impossible de charger le solde élève. Veuillez réessayer.',
      null,
      () =>
        computeStudentBalancesFromLedger(FICTIONAL_ENTRIES).find(
          (balance) => balance.studentId === studentId
        ) ?? null
    );
  }

  getSummary(): Observable<FinanceSummary> {
    return simulateMockRequest(
      this.options,
      'Impossible de charger le résumé financier. Veuillez réessayer.',
      EMPTY_SUMMARY,
      () => computeFinanceSummaryFromLedger(FICTIONAL_ENTRIES)
    );
  }
}
