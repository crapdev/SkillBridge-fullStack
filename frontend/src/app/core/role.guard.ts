import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, Role } from './auth.service';

/**
 * Protege una ruta para los roles indicados.
 * - Sin sesión (o con el token expirado): redirige a /login y conserva la URL pedida en `returnUrl`.
 * - Con sesión pero con otro rol: redirige al inicio.
 * Es un control de UX: el backend sigue siendo quien autoriza cada petición.
 */
export const roleGuard = (...roles: Role[]): CanActivateFn => (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  return auth.hasRole(...roles) ? true : router.createUrlTree(['/']);
};

export const customerGuard = roleGuard('CUSTOMER');
export const adminGuard = roleGuard('ADMIN');
