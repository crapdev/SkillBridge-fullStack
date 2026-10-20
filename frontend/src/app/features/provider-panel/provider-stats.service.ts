import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from '../../core/api';

/** Indicadores del dashboard del proveedor (HU-22): mismo contrato que GET /api/provider/stats */
export interface ProviderStats {
  offerings: { total: number; approved: number; pendingReview: number; rejected: number; approvedWithoutFreeSlots: number };
  slots: { upcomingFree: number; upcomingReserved: number };
  bookings: { total: number; upcoming: number; pendingPayment: number; confirmed: number; completed: number; cancelled: number };
}

@Injectable({ providedIn: 'root' })
export class ProviderStatsService {
  private http = inject(HttpClient);

  /** Solo los datos del proveedor de la sesión: el backend lo toma del token */
  stats() { return this.http.get<ProviderStats>(`${apiBase()}/provider/stats`); }
}
