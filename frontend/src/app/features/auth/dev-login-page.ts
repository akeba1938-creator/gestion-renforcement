import { Component, inject } from '@angular/core';
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
  template: `
    <section class="flex min-h-screen items-center justify-center bg-base-200 p-4">
      <div class="card w-full max-w-md border border-base-300 bg-base-100 shadow-sm">
        <div class="card-body gap-4">
          <h1 class="card-title text-xl">Connexion de démonstration (Dev uniquement)</h1>
          <p class="text-sm text-base-content/75">
            Ouvre une session simulée en mémoire pour explorer l'interface en développement local.
          </p>
          <button type="button" class="btn btn-primary w-full" (click)="signInDemo()">
            Accéder en mode démonstration (Directeur)
          </button>
        </div>
      </div>
    </section>
  `,
})
export class DevLoginPageComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected signInDemo(): void {
    this.authService.setSession('demo-memory-token', {
      id: 'usr-demo-director',
      fullName: 'Direction Démo',
      email: 'direction@ecole-demo.invalid',
      role: 'DIRECTOR',
      tenantId: 'tenant-demo-01',
      tenantName: 'Groupe Scolaire Démo',
    });
    void this.router.navigate(['/dashboard']);
  }
}
