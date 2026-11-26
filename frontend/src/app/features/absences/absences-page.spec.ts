import { ChangeDetectionStrategy, reflectComponentType } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ProblemDetailError } from '../../core/models/problem-detail.model';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { AbsencesPageComponent } from './absences-page';
import { AbsencesApiService } from './data-access/absences-api.service';
import {
  AbsenceClassOption,
  AbsenceRecord,
  AbsencesKpiSummary,
  AbsencesListQuery,
  AbsencesPageResult,
  JustifyAbsenceInput,
  RollCallStudentItem,
  SubmitRollCallInput,
  SubmitRollCallResult,
  formatDakarLongDate,
} from './models/absences.model';

const SAMPLE_KPIS: AbsencesKpiSummary = {
  attendanceRatePercent: 95.4,
  attendanceRateTrendLabel: '+0,6 pt',
  attendanceRateSubLabel: 'vs semaine précédente',
  todayAbsencesCount: 3,
  todayAbsencesTrendLabel: "Aujourd'hui",
  todayAbsencesSubLabel: '7 absences cette semaine',
  unjustifiedCount: 2,
  unjustifiedTrendLabel: 'À régulariser',
  unjustifiedSubLabel: '5 déjà justifiées',
  lateCount: 3,
  lateTrendLabel: '45 min cumulées',
  lateSubLabel: 'sur les derniers appels',
};

const SAMPLE_CLASSES: readonly AbsenceClassOption[] = [
  { id: 'cls-ts2', code: 'TS2', name: 'Terminale S2' },
  { id: 'cls-3a', code: '3A', name: '3ème A' },
];

const SAMPLE_ROSTER: Readonly<Record<string, readonly RollCallStudentItem[]>> = {
  'cls-ts2': [
    {
      studentId: 'stu-1',
      studentName: 'Aminata Diallo-Démo',
      studentInitials: 'AD',
      matricule: 'DEMO-2026-001',
      classRoomId: 'cls-ts2',
      className: 'Terminale S2',
    },
    {
      studentId: 'stu-2',
      studentName: 'Khady Sall-Démo',
      studentInitials: 'KS',
      matricule: 'DEMO-2026-007',
      classRoomId: 'cls-ts2',
      className: 'Terminale S2',
    },
  ],
  'cls-3a': [
    {
      studentId: 'stu-3',
      studentName: 'Cheikh Tidiane Ndiaye-Démo',
      studentInitials: 'CN',
      matricule: 'DEMO-2026-002',
      classRoomId: 'cls-3a',
      className: '3ème A',
    },
  ],
};

const SAMPLE_RECORDS: readonly AbsenceRecord[] = [
  {
    id: 'rec-1',
    sessionId: 'sess-1',
    studentId: 'stu-2',
    studentName: 'Khady Sall-Démo',
    studentInitials: 'KS',
    matricule: 'DEMO-2026-007',
    classRoomId: 'cls-ts2',
    className: 'Terminale S2',
    sessionDate: '2026-11-26',
    recordedAtUtc: '2026-11-26T08:15:00Z',
    timeSlot: 'MORNING',
    slotLabel: 'Matin (08h–12h)',
    subjectName: 'Mathématiques',
    teacherName: 'M. Ousmane Dieng',
    rawStatus: 'ABSENT',
    status: 'UNJUSTIFIED',
    lateMinutes: null,
    justified: false,
    justificationReason: null,
    justificationDocumentRef: null,
    justifiedAtUtc: null,
    notes: 'Parent non joint',
  },
  {
    id: 'rec-2',
    sessionId: 'sess-1',
    studentId: 'stu-1',
    studentName: 'Aminata Diallo-Démo',
    studentInitials: 'AD',
    matricule: 'DEMO-2026-001',
    classRoomId: 'cls-ts2',
    className: 'Terminale S2',
    sessionDate: '2026-11-26',
    recordedAtUtc: '2026-11-26T08:22:00Z',
    timeSlot: 'MORNING',
    slotLabel: 'Matin (08h–12h)',
    subjectName: 'Mathématiques',
    teacherName: 'M. Ousmane Dieng',
    rawStatus: 'LATE',
    status: 'LATE',
    lateMinutes: 15,
    justified: false,
    justificationReason: null,
    justificationDocumentRef: null,
    justifiedAtUtc: null,
    notes: 'Embouteillage VDN',
  },
  {
    id: 'rec-3',
    sessionId: 'sess-2',
    studentId: 'stu-3',
    studentName: 'Cheikh Tidiane Ndiaye-Démo',
    studentInitials: 'CN',
    matricule: 'DEMO-2026-002',
    classRoomId: 'cls-3a',
    className: '3ème A',
    sessionDate: '2026-11-25',
    recordedAtUtc: '2026-11-25T08:10:00Z',
    timeSlot: 'MORNING',
    slotLabel: 'Matin (08h–12h)',
    subjectName: 'Français',
    teacherName: 'Mme Marième Sène',
    rawStatus: 'ABSENT',
    status: 'JUSTIFIED',
    lateMinutes: null,
    justified: true,
    justificationReason: 'Certificat médical reçu au secrétariat',
    justificationDocumentRef: 'CERT-MED-2026-041',
    justifiedAtUtc: '2026-11-25T09:40:00Z',
    notes: 'Retour confirmé',
  },
];

function buildPageResult(
  items: readonly AbsenceRecord[],
  page = 0,
  size = 8,
  totalElements = items.length,
  totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size)
): AbsencesPageResult {
  return {
    content: items,
    page,
    size,
    totalElements,
    totalPages,
    first: page === 0,
    last: totalPages === 0 || page >= totalPages - 1,
    kpis: SAMPLE_KPIS,
    availableClasses: SAMPLE_CLASSES,
    rollCallRosterByClass: SAMPLE_ROSTER,
  };
}

interface AbsencesApiStub {
  list: ReturnType<typeof vi.fn<(query: AbsencesListQuery) => Observable<AbsencesPageResult>>>;
  submitRollCall: ReturnType<
    typeof vi.fn<(input: SubmitRollCallInput) => Observable<SubmitRollCallResult>>
  >;
  justifyRecord: ReturnType<
    typeof vi.fn<(input: JustifyAbsenceInput, existing?: AbsenceRecord) => Observable<AbsenceRecord>>
  >;
}

describe('AbsencesPageComponent', () => {
  function createApiStub(overrides?: {
    list?: (query: AbsencesListQuery) => Observable<AbsencesPageResult>;
    submitRollCall?: (input: SubmitRollCallInput) => Observable<SubmitRollCallResult>;
    justifyRecord?: (
      input: JustifyAbsenceInput,
      existing?: AbsenceRecord
    ) => Observable<AbsenceRecord>;
  }): AbsencesApiStub {
    return {
      list: vi.fn(
        overrides?.list ?? (() => of(buildPageResult(SAMPLE_RECORDS, 0, 8, 10, 2)))
      ),
      submitRollCall: vi.fn(
        overrides?.submitRollCall ??
          ((input: SubmitRollCallInput) =>
            of<SubmitRollCallResult>({
              sessionId: 'sess-created-01',
              className: input.className,
              sessionDate: input.sessionDate,
              timeSlot: input.timeSlot,
              presentCount: input.entries.filter((e) => e.status === 'PRESENT').length,
              absentCount: input.entries.filter((e) => e.status === 'ABSENT').length,
              lateCount: input.entries.filter((e) => e.status === 'LATE').length,
            }))
      ),
      justifyRecord: vi.fn(
        overrides?.justifyRecord ??
          ((input: JustifyAbsenceInput, existing?: AbsenceRecord) =>
            of<AbsenceRecord>({
              ...(existing ?? SAMPLE_RECORDS[0]),
              status: 'JUSTIFIED',
              justified: true,
              justificationReason: input.reason,
              justificationDocumentRef: input.documentRef ?? null,
              justifiedAtUtc: '2026-11-26T11:00:00Z',
            }))
      ),
    };
  }

  async function setup(options?: {
    api?: AbsencesApiStub;
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
      imports: [AbsencesPageComponent],
      providers: [
        provideRouter([]),
        { provide: AbsencesApiService, useValue: api },
        { provide: ActivatedRoute, useValue: activatedRouteStub },
        {
          provide: NetworkStatusService,
          useValue: { isOnline: () => isOnline },
        },
      ],
    }).compileComponents();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(AbsencesPageComponent);
    const toastService = TestBed.inject(ToastService);

    return { fixture, api, router, navigateSpy, toastService, queryParamMap$ };
  }

  afterEach(() => {
    vi.useRealTimers();
    TestBed.inject(ToastService).clear();
  });

  it('uses standalone component with ChangeDetectionStrategy.OnPush', () => {
    const mirror = reflectComponentType(AbsencesPageComponent);
    expect(mirror?.isStandalone).toBe(true);
    const cmpDef = (AbsencesPageComponent as unknown as { ɵcmp?: { onPush?: boolean } }).ɵcmp;
    expect(cmpDef?.onPush).toBe(true);
    expect(ChangeDetectionStrategy.OnPush).toBeDefined();
  });

  it('renders header with Dakar date, strictly 1 btn-primary, 4 neutral-ink KPI StatCards, and soft StatusBadges', async () => {
    const { fixture } = await setup();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;

    // Header
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Absences & Présences');
    expect(el.textContent).toContain(formatDakarLongDate());

    const primaryButtons = el.querySelectorAll('.btn-primary');
    expect(primaryButtons.length).toBe(1);
    expect(primaryButtons[0]?.textContent).toContain("Faire l'appel");

    const exportBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Exporter')
    );
    expect(exportBtn?.classList.contains('btn-outline')).toBe(true);

    // 4 KPI cards
    const kpiCards = el.querySelectorAll('app-stat-card');
    expect(kpiCards.length).toBe(4);
    expect(el.textContent).toContain('Taux de présence');
    expect(el.textContent).toContain('95,4 %');
    expect(el.textContent).toContain('Absences du jour');
    expect(el.textContent).toContain('Non justifiées');
    expect(el.textContent).toContain('Retards');

    // Table rows & soft status badges
    const desktopRows = el.querySelectorAll('table tbody tr');
    expect(desktopRows.length).toBe(3);
    expect(el.textContent).toContain('Khady Sall-Démo');
    expect(el.textContent).toContain('Non justifiée');
    expect(el.textContent).toContain('Retard (15 min)');
    expect(el.textContent).toContain('Justifiée');
    expect(el.textContent).toContain('CERT-MED-2026-041');
  });

  it('shows skeleton loaders while loading (State 1)', async () => {
    const pending$ = new Subject<AbsencesPageResult>();
    const api = createApiStub({ list: () => pending$.asObservable() });
    const { fixture } = await setup({ api });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('app-skeleton-loader').length).toBeGreaterThanOrEqual(2);

    pending$.next(buildPageResult(SAMPLE_RECORDS));
    pending$.complete();
    fixture.detectChanges();

    expect(el.querySelectorAll('app-skeleton-loader').length).toBe(0);
    expect(el.querySelectorAll('table tbody tr').length).toBe(3);
  });

  it('shows initial empty state with CTA when no records exist and no filters are active (State 2)', async () => {
    const api = createApiStub({ list: () => of(buildPageResult([], 0, 8, 0, 0)) });
    const { fixture } = await setup({ api });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const emptyState = el.querySelector('app-empty-state');
    expect(emptyState).not.toBeNull();
    expect(emptyState?.textContent).toContain('Aucune absence ou retard enregistré');
  });

  it('shows filtered empty state with reset action when filters yield 0 matches (State 3)', async () => {
    const api = createApiStub({ list: () => of(buildPageResult([], 0, 8, 0, 0)) });
    const { fixture, navigateSpy } = await setup({
      api,
      initialQueryParams: { q: 'Introuvable', status: 'UNJUSTIFIED' },
    });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const emptyState = el.querySelector('app-empty-state');
    expect(emptyState?.textContent).toContain('Aucun signalement ne correspond à vos critères');

    const resetBtn = emptyState?.querySelector<HTMLButtonElement>('button');
    resetBtn?.click();
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: { q: null, class: null, status: null, date: null, page: null },
        replaceUrl: true,
      })
    );
  });

  it('renders RFC 9457 error state with copyable requestId and retry action (State 4)', async () => {
    let attempts = 0;
    const api = createApiStub({
      list: () => {
        attempts += 1;
        if (attempts === 1) {
          return throwError(
            () =>
              new ProblemDetailError({
                type: 'urn:ecole:problem:service-unavailable',
                title: 'Service absences indisponible',
                status: 503,
                detail: 'Impossible de charger le registre des absences.',
                requestId: 'req-abs-test-503',
              })
          );
        }
        return of(buildPageResult(SAMPLE_RECORDS));
      },
    });
    const { fixture } = await setup({ api });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const errorState = el.querySelector('app-error-state');
    expect(errorState).not.toBeNull();
    expect(errorState?.textContent).toContain('Impossible de charger le registre des absences.');
    expect(errorState?.textContent).toContain('req-abs-test-503');

    const retryBtn = Array.from(errorState!.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => b.textContent?.includes('Réessayer')
    );
    retryBtn?.click();
    fixture.detectChanges();

    expect(attempts).toBe(2);
    expect(el.querySelectorAll('table tbody tr').length).toBe(3);
  });

  it('displays offline PWA indicator when navigator is offline (State 6)', async () => {
    const { fixture } = await setup({ isOnline: false });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Mode hors-ligne détecté');
  });

  it('synchronizes search (debounced), class, status, and date filters to URL queryParams', async () => {
    vi.useFakeTimers();
    const { fixture, api, navigateSpy } = await setup();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;

    // Debounced search
    const searchInput = el.querySelector<HTMLInputElement>('#absences-search-input');
    expect(searchInput).not.toBeNull();
    searchInput!.value = 'Khady';
    searchInput!.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(350);
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({
        queryParams: expect.objectContaining({ q: 'Khady' }),
      })
    );

    // Status filter
    const statusSelect = el.querySelector<HTMLSelectElement>('#absences-status-filter');
    statusSelect!.value = 'LATE';
    statusSelect!.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({
        queryParams: expect.objectContaining({ status: 'LATE' }),
      })
    );

    // Date filter
    const dateInput = el.querySelector<HTMLInputElement>('#absences-date-filter');
    dateInput!.value = '2026-11-26';
    dateInput!.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({
        queryParams: expect.objectContaining({ date: '2026-11-26' }),
      })
    );
    expect(api.list).toHaveBeenCalledWith(
      expect.objectContaining({
        search: 'Khady',
        status: 'LATE',
        date: '2026-11-26',
      })
    );
  });

  it('opens quick roll-call drawer, toggles Présent/Absent/Retard, preserves input on RFC 9457 error, and shows toast on success', async () => {
    let failOnce = true;
    const api = createApiStub({
      submitRollCall: (input) => {
        if (failOnce) {
          failOnce = false;
          return throwError(
            () =>
              new ProblemDetailError({
                type: 'urn:ecole:problem:conflict',
                title: 'Feuille déjà ouverte',
                status: 409,
                detail: 'Une feuille d’appel existe déjà pour ce créneau.',
                requestId: 'req-rollcall-409',
              })
          );
        }
        return of({
          sessionId: 'sess-new-01',
          className: input.className,
          sessionDate: input.sessionDate,
          timeSlot: input.timeSlot,
          presentCount: 1,
          absentCount: 1,
          lateCount: 0,
        });
      },
    });
    const { fixture, toastService } = await setup({ api });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const openBtn = el.querySelector<HTMLButtonElement>('.btn-primary');
    openBtn?.click();
    fixture.detectChanges();

    expect(el.textContent).toContain('Faire l’appel rapide');
    expect(el.textContent).toContain('Aminata Diallo-Démo');
    expect(el.textContent).toContain('Khady Sall-Démo');

    // Toggle second student to Absent
    const absentButtons = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).filter(
      (b) => b.textContent?.trim() === 'Absent'
    );
    absentButtons[1]?.click();
    fixture.detectChanges();

    // Add note
    const notesTextarea = el.querySelector<HTMLTextAreaElement>('#rollcall-notes-input');
    notesTextarea!.value = 'Pluie matinale';
    notesTextarea!.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    // First submit triggers RFC 9457 error and preserves inputs
    const submitBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes("Valider l'appel")
    );
    submitBtn?.click();
    fixture.detectChanges();

    expect(el.textContent).toContain('req-rollcall-409');
    expect(el.querySelector<HTMLTextAreaElement>('#rollcall-notes-input')?.value).toBe(
      'Pluie matinale'
    );
    expect(absentButtons[1]?.getAttribute('aria-pressed')).toBe('true');

    // Second submit succeeds and triggers Toast
    submitBtn?.click();
    fixture.detectChanges();

    expect(toastService.toasts().some((t) => t.tone === 'success' && t.title === 'Appel validé')).toBe(
      true
    );
  });

  it('opens justification drawer, validates required reason, preserves input on RFC 9457 error, and updates record + toast on success', async () => {
    let failOnce = true;
    const api = createApiStub({
      justifyRecord: (input, existing) => {
        if (failOnce) {
          failOnce = false;
          return throwError(
            () =>
              new ProblemDetailError({
                type: 'urn:ecole:problem:validation-error',
                title: 'Erreur de validation',
                status: 422,
                detail: 'Pièce justificative non conforme.',
                requestId: 'req-just-422',
                errors: [{ field: 'documentRef', message: 'Format de référence invalide.' }],
              })
          );
        }
        return of({
          ...(existing ?? SAMPLE_RECORDS[0]),
          status: 'JUSTIFIED',
          justified: true,
          justificationReason: input.reason,
          justificationDocumentRef: input.documentRef ?? null,
          justifiedAtUtc: '2026-11-26T11:30:00Z',
        });
      },
    });
    const { fixture, toastService } = await setup({ api });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const justifyBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('table button')).find(
      (b) => b.textContent?.includes('Justifier')
    );
    justifyBtn?.click();
    fixture.detectChanges();

    expect(el.textContent).toContain('Justifier l’absence');

    // Empty submit triggers client validation
    const form = el.querySelector<HTMLFormElement>('#justify-absence-form');
    form?.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(el.querySelector('#justify-reason-error')?.textContent).toContain(
      'Le motif de justification est obligatoire.'
    );

    // Fill reason and documentRef
    const reasonEl = el.querySelector<HTMLTextAreaElement>('#justify-reason-input');
    const docEl = el.querySelector<HTMLInputElement>('#justify-docref-input');
    reasonEl!.value = 'Certificat médical clinique Pasteur';
    reasonEl!.dispatchEvent(new Event('input'));
    docEl!.value = 'CERT-2026-112';
    docEl!.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    // First submit returns RFC 9457 error and preserves both inputs
    form?.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(el.textContent).toContain('req-just-422');
    expect(el.textContent).toContain('Format de référence invalide.');
    expect(reasonEl?.value).toBe('Certificat médical clinique Pasteur');
    expect(docEl?.value).toBe('CERT-2026-112');

    // Second submit succeeds and shows Toast
    form?.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(
      toastService.toasts().some((t) => t.tone === 'success' && t.title === 'Absence justifiée')
    ).toBe(true);
  });
});
