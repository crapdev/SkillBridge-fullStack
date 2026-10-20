import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

/** Evita mostrar el login a quien ya tiene sesión: lo envía a la página inicial de su rol. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  // Espera a saber si hay sesión, igual que roleGuard
  return auth.restore().pipe(map(() =>
    auth.isAuthenticated() ? router.createUrlTree([auth.hasRole('ADMIN') ? '/admin' : '/']) : true));
};
