import { ChangeDetectionStrategy, reflectComponentType } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ProblemDetailError } from '../../core/models/problem-detail.model';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { FinancesApiService } from './data-access/finances-api.service';
import { FinancesPageComponent } from './finances-page';
import {
  FinanceKpiSummary,
  FinancePayableInvoice,
  FinanceTransactionRecord,
  FinancesListQuery,
  FinancesPageResult,
  ReceiptVerificationResult,
  RecordPaymentInput,
} from './models/finances.model';

const SAMPLE_KPIS: FinanceKpiSummary = {
  period: 'month',
  collectedFcfa: 6450000,
  collectedTrendLabel: '+8,2 %',
  collectedComparisonLabel: 'vs objectif mensuel',
  overdueFcfa: 1280000,
  overdueCount: 19,
  overdueSubLabel: 'relances en cours',
  collectionRatePercent: 86.4,
  collectionRateTrendLabel: '+1,8 pt',
  collectionRateComparisonLabel: 'objectif 90 %',
  pendingFcfa: 110000,
  pendingCount: 2,
  pendingSubLabel: 'à rapprocher (mobile)',
};

const SAMPLE_PAYABLE_INVOICES: readonly FinancePayableInvoice[] = [
  {
    id: 'inv-1',
    invoiceNumber: 'FAC-2026-0118',
    studentId: 'stu-1',
    studentName: 'Mouhamadou Fall-Démo',
    matricule: 'DEMO-2026-004',
    className: '1ère L',
    title: 'Scolarité T1',
    netAmountFcfa: 150000,
    paidAmountFcfa: 75000,
    balanceFcfa: 75000,
    dueDate: '2026-11-10',
    status: 'PARTIALLY_PAID',
  },
  {
    id: 'inv-2',
    invoiceNumber: 'FAC-2026-0145',
    studentId: 'stu-2',
    studentName: 'Cheikh Tidiane Ndiaye-Démo',
    matricule: 'DEMO-2026-002',
    className: '3ème A',
    title: 'Mensualité novembre',
    netAmountFcfa: 95000,
    paidAmountFcfa: 50000,
    balanceFcfa: 45000,
    dueDate: '2026-11-30',
    status: 'PARTIALLY_PAID',
  },
];

const SAMPLE_TRANSACTIONS: readonly FinanceTransactionRecord[] = [
  {
    id: 'tx-1',
    paymentId: 'pay-1',
    invoiceId: 'inv-1',
    invoiceNumber: 'FAC-2026-0142',
    receiptNumber: 'REC-2026-1089',
    verificationToken: 'verif-tok-1089',
    externalReference: 'WV-2611-88412',
    studentId: 'stu-1',
    studentName: 'Aminata Diallo-Démo',
    studentInitials: 'AD',
    matricule: 'DEMO-2026-001',
    className: 'Terminale S2',
    recordedAtUtc: '2026-11-26T10:42:00Z',
    method: 'MOBILE_MONEY_WAVE',
    status: 'VALIDATED',
    amountFcfa: 85000,
    notes: 'Mensualité novembre',
    confirmationSource: 'VERIFIED_WEBHOOK',
    reversedAtUtc: null,
  },
  {
    id: 'tx-2',
    paymentId: 'pay-2',
    invoiceId: 'inv-2',
    invoiceNumber: 'FAC-2026-0145',
    receiptNumber: 'REC-2026-1088',
    verificationToken: 'verif-tok-1088',
    externalReference: 'OM-2611-49201',
    studentId: 'stu-2',
    studentName: 'Cheikh Tidiane Ndiaye-Démo',
    studentInitials: 'CN',
    matricule: 'DEMO-2026-002',
    className: '3ème A',
    recordedAtUtc: '2026-11-26T09:15:00Z',
    method: 'MOBILE_MONEY_OM',
    status: 'PENDING',
    amountFcfa: 50000,
    notes: 'Confirmation opérateur en cours',
    confirmationSource: 'MANUAL_DESK',
    reversedAtUtc: null,
  },
  {
    id: 'tx-3',
    paymentId: 'pay-3',
    invoiceId: 'inv-3',
    invoiceNumber: 'FAC-2026-0149',
    receiptNumber: 'REC-2026-1087',
    verificationToken: 'verif-tok-1087',
    externalReference: null,
    studentId: 'stu-3',
    studentName: 'Fatou Bintou Sow-Démo',
    studentInitials: 'FS',
    matricule: 'DEMO-2026-003',
    className: '6ème B',
    recordedAtUtc: '2026-11-26T08:30:00Z',
    method: 'CASH',
    status: 'VALIDATED',
    amountFcfa: 45000,
    notes: 'Guichet caisse',
    confirmationSource: 'MANUAL_DESK',
    reversedAtUtc: null,
  },
];

function buildPageResult(
  items: readonly FinanceTransactionRecord[],
  page = 0,
  size = 8,
  totalElements = items.length,
  totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size)
): FinancesPageResult {
  return {
    content: items,
    page,
    size,
    totalElements,
    totalPages,
    first: page === 0,
    last: totalPages === 0 || page >= totalPages - 1,
    kpis: SAMPLE_KPIS,
    payableInvoices: SAMPLE_PAYABLE_INVOICES,
  };
}

interface FinancesApiStub {
  list: ReturnType<typeof vi.fn<(query: FinancesListQuery) => Observable<FinancesPageResult>>>;
  recordPayment: ReturnType<
    typeof vi.fn<(input: RecordPaymentInput) => Observable<FinanceTransactionRecord>>
  >;
  verifyReceipt: ReturnType<
    typeof vi.fn<(token: string) => Observable<ReceiptVerificationResult>>
  >;
  confirmPendingTransaction: ReturnType<
    typeof vi.fn<(tx: FinanceTransactionRecord) => Observable<FinanceTransactionRecord>>
  >;
}

describe('FinancesPageComponent', () => {
  function createApiStub(overrides?: {
    list?: (query: FinancesListQuery) => Observable<FinancesPageResult>;
    recordPayment?: (input: RecordPaymentInput) => Observable<FinanceTransactionRecord>;
    verifyReceipt?: (token: string) => Observable<ReceiptVerificationResult>;
    confirmPendingTransaction?: (
      tx: FinanceTransactionRecord
    ) => Observable<FinanceTransactionRecord>;
  }): FinancesApiStub {
    return {
      list: vi.fn(
        overrides?.list ?? (() => of(buildPageResult(SAMPLE_TRANSACTIONS, 0, 8, 12, 2)))
      ),
      recordPayment: vi.fn(
        overrides?.recordPayment ??
          ((input: RecordPaymentInput) =>
            of<FinanceTransactionRecord>({
              id: 'tx-created-01',
              paymentId: 'pay-created-01',
              invoiceId: input.invoiceId,
              invoiceNumber: 'FAC-2026-0118',
              receiptNumber: 'REC-2026-1090',
              verificationToken: 'verif-tok-1090',
              externalReference: input.externalReference ?? null,
              studentId: 'stu-1',
              studentName: 'Mouhamadou Fall-Démo',
              studentInitials: 'MF',
              matricule: 'DEMO-2026-004',
              className: '1ère L',
              recordedAtUtc: '2026-11-26T11:30:00Z',
              method: input.paymentMethod,
              status: 'VALIDATED',
              amountFcfa: input.amountFcfa,
              notes: input.notes ?? null,
              confirmationSource: 'MANUAL_DESK',
              reversedAtUtc: null,
            }))
      ),
      verifyReceipt: vi.fn(
        overrides?.verifyReceipt ??
          (() =>
            of<ReceiptVerificationResult>({
              receiptId: 'rec-1',
              receiptNumber: 'REC-2026-1089',
              amountFcfa: 85000,
              paymentMethod: 'MOBILE_MONEY_WAVE',
              issuedAtUtc: '2026-11-26T10:42:00Z',
              invoiceNumber: 'FAC-2026-0142',
              studentMatricule: 'DEMO-2026-001',
              reversed: false,
              reversedAtUtc: null,
            }))
      ),
      confirmPendingTransaction: vi.fn(
        overrides?.confirmPendingTransaction ??
          ((tx: FinanceTransactionRecord) =>
            of<FinanceTransactionRecord>({ ...tx, status: 'VALIDATED' }))
      ),
    };
  }

  async function setup(options?: {
    api?: FinancesApiStub;
    initialQueryParams?: Record<string, string>;
    isOnline?: boolean;
  }) {
    const api = options?.api ?? createApiStub();
    const initialMap = convertToParamMap(options?.initialQueryParams ?? {});
    const queryParamMap$ = new BehaviorSubject(initialMap);

    const activatedRouteStub = {
      snapshot: { queryParamMap: initialMap },
      queryParamMap: queryParamMap$.asObservable(),
    };

    const isOnline = options?.isOnline ?? true;

    await TestBed.configureTestingModule({
      imports: [FinancesPageComponent],
      providers: [
        provideRouter([]),
        { provide: FinancesApiService, useValue: api },
        { provide: ActivatedRoute, useValue: activatedRouteStub },
        {
          provide: NetworkStatusService,
          useValue: { isOnline: () => isOnline },
        },
      ],
    }).compileComponents();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(FinancesPageComponent);
    const toastService = TestBed.inject(ToastService);

    return { fixture, api, router, navigateSpy, toastService, queryParamMap$ };
  }

  afterEach(() => {
    vi.useRealTimers();
    TestBed.inject(ToastService).clear();
  });

  it('uses standalone component with ChangeDetectionStrategy.OnPush', () => {
    const mirror = reflectComponentType(FinancesPageComponent);
    expect(mirror?.isStandalone).toBe(true);
    const cmpDef = (FinancesPageComponent as unknown as { ɵcmp?: { onPush?: boolean } }).ɵcmp;
    expect(cmpDef?.onPush).toBe(true);
    expect(ChangeDetectionStrategy.OnPush).toBeDefined();
  });

  it('renders header with strictly 1 btn-primary, 4 KPI StatCards in neutral FCFA ink, soft badges, and Lucide 1.75 icons', async () => {
    const { fixture } = await setup();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;

    // Header
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Finances & Caisse');
    expect(el.textContent).toContain('Ce mois • 12 transactions');

    const primaryButtons = el.querySelectorAll('.btn-primary');
    expect(primaryButtons.length).toBe(1);
    expect(primaryButtons[0]?.textContent).toContain('Encaisser un paiement');

    const exportBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Exporter')
    );
    expect(exportBtn?.classList.contains('btn-outline')).toBe(true);

    // 4 KPI cards with neutral ink & FCFA nbsp formatting
    const kpiCards = el.querySelectorAll('app-stat-card');
    expect(kpiCards.length).toBe(4);
    expect(el.textContent).toContain('6\u00A0450\u00A0000 FCFA');
    expect(el.textContent).toContain('1\u00A0280\u00A0000 FCFA');
    expect(el.textContent).toContain('86,4 %');
    expect(el.textContent).toContain('110\u00A0000 FCFA');

    // No solid daisyUI badges
    expect(el.querySelector('.badge-success')).toBeNull();
    expect(el.querySelector('.badge-warning')).toBeNull();
    expect(el.querySelector('.badge-error')).toBeNull();
    expect(el.querySelector('.badge-info')).toBeNull();

    // Desktop table columns
    const rows = el.querySelectorAll('table tbody tr');
    expect(rows.length).toBe(3);

    const firstRow = rows[0];
    expect(firstRow?.textContent).toContain('REC-2026-1089');
    expect(firstRow?.textContent).toContain('Wave');
    expect(firstRow?.textContent).toContain('85\u00A0000 FCFA');
    expect(firstRow?.querySelector('td:nth-child(6)')?.classList.contains('text-right')).toBe(true);
    expect(firstRow?.querySelector('td:nth-child(6)')?.classList.contains('text-base-content')).toBe(true);
    expect(firstRow?.querySelector('td:last-child')?.classList.contains('text-right')).toBe(true);
    expect(firstRow?.querySelector('td:last-child button')?.textContent).toContain('Reçu');

    const secondRow = rows[1];
    expect(secondRow?.querySelector('td:last-child button')?.textContent).toContain('Vérifier');

    // Every Lucide SVG uses stroke-width="1.75" and shrink-0
    const lucideIcons = Array.from(el.querySelectorAll<SVGSVGElement>('svg.lucide'));
    expect(lucideIcons.length).toBeGreaterThan(0);
    for (const icon of lucideIcons) {
      expect(icon.getAttribute('stroke-width')).toBe('1.75');
      expect(icon.classList.contains('shrink-0')).toBe(true);
    }
  });

  it('hydrates and syncs URL queryParams (q debounced 300ms, method, status, period, page) and supports reset + pagination', async () => {
    vi.useFakeTimers();
    const { fixture, api, navigateSpy } = await setup({
      initialQueryParams: {
        q: 'REC-2026',
        method: 'MOBILE_MONEY_WAVE',
        status: 'VALIDATED',
        period: 'term',
        page: '2',
      },
    });
    fixture.detectChanges();

    expect(api.list).toHaveBeenCalledWith(
      expect.objectContaining({
        search: 'REC-2026',
        paymentMethod: 'MOBILE_MONEY_WAVE',
        status: 'VALIDATED',
        period: 'term',
        page: 1,
        size: 8,
      })
    );

    const el = fixture.nativeElement as HTMLElement;
    const searchInput = el.querySelector<HTMLInputElement>('#finances-search-input');
    expect(searchInput?.value).toBe('REC-2026');

    // Reset button clears all filters and restores default period ('month')
    const resetBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Réinitialiser')
    );
    expect(resetBtn).not.toBeUndefined();
    resetBtn?.click();
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: { q: null, method: null, status: null, period: null, page: null },
        replaceUrl: true,
      })
    );

    // Debounced search triggers after 300ms
    if (searchInput) {
      searchInput.value = 'Diallo';
      searchInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }
    vi.advanceTimersByTime(299);
    expect(api.list).toHaveBeenCalledTimes(2);

    vi.advanceTimersByTime(1);
    fixture.detectChanges();
    expect(api.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ search: 'Diallo', page: 0 })
    );
    expect(navigateSpy).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({
        queryParams: expect.objectContaining({ q: 'Diallo', page: null }),
      })
    );

    // Pagination "Suivant" advances page to 1 (URL page=2)
    const nextBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Suivant')
    );
    expect(nextBtn?.disabled).toBe(false);
    nextBtn?.click();
    fixture.detectChanges();

    expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
    expect(navigateSpy).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({
        queryParams: expect.objectContaining({ page: '2' }),
      })
    );
  });

  it('displays skeleton loaders while request is pending', async () => {
    const pending$ = new Subject<FinancesPageResult>();
    const api = createApiStub({ list: () => pending$.asObservable() });
    const { fixture } = await setup({ api });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('app-skeleton-loader').length).toBeGreaterThanOrEqual(2);
    expect(el.querySelector('table')).toBeNull();

    pending$.complete();
  });

  it('displays initial empty state when no transactions exist and no filters are active', async () => {
    const api = createApiStub({
      list: () => of(buildPageResult([], 0, 8, 0, 0)),
    });
    const { fixture } = await setup({ api });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-empty-state')).not.toBeNull();
    expect(el.textContent).toContain('Aucun encaissement enregistré');
  });

  it('displays filtered empty state and resets filters on click', async () => {
    const api = createApiStub({
      list: (query) =>
        query.status === 'CANCELLED'
          ? of(buildPageResult([], 0, 8, 0, 0))
          : of(buildPageResult(SAMPLE_TRANSACTIONS, 0, 8, 3, 1)),
    });
    const { fixture } = await setup({
      api,
      initialQueryParams: { status: 'CANCELLED' },
    });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Aucune transaction ne correspond à vos critères');
    const resetBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => b.textContent?.trim() === 'Réinitialiser les filtres'
    );
    resetBtn?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.querySelector('app-empty-state')).toBeNull();
    expect(el.textContent).toContain('Aminata Diallo-Démo');
  });

  it('displays RFC 9457 error state with copyable requestId and retries on click', async () => {
    let attempts = 0;
    const api = createApiStub({
      list: () => {
        attempts += 1;
        if (attempts === 1) {
          return throwError(
            () =>
              new ProblemDetailError({
                type: 'urn:ecole:problem:service-unavailable',
                title: 'Journal de caisse indisponible',
                status: 503,
                detail: 'Impossible de charger les transactions de caisse.',
                requestId: 'req-fin-retry-503',
              })
          );
        }
        return of(buildPageResult(SAMPLE_TRANSACTIONS, 0, 8, 3, 1));
      },
    });

    const { fixture } = await setup({ api });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-error-state')).not.toBeNull();
    expect(el.textContent).toContain('req-fin-retry-503');

    const retryBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Réessayer')
    );
    retryBtn?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(attempts).toBe(2);
    expect(el.querySelector('app-error-state')).toBeNull();
    expect(el.textContent).toContain('REC-2026-1089');
  });

  it('shows offline banner when network is offline', async () => {
    const { fixture } = await setup({ isOnline: false });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Mode hors-ligne détecté');
  });

  it('validates integer FCFA and required Wave/Orange Money reference, preserves input on RFC 9457 error, and succeeds with toast', async () => {
    let recordAttempts = 0;
    const api = createApiStub({
      recordPayment: (input) => {
        recordAttempts += 1;
        if (recordAttempts === 1) {
          return throwError(
            () =>
              new ProblemDetailError({
                type: 'urn:ecole:problem:payment-reference-duplicate',
                title: 'Référence déjà enregistrée',
                status: 409,
                detail: 'Cette référence Wave a déjà été encaissée.',
                requestId: 'req-pay-409',
              })
          );
        }
        return of<FinanceTransactionRecord>({
          id: 'tx-new-01',
          paymentId: 'pay-new-01',
          invoiceId: input.invoiceId,
          invoiceNumber: 'FAC-2026-0118',
          receiptNumber: 'REC-2026-1091',
          verificationToken: 'verif-1091',
          externalReference: input.externalReference ?? null,
          studentId: 'stu-1',
          studentName: 'Mouhamadou Fall-Démo',
          studentInitials: 'MF',
          matricule: 'DEMO-2026-004',
          className: '1ère L',
          recordedAtUtc: '2026-11-26T12:00:00Z',
          method: input.paymentMethod,
          status: 'VALIDATED',
          amountFcfa: input.amountFcfa,
          notes: input.notes ?? null,
          confirmationSource: 'MANUAL_DESK',
          reversedAtUtc: null,
        });
      },
    });

    const { fixture, toastService } = await setup({ api });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    el.querySelector<HTMLButtonElement>('.btn-primary')?.click();
    fixture.detectChanges();

    const amountInput = el.querySelector<HTMLInputElement>('#payment-amount-input');
    const methodSelect = el.querySelector<HTMLSelectElement>('#payment-method-select');
    const refInput = el.querySelector<HTMLInputElement>('#payment-reference-input');
    const form = el.querySelector<HTMLFormElement>('#record-payment-form');

    expect(amountInput).not.toBeNull();
    expect(methodSelect).not.toBeNull();
    expect(refInput).not.toBeNull();
    expect(form).not.toBeNull();

    if (amountInput && methodSelect && refInput && form) {
      // 1) Decimal FCFA rejected + Wave reference required
      amountInput.value = '50000.5';
      amountInput.dispatchEvent(new Event('input'));
      refInput.value = '';
      refInput.dispatchEvent(new Event('input'));
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      expect(api.recordPayment).not.toHaveBeenCalled();
      expect(amountInput.value).toBe('50000.5');
      expect(el.textContent).toContain('sans décimales');
      expect(el.textContent).toContain('référence transaction est obligatoire pour Wave');

      // 2) Switching to CASH makes externalReference optional
      methodSelect.value = 'CASH';
      methodSelect.dispatchEvent(new Event('change'));
      amountInput.value = '45000';
      amountInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(el.textContent).toContain('Optionnelle');

      // 3) Switching back to Wave requires reference, then RFC 9457 409 preserves input
      methodSelect.value = 'MOBILE_MONEY_WAVE';
      methodSelect.dispatchEvent(new Event('change'));
      refInput.value = 'WV-2611-99001';
      refInput.dispatchEvent(new Event('input'));
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(api.recordPayment).toHaveBeenCalledTimes(1);
      expect(el.querySelector('app-drawer app-error-state')).not.toBeNull();
      expect(el.textContent).toContain('req-pay-409');
      expect(amountInput.value).toBe('45000');
      expect(refInput.value).toBe('WV-2611-99001');

      // 4) Retry succeeds -> closes drawer, shows toast, reloads
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(api.recordPayment).toHaveBeenCalledTimes(2);
      expect(el.querySelector('app-drawer [role="dialog"]')).toBeNull();
      expect(toastService.toasts().length).toBe(1);
      expect(toastService.toasts()[0]?.message).toContain('REC-2026-1091');
    }
  });

  it('opens receipt preview drawer on Reçu click and verifies receipt authenticity', async () => {
    const { fixture, api, toastService } = await setup();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const firstActionBtn = el.querySelector<HTMLButtonElement>(
      'table tbody tr:first-child td:last-child button'
    );
    firstActionBtn?.click();
    fixture.detectChanges();

    const dialog = el.querySelector('app-drawer [role="dialog"]');
    expect(dialog).not.toBeNull();
    expect(dialog?.textContent).toContain('REC-2026-1089');
    expect(dialog?.textContent).toContain('Aminata Diallo-Démo');
    expect(dialog?.textContent).toContain('Terminale S2');
    expect(dialog?.textContent).toContain('85\u00A0000 FCFA');

    const verifyBtn = Array.from(dialog?.querySelectorAll<HTMLButtonElement>('button') ?? []).find(
      (b) => b.textContent?.trim() === 'Vérifier'
    );
    verifyBtn?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.verifyReceipt).toHaveBeenCalledWith('verif-tok-1089');
    expect(dialog?.textContent).toContain('Authenticité du reçu confirmée');
    expect(toastService.toasts().length).toBe(1);
  });

  it('opens verification drawer for PENDING transaction and validates it on action', async () => {
    const { fixture, api, toastService } = await setup();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const secondActionBtn = el.querySelector<HTMLButtonElement>(
      'table tbody tr:nth-child(2) td:last-child button'
    );
    expect(secondActionBtn?.textContent).toContain('Vérifier');
    secondActionBtn?.click();
    fixture.detectChanges();

    const dialog = el.querySelector('app-drawer [role="dialog"]');
    expect(dialog?.textContent).toContain('Vérification de transaction');
    expect(dialog?.textContent).toContain('OM-2611-49201');

    const confirmBtn = Array.from(dialog?.querySelectorAll<HTMLButtonElement>('button') ?? []).find(
      (b) => b.textContent?.includes('Valider la transaction')
    );
    confirmBtn?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.confirmPendingTransaction).toHaveBeenCalledTimes(1);
    expect(toastService.toasts().length).toBe(1);
    expect(toastService.toasts()[0]?.title).toBe('Transaction confirmée');
  });
});
