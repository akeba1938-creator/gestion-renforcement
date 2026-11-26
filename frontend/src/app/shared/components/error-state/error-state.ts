import { Clipboard } from '@angular/cdk/clipboard';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { LucideCheck, LucideCircleAlert, LucideCopy, LucideRefreshCw } from '@lucide/angular';
import { ProblemDetail } from '../../../core/models/problem-detail.model';

/**
 * Accessible RFC 9457 error state with clear French messaging, retry action,
 * and copyable `requestId` for support traceability.
 */
@Component({
  selector: 'app-error-state',
  standalone: true,
  imports: [LucideCheck, LucideCircleAlert, LucideCopy, LucideRefreshCw],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      role="alert"
      class="rounded-xl border border-error/40 bg-base-100 p-6 text-base-content"
    >
      <div class="flex items-start gap-4">
        <span
          class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-error/10 text-error"
          aria-hidden="true"
        >
          <svg lucideCircleAlert [strokeWidth]="1.75" class="h-5 w-5 shrink-0"></svg>
        </span>

        <div class="min-w-0 flex-1 space-y-2">
          <h2 class="font-display text-base font-semibold tracking-tight text-base-content">
            {{ resolvedTitle() }}
          </h2>
          <p class="text-sm text-base-content/80">
            {{ resolvedMessage() }}
          </p>

          @if (fieldErrors().length > 0) {
            <ul class="list-disc space-y-1 pl-5 text-sm text-error">
              @for (item of fieldErrors(); track item.field + ':' + item.message) {
                <li>
                  <span class="font-medium">{{ item.field }}</span> : {{ item.message }}
                </li>
              }
            </ul>
          }

          @if (resolvedRequestId(); as reqId) {
            <div class="flex flex-wrap items-center gap-2 pt-1 text-xs text-base-content/75">
              <span>Référence d'incident :</span>
              <code class="rounded-lg border border-base-300 bg-base-200 px-2 py-0.5 font-mono text-xs text-base-content">
                {{ reqId }}
              </code>
              <button
                type="button"
                class="btn btn-ghost btn-xs gap-1 rounded-lg"
                [attr.aria-label]="copyButtonLabel()"
                (click)="copyRequestId(reqId)"
              >
                @if (copied()) {
                  <svg lucideCheck [strokeWidth]="1.75" class="h-3.5 w-3.5 shrink-0 text-success" aria-hidden="true"></svg>
                  <span class="text-success">Copié</span>
                } @else {
                  <svg lucideCopy [strokeWidth]="1.75" class="h-3.5 w-3.5 shrink-0" aria-hidden="true"></svg>
                  <span>Copier</span>
                }
              </button>
              <span class="sr-only" aria-live="polite">
                @if (copied()) {
                  Référence copiée dans le presse-papiers.
                }
              </span>
            </div>
          }

          @if (retryable()) {
            <div class="pt-2">
              <button
                type="button"
                class="btn btn-outline btn-sm gap-2 rounded-lg"
                [disabled]="retrying()"
                [attr.aria-busy]="retrying()"
                (click)="onRetry()"
              >
                @if (retrying()) {
                  <span class="loading loading-spinner loading-xs" aria-hidden="true"></span>
                } @else {
                  <svg lucideRefreshCw [strokeWidth]="1.75" class="h-4 w-4 shrink-0" aria-hidden="true"></svg>
                }
                Réessayer
              </button>
            </div>
          }
        </div>
      </div>
    </div>
  `,
})
export class ErrorStateComponent {
  private readonly clipboard = inject(Clipboard);
  private readonly destroyRef = inject(DestroyRef);
  private copyResetTimer: ReturnType<typeof setTimeout> | null = null;

  readonly problem = input<ProblemDetail | null>(null);
  readonly title = input<string>('');
  readonly message = input<string>('');
  readonly requestId = input<string | null>(null);
  readonly retryable = input(true);
  readonly retrying = input(false);

  readonly retry = output<void>();

  protected readonly copied = signal(false);

  protected readonly copyButtonLabel = computed(() =>
    this.copied() ? 'Référence copiée' : "Copier la référence d'incident"
  );

  protected readonly resolvedTitle = computed(
    () => this.title().trim() || this.problem()?.title || 'Une erreur est survenue'
  );

  protected readonly resolvedMessage = computed(
    () =>
      this.message().trim() ||
      this.problem()?.detail ||
      'Impossible de charger les données pour le moment. Veuillez réessayer.'
  );

  protected readonly resolvedRequestId = computed(
    () => this.requestId()?.trim() || this.problem()?.requestId || null
  );

  protected readonly fieldErrors = computed(() => this.problem()?.errors ?? []);

  constructor() {
    this.destroyRef.onDestroy(() => {
      if (this.copyResetTimer !== null) {
        clearTimeout(this.copyResetTimer);
      }
    });
  }

  protected copyRequestId(reqId: string): void {
    const success = this.clipboard.copy(reqId);
    if (success) {
      this.copied.set(true);
      if (this.copyResetTimer !== null) {
        clearTimeout(this.copyResetTimer);
      }
      this.copyResetTimer = setTimeout(() => {
        this.copied.set(false);
        this.copyResetTimer = null;
      }, 2500);
    }
  }

  protected onRetry(): void {
    if (!this.retrying()) {
      this.retry.emit();
    }
  }
}
