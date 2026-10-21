import { inject, provideAppInitializer } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { AppComponent } from './app/app.component';
import { routes } from './app/app.routes';
import { authInterceptor } from './app/core/auth.interceptor';
import { AuthService } from './app/core/auth.service';

bootstrapApplication(AppComponent, {
  providers: [
    // anchorScrolling: los enlaces del header con fragment (/#contacto) bajan hasta esa sección
    provideRouter(routes, withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' })),
    provideHttpClient(
      withInterceptors([authInterceptor]),
      // Los mismos nombres que usa el backend (HU-29): Angular lee la cookie y envía la cabecera en las escrituras
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })),
    // Se pregunta por la sesión al arrancar, pero sin esperar la respuesta: si el backend está dormido
    // (cold start de Render) la página se pinta igual. Los guards son los que esperan (role.guard.ts)
    provideAppInitializer(() => { inject(AuthService).restore().subscribe(); })]
}).catch(err => console.error(err));
