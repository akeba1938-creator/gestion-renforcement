import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { UserRole } from '../../core/models/user.model';
import { AdminLayoutComponent } from './admin-layout';

function setUserRole(authService: AuthService, role: UserRole): void {
  authService.setSession('memory-jwt', {
    id: `usr-${role.toLowerCase()}`,
    fullName: `Moussa Diop`,
    email: `${role.toLowerCase()}@ecole-demo.invalid`,
    role,
    tenantId: 'tenant-demo-1',
    tenantName: 'Lycée Démo',
  });
}

describe('AdminLayoutComponent', () => {
  let authService: AuthService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminLayoutComponent],
      providers: [provideRouter([])],
    }).compileComponents();
    authService = TestBed.inject(AuthService);
  });

  it('renders a skip-to-content link targeting #main-content', () => {
    setUserRole(authService, 'DIRECTOR');
    const fixture = TestBed.createComponent(AdminLayoutComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const skipLink = el.querySelector<HTMLAnchorElement>('a.skip-to-content');
    const main = el.querySelector<HTMLElement>('main#main-content');

    expect(skipLink).not.toBeNull();
    expect(skipLink?.getAttribute('href')).toBe('#main-content');
    expect(main).not.toBeNull();
    expect(main?.getAttribute('tabindex')).toBe('-1');
  });

  it('filters navigation menu entries for CASHIER so no link leads to /forbidden', () => {
    setUserRole(authService, 'CASHIER');
    const fixture = TestBed.createComponent(AdminLayoutComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const links = Array.from(el.querySelectorAll<HTMLAnchorElement>('aside nav a')).map((a) =>
      a.getAttribute('href')
    );

    expect(links).toEqual(['/dashboard', '/students', '/finances', '/import']);
    expect(links).not.toContain('/grades');
    expect(links).not.toContain('/attendance');
    expect(links).not.toContain('/settings');
  });

  it('shows all 7 admin navigation entries for DIRECTOR and only permitted ones for SAAS_ADMIN', () => {
    setUserRole(authService, 'DIRECTOR');
    const fixture = TestBed.createComponent(AdminLayoutComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const directorLinks = Array.from(el.querySelectorAll<HTMLAnchorElement>('aside nav a')).map(
      (a) => a.getAttribute('href')
    );
    expect(directorLinks).toEqual([
      '/dashboard',
      '/students',
      '/grades',
      '/attendance',
      '/finances',
      '/import',
      '/settings',
    ]);

    setUserRole(authService, 'SAAS_ADMIN');
    fixture.detectChanges();
    const saasAdminLinks = Array.from(el.querySelectorAll<HTMLAnchorElement>('aside nav a')).map(
      (a) => a.getAttribute('href')
    );
    expect(saasAdminLinks).toEqual(['/dashboard', '/finances', '/settings']);
  });

  it('renders the contrasted navy sidebar with academic year selector 2026–2027 and user profile', () => {
    setUserRole(authService, 'DIRECTOR');
    const fixture = TestBed.createComponent(AdminLayoutComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const sidebar = el.querySelector('#admin-sidebar');
    expect(sidebar?.classList.contains('bg-neutral')).toBe(true);
    expect(sidebar?.classList.contains('text-neutral-content')).toBe(true);

    const yearSelect = el.querySelector<HTMLSelectElement>('#admin-academic-year');
    expect(yearSelect?.value).toBe('2026–2027');

    expect(sidebar?.textContent).toContain('Moussa Diop');
    expect(sidebar?.textContent).toContain('Directeur');
  });

  it('displays network status pill and updates reactively on offline/online events', () => {
    setUserRole(authService, 'DIRECTOR');
    const fixture = TestBed.createComponent(AdminLayoutComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const pill = el.querySelector('[data-testid="network-status-pill"]');
    expect(pill?.textContent).toContain('En ligne');

    window.dispatchEvent(new Event('offline'));
    fixture.detectChanges();
    expect(pill?.textContent).toContain('Hors-ligne');

    window.dispatchEvent(new Event('online'));
    fixture.detectChanges();
    expect(pill?.textContent).toContain('En ligne');
  });

  it('closes mobile navigation drawer on Escape and restores focus to the menu button', () => {
    setUserRole(authService, 'DIRECTOR');
    const fixture = TestBed.createComponent(AdminLayoutComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const menuBtn = el.querySelector<HTMLButtonElement>(
      'button[aria-controls="admin-sidebar"]'
    );
    expect(menuBtn?.getAttribute('aria-expanded')).toBe('false');

    menuBtn?.focus();
    menuBtn?.click();
    fixture.detectChanges();
    expect(menuBtn?.getAttribute('aria-expanded')).toBe('true');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(menuBtn?.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(menuBtn);

    fixture.nativeElement.remove();
  });
});
