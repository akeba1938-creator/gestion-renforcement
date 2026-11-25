import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import {
  LucideCircleAlert,
  LucideCircleCheck,
  LucideCircleX,
  LucideInfo,
  LucideX,
} from '@lucide/angular';
import { ToastService, ToastTone } from './toast.service';

/**
 * Global toast notification container rendered at the application root.
 * Consumes ToastService signals and renders accessible, dismissible toasts.
 */
@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [LucideCircleAlert, LucideCircleCheck, LucideCircleX, LucideInfo, LucideX],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="pointer-events-none fixed right-4 bottom-4 z-50 flex w-full max-w-sm flex-col gap-2"
      aria-label="Notifications"
    >
      @for (item of toastService.toasts(); track item.id) {
        <div
          [attr.role]="item.tone === 'error' ? 'alert' : 'status'"
          [attr.aria-live]="item.tone === 'error' ? 'assertive' : 'polite'"
          [class]="toastClasses(item.tone)"
        >
          <span class="mt-0.5 shrink-0" aria-hidden="true">
            @switch (item.tone) {
              @case ('success') {
                <svg lucideCircleCheck class="h-5 w-5 text-success"></svg>
              }
              @case ('warning') {
                <svg lucideCircleAlert class="h-5 w-5 text-warning"></svg>
              }
              @case ('error') {
                <svg lucideCircleX class="h-5 w-5 text-error"></svg>
              }
              @default {
                <svg lucideInfo class="h-5 w-5 text-info"></svg>
              }
            }
          </span>

          <div class="min-w-0 flex-1 text-sm">
            @if (item.title) {
              <p class="font-semibold text-base-content">{{ item.title }}</p>
            }
            <p class="text-base-content/85">{{ item.message }}</p>
          </div>

          <button
            type="button"
            class="btn btn-ghost btn-xs btn-square shrink-0 rounded-lg"
            aria-label="Fermer la notification"
            (click)="toastService.dismiss(item.id)"
          >
            <svg lucideX class="h-4 w-4" aria-hidden="true"></svg>
          </button>
        </div>
      }
    </div>
  `,
})
export class ToastContainerComponent {
  protected readonly toastService = inject(ToastService);

  protected toastClasses(tone: ToastTone): string {
    const borderByTone: Record<ToastTone, string> = {
      success: 'border-success/40',
      info: 'border-info/40',
      warning: 'border-warning/40',
      error: 'border-error/40',
    };
    return `pointer-events-auto flex items-start gap-3 rounded-xl border bg-base-100 p-4 text-base-content transition-all duration-200 ease-out motion-reduce:transition-none ${borderByTone[tone]}`;
  }
}
