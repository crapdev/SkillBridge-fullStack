import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin, of, catchError, map } from 'rxjs';
import { AvailabilitySlot, Offering, OfferingService } from '../../core/offering.service';
import { AlertService } from '../../shared/alert.service';
import { dayLabel, formatDays, formatDuration, formatTimeRange, weekDayOf } from '../../shared/offering-format';

type LoadStatus = 'loading' | 'ready' | 'not-found' | 'error';

/**
 * Jornada en la que se proponen horarios: la primera sesión empieza a las 7:00 y la última debe terminar
 * a más tardar a las 21:00. Las opciones avanzan de a una sesión (30 min → 7:00, 7:30…; 90 min → 7:00, 8:30…),
 * así las que se proponen encajan una detrás de otra sin cruzarse.
 */
const DAY_START = 7 * 60;
const DAY_END = 21 * 60;

/** Grupos de la cuadrícula para que muchas opciones (sesiones cortas) no sean una lista eterna */
const PERIODS = [
  { label: 'Mañana', until: 12 * 60 },
  { label: 'Tarde', until: 18 * 60 },
  { label: 'Noche', until: 24 * 60 }
];

const toMinutes = (time: string) => { const [h, m] = time.split(':').map(Number); return h * 60 + m; };
const toTime = (minutes: number) => `${String(Math.floor(minutes / 60) % 24).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;

/**
 * Las horas marcadas se pueden repetir en todos los días que ofrece la mentoría durante varias semanas,
 * así el proveedor no tiene que publicarlas día por día. 0 = solo el día elegido.
 */
export const REPEAT_OPTIONS = [
  { weeks: 0, label: 'Solo este día' },
  { weeks: 1, label: '1 semana' },
  { weeks: 2, label: '2 semanas' },
  { weeks: 4, label: '4 semanas' },
  { weeks: 8, label: '8 semanas' }
];
/** Mismo límite que el backend por petición */
export const MAX_BATCH = 500;
/** Motivos de horarios omitidos que se muestran; el resto se resume en "y N más" */
const MAX_SKIPPED_SHOWN = 4;

const dayFormat = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long' });
const shortDayFormat = new Intl.DateTimeFormat('es-CO', { weekday: 'short', day: 'numeric', month: 'short' });
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

  // ---- Repetir las horas en los días que ofrece la mentoría ----
  readonly repeatOptions = REPEAT_OPTIONS;
  readonly maxBatch = MAX_BATCH;
  readonly repeatWeeks = signal(4);

  /** Días en que se publicarán las horas marcadas: el elegido o, al repetir, cada día ofrecido de esas semanas */
  readonly targetDates = computed(() => {
    const weeks = this.repeatWeeks();
    if (!weeks) return [this.date()];
    const dates: string[] = [];
    const d = this.dateOf(this.date());
    for (let i = 0; i < weeks * 7; i++) {
      if (this.workingDays().includes(weekDayOf(d))) dates.push(toDateInput(d));
      d.setDate(d.getDate() + 1);
    }
    return dates;
  });

  /** Cuántos horarios se enviarán (los que ya pasaron no cuentan) */
  readonly plannedCount = computed(() => this.plannedInstants().length);

  /** "del 14 de oct. al 8 de nov." para el resumen de la repetición */
  readonly repeatRange = computed(() => {
    const dates = this.targetDates();
    const format = (date: string) => shortDayFormat.format(this.dateOf(date));
    return dates.length > 1 ? `del ${format(dates[0])} al ${format(dates[dates.length - 1])}` : '';
  });

  /**
   * Horas de inicio que se proponen según la duración de la mentoría, más las personalizadas que agregó el
   * proveedor y las ya publicadas ese día (aunque no encajen en la cuadrícula, para que se vean como publicadas).
   */
  readonly times = computed(() => {
    const step = this.duration();
    const suggested: string[] = [];
    for (let start = DAY_START; start + step <= DAY_END; start += step) suggested.push(toTime(start));
    const published = this.slots()
      .filter(s => toDateInput(new Date(s.scheduledAt)) === this.date())
      .map(s => toTime(new Date(s.scheduledAt).getHours() * 60 + new Date(s.scheduledAt).getMinutes()));
    return [...new Set([...suggested, ...published, ...this.selectedTimes()])].sort();
  });

  /** La cuadrícula agrupada en mañana, tarde y noche (sin grupos vacíos) */
  readonly periods = computed(() => {
    let from = 0;
    return PERIODS.map(p => {
      const times = this.times().filter(t => toMinutes(t) >= from && toMinutes(t) < p.until);
      from = p.until;
      return { label: p.label, times };
    }).filter(p => p.times.length);
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
    // Una hora marcada siempre se puede desmarcar, aunque otra selección la haya dejado "cruzada"
    if (this.isSelected(time)) {
      this.selectedTimes.update(times => { const copy = new Set(times); copy.delete(time); return copy; });
      return;
    }
    if (!this.dayAllowed() || this.isPast(time) || this.isPublished(time) || this.overlaps(time)) return;
    this.selectedTimes.update(times => {
      const copy = new Set(times);
      copy.has(time) ? copy.delete(time) : copy.add(time);
      return copy;
    });
  }

  addCustomTime(): void {
    const time = this.customTime();
    if (!time || !this.dayAllowed() || this.isPast(time) || this.isPublished(time) || this.overlaps(time)) return;
    this.selectedTimes.update(times => new Set(times).add(time));
    this.customTime.set('');
  }

  isSelected(time: string): boolean { return this.selectedTimes().has(time); }

  /** Hora en que termina una sesión que empieza a esa hora: "13:00" con 90 minutos → "14:30" */
  endOf(time: string): string { return toTime(toMinutes(time) + this.duration()); }

  /** Por qué no se puede elegir una hora (vacío = se puede) */
  blockedReason(time: string): string {
    if (this.isPublished(time)) return 'Ya publicado';
    if (this.isPast(time)) return 'Esta hora ya pasó';
    if (this.overlaps(time) && !this.isSelected(time)) return `Se cruza con otra sesión (cada una dura ${this.durationText()})`;
    return '';
  }

  /**
   * Cada sesión ocupa de su inicio a su fin (inicio + duración): una hora se bloquea si se cruzaría con un horario
   * ya publicado o con otra hora marcada. Los intervalos son [inicio, fin): se puede empezar justo al terminar la otra.
   * Aquí solo se conocen los horarios de esta mentoría; los cruces con otras del proveedor los rechaza el backend.
   */
  overlaps(time: string): boolean {
    const start = this.toInstant(time).getTime();
    const end = start + this.duration() * 60_000;
    const crosses = (otherStart: number) => start < otherStart + this.duration() * 60_000 && otherStart < end;
    return this.slots().some(s => crosses(new Date(s.scheduledAt).getTime()))
      || [...this.selectedTimes()].some(t => t !== time && crosses(this.toInstant(t).getTime()));
  }
  isPast(time: string): boolean { return this.toInstant(time).getTime() <= Date.now(); }
  isPublished(time: string): boolean {
    const iso = this.toInstant(time).toISOString();
    return this.slots().some(s => new Date(s.scheduledAt).toISOString() === iso);
  }

  // ---- Publicar y borrar ----

  setRepeat(weeks: number): void { this.repeatWeeks.set(weeks); }

  /**
   * Todas las horas marcadas en todos los días de destino van en una sola petición. El backend valida cada una
   * (cruces con cualquier mentoría del proveedor, duplicados…) y omite las que no se pueden publicar sin cancelar
   * el resto, así repetir varias semanas no falla por un solo día ocupado.
   */
  publish(): void {
    const instants = this.plannedInstants();
    if (!instants.length || instants.length > MAX_BATCH || this.publishing() || !this.dayAllowed()) return;
    this.publishing.set(true);

    this.offerings.addProviderSlots(this.offeringId, instants).subscribe({
      next: ({ created, skipped }) => {
        this.slots.update(list => [...list, ...created]);
        this.selectedTimes.set(new Set());
        this.publishing.set(false);

        if (created.length) {
          this.alerts.success(created.length === 1 ? 'Horario publicado' : `${created.length} horarios publicados`,
            'Los clientes ya pueden reservarlos.');
        }
        if (skipped.length) {
          const shown = skipped.slice(0, MAX_SKIPPED_SHOWN)
            .map(s => `${shortDayFormat.format(new Date(s.scheduledAt))} ${toTime(new Date(s.scheduledAt).getHours() * 60 + new Date(s.scheduledAt).getMinutes())}: ${s.reason}`);
          const more = skipped.length - shown.length;
          const title = skipped.length === 1 ? '1 horario no se publicó' : `${skipped.length} horarios no se publicaron`;
          const detail = shown.join(' · ') + (more > 0 ? ` · y ${more} más` : '');
          // Si al menos uno salió bien, lo omitido es un aviso; si no salió ninguno, es un error
          created.length ? this.alerts.info(title, detail) : this.alerts.error(title, detail);
        }
      },
      error: e => {
        this.publishing.set(false);
        this.alerts.error('No se pudieron publicar los horarios', e?.error?.detail || 'Inténtalo de nuevo.');
      }
    });
  }

  /** Inicio de cada horario a publicar (día de destino × hora marcada), sin los que ya pasaron */
  private plannedInstants(): string[] {
    const times = [...this.selectedTimes()].sort();
    const now = Date.now();
    return this.targetDates()
      .flatMap(date => times.map(time => this.instantOn(date, time)))
      .filter(instant => instant.getTime() > now)
      .map(instant => instant.toISOString());
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
    return this.instantOn(this.date(), time);
  }

  private instantOn(date: string, time: string): Date {
    return new Date(`${date}T${time}`);
  }
}
