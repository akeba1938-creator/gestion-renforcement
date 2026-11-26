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
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, ParamMap, Params, Router } from '@angular/router';
import {
  LucideCalendar,
  LucideCalendarCheck,
  LucideCheck,
  LucideChevronLeft,
  LucideChevronRight,
  LucideCircleCheck,
  LucideClock,
  LucideDownload,
  LucideFileText,
  LucidePlus,
  LucideRotateCcw,
  LucideSearch,
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
import { AbsencesApiService } from './data-access/absences-api.service';
import {
  AbsenceClassOption,
  AbsenceRecord,
  AbsenceRecordStatus,
  AbsenceStatusFilter,
  AbsencesKpiSummary,
  AbsencesListQuery,
  AbsencesPageResult,
  BackendAttendanceTimeSlot,
  RollCallStatus,
  RollCallStudentItem,
  TIME_SLOT_OPTIONS,
  formatDakarLongDate,
  getTodayDakarIsoDate,
} from './models/absences.model';

export type AbsenceDrawerMode = 'rollcall' | 'justify' | null;

interface StatusFilterOption {
  readonly value: AbsenceStatusFilter;
  readonly label: string;
}

interface UrlFilterState {
  readonly search: string;
  readonly className: string;
  readonly status: AbsenceStatusFilter;
  readonly date: string;
  readonly page: number;
}

interface RollCallDraftRow {
  readonly status: RollCallStatus;
  readonly lateMinutes: number;
}

const DEFAULT_PAGE_SIZE = 8;
const SEARCH_DEBOUNCE_MS = 300;
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

const STATUS_FILTER_OPTIONS: readonly StatusFilterOption[] = [
  { value: 'ALL', label: 'Tous les statuts' },
  { value: 'JUSTIFIED', label: 'Justifiée' },
  { value: 'UNJUSTIFIED', label: 'Non justifiée' },
  { value: 'LATE', label: 'Retard' },
];

const INTEGER_FORMATTER = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });
const PERCENT_FORMATTER = new Intl.NumberFormat('fr-FR', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

function formatCount(value: number): string {
  return INTEGER_FORMATTER.format(Math.max(0, Math.trunc(value))).replace(/\u202F/g, '\u00A0');
}

function parseStatusFilter(raw: string | null): AbsenceStatusFilter {
  if (raw === 'JUSTIFIED' || raw === 'UNJUSTIFIED' || raw === 'LATE') {
    return raw;
  }
  return 'ALL';
}

function parseDateFilter(raw: string | null): string {
  const trimmed = raw?.trim() ?? '';
  return ISO_DATE_PATTERN.test(trimmed) ? trimmed : '';
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

@Component({
  selector: 'app-absences-page',
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
    DakarDatePipe,
    LucideCalendar,
    LucideCalendarCheck,
    LucideCheck,
    LucideChevronLeft,
    LucideChevronRight,
    LucideCircleCheck,
    LucideClock,
    LucideDownload,
    LucideFileText,
    LucidePlus,
    LucideRotateCcw,
    LucideSearch,
    LucideWifiOff,
    LucideX,
  ],
  templateUrl: './absences-page.html',
})
export class AbsencesPageComponent implements OnInit {
  private readonly absencesApi = inject(AbsencesApiService);
  private readonly toastService = inject(ToastService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly networkStatus = inject(NetworkStatusService);

  private readonly loadTrigger$ = new Subject<AbsencesListQuery>();

  protected readonly statusFilterOptions = STATUS_FILTER_OPTIONS;
  protected readonly timeSlotOptions = TIME_SLOT_OPTIONS;
  protected readonly pageSize = DEFAULT_PAGE_SIZE;
  protected readonly todayDakarLabel = formatDakarLongDate();
  protected readonly todayDakarIso = getTodayDakarIsoDate();

  protected readonly searchControl = new FormControl<string>('', { nonNullable: true });

  protected readonly searchQuery = signal<string>('');
  protected readonly selectedClass = signal<string>('');
  protected readonly selectedStatus = signal<AbsenceStatusFilter>('ALL');
  protected readonly selectedDate = signal<string>('');
  protected readonly currentPage = signal<number>(0);

  protected readonly pageResult = signal<AbsencesPageResult | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorProblem = signal<ProblemDetail | null>(null);
  protected readonly exporting = signal<boolean>(false);

  protected readonly drawerMode = signal<AbsenceDrawerMode>(null);
  protected readonly selectedRecord = signal<AbsenceRecord | null>(null);
  protected readonly submitting = signal<boolean>(false);
  protected readonly rollCallErrorProblem = signal<ProblemDetail | null>(null);
  protected readonly justifyErrorProblem = signal<ProblemDetail | null>(null);

  protected readonly rollCallClassId = signal<string>('');
  protected readonly rollCallDate = signal<string>(this.todayDakarIso);
  protected readonly rollCallTimeSlot = signal<BackendAttendanceTimeSlot>('MORNING');
  protected readonly rollCallNotes = signal<string>('');
  protected readonly rollCallDrafts = signal<Readonly<Record<string, RollCallDraftRow>>>({});

  protected readonly justifyForm = this.formBuilder.nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(500)]],
    documentRef: ['', [Validators.maxLength(512)]],
  });

  protected readonly records = computed<readonly AbsenceRecord[]>(
    () => this.pageResult()?.content ?? []
  );

  protected readonly kpis = computed<AbsencesKpiSummary | null>(
    () => this.pageResult()?.kpis ?? null
  );

  protected readonly availableClasses = computed<readonly AbsenceClassOption[]>(
    () => this.pageResult()?.availableClasses ?? []
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
      this.selectedClass().trim().length > 0 ||
      this.selectedStatus() !== 'ALL' ||
      this.selectedDate().trim().length > 0
  );

  protected readonly headerSubtitle = computed<string>(() => {
    if (this.loading() && !this.pageResult()) {
      return `${this.todayDakarLabel} • Chargement du registre d'assiduité…`;
    }
    const total = this.totalElements();
    if (total === 0) {
      return this.hasActiveFilters()
        ? `${this.todayDakarLabel} • 0 signalement trouvé`
        : `${this.todayDakarLabel} • Aucun signalement enregistré`;
    }
    const noun = total > 1 ? 'signalements' : 'signalement';
    return `${this.todayDakarLabel} • ${formatCount(total)} ${noun}`;
  });

  protected readonly attendanceRateValue = computed<string>(() => {
    const rate = this.kpis()?.attendanceRatePercent ?? 0;
    return `${PERCENT_FORMATTER.format(rate)} %`;
  });

  protected readonly attendanceRateTrendTone = computed<StatCardTrendTone>(() => {
    const rate = this.kpis()?.attendanceRatePercent ?? 0;
    if (rate >= 94) {
      return 'success';
    }
    if (rate >= 88) {
      return 'warning';
    }
    return 'error';
  });

  protected readonly paginationRangeLabel = computed<string>(() => {
    const result = this.pageResult();
    if (!result || result.totalElements === 0 || result.content.length === 0) {
      return '0 résultat';
    }
    const start = result.page * result.size + 1;
    const end = start + result.content.length - 1;
    const noun = result.totalElements > 1 ? 'signalements' : 'signalement';
    return `Affichage de ${formatCount(start)}–${formatCount(end)} sur ${formatCount(result.totalElements)} ${noun}`;
  });

  protected readonly currentPageDisplay = computed<number>(() => this.currentPage() + 1);

  protected readonly drawerOpen = computed<boolean>(() => this.drawerMode() !== null);

  protected readonly drawerSize = computed<'md' | 'lg'>(() =>
    this.drawerMode() === 'rollcall' ? 'lg' : 'md'
  );

  protected readonly drawerTitle = computed<string>(() => {
    if (this.drawerMode() === 'rollcall') {
      return 'Faire l’appel rapide';
    }
    const record = this.selectedRecord();
    if (!record) {
      return 'Détail du signalement';
    }
    return record.justified ? 'Détail du justificatif' : 'Justifier l’absence';
  });

  protected readonly drawerSubtitle = computed<string>(() => {
    if (this.drawerMode() === 'rollcall') {
      const selected = this.availableClasses().find((c) => c.id === this.rollCallClassId());
      return selected
        ? `Classe ${selected.name} • Bascule rapide Présent / Absent / Retard`
        : 'Sélectionnez une classe et un créneau pour enregistrer la feuille d’appel';
    }
    const record = this.selectedRecord();
    return record ? `${record.studentName} • ${record.className} (${record.matricule})` : '';
  });

  protected readonly currentRollCallRoster = computed<readonly RollCallStudentItem[]>(() => {
    const classId = this.rollCallClassId();
    if (!classId) {
      return [];
    }
    return this.pageResult()?.rollCallRosterByClass[classId] ?? [];
  });

  protected readonly rollCallSummary = computed<{
    readonly present: number;
    readonly absent: number;
    readonly late: number;
  }>(() => {
    const roster = this.currentRollCallRoster();
    const drafts = this.rollCallDrafts();
    let present = 0;
    let absent = 0;
    let late = 0;
    for (const student of roster) {
      const status = drafts[student.studentId]?.status ?? 'PRESENT';
      if (status === 'ABSENT') {
        absent += 1;
      } else if (status === 'LATE') {
        late += 1;
      } else {
        present += 1;
      }
    }
    return { present, absent, late };
  });

  protected readonly justifyApiFieldErrors = computed<Readonly<Record<string, string>>>(() =>
    extractFieldErrors(this.justifyErrorProblem())
  );

  constructor() {
    this.loadTrigger$
      .pipe(
        switchMap((query) => {
          this.loading.set(true);
          this.errorProblem.set(null);
          return this.absencesApi.list(query).pipe(
            catchError((error: unknown) => {
              this.errorProblem.set(parseProblemDetail(error));
              this.loading.set(false);
              return of<AbsencesPageResult | null>(null);
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
          className: this.selectedClass(),
          status: this.selectedStatus(),
          date: this.selectedDate(),
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
  }

  ngOnInit(): void {
    const initial = this.readStateFromParamMap(this.route.snapshot.queryParamMap);
    this.applyLocalState(initial);
    this.triggerLoad(initial);
  }

  protected formatCountValue(value: number): string {
    return formatCount(value);
  }

  protected onClassFilterChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    const className = select?.value?.trim() ?? '';
    if (className === this.selectedClass()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      className,
      status: this.selectedStatus(),
      date: this.selectedDate(),
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
      className: this.selectedClass(),
      status,
      date: this.selectedDate(),
      page: 0,
    });
  }

  protected onDateFilterChange(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const date = parseDateFilter(input?.value ?? null);
    if (date === this.selectedDate()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      className: this.selectedClass(),
      status: this.selectedStatus(),
      date,
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
      className: this.selectedClass(),
      status: this.selectedStatus(),
      date: this.selectedDate(),
      page: 0,
    });
  }

  protected resetFilters(): void {
    this.searchControl.setValue('', { emitEvent: false });
    this.applyStateAndSyncUrl({
      search: '',
      className: '',
      status: 'ALL',
      date: '',
      page: 0,
    });
  }

  protected goToPreviousPage(): void {
    if (this.isFirstPage() || this.loading()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      className: this.selectedClass(),
      status: this.selectedStatus(),
      date: this.selectedDate(),
      page: Math.max(0, this.currentPage() - 1),
    });
  }

  protected goToNextPage(): void {
    if (this.isLastPage() || this.loading()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      className: this.selectedClass(),
      status: this.selectedStatus(),
      date: this.selectedDate(),
      page: this.currentPage() + 1,
    });
  }

  protected reload(): void {
    this.triggerLoad({
      search: this.searchQuery(),
      className: this.selectedClass(),
      status: this.selectedStatus(),
      date: this.selectedDate(),
      page: this.currentPage(),
    });
  }

  protected exportRegister(): void {
    if (this.exporting() || this.loading()) {
      return;
    }
    this.exporting.set(true);
    const count = this.totalElements();
    const noun = count > 1 ? 'signalements' : 'signalement';
    this.toastService.success(
      `Export CSV du registre d'assiduité préparé pour ${formatCount(count)} ${noun}.`,
      'Export prêt'
    );
    this.exporting.set(false);
  }

  protected openRollCallDrawer(): void {
    this.rollCallErrorProblem.set(null);
    this.justifyErrorProblem.set(null);
    this.selectedRecord.set(null);

    const classes = this.availableClasses();
    if (!this.rollCallClassId() && classes.length > 0) {
      const matched = this.selectedClass()
        ? classes.find(
            (c) => c.name === this.selectedClass() || c.id === this.selectedClass()
          )
        : undefined;
      const initialClassId = matched?.id ?? classes[0].id;
      this.rollCallClassId.set(initialClassId);
      this.initializeDraftsForClass(initialClassId);
    } else if (this.rollCallClassId() && Object.keys(this.rollCallDrafts()).length === 0) {
      this.initializeDraftsForClass(this.rollCallClassId());
    }

    if (!this.rollCallDate()) {
      this.rollCallDate.set(this.todayDakarIso);
    }
    this.drawerMode.set('rollcall');
  }

  protected onRollCallClassChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    const classId = select?.value?.trim() ?? '';
    this.rollCallClassId.set(classId);
    this.rollCallErrorProblem.set(null);
    this.initializeDraftsForClass(classId);
  }

  protected onRollCallDateChange(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const value = parseDateFilter(input?.value ?? null);
    if (value) {
      this.rollCallDate.set(value);
    }
  }

  protected onRollCallTimeSlotChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    const value = select?.value;
    if (value === 'MORNING' || value === 'AFTERNOON' || value === 'FULL_DAY' || value === 'CUSTOM') {
      this.rollCallTimeSlot.set(value);
    }
  }

  protected onRollCallNotesInput(event: Event): void {
    const textarea = event.target as HTMLTextAreaElement | null;
    this.rollCallNotes.set(textarea?.value ?? '');
  }

  protected getStudentDraftStatus(studentId: string): RollCallStatus {
    return this.rollCallDrafts()[studentId]?.status ?? 'PRESENT';
  }

  protected getStudentLateMinutes(studentId: string): number {
    return this.rollCallDrafts()[studentId]?.lateMinutes ?? 15;
  }

  protected setStudentRollCallStatus(studentId: string, status: RollCallStatus): void {
    const current = this.rollCallDrafts();
    const existing = current[studentId];
    this.rollCallDrafts.set({
      ...current,
      [studentId]: {
        status,
        lateMinutes: existing?.lateMinutes ?? 15,
      },
    });
  }

  protected onStudentLateMinutesInput(studentId: string, event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const parsed = Number.parseInt(input?.value ?? '', 10);
    const minutes = Number.isFinite(parsed) && parsed > 0 ? Math.min(240, parsed) : 15;
    const current = this.rollCallDrafts();
    const existing = current[studentId];
    this.rollCallDrafts.set({
      ...current,
      [studentId]: {
        status: existing?.status ?? 'LATE',
        lateMinutes: minutes,
      },
    });
  }

  protected markAllPresent(): void {
    const roster = this.currentRollCallRoster();
    const next: Record<string, RollCallDraftRow> = {};
    for (const student of roster) {
      next[student.studentId] = { status: 'PRESENT', lateMinutes: 15 };
    }
    this.rollCallDrafts.set(next);
  }

  protected submitRollCall(): void {
    if (this.submitting()) {
      return;
    }

    const classId = this.rollCallClassId();
    const roster = this.currentRollCallRoster();
    const selectedClassOption = this.availableClasses().find((c) => c.id === classId);
    if (!classId || !selectedClassOption || roster.length === 0) {
      this.rollCallErrorProblem.set({
        type: 'urn:ecole:problem:validation-error',
        title: 'Feuille d’appel incomplète',
        status: 422,
        detail: 'Veuillez sélectionner une classe disposant d’élèves inscrits avant de valider l’appel.',
      });
      return;
    }

    const drafts = this.rollCallDrafts();
    const entries = roster.map((student) => {
      const row = drafts[student.studentId] ?? { status: 'PRESENT' as RollCallStatus, lateMinutes: 15 };
      return {
        studentId: student.studentId,
        status: row.status,
        lateMinutes: row.status === 'LATE' ? row.lateMinutes : undefined,
      };
    });

    this.submitting.set(true);
    this.rollCallErrorProblem.set(null);

    this.absencesApi
      .submitRollCall({
        classRoomId: classId,
        className: selectedClassOption.name,
        sessionDate: this.rollCallDate() || this.todayDakarIso,
        timeSlot: this.rollCallTimeSlot(),
        notes: this.rollCallNotes().trim() || undefined,
        entries,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.submitting.set(false);
          this.drawerMode.set(null);
          this.rollCallNotes.set('');
          this.initializeDraftsForClass(classId);
          this.toastService.success(
            `Appel ${result.className} enregistré : ${result.presentCount} présent(s), ${result.absentCount} absent(s), ${result.lateCount} retard(s).`,
            'Appel validé'
          );
          this.reload();
        },
        error: (error: unknown) => {
          // Preserve all user selections and notes on RFC 9457 error
          this.rollCallErrorProblem.set(parseProblemDetail(error));
          this.submitting.set(false);
        },
      });
  }

  protected openJustifyDrawer(record: AbsenceRecord): void {
    this.rollCallErrorProblem.set(null);
    this.justifyErrorProblem.set(null);
    this.selectedRecord.set(record);
    this.justifyForm.reset({
      reason: record.justificationReason ?? '',
      documentRef: record.justificationDocumentRef ?? '',
    });
    this.drawerMode.set('justify');
  }

  protected submitJustification(): void {
    const record = this.selectedRecord();
    if (!record || this.submitting()) {
      return;
    }

    if (this.justifyForm.invalid) {
      this.justifyForm.markAllAsTouched();
      return;
    }

    const raw = this.justifyForm.getRawValue();
    this.submitting.set(true);
    this.justifyErrorProblem.set(null);

    this.absencesApi
      .justifyRecord(
        {
          recordId: record.id,
          reason: raw.reason.trim(),
          documentRef: raw.documentRef.trim() || undefined,
        },
        record
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.submitting.set(false);
          this.selectedRecord.set(updated);
          this.applyOptimisticRecordUpdate(updated);
          this.drawerMode.set(null);
          this.toastService.success(
            `Justification enregistrée pour ${updated.studentName}.`,
            'Absence justifiée'
          );
          this.reload();
        },
        error: (error: unknown) => {
          // Preserve user inputs and surface RFC 9457 ProblemDetail
          this.justifyErrorProblem.set(parseProblemDetail(error));
          this.submitting.set(false);
        },
      });
  }

  protected closeDrawer(): void {
    if (this.submitting()) {
      return;
    }
    this.drawerMode.set(null);
    this.rollCallErrorProblem.set(null);
    this.justifyErrorProblem.set(null);
  }

  protected statusLabel(record: AbsenceRecord): string {
    switch (record.status) {
      case 'JUSTIFIED':
        return 'Justifiée';
      case 'UNJUSTIFIED':
        return 'Non justifiée';
      case 'LATE':
        return record.lateMinutes ? `Retard (${record.lateMinutes} min)` : 'Retard';
    }
  }

  protected statusTone(status: AbsenceRecordStatus): BadgeTone {
    switch (status) {
      case 'JUSTIFIED':
        return 'success';
      case 'UNJUSTIFIED':
        return 'error';
      case 'LATE':
        return 'warning';
    }
  }

  protected justifyFieldErrorMessage(field: 'reason' | 'documentRef'): string | null {
    const control = this.justifyForm.controls[field];
    if (control.touched || control.dirty) {
      if (control.hasError('required')) {
        return 'Le motif de justification est obligatoire.';
      }
      if (control.hasError('maxlength')) {
        return field === 'reason'
          ? 'Le motif ne doit pas dépasser 500 caractères.'
          : 'La référence ne doit pas dépasser 512 caractères.';
      }
    }
    return this.justifyApiFieldErrors()[field] ?? null;
  }

  private initializeDraftsForClass(classId: string): void {
    const roster = this.pageResult()?.rollCallRosterByClass[classId] ?? [];
    const next: Record<string, RollCallDraftRow> = {};
    for (const student of roster) {
      next[student.studentId] = { status: 'PRESENT', lateMinutes: 15 };
    }
    this.rollCallDrafts.set(next);
  }

  private applyOptimisticRecordUpdate(updated: AbsenceRecord): void {
    const current = this.pageResult();
    if (!current) {
      return;
    }
    const nextContent = current.content.map((item) => (item.id === updated.id ? updated : item));
    const wasUnjustified =
      current.content.find((item) => item.id === updated.id)?.status === 'UNJUSTIFIED';
    const nextUnjustified =
      wasUnjustified && updated.status === 'JUSTIFIED'
        ? Math.max(0, current.kpis.unjustifiedCount - 1)
        : current.kpis.unjustifiedCount;

    this.pageResult.set({
      ...current,
      content: nextContent,
      kpis: {
        ...current.kpis,
        unjustifiedCount: nextUnjustified,
      },
    });
  }

  private readStateFromParamMap(paramMap: ParamMap): UrlFilterState {
    return {
      search: paramMap.get('q')?.trim() ?? '',
      className: paramMap.get('class')?.trim() ?? '',
      status: parseStatusFilter(paramMap.get('status')),
      date: parseDateFilter(paramMap.get('date')),
      page: parseZeroBasedPage(paramMap.get('page')),
    };
  }

  private isSameFilterState(other: UrlFilterState): boolean {
    return (
      other.search === this.searchQuery() &&
      other.className === this.selectedClass() &&
      other.status === this.selectedStatus() &&
      other.date === this.selectedDate() &&
      other.page === this.currentPage()
    );
  }

  private applyLocalState(state: UrlFilterState): void {
    this.searchQuery.set(state.search);
    if (this.searchControl.value !== state.search) {
      this.searchControl.setValue(state.search, { emitEvent: false });
    }
    this.selectedClass.set(state.className);
    this.selectedStatus.set(state.status);
    this.selectedDate.set(state.date);
    this.currentPage.set(state.page);
  }

  private applyStateAndSyncUrl(state: UrlFilterState): void {
    this.applyLocalState(state);
    this.triggerLoad(state);

    const queryParams: Params = {
      q: state.search || null,
      class: state.className || null,
      status: state.status !== 'ALL' ? state.status : null,
      date: state.date || null,
      page: state.page > 0 ? String(state.page + 1) : null,
    };

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      replaceUrl: true,
    });
  }

  private triggerLoad(state: UrlFilterState): void {
    const matchedClass = state.className
      ? this.availableClasses().find(
          (item) => item.name === state.className || item.id === state.className
        )
      : undefined;

    this.loadTrigger$.next({
      page: state.page,
      size: this.pageSize,
      search: state.search || undefined,
      className: state.className || undefined,
      classRoomId: matchedClass?.id,
      status: state.status,
      date: state.date || undefined,
      sortBy: 'sessionDate',
      direction: 'DESC',
    });
  }
}
