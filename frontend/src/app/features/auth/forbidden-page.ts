import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ADMIN_ROLES } from '../../core/config/role-access.config';

@Component({
  selector: 'app-forbidden-page',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="flex min-h-screen items-center justify-center bg-base-200 p-4">
      <div class="card w-full max-w-md border border-base-300 bg-base-100 shadow-sm">
        <div class="card-body gap-4">
          <h1 class="card-title text-xl text-error">Accès refusé (403)</h1>
          <p class="text-sm text-base-content/80">
            Votre rôle actuel ne permet pas d'accéder à cette page.
          </p>

          <div class="flex flex-wrap items-center gap-2">
            @if (roleHomeRoute(); as homeRoute) {
              <a [routerLink]="homeRoute" class="btn btn-primary btn-sm">
                Retour à mon accueil
              </a>
            }
            <button type="button" class="btn btn-outline btn-sm" (click)="logout()">
              Se déconnecter
            </button>
          </div>
        </div>
      </div>
    </section>
  `,
})
export class ForbiddenPageComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  /**
   * Returns the home route for the authenticated user's role if an active space exists.
   * Teacher / Parent / Student spaces are not yet wired, so null is returned for them.
   */
  protected readonly roleHomeRoute = computed<string | null>(() => {
    const role = this.authService.role();
    if (!role) {
      return null;
    }
    if (ADMIN_ROLES.includes(role)) {
      return '/dashboard';
    }
    return null;
  });

  protected logout(): void {
    this.authService.clearSession();
    void this.router.navigate(['/login']);
  }
}
