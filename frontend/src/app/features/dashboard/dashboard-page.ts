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
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  LucideArrowRight,
  LucideBanknote,
  LucideBell,
  LucideCalendarCheck,
  LucideCircleCheck,
  LucideDownload,
  LucideMegaphone,
  LucidePlus,
  LucideReceipt,
  LucideRefreshCw,
  LucideSmartphone,
  LucideWifiOff,
} from '@lucide/angular';
import { Subject, catchError, of, switchMap } from 'rxjs';
import { ProblemDetail, parseProblemDetail } from '../../core/models/problem-detail.model';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog';
import { DrawerComponent } from '../../shared/components/drawer/drawer';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header';
import { SkeletonLoaderComponent } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatCardComponent } from '../../shared/components/stat-card/stat-card';
import { BadgeTone, StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { ToastService } from '../../shared/components/toast/toast.service';
import { DakarDatePipe } from '../../shared/pipes/dakar-date.pipe';
import { FcfaPipe } from '../../shared/pipes/fcfa.pipe';
import { CollectionChartComponent } from './components/collection-chart';
import { DASHBOARD_API } from './data-access/dashboard-mock.api';
import {
  DashboardPaymentMethod,
  DashboardPaymentRecord,
  DashboardPaymentStatus,
  DashboardPeriod,
  DashboardSnapshot,
  PriorityAlertTone,
} from './models/dashboard.model';

export type PaymentMethodFilter = 'ALL' | DashboardPaymentMethod;

interface PeriodOption {
  readonly id: DashboardPeriod;
  readonly label: string;
}

interface PaymentFilterOption {
  readonly id: PaymentMethodFilter;
  readonly label: string;
}

const PERIOD_OPTIONS: readonly PeriodOption[] = [
  { id: 'today', label: "Aujourd'hui" },
  { id: 'month', label: 'Ce mois' },
  { id: 'term', label: 'Trimestre' },
];

const PAYMENT_FILTER_OPTIONS: readonly PaymentFilterOption[] = [
  { id: 'ALL', label: 'Tous' },
  { id: 'WAVE', label: 'Wave' },
  { id: 'ORANGE_MONEY', label: 'Orange Money' },
  { id: 'CASH', label: 'Espèces' },
];

const CLASS_OPTIONS: readonly string[] = [
  'Terminale S2',
  '1ère L',
  'Seconde S',
  '3ème A',
  '6ème B',
  'CM2 (Primaire)',
];

const DAKAR_LONG_DATE_FORMATTER = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'Africa/Dakar',
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

const INTEGER_FORMATTER = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });
const PERCENT_FORMATTER = new Intl.NumberFormat('fr-FR', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

function isDashboardPeriod(value: string | null): value is DashboardPeriod {
  return value === 'today' || value === 'month' || value === 'term';
}

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    PageHeaderComponent,
    StatCardComponent,
    StatusBadgeComponent,
    SkeletonLoaderComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    DrawerComponent,
    ConfirmDialogComponent,
    CollectionChartComponent,
    FcfaPipe,
    DakarDatePipe,
    LucideArrowRight,
    LucideBanknote,
    LucideBell,
    LucideCalendarCheck,
    LucideCircleCheck,
    LucideDownload,
    LucideMegaphone,
    LucidePlus,
    LucideReceipt,
    LucideRefreshCw,
    LucideSmartphone,
    LucideWifiOff,
  ],
  templateUrl: './dashboard-page.html',
})
export class DashboardPageComponent implements OnInit {
  private readonly dashboardApi = inject(DASHBOARD_API);
  private readonly toastService = inject(ToastService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly networkStatus = inject(NetworkStatusService);

  private readonly loadTrigger$ = new Subject<DashboardPeriod>();

  protected readonly periodOptions = PERIOD_OPTIONS;
  protected readonly paymentFilterOptions = PAYMENT_FILTER_OPTIONS;
  protected readonly classOptions = CLASS_OPTIONS;

  protected readonly selectedPeriod = signal<DashboardPeriod>('month');
  protected readonly paymentMethodFilter = signal<PaymentMethodFilter>('ALL');
  protected readonly snapshot = signal<DashboardSnapshot | null>(null);
  protected readonly loading = signal(true);
  protected readonly errorProblem = signal<ProblemDetail | null>(null);

  protected readonly exporting = signal(false);
  protected readonly drawerOpen = signal(false);
  protected readonly submittingStudent = signal(false);
  protected readonly reminderDialogOpen = signal(false);
  protected readonly sendingReminders = signal(false);
  protected readonly busyPaymentId = signal<string | null>(null);

  protected readonly studentForm = this.formBuilder.nonNullable.group({
    firstName: ['', [Validators.required, Validators.minLength(2)]],
    lastName: ['', [Validators.required, Validators.minLength(2)]],
    className: [CLASS_OPTIONS[0], [Validators.required]],
    guardianPhone: ['+221', [Validators.required, Validators.pattern(/^\+221\d{9}$/)]],
  });

  protected readonly headerSubtitle = computed(() => {
    const generated = this.snapshot()?.generatedAtUtc;
    const referenceDate = generated ? new Date(generated) : new Date();
    const formattedDate = DAKAR_LONG_DATE_FORMATTER.format(referenceDate);
    const capitalizedDate =
      formattedDate.charAt(0).toUpperCase() + formattedDate.slice(1);
    return `Vue d'ensemble de l'établissement • ${capitalizedDate}`;
  });

  protected readonly isEmptySnapshot = computed(() => {
    const data = this.snapshot();
    if (!data) {
      return false;
    }
    return (
      data.kpis.enrolledStudents === 0 &&
      data.monthlyEvolution.length === 0 &&
      data.recentPayments.length === 0
    );
  });

  protected readonly filteredPayments = computed<readonly DashboardPaymentRecord[]>(() => {
    const data = this.snapshot();
    if (!data) {
      return [];
    }
    const filter = this.paymentMethodFilter();
    if (filter === 'ALL') {
      return data.recentPayments;
    }
    return data.recentPayments.filter((payment) => payment.method === filter);
  });

  protected readonly enrolledStudentsValue = computed(() => {
    const count = this.snapshot()?.kpis.enrolledStudents ?? 0;
    return INTEGER_FORMATTER.format(Math.trunc(count)).replace(/\u202F/g, '\u00A0');
  });

  protected readonly collectedKpiLabel = computed<string>(() => {
    const labels: Readonly<Record<DashboardPeriod, string>> = {
      today: 'Encaissements du jour',
      month: 'Encaissements du mois',
      term: 'Encaissements du trimestre',
    };
    return labels[this.selectedPeriod()];
  });

  protected readonly attendanceRateValue = computed(() => {
    const rate = this.snapshot()?.kpis.attendanceRatePercent ?? 0;
    return `${PERCENT_FORMATTER.format(rate)} %`;
  });

  protected readonly overdueFamiliesTrend = computed(() => {
    const count = this.snapshot()?.kpis.overdueFamiliesCount ?? 0;
    return count > 1 ? `${count} familles` : `${count} famille`;
  });

  protected readonly absencesDescription = computed(() => {
    const count = this.snapshot()?.kpis.absencesTodayCount ?? 0;
    return count > 1 ? `${count} absences aujourd'hui` : `${count} absence aujourd'hui`;
  });

  constructor() {
    this.loadTrigger$
      .pipe(
        switchMap((period) => {
          this.loading.set(true);
          this.errorProblem.set(null);
          return this.dashboardApi.getSnapshot(period).pipe(
            catchError((error: unknown) => {
              this.errorProblem.set(parseProblemDetail(error));
              this.loading.set(false);
              return of<DashboardSnapshot | null>(null);
            })
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((data) => {
        if (data !== null) {
          this.snapshot.set(data);
          this.loading.set(false);
        }
      });
  }

  ngOnInit(): void {
    const queryPeriod = this.route.snapshot.queryParamMap.get('period');
    const initialPeriod: DashboardPeriod = isDashboardPeriod(queryPeriod) ? queryPeriod : 'month';
    this.selectedPeriod.set(initialPeriod);
    this.loadTrigger$.next(initialPeriod);
  }

  protected selectPeriod(period: DashboardPeriod): void {
    if (period === this.selectedPeriod() && !this.errorProblem()) {
      return;
    }
    this.selectedPeriod.set(period);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { period },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    this.loadTrigger$.next(period);
  }

  protected reload(): void {
    this.loadTrigger$.next(this.selectedPeriod());
  }

  protected selectPaymentFilter(filter: PaymentMethodFilter): void {
    this.paymentMethodFilter.set(filter);
  }

  protected resetPaymentFilter(): void {
    this.paymentMethodFilter.set('ALL');
  }

  protected exportReport(): void {
    if (this.exporting()) {
      return;
    }
    this.exporting.set(true);
    const periodLabel =
      this.periodOptions.find((option) => option.id === this.selectedPeriod())?.label ?? 'Ce mois';
    this.toastService.success(
      `Export CSV généré pour la période « ${periodLabel} ».`,
      'Export prêt'
    );
    this.exporting.set(false);
  }

  protected openNewStudentDrawer(): void {
    this.drawerOpen.set(true);
  }

  protected closeNewStudentDrawer(): void {
    if (!this.submittingStudent()) {
      this.drawerOpen.set(false);
    }
  }

  protected submitQuickStudent(): void {
    if (this.submittingStudent()) {
      return;
    }
    if (this.studentForm.invalid) {
      this.studentForm.markAllAsTouched();
      return;
    }

    this.submittingStudent.set(true);
    const { firstName, lastName, className } = this.studentForm.getRawValue();
    this.toastService.success(
      `Dossier d'inscription préparé pour ${firstName.trim()} ${lastName.trim()} (${className}).`,
      'Élève enregistré'
    );
    this.studentForm.reset({
      firstName: '',
      lastName: '',
      className: CLASS_OPTIONS[0],
      guardianPhone: '+221',
    });
    this.submittingStudent.set(false);
    this.drawerOpen.set(false);
  }

  protected openReminderDialog(): void {
    this.reminderDialogOpen.set(true);
  }

  protected closeReminderDialog(): void {
    if (!this.sendingReminders()) {
      this.reminderDialogOpen.set(false);
    }
  }

  protected confirmGroupReminders(): void {
    if (this.sendingReminders()) {
      return;
    }
    this.sendingReminders.set(true);
    const families = this.snapshot()?.kpis.overdueFamiliesCount ?? 0;
    this.toastService.success(
      `Campagne de relance programmée pour ${families} familles en retard.`,
      'Relances programmées'
    );
    this.sendingReminders.set(false);
    this.reminderDialogOpen.set(false);
  }

  protected handlePaymentAction(payment: DashboardPaymentRecord): void {
    if (this.busyPaymentId() === payment.id) {
      return;
    }
    this.busyPaymentId.set(payment.id);
    if (payment.status === 'PENDING') {
      this.toastService.info(
        `Vérification de la transaction ${payment.receiptRef} (${payment.studentName}) lancée.`,
        'Contrôle en cours'
      );
    } else {
      this.toastService.success(
        `Reçu ${payment.receiptRef} prêt au téléchargement pour ${payment.studentName}.`,
        'Reçu disponible'
      );
    }
    this.busyPaymentId.set(null);
  }

  protected paymentMethodLabel(method: DashboardPaymentMethod): string {
    const labels: Record<DashboardPaymentMethod, string> = {
      WAVE: 'Wave',
      ORANGE_MONEY: 'Orange Money',
      CASH: 'Espèces',
    };
    return labels[method];
  }

  protected paymentMethodClasses(method: DashboardPaymentMethod): string {
    const styles: Record<DashboardPaymentMethod, string> = {
      WAVE: 'border-info/20 bg-info/10 text-info',
      ORANGE_MONEY: 'border-warning/20 bg-warning/10 text-warning',
      CASH: 'border-base-300/80 bg-base-200/80 text-base-content/80',
    };
    return `inline-flex items-center gap-1.5 rounded-lg border px-2 py-0.5 text-xs font-medium whitespace-nowrap ${styles[method]}`;
  }

  protected paymentStatusLabel(status: DashboardPaymentStatus): string {
    return status === 'VALIDATED' ? 'Validé' : 'En attente';
  }

  protected paymentStatusTone(status: DashboardPaymentStatus): BadgeTone {
    return status === 'VALIDATED' ? 'success' : 'warning';
  }

  protected alertIconClasses(tone: PriorityAlertTone): string {
    const styles: Record<PriorityAlertTone, string> = {
      warning: 'border-warning/20 bg-warning/10 text-warning',
      info: 'border-info/20 bg-info/10 text-info',
      success: 'border-success/20 bg-success/10 text-success',
      error: 'border-error/20 bg-error/10 text-error',
    };
    return `flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border ${styles[tone]}`;
  }
}
