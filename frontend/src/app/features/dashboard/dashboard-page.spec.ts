import { ChangeDetectionStrategy, reflectComponentType } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { MOCK_API_OPTIONS } from '../../core/api/mock-api.util';
import { ProblemDetailError } from '../../core/models/problem-detail.model';
import { ToastService } from '../../shared/components/toast/toast.service';
import { DashboardPageComponent } from './dashboard-page';
import { DASHBOARD_API, DashboardMockApi } from './data-access/dashboard-mock.api';
import { DashboardApi, DashboardPeriod, DashboardSnapshot } from './models/dashboard.model';

describe('DashboardPageComponent', () => {
  function configureDefault(apiOverride?: DashboardApi) {
    return TestBed.configureTestingModule({
      imports: [DashboardPageComponent],
      providers: [
        provideRouter([]),
        { provide: MOCK_API_OPTIONS, useValue: { delayMs: 0, scenario: 'default' } },
        ...(apiOverride ? [{ provide: DASHBOARD_API, useValue: apiOverride }] : []),
      ],
    }).compileComponents();
  }

  afterEach(() => {
    TestBed.inject(ToastService).clear();
  });

  it('uses ChangeDetectionStrategy.OnPush', () => {
    const mirror = reflectComponentType(DashboardPageComponent);
    expect(mirror?.isStandalone).toBe(true);
    const cmpDef = (DashboardPageComponent as unknown as { ɵcmp?: { onPush?: boolean } }).ɵcmp;
    expect(cmpDef?.onPush).toBe(true);
    expect(ChangeDetectionStrategy.OnPush).toBeDefined();
  });

  it('renders 4 KPI cards on a single line, SVG chart, 3 alerts, payments, and strictly 1 btn-primary', async () => {
    await configureDefault();
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;

    const kpiCards = el.querySelectorAll('app-stat-card');
    expect(kpiCards.length).toBe(4);
    expect(el.textContent).toContain('Élèves inscrits');
    expect(el.textContent).toContain('Encaissements du mois');
    expect(el.textContent).toContain('Impayés en retard');
    expect(el.textContent).toContain('Taux de présence');
    expect(el.textContent).toContain('482');
    expect(el.textContent).toContain('FCFA');
    expect(el.textContent).toContain('94,6 %');

    for (const card of Array.from(kpiCards)) {
      const valueEl = card.querySelectorAll('p')[1];
      expect(valueEl?.classList.contains('text-base-content')).toBe(true);
      expect(valueEl?.classList.contains('whitespace-nowrap')).toBe(true);
      expect(valueEl?.classList.contains('tabular-nums')).toBe(true);
    }

    // Polished payments table: discrete headers, whitespace-nowrap on Classe, soft status badges
    const paymentsSection = el.querySelector('section[aria-labelledby="recent-payments-title"]');
    const tableHeaderRow = paymentsSection?.querySelector('table thead tr');
    expect(tableHeaderRow?.classList.contains('uppercase')).toBe(true);
    expect(tableHeaderRow?.classList.contains('text-base-content/50')).toBe(true);

    const firstRowClassCell = paymentsSection?.querySelector('table tbody tr td:nth-child(2)');
    expect(firstRowClassCell?.classList.contains('whitespace-nowrap')).toBe(true);

    // Action column is right-aligned with compact harmonized buttons and zero wrapping
    const actionCells = Array.from(
      paymentsSection?.querySelectorAll<HTMLTableCellElement>('table tbody tr td:last-child') ?? []
    );
    expect(actionCells.length).toBeGreaterThan(0);
    for (const cell of actionCells) {
      expect(cell.classList.contains('text-right')).toBe(true);
      expect(cell.classList.contains('whitespace-nowrap')).toBe(true);
      const btn = cell.querySelector('button');
      expect(btn?.classList.contains('whitespace-nowrap')).toBe(true);
      expect(btn?.classList.contains('h-7')).toBe(true);
    }

    // All rendered Lucide icons use consistent stroke-width 1.75 and shrink-0
    const lucideIcons = Array.from(el.querySelectorAll<SVGSVGElement>('svg.lucide'));
    expect(lucideIcons.length).toBeGreaterThan(0);
    for (const icon of lucideIcons) {
      expect(icon.getAttribute('stroke-width')).toBe('1.75');
      expect(icon.classList.contains('shrink-0')).toBe(true);
    }

    expect(el.querySelector('.badge-success')).toBeNull();
    expect(el.querySelector('.badge-warning')).toBeNull();
    expect(el.querySelector('.badge-error')).toBeNull();

    // Strictly 1 btn-primary on the screen (+ Nouvel élève); reminder button is btn-outline
    const primaryButtons = el.querySelectorAll('.btn-primary');
    expect(primaryButtons.length).toBe(1);
    expect(primaryButtons[0]?.textContent).toContain('Nouvel élève');

    const reminderBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Lancer la relance')
    );
    expect(reminderBtn?.classList.contains('btn-outline')).toBe(true);
    expect(reminderBtn?.classList.contains('btn-primary')).toBe(false);

    // SVG collection chart rendered with accessible role="img"
    const chartSvg = el.querySelector('app-collection-chart svg[role="img"]');
    expect(chartSvg).not.toBeNull();
    expect(el.textContent).toContain('Évolution des encaissements (FCFA)');
    expect(el.textContent).toContain('Actions prioritaires');
    expect(el.textContent).toContain('Derniers paiements enregistrés');
  });

  it('reloads indicators when switching the analysis period', async () => {
    await configureDefault();
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Encaissements du mois');
    expect(el.textContent).toContain('+14 ce mois');

    const todayBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => b.textContent?.trim() === "Aujourd'hui"
    );
    expect(todayBtn).not.toBeUndefined();
    todayBtn?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(todayBtn?.getAttribute('aria-pressed')).toBe('true');
    expect(el.textContent).toContain('Encaissements du jour');
    expect(el.textContent).toContain('425\u00A0000 FCFA');
    expect(el.textContent).toContain('95,2 %');

    const termBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => b.textContent?.trim() === 'Trimestre'
    );
    termBtn?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(termBtn?.getAttribute('aria-pressed')).toBe('true');
    expect(el.textContent).toContain('Encaissements du trimestre');
    expect(el.textContent).toContain('20\u00A0150\u00A0000 FCFA');
    expect(el.textContent).toContain('+38 ce trimestre');
  });

  it('displays skeleton loaders while snapshot request is pending', async () => {
    const pending$ = new Subject<DashboardSnapshot>();
    const pendingApi: DashboardApi = {
      getSnapshot: (): Observable<DashboardSnapshot> => pending$.asObservable(),
    };

    await configureDefault(pendingApi);
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('app-skeleton-loader').length).toBe(3);
    expect(el.querySelector('app-stat-card')).toBeNull();

    pending$.complete();
  });

  it('displays RFC 9457 error state with requestId and retries on click', async () => {
    let attempts = 0;
    const flakyApi: DashboardApi = {
      getSnapshot: (period: DashboardPeriod): Observable<DashboardSnapshot> => {
        attempts += 1;
        if (attempts === 1) {
          return throwError(
            () =>
              new ProblemDetailError({
                type: 'urn:ecole:problem:dashboard-unavailable',
                title: 'Tableau de bord indisponible',
                status: 503,
                detail: 'Service momentanément saturé.',
                requestId: 'req-dash-retry-01',
              })
          );
        }
        return TestBed.inject(DashboardMockApi).getSnapshot(period);
      },
    };

    await configureDefault(flakyApi);
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-error-state')).not.toBeNull();
    expect(el.textContent).toContain('Service momentanément saturé.');
    expect(el.textContent).toContain('req-dash-retry-01');

    const retryBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Réessayer')
    );
    retryBtn?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(attempts).toBe(2);
    expect(el.querySelector('app-error-state')).toBeNull();
    expect(el.querySelectorAll('app-stat-card').length).toBe(4);
  });

  it('displays empty state when the establishment has no data yet', async () => {
    const emptyApi: DashboardApi = {
      getSnapshot: (period: DashboardPeriod) =>
        of<DashboardSnapshot>({
          period,
          generatedAtUtc: '2026-11-25T11:30:00Z',
          kpis: {
            enrolledStudents: 0,
            enrolledDeltaLabel: '0',
            enrolledComparisonLabel: '',
            collectedFcfa: 0,
            collectedTrendLabel: '0 %',
            collectedComparisonLabel: '',
            overdueFcfa: 0,
            overdueFamiliesCount: 0,
            overdueSubLabel: '',
            attendanceRatePercent: 0,
            attendanceTrendLabel: '—',
            absencesTodayCount: 0,
          },
          monthlyEvolution: [],
          evolutionNote: '',
          totalCumulativeFcfa: 0,
          priorityAlerts: [],
          recentPayments: [],
          totalTransactionsCount: 0,
        }),
    };

    await configureDefault(emptyApi);
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-empty-state')).not.toBeNull();
    expect(el.textContent).toContain('Aucune donnée enregistrée pour cette période');
    expect(el.textContent).toContain('Inscrire un élève');
  });

  it('shows filtered empty state and resets payment filter cleanly', async () => {
    const waveOnlyApi: DashboardApi = {
      getSnapshot: (period: DashboardPeriod) =>
        of<DashboardSnapshot>({
          period,
          generatedAtUtc: '2026-11-25T11:30:00Z',
          kpis: {
            enrolledStudents: 100,
            enrolledDeltaLabel: '+2',
            enrolledComparisonLabel: 'vs hier',
            collectedFcfa: 100000,
            collectedTrendLabel: '+5 %',
            collectedComparisonLabel: 'vs objectif',
            overdueFcfa: 25000,
            overdueFamiliesCount: 1,
            overdueSubLabel: 'relance',
            attendanceRatePercent: 96.0,
            attendanceTrendLabel: 'Stable',
            absencesTodayCount: 4,
          },
          monthlyEvolution: [
            { monthKey: '2026-11', monthLabel: 'Nov', targetFcfa: 100000, collectedFcfa: 100000 },
          ],
          evolutionNote: 'Note',
          totalCumulativeFcfa: 100000,
          priorityAlerts: [],
          recentPayments: [
            {
              id: 'p-1',
              receiptRef: 'REC-1',
              studentName: 'Awa Ndiaye-Démo',
              studentInitials: 'AN',
              matricule: 'DEMO-1',
              className: '3ème A',
              recordedAtUtc: '2026-11-25T10:00:00Z',
              method: 'WAVE',
              status: 'VALIDATED',
              amountFcfa: 100000,
            },
          ],
          totalTransactionsCount: 1,
        }),
    };

    await configureDefault(waveOnlyApi);
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const cashFilterBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => b.textContent?.trim() === 'Espèces'
    );
    cashFilterBtn?.click();
    fixture.detectChanges();

    expect(el.querySelector('app-empty-state')).not.toBeNull();
    expect(el.textContent).toContain('Réinitialiser les filtres');

    const resetBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => b.textContent?.trim() === 'Réinitialiser les filtres'
    );
    resetBtn?.click();
    fixture.detectChanges();

    expect(el.querySelector('app-empty-state')).toBeNull();
    expect(el.textContent).toContain('Awa Ndiaye-Démo');
  });

  it('opens the new student drawer, validates input without clearing it, and triggers toast on valid submit', async () => {
    await configureDefault();
    const fixture = TestBed.createComponent(DashboardPageComponent);
    const toastService = TestBed.inject(ToastService);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const addBtn = el.querySelector<HTMLButtonElement>('.btn-primary');
    addBtn?.click();
    fixture.detectChanges();

    expect(el.querySelector('app-drawer [role="dialog"]')).not.toBeNull();

    // Fill invalid phone and submit -> preserves input and shows French validation message
    const firstNameInput = el.querySelector<HTMLInputElement>('#quick-student-firstname');
    const lastNameInput = el.querySelector<HTMLInputElement>('#quick-student-lastname');
    const phoneInput = el.querySelector<HTMLInputElement>('#quick-student-phone');
    const form = el.querySelector<HTMLFormElement>('#quick-student-form');

    if (firstNameInput && lastNameInput && phoneInput && form) {
      firstNameInput.value = 'Mariama';
      firstNameInput.dispatchEvent(new Event('input'));
      lastNameInput.value = 'Diagne-Démo';
      lastNameInput.dispatchEvent(new Event('input'));
      phoneInput.value = '12345';
      phoneInput.dispatchEvent(new Event('input'));

      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      expect(firstNameInput.value).toBe('Mariama');
      expect(el.textContent).toContain('Format attendu : +221');

      // Now enter valid E.164 phone and submit
      phoneInput.value = '+221000000099';
      phoneInput.dispatchEvent(new Event('input'));
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      expect(toastService.toasts().length).toBe(1);
      expect(toastService.toasts()[0]?.message).toContain('Mariama Diagne-Démo');
      expect(el.querySelector('app-drawer [role="dialog"]')).toBeNull();
    }
  });
});
