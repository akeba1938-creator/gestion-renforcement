import { TestBed } from '@angular/core/testing';
import { devRoutes as prodDevRoutes } from '../../app.dev-routes.prod';
import { AuthService } from '../../core/auth/auth.service';
import { LoginPageComponent } from './login-page';

describe('LoginPageComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginPageComponent],
    }).compileComponents();
  });

  it('displays server unavailable message on submit and never creates a session', () => {
    const fixture = TestBed.createComponent(LoginPageComponent);
    const authService = TestBed.inject(AuthService);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const form = el.querySelector('form');
    expect(form).not.toBeNull();
    expect(el.textContent).not.toContain('mode démonstration');

    form?.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(el.textContent).toContain(
      "Connexion indisponible : le serveur n'est pas encore branché"
    );
    expect(authService.isAuthenticated()).toBe(false);
    expect(authService.token()).toBeNull();
  });

  it('keeps production devRoutes empty so /theme-preview and /dev-login are excluded', () => {
    expect(prodDevRoutes).toEqual([]);
  });
});
