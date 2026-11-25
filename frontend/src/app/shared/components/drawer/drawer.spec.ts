import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DrawerComponent } from './drawer';

@Component({
  standalone: true,
  imports: [DrawerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button id="open-drawer-trigger" type="button" (click)="open.set(true)">Détails</button>
    <app-drawer
      [open]="open()"
      title="Fiche élève"
      subtitle="Terminale S2"
      (closed)="open.set(false)"
    >
      <p>Contenu du tiroir</p>
      <div drawerFooter>
        <button type="button" class="btn btn-primary btn-sm">Enregistrer</button>
      </div>
    </app-drawer>
  `,
})
class DrawerHostComponent {
  readonly open = signal(false);
}

describe('DrawerComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DrawerHostComponent],
    }).compileComponents();
  });

  it('renders dialog content when open, closes on Escape, and restores focus to trigger', () => {
    const fixture = TestBed.createComponent(DrawerHostComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();

    const trigger = fixture.nativeElement.querySelector(
      '#open-drawer-trigger'
    ) as HTMLButtonElement;
    trigger.focus();
    trigger.click();
    fixture.detectChanges();

    const dialog = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement | null;
    expect(dialog).not.toBeNull();
    expect(dialog?.getAttribute('aria-modal')).toBe('true');
    expect(dialog?.textContent).toContain('Fiche élève');
    expect(dialog?.textContent).toContain('Contenu du tiroir');
    expect(dialog?.textContent).toContain('Enregistrer');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(fixture.componentInstance.open()).toBe(false);
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement).toBe(trigger);

    fixture.nativeElement.remove();
  });
});
