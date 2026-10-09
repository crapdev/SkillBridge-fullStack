import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';

export interface Offering {
  id: string;
  title: string;
  description: string;
  category: string;
  price: number;
  active: boolean;
}

/** Horario publicado por el proveedor; `reserved` indica que un cliente ya lo tomó. */
export interface AvailabilitySlot {
  id: string;
  offeringId: string;
  scheduledAt: string;
  reserved: boolean;
}

@Injectable({ providedIn: 'root' })
export class OfferingService {
  constructor(private http: HttpClient) {}
  
  // Catálogo público para los estudiantes
  list() { return this.http.get<Offering[]>(`${apiBase()}/offerings`); }

  // Horarios libres y futuros de una mentoría: los que el cliente puede reservar
  availableSlots(offeringId: string) {
    return this.http.get<AvailabilitySlot[]>(`${apiBase()}/offerings/${offeringId}/slots`);
  }

  // --- MÉTODOS EXCLUSIVOS DEL PROVEEDOR ---
  
  getProviderOfferings() {
    return this.http.get<Offering[]>(`${apiBase()}/provider/offerings`);
  }

  createProviderOffering(data: any) {
    return this.http.post<Offering>(`${apiBase()}/provider/offerings`, data);
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

  deleteProviderSlot(offeringId: string, slotId: string) {
    return this.http.delete(`${apiBase()}/provider/offerings/${offeringId}/slots/${slotId}`);
  }
}