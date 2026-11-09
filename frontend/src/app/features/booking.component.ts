import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AvailabilitySlot, Offering, OfferingService } from '../core/offering.service';
import { apiBase } from '../core/api';
import { CurrencyPipe } from '@angular/common';
import { SERVICE_CONTENT, categoryKey } from './service-detail/service-content';
import { formatDuration, formatTime, formatTimeRange, endOf, levelLabel } from '../shared/offering-format';
import { businessDateFormat, businessDayKey } from '../shared/business-time';

const dayFormat = businessDateFormat({ weekday: 'short', day: 'numeric', month: 'short' });
const fullDayFormat = businessDateFormat({ weekday: 'long', day: 'numeric', month: 'long' });
// Solo la primera letra en mayúscula ("Sáb, 10 de oct"); text-transform: capitalize afectaría cada palabra
const capitalize = (text: string) => text.charAt(0).toUpperCase() + text.slice(1);

@Component({
  standalone: true,
  imports: [RouterLink, CurrencyPipe],
  template: `
    <section class="container section">
      <div class="card booking-card">
        <p class="eyebrow">Reserva</p>
        <h1>Reservar una sesión</h1>
        <p class="muted">Elige uno de los horarios que publicó el especialista.</p>

        @if (notFound()) {
          <div class="empty">
            Esta mentoría no existe o ya no está disponible.
            <a routerLink="/servicios">Ver otras mentorías →</a>
          </div>
        }

        @if (offering(); as o) {
          <div class="offering" [class]="'offering offering--' + category()">
            <span class="offering__icon" aria-hidden="true"><img [src]="icon()" alt=""></span>
            <div class="offering__body">
              <span class="offering__category">{{ o.category }}</span>
              <h2>{{ o.title }}</h2>
              <span class="offering__profile">{{ durationText() }} · Nivel {{ levelText().toLowerCase() }}</span>
              <a [routerLink]="['/services', o.id]">Ver detalle</a>
            </div>
            <strong class="offering__price">{{ o.price | currency:'COP':'symbol-narrow':'1.0-0' }}<small>/ sesión</small></strong>
          </div>
        }

        @if (offeringId()) {
          <div class="field">
            Horario
            @if (slotsLoading()) {
              <p class="muted">Cargando horarios…</p>
            } @else if (!days().length) {
              <p class="empty">
                Esta mentoría no tiene horarios disponibles por ahora.
                <a routerLink="/servicios">Ver otras mentorías →</a>
              </p>
            } @else {
              <div class="days" role="group" aria-label="Días disponibles">
                @for (day of days(); track day.key) {
                  <button type="button" class="day" [class.active]="day.key === selectedDay()"
                          [attr.aria-pressed]="day.key === selectedDay()" (click)="selectDay(day.key)">
                    {{ day.label }}
                    <span>{{ day.slots.length }} {{ day.slots.length === 1 ? 'horario' : 'horarios' }}</span>
                  </button>
                }
              </div>
              <div class="times" role="group" aria-label="Horas disponibles">
                @for (slot of dayTimes(); track slot.id) {
                  <button type="button" class="time" [class.active]="slot.id === selectedSlot()?.id"
                          [attr.aria-pressed]="slot.id === selectedSlot()?.id" (click)="selectedSlot.set(slot)">
                    {{ time(slot) }}
                  </button>
                }
              </div>
            }
          </div>
        }

        @if (selectedSlot(); as slot) {
          <p class="summary">Vas a reservar <strong>{{ selectedTitle() }}</strong> el <strong>{{ fullDate(slot) }}</strong></p>
        }

        @if (!notFound()) {
          <button class="btn" [disabled]="loading() || !selectedSlot()" (click)="book()">
            {{ loading() ? 'Reservando…' : 'Confirmar reserva' }}
          </button>
        }

        @if (error()) { <p class="error">{{ error() }}</p> }
        @if (success()) {
          <p class="success">{{ success() }} <a routerLink="/bookings/me">Ver mis reservas →</a></p>
        }
      </div>
    </section>
  `,
  styles: [`
    .section{padding:50px 0}.booking-card{max-width:700px;margin:auto}
    .eyebrow{font-weight:800;letter-spacing:.1em;text-transform:uppercase;color:#2563eb;font-size:.75rem}
    .offering{display:grid;grid-template-columns:auto 1fr auto;gap:14px;align-items:center;padding:14px;margin:18px 0;border:1px solid #e2e8f0;border-radius:12px;background:#f8fafc}
    .offering__icon{display:grid;place-items:center;width:46px;height:46px;border-radius:12px;background:#fffbeb}
    .offering--cloud .offering__icon{background:#eff6ff}.offering--frontend .offering__icon{background:#fff1f2}
    .offering__icon img{width:24px;height:24px;object-fit:contain}
    .offering__category{font-size:.72rem;font-weight:700;letter-spacing:.06em;text-transform:uppercase;color:#64748b}
    .offering h2{margin:2px 0;font-size:1.1rem}
    .offering a{font-size:.85rem;font-weight:600;color:#2563eb}
    .offering__profile{display:block;margin-bottom:2px;font-size:.82rem;color:#64748b}
    .offering__price{font-size:1.15rem;white-space:nowrap}.offering__price small{margin-left:4px;font-weight:500;color:#64748b}
    @media (max-width:560px){.offering{grid-template-columns:auto 1fr}.offering__price{grid-column:1/-1}}
    .days{display:flex;gap:8px;overflow-x:auto;padding-bottom:4px;scrollbar-width:thin}
    .day{flex-shrink:0;display:grid;gap:2px;padding:10px 14px;border:1px solid #cfd8e6;border-radius:10px;background:#fff;cursor:pointer;font-weight:600;text-align:left}
    .day span{font-size:.75rem;font-weight:500;color:#667085}
    .day.active{border-color:#2563eb;background:#eff6ff;color:#1d4ed8}
    .times{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:8px;margin-top:10px}
    .time{height:40px;border:1px solid #cfd8e6;border-radius:9px;background:#fff;cursor:pointer;font-weight:600}
    .time:hover,.day:hover{border-color:#93c5fd}
    .time.active{background:#2563eb;border-color:#2563eb;color:#fff}
    .empty{margin:0;padding:12px;border-radius:10px;background:#fffbeb;color:#92400e;font-weight:400}
    .empty a{display:inline-block;margin-top:4px;font-weight:600;color:#92400e;text-decoration:underline}
    .summary{padding:12px;border-radius:10px;background:#f0f9ff;color:#0c4a6e}
    .btn:disabled{opacity:.6;cursor:not-allowed}
    .success a{font-weight:600;color:#067647;text-decoration:underline}
  `]
})
export class BookingComponent implements OnInit {
  private readonly offeringsService = inject(OfferingService);
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /** Mentoría fija que llega desde el detalle (/book?offering=<id>) */
  readonly offering = signal<Offering | null>(null);
  readonly offeringId = computed(() => this.offering()?.id ?? '');
  readonly selectedTitle = computed(() => this.offering()?.title ?? '');
  readonly category = computed(() => categoryKey(this.offering()?.category ?? ''));
  readonly icon = computed(() => SERVICE_CONTENT[this.category()].icon);
  // HU-20: cada horario muestra inicio y fin según la duración de la mentoría
  readonly duration = computed(() => this.offering()?.durationMinutes ?? 60);
  readonly durationText = computed(() => formatDuration(this.duration()));
  readonly levelText = computed(() => levelLabel(this.offering()?.level));
  readonly slots = signal<AvailabilitySlot[]>([]);
  readonly slotsLoading = signal(false);
  readonly selectedDay = signal('');
  readonly selectedSlot = signal<AvailabilitySlot | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly notFound = signal(false);

  readonly days = computed(() => {
    const groups = new Map<string, AvailabilitySlot[]>();
    for (const slot of this.slots()) groups.set(businessDayKey(slot.scheduledAt), [...(groups.get(businessDayKey(slot.scheduledAt)) ?? []), slot]);
    return [...groups.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, slots]) => ({ key, label: capitalize(dayFormat.format(new Date(slots[0].scheduledAt)).replace(/\./g, '')), slots }));
  });
  readonly dayTimes = computed(() => this.days().find(d => d.key === this.selectedDay())?.slots ?? []);

  ngOnInit(): void {
    const id = this.route.snapshot.queryParamMap.get('offering');
    // Sin mentoría no hay nada que reservar: se elige desde el catálogo.
    // replaceUrl evita que "atrás" vuelva a /book y redirija otra vez
    if (!id) {
      this.router.navigate(['/servicios'], { replaceUrl: true });
      return;
    }
    this.offeringsService.list().subscribe({
      next: list => {
        const found = list.find(o => o.id === id) ?? null;
        this.offering.set(found);
        if (found) this.loadSlots(found.id);
        else this.notFound.set(true);
      },
      error: () => this.error.set('No fue posible cargar la mentoría.')
    });
  }

  private loadSlots(id: string): void {
    this.selectedSlot.set(null);
    this.error.set('');
    this.success.set('');
    this.slotsLoading.set(true);
    this.offeringsService.availableSlots(id).subscribe({
      next: slots => {
        this.slots.set([...slots].sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt)));
        this.selectedDay.set(this.days()[0]?.key ?? '');
        this.slotsLoading.set(false);
      },
      error: () => {
        this.slots.set([]);
        this.slotsLoading.set(false);
        this.error.set('No fue posible cargar los horarios de este servicio.');
      }
    });
  }

  selectDay(key: string): void {
    this.selectedDay.set(key);
    this.selectedSlot.set(null);
  }

  /** "9:00 a. m. – 10:00 a. m." */
  time(slot: AvailabilitySlot): string { return formatTimeRange(slot.scheduledAt, this.duration()); }
  /** "lunes, 13 de octubre de 9:00 a. m. a 10:00 a. m." */
  fullDate(slot: AvailabilitySlot): string {
    const end = endOf(slot.scheduledAt, this.duration());
    return `${fullDayFormat.format(new Date(slot.scheduledAt))} de ${formatTime(slot.scheduledAt)} a ${formatTime(end)}`;
  }

  book(): void {
    const slot = this.selectedSlot();
    if (!slot) return;
    this.error.set('');
    this.success.set('');
    this.loading.set(true);

    // Se envía exactamente la fecha del horario publicado, que es lo que valida el backend
    this.http.post<{ id: string }>(`${apiBase()}/bookings`, { offeringId: this.offeringId(), scheduledAt: slot.scheduledAt })
      .subscribe({
        next: () => {
          this.success.set('¡Reserva creada!');
          this.loading.set(false);
          // El horario ya no está disponible para nadie más
          this.slots.update(list => list.filter(s => s.id !== slot.id));
          this.selectedSlot.set(null);
          if (!this.dayTimes().length) this.selectedDay.set(this.days()[0]?.key ?? '');
        },
        error: e => {
          this.error.set(e?.error?.detail || 'No fue posible crear la reserva.');
          this.loading.set(false);
        }
      });
  }
}
