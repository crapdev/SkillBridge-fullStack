import { HttpErrorResponse, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, delay, of, switchMap, throwError, timer } from 'rxjs';
import { AdminOffering } from './admin-offering.model';

/**
 * Backend simulado de /api/admin/offerings (HU-21) para revisar el panel sin servidor.
 * Mismo contrato que AdminOfferingReviewController; los datos viven en memoria.
 */
export const OFFERINGS_URL = /\/admin\/offerings\/(?:(pending)|([^/?]+)\/review)$/;

const offerings: AdminOffering[] = [
  mock('Kubernetes desde cero', 'CLOUD', 130000, 'Andrés Quintero', 'AVANZADO', 90,
    ['Pods y deployments', 'Servicios e ingress', 'Helm']),
  mock('Testing en Angular', 'FRONTEND', 80000, 'Laura Salazar', 'INTERMEDIO', 60,
    ['Jasmine y Karma', 'Pruebas de componentes']),
  mock('Diseño de APIs REST', 'BACKEND', 95000, 'Felipe Arango', 'BASICO', 60,
    ['Recursos y verbos HTTP', 'Errores con ProblemDetail', 'Versionado'])
];

export function handleOfferings(req: HttpRequest<unknown>, match: RegExpMatchArray): Observable<HttpResponse<unknown>> {
  if (req.method === 'GET' && match[1]) {
    return ok(offerings.filter(o => o.reviewStatus === 'PENDING_REVIEW'));
  }
  const offering = offerings.find(o => o.id === match[2]);
  if (!offering) return fail(404, 'Mentoría no encontrada');
  if (offering.reviewStatus !== 'PENDING_REVIEW') {
    return fail(422, 'Solo se pueden revisar mentorías que están pendientes de revisión');
  }
  const body = req.body as { decision: string; reason?: string | null };
  offering.reviewStatus = body.decision === 'APPROVED' ? 'APPROVED' : 'REJECTED';
  offering.rejectionReason = offering.reviewStatus === 'REJECTED' ? body.reason?.trim() || null : null;
  return ok(offering);
}

function mock(title: string, category: string, price: number, providerName: string,
              level: AdminOffering['level'], durationMinutes: number, topics: string[]): AdminOffering {
  return {
    id: crypto.randomUUID(), title, category, price, level, durationMinutes,
    description: `Mentoría 1:1 de ${title.toLowerCase()} con ejercicios sobre tu propio proyecto.`,
    syllabus: topics.map(t => ({ title: t, description: `Práctica guiada de ${t.toLowerCase()}.` })),
    workingDays: ['MONDAY', 'WEDNESDAY', 'FRIDAY'],
    reviewStatus: 'PENDING_REVIEW', rejectionReason: null,
    providerId: crypto.randomUUID(), providerName,
    providerEmail: providerName.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(' ', '.') + '@skillbridge.dev'
  };
}

const ok = (body: unknown) => of(new HttpResponse({ status: 200, body })).pipe(delay(350));
// delay() no retrasa errores, por eso timer (igual que en los datos simulados de usuarios)
const fail = (status: number, detail: string) =>
  timer(350).pipe(switchMap(() => throwError(() => new HttpErrorResponse({ status, error: { status, detail } }))));
