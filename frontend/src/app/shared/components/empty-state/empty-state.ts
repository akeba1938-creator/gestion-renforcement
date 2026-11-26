import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import {
  LucideFileText,
  LucideInbox,
  LucideSearchX,
  LucideUsers,
  LucideWallet,
} from '@lucide/angular';

export type EmptyStateVariant = 'empty' | 'filtered';
export type EmptyStateIcon = 'inbox' | 'search' | 'users' | 'wallet' | 'file';

/**
 * Empty state supporting both "no data yet" (primary CTA) and
 * "no match after filtering" ("Réinitialiser les filtres" secondary action).
 */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [LucideFileText, LucideInbox, LucideSearchX, LucideUsers, LucideWallet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="flex flex-col items-center justify-center rounded-xl border border-base-300 bg-base-100 px-6 py-10 text-center"
    >
      <div
        class="mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-base-200 text-base-content/70"
        aria-hidden="true"
      >
        @switch (resolvedIcon()) {
          @case ('search') {
            <svg lucideSearchX [strokeWidth]="1.75" class="h-6 w-6 shrink-0"></svg>
          }
          @case ('users') {
            <svg lucideUsers [strokeWidth]="1.75" class="h-6 w-6 shrink-0"></svg>
          }
          @case ('wallet') {
            <svg lucideWallet [strokeWidth]="1.75" class="h-6 w-6 shrink-0"></svg>
          }
          @case ('file') {
            <svg lucideFileText [strokeWidth]="1.75" class="h-6 w-6 shrink-0"></svg>
          }
          @default {
            <svg lucideInbox [strokeWidth]="1.75" class="h-6 w-6 shrink-0"></svg>
          }
        }
      </div>

      <h2 class="font-display text-base font-semibold tracking-tight text-base-content">
        {{ title() }}
      </h2>

      @if (description()) {
        <p class="mt-1 max-w-md text-sm text-base-content/75">
          {{ description() }}
        </p>
      }

      @if (resolvedActionLabel()) {
        <button
          type="button"
          [class]="actionButtonClasses()"
          [disabled]="actionDisabled()"
          (click)="action.emit()"
        >
          {{ resolvedActionLabel() }}
        </button>
      }
    </div>
  `,
})
export class EmptyStateComponent {
  readonly title = input.required<string>();
  readonly description = input<string>('');
  readonly variant = input<EmptyStateVariant>('empty');
  readonly icon = input<EmptyStateIcon | null>(null);
  readonly actionLabel = input<string>('');
  readonly actionDisabled = input(false);

  readonly action = output<void>();

  protected readonly resolvedIcon = computed<EmptyStateIcon>(() => {
    const explicit = this.icon();
    if (explicit) {
      return explicit;
    }
    return this.variant() === 'filtered' ? 'search' : 'inbox';
  });

  protected readonly resolvedActionLabel = computed(() => {
    const label = this.actionLabel().trim();
    if (label) {
      return label;
    }
    return this.variant() === 'filtered' ? 'Réinitialiser les filtres' : '';
  });

  protected readonly actionButtonClasses = computed(() =>
    this.variant() === 'filtered'
      ? 'btn btn-outline btn-sm mt-5 rounded-lg'
      : 'btn btn-primary btn-sm mt-5 rounded-lg'
  );
}
