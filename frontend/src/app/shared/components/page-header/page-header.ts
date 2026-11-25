import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

export interface BreadcrumbItem {
  readonly label: string;
  readonly route?: string | readonly string[];
}

/**
 * Standard page header with display title, optional subtitle, optional breadcrumbs,
 * and an action slot (keep at most one `btn-primary` per screen).
 */
@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="border-b border-base-300 pb-4">
      @if (breadcrumbs().length > 0) {
        <nav aria-label="Fil d'Ariane" class="breadcrumbs mb-2 py-0 text-xs text-base-content/70">
          <ul>
            @for (crumb of breadcrumbs(); track crumb.label; let isLast = $last) {
              <li>
                @if (crumb.route && !isLast) {
                  <a
                    [routerLink]="crumb.route"
                    class="transition-colors duration-150 ease-out hover:text-primary motion-reduce:transition-none"
                  >
                    {{ crumb.label }}
                  </a>
                } @else {
                  <span [attr.aria-current]="isLast ? 'page' : null" class="font-medium text-base-content">
                    {{ crumb.label }}
                  </span>
                }
              </li>
            }
          </ul>
        </nav>
      }

      <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div class="min-w-0">
          <h1
            class="truncate font-display text-2xl font-bold tracking-tight text-base-content"
            [title]="title()"
          >
            {{ title() }}
          </h1>
          @if (subtitle()) {
            <p class="mt-1 text-sm text-base-content/75">{{ subtitle() }}</p>
          }
        </div>

        <div class="flex shrink-0 flex-wrap items-center gap-2">
          <ng-content select="[pageHeaderActions]" />
          <ng-content />
        </div>
      </div>
    </header>
  `,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>('');
  readonly breadcrumbs = input<readonly BreadcrumbItem[]>([]);
}
