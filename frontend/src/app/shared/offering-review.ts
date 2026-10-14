import { OfferingReviewStatus } from '../core/offering.service';

/**
 * Revisión de mentorías por el administrador (HU-21), tal como la ve el proveedor.
 * Plazo que se le comunica al proveedor: si el equipo define otro, se cambia solo aquí.
 */
export const REVIEW_ESTIMATE = '24 a 48 horas';

export const REVIEW_STATUS: Record<OfferingReviewStatus, { label: string; tone: 'warn' | 'ok' | 'danger' }> = {
  PENDING_REVIEW: { label: 'En revisión', tone: 'warn' },
  APPROVED: { label: 'Aprobada', tone: 'ok' },
  REJECTED: { label: 'Rechazada', tone: 'danger' }
};

/** Sin estado (datos anteriores a HU-21) cuenta como aprobada, igual que en el backend */
export function reviewStatusOf(offering: { reviewStatus?: OfferingReviewStatus }): OfferingReviewStatus {
  return offering.reviewStatus ?? 'APPROVED';
}
