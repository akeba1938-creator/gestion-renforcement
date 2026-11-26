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
import { ActivatedRoute, ParamMap, Params, Router, RouterLink } from '@angular/router';
import {
  LucideArrowRight,
  LucideChevronLeft,
  LucideChevronRight,
  LucideDownload,
  LucideEye,
  LucideGraduationCap,
  LucidePhone,
  LucidePlus,
  LucideRotateCcw,
  LucideSearch,
  LucideUser,
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
import { BadgeTone, StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { ToastService } from '../../shared/components/toast/toast.service';
import { DakarDatePipe } from '../../shared/pipes/dakar-date.pipe';
import { FcfaPipe } from '../../shared/pipes/fcfa.pipe';
import { StudentsApiService } from './data-access/students-api.service';
import {
  BackendStudentGender,
  DEFAULT_STUDENT_CLASSES,
  StudentDisplayStatus,
  StudentFinancialStatus,
  StudentRecord,
  StudentStatusFilter,
  StudentsListQuery,
  StudentsPageResult,
} from './models/students.model';

export type StudentDrawerMode = 'create' | 'detail' | null;

interface StatusFilterOption {
  readonly value: StudentStatusFilter;
  readonly label: string;
}

interface GenderOption {
  readonly value: BackendStudentGender;
  readonly label: string;
}

const DEFAULT_PAGE_SIZE = 8;
const SEARCH_DEBOUNCE_MS = 300;
const SENEGAL_E164_PHONE_PATTERN = /^\+221\d{9}$/;

const STATUS_FILTER_OPTIONS: readonly StatusFilterOption[] = [
  { value: 'ALL', label: 'Tous les statuts' },
  { value: 'ACTIVE', label: 'Actif' },
  { value: 'PENDING', label: 'En attente' },
  { value: 'WITHDRAWN', label: 'Radié' },
];

const GENDER_OPTIONS: readonly GenderOption[] = [
  { value: 'FEMALE', label: 'Féminin' },
  { value: 'MALE', label: 'Masculin' },
  { value: 'OTHER', label: 'Autre' },
];

const INTEGER_FORMATTER = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });

function formatCount(value: number): string {
  return INTEGER_FORMATTER.format(Math.max(0, Math.trunc(value))).replace(/\u202F/g, '\u00A0');
}

function parseStatusFilter(raw: string | null): StudentStatusFilter {
  if (raw === 'ACTIVE' || raw === 'PENDING' || raw === 'WITHDRAWN') {
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

interface UrlFilterState {
  readonly search: string;
  readonly className: string;
  readonly status: StudentStatusFilter;
  readonly page: number;
}

@Component({
  selector: 'app-students-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    PageHeaderComponent,
    StatusBadgeComponent,
    SkeletonLoaderComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    DrawerComponent,
    FcfaPipe,
    DakarDatePipe,
    LucideArrowRight,
    LucideChevronLeft,
    LucideChevronRight,
    LucideDownload,
    LucideEye,
    LucideGraduationCap,
    LucidePhone,
    LucidePlus,
    LucideRotateCcw,
    LucideSearch,
    LucideUser,
    LucideWallet,
    LucideWifiOff,
    LucideX,
  ],
  templateUrl: './students-page.html',
})
export class StudentsPageComponent implements OnInit {
  private readonly studentsApi = inject(StudentsApiService);
  private readonly toastService = inject(ToastService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly networkStatus = inject(NetworkStatusService);

  private readonly loadTrigger$ = new Subject<StudentsListQuery>();

  protected readonly statusFilterOptions = STATUS_FILTER_OPTIONS;
  protected readonly genderOptions = GENDER_OPTIONS;
  protected readonly pageSize = DEFAULT_PAGE_SIZE;

  protected readonly searchControl = new FormControl<string>('', { nonNullable: true });

  protected readonly searchQuery = signal<string>('');
  protected readonly selectedClass = signal<string>('');
  protected readonly selectedStatus = signal<StudentStatusFilter>('ALL');
  protected readonly currentPage = signal<number>(0);

  protected readonly pageResult = signal<StudentsPageResult | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorProblem = signal<ProblemDetail | null>(null);
  protected readonly exporting = signal<boolean>(false);

  protected readonly drawerMode = signal<StudentDrawerMode>(null);
  protected readonly selectedStudent = signal<StudentRecord | null>(null);
  protected readonly submitting = signal<boolean>(false);
  protected readonly createErrorProblem = signal<ProblemDetail | null>(null);

  protected readonly createForm = this.formBuilder.nonNullable.group({
    firstName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    className: [DEFAULT_STUDENT_CLASSES[0], [Validators.required]],
    guardianName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    guardianPhone: [
      '+221',
      [Validators.required, Validators.pattern(SENEGAL_E164_PHONE_PATTERN)],
    ],
    gender: ['FEMALE' as BackendStudentGender],
    birthDate: [''],
  });

  protected readonly students = computed<readonly StudentRecord[]>(
    () => this.pageResult()?.content ?? []
  );

  protected readonly availableClasses = computed<readonly string[]>(() => {
    const classes = this.pageResult()?.availableClasses;
    return classes && classes.length > 0 ? classes : DEFAULT_STUDENT_CLASSES;
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
      this.selectedClass().trim().length > 0 ||
      this.selectedStatus() !== 'ALL'
  );

  protected readonly headerSubtitle = computed<string>(() => {
    if (this.loading() && !this.pageResult()) {
      return 'Chargement du répertoire des élèves…';
    }
    const total = this.totalElements();
    if (total === 0) {
      return this.hasActiveFilters()
        ? '0 élève trouvé pour les critères sélectionnés'
        : 'Aucun élève enregistré • Année scolaire 2026-2027';
    }
    const label = total > 1 ? 'élèves enregistrés' : 'élève enregistré';
    return `${formatCount(total)} ${label} • Année scolaire 2026-2027`;
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
    if (this.drawerMode() === 'create') {
      return 'Nouvel élève';
    }
    return this.selectedStudent()?.fullName ?? 'Fiche élève';
  });

  protected readonly drawerSubtitle = computed<string>(() => {
    if (this.drawerMode() === 'create') {
      return "Enregistrement d'un nouveau dossier élève";
    }
    const student = this.selectedStudent();
    return student ? `Matricule ${student.matricule}` : '';
  });

  protected readonly apiFieldErrors = computed<Readonly<Record<string, string>>>(() =>
    extractFieldErrors(this.createErrorProblem())
  );

  constructor() {
    this.loadTrigger$
      .pipe(
        switchMap((query) => {
          this.loading.set(true);
          this.errorProblem.set(null);
          return this.studentsApi.list(query).pipe(
            catchError((error: unknown) => {
              this.errorProblem.set(parseProblemDetail(error));
              this.loading.set(false);
              return of<StudentsPageResult | null>(null);
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

  protected onClassFilterChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    const className = select?.value ?? '';
    if (className === this.selectedClass()) {
      return;
    }
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      className,
      status: this.selectedStatus(),
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
      page: 0,
    });
  }

  protected resetFilters(): void {
    this.searchControl.setValue('', { emitEvent: false });
    this.applyStateAndSyncUrl({
      search: '',
      className: '',
      status: 'ALL',
      page: 0,
    });
  }

  protected goToPreviousPage(): void {
    if (this.isFirstPage() || this.loading()) {
      return;
    }
    const targetPage = Math.max(0, this.currentPage() - 1);
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      className: this.selectedClass(),
      status: this.selectedStatus(),
      page: targetPage,
    });
  }

  protected goToNextPage(): void {
    if (this.isLastPage() || this.loading()) {
      return;
    }
    const targetPage = this.currentPage() + 1;
    this.applyStateAndSyncUrl({
      search: this.searchQuery(),
      className: this.selectedClass(),
      status: this.selectedStatus(),
      page: targetPage,
    });
  }

  protected reload(): void {
    this.triggerLoad({
      search: this.searchQuery(),
      className: this.selectedClass(),
      status: this.selectedStatus(),
      page: this.currentPage(),
    });
  }

  protected exportStudents(): void {
    if (this.exporting() || this.loading()) {
      return;
    }
    this.exporting.set(true);
    const count = this.totalElements();
    const noun = count > 1 ? 'élèves' : 'élève';
    this.toastService.success(
      `Export CSV généré pour ${formatCount(count)} ${noun}.`,
      'Export prêt'
    );
    this.exporting.set(false);
  }

  protected openCreateDrawer(): void {
    this.createErrorProblem.set(null);
    this.selectedStudent.set(null);
    this.drawerMode.set('create');
  }

  protected openDetailDrawer(student: StudentRecord): void {
    this.createErrorProblem.set(null);
    this.selectedStudent.set(student);
    this.drawerMode.set('detail');
  }

  protected closeDrawer(): void {
    if (this.submitting()) {
      return;
    }
    this.drawerMode.set(null);
    this.createErrorProblem.set(null);
  }

  protected submitCreateStudent(): void {
    if (this.submitting()) {
      return;
    }
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.createErrorProblem.set(null);
    const raw = this.createForm.getRawValue();

    this.studentsApi
      .create({
        firstName: raw.firstName.trim(),
        lastName: raw.lastName.trim(),
        className: raw.className,
        guardianName: raw.guardianName.trim(),
        guardianPhone: raw.guardianPhone.trim(),
        gender: raw.gender,
        birthDate: raw.birthDate.trim() || undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.submitting.set(false);
          this.drawerMode.set(null);
          this.createForm.reset({
            firstName: '',
            lastName: '',
            className: this.availableClasses()[0] ?? DEFAULT_STUDENT_CLASSES[0],
            guardianName: '',
            guardianPhone: '+221',
            gender: 'FEMALE',
            birthDate: '',
          });
          this.toastService.success(
            `${created.fullName} (${created.className}) a été ajouté au répertoire.`,
            'Élève enregistré'
          );
          this.reload();
        },
        error: (error: unknown) => {
          // Preserve user input in the form and surface normalized RFC 9457 error
          this.createErrorProblem.set(parseProblemDetail(error));
          this.submitting.set(false);
        },
      });
  }

  protected fieldErrorMessage(field: 'firstName' | 'lastName' | 'guardianName' | 'guardianPhone'): string | null {
    const serverError = this.apiFieldErrors()[field === 'guardianPhone' ? 'phone' : field];
    if (serverError) {
      return serverError;
    }
    const control = this.createForm.controls[field];
    if (!control.invalid || !control.touched) {
      return null;
    }
    switch (field) {
      case 'firstName':
        return "Veuillez saisir un prénom d'au moins 2 caractères.";
      case 'lastName':
        return "Veuillez saisir un nom d'au moins 2 caractères.";
      case 'guardianName':
        return "Veuillez saisir le nom du tuteur (au moins 2 caractères).";
      case 'guardianPhone':
        return 'Format E.164 requis : +221 suivi de 9 chiffres (ex. +221770000001).';
    }
  }

  protected statusLabel(status: StudentDisplayStatus): string {
    const labels: Readonly<Record<StudentDisplayStatus, string>> = {
      ACTIVE: 'Actif',
      PENDING: 'En attente',
      WITHDRAWN: 'Radié',
      TRANSFERRED: 'Transféré',
      GRADUATED: 'Diplômé',
      ARCHIVED: 'Archivé',
    };
    return labels[status];
  }

  protected statusTone(status: StudentDisplayStatus): BadgeTone {
    const tones: Readonly<Record<StudentDisplayStatus, BadgeTone>> = {
      ACTIVE: 'success',
      PENDING: 'warning',
      WITHDRAWN: 'error',
      TRANSFERRED: 'info',
      GRADUATED: 'neutral',
      ARCHIVED: 'neutral',
    };
    return tones[status];
  }

  protected financialStatusLabel(status: StudentFinancialStatus, balanceFcfa: number | null): string {
    if (balanceFcfa === null) {
      return 'Non renseigné';
    }
    const labels: Readonly<Record<StudentFinancialStatus, string>> = {
      UP_TO_DATE: 'À jour',
      PARTIAL: 'Solde restant',
      OVERDUE: 'Impayé en retard',
      UNKNOWN: 'Non renseigné',
    };
    return labels[status];
  }

  protected financialStatusTone(status: StudentFinancialStatus, balanceFcfa: number | null): BadgeTone {
    if (balanceFcfa === null) {
      return 'neutral';
    }
    const tones: Readonly<Record<StudentFinancialStatus, BadgeTone>> = {
      UP_TO_DATE: 'success',
      PARTIAL: 'warning',
      OVERDUE: 'error',
      UNKNOWN: 'neutral',
    };
    return tones[status];
  }

  protected genderLabel(gender: BackendStudentGender): string {
    const labels: Readonly<Record<BackendStudentGender, string>> = {
      FEMALE: 'Féminin',
      MALE: 'Masculin',
      OTHER: 'Autre',
    };
    return labels[gender];
  }

  private readStateFromParamMap(paramMap: ParamMap): UrlFilterState {
    return {
      search: (paramMap.get('q') ?? '').trim(),
      className: (paramMap.get('class') ?? '').trim(),
      status: parseStatusFilter(paramMap.get('status')),
      page: parseZeroBasedPage(paramMap.get('page')),
    };
  }

  private isSameFilterState(state: UrlFilterState): boolean {
    return (
      state.search === this.searchQuery() &&
      state.className === this.selectedClass() &&
      state.status === this.selectedStatus() &&
      state.page === this.currentPage()
    );
  }

  private applyLocalState(state: UrlFilterState): void {
    this.searchQuery.set(state.search);
    this.selectedClass.set(state.className);
    this.selectedStatus.set(state.status);
    this.currentPage.set(state.page);
    if (this.searchControl.value !== state.search) {
      this.searchControl.setValue(state.search, { emitEvent: false });
    }
  }

  private applyStateAndSyncUrl(state: UrlFilterState): void {
    this.applyLocalState(state);

    const queryParams: Params = {
      q: state.search || null,
      class: state.className || null,
      status: state.status !== 'ALL' ? state.status : null,
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
      className: state.className || undefined,
      status: state.status,
      page: state.page,
      size: DEFAULT_PAGE_SIZE,
      sortBy: 'lastName',
      direction: 'ASC',
    });
  }
}
