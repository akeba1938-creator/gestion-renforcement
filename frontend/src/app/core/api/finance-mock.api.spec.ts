import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { FinanceMockApi } from './finance-mock.api';
import { MOCK_API_OPTIONS } from './mock-api.util';

describe('FinanceMockApi', () => {
  let api: FinanceMockApi;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: MOCK_API_OPTIONS, useValue: { delayMs: 0, scenario: 'default' } }],
    });
    api = TestBed.inject(FinanceMockApi);
  });

  it('returns Page<PaymentRecord> with integer FCFA amounts and search/sort support', async () => {
    const page = await firstValueFrom(
      api.listPayments({ page: 0, size: 10, sortBy: 'amountFcfa', sortDir: 'desc' })
    );
    expect(page.total).toBeGreaterThan(0);
    expect(page.items[0].amountFcfa).toBeGreaterThanOrEqual(page.items[1].amountFcfa);
    for (const payment of page.items) {
      expect(Number.isInteger(payment.amountFcfa)).toBe(true);
    }

    const searched = await firstValueFrom(
      api.listPayments({ page: 0, size: 10, search: 'REC-2026-0001' })
    );
    expect(searched.total).toBe(1);
  });

  it('simulates empty list and error scenarios', async () => {
    api.setScenario('empty');
    const emptyPage = await firstValueFrom(api.listPayments({ page: 0, size: 10 }));
    expect(emptyPage.items).toEqual([]);
    expect(emptyPage.total).toBe(0);

    api.setScenario('error');
    await expect(firstValueFrom(api.listPayments({ page: 0, size: 10 }))).rejects.toThrow(
      'Impossible de charger les écritures financières'
    );
  });
});
