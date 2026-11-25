import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type StatCardTone = 'primary' | 'secondary' | 'accent' | 'neutral';

@Component({
  selector: 'app-stat-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="card rounded-xl border border-base-300 bg-base-100">
      <div class="card-body gap-1 p-4 sm:p-5">
        <p class="text-sm font-medium text-base-content/75">{{ label() }}</p>
        <p [class]="valueClasses()">{{ value() }}</p>
        @if (description()) {
          <p class="text-xs text-base-content/70">{{ description() }}</p>
        }
      </div>
    </article>
  `,
})
export class StatCardComponent {
  readonly label = input.required<string>();
  readonly value = input.required<string>();
  readonly description = input<string>('');
  readonly tone = input<StatCardTone>('neutral');

  protected readonly valueClasses = computed(() => {
    const toneMap: Record<StatCardTone, string> = {
      primary: 'text-primary',
      secondary: 'text-secondary',
      accent: 'text-accent',
      neutral: 'text-base-content',
    };
    return `font-display text-2xl font-bold tracking-tight tabular-nums ${toneMap[this.tone()]}`;
  });
}
