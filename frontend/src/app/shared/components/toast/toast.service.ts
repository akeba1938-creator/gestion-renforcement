import { LiveAnnouncer } from '@angular/cdk/a11y';
import { Injectable, inject, signal } from '@angular/core';

export type ToastTone = 'success' | 'info' | 'warning' | 'error';

export interface ToastOptions {
  readonly message: string;
  readonly title?: string;
  readonly tone?: ToastTone;
  readonly durationMs?: number;
}

export interface ToastItem {
  readonly id: string;
  readonly message: string;
  readonly title?: string;
  readonly tone: ToastTone;
  readonly durationMs: number;
}

const DEFAULT_DURATION_MS = 4500;
const MAX_VISIBLE_TOASTS = 5;

/**
 * Signal-based toast notification service with automatic dismissal and
 * screen-reader announcements via Angular CDK LiveAnnouncer.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly liveAnnouncer = inject(LiveAnnouncer);
  private readonly toastsSignal = signal<readonly ToastItem[]>([]);
  private readonly timers = new Map<string, ReturnType<typeof setTimeout>>();
  private sequence = 0;

  readonly toasts = this.toastsSignal.asReadonly();

  show(options: ToastOptions): string {
    const id = `toast-${++this.sequence}`;
    const tone: ToastTone = options.tone ?? 'info';
    const durationMs = options.durationMs ?? DEFAULT_DURATION_MS;
    const item: ToastItem = {
      id,
      message: options.message,
      ...(options.title ? { title: options.title } : {}),
      tone,
      durationMs,
    };

    this.toastsSignal.update((current) => {
      const next = [...current, item];
      if (next.length > MAX_VISIBLE_TOASTS) {
        const dropped = next.splice(0, next.length - MAX_VISIBLE_TOASTS);
        for (const removed of dropped) {
          this.clearTimer(removed.id);
        }
      }
      return next;
    });

    const announcement = item.title ? `${item.title} : ${item.message}` : item.message;
    const politeness = tone === 'error' ? 'assertive' : 'polite';
    void this.liveAnnouncer.announce(announcement, politeness);

    if (durationMs > 0) {
      const handle = setTimeout(() => this.dismiss(id), durationMs);
      this.timers.set(id, handle);
    }

    return id;
  }

  success(message: string, title?: string, durationMs?: number): string {
    return this.show({ message, title, tone: 'success', durationMs });
  }

  info(message: string, title?: string, durationMs?: number): string {
    return this.show({ message, title, tone: 'info', durationMs });
  }

  warning(message: string, title?: string, durationMs?: number): string {
    return this.show({ message, title, tone: 'warning', durationMs });
  }

  error(message: string, title?: string, durationMs?: number): string {
    return this.show({ message, title, tone: 'error', durationMs });
  }

  dismiss(id: string): void {
    this.clearTimer(id);
    this.toastsSignal.update((current) => current.filter((item) => item.id !== id));
  }

  clear(): void {
    for (const id of Array.from(this.timers.keys())) {
      this.clearTimer(id);
    }
    this.toastsSignal.set([]);
  }

  private clearTimer(id: string): void {
    const handle = this.timers.get(id);
    if (handle !== undefined) {
      clearTimeout(handle);
      this.timers.delete(id);
    }
  }
}
