import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  const originalMatchMedia = window.matchMedia;

  afterEach(() => {
    Object.defineProperty(window, 'matchMedia', {
      value: originalMatchMedia,
      writable: true,
      configurable: true,
    });
  });

  it('defaults to light when matchMedia is unavailable', () => {
    Object.defineProperty(window, 'matchMedia', {
      value: undefined,
      writable: true,
      configurable: true,
    });

    TestBed.configureTestingModule({});
    const doc = TestBed.inject(DOCUMENT);
    const service = TestBed.inject(ThemeService);

    expect(service.theme()).toBe('light');
    expect(service.isDark()).toBe(false);
    expect(doc.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('follows system prefers-color-scheme: dark on initialization', () => {
    Object.defineProperty(window, 'matchMedia', {
      value: vi.fn().mockReturnValue({ matches: true }),
      writable: true,
      configurable: true,
    });

    TestBed.configureTestingModule({});
    const doc = TestBed.inject(DOCUMENT);
    const service = TestBed.inject(ThemeService);

    expect(service.theme()).toBe('dark');
    expect(service.isDark()).toBe(true);
    expect(doc.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('toggles theme manually in memory only and updates html data-theme', () => {
    Object.defineProperty(window, 'matchMedia', {
      value: vi.fn().mockReturnValue({ matches: false }),
      writable: true,
      configurable: true,
    });
    const storageSpy = vi.spyOn(Storage.prototype, 'setItem');

    TestBed.configureTestingModule({});
    const doc = TestBed.inject(DOCUMENT);
    const service = TestBed.inject(ThemeService);

    service.toggleTheme();
    expect(service.theme()).toBe('dark');
    expect(doc.documentElement.getAttribute('data-theme')).toBe('dark');

    service.setTheme('light');
    expect(service.theme()).toBe('light');
    expect(doc.documentElement.getAttribute('data-theme')).toBe('light');
    expect(storageSpy).not.toHaveBeenCalled();

    storageSpy.mockRestore();
  });
});
