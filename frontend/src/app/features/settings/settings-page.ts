import { Clipboard } from '@angular/cdk/clipboard';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  effect,
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
import { ActivatedRoute, Router } from '@angular/router';
import {
  LucideBell,
  LucideBuilding,
  LucideCalendar,
  LucideCheck,
  LucideClock,
  LucideCopy,
  LucideGlobe,
  LucideKeyRound,
  LucideLock,
  LucideMail,
  LucideMapPin,
  LucideMoon,
  LucidePhone,
  LucideRefreshCw,
  LucideSave,
  LucideShieldCheck,
  LucideSlidersHorizontal,
  LucideSun,
  LucideWifiOff,
} from '@lucide/angular';
import { catchError, finalize, of } from 'rxjs';
import {
  ProblemDetail,
  extractFieldErrors,
  parseProblemDetail,
} from '../../core/models/problem-detail.model';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { ThemeMode, ThemeService } from '../../core/services/theme.service';
import { DrawerComponent } from '../../shared/components/drawer/drawer';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state';
import { BreadcrumbItem, PageHeaderComponent } from '../../shared/components/page-header/page-header';
import { SkeletonLoaderComponent } from '../../shared/components/skeleton-loader/skeleton-loader';
import { BadgeTone, StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { ToastService } from '../../shared/components/toast/toast.service';
import { DakarDatePipe } from '../../shared/pipes/dakar-date.pipe';
import { SettingsApiService } from './data-access/settings-api.service';
import {
  AcademicYearItem,
  AcademicYearStatus,
  AcademicYearStatusFilter,
  CommunicationChannel,
  DEFAULT_NOTIFICATION_PREFERENCES,
  E164_PHONE_PATTERN,
  EMAIL_PATTERN,
  EvaluationPeriodItem,
  EvaluationPeriodStatus,
  GradingPeriodSystem,
  MfaEnrollmentPayload,
  NotificationPreferences,
  SETTINGS_TABS,
  SchoolSettings,
  SecurityPolicySnapshot,
  SettingsSnapshot,
  SettingsTab,
  TOTP_CODE_PATTERN,
  formatAcademicYearStatusLabel,
  formatEvaluationPeriodStatusLabel,
  formatPeriodSystemLabel,
  isValidSettingsTab,
  resolveAcademicYearBadgeTone,
} from './models/settings.model';

interface SettingsTabItem {
  readonly id: SettingsTab;
  readonly label: string;
}

interface StatusFilterOption {
  readonly value: AcademicYearStatusFilter;
  readonly label: string;
}

interface SchoolFormControls {
  readonly name: FormControl<string>;
  readonly address: FormControl<string>;
  readonly city: FormControl<string>;
  readonly region: FormControl<string>;
  readonly phone: FormControl<string>;
  readonly email: FormControl<string>;
}

interface AcademicFormControls {
  readonly academicYearId: FormControl<string>;
  readonly activePeriodId: FormControl<string>;
  readonly startDate: FormControl<string>;
  readonly endDate: FormControl<string>;
  readonly periodSystem: FormControl<GradingPeriodSystem>;
  readonly setAsCurrent: FormControl<boolean>;
}

interface PreferencesFormControls {
  readonly receiptNotificationsEnabled: FormControl<boolean>;
  readonly absenceNotificationsEnabled: FormControl<boolean>;
  readonly inAppEnabled: FormControl<boolean>;
  readonly emailEnabled: FormControl<boolean>;
  readonly smsEnabled: FormControl<boolean>;
  readonly whatsappEnabled: FormControl<boolean>;
  readonly preferredChannel: FormControl<CommunicationChannel>;
  readonly quietHoursStart: FormControl<string>;
  readonly quietHoursEnd: FormControl<string>;
}

interface MfaVerifyFormControls {
  readonly code: FormControl<string>;
}

function optionalE164Validator(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value?.trim() ?? '';
  if (!value) {
    return null;
  }
  return E164_PHONE_PATTERN.test(value) ? null : { e164: true };
}

function optionalEmailValidator(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value?.trim() ?? '';
  if (!value) {
    return null;
  }
  return EMAIL_PATTERN.test(value) ? null : { emailFormat: true };
}

function academicDateRangeValidator(group: AbstractControl): ValidationErrors | null {
  const start = String(group.get('startDate')?.value ?? '').trim();
  const end = String(group.get('endDate')?.value ?? '').trim();
  if (!start || !end) {
    return null;
  }
  return end > start ? null : { dateOrder: true };
}

@Component({
  selector: 'app-settings-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    PageHeaderComponent,
    StatusBadgeComponent,
    SkeletonLoaderComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    DrawerComponent,
    DakarDatePipe,
    LucideBuilding,
    LucideCalendar,
    LucideShieldCheck,
    LucideSlidersHorizontal,
    LucideSave,
    LucideKeyRound,
    LucideSun,
    LucideMoon,
    LucideBell,
    LucideWifiOff,
    LucideCopy,
    LucideCheck,
    LucideLock,
    LucideClock,
    LucideGlobe,
    LucidePhone,
    LucideMail,
    LucideMapPin,
    LucideRefreshCw,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './settings-page.html',
})
export class SettingsPageComponent implements OnInit {
  private readonly settingsApi = inject(SettingsApiService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toastService = inject(ToastService);
  private readonly clipboard = inject(Clipboard);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly themeService = inject(ThemeService);
  protected readonly networkStatus = inject(NetworkStatusService);

  protected readonly breadcrumbs: readonly BreadcrumbItem[] = [
    { label: 'Tableau de bord', route: '/dashboard' },
    { label: 'Paramètres' },
  ];

  protected readonly tabs: readonly SettingsTabItem[] = [
    { id: 'school', label: 'Établissement' },
    { id: 'academic', label: 'Année scolaire' },
    { id: 'security', label: 'Sécurité & MFA' },
    { id: 'preferences', label: 'Préférences' },
  ];

  protected readonly yearStatusFilters: readonly StatusFilterOption[] = [
    { value: 'ALL', label: 'Toutes' },
    { value: 'ACTIVE', label: 'Active' },
    { value: 'CLOSED', label: 'Clôturée' },
    { value: 'ARCHIVED', label: 'Archivée' },
  ];

  protected readonly activeTab = signal<SettingsTab>('school');
  protected readonly loading = signal<boolean>(true);
  protected readonly loadError = signal<ProblemDetail | null>(null);
  protected readonly saving = signal<boolean>(false);
  protected readonly saveError = signal<ProblemDetail | null>(null);
  protected readonly serverFieldErrors = signal<Readonly<Record<string, string>>>({});
  protected readonly formVersion = signal<number>(0);

  protected readonly school = signal<SchoolSettings | null>(null);
  protected readonly academicYears = signal<readonly AcademicYearItem[]>([]);
  protected readonly periodsByYearId = signal<
    Readonly<Record<string, readonly EvaluationPeriodItem[]>>
  >({});
  protected readonly periodsLoading = signal<boolean>(false);
  protected readonly security = signal<SecurityPolicySnapshot | null>(null);
  protected readonly preferences = signal<NotificationPreferences>({
    ...DEFAULT_NOTIFICATION_PREFERENCES,
  });

  protected readonly yearStatusFilter = signal<AcademicYearStatusFilter>('ALL');

  // MFA Drawer state
  protected readonly mfaDrawerOpen = signal<boolean>(false);
  protected readonly mfaEnrolling = signal<boolean>(false);
  protected readonly mfaVerifying = signal<boolean>(false);
  protected readonly mfaEnrollment = signal<MfaEnrollmentPayload | null>(null);
  protected readonly mfaError = signal<ProblemDetail | null>(null);
  protected readonly mfaFieldErrors = signal<Readonly<Record<string, string>>>({});
  protected readonly secretCopied = signal<boolean>(false);
  private secretCopyTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly schoolForm = this.fb.nonNullable.group<SchoolFormControls>({
    name: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(150)]),
    address: this.fb.nonNullable.control('', [Validators.maxLength(500)]),
    city: this.fb.nonNullable.control('', [Validators.maxLength(100)]),
    region: this.fb.nonNullable.control('', [Validators.maxLength(100)]),
    phone: this.fb.nonNullable.control('', [Validators.maxLength(20), optionalE164Validator]),
    email: this.fb.nonNullable.control('', [Validators.maxLength(255), optionalEmailValidator]),
  });

  protected readonly academicForm = this.fb.nonNullable.group<AcademicFormControls>(
    {
      academicYearId: this.fb.nonNullable.control('', [Validators.required]),
      activePeriodId: this.fb.nonNullable.control(''),
      startDate: this.fb.nonNullable.control('', [Validators.required]),
      endDate: this.fb.nonNullable.control('', [Validators.required]),
      periodSystem: this.fb.nonNullable.control<GradingPeriodSystem>('TRIMESTER'),
      setAsCurrent: this.fb.nonNullable.control(true),
    },
    { validators: [academicDateRangeValidator] }
  );

  protected readonly preferencesForm = this.fb.nonNullable.group<PreferencesFormControls>({
    receiptNotificationsEnabled: this.fb.nonNullable.control(true),
    absenceNotificationsEnabled: this.fb.nonNullable.control(true),
    inAppEnabled: this.fb.nonNullable.control(true),
    emailEnabled: this.fb.nonNullable.control(true),
    smsEnabled: this.fb.nonNullable.control(true),
    whatsappEnabled: this.fb.nonNullable.control(false),
    preferredChannel: this.fb.nonNullable.control<CommunicationChannel>('IN_APP'),
    quietHoursStart: this.fb.nonNullable.control('21:00'),
    quietHoursEnd: this.fb.nonNullable.control('07:00'),
  });

  protected readonly mfaVerifyForm = this.fb.nonNullable.group<MfaVerifyFormControls>({
    code: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.pattern(TOTP_CODE_PATTERN),
    ]),
  });

  protected readonly currentAcademicYear = computed<AcademicYearItem | null>(() => {
    const years = this.academicYears();
    return (
      years.find((item) => item.current) ??
      years.find((item) => item.status === 'ACTIVE') ??
      years[0] ??
      null
    );
  });

  protected readonly headerSubtitle = computed<string>(() => {
    const currentSchool = this.school();
    const activeYear = this.currentAcademicYear();
    if (currentSchool && activeYear) {
      return `${currentSchool.name} · ${activeYear.label}`;
    }
    if (currentSchool) {
      return `${currentSchool.name} · Configuration générale`;
    }
    if (activeYear) {
      return `Configuration générale · ${activeYear.label}`;
    }
    return "Configuration de l'établissement, année scolaire et sécurité";
  });

  protected readonly hasAnyData = computed<boolean>(
    () => this.school() !== null || this.academicYears().length > 0 || this.security() !== null
  );

  protected readonly selectedAcademicYear = computed<AcademicYearItem | null>(() => {
    this.formVersion();
    const selectedId = this.academicForm.controls.academicYearId.value;
    return this.academicYears().find((item) => item.id === selectedId) ?? this.currentAcademicYear();
  });

  protected readonly selectedYearPeriods = computed<readonly EvaluationPeriodItem[]>(() => {
    const year = this.selectedAcademicYear();
    if (!year) {
      return [];
    }
    return this.periodsByYearId()[year.id] ?? [];
  });

  protected readonly filteredAcademicYears = computed<readonly AcademicYearItem[]>(() => {
    const filter = this.yearStatusFilter();
    const years = this.academicYears();
    if (filter === 'ALL') {
      return years;
    }
    if (filter === 'ACTIVE') {
      return years.filter((year) => year.current || year.status === 'ACTIVE');
    }
    return years.filter((year) => year.status === filter);
  });

  protected readonly hasUnsavedChanges = computed<boolean>(() => {
    this.formVersion();
    return this.schoolForm.dirty || this.academicForm.dirty || this.preferencesForm.dirty;
  });

  protected readonly isSaveDisabled = computed<boolean>(() => {
    this.formVersion();
    if (this.loading() || this.saving() || !this.hasAnyData() || !this.networkStatus.isOnline()) {
      return true;
    }
    if (this.activeTab() === 'security' && !this.hasUnsavedChanges()) {
      return true;
    }
    if (this.activeTab() === 'academic' && this.academicYears().length === 0) {
      return true;
    }
    return false;
  });

  protected readonly formattedStartPreview = computed<string>(() => {
    this.formVersion();
    const raw = this.academicForm.controls.startDate.value.trim();
    return raw ? `${raw}T12:00:00Z` : '';
  });

  protected readonly formattedEndPreview = computed<string>(() => {
    this.formVersion();
    const raw = this.academicForm.controls.endDate.value.trim();
    return raw ? `${raw}T12:00:00Z` : '';
  });

  constructor() {
    this.destroyRef.onDestroy(() => {
      if (this.secretCopyTimer !== null) {
        clearTimeout(this.secretCopyTimer);
      }
    });

    this.schoolForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.formVersion.update((v) => v + 1);
    });
    this.academicForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.formVersion.update((v) => v + 1);
    });
    this.preferencesForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.formVersion.update((v) => v + 1);
    });

    effect(() => {
      const disablePeriods = this.periodsLoading() || this.selectedYearPeriods().length === 0;
      const periodControl = this.academicForm.controls.activePeriodId;
      if (disablePeriods && periodControl.enabled) {
        periodControl.disable({ emitEvent: false });
      } else if (!disablePeriods && periodControl.disabled) {
        periodControl.enable({ emitEvent: false });
      }
    });
  }

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((paramMap) => {
        const rawTab = paramMap.get('tab');
        const nextTab: SettingsTab = isValidSettingsTab(rawTab) ? rawTab : 'school';
        if (this.activeTab() !== nextTab) {
          this.activeTab.set(nextTab);
          this.saveError.set(null);
          this.serverFieldErrors.set({});
        }
      });

    this.loadSettings();
  }

  protected selectTab(tab: SettingsTab): void {
    if (this.activeTab() === tab) {
      return;
    }
    this.activeTab.set(tab);
    this.saveError.set(null);
    this.serverFieldErrors.set({});
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected onTabKeydown(event: KeyboardEvent, currentIndex: number): void {
    let nextIndex: number | null = null;
    if (event.key === 'ArrowRight') {
      nextIndex = (currentIndex + 1) % SETTINGS_TABS.length;
    } else if (event.key === 'ArrowLeft') {
      nextIndex = (currentIndex - 1 + SETTINGS_TABS.length) % SETTINGS_TABS.length;
    } else if (event.key === 'Home') {
      nextIndex = 0;
    } else if (event.key === 'End') {
      nextIndex = SETTINGS_TABS.length - 1;
    }

    if (nextIndex !== null) {
      event.preventDefault();
      const targetTab = SETTINGS_TABS[nextIndex];
      this.selectTab(targetTab);
      const tabEl = document.getElementById(`settings-tab-${targetTab}`);
      tabEl?.focus();
    }
  }

  protected reloadSettings(): void {
    this.loadSettings();
  }

  protected saveActiveTab(): void {
    if (this.saving() || this.loading()) {
      return;
    }
    if (!this.networkStatus.isOnline()) {
      this.toastService.warning(
        'Vous êtes hors-ligne. Reconnectez-vous pour enregistrer les modifications.',
        'Enregistrement impossible'
      );
      return;
    }

    const tab = this.activeTab();
    if (tab === 'school') {
      this.submitSchoolForm();
      return;
    }
    if (tab === 'academic') {
      this.submitAcademicForm();
      return;
    }
    if (tab === 'preferences') {
      this.submitPreferencesForm();
      return;
    }

    // Security tab: if user has pending edits on another tab, save them gracefully.
    if (this.schoolForm.dirty) {
      this.submitSchoolForm();
    } else if (this.academicForm.dirty) {
      this.submitAcademicForm();
    } else if (this.preferencesForm.dirty) {
      this.submitPreferencesForm();
    }
  }

  protected onAcademicYearSelectionChange(yearId: string): void {
    const selected = this.academicYears().find((year) => year.id === yearId);
    if (!selected) {
      return;
    }

    const existingPeriods = this.periodsByYearId()[selected.id];
    const openPeriod =
      existingPeriods?.find((period) => period.status === 'OPEN') ?? existingPeriods?.[0] ?? null;

    this.academicForm.patchValue({
      academicYearId: selected.id,
      activePeriodId: openPeriod?.id ?? '',
      startDate: selected.startDate,
      endDate: selected.endDate,
      periodSystem: selected.periodSystem,
      setAsCurrent: selected.current,
    });
    this.academicForm.markAsDirty();
    this.formVersion.update((v) => v + 1);

    if (!existingPeriods) {
      this.fetchPeriodsForYear(selected.id);
    }
  }

  protected selectYearFromList(year: AcademicYearItem): void {
    this.onAcademicYearSelectionChange(year.id);
    if (!year.current) {
      this.academicForm.controls.setAsCurrent.setValue(true);
      this.academicForm.markAsDirty();
    }
  }

  protected setYearStatusFilter(filter: AcademicYearStatusFilter): void {
    this.yearStatusFilter.set(filter);
  }

  protected resetYearStatusFilter(): void {
    this.yearStatusFilter.set('ALL');
  }

  protected setTheme(mode: ThemeMode): void {
    this.themeService.setTheme(mode);
  }

  protected openMfaDrawer(): void {
    this.mfaError.set(null);
    this.mfaFieldErrors.set({});
    this.mfaVerifyForm.reset({ code: '' });
    this.mfaEnrollment.set(null);
    this.secretCopied.set(false);
    this.mfaDrawerOpen.set(true);
    this.startMfaEnrollment();
  }

  protected closeMfaDrawer(): void {
    if (this.mfaVerifying()) {
      return;
    }
    this.mfaDrawerOpen.set(false);
    this.mfaError.set(null);
    this.mfaFieldErrors.set({});
  }

  protected startMfaEnrollment(): void {
    if (this.mfaEnrolling()) {
      return;
    }
    this.mfaEnrolling.set(true);
    this.mfaError.set(null);

    this.settingsApi
      .enrollMfa()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.mfaEnrolling.set(false))
      )
      .subscribe({
        next: (payload) => {
          this.mfaEnrollment.set(payload);
        },
        error: (error: unknown) => {
          this.mfaError.set(parseProblemDetail(error));
        },
      });
  }

  protected copyMfaSecret(secret: string): void {
    if (this.clipboard.copy(secret)) {
      this.secretCopied.set(true);
      if (this.secretCopyTimer !== null) {
        clearTimeout(this.secretCopyTimer);
      }
      this.secretCopyTimer = setTimeout(() => {
        this.secretCopied.set(false);
        this.secretCopyTimer = null;
      }, 2500);
    }
  }

  protected submitMfaVerification(): void {
    if (this.mfaVerifying() || this.mfaEnrolling()) {
      return;
    }
    this.mfaError.set(null);
    this.mfaFieldErrors.set({});
    this.mfaVerifyForm.markAllAsTouched();

    if (this.mfaVerifyForm.invalid) {
      return;
    }

    const code = this.mfaVerifyForm.controls.code.value.trim();
    this.mfaVerifying.set(true);

    this.settingsApi
      .verifyMfa(code)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.mfaVerifying.set(false))
      )
      .subscribe({
        next: (result) => {
          const currentSec = this.security();
          if (currentSec) {
            this.security.set({
              ...currentSec,
              mfaEnabled: result.mfaEnabled,
            });
          }
          this.mfaDrawerOpen.set(false);
          this.mfaEnrollment.set(null);
          this.mfaVerifyForm.reset({ code: '' });
          this.toastService.success(
            "L'authentification à deux facteurs (MFA) est désormais active sur votre compte.",
            'MFA configurée'
          );
        },
        error: (error: unknown) => {
          const problem = parseProblemDetail(error);
          this.mfaError.set(problem);
          this.mfaFieldErrors.set(extractFieldErrors(problem));
        },
      });
  }

  protected getSchoolFieldError(field: keyof SchoolFormControls): string | null {
    const serverError = this.serverFieldErrors()[field];
    if (serverError) {
      return serverError;
    }
    const control = this.schoolForm.controls[field];
    if (!control.touched && !control.dirty) {
      return null;
    }
    if (control.hasError('required')) {
      return "Le nom de l'établissement est obligatoire.";
    }
    if (control.hasError('e164')) {
      return 'Format international E.164 requis (ex. +221338214590 ou +221770000001).';
    }
    if (control.hasError('emailFormat')) {
      return "Veuillez saisir une adresse e-mail officielle valide.";
    }
    if (control.hasError('maxlength')) {
      return 'La valeur dépasse la longueur maximale autorisée.';
    }
    return null;
  }

  protected getAcademicFieldError(field: 'startDate' | 'endDate'): string | null {
    const serverError = this.serverFieldErrors()[field];
    if (serverError) {
      return serverError;
    }
    const control = this.academicForm.controls[field];
    if (control.touched && control.hasError('required')) {
      return field === 'startDate'
        ? 'La date de début est obligatoire.'
        : 'La date de fin est obligatoire.';
    }
    if (field === 'endDate' && this.academicForm.hasError('dateOrder')) {
      return 'La date de fin doit être postérieure à la date de début.';
    }
    return null;
  }

  protected getMfaCodeError(): string | null {
    const serverError = this.mfaFieldErrors()['code'];
    if (serverError) {
      return serverError;
    }
    const control = this.mfaVerifyForm.controls.code;
    if (!control.touched) {
      return null;
    }
    if (control.hasError('required')) {
      return 'Veuillez saisir le code à 6 chiffres généré par votre application.';
    }
    if (control.hasError('pattern')) {
      return 'Le code TOTP doit contenir exactement 6 chiffres.';
    }
    return null;
  }

  protected yearStatusLabel(status: AcademicYearStatus, isCurrent: boolean): string {
    return formatAcademicYearStatusLabel(status, isCurrent);
  }

  protected yearBadgeTone(status: AcademicYearStatus, isCurrent: boolean): BadgeTone {
    return resolveAcademicYearBadgeTone(status, isCurrent);
  }

  protected periodSystemLabel(system: GradingPeriodSystem): string {
    return formatPeriodSystemLabel(system);
  }

  protected periodStatusLabel(status: EvaluationPeriodStatus): string {
    return formatEvaluationPeriodStatusLabel(status);
  }

  protected formatIsoDateForPipe(isoDate: string): string {
    return isoDate ? `${isoDate}T12:00:00Z` : '';
  }

  private loadSettings(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.saveError.set(null);
    this.serverFieldErrors.set({});

    this.settingsApi
      .loadSettings()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (snapshot) => {
          this.applySnapshot(snapshot);
        },
        error: (error: unknown) => {
          this.loadError.set(parseProblemDetail(error));
        },
      });
  }

  private applySnapshot(snapshot: SettingsSnapshot): void {
    this.school.set(snapshot.school);
    this.academicYears.set(snapshot.academicYears);
    this.periodsByYearId.set(snapshot.periodsByYearId);
    this.security.set(snapshot.security);
    this.preferences.set(snapshot.preferences);

    if (snapshot.school) {
      this.schoolForm.reset(
        {
          name: snapshot.school.name,
          address: snapshot.school.address,
          city: snapshot.school.city,
          region: snapshot.school.region,
          phone: snapshot.school.phone,
          email: snapshot.school.email,
        },
        { emitEvent: false }
      );
    }

    const activeYear =
      snapshot.academicYears.find((year) => year.current) ??
      snapshot.academicYears.find((year) => year.status === 'ACTIVE') ??
      snapshot.academicYears[0] ??
      null;

    if (activeYear) {
      const periods = snapshot.periodsByYearId[activeYear.id] ?? [];
      const openPeriod = periods.find((p) => p.status === 'OPEN') ?? periods[0] ?? null;
      this.academicForm.reset(
        {
          academicYearId: activeYear.id,
          activePeriodId: openPeriod?.id ?? '',
          startDate: activeYear.startDate,
          endDate: activeYear.endDate,
          periodSystem: activeYear.periodSystem,
          setAsCurrent: activeYear.current,
        },
        { emitEvent: false }
      );
    }

    this.preferencesForm.reset(
      {
        receiptNotificationsEnabled: snapshot.preferences.receiptNotificationsEnabled,
        absenceNotificationsEnabled: snapshot.preferences.absenceNotificationsEnabled,
        inAppEnabled: snapshot.preferences.inAppEnabled,
        emailEnabled: snapshot.preferences.emailEnabled,
        smsEnabled: snapshot.preferences.smsEnabled,
        whatsappEnabled: snapshot.preferences.whatsappEnabled,
        preferredChannel: snapshot.preferences.preferredChannel,
        quietHoursStart: snapshot.preferences.quietHoursStart,
        quietHoursEnd: snapshot.preferences.quietHoursEnd,
      },
      { emitEvent: false }
    );

    this.formVersion.update((v) => v + 1);
  }

  private fetchPeriodsForYear(yearId: string): void {
    this.periodsLoading.set(true);
    this.settingsApi
      .listPeriodsForYear(yearId)
      .pipe(
        catchError(() => of<readonly EvaluationPeriodItem[]>([])),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.periodsLoading.set(false))
      )
      .subscribe((periods) => {
        this.periodsByYearId.update((current) => ({
          ...current,
          [yearId]: periods,
        }));
        if (this.academicForm.controls.academicYearId.value === yearId) {
          const openPeriod = periods.find((p) => p.status === 'OPEN') ?? periods[0] ?? null;
          this.academicForm.controls.activePeriodId.setValue(openPeriod?.id ?? '', {
            emitEvent: false,
          });
          this.formVersion.update((v) => v + 1);
        }
      });
  }

  private submitSchoolForm(): void {
    const currentSchool = this.school();
    if (!currentSchool) {
      return;
    }
    this.saveError.set(null);
    this.serverFieldErrors.set({});
    this.schoolForm.markAllAsTouched();
    this.formVersion.update((v) => v + 1);

    if (this.schoolForm.invalid) {
      return;
    }

    const raw = this.schoolForm.getRawValue();
    this.saving.set(true);

    this.settingsApi
      .updateSchool({
        name: raw.name,
        address: raw.address,
        city: raw.city,
        region: raw.region,
        phone: raw.phone,
        email: raw.email,
        currentSchool,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false))
      )
      .subscribe({
        next: (updated) => {
          this.school.set(updated);
          this.schoolForm.reset(
            {
              name: updated.name,
              address: updated.address,
              city: updated.city,
              region: updated.region,
              phone: updated.phone,
              email: updated.email,
            },
            { emitEvent: false }
          );
          this.formVersion.update((v) => v + 1);
          this.toastService.success(
            "Les coordonnées de l'établissement ont été enregistrées.",
            'Paramètres enregistrés'
          );
        },
        error: (error: unknown) => {
          const problem = parseProblemDetail(error);
          this.saveError.set(problem);
          this.serverFieldErrors.set(extractFieldErrors(problem));
        },
      });
  }

  private submitAcademicForm(): void {
    const selectedYear = this.selectedAcademicYear();
    if (!selectedYear) {
      return;
    }
    this.saveError.set(null);
    this.serverFieldErrors.set({});
    this.academicForm.markAllAsTouched();
    this.formVersion.update((v) => v + 1);

    if (this.academicForm.invalid) {
      return;
    }

    const raw = this.academicForm.getRawValue();
    const selectedPeriod =
      this.selectedYearPeriods().find((period) => period.id === raw.activePeriodId) ?? null;

    this.saving.set(true);

    this.settingsApi
      .updateAcademicSettings({
        academicYearId: selectedYear.id,
        label: selectedYear.label,
        startDate: raw.startDate,
        endDate: raw.endDate,
        periodSystem: raw.periodSystem,
        setAsCurrent: raw.setAsCurrent,
        shouldActivateYear: raw.setAsCurrent && selectedYear.status === 'DRAFT',
        activePeriodId: selectedPeriod?.id ?? null,
        shouldOpenPeriod: selectedPeriod !== null && selectedPeriod.status === 'PLANNED',
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false))
      )
      .subscribe({
        next: ({ year, openedPeriod }) => {
          this.academicYears.update((list) =>
            list.map((item) => {
              if (item.id === year.id) {
                return year;
              }
              if (year.current && item.current) {
                return { ...item, current: false };
              }
              return item;
            })
          );

          if (openedPeriod) {
            this.periodsByYearId.update((map) => ({
              ...map,
              [year.id]: (map[year.id] ?? []).map((period) => {
                if (period.id === openedPeriod.id) {
                  return openedPeriod;
                }
                if (period.status === 'OPEN') {
                  return { ...period, status: 'CLOSED' as const };
                }
                return period;
              }),
            }));
          }

          this.academicForm.markAsPristine();
          this.formVersion.update((v) => v + 1);
          this.toastService.success(
            `La configuration de ${year.label} a été mise à jour.`,
            'Année scolaire enregistrée'
          );
        },
        error: (error: unknown) => {
          const problem = parseProblemDetail(error);
          this.saveError.set(problem);
          this.serverFieldErrors.set(extractFieldErrors(problem));
        },
      });
  }

  private submitPreferencesForm(): void {
    this.saveError.set(null);
    this.serverFieldErrors.set({});
    this.preferencesForm.markAllAsTouched();

    const raw = this.preferencesForm.getRawValue();
    const payload: NotificationPreferences = {
      receiptNotificationsEnabled: raw.receiptNotificationsEnabled,
      absenceNotificationsEnabled: raw.absenceNotificationsEnabled,
      inAppEnabled: raw.inAppEnabled,
      emailEnabled: raw.emailEnabled,
      smsEnabled: raw.smsEnabled,
      whatsappEnabled: raw.whatsappEnabled,
      preferredChannel: raw.preferredChannel,
      preferredLanguage: this.preferences().preferredLanguage || 'fr',
      quietHoursStart: raw.quietHoursStart,
      quietHoursEnd: raw.quietHoursEnd,
    };

    this.saving.set(true);

    this.settingsApi
      .updatePreferences(payload)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false))
      )
      .subscribe({
        next: (saved) => {
          this.preferences.set(saved);
          this.preferencesForm.markAsPristine();
          this.formVersion.update((v) => v + 1);
          this.toastService.success(
            'Vos préférences d’affichage et de notifications ont été enregistrées.',
            'Préférences enregistrées'
          );
        },
        error: (error: unknown) => {
          const problem = parseProblemDetail(error);
          this.saveError.set(problem);
          this.serverFieldErrors.set(extractFieldErrors(problem));
        },
      });
  }
}
