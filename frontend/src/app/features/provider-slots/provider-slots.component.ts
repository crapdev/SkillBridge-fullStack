import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin, of, catchError, map } from 'rxjs';
import { AvailabilitySlot, Offering, OfferingService } from '../../core/offering.service';
import { AlertService } from '../../shared/alert.service';
import { dayLabel, formatDays, formatDuration, formatTimeRange, weekDayOf } from '../../shared/offering-format';

type LoadStatus = 'loading' | 'ready' | 'not-found' | 'error';

const PRESET_TIMES = ['07:00', '08:00', '09:00', '10:00', '11:00', '12:00', '13:00', '14:00',
  '15:00', '16:00', '17:00', '18:00', '19:00', '20:00'];

const dayFormat = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long' });
const capitalize = (text: string) => text.charAt(0).toUpperCase() + text.slice(1);
const pad = (n: number) => String(n).padStart(2, '0');
/** Fecha local en formato yyyy-mm-dd, el que usa <input type="date"> */
const toDateInput = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

@Component({
  standalone: true,
  imports: [RouterLink],
  templateUrl: './provider-slots.component.html',
  styleUrls: ['./provider-slots.component.css']
})
export class ProviderSlotsComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly offerings = inject(OfferingService);
  private readonly alerts = inject(AlertService);

  private readonly offeringId = this.route.snapshot.paramMap.get('id') ?? '';

  readonly status = signal<LoadStatus>('loading');
  readonly offering = signal<Offering | null>(null);
  readonly slots = signal<AvailabilitySlot[]>([]);
  readonly deletingIds = signal(new Set<string>());
  readonly publishing = signal(false);

  readonly today = toDateInput(new Date());
  readonly date = signal(toDateInput(new Date(Date.now() + 86_400_000)));
  readonly selectedTimes = signal(new Set<string>());
  readonly customTime = signal('');

  /** Horas sugeridas más las personalizadas que agregó el proveedor, en orden */
  readonly times = computed(() => {
    const all = new Set([...PRESET_TIMES, ...this.selectedTimes()]);
    return [...all].sort();
  });

  // ---- HU-20: días en que se ofrece la mentoría y duración de cada sesión ----
  readonly workingDays = computed(() => this.offering()?.workingDays ?? []);
  readonly duration = computed(() => this.offering()?.durationMinutes ?? 60);
  readonly daysText = computed(() => formatDays(this.workingDays()));
  readonly durationText = computed(() => formatDuration(this.duration()));
  /** El día elegido está entre los que ofrece la mentoría (mientras carga no se bloquea nada) */
  readonly dayAllowed = computed(() => !this.offering() || this.offersOn(this.date()));
  /** Plural para el aviso: "los sábados", "los lunes" */
  readonly selectedDayName = computed(() => {
    const name = dayLabel(weekDayOf(this.dateOf(this.date()))).toLowerCase();
    return name.endsWith('s') ? name : `${name}s`;
  });

  readonly freeCount = computed(() => this.slots().filter(s => !s.reserved).length);
  readonly reservedCount = computed(() => this.slots().filter(s => s.reserved).length);

  /** Horarios agrupados por día para la lista */
  readonly days = computed(() => {
    const groups = new Map<string, AvailabilitySlot[]>();
    for (const slot of [...this.slots()].sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt))) {
      const key = toDateInput(new Date(slot.scheduledAt));
      groups.set(key, [...(groups.get(key) ?? []), slot]);
    }
    return [...groups.entries()].map(([key, slots]) => ({
      key,
      label: capitalize(dayFormat.format(new Date(slots[0].scheduledAt))),
      slots
    }));
  });

  ngOnInit(): void { this.load(); }

  load(): void {
    this.status.set('loading');
    forkJoin({
      offering: this.offerings.getProviderOfferings().pipe(map(list => list.find(o => o.id === this.offeringId) ?? null)),
      slots: this.offerings.getProviderSlots(this.offeringId).pipe(catchError(() => of(null)))
    }).subscribe({
      next: ({ offering, slots }) => {
        if (!offering || !slots) {
          this.status.set('not-found');
          return;
        }
        this.offering.set(offering);
        this.slots.set(slots);
        // Si el día propuesto no se ofrece, se salta al siguiente que sí
        if (!this.offersOn(this.date())) this.date.set(this.nextAllowedDate(this.date()));
        this.status.set('ready');
      },
      error: () => this.status.set('error')
    });
  }

  // ---- Selección de horas ----

  setDate(value: string): void {
    this.date.set(value);
    // Al cambiar de día se descartan las horas que en ese día ya pasaron
    this.selectedTimes.update(times => new Set([...times].filter(t => !this.isPast(t))));
  }

  toggleTime(time: string): void {
    if (!this.dayAllowed() || this.isPast(time) || this.isPublished(time)) return;
    this.selectedTimes.update(times => {
      const copy = new Set(times);
      copy.has(time) ? copy.delete(time) : copy.add(time);
      return copy;
    });
  }

  addCustomTime(): void {
    const time = this.customTime();
    if (!time || !this.dayAllowed() || this.isPast(time) || this.isPublished(time)) return;
    this.selectedTimes.update(times => new Set(times).add(time));
    this.customTime.set('');
  }

  isSelected(time: string): boolean { return this.selectedTimes().has(time); }
  isPast(time: string): boolean { return this.toInstant(time).getTime() <= Date.now(); }
  isPublished(time: string): boolean {
    const iso = this.toInstant(time).toISOString();
    return this.slots().some(s => new Date(s.scheduledAt).toISOString() === iso);
  }

  // ---- Publicar y borrar ----

  publish(): void {
    const times = [...this.selectedTimes()].sort();
    if (!times.length || this.publishing() || !this.dayAllowed()) return;
    this.publishing.set(true);

    // Cada horario se publica por separado: si uno falla (p. ej. duplicado), los demás siguen
    forkJoin(times.map(time =>
      this.offerings.addProviderSlot(this.offeringId, this.toInstant(time).toISOString()).pipe(
        map(slot => ({ slot, error: null as string | null })),
        catchError(e => of({ slot: null, error: `${time}: ${e?.error?.detail || 'no se pudo publicar'}` }))
      )
    )).subscribe(results => {
      const created = results.flatMap(r => r.slot ? [r.slot] : []);
      const errors = results.flatMap(r => r.error ? [r.error] : []);
      this.slots.update(list => [...list, ...created]);
      this.selectedTimes.set(new Set());
      this.publishing.set(false);

      if (created.length) {
        this.alerts.success(created.length === 1 ? 'Horario publicado' : `${created.length} horarios publicados`,
          'Los clientes ya pueden reservarlos.');
      }
      if (errors.length) this.alerts.error('Algunos horarios no se publicaron', errors.join(' · '));
    });
  }

  remove(slot: AvailabilitySlot): void {
    this.deletingIds.update(ids => new Set(ids).add(slot.id));
    this.offerings.deleteProviderSlot(this.offeringId, slot.id).subscribe({
      next: () => {
        this.slots.update(list => list.filter(s => s.id !== slot.id));
        this.stopDeleting(slot.id);
      },
      error: e => {
        this.stopDeleting(slot.id);
        this.alerts.error('No se pudo eliminar', e?.error?.detail || 'Inténtalo de nuevo.');
      }
    });
  }

  isDeleting(slot: AvailabilitySlot): boolean { return this.deletingIds().has(slot.id); }
  /** "9:00 a. m. – 10:00 a. m.": hora de inicio y de finalización según la duración de la mentoría */
  time(slot: AvailabilitySlot): string { return formatTimeRange(slot.scheduledAt, this.duration()); }

  /** Lleva el selector al siguiente día que ofrece la mentoría */
  goToNextAllowedDay(): void { this.setDate(this.nextAllowedDate(this.date())); }

  private stopDeleting(id: string): void {
    this.deletingIds.update(ids => { const copy = new Set(ids); copy.delete(id); return copy; });
  }

  private offersOn(date: string): boolean {
    return this.workingDays().includes(weekDayOf(this.dateOf(date)));
  }

  private nextAllowedDate(from: string): string {
    const d = this.dateOf(from);
    for (let i = 0; i < 7; i++) {
      d.setDate(d.getDate() + 1);
      if (this.workingDays().includes(weekDayOf(d))) return toDateInput(d);
    }
    return from;
  }

  /** "yyyy-mm-dd" como fecha local (con new Date("yyyy-mm-dd") sería UTC y podría caer el día anterior) */
  private dateOf(date: string): Date {
    return new Date(`${date}T00:00`);
  }

  /** Combina el día elegido y una hora "HH:mm" en un instante de la zona horaria del navegador */
  private toInstant(time: string): Date {
    return new Date(`${this.date()}T${time}`);
  }
}
