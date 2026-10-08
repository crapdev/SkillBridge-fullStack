import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injector, inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // AuthService depende de HttpClient: se resuelve de forma diferida para evitar una dependencia circular
  const injector = inject(Injector);
  const token = localStorage.getItem('skillbridge_token');
  return next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req).pipe(
    catchError((error: unknown) => {
      // Un 401 en una petición autenticada significa que el token expiró o dejó de ser válido.
      // Se excluye /auth/ porque ahí el 401 es "credenciales inválidas" y lo muestra el formulario.
      if (error instanceof HttpErrorResponse && error.status === 401 && token && !req.url.includes('/auth/')) {
        const router = injector.get(Router);
        injector.get(AuthService).logout('/login?returnUrl=' + encodeURIComponent(router.url));
      }
      return throwError(() => error);
    })
  );
};
