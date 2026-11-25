import { Clipboard } from '@angular/cdk/clipboard';
import { TestBed } from '@angular/core/testing';
import { ProblemDetail } from '../../../core/models/problem-detail.model';
import { ErrorStateComponent } from './error-state';

describe('ErrorStateComponent', () => {
  let clipboardCopySpy: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    clipboardCopySpy = vi.fn(() => true);
    await TestBed.configureTestingModule({
      imports: [ErrorStateComponent],
      providers: [{ provide: Clipboard, useValue: { copy: clipboardCopySpy } }],
    }).compileComponents();
  });

  it('displays RFC 9457 details, copies requestId, and emits retry', () => {
    const problem: ProblemDetail = {
      type: 'urn:ecole:problem:internal-error',
      title: 'Erreur serveur',
      status: 500,
      detail: 'Une erreur interne est survenue.',
      requestId: 'req-2026-xyz',
    };

    const fixture = TestBed.createComponent(ErrorStateComponent);
    fixture.componentRef.setInput('problem', problem);

    let retries = 0;
    fixture.componentInstance.retry.subscribe(() => {
      retries += 1;
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="alert"]')).not.toBeNull();
    expect(el.textContent).toContain('Erreur serveur');
    expect(el.textContent).toContain('Une erreur interne est survenue.');
    expect(el.querySelector('code')?.textContent?.trim()).toBe('req-2026-xyz');

    const copyBtn = el.querySelector<HTMLButtonElement>(
      'button[aria-label="Copier la référence d\'incident"]'
    );
    expect(copyBtn).not.toBeNull();
    copyBtn?.click();
    fixture.detectChanges();

    expect(clipboardCopySpy).toHaveBeenCalledWith('req-2026-xyz');
    expect(el.textContent).toContain('Copié');

    const retryBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Réessayer')
    );
    expect(retryBtn).not.toBeUndefined();
    retryBtn?.click();
    expect(retries).toBe(1);
  });

  it('prevents double retry clicks when retrying is true', () => {
    const fixture = TestBed.createComponent(ErrorStateComponent);
    fixture.componentRef.setInput('retrying', true);

    let retries = 0;
    fixture.componentInstance.retry.subscribe(() => {
      retries += 1;
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const retryBtn = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('Réessayer')
    );
    expect(retryBtn?.disabled).toBe(true);
    retryBtn?.click();
    expect(retries).toBe(0);
  });
});
