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
import { LucideCircleAlert, LucideX } from '@lucide/angular';

export type ConfirmDialogTone = 'primary' | 'destructive';

let nextConfirmDialogId = 0;

/**
 * Accessible confirmation modal using Angular CDK FocusTrap, Escape dismissal,
 * focus restoration on close, and anti-double-click loading state.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CdkTrapFocus, LucideCircleAlert, LucideX],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(document:keydown.escape)': 'onEscapeKey($event)',
  },
  template: `
    @if (open()) {
      <div
        class="fixed inset-0 z-50 flex items-center justify-center bg-neutral/50 p-4 transition-opacity duration-200 ease-out motion-reduce:transition-none"
        (click)="onBackdropClick()"
      >
        <div
          role="dialog"
          aria-modal="true"
          [attr.aria-labelledby]="titleId"
          [attr.aria-describedby]="descriptionId"
          [cdkTrapFocus]="true"
          [cdkTrapFocusAutoCapture]="true"
          class="w-full max-w-md rounded-xl border border-base-300 bg-base-100 p-6 text-base-content transition-transform duration-200 ease-out motion-reduce:transition-none"
          (click)="$event.stopPropagation()"
        >
          <div class="flex items-start justify-between gap-4">
            <div class="flex items-start gap-3">
              @if (tone() === 'destructive') {
                <span
                  class="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-error/10 text-error"
                  aria-hidden="true"
                >
                  <svg lucideCircleAlert class="h-5 w-5"></svg>
                </span>
              }
              <div class="min-w-0">
                <h2
                  [id]="titleId"
                  class="font-display text-lg font-semibold tracking-tight text-base-content"
                >
                  {{ title() }}
                </h2>
                <p [id]="descriptionId" class="mt-2 text-sm text-base-content/80">
                  {{ message() }}
                </p>
              </div>
            </div>

            <button
              type="button"
              class="btn btn-ghost btn-sm btn-square shrink-0 rounded-lg"
              aria-label="Fermer la fenêtre de confirmation"
              [disabled]="loading()"
              (click)="onCancel()"
            >
              <svg lucideX class="h-4 w-4" aria-hidden="true"></svg>
            </button>
          </div>

          <div class="mt-6 flex flex-wrap justify-end gap-2">
            <button
              type="button"
              class="btn btn-ghost rounded-lg"
              [disabled]="loading()"
              (click)="onCancel()"
            >
              {{ cancelLabel() }}
            </button>
            <button
              type="button"
              [class]="confirmButtonClasses()"
              [disabled]="loading()"
              [attr.aria-busy]="loading()"
              (click)="onConfirm()"
            >
              @if (loading()) {
                <span class="loading loading-spinner loading-xs" aria-hidden="true"></span>
              }
              {{ confirmLabel() }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class ConfirmDialogComponent {
  private readonly document = inject(DOCUMENT);
  private previouslyFocusedElement: HTMLElement | null = null;
  private wasOpen = false;

  protected readonly instanceId = ++nextConfirmDialogId;
  protected readonly titleId = `confirm-dialog-title-${this.instanceId}`;
  protected readonly descriptionId = `confirm-dialog-desc-${this.instanceId}`;

  readonly open = input(false);
  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly confirmLabel = input('Confirmer');
  readonly cancelLabel = input('Annuler');
  readonly tone = input<ConfirmDialogTone>('primary');
  readonly loading = input(false);

  readonly confirm = output<void>();
  readonly cancel = output<void>();

  protected readonly confirmButtonClasses = computed(() =>
    this.tone() === 'destructive' ? 'btn btn-error rounded-lg' : 'btn btn-primary rounded-lg'
  );

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

  protected onConfirm(): void {
    if (!this.loading()) {
      this.confirm.emit();
    }
  }

  protected onCancel(): void {
    if (!this.loading()) {
      this.cancel.emit();
    }
  }

  protected onBackdropClick(): void {
    this.onCancel();
  }

  protected onEscapeKey(event: Event): void {
    if (this.open() && !this.loading()) {
      event.preventDefault();
      this.cancel.emit();
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
