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
    Object.defineProperty(window, 'innerWidth', { value: 360, configurable: true });

    await TestBed.configureTestingModule({
      imports: [ThemePreviewComponent],
      providers: [
        { provide: MOCK_API_OPTIONS, useValue: { delayMs: 0, scenario: 'default' } },
        { provide: STUDENT_API, useExisting: StudentMockApi },
        { provide: FINANCE_API, useExisting: FinanceMockApi },
      ],
    }).compileComponents();
  });

  it('supports mobile drawer toggle at 360px viewport width', async () => {
    const fixture = TestBed.createComponent(ThemePreviewComponent);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(window.innerWidth).toBe(360);
    const el = fixture.nativeElement as HTMLElement;
    const menuBtn = el.querySelector<HTMLButtonElement>(
      'button[aria-controls="theme-preview-sidebar"]'
    );
    expect(menuBtn).not.toBeNull();
    expect(menuBtn?.getAttribute('aria-expanded')).toBe('false');

    menuBtn?.click();
    fixture.detectChanges();
    expect(menuBtn?.getAttribute('aria-expanded')).toBe('true');
  });

  it('toggles light/dark theme via button and updates html data-theme', async () => {
    const fixture = TestBed.createComponent(ThemePreviewComponent);
    const doc = TestBed.inject(DOCUMENT);
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const themeBtn = el.querySelector<HTMLButtonElement>('button[aria-pressed]');
    expect(doc.documentElement.getAttribute('data-theme')).toBe('light');

    themeBtn?.click();
    fixture.detectChanges();
    expect(doc.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(themeBtn?.getAttribute('aria-pressed')).toBe('true');
  });

  it('exposes ARIA tablist/tab/tabpanel roles and explicit form labels', async () => {
    const fixture = TestBed.createComponent(ThemePreviewComponent);
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="tablist"]')).not.toBeNull();
    const tabs = Array.from(el.querySelectorAll<HTMLButtonElement>('[role="tab"]'));
    expect(tabs.length).toBe(3);
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');

    // Switch to forms tab and verify ARIA selection + explicit form labels
    tabs[2].click();
    fixture.detectChanges();
    expect(tabs[2].getAttribute('aria-selected')).toBe('true');
    const formsPanel = el.querySelector<HTMLElement>('#panel-forms[role="tabpanel"]');
    expect(formsPanel?.hidden).toBe(false);

    const formInputs = Array.from(
      el.querySelectorAll<HTMLElement>('form input[id], form select[id]')
    );
    expect(formInputs.length).toBeGreaterThanOrEqual(4);
    for (const inputEl of formInputs) {
      const label = el.querySelector(`label[for="${inputEl.id}"]`);
      expect(label).not.toBeNull();
    }
  });
});
