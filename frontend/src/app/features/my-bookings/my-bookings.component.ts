import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of, catchError } from 'rxjs';
import { Booking, BookingService, BookingStatus } from '../../core/booking.service';
import { Offering, OfferingService } from '../../core/offering.service';
import { AlertService } from '../../shared/alert.service';
import { RevealDirective } from '../../shared/reveal.directive';
import { SERVICE_CONTENT, categoryKey } from '../service-detail/service-content';

type Tab = 'upcoming' | 'past' | 'cancelled' | 'all';
type LoadStatus = 'loading' | 'ready' | 'error';

// El backend solo permite cancelar con al menos 24 horas de antelación (Booking.cancel)
const CANCEL_WINDOW_MS = 24 * 60 * 60 * 1000;

const STATUS_INFO: Record<BookingStatus, { label: string; hint: string }> = {
  CREATED: { label: 'Pendiente de pago', hint: 'Paga la reserva para confirmar tu sesión' },
  CONFIRMED: { label: 'Confirmada', hint: 'Todo listo para tu sesión' },
  CANCELLED: { label: 'Cancelada', hint: 'Esta reserva fue cancelada' },
  COMPLETED: { label: 'Completada', hint: 'Sesión finalizada' }
};

const dayFormat = new Intl.DateTimeFormat('es-CO', { day: '2-digit' });
const monthFormat = new Intl.DateTimeFormat('es-CO', { month: 'short' });
const weekdayFormat = new Intl.DateTimeFormat('es-CO', { weekday: 'long' });
const fullFormat = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long', hour: 'numeric', minute: '2-digit' });
const timeFormat = new Intl.DateTimeFormat('es-CO', { hour: 'numeric', minute: '2-digit' });
const relativeFormat = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });
// Solo la primera letra en mayúscula ("Viernes, 9 de octubre"); text-transform: capitalize afectaría cada palabra
const capitalize = (text: string) => text.charAt(0).toUpperCase() + text.slice(1);

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [RouterLink, RevealDirective],
  templateUrl: './my-bookings.component.html',
  styleUrls: ['./my-bookings.component.css']
})
export class MyBookingsComponent implements OnInit {
  private readonly bookingService = inject(BookingService);
  private readonly offeringService = inject(OfferingService);
  private readonly alerts = inject(AlertService);

  readonly status = signal<LoadStatus>('loading');
  readonly bookings = signal<Booking[]>([]);
  readonly offerings = signal(new Map<string, Offering>());
  readonly tab = signal<Tab>('upcoming');
  readonly cancelingIds = signal(new Set<string>());
  readonly skeletons = [1, 2, 3];

  readonly tabs: { id: Tab; label: string }[] = [
    { id: 'upcoming', label: 'Próximas' },
    { id: 'past', label: 'Pasadas' },
    { id: 'cancelled', label: 'Canceladas' },
    { id: 'all', label: 'Todas' }
  ];

  private readonly groups = computed(() => {
    const now = Date.now();
    const time = (b: Booking) => new Date(b.scheduledAt).getTime();
    const all = this.bookings();
    const upcoming = all
      .filter(b => (b.status === 'CREATED' || b.status === 'CONFIRMED') && time(b) >= now)
      .sort((a, b) => time(a) - time(b));
    const cancelled = all.filter(b => b.status === 'CANCELLED').sort((a, b) => time(b) - time(a));
    const past = all
      .filter(b => b.status === 'COMPLETED' || (b.status !== 'CANCELLED' && time(b) < now))
      .sort((a, b) => time(b) - time(a));
    return { upcoming, past, cancelled, all: [...upcoming, ...past, ...cancelled] };
  });

  readonly counts = computed(() => {
    const g = this.groups();
    return { upcoming: g.upcoming.length, past: g.past.length, cancelled: g.cancelled.length, all: g.all.length };
  });
  readonly visible = computed(() => this.groups()[this.tab()]);
  readonly next = computed(() => this.groups().upcoming[0] ?? null);

  ngOnInit(): void { this.load(); }

  load(): void {
    this.status.set('loading');
    // Si el catálogo falla, las reservas se muestran igual con un título genérico
    forkJoin({
      bookings: this.bookingService.getMyBookings(),
      offerings: this.offeringService.list().pipe(catchError(() => of([] as Offering[])))
    }).subscribe({
      next: ({ bookings, offerings }) => {
        this.bookings.set(bookings);
        this.offerings.set(new Map(offerings.map(o => [o.id, o])));
        // Si no hay próximas, se abre directamente la pestaña con contenido
        if (!this.groups().upcoming.length && bookings.length) this.tab.set('all');
        this.status.set('ready');
      },
      error: () => this.status.set('error')
    });
  }

  async cancel(booking: Booking): Promise<void> {
    const confirmed = await this.alerts.confirm({
      title: '¿Cancelar esta reserva?',
      message: `${this.title(booking)} (${fullFormat.format(new Date(booking.scheduledAt))}). La sesión quedará cancelada y no podrás reactivarla.`,
      confirmText: 'Sí, cancelar',
      cancelText: 'Volver',
      danger: true
    });
    if (!confirmed) return;

    this.cancelingIds.update(ids => new Set(ids).add(booking.id));
    this.bookingService.cancelBooking(booking.id).subscribe({
      next: updated => {
        this.bookings.update(list => list.map(b => b.id === updated.id ? updated : b));
        this.stopCanceling(booking.id);
        this.alerts.success('Reserva cancelada', 'Tu reserva se canceló correctamente.');
      },
      error: e => {
        this.stopCanceling(booking.id);
        this.alerts.error('No se pudo cancelar', e?.error?.detail || 'Ocurrió un error al cancelar la reserva.');
      }
    });
  }

  private stopCanceling(id: string): void {
    this.cancelingIds.update(ids => { const copy = new Set(ids); copy.delete(id); return copy; });
  }

  // ---- Ayudas para la plantilla ----

  offering(b: Booking): Offering | undefined { return this.offerings().get(b.offeringId); }
  title(b: Booking): string { return this.offering(b)?.title ?? 'Servicio no disponible'; }
  category(b: Booking): string { return categoryKey(this.offering(b)?.category ?? ''); }
  icon(b: Booking): string { return SERVICE_CONTENT[categoryKey(this.offering(b)?.category ?? '')].icon; }

  statusLabel(b: Booking): string { return STATUS_INFO[b.status].label; }
  statusHint(b: Booking): string { return STATUS_INFO[b.status].hint; }

  day(b: Booking): string { return dayFormat.format(new Date(b.scheduledAt)); }
  month(b: Booking): string { return monthFormat.format(new Date(b.scheduledAt)).replace('.', ''); }
  weekday(b: Booking): string { return capitalize(weekdayFormat.format(new Date(b.scheduledAt))); }
  time(b: Booking): string { return timeFormat.format(new Date(b.scheduledAt)); }
  fullDate(b: Booking): string { return capitalize(fullFormat.format(new Date(b.scheduledAt))); }

  relative(b: Booking): string {
    const diff = new Date(b.scheduledAt).getTime() - Date.now();
    const hours = Math.round(diff / 3_600_000);
    if (Math.abs(hours) < 24) return relativeFormat.format(hours, 'hour');
    return relativeFormat.format(Math.round(diff / 86_400_000), 'day');
  }

  isActive(b: Booking): boolean { return b.status === 'CREATED' || b.status === 'CONFIRMED'; }
  canCancel(b: Booking): boolean {
    return this.isActive(b) && new Date(b.scheduledAt).getTime() - Date.now() > CANCEL_WINDOW_MS;
  }
  /** Reserva activa y futura que ya está dentro de las 24 horas: se explica por qué no se puede cancelar */
  tooLateToCancel(b: Booking): boolean {
    const diff = new Date(b.scheduledAt).getTime() - Date.now();
    return this.isActive(b) && diff > 0 && diff <= CANCEL_WINDOW_MS;
  }
  /** Solo las reservas pendientes y futuras se pueden pagar (el backend también lo valida) */
  canPay(b: Booking): boolean {
    return b.status === 'CREATED' && new Date(b.scheduledAt).getTime() > Date.now();
  }
  isCanceling(b: Booking): boolean { return this.cancelingIds().has(b.id); }
}
