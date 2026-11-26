import { ChangeDetectionStrategy, reflectComponentType } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ProblemDetailError } from '../../core/models/problem-detail.model';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { GradesApiService } from './data-access/grades-api.service';
import { GradesPageComponent } from './grades-page';
import {
  DEFAULT_GRADE_TERMS,
  GenerateReportCardsInput,
  GenerateReportCardsResult,
  GradeClassOption,
  GradeEntryStudentOption,
  GradeSubjectOption,
  GradesKpiSummary,
  GradesListQuery,
  GradesPageResult,
  SaveEvaluationGradesInput,
  SaveEvaluationGradesResult,
  StudentReportCardRecord,
} from './models/grades.model';

const SAMPLE_CLASSES: readonly GradeClassOption[] = [
  { id: 'cls-ts2', code: 'TS2', name: 'Terminale S2' },
  { id: 'cls-3a', code: '3A', name: '3ème A' },
];

const SAMPLE_SUBJECTS: readonly GradeSubjectOption[] = [
  { id: 'sub-math', code: 'MATH', name: 'Mathématiques', defaultCoefficient: 5 },
  { id: 'sub-fr', code: 'FR', name: 'Français', defaultCoefficient: 4 },
];

const SAMPLE_STUDENTS_FOR_ENTRY: readonly GradeEntryStudentOption[] = [
  {
    studentId: 'stu-1',
    studentFullName: 'Aminata Diallo-Démo',
    studentInitials: 'AD',
    studentMatricule: 'DEMO-2026-001',
    classRoomId: 'cls-ts2',
    classRoomName: 'Terminale S2',
  },
  {
    studentId: 'stu-2',
    studentFullName: 'Khady Sall-Démo',
    studentInitials: 'KS',
    studentMatricule: 'DEMO-2026-007',
    classRoomId: 'cls-ts2',
    classRoomName: 'Terminale S2',
  },
];

const SAMPLE_KPIS: GradesKpiSummary = {
  term: 'T1',
  overallAverage: 13.85,
  overallAverageTrend: 'Objectif atteint',
  overallAverageDescription: 'sur 12 élèves classés',
  passRatePercent: 83.3,
  passedStudentCount: 10,
  rankedStudentCount: 12,
  enteredGradesCount: 144,
  evaluationsCount: 18,
  readyReportCardsCount: 10,
  totalReportCardsCount: 12,
};

const SAMPLE_REPORT_CARDS: readonly StudentReportCardRecord[] = [
  {
    id: 'rc-1',
    academicYearId: 'ay-2026',
    evaluationPeriodId: 'term-t1',
    termCode: 'T1',
    periodName: 'Trimestre 1',
    classRoomId: 'cls-ts2',
    classRoomCode: 'TS2',
    classRoomName: 'Terminale S2',
    studentId: 'stu-1',
    studentMatricule: 'DEMO-2026-001',
    studentFirstName: 'Aminata',
    studentLastName: 'Diallo-Démo',
    studentFullName: 'Aminata Diallo-Démo',
    studentInitials: 'AD',
    gradingScale: 20,
    generalAverage: 16.45,
    classAverage: 13.1,
    minClassAverage: 8.5,
    maxClassAverage: 16.45,
    rankInClass: 1,
    classSize: 34,
    tiedRank: false,
    totalWeightedScore: 148.05,
    totalCoefficients: 9,
    mention: 'TRES_BIEN',
    councilDecision: 'FELICITATIONS',
    homeroomAppreciation: 'Excellent trimestre, travail exemplaire.',
    directorAppreciation: 'Félicitations du conseil.',
    status: 'LOCKED',
    computedAtUtc: '2026-11-25T16:30:00Z',
    subjectResults: [
      {
        id: 'sr-1',
        subjectId: 'sub-math',
        subjectCode: 'MATH',
        subjectName: 'Mathématiques',
        coefficient: 5,
        averageScore: 17.5,
        classSubjectAverage: 12.6,
        rankInSubject: 1,
        evaluationCount: 2,
        teacherAppreciation: 'Très bonne maîtrise.',
      },
      {
        id: 'sr-2',
        subjectId: 'sub-fr',
        subjectCode: 'FR',
        subjectName: 'Français',
        coefficient: 4,
        averageScore: 15.15,
        classSubjectAverage: 13.2,
        rankInSubject: 2,
        evaluationCount: 2,
        teacherAppreciation: 'Bon trimestre.',
      },
    ],
  },
  {
    id: 'rc-2',
    academicYearId: 'ay-2026',
    evaluationPeriodId: 'term-t1',
    termCode: 'T1',
    periodName: 'Trimestre 1',
    classRoomId: 'cls-3a',
    classRoomCode: '3A',
    classRoomName: '3ème A',
    studentId: 'stu-3',
    studentMatricule: 'DEMO-2026-008',
    studentFirstName: 'Oumar',
    studentLastName: 'Gueye-Démo',
    studentFullName: 'Oumar Gueye-Démo',
    studentInitials: 'OG',
    gradingScale: 20,
    generalAverage: 9.15,
    classAverage: 12.0,
    minClassAverage: 9.15,
    maxClassAverage: 15.8,
    rankInClass: 32,
    classSize: 38,
    tiedRank: false,
    totalWeightedScore: 82.35,
    totalCoefficients: 9,
    mention: 'INSUFFISANT',
    councilDecision: 'AVERTISSEMENT_TRAVAIL',
    homeroomAppreciation: 'Des lacunes à combler.',
    directorAppreciation: 'Avertissement travail.',
    status: 'VALIDATED',
    computedAtUtc: '2026-11-25T16:30:00Z',
    subjectResults: [
      {
        id: 'sr-3',
        subjectId: 'sub-math',
        subjectCode: 'MATH',
        subjectName: 'Mathématiques',
        coefficient: 5,
        averageScore: 8.75,
        classSubjectAverage: 11.8,
        rankInSubject: 31,
        evaluationCount: 2,
        teacherAppreciation: 'Notions à consolider.',
      },
    ],
  },
];

function buildPageResult(
  items: readonly StudentReportCardRecord[],
  page = 0,
  size = 8,
  totalElements = items.length,
  totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size)
): GradesPageResult {
  return {
    content: items,
    page,
    size,
    totalElements,
    totalPages,
    first: page === 0,
    last: totalPages === 0 || page >= totalPages - 1,
    kpis: SAMPLE_KPIS,
    classes: SAMPLE_CLASSES,
    subjects: SAMPLE_SUBJECTS,
    terms: DEFAULT_GRADE_TERMS,
    studentsForEntry: SAMPLE_STUDENTS_FOR_ENTRY,
  };
}

interface GradesApiStub {
  list: ReturnType<typeof vi.fn<(query: GradesListQuery) => Observable<GradesPageResult>>>;
  getReportCard: ReturnType<typeof vi.fn<(id: string) => Observable<StudentReportCardRecord>>>;
  saveEvaluationGrades: ReturnType<
    typeof vi.fn<(input: SaveEvaluationGradesInput) => Observable<SaveEvaluationGradesResult>>
  >;
  generateReportCards: ReturnType<
    typeof vi.fn<(input: GenerateReportCardsInput) => Observable<GenerateReportCardsResult>>
  >;
}

describe('GradesPageComponent', () => {
  function createApiStub(overrides?: {
    list?: (query: GradesListQuery) => Observable<GradesPageResult>;
    saveEvaluationGrades?: (
      input: SaveEvaluationGradesInput
    ) => Observable<SaveEvaluationGradesResult>;
    generateReportCards?: (
      input: GenerateReportCardsInput
    ) => Observable<GenerateReportCardsResult>;
  }): GradesApiStub {
    return {
      list: vi.fn(
        overrides?.list ?? (() => of(buildPageResult(SAMPLE_REPORT_CARDS, 0, 8, 12, 2)))
      ),
      getReportCard: vi.fn(() => of(SAMPLE_REPORT_CARDS[0])),
      saveEvaluationGrades: vi.fn(
        overrides?.saveEvaluationGrades ??
          ((input: SaveEvaluationGradesInput) =>
            of<SaveEvaluationGradesResult>({
              evaluationId: 'eval-saved-01',
              title: input.title,
              savedCount: input.entries.length,
              classAverage: 15.25,
            }))
      ),
      generateReportCards: vi.fn(
        overrides?.generateReportCards ??
          ((input: GenerateReportCardsInput) =>
            of<GenerateReportCardsResult>({
              generatedCount: 12,
              termCode: input.termCode,
              classRoomName: 'Terminale S2',
            }))
      ),
    };
  }

  async function setup(options?: {
    api?: GradesApiStub;
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
      imports: [GradesPageComponent],
      providers: [
        provideRouter([]),
        { provide: GradesApiService, useValue: api },
        { provide: ActivatedRoute, useValue: activatedRouteStub },
        {
          provide: NetworkStatusService,
          useValue: { isOnline: () => isOnline },
        },
      ],
    }).compileComponents();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(GradesPageComponent);
    const toastService = TestBed.inject(ToastService);

    return { fixture, api, router, navigateSpy, toastService, queryParamMap$ };
  }

  afterEach(() => {
    vi.useRealTimers();
    TestBed.inject(ToastService).clear();
  });

  it('uses standalone component with ChangeDetectionStrategy.OnPush', () => {
    const mirror = reflectComponentType(GradesPageComponent);
    expect(mirror?.isStandalone).toBe(true);
    const cmpDef = (GradesPageComponent as unknown as { ɵcmp?: { onPush?: boolean } }).ɵcmp;
    expect(cmpDef?.onPush).toBe(true);
    expect(ChangeDetectionStrategy.OnPush).toBeDefined();
  });

  it('renders header with strictly 1 btn-primary, 4 KPI StatCards in neutral ink, soft badges, and Lucide 1.75 icons', async () => {
    const { fixture } = await setup();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Notes et bulletins');
    expect(el.textContent).toContain('Trimestre 1 (2026–2027) • 12 élèves évalués');

    const primaryButtons = el.querySelectorAll('.btn-primary');
    expect(primaryButtons.length).toBe(1);
    expect(primaryButtons[0]?.textContent).toContain('Saisir des notes');

    const generateBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Générer bulletins')
    );
    expect(generateBtn?.classList.contains('btn-outline')).toBe(true);

    // 4 KPI cards with neutral text-base-content ink
    const kpiCards = el.querySelectorAll('app-stat-card');
    expect(kpiCards.length).toBe(4);
    expect(el.textContent).toContain('Moyenne générale');
    expect(el.textContent).toContain('13,85 / 20');
    expect(el.textContent).toContain('Taux de réussite (>= 10/20)');
    expect(el.textContent).toContain('83,3 %');
    expect(el.textContent).toContain('Notes saisies');
    expect(el.textContent).toContain('144');
    expect(el.textContent).toContain('Bulletins prêts');
    expect(el.textContent).toContain('10 / 12');

    // No solid daisyUI badges
    expect(el.querySelector('.badge-success')).toBeNull();
    expect(el.querySelector('.badge-warning')).toBeNull();
    expect(el.querySelector('.badge-error')).toBeNull();
    expect(el.querySelector('.badge-info')).toBeNull();

    // Desktop table columns
    const rows = el.querySelectorAll('table tbody tr');
    expect(rows.length).toBe(2);

    const firstRow = rows[0];
    expect(firstRow?.textContent).toContain('Aminata Diallo-Démo');
    expect(firstRow?.textContent).toContain('DEMO-2026-001');
    expect(firstRow?.textContent).toContain('Terminale S2');
    expect(firstRow?.textContent).toContain('16,45');
    expect(firstRow?.textContent).toContain('1er / 34');
    expect(firstRow?.textContent).toContain('Très bien');
    expect(firstRow?.querySelector('td:last-child')?.classList.contains('text-right')).toBe(true);
    expect(firstRow?.querySelector('td:last-child button')?.textContent).toContain('Bulletin');

    // Every Lucide SVG uses stroke-width="1.75" and shrink-0
    const lucideIcons = Array.from(el.querySelectorAll<SVGSVGElement>('svg.lucide'));
    expect(lucideIcons.length).toBeGreaterThan(0);
    for (const icon of lucideIcons) {
      expect(icon.getAttribute('stroke-width')).toBe('1.75');
      expect(icon.classList.contains('shrink-0')).toBe(true);
    }
  });

  it('hydrates and syncs URL queryParams (q debounced 300ms, class, subject, term, page) and supports reset + pagination', async () => {
    vi.useFakeTimers();
    const { fixture, api, navigateSpy } = await setup({
      initialQueryParams: {
        q: 'Aminata',
        class: 'cls-ts2',
        subject: 'sub-math',
        term: 'T2',
        page: '2',
      },
    });
    fixture.detectChanges();

    expect(api.list).toHaveBeenCalledWith(
      expect.objectContaining({
        search: 'Aminata',
        classRoomId: 'cls-ts2',
        subjectId: 'sub-math',
        term: 'T2',
        page: 1,
        size: 8,
      })
    );

    const el = fixture.nativeElement as HTMLElement;
    const searchInput = el.querySelector<HTMLInputElement>('#grades-search-input');
    expect(searchInput?.value).toBe('Aminata');

    // Reset clears all URL params
    const resetBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Réinitialiser')
    );
    expect(resetBtn).not.toBeUndefined();
    resetBtn?.click();
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: { q: null, class: null, subject: null, term: null, page: null },
        replaceUrl: true,
      })
    );

    // Debounced search triggers after 300ms
    if (searchInput) {
      searchInput.value = 'Gueye';
      searchInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }
    vi.advanceTimersByTime(299);
    expect(api.list).toHaveBeenCalledTimes(2);

    vi.advanceTimersByTime(1);
    fixture.detectChanges();
    expect(api.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ search: 'Gueye', page: 0 })
    );
    expect(navigateSpy).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({
        queryParams: expect.objectContaining({ q: 'Gueye', page: null }),
      })
    );

    // Next page updates URL page=2
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
    const pending$ = new Subject<GradesPageResult>();
    const api = createApiStub({ list: () => pending$.asObservable() });
    const { fixture } = await setup({ api });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('app-skeleton-loader').length).toBeGreaterThanOrEqual(2);
    expect(el.querySelector('table')).toBeNull();

    pending$.complete();
  });

  it('displays initial empty state when no report cards exist and no filters are active', async () => {
    const api = createApiStub({
      list: () => of(buildPageResult([], 0, 8, 0, 0)),
    });
    const { fixture } = await setup({ api });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-empty-state')).not.toBeNull();
    expect(el.textContent).toContain('Aucune note enregistrée pour ce trimestre');
  });

  it('displays filtered empty state and resets filters on click', async () => {
    const api = createApiStub({
      list: (query) =>
        query.classRoomId === 'cls-3a'
          ? of(buildPageResult([], 0, 8, 0, 0))
          : of(buildPageResult(SAMPLE_REPORT_CARDS, 0, 8, 2, 1)),
    });
    const { fixture } = await setup({
      api,
      initialQueryParams: { class: 'cls-3a' },
    });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Aucun bulletin ne correspond à vos critères');
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
                title: 'Module notes indisponible',
                status: 503,
                detail: 'Impossible de charger les moyennes du trimestre.',
                requestId: 'req-grades-retry-503',
              })
          );
        }
        return of(buildPageResult(SAMPLE_REPORT_CARDS, 0, 8, 2, 1));
      },
    });

    const { fixture } = await setup({ api });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-error-state')).not.toBeNull();
    expect(el.textContent).toContain('req-grades-retry-503');

    const retryBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Réessayer')
    );
    retryBtn?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(attempts).toBe(2);
    expect(el.querySelector('app-error-state')).toBeNull();
    expect(el.textContent).toContain('Aminata Diallo-Démo');
  });

  it('shows offline banner when network is offline', async () => {
    const { fixture } = await setup({ isOnline: false });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Mode hors-ligne détecté');
  });

  it('triggers report card generation on Générer bulletins click and displays success toast', async () => {
    const { fixture, api, toastService } = await setup();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const generateBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Générer bulletins')
    );
    generateBtn?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.generateReportCards).toHaveBeenCalledTimes(1);
    expect(toastService.toasts().length).toBe(1);
    expect(toastService.toasts()[0]?.title).toBe('Bulletins générés');
  });

  it('validates bounded scores [0..20], preserves inputs on RFC 9457 error, and saves with toast on retry', async () => {
    let saveAttempts = 0;
    const api = createApiStub({
      saveEvaluationGrades: (input) => {
        saveAttempts += 1;
        if (saveAttempts === 1) {
          return throwError(
            () =>
              new ProblemDetailError({
                type: 'urn:ecole:problem:period-closed',
                title: 'Période verrouillée',
                status: 409,
                detail: 'Le trimestre est verrouillé en écriture.',
                requestId: 'req-grade-save-409',
              })
          );
        }
        return of<SaveEvaluationGradesResult>({
          evaluationId: 'eval-saved-02',
          title: input.title,
          savedCount: input.entries.length,
          classAverage: 16.25,
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

    const scoreInput1 = el.querySelector<HTMLInputElement>('#grade-score-stu-1');
    const scoreInput2 = el.querySelector<HTMLInputElement>('#grade-score-stu-2');
    const form = el.querySelector<HTMLFormElement>('#grade-entry-form');

    expect(scoreInput1).not.toBeNull();
    expect(scoreInput2).not.toBeNull();
    expect(form).not.toBeNull();

    if (scoreInput1 && scoreInput2 && form) {
      // 1) Out-of-bounds score (> 20) rejected and input preserved
      scoreInput1.value = '22.5';
      scoreInput1.dispatchEvent(new Event('input'));
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      expect(api.saveEvaluationGrades).not.toHaveBeenCalled();
      expect(scoreInput1.value).toBe('22.5');
      expect(el.textContent).toContain('Note comprise entre 0 et 20');

      // 2) Valid bounded scores submitted -> RFC 9457 409 error preserves entered values
      scoreInput1.value = '17,5';
      scoreInput1.dispatchEvent(new Event('input'));
      scoreInput2.value = '15';
      scoreInput2.dispatchEvent(new Event('input'));
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(api.saveEvaluationGrades).toHaveBeenCalledTimes(1);
      expect(el.querySelector('app-drawer app-error-state')).not.toBeNull();
      expect(el.textContent).toContain('req-grade-save-409');
      expect(scoreInput1.value).toBe('17,5');
      expect(scoreInput2.value).toBe('15');

      // 3) Retry succeeds -> closes drawer and displays toast
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(api.saveEvaluationGrades).toHaveBeenCalledTimes(2);
      expect(el.querySelector('app-drawer [role="dialog"]')).toBeNull();
      expect(toastService.toasts().length).toBe(1);
      expect(toastService.toasts()[0]?.title).toBe('Notes sauvegardées');
    }
  });

  it('opens bulletin preview drawer with subjects, coefficients, rank, council appreciation, and download action', async () => {
    const { fixture, toastService } = await setup();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const firstBulletinBtn = el.querySelector<HTMLButtonElement>(
      'table tbody tr:first-child td:last-child button'
    );
    firstBulletinBtn?.click();
    fixture.detectChanges();

    const dialog = el.querySelector('app-drawer [role="dialog"]');
    expect(dialog).not.toBeNull();
    expect(dialog?.textContent).toContain('Aminata Diallo-Démo');
    expect(dialog?.textContent).toContain('Mathématiques');
    expect(dialog?.textContent).toContain('Coeff. 5');
    expect(dialog?.textContent).toContain('16,45');
    expect(dialog?.textContent).toContain('1er / 34');
    expect(dialog?.textContent).toContain('Félicitations');

    const downloadBtn = Array.from(dialog?.querySelectorAll<HTMLButtonElement>('button') ?? []).find(
      (b) => b.textContent?.includes('Télécharger')
    );
    expect(downloadBtn).not.toBeUndefined();
    downloadBtn?.click();
    fixture.detectChanges();

    expect(toastService.toasts().length).toBe(1);
    expect(toastService.toasts()[0]?.title).toBe('Bulletin prêt');
  });
});
