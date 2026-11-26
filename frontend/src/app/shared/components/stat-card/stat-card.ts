import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  LucideCalendarCheck,
  LucideCircleAlert,
  LucideClock,
  LucideTrendingUp,
  LucideUsers,
  LucideWallet,
} from '@lucide/angular';

export type StatCardTone =
  | 'primary'
  | 'secondary'
  | 'accent'
  | 'neutral'
  | 'success'
  | 'warning'
  | 'error'
  | 'info';
export type StatCardTrendTone = 'success' | 'warning' | 'error' | 'info' | 'neutral';
export type StatCardIcon = 'users' | 'wallet' | 'alert' | 'attendance' | 'trending' | 'clock';

@Component({
  selector: 'app-stat-card',
  standalone: true,
  imports: [
    LucideCalendarCheck,
    LucideCircleAlert,
    LucideClock,
    LucideTrendingUp,
    LucideUsers,
    LucideWallet,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article
      class="flex h-full flex-col justify-between gap-3 rounded-xl border border-base-300/80 bg-base-100 p-4 shadow-xs transition-colors duration-150 ease-out hover:border-base-300 motion-reduce:transition-none sm:p-5"
    >
      <div class="flex items-start justify-between gap-3">
        <p class="truncate pt-0.5 text-xs font-medium text-base-content/60" [title]="label()">
          {{ label() }}
        </p>
        @if (icon(); as iconName) {
          <span [class]="iconContainerClasses()" aria-hidden="true">
            @switch (iconName) {
              @case ('users') {
                <svg lucideUsers [strokeWidth]="1.75" class="h-4 w-4 shrink-0"></svg>
              }
              @case ('wallet') {
                <svg lucideWallet [strokeWidth]="1.75" class="h-4 w-4 shrink-0"></svg>
              }
              @case ('alert') {
                <svg lucideCircleAlert [strokeWidth]="1.75" class="h-4 w-4 shrink-0"></svg>
              }
              @case ('attendance') {
                <svg lucideCalendarCheck [strokeWidth]="1.75" class="h-4 w-4 shrink-0"></svg>
              }
              @case ('trending') {
                <svg lucideTrendingUp [strokeWidth]="1.75" class="h-4 w-4 shrink-0"></svg>
              }
              @case ('clock') {
                <svg lucideClock [strokeWidth]="1.75" class="h-4 w-4 shrink-0"></svg>
              }
            }
          </span>
        }
      </div>

      <p [class]="valueClasses" [title]="value()">{{ value() }}</p>

      @if (trend() || description()) {
        <div class="flex flex-wrap items-center gap-2 text-xs">
          @if (trend()) {
            <span [class]="trendClasses()">{{ trend() }}</span>
          }
          @if (description()) {
            <span class="truncate text-base-content/60" [title]="description()">
              {{ description() }}
            </span>
          }
        </div>
      }
    </article>
  `,
})
export class StatCardComponent {
  readonly label = input.required<string>();
  readonly value = input.required<string>();
  readonly description = input<string>('');
  readonly tone = input<StatCardTone>('neutral');
  readonly icon = input<StatCardIcon | null>(null);
  readonly trend = input<string>('');
  readonly trendTone = input<StatCardTrendTone>('neutral');

  /**
   * Neutral ink rule: large KPI numbers and FCFA amounts always render in base-content,
   * never loud semantic colors.
   */
  protected readonly valueClasses =
    'font-display text-xl font-bold tracking-tight text-base-content whitespace-nowrap tabular-nums xl:text-2xl';

  protected readonly iconContainerClasses = computed(() => {
    const toneMap: Record<StatCardTone, string> = {
      primary: 'bg-primary/10 text-primary border-primary/20',
      secondary: 'bg-secondary/10 text-secondary border-secondary/20',
      accent: 'bg-warning/10 text-warning border-warning/20',
      success: 'bg-success/10 text-success border-success/20',
      warning: 'bg-warning/10 text-warning border-warning/20',
      error: 'bg-error/10 text-error border-error/20',
      info: 'bg-info/10 text-info border-info/20',
      neutral: 'bg-base-200/80 text-base-content/70 border-base-300/80',
    };
    return `flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border ${toneMap[this.tone()]}`;
  });

  protected readonly trendClasses = computed(() => {
    const toneMap: Record<StatCardTrendTone, string> = {
      success: 'bg-success/10 text-success border-success/20',
      warning: 'bg-warning/10 text-warning border-warning/20',
      error: 'bg-error/10 text-error border-error/20',
      info: 'bg-info/10 text-info border-info/20',
      neutral: 'bg-base-200/80 text-base-content/75 border-base-300/80',
    };
    return `inline-flex items-center rounded-lg border px-2 py-0.5 text-xs font-medium whitespace-nowrap tabular-nums ${toneMap[this.trendTone()]}`;
  });
}
