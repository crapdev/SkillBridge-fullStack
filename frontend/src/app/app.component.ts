import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    <!-- La pantalla de login tiene su propio encabezado -->
    @if (!isAuthPage()) {
    <header class="nav">
      <div class="container nav-inner">
        <a routerLink="/" class="brand">SkillBridge AI</a>
        <nav>
          <a routerLink="/">Servicios</a>
          <!-- Solo para clientes con sesión; el backend también restringe estas rutas al rol CUSTOMER -->
          @if (auth.hasRole('CUSTOMER')) {
            <a routerLink="/book">Reservar</a>
            <a routerLink="/ai">IA</a>
          }
        </nav>
        <div class="buttons">
        @if (!auth.isAuthenticated()) {
          <a routerLink="/login">Ingresar</a>
        } @else {
          <button class="link-button" (click)="auth.logout()">Salir</button>
        }
        <a routerLink="" class="button-primary">Agendar Sesión</a>
        </div>
      </div>
    </header>
    }
    <main><router-outlet /></main>
  `,
  styleUrls: ['./header.css']
})
export class AppComponent {
  auth = inject(AuthService);
  private router = inject(Router);

  readonly isAuthPage = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map(e => e.urlAfterRedirects.startsWith('/login'))
    ),
    { initialValue: false }
  );
}
