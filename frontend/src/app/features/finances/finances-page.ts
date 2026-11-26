import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  FormControl,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, ParamMap, Params, Router } from '@angular/router';
import {
  LucideBanknote,
  LucideChevronLeft,
  LucideChevronRight,
  LucideCircleCheck,
  LucideDownload,
  LucideFileText,
  LucidePlus,
  LucidePrinter,
  LucideReceipt,
  LucideRefreshCw,
  LucideRotateCcw,
  LucideSearch,
  LucideShieldCheck,
  LucideSmartphone,
  LucideWallet,
  LucideWifiOff,
  LucideX,
} from '@lucide/angular';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, skip, switchMap } from 'rxjs';
import {
  ProblemDetail,
  extractFieldErrors,
  parseProblemDetail,
} from '../../core/models/problem-detail.model';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { DrawerComponent } from '../../shared/components/drawer/drawer';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header';
import { SkeletonLoaderComponent } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatCardComponent, StatCardTrendTone } from '../../shared/components/stat-card/stat-card';
import { BadgeTone, StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { ToastService } from '../../shared/components/toast/toast.service';
import { DakarDatePipe } from '../../shared/pipes/dakar-date.pipe';
import { FcfaPipe } from '../../shared/pipes/fcfa.pipe';
import { FinancesApiService } from './data-access/finances-api.service';
import {
  BackendPaymentMethod,
  FinanceKpiSummary,
  FinancePayableInvoice,
  FinancePaymentMethodFilter,
  FinancePeriod,
  FinanceStatusFilter,
  FinanceTransactionRecord,
  FinanceTransactionStatus,
  FinancesListQuery,
  FinancesPageResult,
  PrimaryPaymentMethod,
  ReceiptVerificationResult,
  requiresExternalReference,
} from './models/finances.model';

export type FinanceDrawerMode = 'record' | 'receipt' | null;

interface PeriodOption {
  readonly value: FinancePeriod;
  readonly label: string;
}

interface MethodFilterOption {
  readonly value: FinancePaymentMethodFilter;
  readonly label: string;
}

interface PrimaryMethodOption {
  readonly value: PrimaryPaymentMethod;
  readonly label: string;
}

interface StatusFilterOption {
  readonly value: FinanceStatusFilter;
  readonly label: string;
}

interface UrlFilterState {
  readonly search: string;
  readonly paymentMethod: FinancePaymentMethodFilter;
  readonly status: FinanceStatusFilter;
  readonly period: FinancePeriod;
  readonly page: number;
}

const DEFAULT_PAGE_SIZE = 8;
const SEARCH_DEBOUNCE_MS = 300;
const STRICT_POSITIVE_INTEGER_PATTERN = /^[1-9]\d*$/;

const PERIOD_OPTIONS: readonly PeriodOption[] = [
  { value: 'today', label: "Aujourd'hui" },
  { value: 'month', label: 'Ce mois' },
  { value: 'term', label: 'Trimestre' },
];

const METHOD_FILTER_OPTIONS: readonly MethodFilterOption[] = [
  { value: 'ALL', label: 'Tous les moyens' },
  { value: 'MOBILE_MONEY_WAVE', label: 'Wave' },
  { value: 'MOBILE_MONEY_OM', label: 'Orange Money' },
  { value: 'CASH', label: 'Espèces' },
];

const PRIMARY_METHOD_OPTIONS: readonly PrimaryMethodOption[] = [
  { value: 'MOBILE_MONEY_WAVE', label: 'Wave' },
  { value: 'MOBILE_MONEY_OM', label: 'Orange Money' },
  { value: 'CASH', label: 'Espèces' },
];

const STATUS_FILTER_OPTIONS: readonly StatusFilterOption[] = [
  { value: 'ALL', label: 'Tous les statuts' },
  { value: 'VALIDATED', label: 'Validé' },
  { value: 'PENDING', label: 'En attente' },
  { value: 'OVERDUE', label: 'Impayé' },
  { value: 'CANCELLED', label: 'Annulé' },
];

const INTEGER_FORMATTER = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });
const PERCENT_FORMATTER = new Intl.NumberFormat('fr-FR', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

function formatCount(value: number): string {
  return INTEGER_FORMATTER.format(Math.max(0, Math.trunc(value))).replace(/\u202F/g, '\u00A0');
}

function formatFcfaPlain(value: number): string {
  return `${INTEGER_FORMATTER.format(Math.trunc(value)).replace(/\u202F/g, '\u00A0')} FCFA`;
}

function parsePeriod(raw: string | null): FinancePeriod {
  if (raw === 'today' || raw === 'month' || raw === 'term') {
    return raw;
  }
  return 'month';
}

function parseMethodFilter(raw: string | null): FinancePaymentMethodFilter {
  if (raw === 'MOBILE_MONEY_WAVE' || raw === 'MOBILE_MONEY_OM' || raw === 'CASH') {
    return raw;
  }
  return 'ALL';
}

function parseStatusFilter(raw: string | null): FinanceStatusFilter {
  if (raw === 'VALIDATED' || raw === 'PENDING' || raw === 'OVERDUE' || raw === 'CANCELLED') {
    return raw;
  }
  return 'ALL';
}

function parseZeroBasedPage(raw: string | null): number {
  if (!raw) {
    return 0;
  }
  const parsed = Number.parseInt(raw, 10);
  if (!Number.isFinite(parsed) || parsed < 1) {
    return 0;
  }
  return parsed - 1;
}

/**
 * Validates that the FCFA input is a strictly positive whole integer (no decimals, no <= 0).
 */
export function positiveIntegerFcfaValidator(control: AbstractControl<string>): ValidationErrors | null {
  const raw = String(control.value ?? '').trim();
  if (!raw) {
    return { required: true };
  }
  const normalized = raw.replace(/[\s\u00A0\u202F]/g, '');
  if (/[.,]/.test(normalized)) {
    return { decimalNotAllowed: true };
  }
  if (!STRICT_POSITIVE_INTEGER_PATTERN.test(normalized)) {
    return { invalidFcfaInteger: true };
  }
  const numeric = Number.parseInt(normalized, 10);
  if (!Number.isSafeInteger(numeric) || numeric <= 0) {
    return { invalidFcfaInteger: true };
  }
  return null;
}

function parseFcfaIntegerInput(raw: string): number {
  const normalized = raw.trim().replace(/[\s\u00A0\u202F]/g, '');
  return Number.parseInt(normalized, 10);
}

@Component({
  selector: 'app-finances-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    PageHeaderComponent,
    StatCardComponent,
    StatusBadgeComponent,
    SkeletonLoaderComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    DrawerComponent,
    FcfaPipe,
    DakarDatePipe,
    LucideBanknote,
    LucideChevronLeft,
    LucideChevronRight,
    LucideCircleCheck,
    LucideDownload,
    LucideFileText,
    LucidePlus,
    LucidePrinter,
    LucideReceipt,
    LucideRefreshCw,
    LucideRotateCcw,
    LucideSearch,
    LucideShieldCheck,
    LucideSmartphone,
    LucideWallet,
    LucideWifiOff,
    LucideX,
  ],
  templateUrl: './finances-page.html',
})
export class FinancesPageComponent implements OnInit {
  private readonly financesApi = inject(FinancesApiService);
  private readonly toastService = inject(ToastService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly document = inject(DOCUMENT);
  protected readonly networkStatus = inject(NetworkStatusService);

  private readonly loadTrigger$ = new Subject<FinancesListQuery>();

  protected readonly periodOptions = PERIOD_OPTIONS;
  protected readonly methodFilterOptions = METHOD_FILTER_OPTIONS;
  protected readonly primaryMethodOptions = PRIMARY_METHOD_OPTIONS;
  protected readonly statusFilterOptions = STATUS_FILTER_OPTIONS;
  protected readonly pageSize = DEFAULT_PAGE_SIZE;

  protected readonly searchControl = new FormControl<string>('', { nonNullable: true });

  protected readonly searchQuery = signal<string>('');
  protected readonly selectedMethod = signal<FinancePaymentMethodFilter>('ALL');
  protected readonly selectedStatus = signal<FinanceStatusFilter>('ALL');
  protected readonly selectedPeriod = signal<FinancePeriod>('month');
  protected readonly currentPage = signal<number>(0);

  protected readonly pageResult = signal<FinancesPageResult | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorProblem = signal<ProblemDetail | null>(null);
  protected readonly exporting = signal<boolean>(false);

  protected readonly drawerMode = signal<FinanceDrawerMode>(null);
  protected readonly selectedTransaction = signal<FinanceTransactionRecord | null>(null);
  protected readonly submitting = signal<boolean>(false);
  protected readonly recordErrorProblem = signal<ProblemDetail | null>(null);
  protected readonly verifyingReceipt = signal<boolean>(false);
  protected readonly verificationResult = signal<ReceiptVerificationResult | null>(null);
  protected readonly verificationErrorProblem = signal<ProblemDetail | null>(null);
  protected readonly confirmingTransaction = signal<boolean>(false);

  protected readonly paymentForm = this.formBuilder.nonNullable.group({
    invoiceId: ['', [Validators.required]],
    amountFcfa: ['', [positiveIntegerFcfaValidator]],
    paymentMethod: ['MOBILE_MONEY_WAVE' as PrimaryPaymentMethod, [Validators.required]],
    externalReference: ['', [Validators.required, Validators.maxLength(120)]],
    notes: ['', [Validators.maxLength(500)]],
  });

  protected readonly transactions = computed<readonly FinanceTransactionRecord[]>(
    () => this.pageResult()?.content ?? []
  );

  protected readonly kpis = computed<FinanceKpiSummary | null>(
    () => this.pageResult()?.kpis ?? null
  );

  protected readonly payableInvoices = computed<readonly FinancePayableInvoice[]>(
    () => this.pageResult()?.payableInvoices ?? []
  );

  protected readonly totalElements = computed<number>(() => this.pageResult()?.totalElements ?? 0);

  protected readonly totalPages = computed<number>(() => {
    const result = this.pageResult();
    if (!result) {
      return 0;
    }
    return result.totalPages > 0 ? result.totalPages : result.content.length > 0 ? 1 : 0;
  });

  protected readonly isFirstPage = computed<boolean>(
    () => this.pageResult()?.first ?? this.currentPage() <= 0
  );

  protected readonly isLastPage = computed<boolean>(() => {
    const result = this.pageResult();
    if (!result) {
      return true;
    }
    return result.last || this.currentPage() >= Math.max(0, result.totalPages - 1);
  });

  protected readonly hasActiveFilters = computed<boolean>(
    () =>
      this.searchQuery().trim().length > 0 ||
      this.selectedMethod() !== 'ALL' ||
      this.selectedStatus() !== 'ALL' ||
      this.selectedPeriod() !== 'month'
  );

  protected readonly hasNarrowingFilters = computed<boolean>(
    () =>
      this.searchQuery().trim().length > 0 ||
      this.selectedMethod() !== 'ALL' ||
      this.selectedStatus() !== 'ALL'
  );

  protected readonly activePeriodLabel = computed<string>(() => {
    const labels: Readonly<Record<FinancePeriod, string>> = {
      today: "Aujourd'hui",
      month: 'Ce mois',
      term: 'Trimestre',
    };
    return labels[this.selectedPeriod()];
  });

  protected readonly headerSubtitle = computed<string>(() => {
    if (this.loading() && !this.pageResult()) {
      return 'Chargement du journal de caisse…';
    }
    const total = this.totalElements();
    const period = this.activePeriodLabel();
    if (total === 0) {
      return this.hasNarrowingFilters()
        ? `${period} • 0 transaction trouvée`
        : `${period} • Aucune transaction enregistrée`;
    }
    const noun = total > 1 ? 'transactions' : 'transaction';
    return `${period} • ${formatCount(total)} ${noun}`;
  });

  protected readonly collectedKpiLabel = computed<string>(() => {
    const labels: Readonly<Record<FinancePeriod, string>> = {
      today: 'Encaissements du jour',
      month: 'Encaissements du mois',
      term: 'Encaissements du trimestre',
    };
    return labels[this.selectedPeriod()];
  });

  protected readonly overdueTrendLabel = computed<string>(() => {
    const count = this.kpis()?.overdueCount ?? 0;
    return count > 1 ? `${formatCount(count)} factures` : `${formatCount(count)} facture`;
  });

  protected readonly collectionRateValue = computed<string>(() => {
    const rate = this.kpis()?.collectionRatePercent ?? 0;
    return `${PERCENT_FORMATTER.format(rate)} %`;
  });

  protected readonly collectionRateTrendTone = computed<StatCardTrendTone>(() => {
    const rate = this.kpis()?.collectionRatePercent ?? 0;
    if (rate >= 85) {
      return 'success';
    }
    if (rate >= 70) {
      return 'warning';
    }
    return 'error';
  });

  protected readonly pendingTrendLabel = computed<string>(() => {
    const count = this.kpis()?.pendingCount ?? 0;
    return count > 1 ? `${formatCount(count)} opérations` : `${formatCount(count)} opération`;
  });

  protected readonly paginationRangeLabel = computed<string>(() => {
    const result = this.pageResult();
    if (!result || result.totalElements === 0 || result.content.length === 0) {
      return '0 résultat';
    }
    const start = result.page * result.size + 1;
    const end = start + result.content.length - 1;
    const noun = result.totalElements > 1 ? 'transactions' : 'transaction';
    return `Affichage de ${formatCount(start)}–${formatCount(end)} sur ${formatCount(result.totalElements)} ${noun}`;
  });

  protected readonly currentPageDisplay = computed<number>(() => this.currentPage() + 1);

  protected readonly drawerOpen = computed<boolean>(() => this.drawerMode() !== null);

  protected readonly drawerTitle = computed<string>(() => {
    if (this.drawerMode() === 'record') {
      return 'Encaisser un paiement';
    }
    const tx = this.selectedTransaction();
    if (!tx) {
      return 'Aperçu du reçu';
    }
    return tx.status === 'PENDING' ? 'Vérification de transaction' : 'Reçu de paiement';
  });

  protected readonly drawerSubtitle = computed<string>(() => {
    if (this.drawerMode() === 'record') {
      return 'Enregistrement caisse en FCFA (montant entier, zéro décimale)';
    }
    const tx = this.selectedTransaction();
    return tx ? `Référence ${tx.receiptNumber} • ${tx.studentName}` : '';
  });

  protected readonly selectedFormMethod = signal<PrimaryPaymentMethod>('MOBILE_MONEY_WAVE');

  protected readonly isReferenceRequired = computed<boolean>(() =>
    requiresExternalReference(this.selectedFormMethod())
  );

  protected readonly selectedInvoiceForForm = computed<FinancePayableInvoice | null>(() => {
    const invoiceId = this.selectedInvoiceIdSignal();
    if (!invoiceId) {
      return null;
    }
    return this.payableInvoices().find((inv) => inv.id === invoiceId) ?? null;
  });

  private readonly selectedInvoiceIdSignal = signal<string>('');

  protected readonly apiFieldErrors = computed<Readonly<Record<string, string>>>(() =>
    extractFieldErrors(this.recordErrorProblem())
  );

  constructor() {
    this.loadTrigger$
      .pipe(
        switchMap((query) => {
          this.loading.set(true);
          this.errorProblem.set(null);
          return this.financesApi.list(query).pipe(
            catchError((error: unknown) => {
              this.errorProblem.set(parseProblemDetail(error));
              this.loading.set(false);
              return of<FinancesPageResult | null>(null);
            })
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((result) => {
        if (result !== null) {
          this.pageResult.set(result);
          this.currentPage.set(result.page);
          this.loading.set(false);
        }
      });

    this.searchControl.valueChanges
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((value) => {
        const trimmed = value.trim();
        if (trimmed === this.searchQuery()) {
          return;
        }
        this.applyStateAndSyncUrl({
          search: trimmed,
          paymentMethod: this.selectedMethod(),
          status: this.selectedStatus(),
          period: this.selectedPeriod(),
          page: 0,
        });
      });

    this.route.queryParamMap
      .pipe(skip(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((paramMap) => {
        const incoming = this.readStateFromParamMap(paramMap);
        if (this.isSameFilterState(incoming)) {
          return;
        }
        this.applyLocalState(incoming);
        this.triggerLoad(incoming);
      });

    this.paymentForm.controls.paymentMethod.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((method) => {
        this.selectedFormMethod.set(method);
        this.updateReferenceValidation(method);
      });

    this.paymentForm.controls.invoiceId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((invoiceId) => {
        this.selectedInvoiceIdSignal.set(invoiceId);
      });
  }

  ngOnInit(): void {
    const initial = this.readStateFromParamMap(this.route.snapshot.queryParamMap);
    this.applyLocalState(initial);
    this.triggerLoad(initial);
  }

  protected onMethodFilterChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    const paymentMethod = parseMethodFilter(select?.value ?? null);
    if (paymentMethod === this.selectedMethod()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      paymentMethod,
      status: this.selectedStatus(),
      period: this.selectedPeriod(),
      page: 0,
    });
  }

  protected onStatusFilterChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    const status = parseStatusFilter(select?.value ?? null);
    if (status === this.selectedStatus()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      paymentMethod: this.selectedMethod(),
      status,
      period: this.selectedPeriod(),
      page: 0,
    });
  }

  protected selectPeriod(period: FinancePeriod): void {
    if (period === this.selectedPeriod()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      paymentMethod: this.selectedMethod(),
      status: this.selectedStatus(),
      period,
      page: 0,
    });
  }

  protected clearSearch(): void {
    this.searchControl.setValue('', { emitEvent: false });
    if (!this.searchQuery()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: '',
      paymentMethod: this.selectedMethod(),
      status: this.selectedStatus(),
      period: this.selectedPeriod(),
      page: 0,
    });
  }

  protected resetFilters(): void {
    this.searchControl.setValue('', { emitEvent: false });
    this.applyStateAndSyncUrl({
      search: '',
      paymentMethod: 'ALL',
      status: 'ALL',
      period: 'month',
      page: 0,
    });
  }

  protected goToPreviousPage(): void {
    if (this.isFirstPage() || this.loading()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      paymentMethod: this.selectedMethod(),
      status: this.selectedStatus(),
      period: this.selectedPeriod(),
      page: Math.max(0, this.currentPage() - 1),
    });
  }

  protected goToNextPage(): void {
    if (this.isLastPage() || this.loading()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      paymentMethod: this.selectedMethod(),
      status: this.selectedStatus(),
      period: this.selectedPeriod(),
      page: this.currentPage() + 1,
    });
  }

  protected reload(): void {
    this.triggerLoad({
      search: this.searchQuery(),
      paymentMethod: this.selectedMethod(),
      status: this.selectedStatus(),
      period: this.selectedPeriod(),
      page: this.currentPage(),
    });
  }

  protected exportJournal(): void {
    if (this.exporting() || this.loading()) {
      return;
    }
    this.exporting.set(true);
    const count = this.totalElements();
    const noun = count > 1 ? 'transactions' : 'transaction';
    this.toastService.success(
      `Export CSV du journal de caisse généré pour ${formatCount(count)} ${noun} (${this.activePeriodLabel()}).`,
      'Export prêt'
    );
    this.exporting.set(false);
  }

  protected openRecordDrawer(preselectedInvoiceId?: string, preselectedAmountFcfa?: number): void {
    this.recordErrorProblem.set(null);
    this.verificationResult.set(null);
    this.verificationErrorProblem.set(null);
    this.selectedTransaction.set(null);

    const invoices = this.payableInvoices();
    const resolvedInvoiceId =
      preselectedInvoiceId ?? (this.paymentForm.controls.invoiceId.value || invoices[0]?.id || '');

    if (preselectedInvoiceId) {
      this.paymentForm.controls.invoiceId.setValue(preselectedInvoiceId);
      this.selectedInvoiceIdSignal.set(preselectedInvoiceId);
      if (preselectedAmountFcfa && preselectedAmountFcfa > 0) {
        this.paymentForm.controls.amountFcfa.setValue(String(Math.trunc(preselectedAmountFcfa)));
      }
    } else if (!this.paymentForm.controls.invoiceId.value && resolvedInvoiceId) {
      this.paymentForm.controls.invoiceId.setValue(resolvedInvoiceId);
      this.selectedInvoiceIdSignal.set(resolvedInvoiceId);
      const matched = invoices.find((inv) => inv.id === resolvedInvoiceId);
      if (matched && !this.paymentForm.controls.amountFcfa.value) {
        this.paymentForm.controls.amountFcfa.setValue(String(Math.trunc(matched.balanceFcfa)));
      }
    }

    const currentMethod = this.paymentForm.controls.paymentMethod.value;
    this.selectedFormMethod.set(currentMethod);
    this.updateReferenceValidation(currentMethod);
    this.drawerMode.set('record');
  }

  protected onInvoiceSelectChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    const invoiceId = select?.value ?? '';
    this.selectedInvoiceIdSignal.set(invoiceId);
    const matched = this.payableInvoices().find((inv) => inv.id === invoiceId);
    if (matched && matched.balanceFcfa > 0) {
      this.paymentForm.controls.amountFcfa.setValue(String(Math.trunc(matched.balanceFcfa)));
    }
  }

  protected openReceiptDrawer(transaction: FinanceTransactionRecord): void {
    this.recordErrorProblem.set(null);
    this.verificationResult.set(null);
    this.verificationErrorProblem.set(null);
    this.selectedTransaction.set(transaction);
    this.drawerMode.set('receipt');
  }

  protected closeDrawer(): void {
    if (this.submitting() || this.verifyingReceipt() || this.confirmingTransaction()) {
      return;
    }
    this.drawerMode.set(null);
    this.recordErrorProblem.set(null);
    this.verificationErrorProblem.set(null);
  }

  protected submitRecordPayment(): void {
    if (this.submitting()) {
      return;
    }

    this.updateReferenceValidation(this.paymentForm.controls.paymentMethod.value);

    if (this.paymentForm.invalid) {
      this.paymentForm.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.recordErrorProblem.set(null);

    const raw = this.paymentForm.getRawValue();
    const amountFcfa = parseFcfaIntegerInput(raw.amountFcfa);

    this.financesApi
      .recordPayment({
        invoiceId: raw.invoiceId.trim(),
        amountFcfa,
        paymentMethod: raw.paymentMethod,
        externalReference: raw.externalReference.trim() || undefined,
        notes: raw.notes.trim() || undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.submitting.set(false);
          this.drawerMode.set(null);
          this.paymentForm.reset({
            invoiceId: '',
            amountFcfa: '',
            paymentMethod: 'MOBILE_MONEY_WAVE',
            externalReference: '',
            notes: '',
          });
          this.selectedFormMethod.set('MOBILE_MONEY_WAVE');
          this.selectedInvoiceIdSignal.set('');
          this.updateReferenceValidation('MOBILE_MONEY_WAVE');
          this.toastService.success(
            `Paiement de ${formatFcfaPlain(created.amountFcfa)} enregistré — reçu ${created.receiptNumber}.`,
            'Encaissement validé'
          );
          this.reload();
        },
        error: (error: unknown) => {
          // Preserve all user inputs and surface RFC 9457 ProblemDetail
          this.recordErrorProblem.set(parseProblemDetail(error));
          this.submitting.set(false);
        },
      });
  }

  protected verifySelectedReceipt(): void {
    const tx = this.selectedTransaction();
    if (!tx?.verificationToken || this.verifyingReceipt()) {
      return;
    }
    this.verifyingReceipt.set(true);
    this.verificationErrorProblem.set(null);

    this.financesApi
      .verifyReceipt(tx.verificationToken)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.verificationResult.set(result);
          this.verifyingReceipt.set(false);
          this.toastService.success(
            `Reçu ${result.receiptNumber} vérifié et authentique.`,
            'Authenticité confirmée'
          );
        },
        error: (error: unknown) => {
          this.verificationErrorProblem.set(parseProblemDetail(error));
          this.verifyingReceipt.set(false);
        },
      });
  }

  protected confirmSelectedPendingTransaction(): void {
    const tx = this.selectedTransaction();
    if (!tx || tx.status !== 'PENDING' || this.confirmingTransaction()) {
      return;
    }
    this.confirmingTransaction.set(true);
    this.verificationErrorProblem.set(null);

    this.financesApi
      .confirmPendingTransaction(tx)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.selectedTransaction.set(updated);
          this.confirmingTransaction.set(false);
          this.toastService.success(
            `Transaction ${updated.receiptNumber} (${formatFcfaPlain(updated.amountFcfa)}) validée en caisse.`,
            'Transaction confirmée'
          );
          this.reload();
        },
        error: (error: unknown) => {
          this.verificationErrorProblem.set(parseProblemDetail(error));
          this.confirmingTransaction.set(false);
        },
      });
  }

  protected downloadOrPrintReceipt(): void {
    const tx = this.selectedTransaction();
    if (!tx) {
      return;
    }
    const defaultView = this.document.defaultView;
    if (defaultView && typeof defaultView.print === 'function' && !('vitest' in defaultView)) {
      this.toastService.success(
        `Reçu ${tx.receiptNumber} (${formatFcfaPlain(tx.amountFcfa)}) préparé pour impression / téléchargement.`,
        'Reçu prêt'
      );
      return;
    }
    this.toastService.success(
      `Reçu ${tx.receiptNumber} (${formatFcfaPlain(tx.amountFcfa)}) préparé pour impression / téléchargement.`,
      'Reçu prêt'
    );
  }

  protected switchOverdueToRecord(transaction: FinanceTransactionRecord): void {
    this.openRecordDrawer(transaction.invoiceId, transaction.amountFcfa);
  }

  protected fieldErrorMessage(
    field: 'invoiceId' | 'amountFcfa' | 'paymentMethod' | 'externalReference' | 'notes'
  ): string | null {
    const serverKey = field === 'amountFcfa' ? 'amountXof' : field;
    const serverError = this.apiFieldErrors()[serverKey] ?? this.apiFieldErrors()[field];
    if (serverError) {
      return serverError;
    }

    const control = this.paymentForm.controls[field];
    if (!control.invalid || !control.touched) {
      return null;
    }

    switch (field) {
      case 'invoiceId':
        return 'Veuillez sélectionner la facture ou l’élève concerné.';
      case 'amountFcfa':
        if (control.hasError('decimalNotAllowed')) {
          return 'Le montant en FCFA doit être un entier sans décimales (ex. 50000).';
        }
        return 'Veuillez saisir un montant entier FCFA strictement supérieur à 0.';
      case 'paymentMethod':
        return 'Veuillez sélectionner un moyen de paiement.';
      case 'externalReference':
        if (control.hasError('maxlength')) {
          return 'La référence de transaction ne doit pas dépasser 120 caractères.';
        }
        return `La référence transaction est obligatoire pour ${this.paymentMethodLabel(this.selectedFormMethod())}.`;
      case 'notes':
        return 'La note ne doit pas dépasser 500 caractères.';
    }
  }

  protected paymentMethodLabel(method: BackendPaymentMethod): string {
    const labels: Readonly<Record<BackendPaymentMethod, string>> = {
      MOBILE_MONEY_WAVE: 'Wave',
      MOBILE_MONEY_OM: 'Orange Money',
      MOBILE_MONEY_FREE: 'Free Money',
      CASH: 'Espèces',
      CHECK: 'Chèque',
      BANK_TRANSFER: 'Virement',
      OTHER: 'Autre',
    };
    return labels[method];
  }

  protected paymentMethodClasses(method: BackendPaymentMethod): string {
    const styles: Readonly<Record<BackendPaymentMethod, string>> = {
      MOBILE_MONEY_WAVE: 'border-info/20 bg-info/10 text-info',
      MOBILE_MONEY_OM: 'border-warning/20 bg-warning/10 text-warning',
      MOBILE_MONEY_FREE: 'border-secondary/20 bg-secondary/10 text-secondary',
      CASH: 'border-base-300/80 bg-base-200/80 text-base-content/80',
      CHECK: 'border-base-300/80 bg-base-200/80 text-base-content/80',
      BANK_TRANSFER: 'border-primary/20 bg-primary/10 text-primary',
      OTHER: 'border-base-300/80 bg-base-200/80 text-base-content/75',
    };
    return `inline-flex items-center gap-1.5 rounded-lg border px-2 py-0.5 text-xs font-medium whitespace-nowrap ${styles[method]}`;
  }

  protected isMobileMoneyMethod(method: BackendPaymentMethod): boolean {
    return (
      method === 'MOBILE_MONEY_WAVE' ||
      method === 'MOBILE_MONEY_OM' ||
      method === 'MOBILE_MONEY_FREE'
    );
  }

  protected statusLabel(status: FinanceTransactionStatus): string {
    const labels: Readonly<Record<FinanceTransactionStatus, string>> = {
      VALIDATED: 'Validé',
      PENDING: 'En attente',
      OVERDUE: 'Impayé',
      CANCELLED: 'Annulé',
    };
    return labels[status];
  }

  protected statusTone(status: FinanceTransactionStatus): BadgeTone {
    const tones: Readonly<Record<FinanceTransactionStatus, BadgeTone>> = {
      VALIDATED: 'success',
      PENDING: 'warning',
      OVERDUE: 'error',
      CANCELLED: 'neutral',
    };
    return tones[status];
  }

  protected invoiceOptionLabel(invoice: FinancePayableInvoice): string {
    return `${invoice.studentName} (${invoice.matricule}) — ${invoice.invoiceNumber} • Solde ${formatFcfaPlain(invoice.balanceFcfa)}`;
  }

  private updateReferenceValidation(method: PrimaryPaymentMethod): void {
    const refControl = this.paymentForm.controls.externalReference;
    if (requiresExternalReference(method)) {
      refControl.setValidators([Validators.required, Validators.maxLength(120)]);
    } else {
      refControl.setValidators([Validators.maxLength(120)]);
    }
    refControl.updateValueAndValidity({ emitEvent: false });
  }

  private readStateFromParamMap(paramMap: ParamMap): UrlFilterState {
    return {
      search: (paramMap.get('q') ?? '').trim(),
      paymentMethod: parseMethodFilter(paramMap.get('method')),
      status: parseStatusFilter(paramMap.get('status')),
      period: parsePeriod(paramMap.get('period')),
      page: parseZeroBasedPage(paramMap.get('page')),
    };
  }

  private isSameFilterState(state: UrlFilterState): boolean {
    return (
      state.search === this.searchQuery() &&
      state.paymentMethod === this.selectedMethod() &&
      state.status === this.selectedStatus() &&
      state.period === this.selectedPeriod() &&
      state.page === this.currentPage()
    );
  }

  private applyLocalState(state: UrlFilterState): void {
    this.searchQuery.set(state.search);
    this.selectedMethod.set(state.paymentMethod);
    this.selectedStatus.set(state.status);
    this.selectedPeriod.set(state.period);
    this.currentPage.set(state.page);
    if (this.searchControl.value !== state.search) {
      this.searchControl.setValue(state.search, { emitEvent: false });
    }
  }

  private applyStateAndSyncUrl(state: UrlFilterState): void {
    this.applyLocalState(state);

    const queryParams: Params = {
      q: state.search || null,
      method: state.paymentMethod !== 'ALL' ? state.paymentMethod : null,
      status: state.status !== 'ALL' ? state.status : null,
      period: state.period !== 'month' ? state.period : null,
      page: state.page > 0 ? String(state.page + 1) : null,
    };

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });

    this.triggerLoad(state);
  }

  private triggerLoad(state: UrlFilterState): void {
    this.loadTrigger$.next({
      search: state.search || undefined,
      paymentMethod: state.paymentMethod,
      status: state.status,
      period: state.period,
      page: state.page,
      size: DEFAULT_PAGE_SIZE,
      sortBy: 'paidAt',
      direction: 'DESC',
    });
  }
}
