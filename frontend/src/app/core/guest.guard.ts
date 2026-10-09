import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Evita mostrar el login a quien ya tiene sesión: lo envía a la página inicial de su rol. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (!auth.isAuthenticated()) return true;
  return inject(Router).createUrlTree([auth.hasRole('ADMIN') ? '/admin' : '/']);
};
