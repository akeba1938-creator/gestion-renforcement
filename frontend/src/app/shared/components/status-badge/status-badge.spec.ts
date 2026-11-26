import { ChangeDetectionStrategy, reflectComponentType } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { BadgeTone, StatusBadgeComponent } from './status-badge';

describe('StatusBadgeComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusBadgeComponent],
    }).compileComponents();
  });

  it('uses ChangeDetectionStrategy.OnPush and is standalone', () => {
    const mirror = reflectComponentType(StatusBadgeComponent);
    expect(mirror?.isStandalone).toBe(true);
    const cmpDef = (StatusBadgeComponent as unknown as { ɵcmp?: { onPush?: boolean } }).ɵcmp;
    expect(cmpDef?.onPush).toBe(true);
    expect(ChangeDetectionStrategy.OnPush).toBeDefined();
  });

  it('renders soft/tinted styles with strict whitespace-nowrap and never uses loud daisyUI badge classes', () => {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    const tones: readonly BadgeTone[] = ['success', 'warning', 'error', 'info'];

    for (const tone of tones) {
      fixture.componentRef.setInput('label', 'En attente');
      fixture.componentRef.setInput('tone', tone);
      fixture.detectChanges();

      const badge = (fixture.nativeElement as HTMLElement).querySelector('span');
      expect(badge).not.toBeNull();
      expect(badge?.classList.contains('whitespace-nowrap')).toBe(true);
      expect(badge?.classList.contains(`bg-${tone}/10`)).toBe(true);
      expect(badge?.classList.contains(`text-${tone}`)).toBe(true);
      expect(badge?.classList.contains(`border-${tone}/20`)).toBe(true);
      expect(badge?.classList.contains(`badge-${tone}`)).toBe(false);
    }
  });

  it('shows an optional decorative micro-dot indicator by default and hides it when showDot is false', () => {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentRef.setInput('label', 'Validé');
    fixture.componentRef.setInput('tone', 'success');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const dot = el.querySelector('span[aria-hidden="true"]');
    expect(dot).not.toBeNull();
    expect(dot?.classList.contains('bg-success')).toBe(true);

    fixture.componentRef.setInput('showDot', false);
    fixture.detectChanges();

    expect(el.querySelector('span[aria-hidden="true"]')).toBeNull();
    expect(el.textContent?.trim()).toBe('Validé');
  });
});
