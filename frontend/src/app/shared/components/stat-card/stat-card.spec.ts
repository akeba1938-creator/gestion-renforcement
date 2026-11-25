import { TestBed } from '@angular/core/testing';
import { StatCardComponent } from './stat-card';

describe('StatCardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatCardComponent],
    }).compileComponents();
  });

  it('applies semantic tone classes for secondary and accent and includes tabular-nums', () => {
    const fixture = TestBed.createComponent(StatCardComponent);
    fixture.componentRef.setInput('label', 'Encaissements');
    fixture.componentRef.setInput('value', '1 250 000 FCFA');
    fixture.componentRef.setInput('tone', 'secondary');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const valueEl = el.querySelectorAll('p')[1];
    expect(valueEl?.classList.contains('text-secondary')).toBe(true);
    expect(valueEl?.classList.contains('tabular-nums')).toBe(true);
    expect(valueEl?.classList.contains('font-display')).toBe(true);

    fixture.componentRef.setInput('tone', 'accent');
    fixture.detectChanges();

    expect(valueEl?.classList.contains('text-accent')).toBe(true);
    expect(valueEl?.classList.contains('tabular-nums')).toBe(true);
  });
});
