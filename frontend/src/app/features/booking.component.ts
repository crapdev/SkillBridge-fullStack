import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AvailabilitySlot, Offering, OfferingService } from '../core/offering.service';
import { apiBase } from '../core/api';

const dayFormat = new Intl.DateTimeFormat('es-CO', { weekday: 'short', day: 'numeric', month: 'short' });
const fullFormat = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long', hour: 'numeric', minute: '2-digit' });
const timeFormat = new Intl.DateTimeFormat('es-CO', { hour: 'numeric', minute: '2-digit' });
const pad = (n: number) => String(n).padStart(2, '0');
// Solo la primera letra en mayúscula ("Sáb, 10 de oct"); text-transform: capitalize afectaría cada palabra
const capitalize = (text: string) => text.charAt(0).toUpperCase() + text.slice(1);
const dayKey = (iso: string) => { const d = new Date(iso); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; };

@Component({
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="container section">
      <div class="card booking-card">
        <p class="eyebrow">Reserva</p>
        <h1>Reservar una sesión</h1>
        <p class="muted">Elige la mentoría y uno de los horarios que publicó el especialista.</p>

        <label class="field">
          Servicio
          <select [value]="offeringId()" (change)="selectOffering($any($event.target).value)">
            <option value="" disabled>Selecciona un servicio</option>
            @for (offering of offerings(); track offering.id) {
              <option [value]="offering.id">{{ offering.title }}</option>
            }
          </select>
        </label>

        @if (offeringId()) {
          <div class="field">
            Horario
            @if (slotsLoading()) {
              <p class="muted">Cargando horarios…</p>
            } @else if (!days().length) {
              <p class="empty">Este servicio no tiene horarios disponibles por ahora. Prueba con otra mentoría o vuelve más tarde.</p>
            } @else {
              <div class="days" role="group" aria-label="Días disponibles">
                @for (day of days(); track day.key) {
                  <button type="button" class="day" [class.active]="day.key === selectedDay()"
                          [attr.aria-pressed]="day.key === selectedDay()" (click)="selectDay(day.key)">
                    {{ day.label }}
                    <span>{{ day.slots.length }} {{ day.slots.length === 1 ? 'hora' : 'horas' }}</span>
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

        <button class="btn" [disabled]="loading() || !selectedSlot()" (click)="book()">
          {{ loading() ? 'Reservando…' : 'Confirmar reserva' }}
        </button>

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
    .field select{border:1px solid #cfd8e6;border-radius:10px;padding:11px;background:white}
    .days{display:flex;gap:8px;overflow-x:auto;padding-bottom:4px;scrollbar-width:thin}
    .day{flex-shrink:0;display:grid;gap:2px;padding:10px 14px;border:1px solid #cfd8e6;border-radius:10px;background:#fff;cursor:pointer;font-weight:600;text-align:left}
    .day span{font-size:.75rem;font-weight:500;color:#667085}
    .day.active{border-color:#2563eb;background:#eff6ff;color:#1d4ed8}
    .times{display:grid;grid-template-columns:repeat(auto-fill,minmax(96px,1fr));gap:8px;margin-top:10px}
    .time{height:40px;border:1px solid #cfd8e6;border-radius:9px;background:#fff;cursor:pointer;font-weight:600}
    .time:hover,.day:hover{border-color:#93c5fd}
    .time.active{background:#2563eb;border-color:#2563eb;color:#fff}
    .empty{margin:0;padding:12px;border-radius:10px;background:#fffbeb;color:#92400e;font-weight:400}
    .summary{padding:12px;border-radius:10px;background:#f0f9ff;color:#0c4a6e}
    .btn:disabled{opacity:.6;cursor:not-allowed}
    .success a{font-weight:600;color:#067647;text-decoration:underline}
  `]
})
export class BookingComponent implements OnInit {
  private readonly offeringsService = inject(OfferingService);
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);

  readonly offerings = signal<Offering[]>([]);
  readonly offeringId = signal('');
  readonly slots = signal<AvailabilitySlot[]>([]);
  readonly slotsLoading = signal(false);
  readonly selectedDay = signal('');
  readonly selectedSlot = signal<AvailabilitySlot | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly success = signal('');

  readonly days = computed(() => {
    const groups = new Map<string, AvailabilitySlot[]>();
    for (const slot of this.slots()) groups.set(dayKey(slot.scheduledAt), [...(groups.get(dayKey(slot.scheduledAt)) ?? []), slot]);
    return [...groups.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, slots]) => ({ key, label: capitalize(dayFormat.format(new Date(slots[0].scheduledAt)).replace(/\./g, '')), slots }));
  });
  readonly dayTimes = computed(() => this.days().find(d => d.key === this.selectedDay())?.slots ?? []);
  readonly selectedTitle = computed(() => this.offerings().find(o => o.id === this.offeringId())?.title ?? '');

  ngOnInit(): void {
    this.offeringsService.list().subscribe({
      next: list => {
        this.offerings.set(list);
        // Viene preseleccionado desde el detalle del servicio (/book?offering=<id>)
        const preselected = this.route.snapshot.queryParamMap.get('offering');
        if (preselected && list.some(o => o.id === preselected)) this.selectOffering(preselected);
      },
      error: () => this.error.set('No fue posible cargar los servicios.')
    });
  }

  selectOffering(id: string): void {
    this.offeringId.set(id);
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

  time(slot: AvailabilitySlot): string { return timeFormat.format(new Date(slot.scheduledAt)); }
  fullDate(slot: AvailabilitySlot): string { return fullFormat.format(new Date(slot.scheduledAt)); }

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
