import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  inject,
  signal,
  viewChildren,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  LucideCircleAlert,
  LucideCircleCheck,
  LucideCircleX,
  LucideInfo,
  LucideMenu,
  LucideMoon,
  LucidePalette,
  LucideSun,
  LucideX,
} from '@lucide/angular';
import { FINANCE_API } from '../../core/api/finance.api';
import { STUDENT_API } from '../../core/api/student.api';
import {
  FinanceSummary,
  StudentBalance,
  TuitionPaymentStatus,
} from '../../core/models/finance.model';
import { Student } from '../../core/models/student.model';
import { ThemeService } from '../../core/services/theme.service';
import { DataTableComponent } from '../../shared/components/data-table/data-table';
import { StatCardComponent } from '../../shared/components/stat-card/stat-card';
import { BadgeTone, StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { DakarDatePipe } from '../../shared/pipes/dakar-date.pipe';
import { FcfaPipe } from '../../shared/pipes/fcfa.pipe';

export type PreviewTabId = 'overview' | 'students' | 'forms';

interface PreviewTab {
  readonly id: PreviewTabId;
  readonly label: string;
}

interface StudentPreviewRow {
  readonly student: Student;
  readonly balanceFcfa: number | null;
  readonly paymentStatus: TuitionPaymentStatus | null;
}

@Component({
  selector: 'app-theme-preview',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    StatCardComponent,
    StatusBadgeComponent,
    DataTableComponent,
    FcfaPipe,
    DakarDatePipe,
    LucideCircleAlert,
    LucideCircleCheck,
    LucideCircleX,
    LucideInfo,
    LucideMenu,
    LucideMoon,
    LucidePalette,
    LucideSun,
    LucideX,
  ],
  templateUrl: './theme-preview.html',
})
export class ThemePreviewComponent implements OnInit {
  protected readonly themeService = inject(ThemeService);
  private readonly studentApi = inject(STUDENT_API);
  private readonly financeApi = inject(FINANCE_API);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  private readonly tabButtons = viewChildren<ElementRef<HTMLButtonElement>>('tabBtn');

  protected readonly drawerOpen = signal(false);
  protected readonly activeTab = signal<PreviewTabId>('overview');
  protected readonly students = signal<Student[]>([]);
  protected readonly balances = signal<StudentBalance[]>([]);
  protected readonly studentsLoading = signal(true);
  protected readonly studentsError = signal<string | null>(null);
  protected readonly financeSummary = signal<FinanceSummary | null>(null);
  protected readonly formSubmittedMessage = signal<string | null>(null);

  /**
   * Presentation-layer composition of Student (student module) and StudentBalance (finance module).
   */
  protected readonly studentRows = computed<StudentPreviewRow[]>(() => {
    const balanceByStudentId = new Map(this.balances().map((b) => [b.studentId, b]));
    return this.students().map((student) => {
      const balance = balanceByStudentId.get(student.id);
      return {
        student,
        balanceFcfa: balance?.balanceFcfa ?? null,
        paymentStatus: balance?.paymentStatus ?? null,
      };
    });
  });

  protected readonly tabs: readonly PreviewTab[] = [
    { id: 'overview', label: 'Vue générale' },
    { id: 'students', label: 'Élèves' },
    { id: 'forms', label: 'Formulaire & alertes' },
  ];

  protected readonly previewForm = this.formBuilder.nonNullable.group({
    studentName: ['', [Validators.required, Validators.minLength(2)]],
    className: ['Terminale S2', [Validators.required]],
    amountFcfa: [45000, [Validators.required, Validators.min(1000)]],
    notifyParent: [true],
  });

  ngOnInit(): void {
    this.loadPreviewData();
  }

  protected toggleDrawer(): void {
    this.drawerOpen.update((open) => !open);
  }

  protected closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  protected selectTab(tabId: PreviewTabId): void {
    this.activeTab.set(tabId);
  }

  protected onTabKeydown(event: KeyboardEvent, currentIndex: number): void {
    let nextIndex: number | null = null;
    if (event.key === 'ArrowRight') {
      nextIndex = (currentIndex + 1) % this.tabs.length;
    } else if (event.key === 'ArrowLeft') {
      nextIndex = (currentIndex - 1 + this.tabs.length) % this.tabs.length;
    } else if (event.key === 'Home') {
      nextIndex = 0;
    } else if (event.key === 'End') {
      nextIndex = this.tabs.length - 1;
    }

    if (nextIndex !== null) {
      event.preventDefault();
      this.selectTab(this.tabs[nextIndex].id);
      this.tabButtons()[nextIndex]?.nativeElement.focus();
    }
  }

  protected submitPreviewForm(): void {
    if (this.previewForm.invalid) {
      this.previewForm.markAllAsTouched();
      this.formSubmittedMessage.set(null);
      return;
    }
    const { studentName, className } = this.previewForm.getRawValue();
    this.formSubmittedMessage.set(
      `Simulation enregistrée pour ${studentName} (${className}) — aucune donnée envoyée.`
    );
  }

  protected paymentBadgeLabel(status: TuitionPaymentStatus | null): string {
    if (!status) {
      return 'Non renseigné';
    }
    const labels: Record<TuitionPaymentStatus, string> = {
      PAID: 'Payé',
      PENDING: 'En attente',
      OVERDUE: 'Impayé',
    };
    return labels[status];
  }

  protected paymentBadgeTone(status: TuitionPaymentStatus | null): BadgeTone {
    if (!status) {
      return 'neutral';
    }
    const tones: Record<TuitionPaymentStatus, BadgeTone> = {
      PAID: 'success',
      PENDING: 'warning',
      OVERDUE: 'error',
    };
    return tones[status];
  }

  private loadPreviewData(): void {
    this.studentsLoading.set(true);
    this.studentsError.set(null);

    this.studentApi
      .list({ page: 0, size: 5, sortBy: 'lastName', sortDir: 'asc' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.students.set(page.items);
          this.studentsLoading.set(false);
        },
        error: (err: Error) => {
          this.studentsError.set(err.message);
          this.studentsLoading.set(false);
        },
      });

    this.financeApi
      .getStudentBalances()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (balances) => this.balances.set(balances),
      });

    this.financeApi
      .getSummary()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (summary) => this.financeSummary.set(summary),
      });
  }
}
