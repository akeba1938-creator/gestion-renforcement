import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  let service: ThemeService;
  let doc: Document;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    doc = TestBed.inject(DOCUMENT);
    service = TestBed.inject(ThemeService);
  });

  it('initializes with light theme and sets data-theme on html', () => {
    expect(service.theme()).toBe('light');
    expect(service.isDark()).toBe(false);
    expect(doc.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('toggles between light and dark themes and updates html data-theme', () => {
    service.toggleTheme();
    expect(service.theme()).toBe('dark');
    expect(service.isDark()).toBe(true);
    expect(doc.documentElement.getAttribute('data-theme')).toBe('dark');

    service.toggleTheme();
    expect(service.theme()).toBe('light');
    expect(service.isDark()).toBe(false);
    expect(doc.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('sets a specific theme via setTheme()', () => {
    service.setTheme('dark');
    expect(service.theme()).toBe('dark');
    expect(doc.documentElement.getAttribute('data-theme')).toBe('dark');
  });
});
