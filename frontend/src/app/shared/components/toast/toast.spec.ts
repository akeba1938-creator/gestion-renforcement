import { TestBed } from '@angular/core/testing';
import { ToastContainerComponent } from './toast-container';
import { ToastService } from './toast.service';

describe('ToastService & ToastContainerComponent', () => {
  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [ToastContainerComponent],
    }).compileComponents();
  });

  afterEach(() => {
    TestBed.inject(ToastService).clear();
    vi.useRealTimers();
  });

  it('shows a toast notification and auto-dismisses it after durationMs', () => {
    const service = TestBed.inject(ToastService);
    const fixture = TestBed.createComponent(ToastContainerComponent);

    service.success('Paiement enregistré.', 'Succès', 3000);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(service.toasts().length).toBe(1);
    expect(el.textContent).toContain('Succès');
    expect(el.textContent).toContain('Paiement enregistré.');
    expect(el.querySelector('[role="status"]')).not.toBeNull();

    vi.advanceTimersByTime(3000);
    fixture.detectChanges();

    expect(service.toasts().length).toBe(0);
    expect(el.querySelector('[role="status"]')).toBeNull();
  });

  it('allows manual dismissal via close button and uses alert role for errors', () => {
    const service = TestBed.inject(ToastService);
    const fixture = TestBed.createComponent(ToastContainerComponent);

    service.error('Échec de la synchronisation.', undefined, 0);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="alert"]')).not.toBeNull();

    const closeBtn = el.querySelector<HTMLButtonElement>('button[aria-label="Fermer la notification"]');
    closeBtn?.click();
    fixture.detectChanges();

    expect(service.toasts().length).toBe(0);
  });
});
