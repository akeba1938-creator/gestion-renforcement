import { ChangeDetectionStrategy, reflectComponentType, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ProblemDetailError } from '../../core/models/problem-detail.model';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { ImportApiService } from './data-access/import-api.service';
import { ImportPageComponent } from './import-page';
import {
  CreateImportJobInput,
  IMPORT_MAX_FILE_SIZE_BYTES,
  ImportFileDownload,
  ImportJobDto,
  ImportJobRowsPage,
  ImportPreviewRow,
  ImportType,
  ListImportRowsQuery,
} from './models/import.model';

const ANALYZED_JOB: ImportJobDto = {
  id: 'job-001',
  jobNumber: 'IMP-2026-0042',
  importType: 'STUDENTS',
  sourceFormat: 'CSV',
  validationMode: 'PARTIAL_SKIP_ERRORS',
  duplicateStrategy: 'SKIP_DUPLICATES',
  status: 'PREVIEW_READY',
  academicYearId: null,
  evaluationId: null,
  attendanceSessionId: null,
  fileName: 'eleves-rentree.csv',
  contentType: 'text/csv; charset=UTF-8',
  fileSizeBytes: 3456,
  fileSha256: 'sha-demo-1',
  totalRows: 3,
  validRows: 2,
  errorRows: 1,
  duplicateRows: 0,
  createdCount: 0,
  updatedCount: 0,
  skippedCount: 0,
  quotaResource: 'STUDENTS',
  quotaLimit: 2000,
  quotaUsedBefore: 300,
  quotaProjectedAfter: 302,
  createdBy: 'usr-1',
  analyzedAt: '2026-11-26T10:00:00Z',
  executedAt: null,
  executedBy: null,
  cancelledAt: null,
  cancelledBy: null,
  cancellationReason: null,
  createdAt: '2026-11-26T10:00:00Z',
};

const EXECUTED_JOB: ImportJobDto = {
  ...ANALYZED_JOB,
  status: 'COMPLETED_WITH_ERRORS',
  createdCount: 2,
  updatedCount: 0,
  skippedCount: 1,
  executedAt: '2026-11-26T10:05:00Z',
  executedBy: 'usr-1',
};

const SAMPLE_ROWS: readonly ImportPreviewRow[] = [
  {
    id: 'row-2',
    rowNumber: 2,
    naturalKey: 'DEMO-2026-101',
    studentName: 'Aminata Diallo-Démo',
    matricule: 'DEMO-2026-101',
    className: '6ème A',
    guardianPhone: '+221000000001',
    rowStatus: 'VALID',
    plannedAction: 'CREATE',
    appliedAction: null,
    errorField: null,
    anomaly: null,
  },
  {
    id: 'row-3',
    rowNumber: 3,
    naturalKey: 'DEMO-2026-102',
    studentName: 'Cheikh Ndiaye-Démo',
    matricule: 'DEMO-2026-102',
    className: '3ème A',
    guardianPhone: '+221000000002',
    rowStatus: 'VALID',
    plannedAction: 'CREATE',
    appliedAction: null,
    errorField: null,
    anomaly: null,
  },
  {
    id: 'row-4',
    rowNumber: 4,
    naturalKey: 'DEMO-2026-103',
    studentName: 'Mouhamadou Fall-Démo',
    matricule: 'DEMO-2026-103',
    className: 'Seconde S',
    guardianPhone: '7712345',
    rowStatus: 'ERROR',
    plannedAction: 'ERROR',
    appliedAction: null,
    errorField: 'phone',
    anomaly: 'Format téléphone invalide : attendu +221 suivi de 9 chiffres.',
  },
];

function buildRowsPage(rows: readonly ImportPreviewRow[]): ImportJobRowsPage {
  return {
    content: rows,
    page: 0,
    size: 20,
    totalElements: rows.length,
    totalPages: rows.length > 0 ? 1 : 0,
    first: true,
    last: true,
  };
}

interface ImportApiStub {
  downloadTemplate: ReturnType<
    typeof vi.fn<(type: ImportType) => Observable<ImportFileDownload>>
  >;
  createImportJob: ReturnType<
    typeof vi.fn<(input: CreateImportJobInput) => Observable<ImportJobDto>>
  >;
  listJobRows: ReturnType<
    typeof vi.fn<(jobId: string, query?: ListImportRowsQuery) => Observable<ImportJobRowsPage>>
  >;
  executeJob: ReturnType<
    typeof vi.fn<(jobId: string, idempotencyKey?: string) => Observable<ImportJobDto>>
  >;
  downloadErrorReport: ReturnType<
    typeof vi.fn<(jobId: string) => Observable<ImportFileDownload>>
  >;
}

describe('ImportPageComponent', () => {
  function createApiStub(overrides?: Partial<ImportApiStub>): ImportApiStub {
    return {
      downloadTemplate:
        overrides?.downloadTemplate ??
        vi.fn(() =>
          of<ImportFileDownload>({
            blob: new Blob(['first_name;last_name\r\n'], { type: 'text/csv; charset=UTF-8' }),
            fileName: 'modele-import-students.csv',
            contentType: 'text/csv; charset=UTF-8',
          })
        ),
      createImportJob:
        overrides?.createImportJob ?? vi.fn(() => of<ImportJobDto>(ANALYZED_JOB)),
      listJobRows:
        overrides?.listJobRows ??
        vi.fn((_jobId: string, query?: ListImportRowsQuery) => {
          if (query?.rowStatus === 'VALID') {
            return of(buildRowsPage(SAMPLE_ROWS.filter((r) => r.rowStatus === 'VALID')));
          }
          if (query?.rowStatus === 'ERROR') {
            return of(buildRowsPage(SAMPLE_ROWS.filter((r) => r.rowStatus === 'ERROR')));
          }
          return of(buildRowsPage(SAMPLE_ROWS));
        }),
      executeJob: overrides?.executeJob ?? vi.fn(() => of<ImportJobDto>(EXECUTED_JOB)),
      downloadErrorReport:
        overrides?.downloadErrorReport ??
        vi.fn(() =>
          of<ImportFileDownload>({
            blob: new Blob(['row_number;error_message\r\n4;Téléphone invalide'], {
              type: 'text/csv; charset=UTF-8',
            }),
            fileName: 'erreurs-imp-2026-0042.csv',
            contentType: 'text/csv; charset=UTF-8',
          })
        ),
    };
  }

  async function setup(options?: { api?: ImportApiStub; isOnline?: boolean }) {
    const api = options?.api ?? createApiStub();
    const onlineSignal = signal<boolean>(options?.isOnline ?? true);
    const toastSuccessSpy = vi.fn();
    const toastWarningSpy = vi.fn();

    await TestBed.configureTestingModule({
      imports: [ImportPageComponent],
      providers: [
        { provide: ImportApiService, useValue: api },
        {
          provide: NetworkStatusService,
          useValue: { isOnline: onlineSignal.asReadonly() },
        },
        {
          provide: ToastService,
          useValue: {
            success: toastSuccessSpy,
            warning: toastWarningSpy,
            error: vi.fn(),
            info: vi.fn(),
          },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ImportPageComponent);
    fixture.detectChanges();

    return { fixture, api, onlineSignal, toastSuccessSpy, toastWarningSpy };
  }

  function triggerFileSelection(fixture: { nativeElement: HTMLElement; detectChanges(): void }, file: File): void {
    const input = fixture.nativeElement.querySelector(
      '#import-file-input'
    ) as HTMLInputElement;
    Object.defineProperty(input, 'files', {
      value: {
        length: 1,
        item: (index: number) => (index === 0 ? file : null),
        0: file,
      },
      configurable: true,
    });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('uses standalone OnPush change detection and renders a single disabled btn-primary initially', async () => {
    const mirror = reflectComponentType(ImportPageComponent);
    expect(mirror?.isStandalone).toBe(true);
    const cmpDef = (ImportPageComponent as unknown as { ɵcmp?: { onPush?: boolean } }).ɵcmp;
    expect(cmpDef?.onPush).toBe(true);
    expect(ChangeDetectionStrategy.OnPush).toBeDefined();

    const { fixture } = await setup();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Import Excel & CSV');
    const primaryButtons = el.querySelectorAll('button.btn-primary');
    expect(primaryButtons.length).toBe(1);
    const launchBtn = primaryButtons[0] as HTMLButtonElement;
    expect(launchBtn.textContent).toContain("Lancer l'import");
    expect(launchBtn.disabled).toBe(true);

    const stepItems = el.querySelectorAll('nav[aria-label="Étapes de l\'import"] li');
    expect(stepItems.length).toBe(3);
    expect(stepItems[0]?.getAttribute('aria-current')).toBe('step');
  });

  it('validates file extension, empty file, and max file size client-side before calling backend', async () => {
    const { fixture, api } = await setup();
    const el: HTMLElement = fixture.nativeElement;

    // 1. Unsupported extension
    const pdfFile = new File(['%PDF-1.4'], 'bulletin.pdf', { type: 'application/pdf' });
    triggerFileSelection(fixture, pdfFile);
    expect(el.querySelector('#import-file-error')?.textContent).toContain('Format non pris en charge');
    expect(api.createImportJob).not.toHaveBeenCalled();

    // 2. Empty file
    const emptyCsv = new File([], 'vide.csv', { type: 'text/csv' });
    triggerFileSelection(fixture, emptyCsv);
    expect(el.querySelector('#import-file-error')?.textContent).toContain('est vide');
    expect(api.createImportJob).not.toHaveBeenCalled();

    // 3. Oversized file (> 5 Mo)
    const oversizedFile = new File(['a'], 'gros.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    Object.defineProperty(oversizedFile, 'size', {
      value: IMPORT_MAX_FILE_SIZE_BYTES + 1024,
      configurable: true,
    });
    triggerFileSelection(fixture, oversizedFile);
    expect(el.querySelector('#import-file-error')?.textContent).toContain('taille maximale');
    expect(api.createImportJob).not.toHaveBeenCalled();
  });

  it('shows skeleton loader while analyzing, then displays step 2 preview counters, table, filters, and enables launch button', async () => {
    const jobSubject = new Subject<ImportJobDto>();
    const api = createApiStub({
      createImportJob: vi.fn(() => jobSubject.asObservable()),
    });
    const { fixture, toastSuccessSpy } = await setup({ api });
    const el: HTMLElement = fixture.nativeElement;

    const validCsv = new File(['first_name;last_name\r\nAminata;Diallo'], 'eleves-rentree.csv', {
      type: 'text/csv',
    });
    triggerFileSelection(fixture, validCsv);

    // State 1: Skeleton loader visible while analyzing
    expect(el.querySelectorAll('app-skeleton-loader').length).toBeGreaterThan(0);

    jobSubject.next(ANALYZED_JOB);
    jobSubject.complete();
    fixture.detectChanges();

    // Step 2 active
    const stepItems = el.querySelectorAll('nav[aria-label="Étapes de l\'import"] li');
    expect(stepItems[1]?.getAttribute('aria-current')).toBe('step');

    // Single btn-primary is now enabled
    const primaryButtons = el.querySelectorAll('button.btn-primary');
    expect(primaryButtons.length).toBe(1);
    expect((primaryButtons[0] as HTMLButtonElement).disabled).toBe(false);

    // Preview table rows and phone + anomaly rendered
    expect(el.textContent).toContain('Lignes détectées');
    expect(el.textContent).toContain('Aminata Diallo-Démo');
    expect(el.textContent).toContain('+221000000001');
    expect(el.textContent).toContain('Format téléphone invalide');
    expect(toastSuccessSpy).toHaveBeenCalled();

    // Filter to "En erreur"
    const errorFilterBtn = Array.from(
      el.querySelectorAll('div[role="group"] button')
    ).find((b) => b.textContent?.includes('En erreur')) as HTMLButtonElement;
    errorFilterBtn.click();
    fixture.detectChanges();

    expect(api.listJobRows).toHaveBeenCalledWith(
      ANALYZED_JOB.id,
      expect.objectContaining({ rowStatus: 'ERROR', page: 0 })
    );
    expect(el.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('displays filtered empty state (State 3) and allows resetting filter back to ALL', async () => {
    const api = createApiStub({
      listJobRows: vi.fn((_jobId: string, query?: ListImportRowsQuery) => {
        if (query?.rowStatus === 'ERROR') {
          return of(buildRowsPage([]));
        }
        return of(buildRowsPage(SAMPLE_ROWS));
      }),
    });
    const { fixture } = await setup({ api });
    const el: HTMLElement = fixture.nativeElement;

    const csv = new File(['first_name;last_name\r\nAwa;Ndiaye'], 'clean.csv', { type: 'text/csv' });
    triggerFileSelection(fixture, csv);

    const errorFilterBtn = Array.from(
      el.querySelectorAll('div[role="group"] button')
    ).find((b) => b.textContent?.includes('En erreur')) as HTMLButtonElement;
    errorFilterBtn.click();
    fixture.detectChanges();

    expect(el.querySelector('app-empty-state')?.textContent).toContain('Aucune ligne pour ce filtre');
    const resetBtn = el.querySelector('app-empty-state button') as HTMLButtonElement;
    expect(resetBtn.textContent).toContain('Réinitialiser les filtres');
    resetBtn.click();
    fixture.detectChanges();

    expect(el.querySelectorAll('tbody tr').length).toBe(3);
  });

  it('displays empty state (State 2) when analyzed file has zero data rows and keeps launch disabled', async () => {
    const emptyJob: ImportJobDto = {
      ...ANALYZED_JOB,
      status: 'VALIDATION_FAILED',
      totalRows: 0,
      validRows: 0,
      errorRows: 0,
    };
    const api = createApiStub({
      createImportJob: vi.fn(() => of(emptyJob)),
      listJobRows: vi.fn(() => of(buildRowsPage([]))),
    });
    const { fixture } = await setup({ api });
    const el: HTMLElement = fixture.nativeElement;

    const csv = new File(['first_name;last_name\r\n'], 'header-only.csv', { type: 'text/csv' });
    triggerFileSelection(fixture, csv);

    expect(el.querySelector('app-empty-state')?.textContent).toContain(
      'Aucune ligne exploitable détectée'
    );
    const launchBtn = el.querySelector('button.btn-primary') as HTMLButtonElement;
    expect(launchBtn.disabled).toBe(true);
    expect(el.querySelectorAll('button.btn-primary').length).toBe(1);
  });

  it('executes import to step 3 report, allows downloading error report CSV, and starts new import', async () => {
    const { fixture, api, toastSuccessSpy } = await setup();
    const el: HTMLElement = fixture.nativeElement;

    const csv = new File(['first_name;last_name\r\nAwa;Ndiaye'], 'eleves-rentree.csv', {
      type: 'text/csv',
    });
    triggerFileSelection(fixture, csv);

    const launchBtn = el.querySelector('button.btn-primary') as HTMLButtonElement;
    launchBtn.click();
    fixture.detectChanges();

    expect(api.executeJob).toHaveBeenCalledWith(ANALYZED_JOB.id, expect.stringMatching(/^import-exec-/));
    const stepItems = el.querySelectorAll('nav[aria-label="Étapes de l\'import"] li');
    expect(stepItems[2]?.getAttribute('aria-current')).toBe('step');
    expect(el.textContent).toContain("Rapport d'import finalisé");
    expect(el.textContent).toContain('Lignes importées');
    expect(el.textContent).toContain('Lignes ignorées');

    // Download error report CSV
    const downloadErrorsBtn = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes("Télécharger le rapport d'erreurs CSV")
    ) as HTMLButtonElement;
    expect(downloadErrorsBtn).toBeTruthy();
    downloadErrorsBtn.click();
    fixture.detectChanges();
    expect(api.downloadErrorReport).toHaveBeenCalledWith(EXECUTED_JOB.id);
    expect(toastSuccessSpy).toHaveBeenCalled();

    // Still strictly 1 btn-primary on Step 3
    expect(el.querySelectorAll('button.btn-primary').length).toBe(1);

    // Start new import resets back to Step 1
    const newImportBtn = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Nouvel import')
    ) as HTMLButtonElement;
    newImportBtn.click();
    fixture.detectChanges();

    const resetSteps = el.querySelectorAll('nav[aria-label="Étapes de l\'import"] li');
    expect(resetSteps[0]?.getAttribute('aria-current')).toBe('step');
  });

  it('renders RFC 9457 error state (State 4) with requestId and retries failed analysis', async () => {
    let callCount = 0;
    const api = createApiStub({
      createImportJob: vi.fn(() => {
        callCount += 1;
        if (callCount === 1) {
          return throwError(
            () =>
              new ProblemDetailError({
                type: 'urn:ecole:problem:unprocessable-entity',
                title: 'Fichier illisible',
                status: 422,
                detail: 'Encodage UTF-8 invalide dans le fichier CSV.',
                requestId: 'req-import-422-xyz',
              })
          );
        }
        return of(ANALYZED_JOB);
      }),
    });

    const { fixture } = await setup({ api });
    const el: HTMLElement = fixture.nativeElement;

    const csv = new File(['first_name;last_name\r\nAwa;Ndiaye'], 'eleves.csv', { type: 'text/csv' });
    triggerFileSelection(fixture, csv);

    const errorEl = el.querySelector('app-error-state');
    expect(errorEl).toBeTruthy();
    expect(errorEl?.textContent).toContain('Encodage UTF-8 invalide');
    expect(errorEl?.textContent).toContain('req-import-422-xyz');

    const retryBtn = Array.from(errorEl?.querySelectorAll('button') ?? []).find((b) =>
      b.textContent?.includes('Réessayer')
    ) as HTMLButtonElement;
    retryBtn.click();
    fixture.detectChanges();

    expect(api.createImportJob).toHaveBeenCalledTimes(2);
    expect(el.querySelector('app-error-state')).toBeNull();
    expect(el.textContent).toContain('Prévisualisation & anomalies');
  });

  it('shows offline banner (State 6) and disables network actions when offline', async () => {
    const { fixture } = await setup({ isOnline: false });
    const el: HTMLElement = fixture.nativeElement;

    expect(el.textContent).toContain('Mode hors-ligne détecté');
    const downloadTemplateBtn = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Télécharger le modèle CSV')
    ) as HTMLButtonElement;
    expect(downloadTemplateBtn.disabled).toBe(true);
  });
});
