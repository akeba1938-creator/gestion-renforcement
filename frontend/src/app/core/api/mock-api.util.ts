import { InjectionToken } from '@angular/core';
import { Observable, of, switchMap, throwError, timer } from 'rxjs';
import { Page, PageQuery } from '../models/page.model';

export type MockScenario = 'default' | 'empty' | 'error';

export interface MockApiOptions {
  delayMs: number;
  scenario: MockScenario;
}

export const MOCK_API_OPTIONS = new InjectionToken<MockApiOptions>('MOCK_API_OPTIONS', {
  providedIn: 'root',
  factory: () => ({
    delayMs: 120,
    scenario: 'default',
  }),
});

export function buildMockPage<T>(
  source: readonly T[],
  query: PageQuery,
  matchesSearch: (item: T, normalizedSearch: string) => boolean,
  comparators: Record<string, (a: T, b: T) => number>
): Page<T> {
  const safePage = Math.max(0, Math.trunc(query.page));
  const safeSize = Math.max(1, Math.min(100, Math.trunc(query.size)));
  const normalizedSearch = query.search?.trim().toLowerCase() ?? '';

  let filtered = normalizedSearch
    ? source.filter((item) => matchesSearch(item, normalizedSearch))
    : [...source];

  if (query.sortBy && comparators[query.sortBy]) {
    const comparator = comparators[query.sortBy];
    const factor = query.sortDir === 'desc' ? -1 : 1;
    filtered = [...filtered].sort((a, b) => comparator(a, b) * factor);
  }

  const start = safePage * safeSize;
  const items = filtered.slice(start, start + safeSize);

  return {
    items,
    page: safePage,
    size: safeSize,
    total: filtered.length,
  };
}

export function simulateMockRequest<T>(
  options: MockApiOptions,
  errorMessage: string,
  emptyValue: T,
  defaultProducer: () => T
): Observable<T> {
  const trigger$ = options.delayMs > 0 ? timer(options.delayMs) : of(0);

  return trigger$.pipe(
    switchMap(() => {
      if (options.scenario === 'error') {
        return throwError(() => new Error(errorMessage));
      }
      if (options.scenario === 'empty') {
        return of(emptyValue);
      }
      return of(defaultProducer());
    })
  );
}
