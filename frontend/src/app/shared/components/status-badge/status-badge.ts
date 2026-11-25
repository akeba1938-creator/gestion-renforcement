import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type BadgeTone = 'success' | 'warning' | 'error' | 'info' | 'neutral';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span [class]="badgeClasses()">{{ label() }}</span>
  `,
})
export class StatusBadgeComponent {
  readonly label = input.required<string>();
  readonly tone = input<BadgeTone>('neutral');

  protected readonly badgeClasses = computed(() => {
    const toneClass: Record<BadgeTone, string> = {
      success: 'badge-success',
      warning: 'badge-warning',
      error: 'badge-error',
      info: 'badge-info',
      neutral: 'badge-neutral',
    };
    return `badge badge-sm font-medium ${toneClass[this.tone()]}`;
  });
}
