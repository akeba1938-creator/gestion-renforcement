import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type BadgeTone = 'success' | 'warning' | 'error' | 'info' | 'neutral';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span [class]="badgeClasses()">
      @if (showDot()) {
        <span [class]="dotClasses()" aria-hidden="true"></span>
      }
      <span>{{ label() }}</span>
    </span>
  `,
})
export class StatusBadgeComponent {
  readonly label = input.required<string>();
  readonly tone = input<BadgeTone>('neutral');
  readonly showDot = input<boolean>(true);

  protected readonly badgeClasses = computed(() => {
    const toneClass: Record<BadgeTone, string> = {
      success: 'bg-success/10 text-success border-success/20',
      warning: 'bg-warning/10 text-warning border-warning/20',
      error: 'bg-error/10 text-error border-error/20',
      info: 'bg-info/10 text-info border-info/20',
      neutral: 'bg-base-200/80 text-base-content/75 border-base-300/80',
    };
    return `inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-0.5 text-xs font-medium leading-4 whitespace-nowrap transition-colors duration-150 ease-out motion-reduce:transition-none ${toneClass[this.tone()]}`;
  });

  protected readonly dotClasses = computed(() => {
    const dotToneClass: Record<BadgeTone, string> = {
      success: 'bg-success',
      warning: 'bg-warning',
      error: 'bg-error',
      info: 'bg-info',
      neutral: 'bg-base-content/50',
    };
    return `h-1.5 w-1.5 shrink-0 rounded-full ${dotToneClass[this.tone()]}`;
  });
}
