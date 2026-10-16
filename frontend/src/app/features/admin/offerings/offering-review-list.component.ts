import { Component, inject, signal } from '@angular/core';
import { AdminOfferingsService } from '../data/admin-offerings.service';
import { AdminOffering, ReviewAction } from '../data/admin-offering.model';
import { AvatarComponent } from '../shared/avatar.component';
import { OfferingReviewDialogComponent } from '../shared/offering-review-dialog.component';
import { ToastService } from '../shared/toast.service';
import { errorMessage } from '../shared/format';
import { formatDays, formatDuration, formatPrice, levelLabel } from '../../../shared/offering-format';

/** HU-21: cola de mentorías que esperan la revisión del administrador antes de llegar al catálogo. */
@Component({
  standalone: true,
  imports: [AvatarComponent, OfferingReviewDialogComponent],
  templateUrl: './offering-review-list.component.html',
  styleUrls: ['./offering-review-list.component.css']
})
export class OfferingReviewListComponent {
  private service = inject(AdminOfferingsService);
  private toast = inject(ToastService);

  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly pending = signal<AdminOffering[]>([]);

  protected readonly selected = signal<AdminOffering | null>(null);
  protected readonly action = signal<ReviewAction>('approve');
  protected readonly saving = signal(false);

  protected readonly level = (o: AdminOffering) => levelLabel(o.level);
  protected readonly duration = (o: AdminOffering) => formatDuration(o.durationMinutes);
  protected readonly days = (o: AdminOffering) => formatDays(o.workingDays);
  protected readonly price = (o: AdminOffering) => formatPrice(o.price);

  constructor() { this.load(); }

  protected load() {
    this.loading.set(true);
    this.error.set('');
    this.service.pending().subscribe({
      next: rows => { this.pending.set(rows); this.loading.set(false); },
      error: e => { this.error.set(errorMessage(e, 'No fue posible cargar las mentorías pendientes.')); this.loading.set(false); }
    });
  }

  protected ask(offering: AdminOffering, action: ReviewAction) {
    this.action.set(action);
    this.selected.set(offering);
  }

  protected confirm(reason: string) {
    const offering = this.selected();
    if (!offering) return;
    const approving = this.action() === 'approve';
    this.saving.set(true);
    const request = approving ? this.service.approve(offering.id) : this.service.reject(offering.id, reason);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.selected.set(null);
        // Ya no está pendiente: sale de la cola sin recargar toda la lista
        this.pending.update(rows => rows.filter(o => o.id !== offering.id));
        this.toast.success(approving
          ? `«${offering.title}» fue aprobada y aparecerá en el catálogo cuando tenga horarios.`
          : `«${offering.title}» fue rechazada. ${offering.providerName ?? 'El proveedor'} verá el estado en su panel.`);
      },
      error: e => {
        this.saving.set(false);
        this.toast.error(errorMessage(e, 'No fue posible guardar la revisión.'));
      }
    });
  }
}
