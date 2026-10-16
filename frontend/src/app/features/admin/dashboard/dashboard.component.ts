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
import { AdminOfferingsService } from '../data/admin-offerings.service';
import { AdminOffering, ReviewAction } from '../data/admin-offering.model';
import { OfferingReviewDialogComponent } from '../shared/offering-review-dialog.component';
import { formatPrice } from '../../../shared/offering-format';

interface Recent { loading: boolean; error: string; rows: AdminUser[]; }
interface PendingOfferings { loading: boolean; error: string; rows: AdminOffering[]; }

const PENDING_PREVIEW = 5;
const OFFERINGS_PREVIEW = 3;

@Component({
  standalone: true,
  imports: [RouterLink, AvatarComponent, UserStatusDialogComponent, OfferingReviewDialogComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent {
  private service = inject(AdminUsersService);
  private offeringsService = inject(AdminOfferingsService);
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

  // HU-21: mentorías que esperan revisión (vista previa; la lista completa está en /admin/mentorias)
  protected readonly offerings = signal<PendingOfferings>({ loading: true, error: '', rows: [] });
  protected readonly offeringPreview = computed(() => this.offerings().rows.slice(0, OFFERINGS_PREVIEW));
  protected readonly selectedOffering = signal<AdminOffering | null>(null);
  protected readonly offeringAction = signal<ReviewAction>('approve');
  protected readonly offeringSaving = signal(false);
  protected readonly price = (o: AdminOffering) => formatPrice(o.price);

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
    this.loadOfferings();
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

  protected loadOfferings() {
    this.offerings.update(o => ({ ...o, loading: true, error: '' }));
    this.offeringsService.pending().subscribe({
      next: rows => this.offerings.set({ loading: false, error: '', rows }),
      error: e => this.offerings.set({ loading: false, error: errorMessage(e, 'No fue posible cargar las mentorías.'), rows: [] })
    });
  }

  protected askOffering(offering: AdminOffering, action: ReviewAction) {
    this.offeringAction.set(action);
    this.selectedOffering.set(offering);
  }

  protected confirmOffering(reason: string) {
    const offering = this.selectedOffering();
    if (!offering) return;
    const approving = this.offeringAction() === 'approve';
    this.offeringSaving.set(true);
    const request = approving ? this.offeringsService.approve(offering.id) : this.offeringsService.reject(offering.id, reason);
    request.subscribe({
      next: () => {
        this.offeringSaving.set(false);
        this.selectedOffering.set(null);
        this.toast.success(approving
          ? `«${offering.title}» fue aprobada y aparecerá en el catálogo cuando tenga horarios.`
          : `«${offering.title}» fue rechazada. El proveedor verá el estado en su panel.`);
        this.loadOfferings();
      },
      error: e => {
        this.offeringSaving.set(false);
        this.toast.error(errorMessage(e, 'No fue posible guardar la revisión.'));
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
