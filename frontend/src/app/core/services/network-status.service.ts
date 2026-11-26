import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';

/**
 * Tracks browser online/offline connectivity reactively using Signals.
 * Used by layouts and PWA-ready pages to display a discreet network status indicator.
 */
@Injectable({ providedIn: 'root' })
export class NetworkStatusService {
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  private readonly onlineSignal = signal<boolean>(this.readInitialOnlineState());
  readonly isOnline = this.onlineSignal.asReadonly();

  constructor() {
    const win = this.document.defaultView;
    if (!win) {
      return;
    }

    const handleOnline = (): void => this.onlineSignal.set(true);
    const handleOffline = (): void => this.onlineSignal.set(false);

    win.addEventListener('online', handleOnline);
    win.addEventListener('offline', handleOffline);

    this.destroyRef.onDestroy(() => {
      win.removeEventListener('online', handleOnline);
      win.removeEventListener('offline', handleOffline);
    });
  }

  private readInitialOnlineState(): boolean {
    const win = this.document.defaultView;
    if (win && typeof win.navigator?.onLine === 'boolean') {
      return win.navigator.onLine;
    }
    return true;
  }
}
