import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  LucideArrowLeft,
  LucideCheck,
  LucideChevronLeft,
  LucideChevronRight,
  LucideCircleAlert,
  LucideCircleCheck,
  LucideDownload,
  LucideFileCheck,
  LucideFileText,
  LucideInfo,
  LucideRotateCcw,
  LucideShieldCheck,
  LucideUpload,
  LucideWifiOff,
  LucideX,
} from '@lucide/angular';
import { switchMap } from 'rxjs';
import { ProblemDetail, parseProblemDetail } from '../../core/models/problem-detail.model';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header';
import { SkeletonLoaderComponent } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatCardComponent } from '../../shared/components/stat-card/stat-card';
import { BadgeTone, StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { ToastService } from '../../shared/components/toast/toast.service';
import { DakarDatePipe } from '../../shared/pipes/dakar-date.pipe';
import { ImportApiService } from './data-access/import-api.service';
import {
  IMPORT_ACCEPT_ATTRIBUTE,
  IMPORT_MAX_FILE_SIZE_BYTES,
  IMPORT_TYPE_OPTIONS,
  ImportFileDownload,
  ImportJobDto,
  ImportJobStatus,
  ImportPreviewRow,
  ImportRowStatus,
  ImportSourceFormat,
  ImportType,
  ImportValidationMode,
  PreviewRowFilter,
  VALIDATION_MODE_OPTIONS,
  detectFileExtensionFormat,
  formatFileSize,
  isJobExecutable,
  validateImportFile,
} from './models/import.model';

export type ImportWizardStep = 1 | 2 | 3;

type RetryableAction = 'analyze' | 'rows' | 'execute' | 'template' | 'errors' | null;

interface StepDescriptor {
  readonly number: ImportWizardStep;
  readonly label: string;
  readonly shortLabel: string;
}

interface FilterOption {
  readonly value: PreviewRowFilter;
  readonly label: string;
}

const STEPS: readonly StepDescriptor[] = [
  { number: 1, label: '1. Fichier', shortLabel: 'Fichier' },
  { number: 2, label: '2. Prévisualisation & validation', shortLabel: 'Prévisualisation' },
  { number: 3, label: "3. Rapport d'import", shortLabel: 'Rapport' },
];

const ROW_FILTER_OPTIONS: readonly FilterOption[] = [
  { value: 'ALL', label: 'Toutes' },
  { value: 'VALID', label: 'Valides' },
  { value: 'ERROR', label: 'En erreur' },
];

const PREVIEW_PAGE_SIZE = 20;
const INTEGER_FORMATTER = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });

function formatCount(value: number): string {
  return INTEGER_FORMATTER.format(Math.max(0, Math.trunc(value))).replace(/\u202F/g, '\u00A0');
}

function generateIdempotencyKey(prefix: string): string {
  const randomPart =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  return `${prefix}-${randomPart}`;
}

function parseImportType(raw: string): ImportType {
  const match = IMPORT_TYPE_OPTIONS.find((option) => option.value === raw);
  return match ? match.value : 'STUDENTS';
}

function parseValidationMode(raw: string): ImportValidationMode {
  return raw === 'STRICT_ALL_OR_NOTHING' ? 'STRICT_ALL_OR_NOTHING' : 'PARTIAL_SKIP_ERRORS';
}

@Component({
  selector: 'app-import-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent,
    StatCardComponent,
    StatusBadgeComponent,
    SkeletonLoaderComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    DakarDatePipe,
    LucideArrowLeft,
    LucideCheck,
    LucideChevronLeft,
    LucideChevronRight,
    LucideCircleAlert,
    LucideCircleCheck,
    LucideDownload,
    LucideFileCheck,
    LucideFileText,
    LucideInfo,
    LucideRotateCcw,
    LucideShieldCheck,
    LucideUpload,
    LucideWifiOff,
    LucideX,
  ],
  templateUrl: './import-page.html',
})
export class ImportPageComponent {
  private readonly importApi = inject(ImportApiService);
  private readonly toastService = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  protected readonly networkStatus = inject(NetworkStatusService);

  private readonly fileInputRef = viewChild<ElementRef<HTMLInputElement>>('fileInput');

  protected readonly steps = STEPS;
  protected readonly rowFilterOptions = ROW_FILTER_OPTIONS;
  protected readonly importTypeOptions = IMPORT_TYPE_OPTIONS;
  protected readonly validationModeOptions = VALIDATION_MODE_OPTIONS;
  protected readonly acceptAttribute = IMPORT_ACCEPT_ATTRIBUTE;
  protected readonly maxFileSizeLabel = formatFileSize(IMPORT_MAX_FILE_SIZE_BYTES);

  protected readonly currentStep = signal<ImportWizardStep>(1);
  protected readonly selectedImportType = signal<ImportType>('STUDENTS');
  protected readonly selectedValidationMode = signal<ImportValidationMode>('PARTIAL_SKIP_ERRORS');

  protected readonly selectedFile = signal<File | null>(null);
  protected readonly fileValidationError = signal<string | null>(null);
  protected readonly isDragging = signal<boolean>(false);

  protected readonly analyzing = signal<boolean>(false);
  protected readonly rowsLoading = signal<boolean>(false);
  protected readonly executing = signal<boolean>(false);
  protected readonly downloadingTemplate = signal<boolean>(false);
  protected readonly downloadingErrors = signal<boolean>(false);

  protected readonly errorProblem = signal<ProblemDetail | null>(null);
  private readonly lastFailedAction = signal<RetryableAction>(null);

  protected readonly currentJob = signal<ImportJobDto | null>(null);
  protected readonly previewRows = signal<readonly ImportPreviewRow[]>([]);
  protected readonly rowFilter = signal<PreviewRowFilter>('ALL');
  protected readonly rowsPage = signal<number>(0);
  protected readonly rowsTotalElements = signal<number>(0);
  protected readonly rowsTotalPages = signal<number>(0);
  protected readonly rowsFirst = signal<boolean>(true);
  protected readonly rowsLast = signal<boolean>(true);

  protected readonly isBusy = computed<boolean>(
    () => this.analyzing() || this.executing() || this.rowsLoading()
  );

  protected readonly selectedFileSizeLabel = computed<string>(() => {
    const file = this.selectedFile();
    if (file) {
      return formatFileSize(file.size);
    }
    const job = this.currentJob();
    return job ? formatFileSize(job.fileSizeBytes) : '';
  });

  protected readonly selectedFileName = computed<string>(
    () => this.selectedFile()?.name ?? this.currentJob()?.fileName ?? ''
  );

  protected readonly selectedFileFormat = computed<ImportSourceFormat | null>(() => {
    const file = this.selectedFile();
    if (file) {
      return detectFileExtensionFormat(file.name);
    }
    return this.currentJob()?.sourceFormat ?? null;
  });

  protected readonly activeImportTypeMeta = computed(
    () =>
      IMPORT_TYPE_OPTIONS.find((item) => item.value === this.selectedImportType()) ??
      IMPORT_TYPE_OPTIONS[0]
  );

  protected readonly canLaunchImport = computed<boolean>(
    () =>
      this.currentStep() === 2 &&
      isJobExecutable(this.currentJob()) &&
      !this.analyzing() &&
      !this.executing() &&
      this.networkStatus.isOnline()
  );

  protected readonly headerSubtitle = computed<string>(() => {
    const job = this.currentJob();
    if (this.currentStep() === 2 && job) {
      return `Lot ${job.jobNumber} • ${formatCount(job.totalRows)} lignes détectées (${formatCount(job.validRows)} valides, ${formatCount(job.errorRows)} en erreur)`;
    }
    if (this.currentStep() === 3 && job) {
      const imported = job.createdCount + job.updatedCount;
      const noun = imported > 1 ? 'lignes importées' : 'ligne importée';
      return `Lot ${job.jobNumber} clôturé • ${formatCount(imported)} ${noun}`;
    }
    return 'Importez vos fichiers .csv ou .xlsx avec prévisualisation, détection des anomalies et rapport téléchargeable.';
  });

  protected readonly totalRowsFormatted = computed<string>(() =>
    formatCount(this.currentJob()?.totalRows ?? 0)
  );

  protected readonly validRowsFormatted = computed<string>(() =>
    formatCount(this.currentJob()?.validRows ?? 0)
  );

  protected readonly errorRowsFormatted = computed<string>(() =>
    formatCount(this.currentJob()?.errorRows ?? 0)
  );

  protected readonly importedCount = computed<number>(() => {
    const job = this.currentJob();
    return job ? job.createdCount + job.updatedCount : 0;
  });

  protected readonly importedCountFormatted = computed<string>(() =>
    formatCount(this.importedCount())
  );

  protected readonly importedBreakdown = computed<string>(() => {
    const job = this.currentJob();
    if (!job) {
      return '';
    }
    const createdNoun = job.createdCount > 1 ? 'créées' : 'créée';
    const updatedNoun = job.updatedCount > 1 ? 'mises à jour' : 'mise à jour';
    return `${formatCount(job.createdCount)} ${createdNoun} • ${formatCount(job.updatedCount)} ${updatedNoun}`;
  });

  protected readonly skippedCountFormatted = computed<string>(() =>
    formatCount(this.currentJob()?.skippedCount ?? 0)
  );

  protected readonly hasJobErrors = computed<boolean>(
    () => (this.currentJob()?.errorRows ?? 0) > 0
  );

  protected readonly isStrictValidationBlocked = computed<boolean>(() => {
    const job = this.currentJob();
    return (
      job !== null &&
      job.status === 'VALIDATION_FAILED' &&
      job.errorRows > 0 &&
      job.validationMode === 'STRICT_ALL_OR_NOTHING'
    );
  });

  protected readonly currentPageDisplay = computed<number>(() => this.rowsPage() + 1);

  protected readonly paginationRangeLabel = computed<string>(() => {
    const total = this.rowsTotalElements();
    const count = this.previewRows().length;
    if (total === 0 || count === 0) {
      return '0 ligne affichée';
    }
    const start = this.rowsPage() * PREVIEW_PAGE_SIZE + 1;
    const end = start + count - 1;
    const noun = total > 1 ? 'lignes' : 'ligne';
    return `Affichage de ${formatCount(start)}–${formatCount(end)} sur ${formatCount(total)} ${noun}`;
  });

  protected onImportTypeChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    if (!select) {
      return;
    }
    this.selectedImportType.set(parseImportType(select.value));
  }

  protected onValidationModeChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    if (!select) {
      return;
    }
    this.selectedValidationMode.set(parseValidationMode(select.value));
  }

  protected openFilePicker(event?: Event): void {
    event?.stopPropagation();
    if (this.analyzing() || this.executing()) {
      return;
    }
    this.fileInputRef()?.nativeElement.click();
  }

  protected onDropzoneKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.openFilePicker();
    }
  }

  protected onDragEnter(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    if (!this.analyzing() && !this.executing()) {
      this.isDragging.set(true);
    }
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    if (!this.analyzing() && !this.executing()) {
      this.isDragging.set(true);
    }
  }

  protected onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(false);
  }

  protected onFileDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(false);
    if (this.analyzing() || this.executing()) {
      return;
    }
    const droppedFile = event.dataTransfer?.files?.item(0) ?? null;
    this.handleIncomingFile(droppedFile);
  }

  protected onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const pickedFile = input?.files?.item(0) ?? null;
    this.handleIncomingFile(pickedFile);
    if (input) {
      input.value = '';
    }
  }

  protected clearSelectedFile(): void {
    if (this.analyzing() || this.executing()) {
      return;
    }
    this.selectedFile.set(null);
    this.fileValidationError.set(null);
    this.errorProblem.set(null);
    this.lastFailedAction.set(null);
    this.currentJob.set(null);
    this.previewRows.set([]);
    this.rowFilter.set('ALL');
    this.currentStep.set(1);
  }

  protected backToFileSelection(): void {
    if (this.analyzing() || this.executing()) {
      return;
    }
    this.errorProblem.set(null);
    this.lastFailedAction.set(null);
    this.currentStep.set(1);
  }

  protected downloadTemplate(): void {
    if (this.downloadingTemplate()) {
      return;
    }
    if (!this.networkStatus.isOnline()) {
      this.toastService.warning(
        'Connexion réseau requise pour télécharger le modèle CSV.',
        'Hors-ligne'
      );
      return;
    }

    this.downloadingTemplate.set(true);
    this.errorProblem.set(null);

    this.importApi
      .downloadTemplate(this.selectedImportType())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (download) => {
          this.downloadingTemplate.set(false);
          this.triggerBrowserDownload(download);
          this.toastService.success(
            `Le modèle ${download.fileName} a été téléchargé.`,
            'Modèle CSV prêt'
          );
        },
        error: (error: unknown) => {
          this.downloadingTemplate.set(false);
          this.lastFailedAction.set('template');
          this.errorProblem.set(parseProblemDetail(error));
        },
      });
  }

  protected analyzeSelectedFile(): void {
    const file = this.selectedFile();
    if (!file || this.analyzing() || this.executing()) {
      return;
    }

    const validation = validateImportFile(file);
    if (!validation.valid) {
      this.fileValidationError.set(validation.errorMessage);
      return;
    }

    if (!this.networkStatus.isOnline()) {
      this.toastService.warning(
        "Impossible d'analyser le fichier hors-ligne. Reconnectez-vous puis réessayez.",
        'Connexion requise'
      );
      return;
    }

    this.analyzing.set(true);
    this.fileValidationError.set(null);
    this.errorProblem.set(null);
    this.lastFailedAction.set(null);
    this.rowFilter.set('ALL');
    this.rowsPage.set(0);

    const idempotencyKey = generateIdempotencyKey('import-analyze');

    this.importApi
      .createImportJob({
        file,
        importType: this.selectedImportType(),
        validationMode: this.selectedValidationMode(),
        duplicateStrategy: 'SKIP_DUPLICATES',
        executeImmediately: false,
        idempotencyKey,
      })
      .pipe(
        switchMap((job) => {
          this.currentJob.set(job);
          return this.importApi.listJobRows(job.id, {
            page: 0,
            size: PREVIEW_PAGE_SIZE,
            sortBy: 'rowNumber',
            direction: 'ASC',
          });
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (rowsPage) => {
          this.previewRows.set(rowsPage.content);
          this.rowsPage.set(rowsPage.page);
          this.rowsTotalElements.set(rowsPage.totalElements);
          this.rowsTotalPages.set(rowsPage.totalPages);
          this.rowsFirst.set(rowsPage.first);
          this.rowsLast.set(rowsPage.last);
          this.analyzing.set(false);
          this.currentStep.set(2);

          const analyzedJob = this.currentJob();
          if (analyzedJob) {
            const noun = analyzedJob.totalRows > 1 ? 'lignes analysées' : 'ligne analysée';
            this.toastService.success(
              `${formatCount(analyzedJob.totalRows)} ${noun} (${formatCount(analyzedJob.validRows)} valides, ${formatCount(analyzedJob.errorRows)} en erreur).`,
              'Prévisualisation prête'
            );
          }
        },
        error: (error: unknown) => {
          this.analyzing.set(false);
          this.lastFailedAction.set('analyze');
          this.errorProblem.set(parseProblemDetail(error));
        },
      });
  }

  protected setRowFilter(filter: PreviewRowFilter): void {
    const job = this.currentJob();
    if (!job || this.rowFilter() === filter || this.rowsLoading()) {
      return;
    }
    this.rowFilter.set(filter);
    this.loadJobRows(job.id, 0, filter);
  }

  protected resetRowFilter(): void {
    this.setRowFilter('ALL');
  }

  protected goToPreviousRowsPage(): void {
    const job = this.currentJob();
    if (!job || this.rowsFirst() || this.rowsLoading()) {
      return;
    }
    this.loadJobRows(job.id, Math.max(0, this.rowsPage() - 1), this.rowFilter());
  }

  protected goToNextRowsPage(): void {
    const job = this.currentJob();
    if (!job || this.rowsLast() || this.rowsLoading()) {
      return;
    }
    this.loadJobRows(job.id, this.rowsPage() + 1, this.rowFilter());
  }

  protected launchImport(): void {
    const job = this.currentJob();
    if (!job || !this.canLaunchImport()) {
      return;
    }

    this.executing.set(true);
    this.errorProblem.set(null);
    this.lastFailedAction.set(null);

    const idempotencyKey = generateIdempotencyKey('import-exec');

    this.importApi
      .executeJob(job.id, idempotencyKey)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (executedJob) => {
          this.currentJob.set(executedJob);
          this.executing.set(false);
          this.currentStep.set(3);
          const imported = executedJob.createdCount + executedJob.updatedCount;
          const noun = imported > 1 ? 'lignes importées' : 'ligne importée';
          this.toastService.success(
            `Lot ${executedJob.jobNumber} terminé : ${formatCount(imported)} ${noun}.`,
            'Import réussi'
          );
        },
        error: (error: unknown) => {
          this.executing.set(false);
          this.lastFailedAction.set('execute');
          this.errorProblem.set(parseProblemDetail(error));
        },
      });
  }

  protected downloadErrorReport(): void {
    const job = this.currentJob();
    if (!job || this.downloadingErrors() || job.errorRows <= 0) {
      return;
    }
    if (!this.networkStatus.isOnline()) {
      this.toastService.warning(
        "Connexion réseau requise pour télécharger le rapport d'erreurs.",
        'Hors-ligne'
      );
      return;
    }

    this.downloadingErrors.set(true);
    this.errorProblem.set(null);

    this.importApi
      .downloadErrorReport(job.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (download) => {
          this.downloadingErrors.set(false);
          this.triggerBrowserDownload(download);
          this.toastService.success(
            `Le rapport d'anomalies ${download.fileName} a été téléchargé.`,
            "Rapport d'erreurs prêt"
          );
        },
        error: (error: unknown) => {
          this.downloadingErrors.set(false);
          this.lastFailedAction.set('errors');
          this.errorProblem.set(parseProblemDetail(error));
        },
      });
  }

  protected startNewImport(): void {
    this.clearSelectedFile();
  }

  protected retryLastAction(): void {
    const action = this.lastFailedAction();
    this.errorProblem.set(null);

    switch (action) {
      case 'analyze':
        this.analyzeSelectedFile();
        break;
      case 'rows': {
        const job = this.currentJob();
        if (job) {
          this.loadJobRows(job.id, this.rowsPage(), this.rowFilter());
        }
        break;
      }
      case 'execute':
        this.launchImport();
        break;
      case 'template':
        this.downloadTemplate();
        break;
      case 'errors':
        this.downloadErrorReport();
        break;
      default:
        break;
    }
  }

  protected filterBadgeCount(filter: PreviewRowFilter): string {
    const job = this.currentJob();
    if (!job) {
      return '0';
    }
    switch (filter) {
      case 'VALID':
        return formatCount(job.validRows);
      case 'ERROR':
        return formatCount(job.errorRows);
      default:
        return formatCount(job.totalRows);
    }
  }

  protected rowStatusLabel(status: ImportRowStatus): string {
    const labels: Readonly<Record<ImportRowStatus, string>> = {
      VALID: 'Valide',
      DUPLICATE: 'Doublon',
      ERROR: 'En erreur',
      CREATED: 'Importée',
      UPDATED: 'Mise à jour',
      SKIPPED: 'Ignorée',
    };
    return labels[status];
  }

  protected rowStatusTone(status: ImportRowStatus): BadgeTone {
    const tones: Readonly<Record<ImportRowStatus, BadgeTone>> = {
      VALID: 'success',
      DUPLICATE: 'warning',
      ERROR: 'error',
      CREATED: 'success',
      UPDATED: 'info',
      SKIPPED: 'neutral',
    };
    return tones[status];
  }

  protected jobStatusLabel(status: ImportJobStatus): string {
    const labels: Readonly<Record<ImportJobStatus, string>> = {
      PREVIEW_READY: 'Prêt à importer',
      VALIDATION_FAILED: 'Validation bloquée',
      PROCESSING: 'Traitement en cours',
      COMPLETED: 'Import terminé',
      COMPLETED_WITH_ERRORS: 'Terminé avec anomalies',
      FAILED: 'Échec de l’import',
      CANCELLED: 'Lot annulé',
    };
    return labels[status];
  }

  protected jobStatusTone(status: ImportJobStatus): BadgeTone {
    const tones: Readonly<Record<ImportJobStatus, BadgeTone>> = {
      PREVIEW_READY: 'info',
      VALIDATION_FAILED: 'error',
      PROCESSING: 'info',
      COMPLETED: 'success',
      COMPLETED_WITH_ERRORS: 'warning',
      FAILED: 'error',
      CANCELLED: 'neutral',
    };
    return tones[status];
  }

  private handleIncomingFile(file: File | null): void {
    const validation = validateImportFile(file);
    if (!validation.valid || !file) {
      this.selectedFile.set(null);
      this.currentJob.set(null);
      this.previewRows.set([]);
      this.fileValidationError.set(validation.errorMessage);
      return;
    }

    this.fileValidationError.set(null);
    this.errorProblem.set(null);
    this.selectedFile.set(file);
    this.analyzeSelectedFile();
  }

  private loadJobRows(jobId: string, page: number, filter: PreviewRowFilter): void {
    this.rowsLoading.set(true);
    this.errorProblem.set(null);
    this.lastFailedAction.set(null);

    const rowStatus: ImportRowStatus | undefined =
      filter === 'VALID' ? 'VALID' : filter === 'ERROR' ? 'ERROR' : undefined;

    this.importApi
      .listJobRows(jobId, {
        rowStatus,
        page,
        size: PREVIEW_PAGE_SIZE,
        sortBy: 'rowNumber',
        direction: 'ASC',
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.previewRows.set(result.content);
          this.rowsPage.set(result.page);
          this.rowsTotalElements.set(result.totalElements);
          this.rowsTotalPages.set(result.totalPages);
          this.rowsFirst.set(result.first);
          this.rowsLast.set(result.last);
          this.rowsLoading.set(false);
        },
        error: (error: unknown) => {
          this.rowsLoading.set(false);
          this.lastFailedAction.set('rows');
          this.errorProblem.set(parseProblemDetail(error));
        },
      });
  }

  private triggerBrowserDownload(download: ImportFileDownload): void {
    const win = this.document.defaultView;
    if (!win || typeof win.URL?.createObjectURL !== 'function') {
      return;
    }
    const objectUrl = win.URL.createObjectURL(download.blob);
    const link = this.document.createElement('a');
    link.href = objectUrl;
    link.download = download.fileName;
    link.style.display = 'none';
    this.document.body.appendChild(link);
    link.click();
    link.remove();
    win.URL.revokeObjectURL(objectUrl);
  }
}
