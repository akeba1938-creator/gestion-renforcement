import { TestBed } from '@angular/core/testing';
import { StatCardComponent } from './stat-card';

describe('StatCardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatCardComponent],
    }).compileComponents();
  });

  it('always renders the main value in neutral text-base-content ink with font-display, tabular-nums, and nowrap', () => {
    const fixture = TestBed.createComponent(StatCardComponent);
    fixture.componentRef.setInput('label', 'Encaissements du mois');
    fixture.componentRef.setInput('value', '6\u00A0450\u00A0000 FCFA');
    fixture.componentRef.setInput('tone', 'secondary');
    fixture.componentRef.setInput('icon', 'wallet');
    fixture.componentRef.setInput('trend', '+8,2 %');
    fixture.componentRef.setInput('trendTone', 'success');
    fixture.componentRef.setInput('description', 'vs objectif fixé');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const valueEl = el.querySelectorAll('p')[1];
    expect(valueEl?.classList.contains('text-base-content')).toBe(true);
    expect(valueEl?.classList.contains('text-secondary')).toBe(false);
    expect(valueEl?.classList.contains('tabular-nums')).toBe(true);
    expect(valueEl?.classList.contains('whitespace-nowrap')).toBe(true);
    expect(valueEl?.classList.contains('font-display')).toBe(true);
    expect(valueEl?.classList.contains('tracking-tight')).toBe(true);

    // Soft top-right icon container receives the subtle semantic tint
    const iconContainer = el.querySelector('span[aria-hidden="true"]');
    expect(iconContainer).not.toBeNull();
    expect(iconContainer?.classList.contains('bg-secondary/10')).toBe(true);
    expect(iconContainer?.classList.contains('text-secondary')).toBe(true);

    // Soft bottom trend micro-badge
    const trendEl = Array.from(el.querySelectorAll('span')).find((s) =>
      s.textContent?.includes('+8,2 %')
    );
    expect(trendEl?.classList.contains('bg-success/10')).toBe(true);
    expect(trendEl?.classList.contains('text-success')).toBe(true);
    expect(trendEl?.classList.contains('border-success/20')).toBe(true);
    expect(trendEl?.classList.contains('whitespace-nowrap')).toBe(true);

    // Even with accent/warning tone, main KPI figure remains neutral text-base-content
    fixture.componentRef.setInput('tone', 'accent');
    fixture.detectChanges();

    expect(valueEl?.classList.contains('text-base-content')).toBe(true);
    expect(valueEl?.classList.contains('text-accent')).toBe(false);
    expect(iconContainer?.classList.contains('bg-warning/10')).toBe(true);
  });
});
