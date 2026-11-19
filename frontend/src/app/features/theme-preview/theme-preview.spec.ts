import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { FINANCE_API } from '../../core/api/finance.api';
import { FinanceMockApi } from '../../core/api/finance-mock.api';
import { MOCK_API_OPTIONS } from '../../core/api/mock-api.util';
import { STUDENT_API } from '../../core/api/student.api';
import { StudentMockApi } from '../../core/api/student-mock.api';
import { ThemePreviewComponent } from './theme-preview';

describe('ThemePreviewComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ThemePreviewComponent],
      providers: [
        { provide: MOCK_API_OPTIONS, useValue: { delayMs: 0, scenario: 'default' } },
        { provide: STUDENT_API, useExisting: StudentMockApi },
        { provide: FINANCE_API, useExisting: FinanceMockApi },
      ],
    }).compileComponents();
  });

  it('opens and closes the navigation drawer cleanly (responsive 360px layout covered by Playwright)', async () => {
    const fixture = TestBed.createComponent(ThemePreviewComponent);
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const menuBtn = el.querySelector<HTMLButtonElement>(
      'button[aria-controls="theme-preview-sidebar"]'
    );
    const drawerCheckbox = el.querySelector<HTMLInputElement>('#theme-preview-drawer');
    const closeBtn = el.querySelector<HTMLButtonElement>('button[aria-label="Fermer le tiroir"]');

    expect(menuBtn?.getAttribute('aria-expanded')).toBe('false');
    expect(drawerCheckbox?.checked).toBe(false);

    menuBtn?.click();
    fixture.detectChanges();
    expect(menuBtn?.getAttribute('aria-expanded')).toBe('true');
    expect(drawerCheckbox?.checked).toBe(true);

    closeBtn?.click();
    fixture.detectChanges();
    expect(menuBtn?.getAttribute('aria-expanded')).toBe('false');
    expect(drawerCheckbox?.checked).toBe(false);
  });

  it('toggles light/dark theme via button and updates html data-theme', async () => {
    const fixture = TestBed.createComponent(ThemePreviewComponent);
    const doc = TestBed.inject(DOCUMENT);
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const themeBtn = el.querySelector<HTMLButtonElement>('button[aria-pressed]');
    const initialTheme = doc.documentElement.getAttribute('data-theme');

    themeBtn?.click();
    fixture.detectChanges();
    const toggledTheme = doc.documentElement.getAttribute('data-theme');
    expect(toggledTheme).not.toBe(initialTheme);
    expect(themeBtn?.getAttribute('aria-pressed')).toBe(String(toggledTheme === 'dark'));
  });

  it('moves both focus and selection on ArrowRight, ArrowLeft, End, and Home with roving tabindex', async () => {
    const fixture = TestBed.createComponent(ThemePreviewComponent);
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const tablist = el.querySelector('[role="tablist"]');
    expect(tablist?.classList.contains('tabs-box')).toBe(true);

    const tabs = Array.from(el.querySelectorAll<HTMLButtonElement>('[role="tab"]'));
    expect(tabs.length).toBe(3);
    expect(tabs.map((t) => t.getAttribute('tabindex'))).toEqual(['0', '-1', '-1']);

    tabs[0].focus();
    tabs[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(tabs[1].getAttribute('aria-selected')).toBe('true');
    expect(tabs.map((t) => t.getAttribute('tabindex'))).toEqual(['-1', '0', '-1']);
    expect(document.activeElement).toBe(tabs[1]);

    tabs[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    fixture.detectChanges();
    expect(tabs[2].getAttribute('aria-selected')).toBe('true');
    expect(document.activeElement).toBe(tabs[2]);

    tabs[2].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    fixture.detectChanges();
    expect(tabs[1].getAttribute('aria-selected')).toBe('true');
    expect(document.activeElement).toBe(tabs[1]);

    tabs[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    fixture.detectChanges();
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
    expect(document.activeElement).toBe(tabs[0]);
  });

  it('associates every form input with an explicit label', async () => {
    const fixture = TestBed.createComponent(ThemePreviewComponent);
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const formInputs = Array.from(
      el.querySelectorAll<HTMLElement>('form input[id], form select[id]')
    );
    expect(formInputs.length).toBeGreaterThanOrEqual(4);
    for (const inputEl of formInputs) {
      expect(el.querySelector(`label[for="${inputEl.id}"]`)).not.toBeNull();
    }
  });
});
