import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, switchMap } from 'rxjs';
import { AdminAnalyticsService } from '../data/admin-analytics.service';
import {
  AnalyticsDataset, BOOKING_STATUS_LABELS, BookingStatus, SparkAnalytics, TasaCancelacionRow
} from '../data/admin-analytics.model';
import { errorMessage } from '../shared/format';

type SortKey = 'tasa' | 'reservas' | 'nombre';

// Orden y color fijos por estado: el color sigue al estado, no a su posición en el ranking
const STATUS_ORDER: BookingStatus[] = ['COMPLETED', 'CANCELLED', 'CONFIRMED', 'CREATED'];
const STATUS_COLORS: Record<BookingStatus, string> = {
  COMPLETED: '#2a78d6',
  CANCELLED: '#eb6834',
  CONFIRMED: '#1baf7a',
  CREATED: '#eda100'
};

/**
 * Analítica de datos: muestra los DataFrames que produce el pipeline de PySpark (data/) en
 * gráficas para el administrador. Sin librerías de gráficos: barras en HTML/CSS.
 */
@Component({
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './analytics.component.html',
  styleUrls: ['./analytics.component.css']
})
export class AnalyticsComponent {
  private service = inject(AdminAnalyticsService);
  private load$ = new Subject<AnalyticsDataset>();

  protected readonly statusLabels = BOOKING_STATUS_LABELS;
  protected readonly statusColors = STATUS_COLORS;

  protected readonly dataset = signal<AnalyticsDataset>('snapshot');
  protected readonly data = signal<SparkAnalytics | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  /** Tasa a partir de la cual un proveedor se marca en alerta */
  protected readonly threshold = signal(20);
  protected readonly sort = signal<SortKey>('tasa');
  protected readonly selectedId = signal<string | null>(null);
  protected readonly focusStatus = signal<BookingStatus | null>(null);
  protected readonly tableTasa = signal(false);
  protected readonly tableEstado = signal(false);

  // --- Reservas por estado ---
  protected readonly totalReservas = computed(() =>
    (this.data()?.reservasPorEstado ?? []).reduce((sum, r) => sum + r.reservas, 0));

  protected readonly estados = computed(() => {
    const rows = this.data()?.reservasPorEstado ?? [];
    const total = this.totalReservas();
    return STATUS_ORDER
      .map(status => rows.find(r => r.status === status))
      .filter(r => !!r)
      .map(r => ({ ...r, pct: total ? (r.reservas / total) * 100 : 0 }));
  });

  // --- Tasa de cancelación por proveedor ---
  protected readonly providers = computed(() => {
    const rows = [...(this.data()?.tasaCancelacion ?? [])];
    const by: Record<SortKey, (a: TasaCancelacionRow, b: TasaCancelacionRow) => number> = {
      tasa: (a, b) => b.tasa_cancelacion - a.tasa_cancelacion || b.reservas - a.reservas,
      reservas: (a, b) => b.reservas - a.reservas,
      nombre: (a, b) => a.provider_name.localeCompare(b.provider_name)
    };
    return rows.sort(by[this.sort()]);
  });

  /** La tasa global sale de sumar reservas y canceladas, no de promediar las tasas de cada proveedor */
  protected readonly global = computed(() => {
    const rows = this.data()?.tasaCancelacion ?? [];
    const reservas = rows.reduce((s, r) => s + r.reservas, 0);
    const canceladas = rows.reduce((s, r) => s + r.canceladas, 0);
    return { reservas, canceladas, tasa: reservas ? Math.round((canceladas / reservas) * 10000) / 100 : 0 };
  });

  protected readonly alerts = computed(() =>
    this.providers().filter(p => p.tasa_cancelacion > this.threshold()));

  /** Eje de 0 al siguiente múltiplo de 10 que cubra la tasa más alta y el umbral */
  protected readonly scaleMax = computed(() => {
    const top = Math.max(this.threshold(), ...this.providers().map(p => p.tasa_cancelacion), 10);
    return Math.ceil(top / 10) * 10;
  });
  protected readonly ticks = computed(() => {
    const max = this.scaleMax();
    const step = max > 50 ? 20 : 10;
    return Array.from({ length: Math.floor(max / step) + 1 }, (_, i) => i * step);
  });

  protected readonly selected = computed<TasaCancelacionRow | null>(() => {
    const rows = this.providers();
    return rows.find(p => p.provider_id === this.selectedId())
      ?? [...rows].sort((a, b) => b.tasa_cancelacion - a.tasa_cancelacion)[0] ?? null;
  });

  // --- Calidad de datos ---
  protected readonly cleanTables = computed(() =>
    (this.data()?.completitud ?? []).filter(t => t.filas_con_nulos === 0).length);

  constructor() {
    this.load$.pipe(
      switchMap(dataset => {
        this.loading.set(true);
        this.error.set('');
        return this.service.results(dataset);
      }),
      takeUntilDestroyed()
    ).subscribe({
      next: data => { this.data.set(data); this.loading.set(false); },
      error: err => {
        this.loading.set(false);
        this.error.set(errorMessage(err, 'No se pudieron cargar los resultados del pipeline de datos'));
      }
    });
    this.load$.next(this.dataset());
  }

  protected setDataset(dataset: AnalyticsDataset) {
    if (dataset === this.dataset()) return;
    this.dataset.set(dataset);
    this.selectedId.set(null);
    this.focusStatus.set(null);
    this.load$.next(dataset);
  }

  protected retry() { this.load$.next(this.dataset()); }

  protected setThreshold(value: string) { this.threshold.set(Number(value)); }

  protected setSort(value: string) { this.sort.set(value as SortKey); }

  protected toggleStatus(status: BookingStatus) {
    this.focusStatus.set(this.focusStatus() === status ? null : status);
  }

  protected inAlert(p: TasaCancelacionRow) { return p.tasa_cancelacion > this.threshold(); }

  protected barWidth(p: TasaCancelacionRow) { return (p.tasa_cancelacion / this.scaleMax()) * 100; }

  protected position(value: number) { return (value / this.scaleMax()) * 100; }

  protected shortId(id: string) { return `${id.slice(0, 8)}…${id.slice(-4)}`; }
}
