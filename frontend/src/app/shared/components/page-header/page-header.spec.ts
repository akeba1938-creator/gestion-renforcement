import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BreadcrumbItem, PageHeaderComponent } from './page-header';

@Component({
  standalone: true,
  imports: [PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Élèves"
      subtitle="Gestion des inscriptions de l'année en cours"
      [breadcrumbs]="breadcrumbs"
    >
      <button type="button" pageHeaderActions class="btn btn-primary btn-sm rounded-lg">
        Nouvel élève
      </button>
    </app-page-header>
  `,
})
class PageHeaderHostComponent {
  readonly breadcrumbs: readonly BreadcrumbItem[] = [
    { label: 'Accueil', route: '/dashboard' },
    { label: 'Élèves' },
  ];
}

describe('PageHeaderComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PageHeaderHostComponent],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('renders font-display title, subtitle, breadcrumbs, and projected primary action', () => {
    const fixture = TestBed.createComponent(PageHeaderHostComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const heading = el.querySelector('h1');
    expect(heading?.textContent?.trim()).toBe('Élèves');
    expect(heading?.classList.contains('font-display')).toBe(true);
    expect(el.textContent).toContain("Gestion des inscriptions de l'année en cours");

    const nav = el.querySelector('nav[aria-label="Fil d\'Ariane"]');
    expect(nav).not.toBeNull();
    expect(nav?.querySelector('a')?.textContent?.trim()).toBe('Accueil');
    expect(nav?.querySelector('[aria-current="page"]')?.textContent?.trim()).toBe('Élèves');

    const primaryButtons = el.querySelectorAll('.btn-primary');
    expect(primaryButtons.length).toBe(1);
    expect(primaryButtons[0]?.textContent?.trim()).toBe('Nouvel élève');
  });
});
