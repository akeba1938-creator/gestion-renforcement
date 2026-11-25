import { TestBed } from '@angular/core/testing';
import { EmptyStateComponent } from './empty-state';

describe('EmptyStateComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmptyStateComponent],
    }).compileComponents();
  });

  it('renders empty variant with primary CTA and emits action on click', () => {
    const fixture = TestBed.createComponent(EmptyStateComponent);
    fixture.componentRef.setInput('title', 'Aucun élève enregistré');
    fixture.componentRef.setInput('description', 'Commencez par inscrire un premier élève.');
    fixture.componentRef.setInput('actionLabel', 'Inscrire un élève');

    let clicked = 0;
    fixture.componentInstance.action.subscribe(() => {
      clicked += 1;
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Aucun élève enregistré');
    expect(el.textContent).toContain('Commencez par inscrire un premier élève.');

    const btn = el.querySelector<HTMLButtonElement>('button');
    expect(btn?.textContent?.trim()).toBe('Inscrire un élève');
    expect(btn?.classList.contains('btn-primary')).toBe(true);

    btn?.click();
    expect(clicked).toBe(1);
  });

  it('renders filtered variant with default "Réinitialiser les filtres" secondary button', () => {
    const fixture = TestBed.createComponent(EmptyStateComponent);
    fixture.componentRef.setInput('title', 'Aucun résultat');
    fixture.componentRef.setInput('variant', 'filtered');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const btn = el.querySelector<HTMLButtonElement>('button');
    expect(btn?.textContent?.trim()).toBe('Réinitialiser les filtres');
    expect(btn?.classList.contains('btn-outline')).toBe(true);
  });
});
