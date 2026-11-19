import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { vi } from 'vitest';
import { MOCK_API_OPTIONS } from './mock-api.util';
import { StudentMockApi } from './student-mock.api';

describe('StudentMockApi', () => {
  let api: StudentMockApi;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: MOCK_API_OPTIONS, useValue: { delayMs: 0, scenario: 'default' } }],
    });
    api = TestBed.inject(StudentMockApi);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns a paginated Page<Student> with clearly fictional phone numbers and no finance fields', async () => {
    const page = await firstValueFrom(api.list({ page: 0, size: 2 }));
    expect(page.page).toBe(0);
    expect(page.size).toBe(2);
    expect(page.items.length).toBe(2);
    expect(page.total).toBeGreaterThan(2);

    for (const student of page.items) {
      expect(student.guardianPhone).toMatch(/^\+22100000000\d$/);
      expect('balanceFcfa' in student).toBe(false);
      expect('paymentStatus' in student).toBe(false);
    }
  });

  it('filters by search query and sorts by lastName descending', async () => {
    const filtered = await firstValueFrom(
      api.list({ page: 0, size: 10, search: 'Terminale', sortBy: 'lastName', sortDir: 'asc' })
    );
    expect(filtered.total).toBe(1);
    expect(filtered.items[0].className).toBe('Terminale S2');

    const sorted = await firstValueFrom(
      api.list({ page: 0, size: 10, sortBy: 'lastName', sortDir: 'desc' })
    );
    expect(sorted.items[0].lastName.localeCompare(sorted.items[1].lastName, 'fr')).toBeGreaterThanOrEqual(
      0
    );
  });

  it('simulates empty list, error, and deterministic delay with fake timers', async () => {
    api.setScenario('empty');
    const emptyPage = await firstValueFrom(api.list({ page: 0, size: 5 }));
    expect(emptyPage.items).toEqual([]);
    expect(emptyPage.total).toBe(0);

    api.setScenario('error');
    await expect(firstValueFrom(api.list({ page: 0, size: 5 }))).rejects.toThrow(
      'Impossible de charger la liste des élèves'
    );

    vi.useFakeTimers();
    api.configureMock({ scenario: 'default', delayMs: 120 });
    let receivedCount = 0;

    api.list({ page: 0, size: 1 }).subscribe((page) => {
      receivedCount = page.items.length;
    });

    vi.advanceTimersByTime(119);
    expect(receivedCount).toBe(0);

    vi.advanceTimersByTime(1);
    expect(receivedCount).toBe(1);
  });
});
