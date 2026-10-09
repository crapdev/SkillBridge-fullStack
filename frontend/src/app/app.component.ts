import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    <header class="nav">
      <div class="container nav-inner">
        <a routerLink="/" class="brand">SkillBridge AI</a>
        <nav>
          <a routerLink="/">Servicios</a>
          <a routerLink="/book">Reservar</a>
          
          @if (auth.isAuthenticated()) {
            
            <!-- ENLACES SOLO PARA ESTUDIANTES -->
            @if (auth.hasRole('CUSTOMER')) {
              <a routerLink="/bookings/me">Mis Reservas</a>
            }
            
            <!-- ENLACES SOLO PARA PROVEEDORES -->
            @if (auth.hasRole('PROVIDER')) {
              <a routerLink="/provider/offerings">Panel Mentorías</a>
              <a routerLink="/provider/bookings">Ver Solicitudes</a>
            }

          }
          
          <a routerLink="/ai">IA</a>
          
          @if (!auth.isAuthenticated()) {
            <a routerLink="/login">Ingresar</a>
          } @else {
            <button class="link-button logout-btn" (click)="auth.logout()">Salir</button>
          }
        </nav>
      </div>
    </header>
    <main><router-outlet /></main>
  `,
  styles: [`
    .nav{background:#fff;border-bottom:1px solid #e7ebf0;position:sticky;top:0;z-index:5}
    .nav-inner{height:64px;display:flex;align-items:center;justify-content:space-between}
    .brand{font-weight:800}.nav nav{display:flex;gap:18px;align-items:center}
    .link-button{border:0;background:none;cursor:pointer}
    .logout-btn{color: #dc2626; font-weight: bold;}
    a.active { font-weight: bold; color: #2563eb; }
  `]
})
export class AppComponent {
  auth = inject(AuthService);
}