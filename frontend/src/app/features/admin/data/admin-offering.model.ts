import { OfferingLevel, SyllabusTopic, WeekDay } from '../../../core/offering.service';

/** Revisión de una mentoría por el administrador (HU-21). Solo las aprobadas llegan al catálogo. */
export type OfferingReviewStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';

/** Lo que ve el administrador: la ficha completa y quién propone la mentoría */
export interface AdminOffering {
  id: string;
  title: string;
  description: string;
  category: string;
  price: number;
  syllabus: SyllabusTopic[];
  durationMinutes: number;
  level: OfferingLevel;
  workingDays: WeekDay[];
  reviewStatus: OfferingReviewStatus;
  rejectionReason: string | null;
  providerId: string;
  providerName: string | null;
  providerEmail: string | null;
}

export type ReviewAction = 'approve' | 'reject';

/** Mismo límite que el backend (columna rejection_reason) */
export const MAX_REJECTION_REASON = 300;
