import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService, Role } from './auth.service';

/**
 * Protege una ruta para los roles indicados.
 * - Espera a que el backend diga si hay sesión: quien recarga la página no termina en el login por llegar antes.
 * - Sin sesión (o con el token expirado): redirige a /login y conserva la URL pedida en `returnUrl`.
 * - Con sesión pero con otro rol: redirige al inicio.
 * Es un control de UX: el backend sigue siendo quien autoriza cada petición.
 */
export const roleGuard = (...roles: Role[]): CanActivateFn => (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.restore().pipe(map(() => {
    if (!auth.isAuthenticated()) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }
    return auth.hasRole(...roles) ? true : router.createUrlTree(['/']);
  }));
};

export const customerGuard = roleGuard('CUSTOMER');
export const providerGuard = roleGuard('PROVIDER');
export const adminGuard = roleGuard('ADMIN');
