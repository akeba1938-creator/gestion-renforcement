import { Injectable, inject } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  FinanceKpiSummary,
  FinancePayableInvoice,
  FinancePeriod,
  FinanceTransactionRecord,
  FinancesListQuery,
  FinancesPageResult,
  ReceiptVerificationResult,
  RecordPaymentInput,
  buildStudentInitials,
} from '../models/finances.model';
import { FinancesDemoFallback } from './finances-demo-fallback.token';

const DEV_DEMO_TOKEN = 'demo-memory-token';

interface DemoTransactionSeed extends FinanceTransactionRecord {
  readonly periodBucket: FinancePeriod;
}

/**
 * Purely fictional finance dataset for local development mode only (`/dev-login` without backend).
 * Never bundled in production builds (excluded via `app.dev-providers.prod.ts` file replacement).
 */
const INITIAL_DEMO_TRANSACTIONS: readonly DemoTransactionSeed[] = [
  {
    id: 'fin-demo-001',
    paymentId: 'pay-demo-001',
    invoiceId: 'inv-demo-001',
    invoiceNumber: 'FAC-2026-0142',
    receiptNumber: 'REC-2026-1089',
    verificationToken: 'verif-demo-rec-1089',
    externalReference: 'WV-2611-88412',
    studentId: 'stu-demo-001',
    studentName: 'Aminata Diallo-Démo',
    studentInitials: 'AD',
    matricule: 'DEMO-2026-001',
    className: 'Terminale S2',
    recordedAtUtc: '2026-11-26T10:42:00Z',
    method: 'MOBILE_MONEY_WAVE',
    status: 'VALIDATED',
    amountFcfa: 85000,
    notes: 'Mensualité novembre + frais de laboratoire',
    confirmationSource: 'VERIFIED_WEBHOOK',
    reversedAtUtc: null,
    periodBucket: 'today',
  },
  {
    id: 'fin-demo-002',
    paymentId: 'pay-demo-002',
    invoiceId: 'inv-demo-002',
    invoiceNumber: 'FAC-2026-0145',
    receiptNumber: 'REC-2026-1088',
    verificationToken: 'verif-demo-rec-1088',
    externalReference: 'OM-2611-49201',
    studentId: 'stu-demo-002',
    studentName: 'Cheikh Tidiane Ndiaye-Démo',
    studentInitials: 'CN',
    matricule: 'DEMO-2026-002',
    className: '3ème A',
    recordedAtUtc: '2026-11-26T09:15:00Z',
    method: 'MOBILE_MONEY_OM',
    status: 'PENDING',
    amountFcfa: 50000,
    notes: 'Confirmation opérateur Orange Money en attente de rapprochement',
    confirmationSource: 'MANUAL_DESK',
    reversedAtUtc: null,
    periodBucket: 'today',
  },
  {
    id: 'fin-demo-003',
    paymentId: 'pay-demo-003',
    invoiceId: 'inv-demo-003',
    invoiceNumber: 'FAC-2026-0149',
    receiptNumber: 'REC-2026-1087',
    verificationToken: 'verif-demo-rec-1087',
    externalReference: null,
    studentId: 'stu-demo-003',
    studentName: 'Fatou Bintou Sow-Démo',
    studentInitials: 'FS',
    matricule: 'DEMO-2026-003',
    className: '6ème B',
    recordedAtUtc: '2026-11-26T08:30:00Z',
    method: 'CASH',
    status: 'VALIDATED',
    amountFcfa: 45000,
    notes: 'Encaissement guichet caisse principale',
    confirmationSource: 'MANUAL_DESK',
    reversedAtUtc: null,
    periodBucket: 'today',
  },
  {
    id: 'fin-demo-004',
    paymentId: null,
    invoiceId: 'inv-demo-004',
    invoiceNumber: 'FAC-2026-0118',
    receiptNumber: 'FAC-2026-0118',
    verificationToken: null,
    externalReference: null,
    studentId: 'stu-demo-004',
    studentName: 'Mouhamadou Fall-Démo',
    studentInitials: 'MF',
    matricule: 'DEMO-2026-004',
    className: '1ère L',
    recordedAtUtc: '2026-11-24T14:05:00Z',
    method: 'MOBILE_MONEY_WAVE',
    status: 'OVERDUE',
    amountFcfa: 75000,
    notes: 'Échéance dépassée depuis 14 jours — relance SMS envoyée',
    confirmationSource: null,
    reversedAtUtc: null,
    periodBucket: 'month',
  },
  {
    id: 'fin-demo-005',
    paymentId: 'pay-demo-005',
    invoiceId: 'inv-demo-005',
    invoiceNumber: 'FAC-2026-0130',
    receiptNumber: 'REC-2026-1082',
    verificationToken: 'verif-demo-rec-1082',
    externalReference: 'OM-2611-31890',
    studentId: 'stu-demo-005',
    studentName: 'Aïssatou Ba-Démo',
    studentInitials: 'AB',
    matricule: 'DEMO-2026-005',
    className: 'CM2 (Primaire)',
    recordedAtUtc: '2026-11-23T11:20:00Z',
    method: 'MOBILE_MONEY_OM',
    status: 'VALIDATED',
    amountFcfa: 40000,
    notes: 'Mensualité novembre + cantine',
    confirmationSource: 'VERIFIED_WEBHOOK',
    reversedAtUtc: null,
    periodBucket: 'month',
  },
  {
    id: 'fin-demo-006',
    paymentId: 'pay-demo-006',
    invoiceId: 'inv-demo-006',
    invoiceNumber: 'FAC-2026-0126',
    receiptNumber: 'REC-2026-1079',
    verificationToken: 'verif-demo-rec-1079',
    externalReference: 'WV-2611-70214',
    studentId: 'stu-demo-006',
    studentName: 'Moussa Diop-Démo',
    studentInitials: 'MD',
    matricule: 'DEMO-2026-006',
    className: 'Seconde S',
    recordedAtUtc: '2026-11-21T16:10:00Z',
    method: 'MOBILE_MONEY_WAVE',
    status: 'PENDING',
    amountFcfa: 60000,
    notes: 'Référence Wave en attente de rapprochement bancaire',
    confirmationSource: 'MANUAL_DESK',
    reversedAtUtc: null,
    periodBucket: 'month',
  },
  {
    id: 'fin-demo-007',
    paymentId: null,
    invoiceId: 'inv-demo-007',
    invoiceNumber: 'FAC-2026-0102',
    receiptNumber: 'FAC-2026-0102',
    verificationToken: null,
    externalReference: null,
    studentId: 'stu-demo-007',
    studentName: 'Khady Sall-Démo',
    studentInitials: 'KS',
    matricule: 'DEMO-2026-007',
    className: 'Terminale S2',
    recordedAtUtc: '2026-11-18T09:40:00Z',
    method: 'CASH',
    status: 'OVERDUE',
    amountFcfa: 120000,
    notes: 'Reste dû 1er trimestre',
    confirmationSource: null,
    reversedAtUtc: null,
    periodBucket: 'month',
  },
  {
    id: 'fin-demo-008',
    paymentId: 'pay-demo-008',
    invoiceId: 'inv-demo-008',
    invoiceNumber: 'FAC-2026-0121',
    receiptNumber: 'REC-2026-1074',
    verificationToken: 'verif-demo-rec-1074',
    externalReference: null,
    studentId: 'stu-demo-008',
    studentName: 'Oumar Gueye-Démo',
    studentInitials: 'OG',
    matricule: 'DEMO-2026-008',
    className: '3ème A',
    recordedAtUtc: '2026-11-16T13:15:00Z',
    method: 'CASH',
    status: 'VALIDATED',
    amountFcfa: 55000,
    notes: 'Règlement espèces au secrétariat',
    confirmationSource: 'MANUAL_DESK',
    reversedAtUtc: null,
    periodBucket: 'month',
  },
  {
    id: 'fin-demo-009',
    paymentId: 'pay-demo-009',
    invoiceId: 'inv-demo-009',
    invoiceNumber: 'FAC-2026-0115',
    receiptNumber: 'REC-2026-1068',
    verificationToken: 'verif-demo-rec-1068',
    externalReference: 'WV-2611-64009',
    studentId: 'stu-demo-009',
    studentName: 'Ndèye Astou Faye-Démo',
    studentInitials: 'NF',
    matricule: 'DEMO-2026-009',
    className: '6ème B',
    recordedAtUtc: '2026-11-12T10:05:00Z',
    method: 'MOBILE_MONEY_WAVE',
    status: 'CANCELLED',
    amountFcfa: 50000,
    notes: 'Contre-passation approuvée : doublon de saisie',
    confirmationSource: 'MANUAL_DESK',
    reversedAtUtc: '2026-11-12T15:20:00Z',
    periodBucket: 'month',
  },
  {
    id: 'fin-demo-010',
    paymentId: 'pay-demo-010',
    invoiceId: 'inv-demo-010',
    invoiceNumber: 'FAC-2026-0094',
    receiptNumber: 'REC-2026-1051',
    verificationToken: 'verif-demo-rec-1051',
    externalReference: 'OM-2610-19822',
    studentId: 'stu-demo-010',
    studentName: 'Serigne Mbaye-Démo',
    studentInitials: 'SM',
    matricule: 'DEMO-2026-010',
    className: 'Seconde S',
    recordedAtUtc: '2026-10-28T11:30:00Z',
    method: 'MOBILE_MONEY_OM',
    status: 'VALIDATED',
    amountFcfa: 90000,
    notes: 'Frais d’inscription et mensualité octobre',
    confirmationSource: 'VERIFIED_WEBHOOK',
    reversedAtUtc: null,
    periodBucket: 'term',
  },
  {
    id: 'fin-demo-011',
    paymentId: 'pay-demo-011',
    invoiceId: 'inv-demo-011',
    invoiceNumber: 'FAC-2026-0088',
    receiptNumber: 'REC-2026-1042',
    verificationToken: 'verif-demo-rec-1042',
    externalReference: null,
    studentId: 'stu-demo-011',
    studentName: 'Ramatoulaye Cissé-Démo',
    studentInitials: 'RC',
    matricule: 'DEMO-2026-011',
    className: 'CM2 (Primaire)',
    recordedAtUtc: '2026-10-19T14:45:00Z',
    method: 'CASH',
    status: 'VALIDATED',
    amountFcfa: 65000,
    notes: 'Paiement inscription annuelle',
    confirmationSource: 'MANUAL_DESK',
    reversedAtUtc: null,
    periodBucket: 'term',
  },
  {
    id: 'fin-demo-012',
    paymentId: 'pay-demo-012',
    invoiceId: 'inv-demo-012',
    invoiceNumber: 'FAC-2026-0075',
    receiptNumber: 'REC-2026-1029',
    verificationToken: 'verif-demo-rec-1029',
    externalReference: 'WV-2610-55017',
    studentId: 'stu-demo-012',
    studentName: 'Pape Abdou Seck-Démo',
    studentInitials: 'PS',
    matricule: 'DEMO-2026-012',
    className: '1ère L',
    recordedAtUtc: '2026-10-08T09:50:00Z',
    method: 'MOBILE_MONEY_WAVE',
    status: 'VALIDATED',
    amountFcfa: 110000,
    notes: 'Forfait trimestriel scolarité',
    confirmationSource: 'VERIFIED_WEBHOOK',
    reversedAtUtc: null,
    periodBucket: 'term',
  },
];

const INITIAL_PAYABLE_INVOICES: readonly FinancePayableInvoice[] = [
  {
    id: 'inv-demo-004',
    invoiceNumber: 'FAC-2026-0118',
    studentId: 'stu-demo-004',
    studentName: 'Mouhamadou Fall-Démo',
    matricule: 'DEMO-2026-004',
    className: '1ère L',
    title: 'Scolarité 1er trimestre — Mouhamadou Fall-Démo',
    netAmountFcfa: 150000,
    paidAmountFcfa: 75000,
    balanceFcfa: 75000,
    dueDate: '2026-11-10',
    status: 'PARTIALLY_PAID',
  },
  {
    id: 'inv-demo-007',
    invoiceNumber: 'FAC-2026-0102',
    studentId: 'stu-demo-007',
    studentName: 'Khady Sall-Démo',
    matricule: 'DEMO-2026-007',
    className: 'Terminale S2',
    title: 'Scolarité 1er trimestre — Khady Sall-Démo',
    netAmountFcfa: 180000,
    paidAmountFcfa: 60000,
    balanceFcfa: 120000,
    dueDate: '2026-11-05',
    status: 'PARTIALLY_PAID',
  },
  {
    id: 'inv-demo-002',
    invoiceNumber: 'FAC-2026-0145',
    studentId: 'stu-demo-002',
    studentName: 'Cheikh Tidiane Ndiaye-Démo',
    matricule: 'DEMO-2026-002',
    className: '3ème A',
    title: 'Mensualité novembre — Cheikh Tidiane Ndiaye-Démo',
    netAmountFcfa: 95000,
    paidAmountFcfa: 50000,
    balanceFcfa: 45000,
    dueDate: '2026-11-30',
    status: 'PARTIALLY_PAID',
  },
  {
    id: 'inv-demo-006',
    invoiceNumber: 'FAC-2026-0126',
    studentId: 'stu-demo-006',
    studentName: 'Moussa Diop-Démo',
    matricule: 'DEMO-2026-006',
    className: 'Seconde S',
    title: 'Mensualité novembre — Moussa Diop-Démo',
    netAmountFcfa: 95000,
    paidAmountFcfa: 60000,
    balanceFcfa: 35000,
    dueDate: '2026-11-30',
    status: 'ISSUED',
  },
  {
    id: 'inv-demo-011',
    invoiceNumber: 'FAC-2026-0151',
    studentId: 'stu-demo-011',
    studentName: 'Ramatoulaye Cissé-Démo',
    matricule: 'DEMO-2026-011',
    className: 'CM2 (Primaire)',
    title: 'Cantine & transport — Ramatoulaye Cissé-Démo',
    netAmountFcfa: 50000,
    paidAmountFcfa: 25000,
    balanceFcfa: 25000,
    dueDate: '2026-12-05',
    status: 'PARTIALLY_PAID',
  },
];

const PERIOD_BASE_COLLECTED: Readonly<Record<FinancePeriod, number>> = {
  today: 295000,
  month: 6120000,
  term: 19660000,
};

const PERIOD_BASE_OVERDUE: Readonly<Record<FinancePeriod, number>> = {
  today: 1085000,
  month: 1085000,
  term: 1085000,
};

function matchesPeriod(tx: DemoTransactionSeed, period: FinancePeriod): boolean {
  if (period === 'today') {
    return tx.periodBucket === 'today';
  }
  if (period === 'month') {
    return tx.periodBucket === 'today' || tx.periodBucket === 'month';
  }
  return true;
}

@Injectable({ providedIn: 'root' })
export class FinancesDevFallbackService implements FinancesDemoFallback {
  private readonly authService = inject(AuthService);

  private transactions: DemoTransactionSeed[] = [...INITIAL_DEMO_TRANSACTIONS];
  private payableInvoices: FinancePayableInvoice[] = [...INITIAL_PAYABLE_INVOICES];
  private receiptSequence = 1089;

  isDemoSessionActive(): boolean {
    return this.authService.token() === DEV_DEMO_TOKEN;
  }

  list(query: FinancesListQuery): Observable<FinancesPageResult> {
    const period = query.period;
    const periodTransactions = this.transactions.filter((tx) => matchesPeriod(tx, period));

    const search = query.search?.trim().toLowerCase() ?? '';
    const method = query.paymentMethod ?? 'ALL';
    const status = query.status ?? 'ALL';

    const filtered = periodTransactions.filter((tx) => {
      if (method !== 'ALL' && tx.method !== method) {
        return false;
      }
      if (status !== 'ALL' && tx.status !== status) {
        return false;
      }
      if (!search) {
        return true;
      }
      return (
        tx.studentName.toLowerCase().includes(search) ||
        tx.matricule.toLowerCase().includes(search) ||
        tx.receiptNumber.toLowerCase().includes(search) ||
        tx.invoiceNumber.toLowerCase().includes(search) ||
        (tx.externalReference?.toLowerCase().includes(search) ?? false) ||
        tx.className.toLowerCase().includes(search)
      );
    });

    const sorted = [...filtered].sort((a, b) => b.recordedAtUtc.localeCompare(a.recordedAtUtc));

    const size = Math.max(1, Math.min(100, Math.trunc(query.size || 8)));
    const totalElements = sorted.length;
    const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size);
    const requestedPage = Math.max(0, Math.trunc(query.page || 0));
    const safePage = totalPages === 0 ? 0 : Math.min(requestedPage, totalPages - 1);
    const start = safePage * size;
    const content = sorted.slice(start, start + size);

    const kpis = this.buildDemoKpis(period, periodTransactions);

    return of({
      content,
      page: safePage,
      size,
      totalElements,
      totalPages,
      first: safePage === 0,
      last: totalPages === 0 || safePage >= totalPages - 1,
      kpis,
      payableInvoices: [...this.payableInvoices],
    });
  }

  recordPayment(input: RecordPaymentInput): Observable<FinanceTransactionRecord> {
    const invoice = this.payableInvoices.find((item) => item.id === input.invoiceId);
    if (!invoice) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:invoice-not-found',
            title: 'Facture introuvable',
            status: 404,
            detail: "La facture sélectionnée n'existe pas ou n'est plus ouverte à l'encaissement.",
            requestId: 'req-demo-fin-404',
          })
      );
    }

    const trimmedRef = input.externalReference?.trim() || null;
    if (
      trimmedRef &&
      this.transactions.some(
        (tx) => tx.method === input.paymentMethod && tx.externalReference?.toLowerCase() === trimmedRef.toLowerCase()
      )
    ) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:payment-reference-duplicate',
            title: 'Référence déjà enregistrée',
            status: 409,
            detail: 'Un encaissement avec cette référence externe existe déjà pour ce mode de paiement.',
            requestId: 'req-demo-fin-409',
            errors: [{ field: 'externalReference', message: 'Cette référence est déjà utilisée.' }],
          })
      );
    }

    this.receiptSequence += 1;
    const amountFcfa = Math.trunc(input.amountFcfa);
    const nowIso = new Date().toISOString();
    const receiptNumber = `REC-2026-${this.receiptSequence}`;

    const created: DemoTransactionSeed = {
      id: `fin-demo-${this.receiptSequence}`,
      paymentId: `pay-demo-${this.receiptSequence}`,
      invoiceId: invoice.id,
      invoiceNumber: invoice.invoiceNumber,
      receiptNumber,
      verificationToken: `verif-demo-rec-${this.receiptSequence}`,
      externalReference: trimmedRef,
      studentId: invoice.studentId,
      studentName: invoice.studentName,
      studentInitials: buildStudentInitials(invoice.studentName),
      matricule: invoice.matricule,
      className: invoice.className,
      recordedAtUtc: nowIso,
      method: input.paymentMethod,
      status: 'VALIDATED',
      amountFcfa,
      notes: input.notes?.trim() || null,
      confirmationSource: 'MANUAL_DESK',
      reversedAtUtc: null,
      periodBucket: 'today',
    };

    this.transactions = [created, ...this.transactions];

    // Update remaining invoice balance in demo memory
    this.payableInvoices = this.payableInvoices
      .map((inv) => {
        if (inv.id !== invoice.id) {
          return inv;
        }
        const paidAmountFcfa = inv.paidAmountFcfa + amountFcfa;
        const balanceFcfa = Math.max(0, inv.balanceFcfa - amountFcfa);
        return {
          ...inv,
          paidAmountFcfa,
          balanceFcfa,
          status: balanceFcfa === 0 ? ('PAID' as const) : ('PARTIALLY_PAID' as const),
        };
      })
      .filter((inv) => inv.balanceFcfa > 0);

    return of(created);
  }

  verifyReceipt(verificationToken: string): Observable<ReceiptVerificationResult> {
    const found = this.transactions.find((tx) => tx.verificationToken === verificationToken);
    if (!found) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:receipt-not-found',
            title: 'Reçu introuvable',
            status: 404,
            detail: 'Aucun reçu ne correspond au jeton de vérification fourni.',
            requestId: 'req-demo-receipt-404',
          })
      );
    }

    return of({
      receiptId: found.paymentId ?? found.id,
      receiptNumber: found.receiptNumber,
      amountFcfa: found.amountFcfa,
      paymentMethod: found.method,
      issuedAtUtc: found.recordedAtUtc,
      invoiceNumber: found.invoiceNumber,
      studentMatricule: found.matricule,
      reversed: found.status === 'CANCELLED',
      reversedAtUtc: found.reversedAtUtc,
    });
  }

  confirmPendingTransaction(transactionId: string): Observable<FinanceTransactionRecord> {
    const existing = this.transactions.find((tx) => tx.id === transactionId);
    if (!existing) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:payment-not-found',
            title: 'Transaction introuvable',
            status: 404,
            detail: "La transaction sélectionnée n'existe pas.",
            requestId: 'req-demo-confirm-404',
          })
      );
    }

    const updated: DemoTransactionSeed = {
      ...existing,
      status: 'VALIDATED',
      confirmationSource: 'VERIFIED_WEBHOOK',
      notes: existing.notes ? `${existing.notes} — Vérifiée en caisse` : 'Vérifiée en caisse',
    };

    this.transactions = this.transactions.map((tx) => (tx.id === transactionId ? updated : tx));
    return of(updated);
  }

  private buildDemoKpis(
    period: FinancePeriod,
    periodTransactions: readonly DemoTransactionSeed[]
  ): FinanceKpiSummary {
    const validatedSum = periodTransactions
      .filter((tx) => tx.status === 'VALIDATED')
      .reduce((sum, tx) => sum + tx.amountFcfa, 0);
    const overdueTxs = this.transactions.filter((tx) => tx.status === 'OVERDUE');
    const overdueSum = overdueTxs.reduce((sum, tx) => sum + tx.amountFcfa, 0);
    const pendingTxs = periodTransactions.filter((tx) => tx.status === 'PENDING');
    const pendingSum = pendingTxs.reduce((sum, tx) => sum + tx.amountFcfa, 0);

    const collectedFcfa = PERIOD_BASE_COLLECTED[period] + validatedSum;
    const overdueFcfa = PERIOD_BASE_OVERDUE[period] + overdueSum;
    const totalExpected = collectedFcfa + overdueFcfa + pendingSum;
    const collectionRatePercent =
      totalExpected > 0 ? Math.round((collectedFcfa / totalExpected) * 1000) / 10 : 0;

    const trendMap: Readonly<Record<FinancePeriod, string>> = {
      today: '+12,4 %',
      month: '+8,2 %',
      term: '+5,9 %',
    };
    const comparisonMap: Readonly<Record<FinancePeriod, string>> = {
      today: 'vs moyenne journalière',
      month: 'vs objectif mensuel',
      term: 'vs objectif trimestriel',
    };

    return {
      period,
      collectedFcfa,
      collectedTrendLabel: trendMap[period],
      collectedComparisonLabel: comparisonMap[period],
      overdueFcfa,
      overdueCount: 17 + overdueTxs.length,
      overdueSubLabel: 'relances en cours',
      collectionRatePercent,
      collectionRateTrendLabel: collectionRatePercent >= 85 ? '+1,8 pt' : 'À suivre',
      collectionRateComparisonLabel: 'objectif 90 %',
      pendingFcfa: pendingSum,
      pendingCount: pendingTxs.length,
      pendingSubLabel:
        pendingTxs.length > 0 ? 'à rapprocher (mobile)' : 'aucune attente',
    };
  }
}
