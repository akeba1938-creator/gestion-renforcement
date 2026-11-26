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
  LucideAward,
  LucideChevronLeft,
  LucideChevronRight,
  LucideDownload,
  LucideFileCheck,
  LucideFileText,
  LucideGraduationCap,
  LucidePlus,
  LucidePrinter,
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
import { GradesApiService } from './data-access/grades-api.service';
import {
  BackendCouncilDecision,
  BackendMention,
  BackendReportCardStatus,
  DEFAULT_GRADE_TERMS,
  GradeClassOption,
  GradeEntryScoreInput,
  GradeEntryStudentOption,
  GradeSubjectOption,
  GradeTermCode,
  GradeTermOption,
  GradesKpiSummary,
  GradesListQuery,
  GradesPageResult,
  PrimaryEvaluationType,
  StudentReportCardRecord,
  SubjectGradeResult,
  parseTermCode,
  roundToTwoDecimals,
} from './models/grades.model';

export type GradesDrawerMode = 'entry' | 'bulletin' | null;

interface EvaluationTypeOption {
  readonly value: PrimaryEvaluationType;
  readonly label: string;
}

interface UrlFilterState {
  readonly search: string;
  readonly classRoomId: string;
  readonly subjectId: string;
  readonly term: GradeTermCode;
  readonly page: number;
}

const DEFAULT_PAGE_SIZE = 8;
const SEARCH_DEBOUNCE_MS = 300;
const SCORE_PATTERN = /^\d+(?:[.,]\d{1,2})?$/;

const EVALUATION_TYPE_OPTIONS: readonly EvaluationTypeOption[] = [
  { value: 'HOMEWORK', label: 'Devoir' },
  { value: 'COMPOSITION', label: 'Composition' },
];

const INTEGER_FORMATTER = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });
const DECIMAL_TWO_FORMATTER = new Intl.NumberFormat('fr-FR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});
const PERCENT_ONE_FORMATTER = new Intl.NumberFormat('fr-FR', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

function formatCount(value: number): string {
  return INTEGER_FORMATTER.format(Math.max(0, Math.trunc(value))).replace(/\u202F/g, '\u00A0');
}

function formatScoreValue(value: number | null): string {
  if (value === null || !Number.isFinite(value)) {
    return '—';
  }
  return DECIMAL_TWO_FORMATTER.format(value);
}

function formatTodayDakarIso(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Dakar',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
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
 * Validates that a grade coefficient is an integer within [1..20].
 */
export function coefficientValidator(control: AbstractControl<string | number>): ValidationErrors | null {
  const raw = String(control.value ?? '').trim();
  if (!raw) {
    return { required: true };
  }
  if (!/^\d+$/.test(raw)) {
    return { invalidCoefficient: true };
  }
  const num = Number.parseInt(raw, 10);
  if (num < 1 || num > 20) {
    return { coefficientOutOfRange: true };
  }
  return null;
}

/**
 * Parses a student score string (French comma or dot) and verifies bounds [0..20].
 */
export function parseBoundedScore(
  raw: string
): { readonly valid: true; readonly score: number | null } | { readonly valid: false; readonly error: string } {
  const trimmed = raw.trim();
  if (!trimmed) {
    return { valid: true, score: null };
  }
  if (!SCORE_PATTERN.test(trimmed)) {
    return { valid: false, error: 'Format invalide (ex. 14,5).' };
  }
  const parsed = Number.parseFloat(trimmed.replace(',', '.'));
  if (!Number.isFinite(parsed) || parsed < 0 || parsed > 20) {
    return { valid: false, error: 'Note comprise entre 0 et 20.' };
  }
  return { valid: true, score: roundToTwoDecimals(parsed) };
}

@Component({
  selector: 'app-grades-page',
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
    LucideAward,
    LucideChevronLeft,
    LucideChevronRight,
    LucideDownload,
    LucideFileCheck,
    LucideFileText,
    LucideGraduationCap,
    LucidePlus,
    LucidePrinter,
    LucideRotateCcw,
    LucideSearch,
    LucideWifiOff,
    LucideX,
  ],
  templateUrl: './grades-page.html',
})
export class GradesPageComponent implements OnInit {
  private readonly gradesApi = inject(GradesApiService);
  private readonly toastService = inject(ToastService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly document = inject(DOCUMENT);
  protected readonly networkStatus = inject(NetworkStatusService);

  private readonly loadTrigger$ = new Subject<GradesListQuery>();

  protected readonly evaluationTypeOptions = EVALUATION_TYPE_OPTIONS;
  protected readonly pageSize = DEFAULT_PAGE_SIZE;

  protected readonly searchControl = new FormControl<string>('', { nonNullable: true });

  protected readonly searchQuery = signal<string>('');
  protected readonly selectedClassId = signal<string>('ALL');
  protected readonly selectedSubjectId = signal<string>('ALL');
  protected readonly selectedTerm = signal<GradeTermCode>('T1');
  protected readonly currentPage = signal<number>(0);

  protected readonly pageResult = signal<GradesPageResult | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorProblem = signal<ProblemDetail | null>(null);
  protected readonly generatingBulletins = signal<boolean>(false);

  protected readonly drawerMode = signal<GradesDrawerMode>(null);
  protected readonly selectedReportCard = signal<StudentReportCardRecord | null>(null);
  protected readonly submittingGrades = signal<boolean>(false);
  protected readonly entryErrorProblem = signal<ProblemDetail | null>(null);
  protected readonly entryScores = signal<Readonly<Record<string, string>>>({});
  protected readonly entryTouched = signal<boolean>(false);
  protected readonly entrySubmitAttempted = signal<boolean>(false);
  protected readonly selectedEntryClassId = signal<string>('');

  protected readonly entryForm = this.formBuilder.nonNullable.group({
    classRoomId: ['', [Validators.required]],
    subjectId: ['', [Validators.required]],
    evaluationType: ['HOMEWORK' as PrimaryEvaluationType, [Validators.required]],
    coefficient: ['2', [coefficientValidator]],
    title: ['', [Validators.required, Validators.maxLength(150)]],
    evaluationDate: [formatTodayDakarIso(), [Validators.required]],
  });

  protected readonly reportCards = computed<readonly StudentReportCardRecord[]>(
    () => this.pageResult()?.content ?? []
  );

  protected readonly kpis = computed<GradesKpiSummary | null>(() => this.pageResult()?.kpis ?? null);

  protected readonly classOptions = computed<readonly GradeClassOption[]>(
    () => this.pageResult()?.classes ?? []
  );

  protected readonly subjectOptions = computed<readonly GradeSubjectOption[]>(
    () => this.pageResult()?.subjects ?? []
  );

  protected readonly termOptions = computed<readonly GradeTermOption[]>(
    () => this.pageResult()?.terms ?? DEFAULT_GRADE_TERMS
  );

  protected readonly studentsForEntry = computed<readonly GradeEntryStudentOption[]>(() => {
    const allStudents = this.pageResult()?.studentsForEntry ?? [];
    const selectedClass = this.selectedEntryClassId();
    if (!selectedClass) {
      return allStudents;
    }
    return allStudents.filter((student) => student.classRoomId === selectedClass);
  });

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
      this.selectedClassId() !== 'ALL' ||
      this.selectedSubjectId() !== 'ALL' ||
      this.selectedTerm() !== 'T1'
  );

  protected readonly hasNarrowingFilters = computed<boolean>(
    () =>
      this.searchQuery().trim().length > 0 ||
      this.selectedClassId() !== 'ALL' ||
      this.selectedSubjectId() !== 'ALL'
  );

  protected readonly activeTermOption = computed<GradeTermOption>(() => {
    const code = this.selectedTerm();
    return this.termOptions().find((t) => t.code === code) ?? DEFAULT_GRADE_TERMS[0];
  });

  protected readonly headerSubtitle = computed<string>(() => {
    const termLabel = this.activeTermOption().label;
    if (this.loading() && !this.pageResult()) {
      return `${termLabel} (2026–2027) • Chargement des résultats…`;
    }
    const total = this.totalElements();
    if (total === 0) {
      return this.hasNarrowingFilters()
        ? `${termLabel} (2026–2027) • 0 bulletin trouvé`
        : `${termLabel} (2026–2027) • Aucune note enregistrée`;
    }
    const noun = total > 1 ? 'élèves évalués' : 'élève évalué';
    return `${termLabel} (2026–2027) • ${formatCount(total)} ${noun}`;
  });

  protected readonly overallAverageValue = computed<string>(() => {
    const avg = this.kpis()?.overallAverage ?? null;
    return avg !== null ? `${formatScoreValue(avg)} / 20` : '— / 20';
  });

  protected readonly overallAverageTrendTone = computed<StatCardTrendTone>(() => {
    const avg = this.kpis()?.overallAverage ?? null;
    if (avg === null) {
      return 'neutral';
    }
    if (avg >= 12) {
      return 'success';
    }
    if (avg >= 10) {
      return 'info';
    }
    return 'warning';
  });

  protected readonly passRateValue = computed<string>(() => {
    const rate = this.kpis()?.passRatePercent ?? 0;
    return `${PERCENT_ONE_FORMATTER.format(rate)} %`;
  });

  protected readonly passRateTrendLabel = computed<string>(() => {
    const summary = this.kpis();
    if (!summary) {
      return '0 élève';
    }
    return `${formatCount(summary.passedStudentCount)} / ${formatCount(summary.rankedStudentCount)} admis`;
  });

  protected readonly passRateTrendTone = computed<StatCardTrendTone>(() => {
    const rate = this.kpis()?.passRatePercent ?? 0;
    if (rate >= 75) {
      return 'success';
    }
    if (rate >= 55) {
      return 'warning';
    }
    return 'error';
  });

  protected readonly enteredGradesValue = computed<string>(() =>
    formatCount(this.kpis()?.enteredGradesCount ?? 0)
  );

  protected readonly evaluationsTrendLabel = computed<string>(() => {
    const count = this.kpis()?.evaluationsCount ?? 0;
    return count > 1 ? `${formatCount(count)} évaluations` : `${formatCount(count)} évaluation`;
  });

  protected readonly readyReportCardsValue = computed<string>(() => {
    const summary = this.kpis();
    if (!summary) {
      return '0 / 0';
    }
    return `${formatCount(summary.readyReportCardsCount)} / ${formatCount(summary.totalReportCardsCount)}`;
  });

  protected readonly readyReportCardsTrendLabel = computed<string>(() => {
    const summary = this.kpis();
    if (!summary || summary.totalReportCardsCount === 0) {
      return '0 %';
    }
    const pct = Math.round((summary.readyReportCardsCount / summary.totalReportCardsCount) * 100);
    return `${pct} % prêts`;
  });

  protected readonly readyReportCardsTrendTone = computed<StatCardTrendTone>(() => {
    const summary = this.kpis();
    if (!summary || summary.totalReportCardsCount === 0) {
      return 'neutral';
    }
    const ratio = summary.readyReportCardsCount / summary.totalReportCardsCount;
    if (ratio >= 0.8) {
      return 'success';
    }
    if (ratio >= 0.4) {
      return 'warning';
    }
    return 'info';
  });

  protected readonly paginationRangeLabel = computed<string>(() => {
    const result = this.pageResult();
    if (!result || result.totalElements === 0 || result.content.length === 0) {
      return '0 résultat';
    }
    const start = result.page * result.size + 1;
    const end = start + result.content.length - 1;
    const noun = result.totalElements > 1 ? 'élèves' : 'élève';
    return `Affichage de ${formatCount(start)}–${formatCount(end)} sur ${formatCount(result.totalElements)} ${noun}`;
  });

  protected readonly currentPageDisplay = computed<number>(() => this.currentPage() + 1);

  protected readonly drawerOpen = computed<boolean>(() => this.drawerMode() !== null);

  protected readonly drawerTitle = computed<string>(() => {
    if (this.drawerMode() === 'entry') {
      return 'Saisir des notes';
    }
    const card = this.selectedReportCard();
    return card ? `Bulletin — ${card.periodName}` : 'Aperçu du bulletin';
  });

  protected readonly drawerSubtitle = computed<string>(() => {
    if (this.drawerMode() === 'entry') {
      return `${this.activeTermOption().label} • Notes bornées de 0 à 20`;
    }
    const card = this.selectedReportCard();
    return card ? `${card.studentFullName} • ${card.classRoomName}` : '';
  });

  protected readonly entryScoreErrors = computed<Readonly<Record<string, string>>>(() => {
    const scores = this.entryScores();
    const errors: Record<string, string> = {};
    for (const student of this.studentsForEntry()) {
      const raw = scores[student.studentId] ?? '';
      const parsed = parseBoundedScore(raw);
      if (!parsed.valid) {
        errors[student.studentId] = parsed.error;
      }
    }
    return errors;
  });

  protected readonly entryStats = computed<{
    readonly filledCount: number;
    readonly totalStudents: number;
    readonly liveAverage: number | null;
    readonly hasInvalidScore: boolean;
  }>(() => {
    const students = this.studentsForEntry();
    const scores = this.entryScores();
    const validValues: number[] = [];
    let hasInvalidScore = false;

    for (const student of students) {
      const parsed = parseBoundedScore(scores[student.studentId] ?? '');
      if (!parsed.valid) {
        hasInvalidScore = true;
      } else if (parsed.score !== null) {
        validValues.push(parsed.score);
      }
    }

    const liveAverage =
      validValues.length > 0
        ? roundToTwoDecimals(validValues.reduce((sum, v) => sum + v, 0) / validValues.length)
        : null;

    return {
      filledCount: validValues.length,
      totalStudents: students.length,
      liveAverage,
      hasInvalidScore,
    };
  });

  protected readonly apiFieldErrors = computed<Readonly<Record<string, string>>>(() =>
    extractFieldErrors(this.entryErrorProblem())
  );

  constructor() {
    this.loadTrigger$
      .pipe(
        switchMap((query) => {
          this.loading.set(true);
          this.errorProblem.set(null);
          return this.gradesApi.list(query).pipe(
            catchError((error: unknown) => {
              this.errorProblem.set(parseProblemDetail(error));
              this.loading.set(false);
              return of<GradesPageResult | null>(null);
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
          classRoomId: this.selectedClassId(),
          subjectId: this.selectedSubjectId(),
          term: this.selectedTerm(),
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

    this.entryForm.controls.classRoomId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((classRoomId) => {
        this.selectedEntryClassId.set(classRoomId);
      });

    this.entryForm.controls.subjectId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((subjectId) => {
        const subject = this.subjectOptions().find((item) => item.id === subjectId);
        if (subject) {
          this.entryForm.controls.coefficient.setValue(String(subject.defaultCoefficient));
          this.suggestEvaluationTitle(subject.name, this.entryForm.controls.evaluationType.value);
        }
      });

    this.entryForm.controls.evaluationType.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((evalType) => {
        const subject = this.subjectOptions().find(
          (item) => item.id === this.entryForm.controls.subjectId.value
        );
        if (subject) {
          this.suggestEvaluationTitle(subject.name, evalType);
        }
      });
  }

  ngOnInit(): void {
    const initial = this.readStateFromParamMap(this.route.snapshot.queryParamMap);
    this.applyLocalState(initial);
    this.triggerLoad(initial);
  }

  protected onClassFilterChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    const classRoomId = select?.value?.trim() || 'ALL';
    if (classRoomId === this.selectedClassId()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      classRoomId,
      subjectId: this.selectedSubjectId(),
      term: this.selectedTerm(),
      page: 0,
    });
  }

  protected onSubjectFilterChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    const subjectId = select?.value?.trim() || 'ALL';
    if (subjectId === this.selectedSubjectId()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      classRoomId: this.selectedClassId(),
      subjectId,
      term: this.selectedTerm(),
      page: 0,
    });
  }

  protected selectTerm(term: GradeTermCode): void {
    if (term === this.selectedTerm()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      classRoomId: this.selectedClassId(),
      subjectId: this.selectedSubjectId(),
      term,
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
      classRoomId: this.selectedClassId(),
      subjectId: this.selectedSubjectId(),
      term: this.selectedTerm(),
      page: 0,
    });
  }

  protected resetFilters(): void {
    this.searchControl.setValue('', { emitEvent: false });
    this.applyStateAndSyncUrl({
      search: '',
      classRoomId: 'ALL',
      subjectId: 'ALL',
      term: 'T1',
      page: 0,
    });
  }

  protected goToPreviousPage(): void {
    if (this.isFirstPage() || this.loading()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      classRoomId: this.selectedClassId(),
      subjectId: this.selectedSubjectId(),
      term: this.selectedTerm(),
      page: Math.max(0, this.currentPage() - 1),
    });
  }

  protected goToNextPage(): void {
    if (this.isLastPage() || this.loading()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      classRoomId: this.selectedClassId(),
      subjectId: this.selectedSubjectId(),
      term: this.selectedTerm(),
      page: this.currentPage() + 1,
    });
  }

  protected reload(): void {
    this.triggerLoad({
      search: this.searchQuery(),
      classRoomId: this.selectedClassId(),
      subjectId: this.selectedSubjectId(),
      term: this.selectedTerm(),
      page: this.currentPage(),
    });
  }

  protected generateBulletins(): void {
    if (this.generatingBulletins() || this.loading()) {
      return;
    }
    const activeTerm = this.activeTermOption();
    const targetClassId =
      this.selectedClassId() !== 'ALL'
        ? this.selectedClassId()
        : (this.classOptions()[0]?.id ?? 'ALL');

    this.generatingBulletins.set(true);

    this.gradesApi
      .generateReportCards({
        classRoomId: targetClassId,
        evaluationPeriodId: activeTerm.id,
        termCode: activeTerm.code,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.generatingBulletins.set(false);
          const noun = result.generatedCount > 1 ? 'bulletins calculés' : 'bulletin calculé';
          this.toastService.success(
            `${formatCount(result.generatedCount)} ${noun} pour ${result.classRoomName} (${activeTerm.label}).`,
            'Bulletins générés'
          );
          this.reload();
        },
        error: (error: unknown) => {
          this.generatingBulletins.set(false);
          const problem = parseProblemDetail(error);
          this.toastService.error(problem.detail, problem.title);
        },
      });
  }

  protected openGradeEntryDrawer(): void {
    this.entryErrorProblem.set(null);
    this.entrySubmitAttempted.set(false);
    this.selectedReportCard.set(null);

    const classes = this.classOptions();
    const subjects = this.subjectOptions();

    const defaultClassId =
      this.selectedClassId() !== 'ALL'
        ? this.selectedClassId()
        : this.entryForm.controls.classRoomId.value || classes[0]?.id || '';
    const defaultSubjectId =
      this.selectedSubjectId() !== 'ALL'
        ? this.selectedSubjectId()
        : this.entryForm.controls.subjectId.value || subjects[0]?.id || '';

    if (defaultClassId) {
      this.entryForm.controls.classRoomId.setValue(defaultClassId);
      this.selectedEntryClassId.set(defaultClassId);
    }
    if (defaultSubjectId) {
      this.entryForm.controls.subjectId.setValue(defaultSubjectId);
      const matchedSubject = subjects.find((s) => s.id === defaultSubjectId);
      if (matchedSubject) {
        this.entryForm.controls.coefficient.setValue(String(matchedSubject.defaultCoefficient));
        if (!this.entryForm.controls.title.value.trim()) {
          this.suggestEvaluationTitle(
            matchedSubject.name,
            this.entryForm.controls.evaluationType.value
          );
        }
      }
    }

    this.drawerMode.set('entry');
  }

  protected openBulletinDrawer(card: StudentReportCardRecord): void {
    this.entryErrorProblem.set(null);
    this.selectedReportCard.set(card);
    this.drawerMode.set('bulletin');
  }

  protected closeDrawer(): void {
    if (this.submittingGrades()) {
      return;
    }
    this.drawerMode.set(null);
    this.entryErrorProblem.set(null);
  }

  protected onStudentScoreInput(studentId: string, event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const value = input?.value ?? '';
    this.entryTouched.set(true);
    this.entryScores.update((current) => ({
      ...current,
      [studentId]: value,
    }));
  }

  protected getStudentScoreValue(studentId: string): string {
    return this.entryScores()[studentId] ?? '';
  }

  protected getStudentScoreError(studentId: string): string | null {
    return this.entryScoreErrors()[studentId] ?? null;
  }

  protected submitGrades(): void {
    if (this.submittingGrades()) {
      return;
    }

    this.entrySubmitAttempted.set(true);
    this.entryForm.markAllAsTouched();

    const students = this.studentsForEntry();
    const scoresMap = this.entryScores();
    const entries: GradeEntryScoreInput[] = [];
    let hasInvalidScore = false;

    for (const student of students) {
      const parsed = parseBoundedScore(scoresMap[student.studentId] ?? '');
      if (!parsed.valid) {
        hasInvalidScore = true;
      } else if (parsed.score !== null) {
        entries.push({
          studentId: student.studentId,
          score: parsed.score,
        });
      }
    }

    if (this.entryForm.invalid || hasInvalidScore || entries.length === 0) {
      return;
    }

    const raw = this.entryForm.getRawValue();
    const activeTerm = this.activeTermOption();
    const coefficient = Number.parseInt(raw.coefficient.trim(), 10);

    this.submittingGrades.set(true);
    this.entryErrorProblem.set(null);

    this.gradesApi
      .saveEvaluationGrades({
        evaluationPeriodId: activeTerm.id,
        termCode: activeTerm.code,
        classRoomId: raw.classRoomId.trim(),
        subjectId: raw.subjectId.trim(),
        evaluationType: raw.evaluationType,
        coefficient,
        title: raw.title.trim(),
        evaluationDate: raw.evaluationDate,
        entries,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.submittingGrades.set(false);
          this.drawerMode.set(null);
          this.entryScores.set({});
          this.entryTouched.set(false);
          this.entrySubmitAttempted.set(false);
          this.entryForm.controls.title.setValue('');
          const avgSuffix =
            result.classAverage !== null
              ? ` (moyenne : ${formatScoreValue(result.classAverage)} / 20)`
              : '';
          this.toastService.success(
            `${formatCount(result.savedCount)} notes enregistrées pour « ${result.title} »${avgSuffix}.`,
            'Notes sauvegardées'
          );
          this.reload();
        },
        error: (error: unknown) => {
          // Preserve all user inputs and surface RFC 9457 ProblemDetail
          this.entryErrorProblem.set(parseProblemDetail(error));
          this.submittingGrades.set(false);
        },
      });
  }

  protected printBulletin(): void {
    const card = this.selectedReportCard();
    if (!card) {
      return;
    }
    const win = this.document.defaultView;
    if (win && typeof win.print === 'function') {
      win.print();
    }
    this.toastService.info(
      `Impression lancée pour le bulletin de ${card.studentFullName} (${card.periodName}).`,
      'Impression du bulletin'
    );
  }

  protected downloadBulletin(): void {
    const card = this.selectedReportCard();
    if (!card) {
      return;
    }
    const lines: string[] = [
      `BULLETIN SCOLAIRE — ${card.periodName.toUpperCase()}`,
      `Élève : ${card.studentFullName} (${card.studentMatricule})`,
      `Classe : ${card.classRoomName}`,
      `Moyenne générale : ${this.formatAverage(card.generalAverage)} / ${card.gradingScale}`,
      `Rang : ${this.formatRank(card.rankInClass, card.classSize, card.tiedRank)}`,
      `Appréciation : ${this.mentionLabel(card.mention)} — ${this.councilDecisionLabel(card.councilDecision)}`,
      '',
      'DÉTAIL PAR MATIÈRE :',
      ...card.subjectResults.map(
        (sub) =>
          `- ${sub.subjectName} (Coeff. ${sub.coefficient}) : ${this.formatAverage(sub.averageScore)} / 20 | Classe : ${this.formatAverage(sub.classSubjectAverage)} / 20 — ${sub.teacherAppreciation ?? ''}`
      ),
      '',
      `Professeur principal : ${card.homeroomAppreciation ?? '—'}`,
      `Direction : ${card.directorAppreciation ?? '—'}`,
    ];

    const win = this.document.defaultView;
    if (win && typeof Blob !== 'undefined' && typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
      const blob = new Blob([lines.join('\n')], { type: 'text/plain;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const link = this.document.createElement('a');
      link.href = url;
      link.download = `bulletin-${card.studentMatricule.toLowerCase()}-${card.termCode.toLowerCase()}.txt`;
      link.click();
      URL.revokeObjectURL(url);
    }

    this.toastService.success(
      `Bulletin de ${card.studentFullName} (${card.periodName}) téléchargé.`,
      'Bulletin prêt'
    );
  }

  protected fieldErrorMessage(
    fieldName: 'classRoomId' | 'subjectId' | 'coefficient' | 'title' | 'evaluationDate'
  ): string | null {
    const control = this.entryForm.controls[fieldName];
    if (control.invalid && (control.touched || control.dirty || this.entrySubmitAttempted())) {
      if (control.hasError('required')) {
        const requiredMessages: Record<typeof fieldName, string> = {
          classRoomId: 'Veuillez sélectionner une classe.',
          subjectId: 'Veuillez sélectionner une matière.',
          coefficient: 'Le coefficient est obligatoire.',
          title: "L'intitulé de l'évaluation est obligatoire.",
          evaluationDate: "La date de l'évaluation est obligatoire.",
        };
        return requiredMessages[fieldName];
      }
      if (control.hasError('invalidCoefficient') || control.hasError('coefficientOutOfRange')) {
        return 'Le coefficient doit être un entier compris entre 1 et 20.';
      }
      if (control.hasError('maxlength')) {
        return "L'intitulé ne doit pas dépasser 150 caractères.";
      }
    }

    return this.apiFieldErrors()[fieldName] ?? null;
  }

  protected selectedSubjectScoreForCard(card: StudentReportCardRecord): SubjectGradeResult | null {
    const filter = this.selectedSubjectId();
    if (!filter || filter === 'ALL') {
      return null;
    }
    return (
      card.subjectResults.find(
        (sub) => sub.subjectId === filter || sub.subjectCode === filter || sub.subjectName === filter
      ) ?? null
    );
  }

  protected formatAverage(value: number | null): string {
    return formatScoreValue(value);
  }

  protected formatRank(rank: number | null, classSize: number, tiedRank: boolean): string {
    if (rank === null || rank <= 0) {
      return '—';
    }
    const ordinal = rank === 1 ? '1er' : `${rank}e`;
    const tieSuffix = tiedRank ? ' ex' : '';
    return classSize > 0 ? `${ordinal}${tieSuffix} / ${classSize}` : `${ordinal}${tieSuffix}`;
  }

  protected mentionLabel(mention: BackendMention): string {
    const labels: Readonly<Record<BackendMention, string>> = {
      EXCELLENT: 'Excellent',
      TRES_BIEN: 'Très bien',
      BIEN: 'Bien',
      ASSEZ_BIEN: 'Assez bien',
      PASSABLE: 'Passable',
      INSUFFISANT: 'Insuffisant',
      MEDIOCRE: 'Médiocre',
      NONE: 'Non classé',
    };
    return labels[mention];
  }

  protected mentionTone(mention: BackendMention): BadgeTone {
    switch (mention) {
      case 'EXCELLENT':
      case 'TRES_BIEN':
      case 'BIEN':
        return 'success';
      case 'ASSEZ_BIEN':
        return 'info';
      case 'PASSABLE':
        return 'neutral';
      case 'INSUFFISANT':
        return 'warning';
      case 'MEDIOCRE':
        return 'error';
      case 'NONE':
        return 'neutral';
    }
  }

  protected councilDecisionLabel(decision: BackendCouncilDecision): string {
    const labels: Readonly<Record<BackendCouncilDecision, string>> = {
      FELICITATIONS: 'Félicitations',
      TABLEAU_HONNEUR: "Tableau d'honneur",
      ENCOURAGEMENTS: 'Encouragements',
      PASSAGE: 'Passage',
      AVERTISSEMENT_TRAVAIL: 'Avertissement travail',
      BLAME_TRAVAIL: 'Blâme travail',
      REDOUBLEMENT: 'Redoublement conseillé',
      NONE: 'En attente de conseil',
    };
    return labels[decision];
  }

  protected councilDecisionTone(decision: BackendCouncilDecision): BadgeTone {
    switch (decision) {
      case 'FELICITATIONS':
      case 'TABLEAU_HONNEUR':
        return 'success';
      case 'ENCOURAGEMENTS':
      case 'PASSAGE':
        return 'info';
      case 'AVERTISSEMENT_TRAVAIL':
        return 'warning';
      case 'BLAME_TRAVAIL':
      case 'REDOUBLEMENT':
        return 'error';
      case 'NONE':
        return 'neutral';
    }
  }

  protected reportCardStatusLabel(status: BackendReportCardStatus): string {
    const labels: Readonly<Record<BackendReportCardStatus, string>> = {
      DRAFT: 'Brouillon',
      VALIDATED: 'Validé',
      LOCKED: 'Verrouillé',
    };
    return labels[status];
  }

  protected reportCardStatusTone(status: BackendReportCardStatus): BadgeTone {
    switch (status) {
      case 'LOCKED':
        return 'success';
      case 'VALIDATED':
        return 'info';
      case 'DRAFT':
        return 'warning';
    }
  }

  private suggestEvaluationTitle(subjectName: string, evalType: PrimaryEvaluationType): void {
    const prefix = evalType === 'COMPOSITION' ? 'Composition' : 'Devoir';
    const current = this.entryForm.controls.title.value.trim();
    if (!current || current.startsWith('Devoir de ') || current.startsWith('Composition de ')) {
      this.entryForm.controls.title.setValue(
        `${prefix} de ${subjectName} — ${this.activeTermOption().shortLabel}`
      );
    }
  }

  private readStateFromParamMap(paramMap: ParamMap): UrlFilterState {
    return {
      search: (paramMap.get('q') ?? '').trim(),
      classRoomId: (paramMap.get('class') ?? '').trim() || 'ALL',
      subjectId: (paramMap.get('subject') ?? '').trim() || 'ALL',
      term: parseTermCode(paramMap.get('term')),
      page: parseZeroBasedPage(paramMap.get('page')),
    };
  }

  private isSameFilterState(state: UrlFilterState): boolean {
    return (
      state.search === this.searchQuery() &&
      state.classRoomId === this.selectedClassId() &&
      state.subjectId === this.selectedSubjectId() &&
      state.term === this.selectedTerm() &&
      state.page === this.currentPage()
    );
  }

  private applyLocalState(state: UrlFilterState): void {
    this.searchQuery.set(state.search);
    if (this.searchControl.value !== state.search) {
      this.searchControl.setValue(state.search, { emitEvent: false });
    }
    this.selectedClassId.set(state.classRoomId);
    this.selectedSubjectId.set(state.subjectId);
    this.selectedTerm.set(state.term);
    this.currentPage.set(state.page);
  }

  private applyStateAndSyncUrl(state: UrlFilterState): void {
    this.applyLocalState(state);

    const queryParams: Params = {
      q: state.search || null,
      class: state.classRoomId !== 'ALL' ? state.classRoomId : null,
      subject: state.subjectId !== 'ALL' ? state.subjectId : null,
      term: state.term !== 'T1' ? state.term : null,
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
    const matchedTerm =
      this.termOptions().find((item) => item.code === state.term) ?? DEFAULT_GRADE_TERMS[0];

    this.loadTrigger$.next({
      search: state.search || undefined,
      classRoomId: state.classRoomId !== 'ALL' ? state.classRoomId : undefined,
      subjectId: state.subjectId !== 'ALL' ? state.subjectId : undefined,
      term: state.term,
      evaluationPeriodId: matchedTerm.id,
      page: state.page,
      size: this.pageSize,
      sortBy: 'rankInClass',
      direction: 'ASC',
    });
  }
}
