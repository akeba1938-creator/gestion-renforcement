import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { UserRole } from '../models/user.model';

/**
 * Route guard reading `route.data['roles']` to restrict navigation by role.
 *
 * IMPORTANT: Les gardes de rôle côté frontend relèvent uniquement de l'affichage
 * et de l'ergonomie de navigation, jamais de la sécurité. Le serveur vérifie
 * systématiquement l'authentification, le tenant et les permissions sur chaque API.
 */
export const roleGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }

  const allowedRoles = route.data['roles'] as readonly UserRole[] | undefined;
  if (!allowedRoles || allowedRoles.length === 0) {
    return true;
  }

  if (authService.hasAnyRole(allowedRoles)) {
    return true;
  }

  // Redirect to a non-role-guarded 403 page to prevent any redirect loop.
  return router.createUrlTree(['/forbidden']);
};
