import { HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, delay, of } from 'rxjs';
import { AnalyticsDataset, CompletitudRow, SparkAnalytics, UpcomingMetric } from './admin-analytics.model';

/**
 * Backend simulado de /api/admin/analytics: devuelve lo que produce el pipeline de PySpark (data/).
 *
 * - fixtures: valores exactos que calculan las funciones sobre data/tests/fixtures/*.csv; son los
 *   mismos que esperan las pruebas (p. ej. prov_02 = 12.5 % y prov_03 = 25 % en test_tasa_cancelacion.py).
 * - snapshot: un corte más grande y simulado, que conserva esas dos tasas para que cuadre con las pruebas.
 */
export const ANALYTICS_URL = /\/admin\/analytics$/;

export function handleAnalytics(req: HttpRequest<unknown>): Observable<HttpResponse<unknown>> {
  const dataset: AnalyticsDataset = req.params.get('dataset') === 'fixtures' ? 'fixtures' : 'snapshot';
  return of(new HttpResponse({ status: 200, body: dataset === 'fixtures' ? FIXTURES : SNAPSHOT })).pipe(delay(400));
}

// Columnas obligatorias (nullable=False) de data/src/skillbridge_data/schemas.py
const REQUIRED: Record<string, string[]> = {
  users: ['id', 'role', 'status', 'created_at'],
  offerings: ['id', 'title', 'category', 'price', 'level', 'duration_minutes', 'review_status', 'active', 'created_at'],
  bookings: ['id', 'offering_id', 'customer_id', 'scheduled_at', 'status', 'created_at'],
  availability_slots: ['id', 'offering_id', 'scheduled_at', 'reserved'],
  payments: ['id', 'booking_id', 'amount', 'currency', 'status', 'created_at'],
  reviews: ['id', 'booking_id', 'rating', 'created_at']
};

const completitud = (rows: Record<string, number>): CompletitudRow[] =>
  Object.entries(REQUIRED).map(([tabla, columnas]) => ({ tabla, filas: rows[tabla], columnas, filas_con_nulos: 0 }));

const PROXIMAS: UpcomingMetric[] = [
  {
    title: 'Integridad referencial de reservas',
    description: 'Reservas que apuntan a una mentoría o a un cliente que no existe.',
    reference: 'SCRUM-353',
    technique: 'join left_anti',
    state: 'EN_DESARROLLO'
  },
  {
    title: 'Calificación promedio por mentoría',
    description: 'Promedio de reseñas de 1 a 5 por mentoría y por proveedor.',
    reference: 'feature/data-calificacion-promedio',
    technique: 'join + groupBy + avg',
    state: 'EN_DESARROLLO'
  },
  {
    title: 'Conciliación de pagos vs. reservas',
    description: 'Pagos PAID sin reserva confirmada o completada, y al revés.',
    reference: 'HU-50',
    technique: 'full outer join',
    state: 'BACKLOG'
  },
  {
    title: 'Ocupación real de franjas',
    description: 'Franjas reservadas sobre franjas publicadas, sin contar las liberadas por cancelación.',
    reference: 'Backlog de datos',
    technique: 'join + agregación condicional',
    state: 'BACKLOG'
  },
  {
    title: 'Retención de clientes por mentor',
    description: 'Clientes que vuelven a reservar con el mismo proveedor.',
    reference: 'Backlog de datos',
    technique: 'window functions',
    state: 'BACKLOG'
  }
];

// Salida real sobre los fixtures: bookings.csv tiene 12 reservas de 2 proveedores
const FIXTURES: SparkAnalytics = {
  dataset: 'fixtures',
  generatedAt: '2026-11-10T14:00:00Z',
  sparkVersion: '4.0.1',
  source: 'data/tests/fixtures/*.csv',
  reservasPorEstado: [
    { status: 'CANCELLED', reservas: 2 },
    { status: 'COMPLETED', reservas: 6 },
    { status: 'CONFIRMED', reservas: 2 },
    { status: 'CREATED', reservas: 2 }
  ],
  tasaCancelacion: [
    { provider_id: 'a0000000-0000-0000-0000-000000000002', provider_name: 'prov_02', reservas: 8, canceladas: 1, tasa_cancelacion: 12.5 },
    { provider_id: 'a0000000-0000-0000-0000-000000000003', provider_name: 'prov_03', reservas: 4, canceladas: 1, tasa_cancelacion: 25.0 }
  ],
  completitud: completitud({ users: 7, offerings: 4, bookings: 12, availability_slots: 14, payments: 11, reviews: 6 }),
  proximas: PROXIMAS
};

// Corte simulado: 240 reservas de 8 proveedores (34 canceladas)
const SNAPSHOT: SparkAnalytics = {
  dataset: 'snapshot',
  generatedAt: '2026-11-10T14:00:00Z',
  sparkVersion: '4.0.1',
  source: 'Snapshot de PostgreSQL (simulado)',
  reservasPorEstado: [
    { status: 'CANCELLED', reservas: 34 },
    { status: 'COMPLETED', reservas: 142 },
    { status: 'CONFIRMED', reservas: 38 },
    { status: 'CREATED', reservas: 26 }
  ],
  tasaCancelacion: [
    provider(2, 'Ana Pérez', 48, 6),
    provider(3, 'Carlos Morales', 32, 8),
    provider(4, 'Javier Fernández', 41, 2),
    provider(5, 'Mariana Silva', 27, 9),
    provider(6, 'Daniela Castro', 36, 3),
    provider(7, 'Roberto Méndez', 19, 1),
    provider(8, 'Gabriel Ortiz', 22, 5),
    provider(9, 'Mateo Morales', 15, 0)
  ],
  completitud: completitud({ users: 64, offerings: 21, bookings: 240, availability_slots: 312, payments: 206, reviews: 118 }),
  proximas: PROXIMAS
};

// Igual que F.round((canceladas / reservas) * 100, 2) en tasa_cancelacion.py
function provider(n: number, name: string, reservas: number, canceladas: number) {
  return {
    provider_id: `a0000000-0000-0000-0000-${String(n).padStart(12, '0')}`,
    provider_name: name,
    reservas,
    canceladas,
    tasa_cancelacion: Math.round((canceladas / reservas) * 10000) / 100
  };
}
