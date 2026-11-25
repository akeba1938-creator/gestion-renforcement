import { CdkTrapFocus } from '@angular/cdk/a11y';
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
} from '@angular/core';
import { LucideX } from '@lucide/angular';

export type DrawerSide = 'right' | 'left';
export type DrawerSize = 'sm' | 'md' | 'lg';

let nextDrawerId = 0;

/**
 * Accessible slide-over drawer using Angular CDK FocusTrap, Escape dismissal,
 * and automatic focus restoration to the triggering element on close.
 */
@Component({
  selector: 'app-drawer',
  standalone: true,
  imports: [CdkTrapFocus, LucideX],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(document:keydown.escape)': 'onEscapeKey($event)',
  },
  template: `
    @if (open()) {
      <div
        class="fixed inset-0 z-50 flex bg-neutral/50 transition-opacity duration-200 ease-out motion-reduce:transition-none"
        [class.justify-end]="side() === 'right'"
        [class.justify-start]="side() === 'left'"
        (click)="onBackdropClick()"
      >
        <section
          role="dialog"
          aria-modal="true"
          [attr.aria-labelledby]="titleId"
          [cdkTrapFocus]="true"
          [cdkTrapFocusAutoCapture]="true"
          [class]="panelClasses()"
          (click)="$event.stopPropagation()"
        >
          <header class="flex items-start justify-between gap-4 border-b border-base-300 px-6 py-4">
            <div class="min-w-0">
              <h2
                [id]="titleId"
                class="truncate font-display text-lg font-semibold tracking-tight text-base-content"
                [title]="title()"
              >
                {{ title() }}
              </h2>
              @if (subtitle()) {
                <p class="mt-0.5 text-sm text-base-content/75">{{ subtitle() }}</p>
              }
            </div>

            <button
              type="button"
              class="btn btn-ghost btn-square min-h-11 min-w-11 shrink-0 rounded-lg"
              aria-label="Fermer le panneau"
              [disabled]="closeDisabled()"
              (click)="requestClose()"
            >
              <svg lucideX class="h-5 w-5" aria-hidden="true"></svg>
            </button>
          </header>

          <div class="flex-1 overflow-y-auto p-6">
            <ng-content />
          </div>

          <div class="border-t border-base-300 px-6 py-4 empty:hidden">
            <ng-content select="[drawerFooter]" />
          </div>
        </section>
      </div>
    }
  `,
})
export class DrawerComponent {
  private readonly document = inject(DOCUMENT);
  private previouslyFocusedElement: HTMLElement | null = null;
  private wasOpen = false;

  protected readonly titleId = `drawer-title-${++nextDrawerId}`;

  readonly open = input(false);
  readonly title = input.required<string>();
  readonly subtitle = input<string>('');
  readonly side = input<DrawerSide>('right');
  readonly size = input<DrawerSize>('md');
  readonly closeOnBackdrop = input(true);
  readonly closeDisabled = input(false);

  readonly closed = output<void>();

  protected readonly panelClasses = computed(() => {
    const sizeClasses: Record<DrawerSize, string> = {
      sm: 'max-w-sm',
      md: 'max-w-lg',
      lg: 'max-w-2xl',
    };
    const sideClasses =
      this.side() === 'right'
        ? 'rounded-l-xl border-l border-base-300'
        : 'rounded-r-xl border-r border-base-300';
    return `flex h-full w-full flex-col bg-base-100 text-base-content transition-transform duration-200 ease-out motion-reduce:transition-none ${sizeClasses[this.size()]} ${sideClasses}`;
  });

  constructor() {
    effect(() => {
      const isOpen = this.open();
      if (isOpen && !this.wasOpen) {
        const active = this.document.activeElement;
        this.previouslyFocusedElement = active instanceof HTMLElement ? active : null;
      } else if (!isOpen && this.wasOpen) {
        this.restoreFocus();
      }
      this.wasOpen = isOpen;
    });
  }

  protected requestClose(): void {
    if (!this.closeDisabled()) {
      this.closed.emit();
    }
  }

  protected onBackdropClick(): void {
    if (this.closeOnBackdrop()) {
      this.requestClose();
    }
  }

  protected onEscapeKey(event: Event): void {
    if (this.open() && !this.closeDisabled()) {
      event.preventDefault();
      this.closed.emit();
    }
  }

  private restoreFocus(): void {
    const target = this.previouslyFocusedElement;
    this.previouslyFocusedElement = null;
    if (target && target.isConnected) {
      target.focus();
    }
  }
}
