import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ConfirmDialogComponent } from './confirm-dialog';

@Component({
  standalone: true,
  imports: [ConfirmDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button id="open-confirm-trigger" type="button" (click)="open.set(true)">Supprimer</button>
    <app-confirm-dialog
      [open]="open()"
      title="Confirmer la suppression"
      message="Cette action est définitive."
      confirmLabel="Supprimer définitivement"
      tone="destructive"
      [loading]="loading()"
      (confirm)="confirmedCount.set(confirmedCount() + 1)"
      (cancel)="open.set(false)"
    />
  `,
})
class ConfirmDialogHostComponent {
  readonly open = signal(false);
  readonly loading = signal(false);
  readonly confirmedCount = signal(0);
}

describe('ConfirmDialogComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfirmDialogHostComponent],
    }).compileComponents();
  });

  it('opens with dialog role, destructive button, closes on Escape, and restores focus', () => {
    const fixture = TestBed.createComponent(ConfirmDialogHostComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();

    const trigger = fixture.nativeElement.querySelector(
      '#open-confirm-trigger'
    ) as HTMLButtonElement;
    trigger.focus();
    expect(document.activeElement).toBe(trigger);

    trigger.click();
    fixture.detectChanges();

    const dialog = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement | null;
    expect(dialog).not.toBeNull();
    expect(dialog?.getAttribute('aria-modal')).toBe('true');

    const confirmBtn = dialog?.querySelector<HTMLButtonElement>('.btn-error');
    expect(confirmBtn?.textContent?.trim()).toBe('Supprimer définitivement');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(fixture.componentInstance.open()).toBe(false);
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement).toBe(trigger);

    fixture.nativeElement.remove();
  });

  it('blocks confirmation and Escape dismissal while loading', () => {
    const fixture = TestBed.createComponent(ConfirmDialogHostComponent);
    fixture.componentInstance.open.set(true);
    fixture.componentInstance.loading.set(true);
    fixture.detectChanges();

    const confirmBtn = fixture.nativeElement.querySelector('.btn-error') as HTMLButtonElement;
    expect(confirmBtn.disabled).toBe(true);
    confirmBtn.click();
    expect(fixture.componentInstance.confirmedCount()).toBe(0);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(fixture.componentInstance.open()).toBe(true);
  });
});
