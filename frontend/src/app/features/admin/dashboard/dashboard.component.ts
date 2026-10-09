import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminUsersService } from '../data/admin-users.service';
import { AdminUser, ManagedRole, ROLE_LABELS, RoleStats, UserStats, isActive } from '../data/admin-user.model';
import { AvatarComponent } from '../shared/avatar.component';
import { errorMessage, timeAgo } from '../shared/format';
import { userStatusEnabled } from '../admin.config';

interface Recent { loading: boolean; error: string; rows: AdminUser[]; }

@Component({
  standalone: true,
  imports: [RouterLink, AvatarComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent {
  private service = inject(AdminUsersService);

  protected readonly statusEnabled = userStatusEnabled();
  protected readonly labels = ROLE_LABELS;
  protected readonly roles: ManagedRole[] = ['PROVIDER', 'CUSTOMER'];
  protected readonly timeAgo = timeAgo;
  protected readonly isActive = isActive;

  protected readonly stats = signal<UserStats | null>(null);
  protected readonly statsError = signal('');
  protected readonly recent = signal<Record<ManagedRole, Recent>>({
    PROVIDER: { loading: true, error: '', rows: [] },
    CUSTOMER: { loading: true, error: '', rows: [] }
  });

  // Mientras no exista app_users.active todas las cuentas cuentan como activas
  protected readonly status = computed(() => {
    const s = this.stats();
    if (!s) return null;
    const active = (r: RoleStats) => r.active ?? r.total;
    const inactive = (r: RoleStats) => r.inactive ?? 0;
    return {
      active: active(s.providers) + active(s.customers),
      inactive: inactive(s.providers) + inactive(s.customers),
      total: s.providers.total + s.customers.total
    };
  });

  protected readonly activePercent = computed(() => {
    const s = this.status();
    return s && s.total ? Math.round((s.active / s.total) * 1000) / 10 : 0;
  });

  constructor() {
    this.service.stats().subscribe({
      next: s => this.stats.set(s),
      error: e => this.statsError.set(errorMessage(e, 'No fue posible cargar los indicadores.'))
    });
    this.roles.forEach(role => this.loadRecent(role));
  }

  protected roleStats(role: ManagedRole): RoleStats | undefined {
    const s = this.stats();
    return role === 'PROVIDER' ? s?.providers : s?.customers;
  }

  protected loadRecent(role: ManagedRole) {
    this.patchRecent(role, { loading: true, error: '' });
    this.service.list({ role, page: 0, size: 5 }).subscribe({
      next: page => this.patchRecent(role, { loading: false, rows: page.content }),
      error: e => this.patchRecent(role, { loading: false, error: errorMessage(e, 'No fue posible cargar la lista.') })
    });
  }

  private patchRecent(role: ManagedRole, patch: Partial<Recent>) {
    this.recent.update(r => ({ ...r, [role]: { ...r[role], ...patch } }));
  }
}
