import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injector, inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Marca peticiones cuyo 401 no debe cerrar la sesión. Lo usa el panel de administración:
 * mientras sus endpoints no existan, el backend responde 401 aunque la sesión sea válida,
 * y la vista debe seguir visible mostrando el error.
 */
export const KEEP_SESSION_ON_401 = new HttpContextToken<boolean>(() => false);

/**
 * La sesión viaja sola en la cookie HttpOnly y Angular añade X-XSRF-TOKEN en las escrituras (HU-29), así
 * que aquí no se agrega ninguna cabecera: solo se cierra la sesión cuando el backend deja de aceptarla.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // AuthService depende de HttpClient: se resuelve de forma diferida para evitar una dependencia circular
  const injector = inject(Injector);
  return next(req).pipe(
    catchError((error: unknown) => {
      // Un 401 con sesión abierta significa que la cookie expiró o dejó de ser válida.
      // Se excluye /auth/ porque ahí el 401 es "credenciales inválidas" o "sin sesión" al arrancar.
      if (error instanceof HttpErrorResponse && error.status === 401 && !req.url.includes('/auth/')
          && !req.context.get(KEEP_SESSION_ON_401)) {
        const auth = injector.get(AuthService);
        if (auth.isAuthenticated()) {
          // expired=1: el login le explica al usuario por qué tiene que volver a ingresar
          auth.logout('/login?expired=1&returnUrl=' + encodeURIComponent(injector.get(Router).url));
        }
      }
      return throwError(() => error);
    })
  );
};
