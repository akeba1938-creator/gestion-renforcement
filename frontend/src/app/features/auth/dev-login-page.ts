import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Development-only demo login page.
 * Exposed exclusively via `app.dev-routes.ts` (/dev-login) and completely excluded
 * from production builds via `fileReplacements` in `angular.json`.
 */
@Component({
  selector: 'app-dev-login-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="flex min-h-screen items-center justify-center bg-base-200 p-4">
      <div class="card w-full max-w-md border border-base-300 bg-base-100 shadow-sm">
        <div class="card-body gap-4">
          <h1 class="card-title text-xl">Connexion de démonstration (Dev uniquement)</h1>
          <p class="text-sm text-base-content/75">
            Ouvre une session simulée en mémoire pour explorer l'interface en développement local.
          </p>
          <button type="button" class="btn btn-primary w-full" (click)="signInDemo('DIRECTOR')">
            Accéder en mode démonstration (Directeur)
          </button>
          <button type="button" class="btn btn-outline w-full" (click)="signInDemo('CASHIER')">
            Accéder en mode démonstration (Caissier)
          </button>
        </div>
      </div>
    </section>
  `,
})
export class DevLoginPageComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected signInDemo(role: 'DIRECTOR' | 'CASHIER' = 'DIRECTOR'): void {
    const isCashier = role === 'CASHIER';
    this.authService.setSession('demo-memory-token', {
      id: isCashier ? 'usr-demo-cashier' : 'usr-demo-director',
      fullName: isCashier ? 'Caisse Démo' : 'Direction Démo',
      email: isCashier ? 'caisse@ecole-demo.invalid' : 'direction@ecole-demo.invalid',
      role,
      tenantId: 'tenant-demo-01',
      tenantName: 'Groupe Scolaire Démo',
    });
    void this.router.navigate([isCashier ? '/finances' : '/dashboard']);
  }
}
