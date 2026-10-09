import { Component, computed, inject, isDevMode, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { AvatarComponent } from '../shared/avatar.component';
import { ToastService } from '../shared/toast.service';
import { adminMockEnabled, setAdminMock } from '../admin.config';

@Component({
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AvatarComponent],
  templateUrl: './admin-layout.component.html',
  styleUrls: ['./admin-layout.component.css']
})
export class AdminLayoutComponent {
  protected auth = inject(AuthService);
  protected toasts = inject(ToastService);

  protected readonly menuOpen = signal(false);
  protected readonly email = computed(() => this.auth.email() ?? '');
  // Sin el nombre en el token, las iniciales salen de la parte local del correo
  protected readonly displayName = computed(() => this.email().split('@')[0]);
  protected readonly devMode = isDevMode();
  protected readonly mock = adminMockEnabled();

  constructor() {
    // En móvil el menú se cierra al navegar
    inject(Router).events.pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.menuOpen.set(false));
  }

  protected toggleMock() {
    setAdminMock(!this.mock);
    location.reload();
  }

  protected logout() { this.auth.logout('/login'); }
}
