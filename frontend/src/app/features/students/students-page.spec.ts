import { ChangeDetectionStrategy, reflectComponentType } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ProblemDetailError } from '../../core/models/problem-detail.model';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { StudentsApiService } from './data-access/students-api.service';
import {
  CreateStudentInput,
  DEFAULT_STUDENT_CLASSES,
  StudentRecord,
  StudentsListQuery,
  StudentsPageResult,
} from './models/students.model';
import { StudentsPageComponent } from './students-page';

const SAMPLE_STUDENTS: readonly StudentRecord[] = [
  {
    id: 'stu-1',
    matricule: 'DEMO-2026-001',
    firstName: 'Aminata',
    lastName: 'Diallo-Démo',
    fullName: 'Aminata Diallo-Démo',
    initials: 'AD',
    gender: 'FEMALE',
    birthDate: '2009-03-14',
    admissionDate: '2026-10-01',
    className: 'Terminale S2',
    guardianName: 'Mamadou Diallo-Démo',
    guardianPhone: '+221000000001',
    email: null,
    address: 'Sacré-Cœur 3, Dakar',
    status: 'ACTIVE',
    statusReason: null,
    balanceFcfa: 0,
    financialStatus: 'UP_TO_DATE',
    createdAt: '2026-10-01T09:00:00Z',
  },
  {
    id: 'stu-2',
    matricule: 'DEMO-2026-002',
    firstName: 'Mouhamadou',
    lastName: 'Fall-Démo',
    fullName: 'Mouhamadou Fall-Démo',
    initials: 'MF',
    gender: 'MALE',
    birthDate: '2010-01-19',
    admissionDate: '2026-10-04',
    className: '1ère L',
    guardianName: 'Ibrahima Fall-Démo',
    guardianPhone: '+221000000004',
    email: null,
    address: 'Liberté 6, Dakar',
    status: 'PENDING',
    statusReason: 'Validation dossier',
    balanceFcfa: 75000,
    financialStatus: 'OVERDUE',
    createdAt: '2026-10-04T11:45:00Z',
  },
  {
    id: 'stu-3',
    matricule: 'DEMO-2026-003',
    firstName: 'Khady',
    lastName: 'Sall-Démo',
    fullName: 'Khady Sall-Démo',
    initials: 'KS',
    gender: 'FEMALE',
    birthDate: '2008-12-03',
    admissionDate: '2026-10-07',
    className: 'Terminale S2',
    guardianName: 'Awa Sall-Démo',
    guardianPhone: '+221000000007',
    email: null,
    address: 'Almadies, Dakar',
    status: 'WITHDRAWN',
    statusReason: 'Déménagement',
    balanceFcfa: 120000,
    financialStatus: 'OVERDUE',
    createdAt: '2026-10-07T09:40:00Z',
  },
];

function buildPageResult(
  items: readonly StudentRecord[],
  page = 0,
  size = 8,
  totalElements = items.length,
  totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size)
): StudentsPageResult {
  return {
    content: items,
    page,
    size,
    totalElements,
    totalPages,
    first: page === 0,
    last: totalPages === 0 || page >= totalPages - 1,
    availableClasses: DEFAULT_STUDENT_CLASSES,
  };
}

interface StudentsApiStub {
  list: ReturnType<typeof vi.fn<(query: StudentsListQuery) => Observable<StudentsPageResult>>>;
  getById: ReturnType<typeof vi.fn<(id: string) => Observable<StudentRecord>>>;
  create: ReturnType<typeof vi.fn<(input: CreateStudentInput) => Observable<StudentRecord>>>;
}

describe('StudentsPageComponent', () => {
  function createApiStub(
    listImpl?: (query: StudentsListQuery) => Observable<StudentsPageResult>,
    createImpl?: (input: CreateStudentInput) => Observable<StudentRecord>
  ): StudentsApiStub {
    return {
      list: vi.fn(
        listImpl ?? (() => of(buildPageResult(SAMPLE_STUDENTS, 0, 8, 12, 2)))
      ),
      getById: vi.fn((id: string) => of(SAMPLE_STUDENTS.find((s) => s.id === id) ?? SAMPLE_STUDENTS[0])),
      create: vi.fn(
        createImpl ??
          ((input: CreateStudentInput) =>
            of<StudentRecord>({
              id: 'stu-created-01',
              matricule: 'DEMO-2026-099',
              firstName: input.firstName,
              lastName: input.lastName,
              fullName: `${input.firstName} ${input.lastName}`,
              initials: `${input.firstName.charAt(0)}${input.lastName.charAt(0)}`.toUpperCase(),
              gender: input.gender ?? 'FEMALE',
              birthDate: input.birthDate ?? '2012-09-15',
              admissionDate: '2026-11-26',
              className: input.className,
              guardianName: input.guardianName,
              guardianPhone: input.guardianPhone,
              email: null,
              address: null,
              status: 'ACTIVE',
              statusReason: null,
              balanceFcfa: 0,
              financialStatus: 'UP_TO_DATE',
              createdAt: '2026-11-26T10:00:00Z',
            }))
      ),
    };
  }

  async function setup(options?: {
    api?: StudentsApiStub;
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
      imports: [StudentsPageComponent],
      providers: [
        provideRouter([]),
        { provide: StudentsApiService, useValue: api },
        { provide: ActivatedRoute, useValue: activatedRouteStub },
        {
          provide: NetworkStatusService,
          useValue: { isOnline: () => isOnline },
        },
      ],
    }).compileComponents();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(StudentsPageComponent);
    const toastService = TestBed.inject(ToastService);

    return { fixture, api, router, navigateSpy, toastService, queryParamMap$ };
  }

  afterEach(() => {
    vi.useRealTimers();
    TestBed.inject(ToastService).clear();
  });

  it('uses standalone component with ChangeDetectionStrategy.OnPush', () => {
    const mirror = reflectComponentType(StudentsPageComponent);
    expect(mirror?.isStandalone).toBe(true);
    const cmpDef = (StudentsPageComponent as unknown as { ɵcmp?: { onPush?: boolean } }).ɵcmp;
    expect(cmpDef?.onPush).toBe(true);
    expect(ChangeDetectionStrategy.OnPush).toBeDefined();
  });

  it('renders header with count, strictly 1 btn-primary, desktop table columns, soft badges, FCFA neutral ink, and Lucide Eye icons', async () => {
    const { fixture } = await setup();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;

    // Header: title, tabular count subtitle, Exporter outline, strictly 1 btn-primary (+ Nouvel élève)
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Élèves');
    expect(el.textContent).toContain('12 élèves enregistrés');

    const primaryButtons = el.querySelectorAll('.btn-primary');
    expect(primaryButtons.length).toBe(1);
    expect(primaryButtons[0]?.textContent).toContain('Nouvel élève');

    const exportBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Exporter')
    );
    expect(exportBtn?.classList.contains('btn-outline')).toBe(true);

    // Table headers and soft badges (no solid daisyUI badges)
    expect(el.querySelector('.badge-success')).toBeNull();
    expect(el.querySelector('.badge-warning')).toBeNull();
    expect(el.querySelector('.badge-error')).toBeNull();

    const tableRows = el.querySelectorAll('table tbody tr');
    expect(tableRows.length).toBe(3);

    // Classe whitespace-nowrap, Solde text-right tabular-nums in neutral ink, Action Voir right-aligned
    const firstRow = tableRows[0];
    expect(firstRow?.querySelector('td:nth-child(2)')?.classList.contains('whitespace-nowrap')).toBe(true);
    expect(firstRow?.querySelector('td:nth-child(5)')?.classList.contains('text-right')).toBe(true);
    expect(firstRow?.querySelector('td:nth-child(5) p')?.classList.contains('text-base-content')).toBe(true);
    expect(firstRow?.querySelector('td:nth-child(5) p')?.classList.contains('tabular-nums')).toBe(true);
    expect(firstRow?.textContent).toContain('0 FCFA');

    const actionCell = firstRow?.querySelector('td:last-child');
    expect(actionCell?.classList.contains('text-right')).toBe(true);
    expect(actionCell?.classList.contains('whitespace-nowrap')).toBe(true);
    const viewBtn = actionCell?.querySelector('button');
    expect(viewBtn?.textContent).toContain('Voir');
    expect(viewBtn?.classList.contains('whitespace-nowrap')).toBe(true);

    // Every Lucide SVG uses stroke-width="1.75" and shrink-0
    const lucideIcons = Array.from(el.querySelectorAll<SVGSVGElement>('svg.lucide'));
    expect(lucideIcons.length).toBeGreaterThan(0);
    for (const icon of lucideIcons) {
      expect(icon.getAttribute('stroke-width')).toBe('1.75');
      expect(icon.classList.contains('shrink-0')).toBe(true);
    }
  });

  it('hydrates filters and page from URL queryParams and syncs search, class, status, reset, and pagination back to URL', async () => {
    vi.useFakeTimers();
    const { fixture, api, navigateSpy } = await setup({
      initialQueryParams: { q: 'Diallo', class: 'Terminale S2', status: 'ACTIVE', page: '2' },
    });

    fixture.detectChanges();

    // Initial load uses URL queryParams (page=2 -> 0-indexed page 1)
    expect(api.list).toHaveBeenCalledWith(
      expect.objectContaining({
        search: 'Diallo',
        className: 'Terminale S2',
        status: 'ACTIVE',
        page: 1,
        size: 8,
      })
    );

    const el = fixture.nativeElement as HTMLElement;
    const searchInput = el.querySelector<HTMLInputElement>('#students-search-input');
    expect(searchInput?.value).toBe('Diallo');

    // Reset button is visible when filters are active
    const resetBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Réinitialiser')
    );
    expect(resetBtn).not.toBeUndefined();
    resetBtn?.click();
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: { q: null, class: null, status: null, page: null },
        replaceUrl: true,
      })
    );
    expect(api.list).toHaveBeenLastCalledWith(
      expect.objectContaining({
        search: undefined,
        className: undefined,
        status: 'ALL',
        page: 0,
      })
    );

    // Debounced search syncs queryParams after 300ms
    if (searchInput) {
      searchInput.value = 'Fall';
      searchInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }
    vi.advanceTimersByTime(299);
    expect(api.list).toHaveBeenCalledTimes(2);

    vi.advanceTimersByTime(1);
    fixture.detectChanges();
    expect(api.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ search: 'Fall', page: 0 })
    );
    expect(navigateSpy).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({
        queryParams: expect.objectContaining({ q: 'Fall', page: null }),
      })
    );

    // Pagination "Suivant" advances to page 1 (URL page=2)
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
    const pending$ = new Subject<StudentsPageResult>();
    const api = createApiStub(() => pending$.asObservable());
    const { fixture } = await setup({ api });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('app-skeleton-loader').length).toBeGreaterThan(0);
    expect(el.querySelector('table')).toBeNull();

    pending$.complete();
  });

  it('displays initial empty state when no students exist and no filters are active', async () => {
    const api = createApiStub(() => of(buildPageResult([], 0, 8, 0, 0)));
    const { fixture } = await setup({ api });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-empty-state')).not.toBeNull();
    expect(el.textContent).toContain('Aucun élève enregistré');
    expect(el.textContent).toContain('Inscrire un élève');
  });

  it('displays filtered empty state and resets filters on click', async () => {
    const api = createApiStub((query) =>
      query.status === 'WITHDRAWN'
        ? of(buildPageResult([], 0, 8, 0, 0))
        : of(buildPageResult(SAMPLE_STUDENTS, 0, 8, 3, 1))
    );
    const { fixture } = await setup({
      api,
      initialQueryParams: { status: 'WITHDRAWN' },
    });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-empty-state')).not.toBeNull();
    expect(el.textContent).toContain('Aucun élève ne correspond à vos critères');
    expect(el.textContent).toContain('Réinitialiser les filtres');

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
    const api = createApiStub(() => {
      attempts += 1;
      if (attempts === 1) {
        return throwError(
          () =>
            new ProblemDetailError({
              type: 'urn:ecole:problem:service-unavailable',
              title: 'Répertoire indisponible',
              status: 503,
              detail: 'Impossible de joindre le service élèves.',
              requestId: 'req-stu-retry-503',
            })
        );
      }
      return of(buildPageResult(SAMPLE_STUDENTS, 0, 8, 3, 1));
    });

    const { fixture } = await setup({ api });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-error-state')).not.toBeNull();
    expect(el.textContent).toContain('Impossible de joindre le service élèves.');
    expect(el.textContent).toContain('req-stu-retry-503');

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

  it('validates +221 phone in create drawer, preserves input on validation and RFC 9457 API errors, then creates with toast', async () => {
    let createAttempts = 0;
    const api = createApiStub(undefined, (input) => {
      createAttempts += 1;
      if (createAttempts === 1) {
        return throwError(
          () =>
            new ProblemDetailError({
              type: 'urn:ecole:problem:conflict',
              title: 'Doublon détecté',
              status: 409,
              detail: 'Un élève similaire existe déjà pour ce numéro.',
              requestId: 'req-create-409',
            })
        );
      }
      return of<StudentRecord>({
        id: 'stu-new-01',
        matricule: '2026-0150',
        firstName: input.firstName,
        lastName: input.lastName,
        fullName: `${input.firstName} ${input.lastName}`,
        initials: 'MD',
        gender: input.gender ?? 'FEMALE',
        birthDate: input.birthDate ?? '2012-09-15',
        admissionDate: '2026-11-26',
        className: input.className,
        guardianName: input.guardianName,
        guardianPhone: input.guardianPhone,
        email: null,
        address: null,
        status: 'ACTIVE',
        statusReason: null,
        balanceFcfa: 0,
        financialStatus: 'UP_TO_DATE',
        createdAt: '2026-11-26T10:00:00Z',
      });
    });

    const { fixture, toastService } = await setup({ api });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    el.querySelector<HTMLButtonElement>('.btn-primary')?.click();
    fixture.detectChanges();

    expect(el.querySelector('app-drawer [role="dialog"]')).not.toBeNull();

    const firstNameInput = el.querySelector<HTMLInputElement>('#student-first-name');
    const lastNameInput = el.querySelector<HTMLInputElement>('#student-last-name');
    const guardianNameInput = el.querySelector<HTMLInputElement>('#student-guardian-name');
    const phoneInput = el.querySelector<HTMLInputElement>('#student-guardian-phone');
    const form = el.querySelector<HTMLFormElement>('#create-student-form');

    expect(firstNameInput).not.toBeNull();
    expect(lastNameInput).not.toBeNull();
    expect(guardianNameInput).not.toBeNull();
    expect(phoneInput).not.toBeNull();
    expect(form).not.toBeNull();

    if (firstNameInput && lastNameInput && guardianNameInput && phoneInput && form) {
      // 1) Invalid E.164 phone -> preserves input and shows validation message
      firstNameInput.value = 'Mariama';
      firstNameInput.dispatchEvent(new Event('input'));
      lastNameInput.value = 'Diagne-Démo';
      lastNameInput.dispatchEvent(new Event('input'));
      guardianNameInput.value = 'Samba Diagne-Démo';
      guardianNameInput.dispatchEvent(new Event('input'));
      phoneInput.value = '770000001';
      phoneInput.dispatchEvent(new Event('input'));

      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      expect(api.create).not.toHaveBeenCalled();
      expect(firstNameInput.value).toBe('Mariama');
      expect(guardianNameInput.value).toBe('Samba Diagne-Démo');
      expect(el.textContent).toContain('Format E.164 requis : +221');

      // 2) Valid E.164 phone -> first API call returns RFC 9457 409 -> preserves form values & displays error
      phoneInput.value = '+221770000099';
      phoneInput.dispatchEvent(new Event('input'));
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(api.create).toHaveBeenCalledTimes(1);
      expect(el.querySelector('app-drawer app-error-state')).not.toBeNull();
      expect(el.textContent).toContain('req-create-409');
      expect(firstNameInput.value).toBe('Mariama');
      expect(phoneInput.value).toBe('+221770000099');

      // 3) Retry submit -> succeeds, closes drawer, shows toast
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(api.create).toHaveBeenCalledTimes(2);
      expect(el.querySelector('app-drawer [role="dialog"]')).toBeNull();
      expect(toastService.toasts().length).toBe(1);
      expect(toastService.toasts()[0]?.message).toContain('Mariama Diagne-Démo');
    }
  });

  it('opens student detail drawer when clicking Voir and displays class, guardian, and financial status', async () => {
    const { fixture } = await setup();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const viewBtn = el.querySelector<HTMLButtonElement>(
      'table tbody tr:nth-child(2) td:last-child button'
    );
    expect(viewBtn).not.toBeNull();
    viewBtn?.click();
    fixture.detectChanges();

    const dialog = el.querySelector('app-drawer [role="dialog"]');
    expect(dialog).not.toBeNull();
    expect(dialog?.textContent).toContain('Mouhamadou Fall-Démo');
    expect(dialog?.textContent).toContain('DEMO-2026-002');
    expect(dialog?.textContent).toContain('1ère L');
    expect(dialog?.textContent).toContain('Ibrahima Fall-Démo');
    expect(dialog?.textContent).toContain('+221000000004');
    expect(dialog?.textContent).toContain('75\u00A0000 FCFA');
    expect(dialog?.textContent).toContain('Impayé en retard');
  });
});
