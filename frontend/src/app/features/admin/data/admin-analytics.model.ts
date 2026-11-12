/**
 * Resultados del pipeline de datos (PySpark, proyecto data/).
 * Cada arreglo es la salida de una función de data/src/skillbridge_data, con los mismos nombres de
 * columna (snake_case) que el DataFrame, para que la vista se pueda explicar fila a fila.
 */

/** fixtures: los CSV de data/tests/fixtures (los de las pruebas). snapshot: corte simulado más grande. */
export type AnalyticsDataset = 'snapshot' | 'fixtures';

export type BookingStatus = 'COMPLETED' | 'CANCELLED' | 'CONFIRMED' | 'CREATED';

/** metrics/ejemplo_reservas_por_estado.py → reservas_por_estado(bookings) */
export interface ReservasPorEstadoRow {
  status: BookingStatus;
  reservas: number;
}

/** metrics/tasa_cancelacion.py → tasa_cancelacion(bookings, offerings) (HU-46). provider_name viene de users. */
export interface TasaCancelacionRow {
  provider_id: string;
  provider_name: string;
  reservas: number;
  canceladas: number;
  tasa_cancelacion: number;
}

/** quality/ejemplo_completitud.py → filas_con_nulos(df, columnas), contado por tabla */
export interface CompletitudRow {
  tabla: string;
  filas: number;
  columnas: string[];
  filas_con_nulos: number;
}

/** Métricas del backlog de datos que todavía no tienen resultados */
export interface UpcomingMetric {
  title: string;
  description: string;
  reference: string;
  technique: string;
  state: 'EN_DESARROLLO' | 'BACKLOG';
}

export interface SparkAnalytics {
  dataset: AnalyticsDataset;
  generatedAt: string;
  sparkVersion: string;
  source: string;
  reservasPorEstado: ReservasPorEstadoRow[];
  tasaCancelacion: TasaCancelacionRow[];
  completitud: CompletitudRow[];
  proximas: UpcomingMetric[];
}

export const BOOKING_STATUS_LABELS: Record<BookingStatus, string> = {
  COMPLETED: 'Completadas',
  CANCELLED: 'Canceladas',
  CONFIRMED: 'Confirmadas',
  CREATED: 'Creadas (sin pagar)'
};
