import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
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

  it('returns a paginated Page<Student> with integer FCFA balances', async () => {
    const page = await firstValueFrom(api.list({ page: 0, size: 2 }));
    expect(page.page).toBe(0);
    expect(page.size).toBe(2);
    expect(page.items.length).toBe(2);
    expect(page.total).toBeGreaterThan(2);
    for (const student of page.items) {
      expect(Number.isInteger(student.balanceFcfa)).toBe(true);
    }
  });

  it('filters by search query and sorts by balanceFcfa descending', async () => {
    const filtered = await firstValueFrom(
      api.list({ page: 0, size: 10, search: 'Terminale', sortBy: 'balanceFcfa', sortDir: 'desc' })
    );
    expect(filtered.total).toBe(1);
    expect(filtered.items[0].className).toBe('Terminale S2');

    const sorted = await firstValueFrom(
      api.list({ page: 0, size: 10, sortBy: 'balanceFcfa', sortDir: 'desc' })
    );
    expect(sorted.items[0].balanceFcfa).toBeGreaterThanOrEqual(sorted.items[1].balanceFcfa);
  });

  it('simulates empty list, error, and delay scenarios', async () => {
    api.setScenario('empty');
    const emptyPage = await firstValueFrom(api.list({ page: 0, size: 5 }));
    expect(emptyPage.items).toEqual([]);
    expect(emptyPage.total).toBe(0);

    api.setScenario('error');
    await expect(firstValueFrom(api.list({ page: 0, size: 5 }))).rejects.toThrow(
      'Impossible de charger la liste des élèves'
    );

    api.configureMock({ scenario: 'default', delayMs: 25 });
    const startedAt = Date.now();
    const delayed = await firstValueFrom(api.list({ page: 0, size: 1 }));
    expect(Date.now() - startedAt).toBeGreaterThanOrEqual(15);
    expect(delayed.items.length).toBe(1);
  });
});
