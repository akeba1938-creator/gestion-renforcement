import { Component, computed, input, output } from '@angular/core';

@Component({
  selector: 'app-data-table',
  standalone: true,
  template: `
    <div class="card bg-base-100 border border-base-300 shadow-xs">
      @if (loading()) {
        <div class="flex items-center justify-center gap-3 p-8" role="status" aria-live="polite">
          <span class="loading loading-spinner loading-md text-primary" aria-hidden="true"></span>
          <span class="text-sm text-base-content/80">Chargement en cours…</span>
        </div>
      } @else if (errorMessage()) {
        <div class="p-4">
          <div class="alert alert-error" role="alert">
            <span>{{ errorMessage() }}</span>
          </div>
        </div>
      } @else if (empty()) {
        <div class="p-8 text-center text-sm text-base-content/75">
          {{ emptyMessage() }}
        </div>
      } @else {
        <div class="overflow-x-auto">
          <ng-content />
        </div>
        @if (total() > size()) {
          <div
            class="flex flex-wrap items-center justify-between gap-2 border-t border-base-300 px-4 py-3 text-sm"
          >
            <span class="text-base-content/75">
              Page {{ page() + 1 }} sur {{ totalPages() }} ({{ total() }} éléments)
            </span>
            <div class="join">
              <button
                type="button"
                class="btn btn-sm join-item"
                [disabled]="page() <= 0"
                (click)="pageChange.emit(page() - 1)"
              >
                Précédent
              </button>
              <button
                type="button"
                class="btn btn-sm join-item"
                [disabled]="page() + 1 >= totalPages()"
                (click)="pageChange.emit(page() + 1)"
              >
                Suivant
              </button>
            </div>
          </div>
        }
      }
    </div>
  `,
})
export class DataTableComponent {
  readonly loading = input(false);
  readonly empty = input(false);
  readonly errorMessage = input<string | null>(null);
  readonly emptyMessage = input('Aucune donnée à afficher.');
  readonly page = input(0);
  readonly size = input(10);
  readonly total = input(0);
  readonly pageChange = output<number>();

  protected readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.total() / Math.max(1, this.size())))
  );
}
