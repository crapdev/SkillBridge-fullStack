import { Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { AvatarComponent } from '../admin/shared/avatar.component';
import { NotificationBellComponent } from '../../shared/notification-bell/notification-bell.component';

/**
 * Panel del proveedor (HU-22): mismo diseño que el de administración (menú lateral, barra superior y
 * tarjetas). Reutiliza el CSS del layout del admin para que ambos paneles no se separen con el tiempo.
 */
@Component({
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AvatarComponent, NotificationBellComponent],
  templateUrl: './provider-layout.component.html',
  styleUrls: ['../admin/layout/admin-layout.component.css', './provider-layout.component.css']
})
export class ProviderLayoutComponent {
  protected auth = inject(AuthService);

  protected readonly menuOpen = signal(false);
  protected readonly email = computed(() => this.auth.email() ?? '');
  // Sin el nombre en el token, las iniciales salen de la parte local del correo
  protected readonly displayName = computed(() => this.email().split('@')[0]);

  constructor() {
    // En móvil el menú se cierra al navegar
    inject(Router).events.pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.menuOpen.set(false));
  }

  protected logout() { this.auth.logout('/login'); }
}
