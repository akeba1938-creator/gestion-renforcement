import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark';

/**
 * Manages the active daisyUI theme strictly in memory.
 * Defaults to the system preference via `prefers-color-scheme`, while allowing
 * manual toggling without writing to localStorage or sessionStorage.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly themeSignal = signal<ThemeMode>(this.detectPreferredTheme());

  readonly theme = this.themeSignal.asReadonly();
  readonly isDark = computed(() => this.themeSignal() === 'dark');

  constructor() {
    this.applyTheme(this.themeSignal());
  }

  setTheme(mode: ThemeMode): void {
    this.themeSignal.set(mode);
    this.applyTheme(mode);
  }

  toggleTheme(): void {
    this.setTheme(this.themeSignal() === 'light' ? 'dark' : 'light');
  }

  private detectPreferredTheme(): ThemeMode {
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    return 'light';
  }

  private applyTheme(mode: ThemeMode): void {
    this.document.documentElement.setAttribute('data-theme', mode);
  }
}
