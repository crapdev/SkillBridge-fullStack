import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { Offering, OfferingService } from '../../core/offering.service';
import { ProviderStats, ProviderStatsService } from './provider-stats.service';
import { REVIEW_ESTIMATE, REVIEW_STATUS, reviewStatusOf } from '../../shared/offering-review';
import { formatPrice } from '../../shared/offering-format';

/** Mentorías que se muestran en el resumen; la lista completa está en /provider/offerings */
const OFFERINGS_PREVIEW = 6;

/** HU-22: dashboard del proveedor con sus indicadores y accesos a crear, editar y gestionar horarios. */
@Component({
  standalone: true,
  imports: [RouterLink],
  templateUrl: './provider-dashboard.component.html',
  styleUrls: ['./provider-dashboard.component.css']
})
export class ProviderDashboardComponent {
  private statsService = inject(ProviderStatsService);
  private offeringService = inject(OfferingService);

  protected readonly stats = signal<ProviderStats | null>(null);
  protected readonly statsError = signal(false);
  protected readonly offerings = signal<Offering[] | null>(null);
  protected readonly offeringsError = signal(false);

  // Primero lo que necesita atención (rechazadas, en revisión) y luego las aprobadas
  protected readonly preview = computed(() => {
    const order = { REJECTED: 0, PENDING_REVIEW: 1, APPROVED: 2 };
    return [...(this.offerings() ?? [])]
      .sort((a, b) => order[reviewStatusOf(a)] - order[reviewStatusOf(b)])
      .slice(0, OFFERINGS_PREVIEW);
  });
  protected readonly hasMore = computed(() => (this.offerings()?.length ?? 0) > OFFERINGS_PREVIEW);

  protected readonly reviewEstimate = REVIEW_ESTIMATE;
  protected readonly status = (o: Offering) => REVIEW_STATUS[reviewStatusOf(o)];
  /** Mismos colores que el panel de administración (clases adm-chip--*) */
  protected readonly chip = (o: Offering) => `adm-chip--${this.status(o).tone}`;
  protected readonly price = (o: Offering) => formatPrice(o.price);
  protected readonly plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

  constructor() { this.load(); }

  protected load() {
    this.statsError.set(false);
    this.offeringsError.set(false);
    this.statsService.stats().pipe(catchError(() => { this.statsError.set(true); return of(null); }))
      .subscribe(s => this.stats.set(s));
    this.offeringService.getProviderOfferings().pipe(catchError(() => { this.offeringsError.set(true); return of(null); }))
      .subscribe(list => this.offerings.set(list));
  }
}
