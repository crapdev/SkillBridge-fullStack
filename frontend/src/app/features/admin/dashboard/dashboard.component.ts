import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminUsersService } from '../data/admin-users.service';
import {
  ACTION_TARGET, AdminUser, ManagedRole, ROLE_LABELS, STATUS_LABELS, StatusAction, UserStats, isBlocked
} from '../data/admin-user.model';
import { AvatarComponent } from '../shared/avatar.component';
import { UserStatusDialogComponent } from '../shared/user-status-dialog.component';
import { ToastService } from '../shared/toast.service';
import { errorMessage, timeAgo } from '../shared/format';

interface Recent { loading: boolean; error: string; rows: AdminUser[]; }

const PENDING_PREVIEW = 5;

@Component({
  standalone: true,
  imports: [RouterLink, AvatarComponent, UserStatusDialogComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent {
  private service = inject(AdminUsersService);
  private toast = inject(ToastService);

  protected readonly labels = ROLE_LABELS;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly roles: ManagedRole[] = ['PROVIDER', 'CUSTOMER'];
  protected readonly timeAgo = timeAgo;
  protected readonly isBlocked = isBlocked;

  protected readonly stats = signal<UserStats | null>(null);
  protected readonly statsError = signal('');
  protected readonly recent = signal<Record<ManagedRole, Recent>>({
    PROVIDER: { loading: true, error: '', rows: [] },
    CUSTOMER: { loading: true, error: '', rows: [] }
  });
  // Solicitudes de proveedores pendientes de aprobación
  protected readonly pending = signal<Recent>({ loading: true, error: '', rows: [] });

  protected readonly selected = signal<AdminUser | null>(null);
  protected readonly action = signal<StatusAction>('approve');
  protected readonly saving = signal(false);

  protected readonly status = computed(() => {
    const s = this.stats();
    if (!s) return null;
    return {
      active: s.providers.active + s.customers.active,
      inactive: s.providers.inactive + s.customers.inactive,
      pending: s.providers.pending,
      total: s.providers.total + s.customers.total
    };
  });

  protected readonly activePercent = computed(() => {
    const s = this.status();
    return s && s.total ? Math.round((s.active / s.total) * 1000) / 10 : 0;
  });

  constructor() {
    this.loadStats();
    this.loadPending();
    this.roles.forEach(role => this.loadRecent(role));
  }

  protected roleStats(role: ManagedRole) {
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

  protected loadPending() {
    this.pending.update(p => ({ ...p, loading: true, error: '' }));
    this.service.list({ role: 'PROVIDER', status: 'PENDING', page: 0, size: PENDING_PREVIEW }).subscribe({
      next: page => this.pending.set({ loading: false, error: '', rows: page.content }),
      error: e => this.pending.set({ loading: false, error: errorMessage(e, 'No fue posible cargar las solicitudes.'), rows: [] })
    });
  }

  protected ask(user: AdminUser, action: StatusAction) {
    this.action.set(action);
    this.selected.set(user);
  }

  protected confirmStatus() {
    const user = this.selected();
    if (!user) return;
    const action = this.action();
    this.saving.set(true);
    this.service.setStatus(user.id, ACTION_TARGET[action]).subscribe({
      next: () => {
        this.saving.set(false);
        this.selected.set(null);
        this.toast.success(action === 'approve'
          ? `${user.name} fue aprobado y ya puede iniciar sesión.`
          : `La solicitud de ${user.name} fue rechazada.`);
        this.loadStats();
        this.loadPending();
        this.loadRecent('PROVIDER');
      },
      error: e => {
        this.saving.set(false);
        this.toast.error(errorMessage(e, 'No fue posible cambiar el estado de la cuenta.'));
      }
    });
  }

  private loadStats() {
    this.service.stats().subscribe({
      next: s => { this.stats.set(s); this.statsError.set(''); },
      error: e => this.statsError.set(errorMessage(e, 'No fue posible cargar los indicadores.'))
    });
  }

  private patchRecent(role: ManagedRole, patch: Partial<Recent>) {
    this.recent.update(r => ({ ...r, [role]: { ...r[role], ...patch } }));
  }
}
