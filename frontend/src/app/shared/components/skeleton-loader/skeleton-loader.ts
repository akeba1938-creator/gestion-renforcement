import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type SkeletonVariant = 'kpi' | 'table' | 'card';

/**
 * Geometry-preserving skeleton loader preventing layout shift (CLS)
 * across KPI rows, tables, and card grids.
 */
@Component({
  selector: 'app-skeleton-loader',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      role="status"
      aria-busy="true"
      [attr.aria-label]="ariaLabel()"
    >
      <span class="sr-only">{{ ariaLabel() }}</span>

      @if (variant() === 'kpi') {
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-hidden="true">
          @for (item of items(); track item) {
            <div class="rounded-xl border border-base-300 bg-base-100 p-4 sm:p-5">
              <div class="skeleton mb-3 h-4 w-24 motion-reduce:animate-none"></div>
              <div class="skeleton mb-2 h-8 w-32 motion-reduce:animate-none"></div>
              <div class="skeleton h-3 w-40 motion-reduce:animate-none"></div>
            </div>
          }
        </div>
      } @else if (variant() === 'table') {
        <div
          class="overflow-hidden rounded-xl border border-base-300 bg-base-100"
          aria-hidden="true"
        >
          <div class="border-b border-base-300 bg-base-200/50 px-4 py-3">
            <div class="grid gap-4" [style.grid-template-columns]="tableGridTemplate()">
              @for (col of cols(); track col) {
                <div class="skeleton h-4 w-3/4 motion-reduce:animate-none"></div>
              }
            </div>
          </div>
          <div class="divide-y divide-base-300">
            @for (row of items(); track row) {
              <div class="px-4 py-3.5">
                <div class="grid gap-4" [style.grid-template-columns]="tableGridTemplate()">
                  @for (col of cols(); track col) {
                    <div class="skeleton h-4 w-full motion-reduce:animate-none"></div>
                  }
                </div>
              </div>
            }
          </div>
        </div>
      } @else {
        <div
          class="grid grid-cols-1 gap-4"
          [class.md:grid-cols-2]="items().length > 1"
          aria-hidden="true"
        >
          @for (item of items(); track item) {
            <div class="rounded-xl border border-base-300 bg-base-100 p-6">
              <div class="skeleton mb-3 h-5 w-1/3 motion-reduce:animate-none"></div>
              <div class="skeleton mb-2 h-4 w-full motion-reduce:animate-none"></div>
              <div class="skeleton mb-4 h-4 w-5/6 motion-reduce:animate-none"></div>
              <div class="flex gap-2">
                <div class="skeleton h-8 w-24 rounded-lg motion-reduce:animate-none"></div>
                <div class="skeleton h-8 w-20 rounded-lg motion-reduce:animate-none"></div>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
})
export class SkeletonLoaderComponent {
  readonly variant = input<SkeletonVariant>('card');
  readonly count = input(4);
  readonly columns = input(4);
  readonly ariaLabel = input('Chargement en cours…');

  protected readonly items = computed(() => {
    const length = Math.max(1, Math.min(20, Math.trunc(this.count())));
    return Array.from({ length }, (_, index) => index);
  });

  protected readonly cols = computed(() => {
    const length = Math.max(1, Math.min(10, Math.trunc(this.columns())));
    return Array.from({ length }, (_, index) => index);
  });

  protected readonly tableGridTemplate = computed(
    () => `repeat(${this.cols().length}, minmax(0, 1fr))`
  );
}
