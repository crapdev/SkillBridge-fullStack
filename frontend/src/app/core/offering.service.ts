import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { apiBase } from './api';

/** Nivel de la mentoría (HU-20) */
export type OfferingLevel = 'BASICO' | 'INTERMEDIO' | 'AVANZADO';

/** Días de la semana tal como los maneja el backend (java.time.DayOfWeek) */
export type WeekDay = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

/** Un tema del temario definido por el proveedor */
export interface SyllabusTopic {
  title: string;
  description: string;
}

export interface Offering {
  id: string;
  title: string;
  description: string;
  category: string;
  price: number;
  active: boolean;
  // ---- Ficha de la mentoría (HU-20) ----
  syllabus: SyllabusTopic[];
  durationMinutes: number;
  level: OfferingLevel;
  workingDays: WeekDay[];
  // ---- Revisión del administrador (HU-21): solo las aprobadas llegan al catálogo ----
  reviewStatus?: OfferingReviewStatus;
  rejectionReason?: string | null;
}

export type OfferingReviewStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';

/** Horario publicado por el proveedor; `reserved` indica que un cliente ya lo tomó. */
export interface AvailabilitySlot {
  id: string;
  offeringId: string;
  scheduledAt: string;
  reserved: boolean;
}

/** Resultado de publicar horarios en lote: los creados y los omitidos con su motivo */
export interface SlotBatchResult {
  created: AvailabilitySlot[];
  skipped: { scheduledAt: string; reason: string }[];
}

export interface PageMetadata {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

export interface PagedOfferings {
  content: Offering[];
  page: PageMetadata;
}

/** Campos que el proveedor llena al crear o editar una mentoría (datos básicos + ficha HU-20) */
export interface OfferingInput {
  title: string;
  description: string;
  category: string;
  price: number;
  syllabus: SyllabusTopic[];
  durationMinutes: number;
  level: OfferingLevel;
  workingDays: WeekDay[];
}

@Injectable({ providedIn: 'root' })
export class OfferingService {
  constructor(private http: HttpClient) {}

  // Catálogo público paginado
  listPaged(page: number = 0, size: number = 9): Observable<PagedOfferings> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedOfferings>(`${apiBase()}/offerings`, { params });
  }

  // Catálogo público compatible con vistas existentes
  list(size: number = 1000): Observable<Offering[]> {
    const params = new HttpParams().set('size', size);
    return this.http.get<any>(`${apiBase()}/offerings`, { params }).pipe(
      map(res => (Array.isArray(res) ? res : (res?.content ?? [])) as Offering[])
    );
  }

  // Detalle de mentoría pública por ID (Criterio 7)
  getById(id: string): Observable<Offering> {
    return this.http.get<Offering>(`${apiBase()}/offerings/${id}`);
  }

  // Horarios libres y futuros de una mentoría: los que el cliente puede reservar
  availableSlots(offeringId: string) {
    return this.http.get<AvailabilitySlot[]>(`${apiBase()}/offerings/${offeringId}/slots`);
  }

  // --- MÉTODOS EXCLUSIVOS DEL PROVEEDOR ---
  
  getProviderOfferings() {
    return this.http.get<Offering[]>(`${apiBase()}/provider/offerings`);
  }

  createProviderOffering(data: OfferingInput) {
    return this.http.post<Offering>(`${apiBase()}/provider/offerings`, data);
  }

  // HU-19: solo el dueño puede editarla; el backend responde 403 si es de otro proveedor
  updateProviderOffering(id: string, data: OfferingInput) {
    return this.http.put<Offering>(`${apiBase()}/provider/offerings/${id}`, data);
  }

  deleteProviderOffering(id: string) {
    return this.http.delete(`${apiBase()}/provider/offerings/${id}`);
  }

  getProviderSlots(offeringId: string) {
    return this.http.get<AvailabilitySlot[]>(`${apiBase()}/provider/offerings/${offeringId}/slots`);
  }

  addProviderSlot(offeringId: string, scheduledAt: string) {
    return this.http.post<AvailabilitySlot>(`${apiBase()}/provider/offerings/${offeringId}/slots`, { scheduledAt });
  }

  /** Varios horarios de una vez: los que no se pueden publicar vuelven en "skipped" con su motivo */
  addProviderSlots(offeringId: string, scheduledAt: string[]) {
    return this.http.post<SlotBatchResult>(`${apiBase()}/provider/offerings/${offeringId}/slots/batch`, { scheduledAt });
  }

  deleteProviderSlot(offeringId: string, slotId: string) {
    return this.http.delete(`${apiBase()}/provider/offerings/${offeringId}/slots/${slotId}`);
  }
}
