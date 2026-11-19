import { Component, computed, input } from '@angular/core';

export type StatCardTone = 'primary' | 'secondary' | 'accent' | 'neutral';

@Component({
  selector: 'app-stat-card',
  standalone: true,
  template: `
    <article class="card bg-base-100 border border-base-300 shadow-xs">
      <div class="card-body p-4 sm:p-5 gap-1">
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
      secondary: 'text-base-content',
      accent: 'text-base-content',
      neutral: 'text-base-content',
    };
    return `text-2xl font-bold tracking-tight ${toneMap[this.tone()]}`;
  });
}
